// SPDX-FileCopyrightText: 2026 German Aerospace Center <amiris@dlr.de>
//
// SPDX-License-Identifier: Apache-2.0
package agents.trader;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import agents.flexibility.Strategist;
import agents.forecast.DamForecastClient;
import agents.forecast.DamForecastProvider;
import agents.heatPump.BuildingParameters;
import agents.heatPump.HeatPump;
import agents.heatPump.HeatPumpSchedule;
import agents.heatPump.HeatingInputData;
import agents.heatPump.StrategyParameters;
import agents.heatPump.ThermalResponse;
import agents.heatPump.strategists.HeatPumpStrategist;
import agents.heatPump.strategists.HeatPumpStrategist.HeatPumpStrategistType;
import agents.heatPump.strategists.StrategistExternal;
import agents.heatPump.strategists.StrategistInflexibleFile;
import agents.heatPump.strategists.StrategistInflexibleRC;
import agents.heatPump.strategists.StrategistMinCostFile;
import agents.heatPump.strategists.StrategistMinCostRC;
import agents.markets.DayAheadMarket;
import agents.markets.DayAheadMarketTrader;
import agents.markets.meritOrder.Bid;
import agents.markets.meritOrder.Constants;
import agents.storage.Device;
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
import de.dlr.gitlab.fame.time.Constants.Interval;
import de.dlr.gitlab.fame.time.TimePeriod;
import de.dlr.gitlab.fame.time.TimeSpan;
import de.dlr.gitlab.fame.time.TimeStamp;
import endUser.EndUserTariff;

/** Buys electricity for a portfolio of heat pumps at the EnergyExchange
 * 
 * @author Evelyn Sperber, Christoph Schimeczek */
public class HeatPumpTrader extends FlexibilityTrader {
	@Input private static final Tree parameters = Make.newTree().addAs("Device", Device.parameters.optional().buildTree())
			.addAs("StrategyBasic", HeatPumpStrategist.parameters)
			.addAs("HeatingInputData", HeatingInputData.parameters).addAs("HeatPump", HeatPump.parameters)
			.addAs("Strategy", StrategyParameters.parameters).addAs("Building", BuildingParameters.parameters)
			.addAs("Policy", EndUserTariff.policyParameters.optional().buildTree())
			.addAs("BusinessModel", EndUserTariff.businessModelParameters).buildTree();

	@Output
	private static enum OutputFields {
		COP, FinalRoomTemperatureInCelsius, StoredEnergyInMWH
	};

	private final TimeSpan operationPeriod = new TimeSpan(1, Interval.HOURS);

	private final ThermalResponse building;
	private final HeatPumpStrategist strategist;
	private final HeatPump heatPump;
	private final Device device;
	private HeatPumpSchedule schedule;
	private HeatPumpStrategistType strategistType;
	private EndUserTariff tariffStrategist;

	/** Creates a {@link HeatPumpTrader}
	 * 
	 * @param dataProvider provides input from config
	 * @throws MissingDataException if any required data is not provided */
	public HeatPumpTrader(DataProvider dataProvider) throws MissingDataException {
		super(dataProvider);
		ParameterData input = parameters.join(dataProvider);

		BuildingParameters buildingParams = new BuildingParameters(input.getGroup("Building"));
		StrategyParameters strategyParams = new StrategyParameters(input.getGroup("Strategy"));
		HeatingInputData heatingData = new HeatingInputData(input.getGroup("HeatingInputData"));
		tariffStrategist = new EndUserTariff(input.getGroup("Policy"), input.getGroup("BusinessModel"));
		device = new Device(input.getGroup("Device"));
		strategistType = strategyParams.getHeatPumpStrategistType();

		double initialRoomTemperatureInC = (strategyParams.getMinimalRoomTemperatureInC()
				+ strategyParams.getMaximalRoomTemperatureInC()) / 2;
		heatPump = new HeatPump(input.getGroup("HeatPump"));
		building = new ThermalResponse(buildingParams, heatPump, initialRoomTemperatureInC);
		ParameterData strategyBasic = input.getGroup("StrategyBasic");
		strategist = createStrategist(strategyBasic, building, heatPump, heatingData, strategyParams, device);

		call(this::requestElectricityForecast).on(DamForecastClient.Products.MeritOrderForecastRequest)
				.use(DayAheadMarket.Products.GateClosureInfo);
		call(this::updateMeritOrderForecast).onAndUse(DamForecastProvider.Products.MeritOrderForecast);
		call(this::requestElectricityForecast).on(DamForecastClient.Products.PriceForecastRequest)
				.use(DayAheadMarket.Products.GateClosureInfo);
		call(this::updateElectricityPriceForecast).onAndUse(DamForecastProvider.Products.PriceForecast);
		call(this::prepareBids).on(DayAheadMarketTrader.Products.Bids).use(DayAheadMarket.Products.GateClosureInfo);
		call(this::digestAwards).onAndUse(DayAheadMarket.Products.Awards);
	}

	/** Creates a heat pump strategist
	 * 
	 * @param strategyBasic input data from config
	 * @param building associated building
	 * @param heatPump associated heatPump
	 * @param heatPumpParams input data from config
	 * @param heatingData input data from config
	 * @param buildingParams input data from config
	 * @param strategyParams input data from config
	 * @param device associated device
	 * @return newly instantiated {@link HeatPumpStrategist} based on the given input
	 * @throws MissingDataException if any required data is not provided */
	private HeatPumpStrategist createStrategist(ParameterData strategyBasic, ThermalResponse building,
			HeatPump heatPump, HeatingInputData heatingData, StrategyParameters strategyParams, Device device)
			throws MissingDataException {
		switch (strategyParams.getHeatPumpStrategistType()) {
			case MIN_COST_RC:
				return new StrategistMinCostRC(strategyBasic, building, heatPump, device, heatingData,
						heatPump.getInstalledUnits(), strategyParams);
			case INFLEXIBLE_RC:
				return new StrategistInflexibleRC(strategyBasic, building, heatPump, device, heatingData,
						heatPump.getInstalledUnits(), strategyParams);
			case MIN_COST_FILE:
				return new StrategistMinCostFile(strategyBasic, heatPump, device, heatingData,
						heatPump.getHeatPumpPenetrationFactor(), heatPump.getInstalledUnits(), strategyParams);
			case INFLEXIBLE_FILE:
				return new StrategistInflexibleFile(strategyBasic, heatPump, device, heatingData,
						heatPump.getHeatPumpPenetrationFactor(), heatPump.getInstalledUnits(), strategyParams,
						building.getCurrentRoomTemperatureInC());
			case EXTERNAL:
				return new StrategistExternal(strategyBasic, heatPump, device, heatingData,
						heatPump.getInstalledUnits(), strategyParams, tariffStrategist,
						building.getCurrentRoomTemperatureInC());
			default:
				throw new RuntimeException("Heat Pump Strategist not implemented.");
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
			Bid demandBid = prepareHourlyDemandBid(targetTime);
			fulfilNext(contractToFulfil, new BidsAtTime(targetTime, getId(), null, Arrays.asList(demandBid)));
		}
	}

	/** Clears past sensitivities and creates new schedule based on current temperature in building
	 * 
	 * @param targetTime TimeStamp of bid to prepare */
	private void excuteBeforeBidPreparation(TimeStamp targetTime) {
		if (schedule == null || !schedule.isApplicable(targetTime, building.getCurrentRoomTemperatureInC())) {
			strategist.clearSensitivitiesBefore(now());
			TimePeriod targetTimeSegment = new TimePeriod(targetTime, operationPeriod);
			schedule = strategist.createSchedule(targetTimeSegment);
		}
	}

	/** Prepares hourly demand bid
	 * 
	 * @param targetTime TimeStamp at which the demand bid should be defined
	 * @return demand bid for requestedTime */
	private Bid prepareHourlyDemandBid(TimeStamp targetTime) {
		Bid demandBid;
		switch (strategistType) {
			case MIN_COST_FILE:
				TimePeriod targetTimeSegment = new TimePeriod(targetTime, operationPeriod);
				double energyBalance = calcEnergyBalanceInPeriod(targetTimeSegment);
				demandBid = new Bid(energyBalance, Constants.SCARCITY_PRICE_IN_EUR_PER_MWH, Double.NaN);
				break;
			default:
				double demandPower = schedule.getScheduledEnergyPurchaseInMWH(targetTime);
				demandBid = new Bid(demandPower, Constants.SCARCITY_PRICE_IN_EUR_PER_MWH, Double.NaN);
		}
		return demandBid;
	}

	/** @return total energy balance (&lt; 0: feed in to larger system, &gt; 0: withdrawal from larger system) in given period
	 * @param currentTimeSegment to calculate the energy balance at */
	public double calcEnergyBalanceInPeriod(TimePeriod currentTimeSegment) {
		double ambientTemperatureInC = strategist.getAmbientTemperatureInC(currentTimeSegment);
		double coefficientOfPerformance = heatPump.calcCoefficientOfPerformance(ambientTemperatureInC);
		double powerToStorage = schedule.getScheduledEnergyPurchaseInMWH(currentTimeSegment.getStartTime());
		double heatToStorage = powerToStorage * coefficientOfPerformance;
		device.chargeInMW(heatToStorage);
		return powerToStorage + strategist.getHeatLoad(currentTimeSegment) / coefficientOfPerformance;
	}

	/** Digests award information from {@link DayAheadMarket} and writes out award data
	 * 
	 * @param input award information received from {@link DayAheadMarket}
	 * @param contracts not used */
	private void digestAwards(ArrayList<Message> input, List<Contract> contracts) {
		Message awards = CommUtils.getExactlyOneEntry(input);
		double awardedPower = awards.getDataItemOfType(AwardData.class).demandEnergyInMWH;
		double powerPrice = awards.getDataItemOfType(AwardData.class).powerPriceInEURperMWH;
		TimePeriod currentTimeSegment = new TimePeriod(now().earlierByOne(), operationPeriod);
		updateBuilding(awardedPower, currentTimeSegment);
		double costs = powerPrice * awardedPower;
		store(FlexibilityTrader.Outputs.VariableCostsInEUR, costs);
	}

	/** Updates the building temperature according to the awarded power and stores outputs
	 * 
	 * @param awardedPower awarded power from {@link DayAheadMarket}
	 * @param currentTimeSegment current time in simulation */
	private void updateBuilding(double awardedPower, TimePeriod currentTimeSegment) {
		double ambientTemperatureInC = strategist.getAmbientTemperatureInC(currentTimeSegment);
		double coefficientOfPerformance = heatPump.calcCoefficientOfPerformance(ambientTemperatureInC);
		double solarRadiationInkWperM2 = strategist.getSolarRadiationInkWperM2(currentTimeSegment);
		double singleUnitPowerInKW = awardedPower
				/ strategist.getUpscalingFactorToAllUnitsInMWperKW(currentTimeSegment);
		if (strategistType == HeatPumpStrategistType.INFLEXIBLE_RC
				|| strategistType == HeatPumpStrategistType.MIN_COST_RC) {
			building.updateBuildingTemperature(ambientTemperatureInC, solarRadiationInkWperM2, singleUnitPowerInKW);
		}
		switch (strategistType) {
			case INFLEXIBLE_FILE:
				store(OutputColumns.AwardedEnergyInMWH, awardedPower);
				store(OutputFields.COP, coefficientOfPerformance);
				break;
			case MIN_COST_FILE:
				store(OutputColumns.AwardedEnergyInMWH, awardedPower);
				store(OutputFields.COP, coefficientOfPerformance);
				store(OutputFields.StoredEnergyInMWH, device.getCurrentEnergyInStorageInMWH());
				break;
			case MIN_COST_RC:
				store(OutputColumns.AwardedEnergyInMWH, awardedPower);
				store(OutputFields.COP, coefficientOfPerformance);
				store(OutputFields.FinalRoomTemperatureInCelsius, building.getCurrentRoomTemperatureInC());
				break;
			case INFLEXIBLE_RC:
				store(OutputColumns.AwardedEnergyInMWH, awardedPower);
				store(OutputFields.COP, coefficientOfPerformance);
				store(OutputFields.FinalRoomTemperatureInCelsius, building.getCurrentRoomTemperatureInC());
				break;
			default:
				store(OutputColumns.AwardedEnergyInMWH, awardedPower);
		}
	}

	@Override
	protected double getInstalledCapacityInMW() {
		return 0;
	}

	@Override
	protected Strategist getStrategist() {
		return strategist;
	}
}
