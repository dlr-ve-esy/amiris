// SPDX-FileCopyrightText: 2021-2026 German Aerospace Center <amiris@dlr.de>
//
// SPDX-License-Identifier: Apache-2.0
package agents.trader;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import agents.flexibility.BidSchedule;
import agents.flexibility.Strategist;
import agents.forecast.DamForecastClient;
import agents.forecast.DamForecastProvider;
import agents.loadShifting.LoadShiftingPortfolio;
import agents.loadShifting.strategists.LoadShiftingStrategist;
import agents.markets.DayAheadMarket;
import agents.markets.DayAheadMarketTrader;
import agents.markets.meritOrder.Bid;
import communications.message.AwardData;
import communications.message.ClearingTimes;
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
import de.dlr.gitlab.fame.time.TimePeriod;
import de.dlr.gitlab.fame.time.TimeStamp;
import endUser.EndUserTariff;

/** Offers load adjustment possibilities of a {@link LoadShiftingPortfolio} at the energy exchange
 * 
 * @author Johannes Kochems, Christoph Schimeczek */
public class LoadShiftingTrader extends FlexibilityTrader {
	@Input private static final Tree parameters = Make.newTree()
			.addAs("LoadShiftingPortfolio", LoadShiftingPortfolio.parameters)
			.addAs("Strategy", LoadShiftingStrategist.parameters)
			.addAs("Policy", EndUserTariff.policyParameters.buildTree())
			.addAs("BusinessModel", EndUserTariff.businessModelParameters).buildTree();

	@Output
	private static enum OutputFields {
		OfferedUpshiftPowerInMW, OfferedDownshiftPowerInMW, OfferedPriceInEURperMWH, AwardedUpshiftPowerInMW,
		AwardedDownshiftPowerInMW, NetAwardedPowerInMW, StoredMWH, CurrentShiftTimeInH, RevenuesInEUR, CostsInEUR,
		VariableShiftingCostsInEUR, ProfitInEUR, VariableShiftingCostsFromOptimiserInEUR
	}

	private final LoadShiftingPortfolio portfolio;
	private final LoadShiftingStrategist strategist;
	private BidSchedule schedule;
	/** An entity holding consumer tariff information */
	private final EndUserTariff endUserTariff;

	/** Instantiate new {@link LoadShiftingTrader}
	 * 
	 * @param dataProvider provides input from config
	 * @throws MissingDataException if any required data is missing */
	public LoadShiftingTrader(DataProvider dataProvider) throws MissingDataException {
		super(dataProvider);
		ParameterData input = parameters.join(dataProvider);
		this.portfolio = new LoadShiftingPortfolio(input.getGroup("LoadShiftingPortfolio"));
		this.endUserTariff = new EndUserTariff(input.getGroup("Policy"), input.getGroup("BusinessModel"));
		this.strategist = LoadShiftingStrategist.createStrategist(input.getGroup("Strategy"), endUserTariff, portfolio);

		call(this::requestElectricityForecast).on(DamForecastClient.Products.MeritOrderForecastRequest)
				.use(DayAheadMarket.Products.GateClosureInfo);
		call(this::updateMeritOrderForecast).onAndUse(DamForecastProvider.Products.MeritOrderForecast);
		call(this::requestElectricityForecast).on(DamForecastClient.Products.PriceForecastRequest)
				.use(DayAheadMarket.Products.GateClosureInfo);
		call(this::updateElectricityPriceForecast).onAndUse(DamForecastProvider.Products.PriceForecast);
		call(this::prepareBids).on(DayAheadMarketTrader.Products.Bids).use(DayAheadMarket.Products.GateClosureInfo);
		call(this::digestAwards).on(DayAheadMarket.Products.Awards).use(DayAheadMarket.Products.Awards);
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
			Bid demandBid = prepareHourlyDemandBid(targetTime);
			Bid supplyBid = prepareHourlySupplyBid(targetTime);
			fulfilNext(contractToFulfil,
					new BidsAtTime(targetTime, getId(), Arrays.asList(supplyBid), Arrays.asList(demandBid)));
		}
	}

	private void excuteBeforeBidPreparation(TimeStamp targetTime) {
		if (schedule == null
				|| !schedule.isApplicable(targetTime, portfolio.getCurrentEnergyShiftStorageLevelInMWH())) {
			strategist.clearSensitivitiesBefore(now());
			TimePeriod targetTimeSegment = new TimePeriod(targetTime, Strategist.OPERATION_PERIOD);
			schedule = strategist.createSchedule(targetTimeSegment,
					portfolio.getCurrentEnergyShiftStorageLevelInMWH(),
					portfolio.getCurrentShiftTimeInHours());
			store(OutputFields.VariableShiftingCostsFromOptimiserInEUR, strategist.getVariableShiftingCostsFromOptimiser());
		}
	}

	private Bid prepareHourlyDemandBid(TimeStamp requestedTime) {
		double demandPower = schedule.getScheduledEnergyPurchaseInMWH(requestedTime);
		double price = schedule.getScheduledBidInHourInEURperMWH(requestedTime);
		Bid demandBid = new Bid(demandPower, price, Double.NaN);
		store(OutputFields.OfferedUpshiftPowerInMW, demandPower);
		store(OutputFields.OfferedPriceInEURperMWH, price);
		return demandBid;
	}

	private Bid prepareHourlySupplyBid(TimeStamp requestedTime) {
		double supplyPower = schedule.getScheduledEnergySalesInMWH(requestedTime);
		double price = schedule.getScheduledBidInHourInEURperMWH(requestedTime);
		double marginalShiftingCost = portfolio.getVariableShiftCostsInEURPerMWH(requestedTime);
		Bid supplyBid = new Bid(supplyPower, price, marginalShiftingCost);
		store(OutputFields.OfferedDownshiftPowerInMW, -supplyPower);
		return supplyBid;
	}

	private void digestAwards(ArrayList<Message> input, List<Contract> contracts) {
		AwardData award = CommUtils.getExactlyOneEntry(input).getDataItemOfType(AwardData.class);
		double awardedChargePower = award.demandEnergyInMWH;
		double awardedDischargePower = award.supplyEnergyInMWH;
		double powerDelta = awardedChargePower - awardedDischargePower;
		double powerPrice = award.powerPriceInEURperMWH;
		double revenues = powerPrice * awardedDischargePower;
		double costs = powerPrice * awardedChargePower;
		TimeStamp deliveryTime = award.beginOfDeliveryInterval;
		double variableShiftingCosts = portfolio.getVariableShiftCostsInEURPerMWH(deliveryTime)
				* (awardedChargePower + awardedDischargePower) + portfolio.getProlongingCosts(powerDelta, deliveryTime);
		costs += variableShiftingCosts;
		portfolio.updateEnergyShiftStorageLevelAndShiftTime(powerDelta);
		store(OutputFields.AwardedUpshiftPowerInMW, awardedChargePower);
		store(OutputFields.AwardedDownshiftPowerInMW, -awardedDischargePower);
		store(OutputFields.NetAwardedPowerInMW, powerDelta);
		store(OutputFields.StoredMWH, portfolio.getCurrentEnergyShiftStorageLevelInMWH());
		store(OutputFields.CurrentShiftTimeInH, portfolio.getCurrentShiftTimeInHours());
		store(OutputFields.RevenuesInEUR, revenues);
		store(OutputFields.CostsInEUR, costs);
		store(OutputFields.VariableShiftingCostsInEUR, variableShiftingCosts);
		store(OutputFields.ProfitInEUR, revenues - costs);
	}

	@Override
	protected double getInstalledCapacityInMW() {
		return portfolio.getPowerInMW();
	}

	@Override
	protected Strategist getStrategist() {
		return strategist;
	}
}
