// SPDX-FileCopyrightText: 2026 German Aerospace Center <amiris@dlr.de>
//
// SPDX-License-Identifier: Apache-2.0
package agents.heatPump.strategists;

import agents.heatPump.HeatPump;
import agents.heatPump.HeatingInputData;
import agents.heatPump.StrategyParameters;
import agents.markets.meritOrder.sensitivities.MeritOrderSensitivity;
import agents.storage.Device;
import de.dlr.gitlab.fame.agent.input.ParameterData;
import de.dlr.gitlab.fame.agent.input.ParameterData.MissingDataException;
import de.dlr.gitlab.fame.data.TimeSeries;
import de.dlr.gitlab.fame.time.TimePeriod;

/** Heat pump dispatch orientated at given heat demand from file, irrespective of electricity prices
 * 
 * @author Evelyn Sperber */
public class StrategistInflexibleFile extends HeatPumpStrategist {
	private TimeSeries totalHeatDemand;
	private double heatPumpPenetrationFactor;
	private final double fixedRoomTemperaturInC;

	/** Creates a {@link StrategistInflexibleFile}
	 * 
	 * @param basicStrategy basic input data related to the strategist
	 * @param heatPump specifies the heat pump to be dispatched
	 * @param device the storage used for heat pump dispatch optimisation
	 * @param heatingData input regarding heat-related input time series
	 * @param heatPumpPenetrationFactor share of heat pumps of total heating demand
	 * @param installedUnits number of installed heat pump units
	 * @param strategyParams strategy parameters for heat pump operation
	 * @param fixedRoomTemperaturInC initial room temperature when instantiated
	 * @throws MissingDataException if any required data is not provided */
	public StrategistInflexibleFile(ParameterData basicStrategy, HeatPump heatPump, Device device,
			HeatingInputData heatingData, double heatPumpPenetrationFactor, TimeSeries installedUnits,
			StrategyParameters strategyParams, double fixedRoomTemperaturInC) throws MissingDataException {
		super(basicStrategy, heatPump, heatingData, device, installedUnits, strategyParams);

		this.heatPumpPenetrationFactor = heatPumpPenetrationFactor;
		this.totalHeatDemand = heatingData.getHeatDemandProfile();
		this.fixedRoomTemperaturInC = fixedRoomTemperaturInC;
	}

	@Override
	public void updateSchedule(TimePeriod timeSegment) {
		for (int periodInSchedule = 0; periodInSchedule < scheduleDurationPeriods; periodInSchedule++) {
			TimePeriod planningTimeSegment = timeSegment.shiftByDuration(periodInSchedule);
			double ambientTemperatureInC = getAmbientTemperatureInC(planningTimeSegment);
			double heatPumpheatDemandInMW = totalHeatDemand.getValueLinear(planningTimeSegment.getStartTime())
					* heatPumpPenetrationFactor;
			double heatPumpPowerDemandInMW = calcHourlyPowerDemandFromHeatFile(heatPumpheatDemandInMW,
					ambientTemperatureInC);
			demandScheduleInMWH[periodInSchedule] = heatPumpPowerDemandInMW;
			hourlyInitialTemperatureInC[periodInSchedule] = fixedRoomTemperaturInC;
		}
	}

	@Override
	/** No {@link MeritOrderSensitivity} needed for {@link StrategistInflexibleFile}, as dispatch is not oriented at prices */
	protected MeritOrderSensitivity createBlankSensitivity() {
		throw new RuntimeException("Forecasts cannot be used with strategist 'StrategistInflexibleFile'.");
	}

	/** Calculates power demand from given heat file according to the current COP */
	private double calcHourlyPowerDemandFromHeatFile(double heatDemandInMW, double ambientTemperaturInC) {
		double COP = heatPump.calcCoefficientOfPerformance(ambientTemperaturInC);
		return heatDemandInMW / COP;
	}

	/** @return current heat load to be covered by heat pumps
	 * @param timeSegment current time */
	public double getHeatLoad(TimePeriod timeSegment) {
		return totalHeatDemand.getValueLinear(timeSegment.getStartTime()) * heatPumpPenetrationFactor;
	}
}
