// SPDX-FileCopyrightText: 2021-2026 German Aerospace Center <amiris@dlr.de>
//
// SPDX-License-Identifier: Apache-2.0
package agents.trader;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import agents.flexibility.BidSchedule;
import agents.flexibility.GenericDevice;
import agents.flexibility.dynamicProgramming.Optimiser;
import agents.flexibility.dynamicProgramming.assessment.AssessmentFunction;
import agents.flexibility.dynamicProgramming.assessment.AssessmentFunctionBuilder;
import agents.flexibility.dynamicProgramming.bidding.BidSchedulerBuilder;
import agents.flexibility.dynamicProgramming.states.StateManager;
import agents.flexibility.dynamicProgramming.states.StateManagerBuilder;
import agents.forecast.sensitivity.SensitivityForecastClient;
import agents.forecast.sensitivity.SensitivityForecastProvider;
import agents.markets.DayAheadMarket;
import agents.markets.DayAheadMarketTrader;
import agents.markets.meritOrder.Bid;
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
import de.dlr.gitlab.fame.time.Constants.Interval;
import de.dlr.gitlab.fame.time.TimePeriod;
import de.dlr.gitlab.fame.time.TimeSpan;
import de.dlr.gitlab.fame.time.TimeStamp;

/** Trader that operates a generic flexibility device, e.g., a battery storage, pumped-hydro storage, load shifting, heat pumps...
 * It can use different dispatch optimisation strategies, optimisation targets, and strategy assessments. Optimisation is based on
 * dynamic programming.
 * 
 * @author Felix Nitsch, Christoph Schimeczek, Johannes Kochems */
public class GenericFlexibilityTrader extends Trader implements SensitivityForecastClient {
	@Input private static final Tree parameters = Make.newTree()
			.addAs("Device", GenericDevice.parameters)
			.addAs("Assessment", AssessmentFunctionBuilder.parameters)
			.addAs("StateDiscretisation", StateManagerBuilder.parameters)
			.addAs("Bidding", BidSchedulerBuilder.parameters)
			.buildTree();

	/** Output columns of {@link GenericFlexibilityTrader}s */
	@Output
	protected static enum Outputs {
		/** Total received money in EUR */
		ReceivedMoneyInEUR,
		OfferedChargePriceInEURperMWH, OfferedDischargePriceInEURperMWH, AwardedChargeEnergyInMWH,
		AwardedDischargeEnergyInMWH, StoredEnergyInMWH, VariableCostsInEUR, DispatchMultiplier,
		/** Assumed electricity price in the next market clearing event based on forecast + flexibility dispatch */
		ElectricityPricePredictionInEURperMWH
	}

	private static final TimeSpan OPERATION_PERIOD = new TimeSpan(1, Interval.HOURS);

	private final GenericDevice device;
	private final AssessmentFunction assessmentFunction;
	private final StateManager stateManager;
	private final Optimiser strategist;
	private BidSchedule bidSchedule;

	/** Instantiate a new {@link GenericFlexibilityTrader}
	 * 
	 * @param dataProvider input from config
	 * @throws MissingDataException if any required data is not provided */
	public GenericFlexibilityTrader(DataProvider dataProvider) throws MissingDataException {
		super(dataProvider);
		ParameterData input = parameters.join(dataProvider);

		device = new GenericDevice(input.getGroup("Device"));
		assessmentFunction = AssessmentFunctionBuilder.build(input.getGroup("Assessment"), device);
		stateManager = StateManagerBuilder.build(device, assessmentFunction, input.getGroup("StateDiscretisation"));
		var bidScheduler = BidSchedulerBuilder.build(input.getGroup("Bidding"));
		strategist = new Optimiser(stateManager, bidScheduler, assessmentFunction.getTargetType());

		call(this::registerAtForecaster).on(SensitivityForecastClient.Products.ForecastRegistration);
		call(this::requestElectricityForecast).on(SensitivityForecastClient.Products.SensitivityRequest)
				.use(DayAheadMarket.Products.GateClosureInfo);
		call(this::updateForecast).onAndUse(SensitivityForecastProvider.Products.SensitivityForecast);
		call(this::prepareBids).on(DayAheadMarketTrader.Products.Bids).use(DayAheadMarket.Products.GateClosureInfo);
		call(this::digestAwards).onAndUse(DayAheadMarket.Products.Awards);
		call(this::sendAward).on(SensitivityForecastClient.Products.NetAward).use(DayAheadMarket.Products.Awards);
	}

	/** Send registration information to {@link SensitivityForecastProvider}
	 * 
	 * @param input none
	 * @param contracts single contract with a {@link SensitivityForecastProvider} */
	private void registerAtForecaster(ArrayList<Message> input, List<Contract> contracts) {
		Contract contract = CommUtils.getExactlyOneEntry(contracts);
		double averagePowerInMW = (device.getExternalChargingPowerInMW(now())
				+ device.getExternalDischargingPowerInMW(now())) / 2.;
		double energyInMWH = averagePowerInMW * OPERATION_PERIOD.getSteps() / new TimeSpan(1, Interval.HOURS).getSteps();
		fulfilNext(contract, new ForecastClientRegistration(assessmentFunction.getSensitivityType(), energyInMWH));
	}

	/** Requests a forecast from a contracted Forecaster.
	 * 
	 * @param input one ClearingTimes message from connected {@link DayAheadMarket}
	 * @param contracts single contracted Forecaster to request forecasts from */
	protected void requestElectricityForecast(ArrayList<Message> input, List<Contract> contracts) {
		Contract contract = CommUtils.getExactlyOneEntry(contracts);
		ClearingTimes clearingTimes = CommUtils.getExactlyOneEntry(input).getDataItemOfType(ClearingTimes.class);
		TimePeriod nextTime = new TimePeriod(clearingTimes.getTimes().get(0), OPERATION_PERIOD);
		var allPlanningTimes = stateManager.getPlanningTimes(nextTime);
		var missingForecastTimes = assessmentFunction.getMissingForecastTimes(allPlanningTimes);
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
		assessmentFunction.storeForecast(input);
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
			double offeredEnergy = supplyBid.getEnergyAmountInMWH() - demandBid.getEnergyAmountInMWH();
			store(OutputColumns.OfferedEnergyInMWH, offeredEnergy);
			store(Outputs.ElectricityPricePredictionInEURperMWH,
					bidSchedule.getExpectedElectricityPricesInEURperMWH(targetTime));
			fulfilNext(contractToFulfil,
					new BidsAtTime(targetTime, getId(), Arrays.asList(supplyBid), Arrays.asList(demandBid)));
		}
		store(Outputs.DispatchMultiplier, assessmentFunction.getMultiplier());
	}

	/** Clears past sensitivities and creates new schedule based on current energy storage level
	 * 
	 * @param targetTime TimeStamp of bid to prepare */
	private void excuteBeforeBidPreparation(TimeStamp targetTime) {
		if (bidSchedule == null || !bidSchedule.isApplicable(targetTime, device.getCurrentInternalEnergyInMWH())) {
			assessmentFunction.clearBefore(now());
			bidSchedule = strategist.createSchedule(new TimePeriod(targetTime, OPERATION_PERIOD));
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

	/** Prepares hourly supply bid
	 * 
	 * @param requestedTime TimeStamp at which the supply bid should be defined
	 * @return supply bid for requestedTime */
	private Bid prepareHourlySupplyBids(TimeStamp requestedTime) {
		double supplyPower = bidSchedule.getScheduledEnergySalesInMWH(requestedTime);
		double price = bidSchedule.getScheduledBidInHourInEURperMWH(requestedTime);
		Bid supplyBid = new Bid(supplyPower, price, Double.NaN);
		if (supplyPower > 0) {
			store(Outputs.OfferedDischargePriceInEURperMWH, price);
		}
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
		double operationalCostInEUR = (awardedChargeEnergyInMWH + awardedDischargeEnergyInMWH)
				* device.getVariableCostInEURperMWH(awards.beginOfDeliveryInterval);
		double prolongingCostInEUR = device.getLastProlongingCostInEUR();
		double costsInEUR = powerPriceInEURperMWH * awardedChargeEnergyInMWH + operationalCostInEUR + prolongingCostInEUR;

		device.transition(awards.beginOfDeliveryInterval, externalEnergyDeltaInMWH, OPERATION_PERIOD);

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
