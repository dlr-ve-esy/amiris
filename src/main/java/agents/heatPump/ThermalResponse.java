// SPDX-FileCopyrightText: 2026 German Aerospace Center <amiris@dlr.de>
//
// SPDX-License-Identifier: Apache-2.0
package agents.heatPump;

/** Describes basic thermodynamic behaviour of one specific type using the 1R1C model according to
 * https://doi.org/10.1016/j.enbuild.2020.110144.
 * 
 * @author Evelyn Sperber */
public class ThermalResponse {
	private static final double TIME_STEP_IN_HOURS = 1; // corresponds to delta T in discretised differential equation
	private static final double TEMPERATURE_DEADBAND_IN_C = 1.0;
	private double roomTemperatureInC;
	private final BuildingParameters buildingParams;
	private HeatPump heatPump;
	private double heatDemandInKW;
	private double powerDemandInKW;

	/** Creates a physical {@link ThermalResponse} building
	 * 
	 * @param buildingParams input data regarding the building type from config
	 * @param heatPump input data regarding the heat pump type from config
	 * @param initialRoomTemperatureInC room temperature at start of simulation */
	public ThermalResponse(BuildingParameters buildingParams, HeatPump heatPump, double initialRoomTemperatureInC) {
		this.buildingParams = buildingParams;
		this.roomTemperatureInC = initialRoomTemperatureInC;
		this.heatPump = heatPump;
	}

	/** Calculates the room temperature in the next time step
	 * 
	 * @param ambientTemperatureInC ambient temperature at current time
	 * @param solarRadiationInKWperM2 tilted solar radiation at current time
	 * @param electricHeatingPowerInKW electric power of heat pump at current time */
	public void updateBuildingTemperature(double ambientTemperatureInC, double solarRadiationInKWperM2,
			double electricHeatingPowerInKW) {
		roomTemperatureInC = calcNextRoomTemperatureInC(roomTemperatureInC, ambientTemperatureInC,
				solarRadiationInKWperM2, electricHeatingPowerInKW);
	}

	/** Calculates the heat demand required to heat the building from the initial room temperature to the final room temperature
	 * 
	 * @param initialRoomTemperatureInC room temperature at current time step
	 * @param finalRoomTemperatureInC room temperature at next time step
	 * @param ambientTemperatureInC ambient temperature at current time step
	 * @param solarRadiationInKWperM2 tilted solar radiation at current time step
	 * @return heatDemandInKW (Positive) heat demand of heat pump */
	public double calcHourlyHeatDemandInKW(double initialRoomTemperatureInC, double finalRoomTemperatureInC,
			double ambientTemperatureInC, double solarRadiationInKWperM2) {
		double calculatedHeatDemandInkW = ((finalRoomTemperatureInC - initialRoomTemperatureInC)
				* buildingParams.getCi() / TIME_STEP_IN_HOURS
				- 1 / buildingParams.getRia() * (ambientTemperatureInC - initialRoomTemperatureInC)
				- buildingParams.getAi() * solarRadiationInKWperM2 - buildingParams.getInternalHeatGainsInKW());
		if (ambientTemperatureInC < buildingParams.getHeatingLimitTemperatureInC()) {
			heatDemandInKW = Math.max(0, calculatedHeatDemandInkW); // Math.max(0,) to avoid cooling
		} else {
			heatDemandInKW = 0;
		}
		return heatDemandInKW;
	}

	/** Calculates electrical power demand required to heat the building from the initial room temperature to the final room
	 * temperature. No hysteresis is implemented to avoid interference with dynamic programming algorithm.
	 * 
	 * @param initialRoomTemperatureInC room temperature at current time step
	 * @param finalRoomTemperatureInC room temperature at next time step
	 * @param ambientTemperatureInC ambient temperature at current time step
	 * @param solarRadiationInKWperM2 tilted solar radiation at current time step
	 * @return (Positive) power demand of heat pump */
	public double calcHourlyPowerDemandSimpleInKW(double initialRoomTemperatureInC, double finalRoomTemperatureInC,
			double ambientTemperatureInC, double solarRadiationInKWperM2) {
		double coefficientOfPerformance = heatPump.calcCoefficientOfPerformance(ambientTemperatureInC);
		double heatDemandInKW = calcHourlyHeatDemandInKW(initialRoomTemperatureInC, finalRoomTemperatureInC,
				ambientTemperatureInC, solarRadiationInKWperM2);
		double powerDemandInKW = heatDemandInKW / coefficientOfPerformance;
		return Math.max(0, Math.min(powerDemandInKW, heatPump.getMaxElectricalHeatPumpPowerInKW()));
	}

	/** Calculates electrical power demand required to heat the building from the initial room temperature to the final room
	 * temperature. A hysteresis is implemented in order to keep the room temperature within a given range that is defined by the
	 * temperature deadband.
	 * 
	 * @param initialRoomTemperatureInC room temperature at current time step
	 * @param finalRoomTemperatureInC room temperature at next time step
	 * @param ambientTemperatureInC ambient temperature at current time step
	 * @param solarRadiationInKWperM2 tilted solar radiation at current time step
	 * @return (Positive) power demand of heat pump considering hysteresis */
	public double calcHourlyPowerDemandWithHysteresisInKW(double initialRoomTemperatureInC,
			double finalRoomTemperatureInC, double ambientTemperatureInC, double solarRadiationInKWperM2) {
		double coefficientOfPerformance = heatPump.calcCoefficientOfPerformance(ambientTemperatureInC);
		double setPointRoomTemperatureInC = finalRoomTemperatureInC + TEMPERATURE_DEADBAND_IN_C / 2;
		double calculatedHeatDemandInKW = calcHourlyHeatDemandInKW(initialRoomTemperatureInC,
				setPointRoomTemperatureInC, ambientTemperatureInC, solarRadiationInKWperM2);
		double previousPowerDemandInKW = powerDemandInKW;
		if (previousPowerDemandInKW > 0) {
			if (initialRoomTemperatureInC < finalRoomTemperatureInC + TEMPERATURE_DEADBAND_IN_C / 2) {
				powerDemandInKW = calculatedHeatDemandInKW / coefficientOfPerformance;
			} else {
				powerDemandInKW = 0;
			}
		}
		if (previousPowerDemandInKW == 0) {
			if (initialRoomTemperatureInC > finalRoomTemperatureInC - TEMPERATURE_DEADBAND_IN_C / 2) {
				powerDemandInKW = 0;
			} else {
				powerDemandInKW = calculatedHeatDemandInKW / coefficientOfPerformance;
			}
		}

		return Math.max(0, Math.min(powerDemandInKW, heatPump.getMaxElectricalHeatPumpPowerInKW()));
	}

	/** Calculates minimal and maximal room temperature that can be reached depending on whether heat pump is off, or completely on.
	 * 
	 * @param initialRoomTemperatureInC room temperature at current time step
	 * @param ambientTemperatureInC ambient temperature at current time step
	 * @param solarRadiationInKWperM2 tilted solar radiation at current time step
	 * @return Tuple of minimum and maximum reachable room temperature */
	public double[] calcMinMaxReachableRoomTemperatureInC(double initialRoomTemperatureInC,
			double ambientTemperatureInC, double solarRadiationInKWperM2) { // temperature
		double coefficientOfPerformance = heatPump.calcCoefficientOfPerformance(ambientTemperatureInC);
		double electricHeatingPowerInKW = heatPump.calcCurrentElectricHeatPumpPowerInKW(ambientTemperatureInC);
		double thermalHeatingPowerInKW = coefficientOfPerformance * electricHeatingPowerInKW;
		double nextRoomTemperatureInC = initialRoomTemperatureInC + (1
				/ (buildingParams.getCi() * buildingParams.getRia())
				* (ambientTemperatureInC - initialRoomTemperatureInC)
				+ 1 / buildingParams.getCi() * (thermalHeatingPowerInKW + buildingParams.getInternalHeatGainsInKW())
				+ buildingParams.getAi() / buildingParams.getCi() * solarRadiationInKWperM2) * TIME_STEP_IN_HOURS;
		double maxNextTemperature = nextRoomTemperatureInC;
		double minNextTemperature = maxNextTemperature
				- 1 / buildingParams.getCi() * thermalHeatingPowerInKW * TIME_STEP_IN_HOURS; // heating is off
		return new double[] {minNextTemperature, maxNextTemperature};
	}

	/** Calculates the next room temperature at given ambient conditions and heating power.
	 * 
	 * @param initialRoomTemperatureInC room temperature at current time step
	 * @param ambientTemperatureInC ambient temperature at current time step
	 * @param solarRadiationInKWperM2 tilted solar radiation at current time step
	 * @param electricHeatingPowerInKW electric heating power of heat pump at current time step
	 * @return Next room temperature */
	public double calcNextRoomTemperatureInC(double initialRoomTemperatureInC, double ambientTemperatureInC,
			double solarRadiationInKWperM2, double electricHeatingPowerInKW) {
		double coefficientOfPerformance = heatPump.calcCoefficientOfPerformance(ambientTemperatureInC);
		double thermalHeatingPowerInKW = coefficientOfPerformance * electricHeatingPowerInKW;
		double nextRoomTemperatureInC = initialRoomTemperatureInC + (1
				/ (buildingParams.getCi() * buildingParams.getRia())
				* (ambientTemperatureInC - initialRoomTemperatureInC)
				+ 1 / buildingParams.getCi() * (thermalHeatingPowerInKW + buildingParams.getInternalHeatGainsInKW())
				+ buildingParams.getAi() / buildingParams.getCi() * solarRadiationInKWperM2) * TIME_STEP_IN_HOURS;
		return nextRoomTemperatureInC;
	}

	/** @return current room temperature */
	public double getCurrentRoomTemperatureInC() {
		return roomTemperatureInC;
	}

	/** @return current heat demand of heat pump */
	public double getHeatDemandInKW() {
		return heatDemandInKW;
	}
}
