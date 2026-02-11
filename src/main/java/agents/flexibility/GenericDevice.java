// SPDX-FileCopyrightText: 2025-2026 German Aerospace Center <amiris@dlr.de>
//
// SPDX-License-Identifier: Apache-2.0
package agents.flexibility;

import static de.dlr.gitlab.fame.time.Constants.STEPS_PER_HOUR;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import agents.storage.Device;
import de.dlr.gitlab.fame.agent.input.Make;
import de.dlr.gitlab.fame.agent.input.ParameterData;
import de.dlr.gitlab.fame.agent.input.ParameterData.MissingDataException;
import de.dlr.gitlab.fame.agent.input.Tree;
import de.dlr.gitlab.fame.data.TimeSeries;
import de.dlr.gitlab.fame.time.Constants.Interval;
import de.dlr.gitlab.fame.time.TimeSpan;
import de.dlr.gitlab.fame.time.TimeStamp;

/** A generic device representing any kind of electrical flexibility, e.g., pumped-hydro storages with inflow, reservoir storages,
 * heat pumps, electric vehicle fleets, load-shifting portfolios. See also the
 * <a href="https://gitlab.com/dlr-ve/esy/amiris/amiris/-/wikis/Classes/Modules/GenericDevice">Wiki description</a>
 * 
 * @author Christoph Schimeczek, Felix Nitsch, Johannes Kochems */
public class GenericDevice {
	static final String ERR_EXCEED_CHARGING_POWER = " Maximum charging power exceeded by MW: ";
	static final String ERR_EXCEED_DISCHARGING_POWER = " Maximum discharging power exceeded by MW: ";
	static final String ERR_EXCEED_UPPER_ENERGY_LIMIT = " Upper energy limit exceeded by MWh: ";
	static final String ERR_EXCEED_LOWER_ENERGY_LIMIT = " Lower energy limit exceeded by MWh: ";
	static final String ERR_NEGATIVE_SELF_DISCHARGE = "Energy out of nothing: negative self discharge occured at time: ";
	static final String WARN_SHIFT_TIME_EXCEEDED = "Maximum shift time exceeded. Is reset at the expense of penalty cost. Was exceeded by seconds: ";
	static final double TOLERANCE = 1E-3;

	static final String PARAM_CHARGING_POWER = "GrossChargingPowerInMW";
	static final String PARAM_DISCHARGING_POWER = "NetDischargingPowerInMW";
	static final String PARAM_CHARGING_EFFICIENCY = "ChargingEfficiency";
	static final String PARAM_DISCHARGING_EFFICIENCY = "DischargingEfficiency";
	static final String PARAM_UPPER_LIMIT = "EnergyContentUpperLimitInMWH";
	static final String PARAM_LOWER_LIMIT = "EnergyContentLowerLimitInMWH";
	static final String PARAM_SELF_DISCHARGE = "SelfDischargeRatePerHour";
	static final String PARAM_INFLOW = "NetInflowPowerInMW";
	static final String PARAM_INITIAL_ENERGY = "InitialEnergyContentInMWH";
	static final String PARAM_VARIABLE_COST = "VariableCostInEURperMWH";
	static final String PARAM_SHIFT_TIME = "MaximumShiftTimeInHours";
	static final String PARAM_ENABLE_PROLONGING = "EnableProlonging";
	static final String PARAM_PENALTY_COST = "PenaltyCostInEURperMWH";

	private static Logger logger = LoggerFactory.getLogger(GenericDevice.class);
	private TimeSeries externalChargingPowerInMW;
	private TimeSeries externalDischargingPowerInMW;
	private TimeSeries chargingEfficiency;
	private TimeSeries dischargingEfficiency;
	private TimeSeries energyContentUpperLimitInMWH;
	private TimeSeries energyContentLowerLimitInMWH;
	private TimeSeries selfDischargeRatePerHour;
	private TimeSeries netInflowPowerInMW;
	private TimeSeries variableCostInEURperMWH;
	private TimeSeries penaltyCostInEURperMWH;
	private double currentEnergyContentInMWH;
	private long maximumShiftTimeInSteps;
	private long currentShiftTimeInSteps;
	private boolean enableProlonging;
	private double lastProlongingCostInEUR;

	/** Input parameters of a storage {@link Device} */
	public static final Tree parameters = Make.newTree()
			.add(Make.newSeries(PARAM_CHARGING_POWER), Make.newSeries(PARAM_DISCHARGING_POWER),
					Make.newSeries(PARAM_CHARGING_EFFICIENCY), Make.newSeries(PARAM_DISCHARGING_EFFICIENCY),
					Make.newSeries(PARAM_UPPER_LIMIT), Make.newSeries(PARAM_LOWER_LIMIT), Make.newSeries(PARAM_SELF_DISCHARGE),
					Make.newSeries(PARAM_INFLOW), Make.newDouble(PARAM_INITIAL_ENERGY), Make.newSeries(PARAM_VARIABLE_COST),
					Make.newDouble(PARAM_SHIFT_TIME), Make.newInt(PARAM_ENABLE_PROLONGING), Make.newSeries(PARAM_PENALTY_COST))
			.buildTree();

	/** Instantiates new {@link GenericDevice}
	 * 
	 * @param input parameters from file
	 * @throws MissingDataException if any required input parameter is missing */
	public GenericDevice(ParameterData input) throws MissingDataException {
		externalChargingPowerInMW = input.getTimeSeries(PARAM_CHARGING_POWER);
		externalDischargingPowerInMW = input.getTimeSeries(PARAM_DISCHARGING_POWER);
		chargingEfficiency = input.getTimeSeries(PARAM_CHARGING_EFFICIENCY);
		dischargingEfficiency = input.getTimeSeries(PARAM_DISCHARGING_EFFICIENCY);
		energyContentUpperLimitInMWH = input.getTimeSeries(PARAM_UPPER_LIMIT);
		energyContentLowerLimitInMWH = input.getTimeSeries(PARAM_LOWER_LIMIT);
		selfDischargeRatePerHour = input.getTimeSeries(PARAM_SELF_DISCHARGE);
		netInflowPowerInMW = input.getTimeSeries(PARAM_INFLOW);
		currentEnergyContentInMWH = input.getDouble(PARAM_INITIAL_ENERGY);
		variableCostInEURperMWH = input.getTimeSeries(PARAM_VARIABLE_COST);
		maximumShiftTimeInSteps = (long) (new TimeSpan(1, Interval.HOURS).getSteps() * input.getDouble(PARAM_SHIFT_TIME));
		enableProlonging = input.getInteger(PARAM_ENABLE_PROLONGING) >= 1;
		penaltyCostInEURperMWH = input.getTimeSeries(PARAM_PENALTY_COST);
	}

	/** @return effective self discharge rate for given duration, considering exponential reduction over time */
	private double calcSelfDischarge(TimeStamp time, TimeSpan duration) {
		return 1. - Math.pow(1 - selfDischargeRatePerHour.getValueLinear(time), calcDurationInHours(duration));
	}

	private double calcDurationInHours(TimeSpan duration) {
		return (double) duration.getSteps() / STEPS_PER_HOUR;
	}

	/** @return current internal energy level of this {@link GenericDevice} in MWh */
	public double getCurrentInternalEnergyInMWH() {
		return currentEnergyContentInMWH;
	}

	/** Returns external energy delta equivalent of given internal energy delta
	 * 
	 * @param internalEnergyDelta &gt; 0: charging; &lt; 0: depleting
	 * @param time of transition
	 * @return external energy delta equivalent */
	public double internalToExternalEnergy(double internalEnergyDelta, TimeStamp time) {
		if (internalEnergyDelta > 0) {
			return internalEnergyDelta / chargingEfficiency.getValueLinear(time);
		} else {
			return internalEnergyDelta * dischargingEfficiency.getValueLinear(time);
		}
	}

	/** Performs an actual transition from current energy content at given time using a given external energy delta. Enforces energy
	 * and power limits. Returns actual external energy delta considering applied limits. Logs errors in case limits are violated.
	 * 
	 * @param time at which charging occurs
	 * @param externalEnergyDeltaInMWH (dis-)charging energy applied to this device (positive: charging, negative: discharging)
	 * @param duration of the transition
	 * @return actual external energy delta for transition considering power and capacity limits */
	public double transition(TimeStamp time, double externalEnergyDeltaInMWH, TimeSpan duration) {
		double externalPowerInMW = ensurePowerWithinLimits(time, externalEnergyDeltaInMWH / calcDurationInHours(duration));
		double internalPowerInMW = externalToInternal(externalPowerInMW, time);
		double netChargingEnergyInMWH = (internalPowerInMW + netInflowPowerInMW.getValueLinear(time))
				* calcDurationInHours(duration);
		double selfDischargeRate = calcSelfDischarge(time, duration);
		selfDischargeRate = ensureNoNegativeSelfDischarge(time, selfDischargeRate);
		double selfDischargeLossInMWH = currentEnergyContentInMWH * selfDischargeRate;
		double finalEnergyContentInMWH = currentEnergyContentInMWH + netChargingEnergyInMWH - selfDischargeLossInMWH;
		finalEnergyContentInMWH = ensureEnergyWithinLimits(time, finalEnergyContentInMWH);
		double internalEnergyDeltaInMWH = finalEnergyContentInMWH - currentEnergyContentInMWH
				+ selfDischargeLossInMWH - netInflowPowerInMW.getValueLinear(time) * calcDurationInHours(duration);
		updateShiftTimeAndProlongingCost(currentEnergyContentInMWH, finalEnergyContentInMWH, duration, time);
		currentEnergyContentInMWH = finalEnergyContentInMWH;
		return internalToExternalEnergy(internalEnergyDeltaInMWH, time);
	}

	private void updateShiftTimeAndProlongingCost(double initialEnergyContentInMWH, double finalEnergyContentInMWH,
			TimeSpan duration, TimeStamp time) {
		if (maximumShiftTimeInSteps > 0) {
			lastProlongingCostInEUR = 0.;
			if (hasZeroEnergyContent(finalEnergyContentInMWH)) {
				currentShiftTimeInSteps = 0;
			} else if (isChangeOfSign(initialEnergyContentInMWH, finalEnergyContentInMWH)) {
				currentShiftTimeInSteps = duration.getSteps();
			} else if (enableProlonging && isProlongedShift(initialEnergyContentInMWH, finalEnergyContentInMWH, duration)
					&& isProlongingWithinPowerBounds(initialEnergyContentInMWH, finalEnergyContentInMWH, duration, time)) {
				currentShiftTimeInSteps = duration.getSteps();
				lastProlongingCostInEUR = 2 * initialEnergyContentInMWH * getVariableCostInEURperMWH(time);
			} else {
				currentShiftTimeInSteps += duration.getSteps();
			}
			if (currentShiftTimeInSteps > maximumShiftTimeInSteps) {
				logger.warn(WARN_SHIFT_TIME_EXCEEDED + (currentShiftTimeInSteps - maximumShiftTimeInSteps));
				currentShiftTimeInSteps = duration.getSteps();
				lastProlongingCostInEUR = Math.abs(finalEnergyContentInMWH) * getPenaltyCostInEURperMWH(time);
			}
		}
	}

	/** Returns true if given energy content is within zero storage level tolerance
	 * 
	 * @param energyContentInMWH to be evaluated
	 * @return true if given energy content is within zero storage level tolerance */
	public static boolean hasZeroEnergyContent(double energyContentInMWH) {
		return -TOLERANCE <= energyContentInMWH && energyContentInMWH <= TOLERANCE;
	}

	/** Returns true if energy content changes its sign
	 * 
	 * @param initialEnergyContent before transition
	 * @param finalEnergyContent after transition
	 * @return true if energy content changes its sign */
	public static boolean isChangeOfSign(double initialEnergyContent, double finalEnergyContent) {
		return Math.signum(finalEnergyContent) != Math.signum(initialEnergyContent);
	}

	/** Check for an initial balancing of parts of the flexibility device
	 * 
	 * @param chargingPower power charged (increment of energy storage level)
	 * @param shiftTime time that the flexibility device has been shifted for in one direction so far
	 * @param initialEnergyContent before transition
	 * @return whether shift is a prolonged shift */
	private boolean isProlongedShift(double chargingPower, double initialEnergyContent, TimeSpan duration) {
		if (currentShiftTimeInSteps + duration.getSteps() >= maximumShiftTimeInSteps) {
			double finalEnergyLevel = initialEnergyContent + chargingPower;
			return (initialEnergyContent > TOLERANCE && finalEnergyLevel > TOLERANCE) ||
					(initialEnergyContent < -TOLERANCE && finalEnergyLevel < -TOLERANCE);
		}
		return false;
	}

	/** Check whether shift is feasible within power bounds
	 * 
	 * @param energyToBalanceInMWH equal to initial energy content
	 * @param finalEnergyContentInMWH
	 * @return whether shift is feasible within power bounds */
	private boolean isProlongingWithinPowerBounds(double energyToBalanceInMWH, double finalEnergyContentInMWH,
			TimeSpan duration, TimeStamp time) {
		double maxNetChargingPowerInMW = getNetInflowInMW(time)
				+ getExternalChargingPowerInMW(time) * getChargingEfficiency(time);
		double maxNetDischargingPowerInMW = getNetInflowInMW(time)
				- getExternalDischargingPowerInMW(time) / getDischargingEfficiency(time);
		double intervalDurationInHours = (double) duration.getSteps() / STEPS_PER_HOUR;
		double currentUpshiftEnergyLimitInMWH = maxNetChargingPowerInMW * intervalDurationInHours;
		double currentDownshiftEnergyLimitInMWH = maxNetDischargingPowerInMW * intervalDurationInHours;
		if (energyToBalanceInMWH > 0) {
			double downshiftShareForBalance = Math.min(1., energyToBalanceInMWH / -currentDownshiftEnergyLimitInMWH);
			return finalEnergyContentInMWH <= (1 - downshiftShareForBalance) * currentUpshiftEnergyLimitInMWH;
		} else {
			double upshiftShareForBalance = Math.min(1., -energyToBalanceInMWH / currentUpshiftEnergyLimitInMWH);
			return finalEnergyContentInMWH >= (1 - upshiftShareForBalance) * currentDownshiftEnergyLimitInMWH;
		}
	}

	/** Returns internal energy delta or power equivalent of given external energy delta or power
	 * 
	 * @param externalValue &gt; 0: charging; &lt; 0: depleting
	 * @param time of transition
	 * @return corresponding internal energy delta or power equivalent */
	private double externalToInternal(double externalValue, TimeStamp time) {
		if (externalValue > 0) {
			return externalValue * chargingEfficiency.getValueLinear(time);
		} else {
			return externalValue / dischargingEfficiency.getValueLinear(time);
		}
	}

	/** Logs an error if the given external (dis-)charging power exceeds its corresponding limit.
	 * 
	 * @param time at which the power is applied
	 * @param powerInMW positive values represent charging
	 * @return the applied (dis-)charging power - capped at its corresponding maximum at the given time */
	private double ensurePowerWithinLimits(TimeStamp time, double powerInMW) {
		if (powerInMW > externalChargingPowerInMW.getValueLinear(time) + TOLERANCE) {
			logger.error(time + ERR_EXCEED_CHARGING_POWER + (powerInMW - externalChargingPowerInMW.getValueLinear(time)));
			powerInMW = externalChargingPowerInMW.getValueLinear(time);
		} else if (powerInMW < -externalDischargingPowerInMW.getValueLinear(time) - TOLERANCE) {
			logger
					.error(time + ERR_EXCEED_DISCHARGING_POWER + (externalDischargingPowerInMW.getValueLinear(time) + powerInMW));
			powerInMW = -externalDischargingPowerInMW.getValueLinear(time);
		}
		return powerInMW;
	}

	/** Logs an error if the given internal target energy content exceeds its upper or lower limit.
	 * 
	 * @param time at which the energy content shall be applied
	 * @param targetEnergyContentInMWH to be checked for consistency with energy content limits
	 * @return valid energy content closest to provided energy content target */
	private double ensureEnergyWithinLimits(TimeStamp time, double targetEnergyContentInMWH) {
		if (targetEnergyContentInMWH > energyContentUpperLimitInMWH.getValueLinear(time) + TOLERANCE) {
			double exceedance = (targetEnergyContentInMWH - energyContentUpperLimitInMWH.getValueLinear(time));
			logger.error(time + ERR_EXCEED_UPPER_ENERGY_LIMIT + exceedance);
			targetEnergyContentInMWH = energyContentUpperLimitInMWH.getValueLinear(time);
		} else if (targetEnergyContentInMWH < energyContentLowerLimitInMWH.getValueLinear(time) - TOLERANCE) {
			double exceedance = (energyContentLowerLimitInMWH.getValueLinear(time) - targetEnergyContentInMWH);
			logger.error(time + ERR_EXCEED_LOWER_ENERGY_LIMIT + exceedance);
			targetEnergyContentInMWH = energyContentLowerLimitInMWH.getValueLinear(time);
		}
		return targetEnergyContentInMWH;
	}

	/** If self discharge is applied based on a negative energy content, return 0 and log an error */
	private double ensureNoNegativeSelfDischarge(TimeStamp time, double selfDischargeRate) {
		if (currentEnergyContentInMWH < -TOLERANCE && selfDischargeRate > 0) {
			logger.error(ERR_NEGATIVE_SELF_DISCHARGE + time);
			return 0;
		}
		return selfDischargeRate;
	}

	/** Returns lower limit of energy content at given time
	 * 
	 * @param time at which the lower limit is to be returned
	 * @return lower energy content limit in MWh */
	public double getEnergyContentLowerLimitInMWH(TimeStamp time) {
		return energyContentLowerLimitInMWH.getValueLinear(time);
	}

	/** Returns upper limit of energy content at given time
	 * 
	 * @param time at which the upper limit is to be returned
	 * @return upper energy content limit in MWh */
	public double getEnergyContentUpperLimitInMWH(TimeStamp time) {
		return energyContentUpperLimitInMWH.getValueLinear(time);
	}

	/** Returns hourly self discharge rate at given time
	 * 
	 * @param time at which the self discharge rate is to be returned
	 * @return hourly self discharge rate */
	public double getSelfDischargeRate(TimeStamp time) {
		return selfDischargeRatePerHour.getValueLinear(time);
	}

	/** Returns charging efficiency at given time
	 * 
	 * @param time at which to return charging efficiency
	 * @return charging efficiency at given time */
	protected double getChargingEfficiency(TimeStamp time) {
		return chargingEfficiency.getValueLinear(time);
	}

	/** Returns discharging efficiency at given time
	 * 
	 * @param time at which to return discharging efficiency
	 * @return discharging efficiency at given time */
	protected double getDischargingEfficiency(TimeStamp time) {
		return dischargingEfficiency.getValueLinear(time);
	}

	/** Returns net inflow power at given time
	 * 
	 * @param time at which to return the net inflow power
	 * @return net inflow power at given time in MW */
	protected double getNetInflowInMW(TimeStamp time) {
		return netInflowPowerInMW.getValueLinear(time);
	}

	/** Returns external charging power at given time
	 * 
	 * @param time at which to return the external charging power
	 * @return external charging power at given time in MW */
	public double getExternalChargingPowerInMW(TimeStamp time) {
		return externalChargingPowerInMW.getValueLinear(time);
	}

	/** Returns external discharging power at given time
	 * 
	 * @param time at which to return the external discharging power
	 * @return external discharging power at given time in MW */
	public double getExternalDischargingPowerInMW(TimeStamp time) {
		return externalDischargingPowerInMW.getValueLinear(time);
	}

	/** Returns variable cost at given time
	 * 
	 * @param time at which to return the variable cost
	 * @return variable cost at given time in EUR per MWh */
	public double getVariableCostInEURperMWH(TimeStamp time) {
		return variableCostInEURperMWH.getValueLinear(time);
	}

	/** Returns penalty cost at given time
	 * 
	 * @param time at which to return the penalty cost
	 * @return penalty cost at given time in EUR per MWh */
	public double getPenaltyCostInEURperMWH(TimeStamp time) {
		return penaltyCostInEURperMWH.getValueLinear(time);
	}

	/** Returns current shift time in steps
	 * 
	 * @return current shift time in steps */
	public long getCurrentShiftTimeInSteps() {
		return currentShiftTimeInSteps;
	}

	/** Returns maximum shift time; if 0, no shift time limit applies
	 * 
	 * @return maximum shift time span */
	public TimeSpan getMaximumShiftTime() {
		return new TimeSpan(maximumShiftTimeInSteps < 0 ? 0 : maximumShiftTimeInSteps);
	}

	/** Returns prolonging cost from last transition in EUR
	 * 
	 * @return prolonging cost from last transition in EUR */
	public double getLastProlongingCostInEUR() {
		return lastProlongingCostInEUR;
	}

	/** Returns true if prolonging is allowed
	 * 
	 * @return true if prolonging is allowed */
	public boolean hasProlonging() {
		return enableProlonging;
	}
}
