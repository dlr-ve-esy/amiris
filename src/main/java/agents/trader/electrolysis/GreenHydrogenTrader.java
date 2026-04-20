// SPDX-FileCopyrightText: 2024-2026 German Aerospace Center <amiris@dlr.de>
//
// SPDX-License-Identifier: Apache-2.0
package agents.trader.electrolysis;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map.Entry;
import accounting.AnnualCostCalculator;
import agents.electrolysis.Electrolyzer;
import agents.forecast.MarketForecaster;
import agents.markets.DayAheadMarket;
import agents.markets.DayAheadMarketTrader;
import agents.markets.FuelsMarket;
import agents.markets.FuelsTrader;
import agents.markets.meritOrder.Bid;
import agents.plantOperator.PowerPlantScheduler;
import agents.plantOperator.renewable.VariableRenewableOperatorPpa;
import agents.policy.hydrogen.HydrogenSupportClient;
import agents.policy.hydrogen.HydrogenSupportProvider;
import agents.policy.hydrogen.Mpfix;
import agents.policy.hydrogen.PolicyItem;
import agents.trader.Trader;
import communications.message.AmountAtTime;
import communications.message.AwardData;
import communications.message.ClearingTimes;
import communications.message.FuelBid;
import communications.message.FuelBid.BidType;
import communications.message.FuelCost;
import communications.message.FuelData;
import communications.message.HydrogenPolicyRegistration;
import communications.message.PpaInformation;
import communications.portable.BidsAtTime;
import communications.portable.HydrogenSupportData;
import de.dlr.gitlab.fame.agent.input.DataProvider;
import de.dlr.gitlab.fame.agent.input.Input;
import de.dlr.gitlab.fame.agent.input.Make;
import de.dlr.gitlab.fame.agent.input.ParameterData;
import de.dlr.gitlab.fame.agent.input.ParameterData.MissingDataException;
import de.dlr.gitlab.fame.agent.input.Tree;
import de.dlr.gitlab.fame.communication.CommUtils;
import de.dlr.gitlab.fame.communication.Contract;
import de.dlr.gitlab.fame.communication.Product;
import de.dlr.gitlab.fame.communication.message.Message;
import de.dlr.gitlab.fame.service.output.Output;
import de.dlr.gitlab.fame.time.TimeStamp;
import util.Util;
import util.Util.MessagePair;

/** Electricity and Hydrogen Trader that uses electricity produced by a renewable plant operator to produce green hydrogen
 * (utilising an electrolysis device) in hourly equivalence. No grey electricity is bought from the market. Thus, if not enough
 * green electricity is available, less hydrogen is produced. In hours with a green electricity surplus (or hours with electricity
 * prices above their corresponding hydrogen equivalence), the electricity is sold at the day-ahead market.
 * 
 * @author Johannes Kochems, Christoph Schimeczek */
public class GreenHydrogenTrader extends Trader
		implements FuelsTrader, PowerPlantScheduler, GreenHydrogenProducer, HydrogenSupportClient {
	static final String ERR_MULTIPLE_TIMES = ": Cannot prepare Bids for multiple time steps";

	/** Available products */
	@Product
	public static enum Products {
		/** Report annual costs (not sent to other agents, but calculated within trader class) */
		AnnualCostReport,
	};

	@Input private static final Tree parameters = Make.newTree().add(FuelsTrader.fuelTypeParameter)
			.addAs("Device", Electrolyzer.parameters).addAs("Refinancing", AnnualCostCalculator.parameters)
			.addAs("Support", HydrogenSupportClient.parameters).buildTree();

	@Output
	enum AgentOutputs {
		/** Amount of green hydrogen produced in this period using the electrolysis unit */
		ProducedHydrogenInMWH,
		/** Surplus electricity generation offered to the day-ahead market in MWh */
		OfferedSurplusEnergyInMWH,
		/** Variable operation and maintenance costs in EUR */
		VariableCostsInEUR,
		/** Fixed operation and maintenance costs */
		FixedCostsInEUR,
		/** Annual share of investment cost */
		InvestmentAnnuityInEUR,
		/** Total received money for selling hydrogen in EUR */
		ReceivedMoneyForHydrogenInEUR,
	}

	private final String fuelType;
	private final FuelData fuelData;
	private final Electrolyzer electrolyzer;

	private double lastYieldPotentialInMWH;
	private double lastSoldElectricityInMWH;
	private double lastHydrogenProducedInMWH;
	private TimeStamp lastClearingTime;

	private final AnnualCostCalculator annualCost;

	private HydrogenPolicyRegistration registrationData;
	private PolicyItem policyItem;
	private Mpfix mpfix;

	/** Creates a new {@link GreenHydrogenTrader}
	 * 
	 * @param data provides input from config
	 * @throws MissingDataException if any required data is not provided */
	public GreenHydrogenTrader(DataProvider data) throws MissingDataException {
		super(data);
		ParameterData input = parameters.join(data);
		fuelType = FuelsTrader.readFuelType(input);
		fuelData = new FuelData(fuelType);
		electrolyzer = new Electrolyzer(input.getGroup("Device"));
		annualCost = AnnualCostCalculator.build(input, "Refinancing");

		registrationData = HydrogenSupportClient.getRegistration(input.getOptionalGroup("Support"));

		call(this::requestPpaInformation).on(GreenHydrogenProducer.Products.PpaInformationForecastRequest)
				.use(MarketForecaster.Products.ForecastRequest);
		call(this::requestHydrogenPrice).on(FuelsTrader.Products.FuelPriceForecastRequest)
				.use(MarketForecaster.Products.ForecastRequest);
		call(this::sendBidsForecasts).on(Trader.Products.BidsForecast).use(FuelsMarket.Products.FuelPriceForecast,
				VariableRenewableOperatorPpa.Products.PpaInformationForecast);

		call(this::requestPpaInformation).on(GreenHydrogenProducer.Products.PpaInformationRequest)
				.use(DayAheadMarket.Products.GateClosureInfo);
		call(this::requestHydrogenPrice).on(FuelsTrader.Products.FuelPriceRequest)
				.use(DayAheadMarket.Products.GateClosureInfo);
		call(this::sendBids).on(DayAheadMarketTrader.Products.Bids).use(FuelsMarket.Products.FuelPrice,
				VariableRenewableOperatorPpa.Products.PpaInformation);

		call(this::digestAwards).on(PowerPlantScheduler.Products.DispatchAssignment).use(DayAheadMarket.Products.Awards);
		call(this::sellProducedGreenHydrogen).on(FuelsTrader.Products.FuelBid);
		call(this::digestHydrogenSales).onAndUse(FuelsMarket.Products.FuelBill);
		call(this::payoutClient).on(PowerPlantScheduler.Products.Payout)
				.use(VariableRenewableOperatorPpa.Products.PpaInformation);
		call(this::registerSupport).on(HydrogenSupportClient.Products.SupportInfoRequest);
		call(this::digestSupportInfo).onAndUse(HydrogenSupportProvider.Products.SupportInfo);
		call(this::sendSupportPayoutRequest).on(HydrogenSupportClient.Products.SupportPayoutRequest);
		call(this::digestSupportPayout).onAndUse(HydrogenSupportProvider.Products.SupportPayout);
		call(this::reportCosts).on(Products.AnnualCostReport);
	}

	/** Requests forecast of hydrogen prices from one contracted {@link FuelsMarket}
	 * 
	 * @param input not used
	 * @param contracts single contracted fuels market to request hydrogen price(s) from */
	private void requestHydrogenPrice(ArrayList<Message> input, List<Contract> contracts) {
		Contract contract = CommUtils.getExactlyOneEntry(contracts);
		ClearingTimes clearingTimes = CommUtils.getExactlyOneEntry(input).getDataItemOfType(ClearingTimes.class);
		sendFuelPriceRequest(contract, fuelData, clearingTimes);
	}

	/** Sends forecasted bids to contracted {@link MarketForecaster}
	 * 
	 * @param input hydrogen price forecast and PPA information forecast messages
	 * @param contracts single contract with {@link MarketForecaster} */
	private void sendBidsForecasts(ArrayList<Message> input, List<Contract> contracts) {
		var allBids = prepareBids(input);
		fulfilBidContract(allBids, CommUtils.getExactlyOneEntry(contracts));
	}

	/** @return two bids for each {@link TimeStamp} specified in the given messages */
	private HashMap<TimeStamp, Bid[]> prepareBids(ArrayList<Message> messages) {
		HashMap<TimeStamp, MessagePair<PpaInformation, FuelCost>> messagePairs = Util.matchMessagesByTime(messages,
				PpaInformation.class, FuelCost.class);
		HashMap<TimeStamp, Bid[]> allBids = new HashMap<>();
		for (TimeStamp time : messagePairs.keySet()) {
			PpaInformation ppa = messagePairs.get(time).getFirstItem();
			FuelCost fuelCost = messagePairs.get(time).getSecondItem();
			double supportRateInEURperThermalMWH = getHydrogenSupportRateFor(time);
			double opportunityCostInEURperMWH = (fuelCost.amount + supportRateInEURperThermalMWH)
					* electrolyzer.getConversionFactor();
			double electrolyserElectricDemandInMWH = electrolyzer.calcCappedElectricDemandInMW(ppa.yieldPotentialInMWH, time);
			double surplusElectricityInMWH = ppa.yieldPotentialInMWH - electrolyserElectricDemandInMWH;
			Bid supplyBidElectrolyser = new Bid(electrolyserElectricDemandInMWH, opportunityCostInEURperMWH,
					ppa.marginalCostsInEURperMWH);
			Bid supplyBidSurplus = new Bid(surplusElectricityInMWH, 0, ppa.marginalCostsInEURperMWH);
			allBids.put(time, new Bid[] {supplyBidElectrolyser, supplyBidSurplus});
		}
		return allBids;
	}

	/** Returns the hydrogen support rate in EUR per MWh if defined, else Zero
	 * 
	 * @param timeStamp to search for associated support rate
	 * @return hydrogen support rate in EUR per MWh */
	protected double getHydrogenSupportRateFor(TimeStamp timeStamp) {
		if (policyItem != null) {
			return policyItem.calcInfeedSupportRate(timeStamp);
		}
		return 0.;
	}

	/** Sends given bids to contracted partner */
	private void fulfilBidContract(HashMap<TimeStamp, Bid[]> bids, Contract contract) {
		for (Entry<TimeStamp, Bid[]> entry : bids.entrySet()) {
			fulfilNext(contract, new BidsAtTime(entry.getKey(), getId(), Arrays.asList(entry.getValue()), null));
		}
	}

	/** Sends bids to contracted {@link DayAheadMarket}
	 * 
	 * @param input one hydrogen price and one PPA information message
	 * @param contracts single contract with {@link DayAheadMarket} */
	private void sendBids(ArrayList<Message> messages, List<Contract> contracts) {
		var allBids = prepareBids(messages);
		fulfilBidContract(allBids, CommUtils.getExactlyOneEntry(contracts));

		double totalElectrolyserDemandInMWH = 0;
		double totalSurplusInMWH = 0;

		for (Bid[] bids : allBids.values()) {
			totalElectrolyserDemandInMWH += bids[0].getEnergyAmountInMWH();
			totalSurplusInMWH += bids[1].getEnergyAmountInMWH();
		}
		store(OutputColumns.OfferedEnergyInMWH, totalElectrolyserDemandInMWH + totalSurplusInMWH);
		store(AgentOutputs.OfferedSurplusEnergyInMWH, totalSurplusInMWH);

		if (allBids.values().size() > 1) {
			throw new RuntimeException(this + ERR_MULTIPLE_TIMES);
		}
		lastYieldPotentialInMWH = totalElectrolyserDemandInMWH + totalSurplusInMWH;
	}

	/** Assign electrolyser and contracted renewable power plant to dispatch based on awards of the energy exchange
	 * 
	 * @param messages one award message from a contracted {@link DayAheadMarket}
	 * @param contracts one contract with one renewable power plant operator */
	private void digestAwards(ArrayList<Message> messages, List<Contract> contracts) {
		Contract contract = CommUtils.getExactlyOneEntry(contracts);
		AwardData award = CommUtils.getExactlyOneEntry(messages).getDataItemOfType(AwardData.class);
		lastClearingTime = award.beginOfDeliveryInterval;

		double unsoldElectricityInMWH = lastYieldPotentialInMWH - award.supplyEnergyInMWH;
		double electrolyserDispatchInMWH = electrolyzer.calcCappedElectricDemandInMW(unsoldElectricityInMWH,
				lastClearingTime);
		lastSoldElectricityInMWH = award.supplyEnergyInMWH;
		double electricityUsedInMWH = lastSoldElectricityInMWH + electrolyserDispatchInMWH;
		fulfilNext(contract, new AmountAtTime(award.beginOfDeliveryInterval, electricityUsedInMWH));
		lastHydrogenProducedInMWH = electrolyzer.calcProducedHydrogenOneHour(electrolyserDispatchInMWH,
				award.beginOfDeliveryInterval);
		store(OutputColumns.AwardedEnergyInMWH, award.supplyEnergyInMWH);
		store(GreenHydrogenProducer.Outputs.ReceivedMoneyForElectricityInEUR,
				award.supplyEnergyInMWH * award.powerPriceInEURperMWH);
		store(GreenHydrogenProducer.Outputs.ConsumedElectricityInMWH, electrolyserDispatchInMWH);
		store(AgentOutputs.ProducedHydrogenInMWH, lastHydrogenProducedInMWH);
	}

	/** Sell hydrogen according to production schedule following the contracted renewable power plant
	 * 
	 * @param messages not used
	 * @param contracts a contract with one {@link FuelsMarket} */
	private void sellProducedGreenHydrogen(ArrayList<Message> messages, List<Contract> contracts) {
		Contract contract = CommUtils.getExactlyOneEntry(contracts);
		FuelBid fuelBid = new FuelBid(lastClearingTime, lastHydrogenProducedInMWH, BidType.Supply, fuelType);
		sendFuelBid(contract, fuelBid);
	}

	/** Evaluate revenues (i.e. negative purchase cost) from selling hydrogen at the fuels market
	 * 
	 * @param messages one AmountAtTime message from fuels market
	 * @param contracts ignored */
	private void digestHydrogenSales(ArrayList<Message> messages, List<Contract> contracts) {
		Message message = CommUtils.getExactlyOneEntry(messages);
		double cost = readFuelBillMessage(message);
		store(AgentOutputs.ReceivedMoneyForHydrogenInEUR, -cost);
	}

	/** Pay client according to PPA specification
	 * 
	 * @param messages contain PPA information
	 * @param contracts payment to client from PPA */
	private void payoutClient(ArrayList<Message> messages, List<Contract> contracts) {
		Message message = CommUtils.getExactlyOneEntry(messages);
		Contract contract = CommUtils.getExactlyOneEntry(contracts);
		PpaInformation ppa = message.getDataItemOfType(PpaInformation.class);
		double payment = ppa.yieldPotentialInMWH * ppa.priceInEURperMWH;
		fulfilNext(contract, new AmountAtTime(ppa.validAt, payment));
		store(AgentOutputs.VariableCostsInEUR, payment);
	}

	/** Write annual costs to output
	 * 
	 * @param input not used
	 * @param contracts not used */
	protected void reportCosts(ArrayList<Message> input, List<Contract> contracts) {
		store(AgentOutputs.InvestmentAnnuityInEUR, annualCost.calcInvestmentAnnuityInEUR(electrolyzer.getPeakPower(now())));
		store(AgentOutputs.FixedCostsInEUR, annualCost.calcFixedCostInEUR(electrolyzer.getPeakPower(now())));
	}

	@Override
	public HydrogenPolicyRegistration getRegistrationData() {
		return registrationData;
	}

	@Override
	public void saveSupportData(HydrogenSupportData hydrogenSupportData) {
		mpfix = hydrogenSupportData.getPolicyOfType(Mpfix.class);
		this.policyItem = mpfix;
	}

	/** Action to send a pay-out request to a single connected {@link HydrogenSupportProvider} based on the last production event */
	private void sendSupportPayoutRequest(ArrayList<Message> messages, List<Contract> contracts) {
		Contract contract = CommUtils.getExactlyOneEntry(contracts);
		fulfilNext(contract, new AmountAtTime(lastClearingTime, lastHydrogenProducedInMWH));
	}
}
