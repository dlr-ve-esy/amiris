// SPDX-FileCopyrightText: 2026 German Aerospace Center <amiris@dlr.de>
//
// SPDX-License-Identifier: Apache-2.0
package agents.heatPump.strategists;

import agents.flexibility.Strategist;
import agents.heatPump.HeatPump;
import agents.heatPump.HeatPumpSchedule;
import agents.heatPump.HeatingInputData;
import agents.heatPump.StrategyParameters;
import agents.markets.meritOrder.sensitivities.MeritOrderSensitivity;
import agents.storage.Device;
import de.dlr.gitlab.fame.agent.input.Make;
import de.dlr.gitlab.fame.agent.input.ParameterData;
import de.dlr.gitlab.fame.agent.input.ParameterData.MissingDataException;
import de.dlr.gitlab.fame.agent.input.Tree;
import de.dlr.gitlab.fame.data.TimeSeries;
import de.dlr.gitlab.fame.time.TimePeriod;

/** Abstract upper class of the different heat pump strategist types; creates a HeatPumpSchedule.
 * 
 * @author Evelyn Sperber, Christoph Schimeczek */
public abstract class HeatPumpStrategist extends Strategist {
	/** Types of {@link HeatPumpStrategist}s */
	public enum HeatPumpStrategistType {
		/** Aims to minimise heat-pump dispatch costs; heat demand calculated from RC-type building model */
		MIN_COST_RC,
		/** Aims to hold a constant target temperature; heat demand calculated from RC-type building model */
		INFLEXIBLE_RC,
		/** Uses heat demand from given file */
		INFLEXIBLE_FILE,
		/** Minimises costs for heat pump dispatch using active storage (hot water tank) for a given heat demand file */
		MIN_COST_FILE,
		/** Creates a cost-optimal HeatPumpSchedule according to real-time prices, using external optimisation model */
		EXTERNAL
	}

	/** Common input parameters required by all {@link HeatPumpStrategist}s */
	public static final Tree parameters = Make.newTree()
			.add(Strategist.forecastPeriodParam, Strategist.scheduleDurationParam, Strategist.bidToleranceParam).buildTree();

	/** {@link HeatPump} controlled by this Strategist */
	protected final HeatPump heatPump;
	/** Input data for heating */
	protected final HeatingInputData heatingData;
	/** Resolution of the temperature in degrees Celsius */
	protected final double temperatureResolutionInC;
	/** Parameters of the Strategy */
	protected final StrategyParameters strategyParams;
	/** Thermal storage device (building) controlled by this Strategist */
	protected Device thermalStorage;
	private TimeSeries installedUnits;

	/** Planned initial temperature in degrees Celsius at the beginning of each planned time period */
	protected double[] hourlyInitialTemperatureInC;

	/** Creates a {@link HeatPumpStrategist}
	 * 
	 * @param basicStrategy basic input data related to the strategist
	 * @param heatPump specifies the heat pump to be dispatched
	 * @param heatingData input regarding heat-related input time series
	 * @param thermalStorage the storage used for heat pump dispatch optimisation
	 * @param installedUnits number of installed heat pump units
	 * @param strategyParams strategy parameters for heat pump operation
	 * @throws MissingDataException if any required data is not provided */
	public HeatPumpStrategist(ParameterData basicStrategy, HeatPump heatPump, HeatingInputData heatingData,
			Device thermalStorage, TimeSeries installedUnits, StrategyParameters strategyParams) throws MissingDataException {
		super(basicStrategy);

		this.strategyParams = strategyParams;
		this.heatPump = heatPump;
		this.heatingData = heatingData;
		this.thermalStorage = thermalStorage;
		this.installedUnits = installedUnits;
		this.temperatureResolutionInC = (strategyParams.getMaximalRoomTemperatureInC()
				- strategyParams.getMinimalRoomTemperatureInC()) / strategyParams.getChargingSteps();
		allocateScheduleResources();
	}

	/** Initialises general permanent arrays used for schedule preparation */
	private void allocateScheduleResources() {
		hourlyInitialTemperatureInC = new double[scheduleDurationPeriods];
	}

	@Override
	protected void callOnSensitivity(MeritOrderSensitivity sensitivity, TimePeriod timePeriod) {
		double maxElectricalPowerInMW;
		switch (strategyParams.getHeatPumpStrategistType()) {
			case MIN_COST_FILE:
				maxElectricalPowerInMW = getAggregatedElectricalChargingPower(timePeriod);
				break;
			case EXTERNAL:
				maxElectricalPowerInMW = 0; // EXTERNAL strategist works with PriceForecast
				break;
			default:
				maxElectricalPowerInMW = getAggregatedElectricalHeatPumpPower(timePeriod);
		}
		sensitivity.updatePowers(maxElectricalPowerInMW, 0);
	};

	/** Calculate aggregated electric power of heat pumps that charge the thermal storage
	 * 
	 * @param timeSegment current time step
	 * @return total electrical power of all heat pumps that charges the thermal storage */
	private double getAggregatedElectricalChargingPower(TimePeriod timeSegment) {
		double ambientTemperatureInC = getAmbientTemperatureInC(timeSegment);
		double COP = heatPump.calcCoefficientOfPerformance(ambientTemperatureInC);
		double currentHeatLoad = getHeatLoad(timeSegment);
		return (thermalStorage.getExternalChargingPowerInMW() + currentHeatLoad) / COP;
	}

	/** @return total electrical power of all heat pumps */
	private double getAggregatedElectricalHeatPumpPower(TimePeriod timeSegment) {
		return installedUnits.getValueLinear(timeSegment.getStartTime()) * heatPump.getMaxElectricalHeatPumpPowerInKW()
				/ 1000.;
	}

	@Override
	public HeatPumpSchedule createSchedule(TimePeriod timePeriod) {
		updateSchedule(timePeriod);
		updateBidSchedule();
		HeatPumpSchedule schedule = new HeatPumpSchedule(timePeriod, scheduleDurationPeriods, temperatureResolutionInC);
		schedule.setBidsScheduleInEURperMWH(scheduledBidPricesInEURperMWH);
		schedule.setRequestedEnergyPerPeriod(demandScheduleInMWH);
		schedule.setExpectedInitialInternalEnergyScheduleInMWH(getInternalEnergySchedule());
		return schedule;
	}

	@Override
	protected abstract void updateSchedule(TimePeriod timePeriod);

	/** @return aggregated heat load to be covered by heat pumps at time step
	 * @param timePeriod for which to get the heat load */
	public abstract double getHeatLoad(TimePeriod timePeriod);

	/** @return ambient temperature at time step
	 * @param timePeriod for which to get the ambient temperature */
	public double getAmbientTemperatureInC(TimePeriod timePeriod) {
		return heatingData.getTemperaturProfile().getValueLinear(timePeriod.getStartTime());
	}

	/** @return tilted solar radiation at time step
	 * @param timePeriod for which to get the solar radiation for */
	public double getSolarRadiationInkWperM2(TimePeriod timePeriod) {
		return heatingData.getSolarRadiation().getValueLinear(timePeriod.getStartTime());
	}

	/** @return installed heat pump units in scenario at time step
	 * @param timePeriod for which to get the installation upscaling factor for */
	public double getUpscalingFactorToAllUnitsInMWperKW(TimePeriod timePeriod) {
		return installedUnits.getValueLinear(timePeriod.getStartTime()) / 1000.;
	}

	/** @return planned initial room temperatures */
	protected double[] getInternalEnergySchedule() {
		return hourlyInitialTemperatureInC;
	}
}
