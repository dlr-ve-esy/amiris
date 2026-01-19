// SPDX-FileCopyrightText: 2026 German Aerospace Center <amiris@dlr.de>
//
// SPDX-License-Identifier: Apache-2.0
package agents.trader;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import agents.flexibility.Strategist;
import agents.forecast.DamForecastClient;
import agents.forecast.DamForecastProvider;
import agents.markets.DayAheadMarket;
import agents.markets.DayAheadMarketTrader;
import agents.markets.meritOrder.Bid;
import agents.markets.meritOrder.Constants;
import agents.storage.Device;
import agents.storage.arbitrageStrategists.PvBiddingStrategist;
import communications.message.AmountAtTime;
import communications.message.AwardData;
import communications.message.ClearingTimes;
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
import de.dlr.gitlab.fame.data.TimeSeries;
import de.dlr.gitlab.fame.service.output.Output;
import de.dlr.gitlab.fame.time.TimePeriod;
import de.dlr.gitlab.fame.time.TimeStamp;
import endUser.EndUserTariff;

/** This agent sells and buys electricity at the EnergyExchange. It models the aggregated behaviours of a cluster of households
 * with PV and battery storage. The actual agent behaviour is not calculated but rather predicted via requesting a pre-trained ML
 * model.
 * 
 * @author A. Achraf El Ghazi, Christoph Schimeczek, Ulrich Frey */
public class HouseholdPvTraderExternal extends FlexibilityTrader {
	@Input private static final Tree parameters = Make.newTree()
			.add(Make.newDouble("InstalledGenerationPowerInMW"),
					Make.newSeries("LoadInMW"),
					Make.newSeries("GenerationProfile"),
					Make.newString("ServiceUrl"),
					Make.newString("ModelId").optional(),
					Make.newInt("ForecastPeriodInHours"))
			.addAs("PredictionWindows", PvBiddingStrategist.parameters)
			.addAs("Device", Device.parameters.buildTree())
			.addAs("Policy", EndUserTariff.policyParameters.buildTree())
			.addAs("BusinessModel", EndUserTariff.businessModelParameters)
			.buildTree();

	@Output
	private static enum OutputFields {
		/** Amount of energy awarded in MWh to be delivered to households from the grid */
		AwardedDemandInMWh,
		/** Amount of energy awarded in MWh to be provided by households to the grid */
		AwardedSupplyInMWh,
	}

	private PvBiddingStrategist biddingStrategist;
	private EndUserTariff tariffStrategist;

	/** Creates a {@link HouseholdPvTraderExternal} based on given input parameters
	 * 
	 * @param dataProvider configured input
	 * @throws MissingDataException if any required data is not provided */
	public HouseholdPvTraderExternal(DataProvider dataProvider) throws MissingDataException {
		super(dataProvider);
		ParameterData input = parameters.join(dataProvider);
		double installedGenerationPowerInMW = input.getDouble("InstalledGenerationPowerInMW");
		TimeSeries tsLoadInMW = input.getTimeSeries("LoadInMW");
		TimeSeries tsGenerationProfile = input.getTimeSeries("GenerationProfile");
		String serviceURL = input.getString("ServiceUrl");
		String modelId = input.getString("ModelId");
		int forecastPeriodInHours = input.getInteger("ForecastPeriodInHours");
		Device storage = new Device(input.getGroup("Device"));
		biddingStrategist = new PvBiddingStrategist(serviceURL, modelId, installedGenerationPowerInMW, tsLoadInMW,
				tsGenerationProfile, storage, forecastPeriodInHours, input.getGroup("PredictionWindows"));
		tariffStrategist = new EndUserTariff(input.getGroup("Policy"), input.getGroup("BusinessModel"));

		call(this::requestPriceForecast).on(DamForecastClient.Products.PriceForecastRequest)
				.use(DayAheadMarket.Products.GateClosureInfo);
		call(this::updatePriceForecast).onAndUse(DamForecastProvider.Products.PriceForecast);
		call(this::prepareBids).on(DayAheadMarketTrader.Products.Bids).use(DayAheadMarket.Products.GateClosureInfo);
		call(this::digestAwards).onAndUse(DayAheadMarket.Products.Awards);
	}

	/** Requests PriceForecast from contracted partner (Forecaster)
	 * 
	 * @param input not used
	 * @param contracts single contracted Forecaster to request forecast from */
	private void requestPriceForecast(ArrayList<Message> input, List<Contract> contracts) {
		Contract contract = CommUtils.getExactlyOneEntry(contracts);
		ClearingTimes clearingTimes = CommUtils.getExactlyOneEntry(input).getDataItemOfType(ClearingTimes.class);
		TimePeriod nextTime = new TimePeriod(clearingTimes.getTimes().get(0), Strategist.OPERATION_PERIOD);
		ArrayList<TimeStamp> missingForecastTimes = biddingStrategist.getTimesMissingForecasts(nextTime);
		for (TimeStamp missingForecastTime : missingForecastTimes) {
			PointInTime pointInTime = new PointInTime(missingForecastTime);
			fulfilNext(contract, pointInTime);
		}
	}

	/** Digests incoming price forecasts
	 * 
	 * @param input one or multiple price forecast message(s)
	 * @param contracts not used */
	private void updatePriceForecast(ArrayList<Message> input, List<Contract> contracts) {
		for (Message inputMessage : input) {
			AmountAtTime forecast = inputMessage.getDataItemOfType(AmountAtTime.class);
			biddingStrategist.storeElectricityPriceForecast(forecast.validAt,
					tariffStrategist.calcSalePriceInEURperMWH(forecast.amount, forecast.validAt));
		}
	}

	/** Prepares and sends Bids to the contracted partner
	 * 
	 * @param input one ClearingTimes message
	 * @param contracts one partner */
	private void prepareBids(ArrayList<Message> input, List<Contract> contracts) {
		Contract contractToFulfil = CommUtils.getExactlyOneEntry(contracts);
		ClearingTimes clearingTimes = CommUtils.getExactlyOneEntry(input).getDataItemOfType(ClearingTimes.class);
		TimeStamp targetTime = CommUtils.getExactlyOneEntry(clearingTimes.getTimes());
		double actualLoad = biddingStrategist.getNetLoadPredictionInMWH(targetTime);
		List<Bid> supplyBids = getSupplyBids(actualLoad);
		List<Bid> demandBids = getDemandBids(actualLoad);
		fulfilNext(contractToFulfil, new BidsAtTime(targetTime, getId(), supplyBids, demandBids));
	}

	/** @return if given load is negative: single supply bid wrapped in a list; empty list otherwise */
	private List<Bid> getSupplyBids(double actualLoad) {
		if (actualLoad > 0) {
			return Collections.emptyList();
		} else {
			store(OutputColumns.OfferedEnergyInMWH, Math.abs(actualLoad));
			return Arrays.asList(new Bid(Math.abs(actualLoad), Constants.MINIMAL_PRICE_IN_EUR_PER_MWH, Double.NaN));
		}
	}

	/** @return if given load is positive: single demand bid wrapped in a list; empty list otherwise */
	private List<Bid> getDemandBids(double actualLoad) {
		if (actualLoad > 0) {
			store(OutputColumns.RequestedEnergyInMWH, actualLoad);
			return Arrays.asList(new Bid(actualLoad, Constants.SCARCITY_PRICE_IN_EUR_PER_MWH, Double.NaN));
		} else {
			return Collections.emptyList();
		}
	}

	/** Digests award information from {@link EnergyExchange} and writes out award data
	 * 
	 * @param input award information received from {@link EnergyExchange}
	 * @param contracts not used */
	private void digestAwards(ArrayList<Message> input, List<Contract> contracts) {
		Message awards = CommUtils.getExactlyOneEntry(input);
		AwardData awardData = awards.getDataItemOfType(AwardData.class);
		biddingStrategist.updateStorage(awardData);
		biddingStrategist.updateLoadHistory(awardData);

		store(OutputFields.AwardedDemandInMWh, awardData.demandEnergyInMWH);
		store(OutputFields.AwardedSupplyInMWh, awardData.supplyEnergyInMWH);
	}

	@Override
	protected double getInstalledCapacityInMW() {
		return 0;
	}

	@Override
	protected Strategist getStrategist() {
		return null;
	}
}
