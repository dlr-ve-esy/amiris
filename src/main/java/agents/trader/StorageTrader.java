// SPDX-FileCopyrightText: 2026 German Aerospace Center <amiris@dlr.de>
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
import agents.forecast.MarketForecaster;
import agents.markets.DayAheadMarket;
import agents.markets.DayAheadMarketTrader;
import agents.markets.meritOrder.Bid;
import agents.markets.meritOrder.Constants;
import agents.storage.Device;
import agents.storage.arbitrageStrategists.ArbitrageStrategist;
import agents.storage.arbitrageStrategists.FileDispatcher;
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

/** Sells and buys energy utilising a Storage {@link Device} at the {@link DayAheadMarket}
 * 
 * @author Christoph Schimeczek, Johannes Kochems, Farzad Sarfarazi, Felix Nitsch */
public class StorageTrader extends FlexibilityTrader {
	@Input private static final Tree parameters = Make.newTree().addAs("Device", Device.parameters.buildTree())
			.addAs("Strategy", ArbitrageStrategist.parameters).buildTree();

	@Output
	private static enum OutputFields {
		OfferedChargePriceInEURperMWH, OfferedDischargePriceInEURperMWH, AwardedChargeEnergyInMWH,
		AwardedDischargeEnergyInMWH, StoredEnergyInMWH
	};

	private final Device storage;
	private final ArbitrageStrategist strategist;
	private BidSchedule schedule;

	/** Creates a {@link StorageTrader}
	 * 
	 * @param dataProvider provides input from config
	 * @throws MissingDataException if any required data is not provided */
	public StorageTrader(DataProvider dataProvider) throws MissingDataException {
		super(dataProvider);
		ParameterData input = parameters.join(dataProvider);
		this.storage = new Device(input.getGroup("Device"));
		this.strategist = ArbitrageStrategist.createStrategist(input.getGroup("Strategy"), storage);

		call(this::prepareForecasts).on(Trader.Products.BidsForecast).use(MarketForecaster.Products.ForecastRequest);
		call(this::requestElectricityForecast).on(DamForecastClient.Products.MeritOrderForecastRequest)
				.use(DayAheadMarket.Products.GateClosureInfo);
		call(this::updateMeritOrderForecast).onAndUse(DamForecastProvider.Products.MeritOrderForecast);
		call(this::requestElectricityForecast).on(DamForecastClient.Products.PriceForecastRequest)
				.use(DayAheadMarket.Products.GateClosureInfo);
		call(this::updateElectricityPriceForecast).onAndUse(DamForecastProvider.Products.PriceForecast);
		call(this::prepareBids).on(DayAheadMarketTrader.Products.Bids).use(DayAheadMarket.Products.GateClosureInfo);
		call(this::digestAwards).onAndUse(DayAheadMarket.Products.Awards);
	}

	/** Prepares forecasts and sends them to the {@link MarketForecaster}; Calling this function will throw an Exception for
	 * Strategists other than {@link FileDispatcher}
	 * 
	 * @param input one ClearingTimes message
	 * @param contracts one partner */
	private void prepareForecasts(ArrayList<Message> input, List<Contract> contracts) {
		Contract contractToFulfil = CommUtils.getExactlyOneEntry(contracts);
		ClearingTimes clearingTimes = CommUtils.getExactlyOneEntry(input).getDataItemOfType(ClearingTimes.class);
		List<TimeStamp> targetTimes = clearingTimes.getTimes();
		for (TimeStamp targetTime : targetTimes) {
			double chargingPowerInMW = strategist.getChargingPowerForecastInMW(targetTime);
			BidsAtTime bids;
			if (chargingPowerInMW > 0) {
				Bid bid = new Bid(chargingPowerInMW, Constants.SCARCITY_PRICE_IN_EUR_PER_MWH, Double.NaN);
				bids = new BidsAtTime(targetTime, getId(), null, Arrays.asList(bid));
			} else {
				Bid bid = new Bid(-chargingPowerInMW, Constants.MINIMAL_PRICE_IN_EUR_PER_MWH, Double.NaN);
				bids = new BidsAtTime(targetTime, getId(), Arrays.asList(bid), null);
			}
			fulfilNext(contractToFulfil, bids);
		}
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
		if (schedule == null || !schedule.isApplicable(targetTime, storage.getCurrentEnergyInStorageInMWH())) {
			strategist.clearSensitivitiesBefore(now());
			TimePeriod targetTimeSegment = new TimePeriod(targetTime, Strategist.OPERATION_PERIOD);
			schedule = strategist.createSchedule(targetTimeSegment);
		}
	}

	/** Prepares hourly demand bid
	 * 
	 * @param requestedTime TimeStamp at which the demand bid should be defined
	 * @return demand bid for requestedTime */
	private Bid prepareHourlyDemandBids(TimeStamp requestedTime) {
		double demandPower = schedule.getScheduledEnergyPurchaseInMWH(requestedTime);
		double price = schedule.getScheduledBidInHourInEURperMWH(requestedTime);
		Bid demandBid = new Bid(demandPower, price, Double.NaN);
		store(OutputFields.OfferedChargePriceInEURperMWH, price);
		return demandBid;
	}

	/** Prepares hourly supply bid
	 * 
	 * @param requestedTime TimeStamp at which the supply bid should be defined
	 * @return supply bid for requestedTime */
	private Bid prepareHourlySupplyBids(TimeStamp requestedTime) {
		double supplyPower = schedule.getScheduledEnergySalesInMWH(requestedTime);
		double price = schedule.getScheduledBidInHourInEURperMWH(requestedTime);
		Bid supplyBid = new Bid(supplyPower, price, Double.NaN);
		store(OutputFields.OfferedDischargePriceInEURperMWH, price);
		return supplyBid;
	}

	/** Digests award information from {@link DayAheadMarket} and writes out award data
	 * 
	 * @param input award information received from {@link DayAheadMarket}
	 * @param contracts not used */
	private void digestAwards(ArrayList<Message> input, List<Contract> contracts) {
		Message awards = CommUtils.getExactlyOneEntry(input);
		double awardedChargePower = awards.getDataItemOfType(AwardData.class).demandEnergyInMWH;
		double awardedDischargePower = awards.getDataItemOfType(AwardData.class).supplyEnergyInMWH;
		double externalPowerDelta = awardedChargePower - awardedDischargePower;
		double powerPrice = awards.getDataItemOfType(AwardData.class).powerPriceInEURperMWH;
		double revenues = powerPrice * awardedDischargePower;
		double costs = powerPrice * awardedChargePower;
		storage.chargeInMW(externalPowerDelta);

		store(OutputFields.AwardedDischargeEnergyInMWH, awardedDischargePower);
		store(OutputFields.AwardedChargeEnergyInMWH, awardedChargePower);
		store(OutputColumns.AwardedEnergyInMWH, externalPowerDelta);
		store(OutputFields.StoredEnergyInMWH, storage.getCurrentEnergyInStorageInMWH());
		store(FlexibilityTrader.Outputs.ReceivedMoneyInEUR, revenues);
		store(FlexibilityTrader.Outputs.VariableCostsInEUR, costs);
	}

	@Override
	protected double getInstalledCapacityInMW() {
		return storage.getInternalPowerInMW();
	}

	@Override
	protected Strategist getStrategist() {
		return strategist;
	}
}