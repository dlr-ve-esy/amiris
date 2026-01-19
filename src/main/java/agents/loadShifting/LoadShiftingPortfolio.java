// SPDX-FileCopyrightText: 2026 German Aerospace Center <amiris@dlr.de>
//
// SPDX-License-Identifier: Apache-2.0
package agents.loadShifting;

import agents.trader.LoadShiftingTrader;
import de.dlr.gitlab.fame.agent.input.Make;
import de.dlr.gitlab.fame.agent.input.ParameterData;
import de.dlr.gitlab.fame.agent.input.ParameterData.MissingDataException;
import de.dlr.gitlab.fame.agent.input.Tree;
import de.dlr.gitlab.fame.data.TimeSeries;
import de.dlr.gitlab.fame.time.TimePeriod;
import de.dlr.gitlab.fame.time.TimeStamp;

/** A load shifting portfolio considers demand units which are eligible for load shifting and are marketed by a
 * {@link LoadShiftingTrader}
 * 
 * @author Johannes Kochems */
public class LoadShiftingPortfolio {
	private final static double STORAGE_TOLERANCE = 1E-3;

	private final double powerInMW;
	private final TimeSeries powerUpAvailabilities;
	private final TimeSeries powerDownAvailabilities;
	private final double energyResolutionInMWH;
	private final int maximumShiftTimeInHours;
	private int currentShiftTimeInHours;
	private double currentEnergyShiftStorageLevelInMWH;
	private final double energyLimitUpInMWH;
	private final double energyLimitDownInMWH;
	private final TimeSeries variableShiftCostsInEURPerMWH;
	private final TimeSeries baselineLoadSeries;
	private final double baselinePeakLoadInMW;
	private final double efficiency;
	private final int interferenceTimeInHours;
	private final int maximumActivations;

	/** Input parameters of a {@link LoadShiftingPortfolio} */
	public static final Tree parameters = Make.newTree()
			.add(Make.newDouble("InitialEnergyLevelInMWH"), Make.newInt("InitialShiftTimeInHours"),
					Make.newDouble("PowerInMW"), Make.newSeries("PowerUpAvailability"),
					Make.newSeries("PowerDownAvailability"), Make.newDouble("EnergyResolutionInMWH"),
					Make.newDouble("EnergyLimitUpInMWH"), Make.newDouble("EnergyLimitDownInMWH"),
					Make.newInt("MaximumShiftTimeInHours"), Make.newSeries("VariableShiftCostsInEURPerMWH"),
					Make.newSeries("BaselineLoadTimeSeries"), Make.newDouble("BaselinePeakLoadInMW"),
					Make.newDouble("Efficiency").optional(), Make.newInt("InterferenceTimeInHours").optional(),
					Make.newInt("MaximumActivations").optional())
			.buildTree();

	/** Initialises a new {@link LoadShiftingPortfolio}
	 * 
	 * @param input provides input from config
	 * @throws MissingDataException if any required data is missing */
	public LoadShiftingPortfolio(ParameterData input) throws MissingDataException {
		powerInMW = input.getDouble("PowerInMW");
		powerUpAvailabilities = input.getTimeSeries("PowerUpAvailability");
		powerDownAvailabilities = input.getTimeSeries("PowerDownAvailability");
		energyLimitUpInMWH = input.getDouble("EnergyLimitUpInMWH");
		energyLimitDownInMWH = input.getDouble("EnergyLimitDownInMWH");
		energyResolutionInMWH = input.getDouble("EnergyResolutionInMWH");
		setEnergyShiftStorageLevelInMWH(input.getDouble("InitialEnergyLevelInMWH"));
		maximumShiftTimeInHours = input.getInteger("MaximumShiftTimeInHours");
		setCurrentShiftTimeInHours(input.getInteger("InitialShiftTimeInHours"));
		variableShiftCostsInEURPerMWH = input.getTimeSeries("VariableShiftCostsInEURPerMWH");
		baselineLoadSeries = input.getTimeSeries("BaselineLoadTimeSeries");
		baselinePeakLoadInMW = input.getDouble("BaselinePeakLoadInMW");
		efficiency = input.getDoubleOrDefault("Efficiency", 1.0);
		interferenceTimeInHours = input.getIntegerOrDefault("InterferenceTimeInHours", maximumShiftTimeInHours);
		maximumActivations = input.getIntegerOrDefault("MaximumActivations", 1000000);
	}

	/** Set current load shift storage level and ensure it is within bounds */
	private void setEnergyShiftStorageLevelInMWH(double energyLevelInMWH) {
		currentEnergyShiftStorageLevelInMWH = Math.max(-energyLimitDownInMWH,
				Math.min(energyLimitUpInMWH, energyLevelInMWH));
	}

	/** Set current shift time and ensure it its within bounds */
	private void setCurrentShiftTimeInHours(Integer initialShiftTimeInHours) {
		currentShiftTimeInHours = Math.max(0, Math.min(initialShiftTimeInHours, maximumShiftTimeInHours - 1));
	}

	/** @return maximum power in MW of load shifting portfolio that can be shifted up or down */
	public double getPowerInMW() {
		return powerInMW;
	}

	/** @return current state of the load shifting portfolio, i.e. its fictitious stored energy content; can be negative */
	public double getCurrentEnergyShiftStorageLevelInMWH() {
		return currentEnergyShiftStorageLevelInMWH;
	}

	/** @return the current time duration the load shifting portfolio has already been shifted for */
	public int getCurrentShiftTimeInHours() {
		return currentShiftTimeInHours;
	}

	/** @return the maximum allowed time for a load shift in hours, including the initial hour of shifting */
	public int getMaximumShiftTimeInHours() {
		return maximumShiftTimeInHours;
	}

	/** Update the energy shift storage level as well as the shift time considering the change in energy:<br>
	 * <ul>
	 * <li>energyChangeInMWH &gt; 0: charging - increasing load</li>
	 * <li>energyChangeInMWH &lt; 0: depleting - decreasing load</li>
	 * </ul>
	 * 
	 * @param energyChangeInMWH energy charged (increment of fictitious load shift energy storage level) */
	public void updateEnergyShiftStorageLevelAndShiftTime(double energyChangeInMWH) {
		int shiftTime = getCurrentShiftTimeInHours();
		double initialEnergyLevel = getCurrentEnergyShiftStorageLevelInMWH();
		double finalEnergyLevel = initialEnergyLevel + energyChangeInMWH;
		if (isProlongedShift(energyChangeInMWH, shiftTime, initialEnergyLevel)) {
			shiftTime = 1;
		} else if (isZeroEnergyLevel(finalEnergyLevel)) {
			shiftTime = 0;
		} else if (isChangeOfSign(initialEnergyLevel, finalEnergyLevel)) {
			shiftTime = 1;
		} else {
			shiftTime += 1;
		}
		setCurrentShiftTimeInHours(shiftTime);
		setEnergyShiftStorageLevelInMWH(finalEnergyLevel);
	}

	/** Check for an initial reset of parts of the {@link LoadShiftingPortfolio}
	 * 
	 * @param chargingPower power charged (increment of fictitious load shift energy storage level)
	 * @param shiftTime time that the {@link LoadShiftingPortfolio} has been shifted for in one direction so far
	 * @param initialEnergyLevel start value of fictitious load shifting energy storage level
	 * @return whether shift is a prolonged shift */
	private boolean isProlongedShift(double chargingPower, int shiftTime, double initialEnergyLevel) {
		if (shiftTime == maximumShiftTimeInHours - 1) {
			double finalEnergyLevel = initialEnergyLevel + chargingPower;
			return (initialEnergyLevel > STORAGE_TOLERANCE && finalEnergyLevel > STORAGE_TOLERANCE) ||
					(initialEnergyLevel < -STORAGE_TOLERANCE && finalEnergyLevel < -STORAGE_TOLERANCE);
		}
		return false;
	}

	/** Return cost for prolonging a given load shift beyond its maximum shift duration by counter-shifts within the portfolio;
	 * prolonging costs only account for that part of the shifted load that has to be prolonged, excluding costs for additional or
	 * reduced load
	 * 
	 * @param energyChangeInMWh change in energy level in MWh
	 * @param timeStamp at which the shift prolonging takes place
	 * @return cost for prolonging the current load shifting given the provided change of load shift energy */
	public double getProlongingCosts(double energyChangeInMWh, TimeStamp timeStamp) {
		int shiftTime = getCurrentShiftTimeInHours();
		double initialEnergyLevelInMWH = getCurrentEnergyShiftStorageLevelInMWH();
		if (isProlongedShift(energyChangeInMWh, shiftTime, initialEnergyLevelInMWH)) {
			double finalEnergyLevelInMWH = initialEnergyLevelInMWH + energyChangeInMWh;
			double prolongedEnergyLevelInMWH = Math.min(Math.abs(initialEnergyLevelInMWH), Math.abs(finalEnergyLevelInMWH));
			return prolongedEnergyLevelInMWH * getVariableShiftCostsInEURPerMWH(timeStamp);
		} else {
			return 0.;
		}
	}

	/** @return true if state within zero storage level tolerance */
	private boolean isZeroEnergyLevel(double energyLevel) {
		return -STORAGE_TOLERANCE <= energyLevel && energyLevel <= STORAGE_TOLERANCE;
	}

	/** @return true if load shifting energy level changes its sign */
	private boolean isChangeOfSign(double initialEnergyLevel, double finalEnergyLevel) {
		boolean initialStateNotZero = !isZeroEnergyLevel(initialEnergyLevel);
		boolean finalStateNotZero = !isZeroEnergyLevel(finalEnergyLevel);
		return initialStateNotZero && finalStateNotZero
				&& (Math.signum(finalEnergyLevel) != Math.signum(initialEnergyLevel));
	}

	/** @return time series of allowed power shifts in upwards direction relative to {@link #powerInMW} */
	public TimeSeries getPowerUpAvailabilities() {
		return powerUpAvailabilities;
	}

	/** @return time series of allowed power shifts in downwards direction relative to {@link #powerInMW} */
	public TimeSeries getDowerDownAvailabilities() {
		return powerDownAvailabilities;
	}

	/** @return resolution of discrete energy steps used for dispatch planning */
	public double getEnergyResolutionInMWH() {
		return energyResolutionInMWH;
	}

	/** @return the absolute energy limit for a shift in upwards direction (increased load) in MWh */
	public double getEnergyLimitUpInMWH() {
		return energyLimitUpInMWH;
	}

	/** @return the absolute energy limit for a shift in downwards direction (decreased load) in MWh */
	public double getEnergyLimitDownInMWH() {
		return energyLimitDownInMWH;
	}

	/** Returns variable costs of load shifting at the specified time stamp
	 * 
	 * @param timeStamp at which the variable shift costs are requested
	 * @return variable costs of load shifting in EUR/MWh */
	public double getVariableShiftCostsInEURPerMWH(TimeStamp timeStamp) {
		return variableShiftCostsInEURPerMWH.getValueLinear(timeStamp);
	}

	/** @return time series of variable costs of load shifting in EUR/MWh */
	public TimeSeries getVariableShiftCostsInEURPerMWHSeries() {
		return variableShiftCostsInEURPerMWH;
	}

	/** @return time series of the baseline demand relative to {@link #baselinePeakLoadInMW} */
	public TimeSeries getBaselineLoadSeries() {
		return baselineLoadSeries;
	}

	/** @return the maximum peak load in MW occurring before load shifting */
	public double getBaselinePeakLoad() {
		return baselinePeakLoadInMW;
	}

	/** Returns maximum increase in demand power above baseline for the given time period
	 * 
	 * @param timePeriod for which the maximum demand increase is requested
	 * @return maximum demand increase in MW */
	public double getMaxPowerUpInMW(TimePeriod timePeriod) {
		return powerUpAvailabilities.getValueEarlierEqual(timePeriod.getStartTime()) * powerInMW;
	}

	/** Returns maximum decrease in demand power below baseline for given the time period
	 * 
	 * @param timePeriod for which the maximum demand decrease is requested
	 * @return maximum demand decrease in MW */
	public double getMaxPowerDownInMW(TimePeriod timePeriod) {
		return powerDownAvailabilities.getValueEarlierEqual(timePeriod.getStartTime()) * powerInMW;
	}

	/** @return the efficiency of load shifting (value between 0 and 1 (inclusive)) */
	public double getEfficiency() {
		return efficiency;
	}

	/** @return maximum allowed time for any shifting process used by strategist "ShiftProfitMaximiserExternal" */
	public int getInterferenceTimeInHours() {
		return interferenceTimeInHours;
	}

	/** @return maximum number of full shift cycles over the course of the planning period (e.g. one year) */
	public int getMaximumActivations() {
		return maximumActivations;
	}
}
