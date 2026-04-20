// SPDX-FileCopyrightText: 2026 German Aerospace Center <amiris@dlr.de>
//
// SPDX-License-Identifier: Apache-2.0
package agents.flexibility.dynamicProgramming;

import agents.flexibility.dynamicProgramming.states.StateManager;

/** Assesses a dispatch planning situation and identifies problems.
 * 
 * @author Christoph Schimeczek */
public class OptimisationProblemAssessor {
	static final String ERR_NO_INITIAL_STATES = "No initial states exist. Check consistency of upper and lower energy limits.";
	static final String ERR_NO_FINAL_STATES = "No final states can be reached. Check charging and discharging power limits as well as inflow / outflow.";
	static final String ERR_NO_TRANSITIONS = "No valid transitions: %d transitions exceeded merit order limits, %d final states had no feasible follow-up path. Check competition multiplier history or merit order situation: Merit order might provide insufficient (dis-)charging options.";

	/** Identifies dispatch planning problems and returns an error message specifying problem
	 * 
	 * @param stateManager that was used when the dispatch planning failed
	 * @param bestValuesNextPeriod that was used when the dispatch planning failed
	 * @return an error message hinting on what could have been the problem and how it might be solved */
	public static String identifyProblems(StateManager stateManager, double[] bestValuesNextPeriod) {
		if (!hasAnyInitialState(stateManager)) {
			return ERR_NO_INITIAL_STATES;
		}
		if (!hasAnyFinalState(stateManager)) {
			return ERR_NO_FINAL_STATES;
		}
		return assessTransitions(stateManager, bestValuesNextPeriod);
	}

	/** @return true if any initial state exists */
	private static boolean hasAnyInitialState(StateManager stateManager) {
		int[] initialStateList = getInitialStateList(stateManager);
		return initialStateList.length > 0;
	}

	/** @return list of available initial states */
	private static int[] getInitialStateList(StateManager stateManager) {
		return stateManager.useStateList() ? stateManager.getInitialStates()
				: boundariesToList(stateManager.getInitialStates());
	}

	/** Transform state index boundaries to state index list */
	private static int[] boundariesToList(int[] boundaries) {
		int[] statesList;
		if (boundaries.length < 2) {
			statesList = new int[0];
		} else {
			statesList = new int[Math.max(0, boundaries[1] - boundaries[0] + 1)];
			int listIndex = 0;
			for (int stateIndex = boundaries[0]; stateIndex <= boundaries[1]; stateIndex++) {
				statesList[listIndex] = stateIndex;
				listIndex++;
			}
		}
		return statesList;
	}

	/** @return true if any (valid) final state can be reached */
	private static boolean hasAnyFinalState(StateManager stateManager) {
		for (int initialStateIndex : getInitialStateList(stateManager)) {
			int[] finalStates = getFinalStateList(stateManager, initialStateIndex);
			if (finalStates.length > 0 && finalStates[0] >= 0) {
				return true;
			}
		}
		return false;
	}

	/** @return list of available final states */
	private static int[] getFinalStateList(StateManager stateManager, int initialStateIndex) {
		return stateManager.useStateList() ? stateManager.getFinalStates(initialStateIndex)
				: boundariesToList(stateManager.getFinalStates(initialStateIndex));
	}

	/** @return error message based on validity assessment of transitions */
	private static String assessTransitions(StateManager stateManager, double[] bestValuesNextPeriod) {
		int invalidTransitionCount = 0, invalidStateCount = 0;
		for (int initialStateIndex : getInitialStateList(stateManager)) {
			for (int finalStateIndex : getFinalStateList(stateManager, initialStateIndex)) {
				if (finalStateIndex >= 0) {
					if (Math.abs(bestValuesNextPeriod[finalStateIndex]) == Double.MAX_VALUE) {
						invalidStateCount++;
					}
					if (Double.isNaN(stateManager.getTransitionValueFor(initialStateIndex, finalStateIndex))) {
						invalidTransitionCount++;
					}
				}
			}
		}
		return String.format(ERR_NO_TRANSITIONS, invalidTransitionCount, invalidStateCount);
	}
}
