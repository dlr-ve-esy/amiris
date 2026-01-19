// SPDX-FileCopyrightText: 2026 German Aerospace Center <amiris@dlr.de>
//
// SPDX-License-Identifier: Apache-2.0
package agents.heatPump.strategists;

import agents.heatPump.HeatPump;
import agents.heatPump.HeatPumpSchedule;
import agents.heatPump.HeatingInputData;
import agents.heatPump.StrategyParameters;
import agents.heatPump.ThermalResponse;
import agents.markets.meritOrder.sensitivities.MeritOrderSensitivity;
import agents.storage.Device;
import de.dlr.gitlab.fame.agent.input.ParameterData;
import de.dlr.gitlab.fame.agent.input.ParameterData.MissingDataException;
import de.dlr.gitlab.fame.data.TimeSeries;
import de.dlr.gitlab.fame.time.TimePeriod;

/** Heat pump dispatch orientated at endogenously calculated heat demand, irrespective of electricity prices
 * 
 * @author Christoph Schimeczek, Evelyn Sperber */
public class StrategistInflexibleRC extends HeatPumpStrategist {
	private ThermalResponse building;

	/** Creates a {@link StrategistInflexibleRC}
	 * 
	 * @param basicStrategy basic input data related to the strategist
	 * @param building specifies the building in which the heat pump is operated
	 * @param heatPump specifies the heat pump to be dispatched
	 * @param device the storage used for heat pump dispatch optimisation
	 * @param heatingData input regarding heat-related input time series
	 * @param installedUnits number of installed heat pump units
	 * @param strategyParams strategy parameters for heat pump operation
	 * @throws MissingDataException if any required data is not provided */
	public StrategistInflexibleRC(ParameterData basicStrategy, ThermalResponse building, HeatPump heatPump,
			Device device, HeatingInputData heatingData, TimeSeries installedUnits, StrategyParameters strategyParams)
			throws MissingDataException {
		super(basicStrategy, heatPump, heatingData, device, installedUnits, strategyParams);
		this.building = building;
	}

	@Override
	public HeatPumpSchedule createSchedule(TimePeriod timePeriod) {
		if (temperatureIsOutOfBounds()) {
			updateScheduleOutOfBounds(timePeriod);
			updateBidSchedule();
			HeatPumpSchedule schedule = new HeatPumpSchedule(timePeriod, 1, temperatureResolutionInC);
			schedule.setBidsScheduleInEURperMWH(new double[] {scheduledBidPricesInEURperMWH[0]});
			schedule.setRequestedEnergyPerPeriod(new double[] {demandScheduleInMWH[0]});
			schedule.setExpectedInitialInternalEnergyScheduleInMWH(new double[] {getInternalEnergySchedule()[0]});
			return schedule;
		}
		return super.createSchedule(timePeriod);
	}

	/** @return true if room temperature is out of allowed limits */
	protected boolean temperatureIsOutOfBounds() {
		double temperatureInC = building.getCurrentRoomTemperatureInC();
		return temperatureInC < strategyParams.getMinimalRoomTemperatureInC() * 0.99999
				|| temperatureInC > strategyParams.getMaximalRoomTemperatureInC();
	}

	/** Updates dispatch schedule in case the room temperature was out of allowed bounds and brings temperature back to allowed
	 * limits
	 * 
	 * @param timeSegment time segment for which schedule is calculated */
	protected void updateScheduleOutOfBounds(TimePeriod timeSegment) {
		double temperatureInC = building.getCurrentRoomTemperatureInC();
		if (temperatureInC < strategyParams.getMinimalRoomTemperatureInC()) {
			double targetTemperatureInC = strategyParams.getMinimalRoomTemperatureInC();
			double ambientTemperatureInC = getAmbientTemperatureInC(timeSegment);
			double solarRadiationInkWperM2 = getSolarRadiationInkWperM2(timeSegment);
			double singleUnitPowerDemandInKW = building.calcHourlyPowerDemandWithHysteresisInKW(temperatureInC,
					targetTemperatureInC, ambientTemperatureInC, solarRadiationInkWperM2);
			demandScheduleInMWH[0] = singleUnitPowerDemandInKW * getUpscalingFactorToAllUnitsInMWperKW(timeSegment);
			hourlyInitialTemperatureInC[0] = temperatureInC;
		} else if (temperatureInC > strategyParams.getMaximalRoomTemperatureInC()) {
			demandScheduleInMWH[0] = 0;
			hourlyInitialTemperatureInC[0] = temperatureInC;
		}
	}

	@Override
	protected void updateSchedule(TimePeriod timeSegment) {
		double currentRoomTemperature = building.getCurrentRoomTemperatureInC();
		double targetTemperatureInC = strategyParams.getMinimalRoomTemperatureInC();
		for (int periodInSchedule = 0; periodInSchedule < scheduleDurationPeriods; periodInSchedule++) {
			TimePeriod planningTimeSegment = timeSegment.shiftByDuration(periodInSchedule);
			double ambientTemperatureInC = getAmbientTemperatureInC(planningTimeSegment);
			double solarRadiationInkWperM2 = getSolarRadiationInkWperM2(planningTimeSegment);
			double singleUnitPowerDemandInKW = building.calcHourlyPowerDemandWithHysteresisInKW(currentRoomTemperature,
					targetTemperatureInC, ambientTemperatureInC, solarRadiationInkWperM2);
			demandScheduleInMWH[periodInSchedule] = singleUnitPowerDemandInKW
					* getUpscalingFactorToAllUnitsInMWperKW(timeSegment);
			hourlyInitialTemperatureInC[periodInSchedule] = currentRoomTemperature;
			currentRoomTemperature = building.calcNextRoomTemperatureInC(currentRoomTemperature, ambientTemperatureInC,
					solarRadiationInkWperM2, singleUnitPowerDemandInKW);
		}
	}

	@Override
	/** No {@link MeritOrderSensitivity} needed for {@link StrategistInflexibleRC}, as dispatch is not oriented at prices */
	protected MeritOrderSensitivity createBlankSensitivity() {
		throw new RuntimeException("Forecasts cannot be used with strategist 'StrategistInflexibleFile'.");
	}

	@Override
	public double getHeatLoad(TimePeriod currentTimeSegment) {
		return 0; // not needed for this Strategist type
	}
}
