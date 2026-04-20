// SPDX-FileCopyrightText: 2026 German Aerospace Center <amiris@dlr.de>
//
// SPDX-License-Identifier: Apache-2.0
package agents.flexibility.dynamicProgramming;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import agents.flexibility.dynamicProgramming.states.StateManager;

/** Test for {@link OptimisationProblemAssessor}
 * 
 * @author Christoph Schimeczek */
public class OptimisationProblemAssessorTest {
	private StateManager stateManager;
	private double[] bestValuesNextPeriod;

	@BeforeEach
	public void setUp() {
		stateManager = mock(StateManager.class);
	}

	@Test
	public void identifyProblems_listNoStates_returnsNoInitialStates() {
		mockStateManagerInitialStates(true, new int[] {});
		assertEquals(OptimisationProblemAssessor.ERR_NO_INITIAL_STATES,
				OptimisationProblemAssessor.identifyProblems(stateManager, bestValuesNextPeriod));
	}

	private void mockStateManagerInitialStates(boolean useList, int[] states) {
		when(stateManager.useStateList()).thenReturn(useList);
		when(stateManager.getInitialStates()).thenReturn(states);
	}

	@Test
	public void identifyProblems_boundariesEmpty_returnsNoInitialStates() {
		mockStateManagerInitialStates(false, new int[] {});
		assertEquals(OptimisationProblemAssessor.ERR_NO_INITIAL_STATES,
				OptimisationProblemAssessor.identifyProblems(stateManager, bestValuesNextPeriod));
	}

	@Test
	public void identifyProblems_boundariesOne_returnsNoInitialStates() {
		mockStateManagerInitialStates(false, new int[] {1});
		assertEquals(OptimisationProblemAssessor.ERR_NO_INITIAL_STATES,
				OptimisationProblemAssessor.identifyProblems(stateManager, bestValuesNextPeriod));
	}

	@Test
	public void identifyProblems_boundariesInverted_returnsNoInitialStates() {
		mockStateManagerInitialStates(false, new int[] {1, 0});
		assertEquals(OptimisationProblemAssessor.ERR_NO_INITIAL_STATES,
				OptimisationProblemAssessor.identifyProblems(stateManager, bestValuesNextPeriod));
	}

	@Test
	public void identifyProblems_listFinalStateEmpty_returnsNoFinalStates() {
		mockStateManagerInitialStates(true, new int[] {0});
		mockStateManagerFinalStates(new int[] {});
		assertEquals(OptimisationProblemAssessor.ERR_NO_FINAL_STATES,
				OptimisationProblemAssessor.identifyProblems(stateManager, bestValuesNextPeriod));
	}

	private void mockStateManagerFinalStates(int[] finalStates) {
		when(stateManager.getFinalStates(any(int.class))).thenReturn(finalStates);
	}

	@Test
	public void identifyProblems_listFinalStateInvalid_returnsNoFinalStates() {
		mockStateManagerInitialStates(true, new int[] {0});
		mockStateManagerFinalStates(new int[] {StateManager.STATE_INFEASIBLE});
		assertEquals(OptimisationProblemAssessor.ERR_NO_FINAL_STATES,
				OptimisationProblemAssessor.identifyProblems(stateManager, bestValuesNextPeriod));
	}

	@Test
	public void identifyProblems_boundariesFinalStateEmpty_returnsNoFinalStates() {
		mockStateManagerInitialStates(false, new int[] {0, 0});
		mockStateManagerFinalStates(new int[] {});
		assertEquals(OptimisationProblemAssessor.ERR_NO_FINAL_STATES,
				OptimisationProblemAssessor.identifyProblems(stateManager, bestValuesNextPeriod));
	}

	@Test
	public void identifyProblems_boundariesFinalStateInvalid_returnsNoFinalStates() {
		mockStateManagerInitialStates(false, new int[] {0, 0});
		mockStateManagerFinalStates(new int[] {StateManager.STATE_INFEASIBLE});
		assertEquals(OptimisationProblemAssessor.ERR_NO_FINAL_STATES,
				OptimisationProblemAssessor.identifyProblems(stateManager, bestValuesNextPeriod));
	}

	@Test
	public void identifyProblems_boundariesFinalStateInverted_returnsNoFinalStates() {
		mockStateManagerInitialStates(false, new int[] {0, 0});
		mockStateManagerFinalStates(new int[] {1, 0});
		assertEquals(OptimisationProblemAssessor.ERR_NO_FINAL_STATES,
				OptimisationProblemAssessor.identifyProblems(stateManager, bestValuesNextPeriod));
	}

	@Test
	public void identifyProblems_invalidTransitions_returnsNoTransitions() {
		mockStateManagerInitialStates(true, new int[] {0});
		mockStateManagerFinalStates(new int[] {0, 1, 2});
		mockTransitions(new double[] {Double.NaN, Double.NaN, 10}, new double[] {-Double.MAX_VALUE, 2, Double.MAX_VALUE});
		String expected = String.format(OptimisationProblemAssessor.ERR_NO_TRANSITIONS, 2, 2);
		assertEquals(expected, OptimisationProblemAssessor.identifyProblems(stateManager, bestValuesNextPeriod));
	}

	private void mockTransitions(double[] transitionValues, double[] nextValues) {
		bestValuesNextPeriod = nextValues;
		for (int index = 0; index < transitionValues.length; index++) {
			when(stateManager.getTransitionValueFor(0, index)).thenReturn(transitionValues[index]);
		}
	}
}
