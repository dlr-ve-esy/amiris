// SPDX-FileCopyrightText: 2025-2026 German Aerospace Center <amiris@dlr.de>
//
// SPDX-License-Identifier: Apache-2.0
package agents.flexibility.dynamicProgramming;

import agents.flexibility.BidSchedule;
import agents.flexibility.GenericDevice;
import agents.flexibility.dynamicProgramming.bidding.BidScheduler;
import agents.flexibility.dynamicProgramming.states.StateManager;
import agents.flexibility.dynamicProgramming.states.StateManager.DispatchSchedule;
import de.dlr.gitlab.fame.time.Constants;
import de.dlr.gitlab.fame.time.TimePeriod;

/** {@link Optimiser} finds the best dispatch strategy for a {@link GenericDevice} using dynamic programming. The operational
 * states are controlled by a {@link StateManager}, which also assesses the value of transitions between states. The best
 * operation path is then obtained from the {@link StateManager} and a {@link BidScheduler} is used to obtain an associated
 * {@link BidSchedule}.
 * 
 * @author Christoph Schimeczek, Felix Nitsch, Johannes Kochems */
public final class Optimiser {
	/** Optimisation target */
	public enum Target {
		/** Maximise the value of a target function */
		MAXIMISE,
		/** Minimise the value of a target function */
		MINIMISE
	}

	private final StateManager stateManager;
	private final BidScheduler bidScheduler;
	private final double initialAssessmentValue;
	private final boolean isMaximisation;

	/** Instantiates new {@link Optimiser}
	 * 
	 * @param stateManager to control feasible states
	 * @param bidScheduler to create bidding schedules
	 * @param target type of optimisation target */
	public Optimiser(StateManager stateManager, BidScheduler bidScheduler, Target target) {
		this.stateManager = stateManager;
		this.bidScheduler = bidScheduler;
		isMaximisation = target == Target.MAXIMISE;
		initialAssessmentValue = isMaximisation ? -Double.MAX_VALUE : Double.MAX_VALUE;
	}

	public BidSchedule createSchedule(TimePeriod startingPeriod) {
		optimise(startingPeriod);
		int numberOfSchedulingSteps = calcHorizonInPeriodSteps(startingPeriod, bidScheduler.getScheduleHorizonInHours());
		DispatchSchedule dispatchSchedule = stateManager.getBestDispatchSchedule(numberOfSchedulingSteps);
		return bidScheduler.createBidSchedule(startingPeriod, dispatchSchedule);
	}

	/** Optimise dispatch following an optimisation target */
	private void optimise(TimePeriod startingPeriod) {
		stateManager.initialise(startingPeriod);
		for (int k = 0; k < stateManager.getNumberOfForecastTimeSteps(); k++) {
			int step = stateManager.getNumberOfForecastTimeSteps() - k - 1; // step backwards in time
			TimePeriod timePeriod = startingPeriod.shiftByDuration(step);
			stateManager.prepareFor(timePeriod.getStartTime());
			double[] bestValuesNextPeriod = stateManager.getBestValuesNextPeriod();
			if (stateManager.useStateList()) {
				optimiseWithStateList(bestValuesNextPeriod);
			} else {
				optimiseWithBoundaries(bestValuesNextPeriod);
			}
		}
	}

	/** Optimise using lists of initial and final state indices */
	private void optimiseWithStateList(double[] bestValuesNextPeriod) {
		for (int initialStateIndex : stateManager.getInitialStates()) {
			double bestAssessmentValue = initialAssessmentValue;
			int bestFinalStateIndex = Integer.MIN_VALUE;
			for (int finalStateIndex : stateManager.getFinalStates(initialStateIndex)) {
				double value = stateManager.getTransitionValueFor(initialStateIndex, finalStateIndex)
						+ bestValuesNextPeriod[finalStateIndex];
				if (compare(value, bestAssessmentValue)) {
					bestAssessmentValue = value;
					bestFinalStateIndex = finalStateIndex;
				}
			}
			stateManager.updateBestFinalState(initialStateIndex, bestFinalStateIndex, bestAssessmentValue);
		}
	}

	/** @return true if given value is better than provided bestValue, false otherwise */
	private boolean compare(double value, double bestValue) {
		if (isMaximisation) {
			return value > bestValue;
		} else {
			return value < bestValue;
		}
	}

	/** Optimise using lowest and highest state index */
	private void optimiseWithBoundaries(double[] bestValuesNextPeriod) {
		int[] initialBoundaries = stateManager.getInitialStates();
		for (int initialStateIndex = initialBoundaries[0]; initialStateIndex <= initialBoundaries[1]; initialStateIndex++) {
			double bestAssessmentValue = initialAssessmentValue;
			int bestFinalStateIndex = StateManager.STATE_INFEASIBLE;
			int[] finalBoundaries = stateManager.getFinalStates(initialStateIndex);
			for (int finalStateIndex = finalBoundaries[0]; finalStateIndex <= finalBoundaries[1]; finalStateIndex++) {
				double value = stateManager.getTransitionValueFor(initialStateIndex, finalStateIndex)
						+ bestValuesNextPeriod[finalStateIndex];
				if (compare(value, bestAssessmentValue)) {
					bestAssessmentValue = value;
					bestFinalStateIndex = finalStateIndex;
				}
			}
			stateManager.updateBestFinalState(initialStateIndex, bestFinalStateIndex, bestAssessmentValue);
		}
	}

	/** Calculates how many specified time periods fit into the given time horizon
	 * 
	 * @param timePeriod to fit into the time horizon
	 * @param horizonInHours time horizon in hours
	 * @return number of time periods */
	public static int calcHorizonInPeriodSteps(TimePeriod timePeriod, double horizonInHours) {
		double periodsPerHour = (double) Constants.STEPS_PER_HOUR / timePeriod.getDuration().getSteps();
		return (int) (horizonInHours * periodsPerHour);
	}
}
