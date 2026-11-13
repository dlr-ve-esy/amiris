// SPDX-FileCopyrightText: 2023-2026 German Aerospace Center <amiris@dlr.de>
//
// SPDX-License-Identifier: Apache-2.0
package agents.trader.electrolysis;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import agents.electrolysis.Electrolyzer;
import agents.electrolysis.sensitivity.ElectrolyzerStrategist;
import agents.electrolysis.sensitivity.Strategist;
import agents.flexibility.BidSchedule;
import agents.flexibility.dynamicProgramming.DispatchPlanningError;
import agents.forecast.sensitivity.SensitivityForecastClient;
import agents.forecast.sensitivity.SensitivityForecastProvider;
import agents.markets.DayAheadMarket;
import agents.markets.DayAheadMarketTrader;
import agents.markets.FuelsMarket;
import agents.markets.FuelsTrader;
import agents.markets.meritOrder.Bid;
import agents.policy.hydrogen.HydrogenSupportClient;
import agents.policy.hydrogen.HydrogenSupportProvider;
import agents.policy.hydrogen.Mpfix;
import communications.message.AmountAtTime;
import communications.message.AwardData;
import communications.message.ClearingTimes;
import communications.message.ForecastClientRegistration;
import communications.message.FuelBid;
import communications.message.FuelBid.BidType;
import communications.message.FuelCost;
import communications.message.FuelData;
import communications.message.HydrogenPolicyRegistration;
import communications.message.PointInTime;
import communications.portable.BidsAtTime;
import communications.portable.HydrogenSupportData;
import de.dlr.gitlab.fame.agent.Agent;
import de.dlr.gitlab.fame.agent.input.DataProvider;
import de.dlr.gitlab.fame.agent.input.GroupBuilder;
import de.dlr.gitlab.fame.agent.input.Input;
import de.dlr.gitlab.fame.agent.input.Make;
import de.dlr.gitlab.fame.agent.input.ParameterData.MissingDataException;
import de.dlr.gitlab.fame.communication.CommUtils;
import de.dlr.gitlab.fame.communication.Contract;
import de.dlr.gitlab.fame.communication.message.Message;
import de.dlr.gitlab.fame.service.output.Output;
import de.dlr.gitlab.fame.time.Constants.Interval;
import de.dlr.gitlab.fame.time.TimePeriod;
import de.dlr.gitlab.fame.time.TimeSpan;
import de.dlr.gitlab.fame.time.TimeStamp;

/** A flexible Trader demanding electricity and producing hydrogen from it via electrolysis using sensitivity forecasts to
 * consider competition of flexibility options.
 * 
 * @author Christoph Schimeczek, Johannes Kochems */
public class ElectrolysisTraderSensitivity extends Agent
		implements DayAheadMarketTrader, SensitivityForecastClient, FuelsTrader, HydrogenSupportClient {
	static final String ERR_PLANNING = "Dispatch planning failed for: ";

	static final String GROUP_DEVICE = "Device";
	static final String GROUP_DISPATCH = "Dispatch";
	static final String GROUP_SUPPORT = "Support";

	@Input public static final GroupBuilder parameters = Make.newTree().add(FuelsTrader.fuelTypeParameter)
			.addAs(GROUP_DEVICE, Electrolyzer.parameters)
			.addAs(GROUP_DISPATCH, Strategist.parameters)
			.addAs(GROUP_SUPPORT, HydrogenSupportClient.parameters);

	private static final TimeSpan OPERATION_PERIOD = new TimeSpan(1, Interval.HOURS);

	/** Available output columns */
	@Output
	protected static enum Outputs {
		/** Price at which electricity offers are placed at the day-ahead market */
		OfferedElectricityPriceInEURperMWH,
		/** Amount of hydrogen produced */
		ProducedHydrogenInMWH,
		/** Total received money for selling hydrogen in EUR */
		ReceivedMoneyForHydrogenInEUR,
		DispatchMultiplier,
		OfferedChargePriceInEURperMWH,
		ElectricityPricePredictionInEURperMWH
	}

	private final String fuelType;
	private final FuelData fuelData;
	private HydrogenPolicyRegistration registrationData;
	private BidSchedule bidSchedule;

	/** Electrolyzer device used for hydrogen production */
	protected final Electrolyzer electrolyzer;
	protected final ElectrolyzerStrategist strategist;

	/** Amount of hydrogen produced based on the last electricity price clearing */
	protected double lastProducedHydrogenInMWH = 0;
	/** First TimeStamp of the last electricity price clearing interval */
	protected TimeStamp lastClearingTime;

	/** Creates a new {@link ElectrolysisTraderSensitivity} based on given input parameters
	 * 
	 * @param data configured input
	 * @throws MissingDataException if any required input is missing */
	public ElectrolysisTraderSensitivity(DataProvider data) throws MissingDataException {
		super(data);
		electrolyzer = new Electrolyzer(fromInput().getGroup(GROUP_DEVICE));
		strategist = ElectrolyzerStrategist.newStrategist(fromInput().getGroup(GROUP_DISPATCH), electrolyzer,
				OPERATION_PERIOD);

		fuelType = FuelsTrader.readFuelType(fromInput());
		fuelData = new FuelData(fuelType);

		registrationData = HydrogenSupportClient.getRegistration(fromInput().getOptionalGroup(GROUP_SUPPORT));
		call(this::registerAtForecaster).on(SensitivityForecastClient.Products.ForecastRegistration);
		call(this::requestElectricitySensitivity).on(SensitivityForecastClient.Products.SensitivityRequest)
				.use(DayAheadMarket.Products.GateClosureInfo);
		call(this::updateForecast).onAndUse(SensitivityForecastProvider.Products.SensitivityForecast);
		call(this::requestHydrogenPriceForecast).on(FuelsTrader.Products.FuelPriceForecastRequest)
				.use(DayAheadMarket.Products.GateClosureInfo);
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

	/** Send registration information to {@link SensitivityForecastProvider}
	 * 
	 * @param input none
	 * @param contracts single contract with a {@link SensitivityForecastProvider} */
	private void registerAtForecaster(ArrayList<Message> input, List<Contract> contracts) {
		Contract contract = CommUtils.getExactlyOneEntry(contracts);
		double averagePowerInMW = electrolyzer.getPeakPower(now());
		double energyInMWH = averagePowerInMW * OPERATION_PERIOD.getSteps() / new TimeSpan(1, Interval.HOURS).getSteps();
		fulfilNext(contract, new ForecastClientRegistration(strategist.getSensitivityType(), energyInMWH));
	}

	/** Requests a sensitivity forecast from a contracted SensitivityForecaster.
	 *
	 * @param input one ClearingTimes message from connected {@link DayAheadMarket}
	 * @param contracts single contracted Forecaster to request forecasts from */
	protected void requestElectricitySensitivity(ArrayList<Message> input, List<Contract> contracts) {
		Contract contract = CommUtils.getExactlyOneEntry(contracts);
		TimePeriod nextTime = new TimePeriod(extractTimesFromGateClosureInfoMessages(input).get(0), OPERATION_PERIOD);
		var missingForecastTimes = strategist.getMissingForecastTimes(nextTime);
		for (TimeStamp missingForecastTime : missingForecastTimes) {
			PointInTime pointInTime = new PointInTime(missingForecastTime);
			fulfilNext(contract, pointInTime);
		}
	}

	/** Store input forecasts for dispatch planning
	 * 
	 * @param input to be stored
	 * @param contracts not used */
	protected void updateForecast(ArrayList<Message> input, List<Contract> contracts) {
		strategist.storeForecast(input);
	}

	/** Requests forecast of hydrogen prices from one contracted {@link FuelsMarket}
	 * 
	 * @param input one ClearingTimes message from connected {@link DayAheadMarket}
	 * @param contracts single contracted fuels market to request hydrogen price(s) from */
	private void requestHydrogenPriceForecast(ArrayList<Message> input, List<Contract> contracts) {
		Contract contract = CommUtils.getExactlyOneEntry(contracts);
		TimePeriod nextTime = new TimePeriod(extractTimesFromGateClosureInfoMessages(input).get(0), OPERATION_PERIOD);
		ArrayList<TimeStamp> missingForecastTimes = strategist.getMissingHydrogenPriceForecastTimes(nextTime);
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
			TimePeriod timeSegment = new TimePeriod(priceForecastMessage.validAt, OPERATION_PERIOD);
			strategist.storeHydrogenPriceForecast(timeSegment, priceForecastInEURperThermalMWH);
		}
	}

	/** Prepares and sends Bids to the contracted partner
	 * 
	 * @param input one ClearingTimes message
	 * @param contracts one partner */
	private void prepareBids(ArrayList<Message> input, List<Contract> contracts) {
		Contract contractToFulfil = CommUtils.getExactlyOneEntry(contracts);
		for (TimeStamp targetTime : extractTimesFromGateClosureInfoMessages(input)) {
			excuteBeforeBidPreparation(targetTime);
			Bid demandBid = prepareHourlyDemandBids(targetTime);
			store(OutputColumns.RequestedEnergyInMWH, demandBid.getEnergyAmountInMWH());
			store(Outputs.ElectricityPricePredictionInEURperMWH,
					bidSchedule.getExpectedElectricityPricesInEURperMWH(targetTime));
			fulfilNext(contractToFulfil,
					new BidsAtTime(targetTime, getId(), null, Arrays.asList(demandBid)));
		}
		store(Outputs.DispatchMultiplier, strategist.getMultiplier());
	}

	/** Clears past sensitivities and creates new schedule
	 * 
	 * @param targetTime TimeStamp of bid to prepare */
	private void excuteBeforeBidPreparation(TimeStamp targetTime) {
		if (bidSchedule == null || !bidSchedule.isApplicable(targetTime)) {
			strategist.clearSensitivitiesBefore(now());
			try {
				bidSchedule = strategist.createSchedule(new TimePeriod(targetTime, OPERATION_PERIOD));
			} catch (DispatchPlanningError e) {
				throw new RuntimeException(ERR_PLANNING + this, e);
			}
		}
	}

	/** Prepares hourly demand bid
	 * 
	 * @param requestedTime TimeStamp at which the demand bid should be defined
	 * @return demand bid for requestedTime */
	private Bid prepareHourlyDemandBids(TimeStamp requestedTime) {
		double demandPower = bidSchedule.getScheduledEnergyPurchaseInMWH(requestedTime);
		double price = bidSchedule.getScheduledBidInHourInEURperMWH(requestedTime);
		Bid demandBid = new Bid(demandPower, price, Double.NaN);
		if (demandPower > 0) {
			store(Outputs.OfferedChargePriceInEURperMWH, price);
		}
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
		// TODO: Add new interface AssetOwner that includes AnnualCostReport and Outputs, cf. FlexibilityTrader
		// store(FlexibilityTrader.Outputs.VariableCostsInEUR, costs);
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
	public HydrogenPolicyRegistration getRegistrationData() {
		return registrationData;
	}

	@Override
	public void saveSupportData(HydrogenSupportData hydrogenSupportData) {
		strategist.setSupportRateInEURperMWH(hydrogenSupportData.getPolicyOfType(Mpfix.class));
	}

	/** Action to send a pay-out request to a single connected {@link HydrogenSupportProvider} based on the last production event */
	private void sendSupportPayoutRequest(ArrayList<Message> __, List<Contract> contracts) {
		Contract contract = CommUtils.getExactlyOneEntry(contracts);
		fulfilNext(contract, new AmountAtTime(lastClearingTime, lastProducedHydrogenInMWH));
	}
}
