// SPDX-FileCopyrightText: 2023-2026 German Aerospace Center <amiris@dlr.de>
//
// SPDX-License-Identifier: Apache-2.0
package agents.electrolysis;

import de.dlr.gitlab.fame.agent.input.Make;
import de.dlr.gitlab.fame.agent.input.ParameterData;
import de.dlr.gitlab.fame.agent.input.ParameterData.MissingDataException;
import de.dlr.gitlab.fame.agent.input.Tree;
import de.dlr.gitlab.fame.data.TimeSeries;
import de.dlr.gitlab.fame.time.TimePeriod;
import de.dlr.gitlab.fame.time.TimeStamp;

/** An electrolyzer device that converts electricity to hydrogen
 * 
 * @author Christoph Schimeczek */
public class Electrolyzer {
	static final String ERR_NEGATIVE_POWER = " MWh for electrolyzer dispatch. Negative for time step: ";

	/** Input parameters of an {@link Electrolyzer} unit */
	public static final Tree parameters = Make.newTree().add(
			Make.newSeries("PeakConsumptionInMW"),
			Make.newDouble("ConversionFactor")
					.help("Factor < 1 to convert electric energy to hydrogen's thermal energy equivalent"))
			.buildTree();

	private static final double NEGATIVE_TOLERANCE = -1E-5;
	private final double conversionFactor;
	private final TimeSeries peakConsumptions;

	/** Create electrolyzer device using its associated input
	 * 
	 * @param input parameter data group associated with {@link Electrolyzer}
	 * @throws MissingDataException if any required input is missing */
	public Electrolyzer(ParameterData input) throws MissingDataException {
		conversionFactor = input.getDouble("ConversionFactor");
		peakConsumptions = input.getTimeSeries("PeakConsumptionInMW");
	}

	/** Calculates produced hydrogen in one hour for given electric power at given time
	 * 
	 * @param electricPowerInMW electric power to use for electrolysis
	 * @param time at which to electrolyze
	 * @return produced amount of hydrogen in thermal MWh */
	public double calcProducedHydrogenOneHour(double electricPowerInMW, TimeStamp time) {
		double cappedPowerInMW = calcCappedElectricDemandInMW(electricPowerInMW, time);
		return cappedPowerInMW * conversionFactor;
	}

	/** Caps given electric demand if it exceeds the maximum peak power;<br>
	 * issues a warning if power is capped
	 * 
	 * @param electricPowerInMW to be used in the conversion
	 * @param time of the energy conversion
	 * @return given value, possible reduced to peak electric conversion power available at given time */
	public double calcCappedElectricDemandInMW(double electricPowerInMW, TimeStamp time) {
		if (electricPowerInMW < NEGATIVE_TOLERANCE) {
			throw new RuntimeException(electricPowerInMW + ERR_NEGATIVE_POWER + time);
		}
		if (electricPowerInMW > getPeakPower(time)) {
			electricPowerInMW = getPeakPower(time);
		}
		return Math.max(0, electricPowerInMW);
	}

	/** Returns true if given amount of hydrogen can be produced in the given time period, false otherwise
	 * 
	 * @param hydrogenOutput requested hydrogen output in thermal MWH
	 * @param period during which to produce the hydrogen
	 * @return true if there is enough power to produce request amount of hydrogen within given time */
	public boolean hasEnoughPowerToConvert(double hydrogenOutput, TimePeriod period) {
		double requiredElectricity = calcElectricEnergy(hydrogenOutput);
		double availableElectricity = getPeakPower(period.getStartTime());
		return availableElectricity >= requiredElectricity;
	}

	/** Calculates required electric energy to produce requested amount of hydrogen
	 * 
	 * @param hydrogenOutputInThermalMWH requested for production
	 * @return required electric energy to output given amount of hydrogen */
	public double calcElectricEnergy(double hydrogenOutputInThermalMWH) {
		return hydrogenOutputInThermalMWH / conversionFactor;
	}

	/** Gets the available peak electricity power consumption at given time
	 * 
	 * @param timeStamp at which to evaluate
	 * @return maximum electric power consumption */
	public double getPeakPower(TimeStamp timeStamp) {
		return peakConsumptions.getValueLinear(timeStamp);
	}

	/** Calculates hydrogen output for given electric input
	 * 
	 * @param electricInputInMWH used for hydrogen production
	 * @return hydrogen output in MWH - ignoring limits of the converter's peak power */
	public double calcHydrogenEnergy(double electricInputInMWH) {
		return electricInputInMWH * conversionFactor;
	}

	/** @return factor &lt; 1 of converting electric energy to hydrogen thermal energy */
	public double getConversionFactor() {
		return conversionFactor;
	}
}
