// SPDX-FileCopyrightText: 2024-2026 German Aerospace Center <amiris@dlr.de>
//
// SPDX-License-Identifier: Apache-2.0
package agents.heatPump;

import de.dlr.gitlab.fame.agent.input.Make;
import de.dlr.gitlab.fame.agent.input.ParameterData;
import de.dlr.gitlab.fame.agent.input.ParameterData.MissingDataException;
import de.dlr.gitlab.fame.agent.input.Tree;
import de.dlr.gitlab.fame.data.TimeSeries;

/** A basic technical description of heat pump performance
 * 
 * @author Evelyn Sperber */
public class HeatPump {
	private static final double DESIGN_AMBIENT_TEMPERATURE_IN_C = -14;
	private static final double UPPER_AMBIENT_TEMPERATURE_FOR_HEAT_PUMP_SPECIFICATION_IN_C = 10;

	/** Input structure of {@link HeatPump} */
	public static final Tree parameters = Make.newTree().optional()
			.add(Make.newDouble("MinElectricHeatPumpPowerInKW"), Make.newDouble("MaxElectricHeatPumpPowerInKW"),
					Make.newDouble("HeatPumpPenetrationFactor"), Make.newDouble("MaxCOP"), Make.newDouble("MinCOP"),
					Make.newSeries("InstalledUnits"))
			.buildTree();

	private double minElectricHeatPumpPowerInKW;
	private double maxElectricHeatPumpPowerInKW;
	private double heatPumpPenetrationFactor;
	private double maxCOP;
	private double minCOP;
	private TimeSeries installedUnits;

	/** Creates a new {@link HeatPump} instance
	 * 
	 * @param data required inputs
	 * @throws MissingDataException if any required parameter is missing */
	public HeatPump(ParameterData data) throws MissingDataException {
		minElectricHeatPumpPowerInKW = data.getDouble("MinElectricHeatPumpPowerInKW");
		maxElectricHeatPumpPowerInKW = data.getDouble("MaxElectricHeatPumpPowerInKW");
		heatPumpPenetrationFactor = data.getDouble("HeatPumpPenetrationFactor");

		maxCOP = data.getDouble("MaxCOP");
		minCOP = data.getDouble("MinCOP");
		installedUnits = data.getTimeSeries("InstalledUnits");
	}

	/** @return coefficient of performance according to ambient temperature
	 * @param ambientTemperatureInC ambient temperature in time step */
	public double calcCoefficientOfPerformance(double ambientTemperatureInC) {
		double copGradient = (maxCOP - minCOP)
				/ (UPPER_AMBIENT_TEMPERATURE_FOR_HEAT_PUMP_SPECIFICATION_IN_C - DESIGN_AMBIENT_TEMPERATURE_IN_C);
		double copOffset = maxCOP - copGradient * UPPER_AMBIENT_TEMPERATURE_FOR_HEAT_PUMP_SPECIFICATION_IN_C;
		return copGradient * ambientTemperatureInC + copOffset;
	}

	/** @return current electric power of heat pump according to ambient temperature
	 * @param ambientTemperatureInC ambient temperature in time step */
	public double calcCurrentElectricHeatPumpPowerInKW(double ambientTemperatureInC) {
		double heatPumpPowerGradient = (maxElectricHeatPumpPowerInKW - minElectricHeatPumpPowerInKW)
				/ (DESIGN_AMBIENT_TEMPERATURE_IN_C - UPPER_AMBIENT_TEMPERATURE_FOR_HEAT_PUMP_SPECIFICATION_IN_C);
		double heatPumpPowerOffset = maxElectricHeatPumpPowerInKW - heatPumpPowerGradient * DESIGN_AMBIENT_TEMPERATURE_IN_C;
		return heatPumpPowerGradient * ambientTemperatureInC + heatPumpPowerOffset;
	}

	/** @return maximum electric power of heat pump */
	public double getMaxElectricalHeatPumpPowerInKW() {
		return maxElectricHeatPumpPowerInKW;
	}

	/** @return installed heat pump units in scenario */
	public TimeSeries getInstalledUnits() {
		return installedUnits;
	}

	/** @return market penetration factor of heat pumps */
	public double getHeatPumpPenetrationFactor() {
		return heatPumpPenetrationFactor;
	}
}