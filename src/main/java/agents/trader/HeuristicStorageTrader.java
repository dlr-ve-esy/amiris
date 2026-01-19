// SPDX-FileCopyrightText: 2026 German Aerospace Center <amiris@dlr.de>
//
// SPDX-License-Identifier: Apache-2.0
package agents.trader;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import agents.flexibility.BidSchedule;
import agents.flexibility.GenericDevice;
import agents.forecast.sensitivity.SensitivityForecastClient;
import agents.forecast.sensitivity.SensitivityForecastProvider;
import agents.forecast.sensitivity.SensitivityForecastProvider.ForecastType;
import agents.markets.DayAheadMarket;
import agents.markets.DayAheadMarketTrader;
import agents.markets.meritOrder.Bid;
import agents.storage.arbitrageStrategists.HeuristicMedian;
import communications.message.AmountAtTime;
import communications.message.AwardData;
import communications.message.ClearingTimes;
import communications.message.ForecastClientRegistration;
import communications.message.PointInTime;
import communications.portable.BidsAtTime;
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
import de.dlr.gitlab.fame.time.Constants;
import de.dlr.gitlab.fame.time.Constants.Interval;
import de.dlr.gitlab.fame.time.TimePeriod;
import de.dlr.gitlab.fame.time.TimeSpan;
import de.dlr.gitlab.fame.time.TimeStamp;
import util.Polynomial;

/** A Trader that operates a {@link GenericDevice} and uses a heuristic dispatch strategy based on sensitivity forecasts
 * 
 * @author Christoph Schimeczek */
public class HeuristicStorageTrader extends Trader implements SensitivityForecastClient {
	static final String GROUP_DEVICE = "Device";
	static final String PARAM_PERIOD = "OperationPeriodInHours";
	static final String PARAM_HORIZON = "PlanningHorizonInHours";
	static final String PARAM_SCHEDULE = "SchedulingHorizonInHours";
	static final String PARAM_PREFACTORS = "AssessmentFunctionPrefactors";

	@Input private static final Tree parameters = Make.newTree()
			.addAs(GROUP_DEVICE, GenericDevice.parameters)
			.add(Make.newDouble(PARAM_PERIOD)).add(Make.newDouble(PARAM_HORIZON)).add(Make.newDouble(PARAM_SCHEDULE))
			.add(Make.newDouble(PARAM_PREFACTORS).list()).buildTree();

	/** Output columns of {@link HeuristicStorageTrader} */
	@Output
	protected static enum Outputs {
		ReceivedMoneyInEUR,
		OfferedChargePriceInEURperMWH, OfferedDischargePriceInEURperMWH, AwardedChargeEnergyInMWH,
		AwardedDischargeEnergyInMWH, StoredEnergyInMWH, VariableCostsInEUR
	}

	private final TimeSpan operationPeriod;
	private final GenericDevice device;
	private final HeuristicMedian strategy;
	private BidSchedule bidSchedule;

	/** Instantiates a {@link HeuristicStorageTrader}
	 * 
	 * @param dataProvider input from config
	 * @throws MissingDataException if any required data is not provided */
	public HeuristicStorageTrader(DataProvider dataProvider) throws MissingDataException {
		super(dataProvider);
		ParameterData input = parameters.join(dataProvider);
		device = new GenericDevice(input.getGroup(GROUP_DEVICE));
		operationPeriod = new TimeSpan(Math.round(input.getDouble(PARAM_PERIOD) * Constants.STEPS_PER_HOUR));
		strategy = createStrategist(input);

		call(this::registerAtForecaster).on(SensitivityForecastClient.Products.ForecastRegistration);
		call(this::requestElectricityForecast).on(SensitivityForecastClient.Products.SensitivityRequest)
				.use(DayAheadMarket.Products.GateClosureInfo);
		call(this::updateForecast).onAndUse(SensitivityForecastProvider.Products.SensitivityForecast);
		call(this::prepareBids).on(DayAheadMarketTrader.Products.Bids).use(DayAheadMarket.Products.GateClosureInfo);
		call(this::digestAwards).onAndUse(DayAheadMarket.Products.Awards);
		call(this::sendAward).on(SensitivityForecastClient.Products.NetAward).use(DayAheadMarket.Products.Awards);
	}

	/** @return {@link HeuristicMedian} strategist created from input */
	private HeuristicMedian createStrategist(ParameterData input) throws MissingDataException {
		Polynomial polynomial = new Polynomial(input.getList(PARAM_PREFACTORS, Double.class));
		double forecastHorizonInHours = input.getDouble(PARAM_HORIZON) * Constants.STEPS_PER_HOUR;
		int forecastPeriods = (int) Math.round(forecastHorizonInHours / operationPeriod.getSteps());
		double scheduleDuration = input.getDouble(PARAM_SCHEDULE) * Constants.STEPS_PER_HOUR;
		int schedulePeriods = (int) Math.round(scheduleDuration / operationPeriod.getSteps());
		return new HeuristicMedian(polynomial, device, forecastPeriods, schedulePeriods);
	}

	/** Send registration information to {@link SensitivityForecastProvider}
	 * 
	 * @param input none
	 * @param contracts single contract with a {@link SensitivityForecastProvider} */
	private void registerAtForecaster(ArrayList<Message> input, List<Contract> contracts) {
		Contract contract = CommUtils.getExactlyOneEntry(contracts);
		double averagePowerInMW = (device.getExternalChargingPowerInMW(now())
				+ device.getExternalDischargingPowerInMW(now())) / 2.;
		double energyInMWH = averagePowerInMW * operationPeriod.getSteps() / new TimeSpan(1, Interval.HOURS).getSteps();
		fulfilNext(contract, new ForecastClientRegistration(ForecastType.CostInsensitive, energyInMWH));
	}

	/** Requests a forecast from a contracted Forecaster.
	 * 
	 * @param input one ClearingTimes message from connected {@link DayAheadMarket}
	 * @param contracts single contracted Forecaster to request forecasts from */
	protected void requestElectricityForecast(ArrayList<Message> input, List<Contract> contracts) {
		Contract contract = CommUtils.getExactlyOneEntry(contracts);
		ClearingTimes clearingTimes = CommUtils.getExactlyOneEntry(input).getDataItemOfType(ClearingTimes.class);
		TimePeriod nextTime = new TimePeriod(clearingTimes.getTimes().get(0), operationPeriod);
		var missingForecastTimes = strategy.getMissingForecastTimes(nextTime);
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
		strategy.storeForecast(input);
	}

	/** Prepares and sends Bids to the contracted partner
	 * 
	 * @param input one ClearingTimes message
	 * @param contracts one partner */
	private void prepareBids(ArrayList<Message> input, List<Contract> contracts) {
		Contract contractToFulfil = CommUtils.getExactlyOneEntry(contracts);
		ClearingTimes clearingTimes = CommUtils.getExactlyOneEntry(input).getDataItemOfType(ClearingTimes.class);
		List<TimeStamp> targetTimes = clearingTimes.getTimes();
		for (TimeStamp targetTime : targetTimes) {
			excuteBeforeBidPreparation(targetTime);
			Bid demandBid = prepareHourlyDemandBids(targetTime);
			Bid supplyBid = prepareHourlySupplyBids(targetTime);
			store(OutputColumns.OfferedEnergyInMWH, supplyBid.getEnergyAmountInMWH() - demandBid.getEnergyAmountInMWH());
			fulfilNext(contractToFulfil,
					new BidsAtTime(targetTime, getId(), Arrays.asList(supplyBid), Arrays.asList(demandBid)));
		}
	}

	/** Clears past sensitivities and creates new schedule based on current energy storage level
	 * 
	 * @param targetTime TimeStamp of bid to prepare */
	private void excuteBeforeBidPreparation(TimeStamp targetTime) {
		if (bidSchedule == null || !bidSchedule.isApplicable(targetTime, device.getCurrentInternalEnergyInMWH())) {
			strategy.clearBefore(now());
			bidSchedule = strategy.createSchedule(new TimePeriod(targetTime, operationPeriod));
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
		store(Outputs.OfferedChargePriceInEURperMWH, price);
		return demandBid;
	}

	/** Prepares hourly supply bid
	 * 
	 * @param requestedTime TimeStamp at which the supply bid should be defined
	 * @return supply bid for requestedTime */
	private Bid prepareHourlySupplyBids(TimeStamp requestedTime) {
		double supplyPower = bidSchedule.getScheduledEnergySalesInMWH(requestedTime);
		double price = bidSchedule.getScheduledBidInHourInEURperMWH(requestedTime);
		Bid supplyBid = new Bid(supplyPower, price, Double.NaN);
		store(Outputs.OfferedDischargePriceInEURperMWH, price);
		return supplyBid;
	}

	/** Digests award information from {@link DayAheadMarket} and writes out award data
	 * 
	 * @param input award information received from {@link DayAheadMarket}
	 * @param contracts not used */
	private void digestAwards(ArrayList<Message> input, List<Contract> contracts) {
		AwardData awards = CommUtils.getExactlyOneEntry(input).getDataItemOfType(AwardData.class);
		double awardedChargeEnergyInMWH = awards.demandEnergyInMWH;
		double awardedDischargeEnergyInMWH = awards.supplyEnergyInMWH;
		double externalEnergyDeltaInMWH = awardedChargeEnergyInMWH - awardedDischargeEnergyInMWH;
		double powerPriceInEURperMWH = awards.powerPriceInEURperMWH;
		double revenuesInEUR = powerPriceInEURperMWH * awardedDischargeEnergyInMWH;
		double costsInEUR = powerPriceInEURperMWH * awardedChargeEnergyInMWH;

		device.transition(awards.beginOfDeliveryInterval, externalEnergyDeltaInMWH, operationPeriod);

		store(Outputs.AwardedDischargeEnergyInMWH, awardedDischargeEnergyInMWH);
		store(Outputs.AwardedChargeEnergyInMWH, awardedChargeEnergyInMWH);
		store(OutputColumns.AwardedEnergyInMWH, externalEnergyDeltaInMWH);
		store(Outputs.StoredEnergyInMWH, device.getCurrentInternalEnergyInMWH());
		store(Outputs.ReceivedMoneyInEUR, revenuesInEUR);
		store(Outputs.VariableCostsInEUR, costsInEUR);
	}

	/** Send award total to {@link SensitivityForecastProvider}
	 * 
	 * @param input award information received from {@link DayAheadMarket}
	 * @param contracts single contract with {@link SensitivityForecastProvider} */
	private void sendAward(ArrayList<Message> input, List<Contract> contracts) {
		AwardData awards = CommUtils.getExactlyOneEntry(input).getDataItemOfType(AwardData.class);
		Contract contract = CommUtils.getExactlyOneEntry(contracts);
		double effectiveSupplyPower = awards.supplyEnergyInMWH - awards.demandEnergyInMWH;
		fulfilNext(contract, new AmountAtTime(awards.beginOfDeliveryInterval, effectiveSupplyPower));
	}
}