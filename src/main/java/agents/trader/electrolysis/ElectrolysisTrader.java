// SPDX-FileCopyrightText: 2026 German Aerospace Center <amiris@dlr.de>
//
// SPDX-License-Identifier: Apache-2.0
package agents.trader.electrolysis;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import agents.electrolysis.Electrolyzer;
import agents.electrolysis.ElectrolyzerStrategist;
import agents.flexibility.BidSchedule;
import agents.flexibility.Strategist;
import agents.forecast.DamForecastClient;
import agents.forecast.DamForecastProvider;
import agents.forecast.MarketForecaster;
import agents.markets.DayAheadMarket;
import agents.markets.DayAheadMarketTrader;
import agents.markets.FuelsMarket;
import agents.markets.FuelsTrader;
import agents.markets.meritOrder.Bid;
import agents.markets.meritOrder.Constants;
import agents.plantOperator.PowerPlantScheduler;
import agents.policy.hydrogen.HydrogenSupportClient;
import agents.policy.hydrogen.HydrogenSupportProvider;
import agents.policy.hydrogen.Mpfix;
import agents.storage.arbitrageStrategists.FileDispatcher;
import agents.trader.FlexibilityTrader;
import agents.trader.Trader;
import communications.message.AmountAtTime;
import communications.message.AwardData;
import communications.message.ClearingTimes;
import communications.message.FuelBid;
import communications.message.FuelBid.BidType;
import communications.message.FuelCost;
import communications.message.FuelData;
import communications.message.HydrogenPolicyRegistration;
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
import de.dlr.gitlab.fame.communication.message.Message;
import de.dlr.gitlab.fame.service.output.Output;
import de.dlr.gitlab.fame.time.TimePeriod;
import de.dlr.gitlab.fame.time.TimeStamp;

/** A flexible Trader demanding electricity and producing hydrogen from it via electrolysis.
 * 
 * @author Christoph Schimeczek */
public class ElectrolysisTrader extends FlexibilityTrader
		implements FuelsTrader, PowerPlantScheduler, HydrogenSupportClient {
	@Input private static final Tree parameters = Make.newTree().add(FuelsTrader.fuelTypeParameter)
			.addAs("Device", Electrolyzer.parameters)
			.addAs("Strategy", ElectrolyzerStrategist.parameters)
			.addAs("Support", HydrogenSupportClient.parameters).buildTree();

	/** Available output columns */
	@Output
	protected static enum Outputs {
		/** Price at which electricity offers are placed at the day-ahead market */
		OfferedElectricityPriceInEURperMWH,
		/** Amount of hydrogen produced */
		ProducedHydrogenInMWH,
		/** Total received money for selling hydrogen in EUR */
		ReceivedMoneyForHydrogenInEUR
	}

	private final String fuelType;
	private final FuelData fuelData;

	/** Electrolyzer device used for hydrogen production */
	protected final Electrolyzer electrolyzer;
	/** Strategist used to plan the dispatch of the electrolyzer device and the bidding at the day-ahead market */
	protected final ElectrolyzerStrategist strategist;
	/** Amount of hydrogen produced based on the last electricity price clearing */
	protected double lastProducedHydrogenInMWH = 0;
	/** First TimeStamp of the last electricity price clearing interval */
	protected TimeStamp lastClearingTime;

	private HydrogenPolicyRegistration registrationData;
	private Mpfix mpfix;

	/** Creates a new {@link ElectrolysisTrader} based on given input parameters
	 * 
	 * @param data configured input
	 * @throws MissingDataException if any required input is missing */
	public ElectrolysisTrader(DataProvider data) throws MissingDataException {
		super(data);
		ParameterData input = parameters.join(data);
		electrolyzer = new Electrolyzer(input.getGroup("Device"));
		strategist = ElectrolyzerStrategist.newStrategist(input.getGroup("Strategy"), electrolyzer);

		fuelType = FuelsTrader.readFuelType(input);
		fuelData = new FuelData(fuelType);

		registrationData = HydrogenSupportClient.getRegistration(input.getOptionalGroup("Support"));

		call(this::prepareForecasts).on(Trader.Products.BidsForecast).use(MarketForecaster.Products.ForecastRequest);
		call(this::requestElectricityForecast).on(DamForecastClient.Products.PriceForecastRequest)
				.use(DayAheadMarket.Products.GateClosureInfo);
		call(this::requestHydrogenPriceForecast).on(FuelsTrader.Products.FuelPriceForecastRequest)
				.use(DayAheadMarket.Products.GateClosureInfo);
		call(this::updateElectricityPriceForecast).onAndUse(DamForecastProvider.Products.PriceForecast);
		call(this::updateHydrogenPriceForecast).onAndUse(FuelsMarket.Products.FuelPriceForecast);
		call(this::prepareBids).on(DayAheadMarketTrader.Products.Bids).use(DayAheadMarket.Products.GateClosureInfo);
		call(this::digestAwards).onAndUse(DayAheadMarket.Products.Awards);
		call(this::sellProducedHydrogen).on(FuelsTrader.Products.FuelBid);
		call(this::digestSaleReturns).onAndUse(FuelsMarket.Products.FuelBill);
		call(this::registerSupport).on(HydrogenSupportClient.Products.SupportInfoRequest);
		call(this::digestSupportInfo).onAndUse(HydrogenSupportProvider.Products.SupportInfo);
		call(this::sendSupportPayoutRequest).on(HydrogenSupportClient.Products.SupportPayoutRequest);
		call(this::digestSupportPayout).onAndUse(HydrogenSupportProvider.Products.SupportPayout);
	}

	/** Prepares forecasts and sends them to the {@link MarketForecaster}; Calling this function will throw an Exception for
	 * Strategists other than {@link FileDispatcher}
	 * 
	 * @param input one ClearingTimes message specifying for which TimeStamps to calculate the forecasts
	 * @param contracts one partner to send the forecasts to */
	private void prepareForecasts(ArrayList<Message> input, List<Contract> contracts) {
		Contract contractToFulfil = CommUtils.getExactlyOneEntry(contracts);
		ClearingTimes clearingTimes = CommUtils.getExactlyOneEntry(input).getDataItemOfType(ClearingTimes.class);
		List<TimeStamp> targetTimes = clearingTimes.getTimes();
		for (TimeStamp targetTime : targetTimes) {
			double electricDemandInMW = strategist.getElectricDemandForecastInMW(targetTime);
			Bid bid = new Bid(electricDemandInMW, Constants.SCARCITY_PRICE_IN_EUR_PER_MWH, Double.NaN);
			fulfilNext(contractToFulfil, new BidsAtTime(targetTime, getId(), null, Arrays.asList(bid)));
		}
	}

	/** Requests forecast of hydrogen prices from one contracted {@link FuelsMarket}
	 * 
	 * @param input one ClearingTimes message from connected {@link DayAheadMarket}
	 * @param contracts single contracted fuels market to request hydrogen price(s) from */
	private void requestHydrogenPriceForecast(ArrayList<Message> input, List<Contract> contracts) {
		Contract contract = CommUtils.getExactlyOneEntry(contracts);
		ClearingTimes clearingTimes = CommUtils.getExactlyOneEntry(input).getDataItemOfType(ClearingTimes.class);
		TimePeriod nextTime = new TimePeriod(clearingTimes.getTimes().get(0), Strategist.OPERATION_PERIOD);
		ArrayList<TimeStamp> missingForecastTimes = strategist.getMissingHydrogenPriceForecastsTimes(nextTime);
		ClearingTimes missingTimes = new ClearingTimes(
				missingForecastTimes.toArray(new TimeStamp[missingForecastTimes.size()]));
		sendFuelPriceRequest(contract, fuelData, missingTimes);
	}

	/** Digests one or multiple incoming hydrogen price forecasts
	 * 
	 * @param input one or multiple hydrogen price forecast message(s)
	 * @param contracts not used */
	private void updateHydrogenPriceForecast(ArrayList<Message> input, List<Contract> contracts) {
		for (Message inputMessage : input) {
			FuelCost priceForecastMessage = readFuelPriceMessage(inputMessage);
			double priceForecastInEURperThermalMWH = priceForecastMessage.amount;
			TimePeriod timeSegment = new TimePeriod(priceForecastMessage.validAt, Strategist.OPERATION_PERIOD);
			strategist.storeHydrogenPriceForecast(timeSegment, priceForecastInEURperThermalMWH);
		}
	}

	/** Prepares and sends Bids to one contracted exchange
	 * 
	 * @param input one GateClosureInfo message containing ClearingTimes
	 * @param contracts single contract with a {@link DayAheadMarket} */
	protected void prepareBids(ArrayList<Message> input, List<Contract> contracts) {
		Contract contractToFulfil = CommUtils.getExactlyOneEntry(contracts);
		for (TimeStamp targetTime : extractTimesFromGateClosureInfoMessages(input)) {
			BidSchedule schedule = strategist.getValidSchedule(targetTime);
			Bid bid = prepareHourlyDemandBid(targetTime, schedule);
			store(OutputColumns.RequestedEnergyInMWH, bid.getEnergyAmountInMWH());
			fulfilNext(contractToFulfil, new BidsAtTime(targetTime, getId(), null, Arrays.asList(bid)));
		}
	}

	/** Prepares hourly demand bid
	 * 
	 * @param requestedTime TimeStamp at which the demand bid should be defined
	 * @return demand bid for requestedTime */
	private Bid prepareHourlyDemandBid(TimeStamp targetTime, BidSchedule schedule) {
		double demandPower = schedule.getScheduledEnergyPurchaseInMWH(targetTime);
		double price = schedule.getScheduledBidInHourInEURperMWH(targetTime);
		Bid demandBid = new Bid(demandPower, price, Double.NaN);
		store(Outputs.OfferedElectricityPriceInEURperMWH, price);
		return demandBid;
	}

	/** Digests award information from {@link DayAheadMarket}, writes dispatch
	 * 
	 * @param messages award information received from {@link DayAheadMarket}
	 * @param contracts none */
	protected void digestAwards(ArrayList<Message> messages, List<Contract> contracts) {
		Message awardMessage = CommUtils.getExactlyOneEntry(messages);
		AwardData award = awardMessage.getDataItemOfType(AwardData.class);

		double awardedEnergyInMWH = award.demandEnergyInMWH;
		double costs = award.powerPriceInEURperMWH * awardedEnergyInMWH;
		lastClearingTime = award.beginOfDeliveryInterval;

		lastProducedHydrogenInMWH = electrolyzer.calcProducedHydrogenOneHour(awardedEnergyInMWH, lastClearingTime);
		strategist.updateProducedHydrogenTotal(lastProducedHydrogenInMWH);

		store(OutputColumns.AwardedEnergyInMWH, awardedEnergyInMWH);
		store(Outputs.ProducedHydrogenInMWH, lastProducedHydrogenInMWH);
		store(FlexibilityTrader.Outputs.VariableCostsInEUR, costs);
	}

	/** Sells hydrogen at fuels market using a "negative purchase" message
	 * 
	 * @param messages none
	 * @param contracts a contract with one {@link FuelsMarket} */
	private void sellProducedHydrogen(ArrayList<Message> messages, List<Contract> contracts) {
		Contract contract = CommUtils.getExactlyOneEntry(contracts);
		FuelBid fuelBid = new FuelBid(lastClearingTime, lastProducedHydrogenInMWH, BidType.Supply, fuelType);
		sendFuelBid(contract, fuelBid);
	}

	/** Evaluate revenues (i.e. negative purchase cost) from selling hydrogen at the fuels market
	 * 
	 * @param messages one AmountAtTime message from fuels market
	 * @param contracts ignored */
	private void digestSaleReturns(ArrayList<Message> messages, List<Contract> contracts) {
		Message message = CommUtils.getExactlyOneEntry(messages);
		double cost = readFuelBillMessage(message);
		store(Outputs.ReceivedMoneyForHydrogenInEUR, -cost);
	}

	@Override
	protected double getInstalledCapacityInMW() {
		return electrolyzer.getPeakPower(now());
	}

	@Override
	protected ElectrolyzerStrategist getStrategist() {
		return strategist;
	}

	@Override
	public HydrogenPolicyRegistration getRegistrationData() {
		return registrationData;
	}

	@Override
	public void saveSupportData(HydrogenSupportData hydrogenSupportData) {
		mpfix = hydrogenSupportData.getPolicyOfType(Mpfix.class);
		strategist.setSupportRateInEURperMWH(mpfix);
	}

	/** Action to send a pay-out request to a single connected {@link HydrogenSupportProvider} based on the last production event */
	private void sendSupportPayoutRequest(ArrayList<Message> __, List<Contract> contracts) {
		Contract contract = CommUtils.getExactlyOneEntry(contracts);
		fulfilNext(contract, new AmountAtTime(lastClearingTime, lastProducedHydrogenInMWH));
	}
}
