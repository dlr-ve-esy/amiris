// SPDX-FileCopyrightText: 2025-2026 German Aerospace Center <amiris@dlr.de>
//
// SPDX-License-Identifier: Apache-2.0
package agents.flexibility.dynamicProgramming.states;

import java.util.ArrayList;
import agents.flexibility.GenericDevice;
import agents.flexibility.GenericDeviceCache;
import agents.flexibility.dynamicProgramming.Optimiser;
import de.dlr.gitlab.fame.time.TimePeriod;
import de.dlr.gitlab.fame.time.TimeStamp;

/** Manages the states allowed within a dynamic programming optimisation
 * 
 * @author Christoph Schimeczek, Felix Nitsch, Johannes Kochems */
public interface StateManager {
	/** Used to avoid rounding errors in floating point calculation of transition steps */
	static final double PRECISION_GUARD = 1E-6;

	/** Contains the course of the internal energy levels, external energy deltas, and water values over a dispatch */
	public static class DispatchSchedule {
		public final double[] externalEnergyDeltasInMWH;
		public final double[] initialInternalEnergiesInMWH;
		public final double[] specificValuesInEURperMWH;
		public final double[] assumedElectricityPriceInEURperMWH;

		/** Instantiates new {@link DispatchSchedule}
		 * 
		 * @param externalEnergyDeltasInMWH course of external energy deltas during dispatch
		 * @param initialInternalEnergiesInMWH course of expected internal energy during dispatch
		 * @param specificValuesInEURperMWH estimated specific value of the dispatch decision */
		DispatchSchedule(double[] externalEnergyDeltasInMWH, double[] initialInternalEnergiesInMWH,
				double[] specificValuesInEURperMWH, double[] assumedElectricityPriceInEURperMWH) {
			this.externalEnergyDeltasInMWH = externalEnergyDeltasInMWH;
			this.initialInternalEnergiesInMWH = initialInternalEnergiesInMWH;
			this.specificValuesInEURperMWH = specificValuesInEURperMWH;
			this.assumedElectricityPriceInEURperMWH = assumedElectricityPriceInEURperMWH;
		}
	}

	/** Initialises {@link StateManager} to allow for planning in current planning period
	 * 
	 * @param startingPeriod first time period of an upcoming planning */
	void initialise(TimePeriod startingPeriod);

	/** Makes {@link StateManager} aware of time currently under assessment
	 * 
	 * @param time to be assessed */
	void prepareFor(TimeStamp time);

	/** Tells whether {@link #getInitialStates()} and {@link #getFinalStates(int)} return a complete list of available states or
	 * just first and last index (inclusive)
	 * 
	 * @return true, if full state lists are returned; false, if exactly the first and last index are returned */
	boolean useStateList();

	/** Retrieves indices of initial states at prepared time; return value depends on result of {@link #useStateList()}:
	 * <ul>
	 * <li>if true, a complete list of available initial state indices is returned</li>
	 * <li>if false, only the first and last (inclusive) initial state index is returned</li>
	 * </ul>
	 * 
	 * @return state indices available at prepared time */
	int[] getInitialStates();

	/** Retrieves indices of possible final state for given initial state index at prepared time; return value depends on result of
	 * {@link #useStateList()}:
	 * <ul>
	 * <li>if true, a complete list of available final state indices is returned</li>
	 * <li>if false, only the first and last (inclusive) final state index is returned</li>
	 * </ul>
	 * 
	 * @param initialStateIndex index of state at the begin of a transition
	 * @return final state indices reachable from given initial state at prepared time */
	int[] getFinalStates(int initialStateIndex);

	/** Gets a transition value from the transition from an initial to a final state
	 * 
	 * @param initialStateIndex index of state at the begin of a transition
	 * @param finalStateIndex index of state at the end of a transition
	 * @return value of the transition between two states */
	double getTransitionValueFor(int initialStateIndex, int finalStateIndex);

	/** Gets best assessment values for all states in the next period
	 * 
	 * @return best assessment known for states in the next period */
	double[] getBestValuesNextPeriod();

	/** Updates the best final state for transition and log the associated best assessment value
	 * 
	 * @param initialStateIndex index of state at the begin of a transition
	 * @param bestFinalStateIndex index of state at the end of a transition
	 * @param bestAssessmentValue to be associated with this transition */
	void updateBestFinalState(int initialStateIndex, int bestFinalStateIndex, double bestAssessmentValue);

	/** Gets number of time intervals within the foresight horizon
	 * 
	 * @return number of time intervals */
	int getNumberOfForecastTimeSteps();

	/** Returns the {@link DispatchSchedule} from the starting period and the current state of the {@link GenericDevice}
	 * 
	 * @param schedulingSteps number of scheduling steps
	 * @return dispatch schedule extending over the given number of scheduling steps */
	DispatchSchedule getBestDispatchSchedule(int schedulingSteps);

	/** Returns starting time of each planning interval in the planning horizon
	 * 
	 * @param startingPeriod first interval of the planning horizon
	 * @return list of starting times */
	ArrayList<TimeStamp> getPlanningTimes(TimePeriod startingPeriod);

	/** Builds and returns a list of starting times of time periods starting with the provided period repeated until the planning
	 * horizon is reached.
	 * 
	 * @param startingPeriod first interval of the planning horizon
	 * @param planningHorizonInHours total length of the planning horizon
	 * @return starting time of each planning interval in the planning horizon */
	static ArrayList<TimeStamp> createPlanningTimes(TimePeriod startingPeriod, double planningHorizonInHours) {
		int numberOfTimeSteps = Optimiser.calcHorizonInPeriodSteps(startingPeriod, planningHorizonInHours);
		ArrayList<TimeStamp> planningTimes = new ArrayList<>(numberOfTimeSteps);
		for (int step = 0; step < numberOfTimeSteps; step++) {
			planningTimes.add(startingPeriod.shiftByDuration(step).getStartTime());
		}
		return planningTimes;
	}

	/** Analyses which minimum lower energy level and maximum upper energy level apply during planning time
	 * 
	 * @param device whose energy levels are to be assessed
	 * @param numberOfTimeSteps of planning interval
	 * @param startingPeriod first period of planning interval
	 * @return lowest and highest energy level */
	static double[] analyseAvailableEnergyLevels(GenericDevice device, int numberOfTimeSteps, TimePeriod startingPeriod) {
		double minLowerLevel = Double.MAX_VALUE;
		double maxUpperLevel = -Double.MAX_VALUE;
		for (int timeIndex = 0; timeIndex < numberOfTimeSteps; timeIndex++) {
			TimeStamp time = getTimeByIndex(startingPeriod, timeIndex);
			double lowerLevel = device.getEnergyContentLowerLimitInMWH(time);
			double upperLevel = device.getEnergyContentUpperLimitInMWH(time);
			minLowerLevel = lowerLevel < minLowerLevel ? lowerLevel : minLowerLevel;
			maxUpperLevel = upperLevel > maxUpperLevel ? upperLevel : maxUpperLevel;
		}
		return new double[] {minLowerLevel, maxUpperLevel};
	}

	/** Returns first time of the given starting period shifted by time index
	 * 
	 * @param startingPeriod to begin with
	 * @param timeIndex number of times to shift the period
	 * @return time first time of the given starting period shifted by time index */
	static TimeStamp getTimeByIndex(TimePeriod startingPeriod, int timeIndex) {
		return startingPeriod.shiftByDuration(timeIndex).getStartTime();
	}

	/** Returns true if device has self-discharge within planning interval
	 * 
	 * @param device whose energy levels are to be assessed
	 * @param numberOfTimeSteps of planning interval
	 * @param startingPeriod first period of planning interval
	 * @return true if device has self-discharge within planning interval */
	static boolean hasSelfDischarge(GenericDevice device, int numberOfTimeSteps, TimePeriod startingPeriod) {
		for (int timeIndex = 0; timeIndex < numberOfTimeSteps; timeIndex++) {
			if (device.getSelfDischargeRate(StateManager.getTimeByIndex(startingPeriod, timeIndex)) > 0) {
				return true;
			}
		}
		return false;
	}

	/** Returns number of time shifts of given starting period to begin with given time
	 * 
	 * @param time to begin with
	 * @param startingPeriod first period of planning interval
	 * @return number of time shifts of given starting period to begin with given time */
	static int getCurrentOptimisationTimeIndex(TimeStamp time, TimePeriod startingPeriod) {
		return (int) ((time.getStep() - startingPeriod.getStartTime().getStep()) / startingPeriod.getDuration().getSteps());
	}

	/** Returns next energy level based on current one and planned energy delta; if current energy level is already out of bounds,
	 * do <b>not</b> force the planned next energy value onto a modelled energy level. This avoids unplanned dispatch purely because
	 * an energy level is out-of-bounds. Instead, follow the original dispatch plan.
	 * 
	 * @param deviceCache cached generic device prepared for time at which transition takes place
	 * @param currentInternalEnergyInMWH initial energy level of device
	 * @param plannedEnergyDeltaInMWH of transition
	 * @return next energy level based on current one and planned energy delta */
	static double calcNextEnergyInMWH(GenericDeviceCache deviceCache, double currentInternalEnergyInMWH,
			double plannedEnergyDeltaInMWH) {
		double lowerLevelInMWH = deviceCache.getEnergyContentLowerLimitInMWH();
		double upperLevelInMWH = deviceCache.getEnergyContentUpperLimitInMWH();
		double plannedNextEnergyContentInMWH = currentInternalEnergyInMWH + plannedEnergyDeltaInMWH;
		if (currentInternalEnergyInMWH >= lowerLevelInMWH && currentInternalEnergyInMWH <= upperLevelInMWH) {
			return Math.max(lowerLevelInMWH, Math.min(upperLevelInMWH, plannedNextEnergyContentInMWH));
		}
		return plannedNextEnergyContentInMWH;
	}

	/** Returns specific value in EUR per MWh of a transition with given deltas for energy and value
	 * 
	 * @param energyDeltaInMWH of transition
	 * @param valueDeltaInEUR of transition
	 * @return specific value of a transition with given deltas for energy and value */
	static double calcSpecificValueInEURperMWH(double energyDeltaInMWH, double valueDeltaInEUR) {
		if (Math.abs(energyDeltaInMWH) > PRECISION_GUARD) {
			return valueDeltaInEUR / energyDeltaInMWH;
		}
		return 0;
	}
}
