// SPDX-FileCopyrightText: 2025 German Aerospace Center <amiris@dlr.de>
//
// SPDX-License-Identifier: Apache-2.0
package agents.flexibility.dynamicProgramming.states;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static testUtils.Exceptions.assertThrowsMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import de.dlr.gitlab.fame.time.Constants.Interval;
import de.dlr.gitlab.fame.time.TimeSpan;

/** Tests for {@link StateDiscretiser} */
public class StateDiscretiserTest {
	private StateDiscretiser discretiser;
	private TimeSpan oneHour = new TimeSpan(1, Interval.HOURS);

	@Test
	public void constructor_invalidEnergyResolution_throws() {
		assertThrowsMessage(RuntimeException.class, StateDiscretiser.ERR_INVALID_ENERGY_RESOLUTION,
				() -> new StateDiscretiser(-2., oneHour));
		assertThrowsMessage(RuntimeException.class, StateDiscretiser.ERR_INVALID_ENERGY_RESOLUTION,
				() -> new StateDiscretiser(0., oneHour));
	}

	@Test
	public void constructor_invalidTimeResolution_throws() {
		assertThrowsMessage(RuntimeException.class, StateDiscretiser.ERR_INVALID_TIME_RESOLUTION,
				() -> new StateDiscretiser(1., new TimeSpan(0)));
	}

	@Test
	public void setBoundaries_limitsInverted_throws() {
		discretiser = new StateDiscretiser(2, oneHour);
		assertThrowsMessage(RuntimeException.class, StateDiscretiser.ERR_INVERTED_ENERGY_LIMITS,
				() -> discretiser.setBoundaries(new double[] {5., -5.}, oneHour));
	}

	@Test
	public void getAllAvailableStates_noTimeState_returnsEnergyStatesOnly() {
		initDiscretiser(1., 3600, 0., 0., 0);
		assertArrayEquals(new int[] {0}, discretiser.getAllAvailableStates());
		initDiscretiser(1., 3600, -1., 1., 0);
		assertArrayEquals(new int[] {0, 1, 2}, discretiser.getAllAvailableStates());
		initDiscretiser(1., 3600, -2.7, 2.7, 0);
		assertArrayEquals(new int[] {0, 1, 2, 3, 4}, discretiser.getAllAvailableStates());
		initDiscretiser(1., 3600, -3.7, -1.7, 0);
		assertArrayEquals(new int[] {0, 1}, discretiser.getAllAvailableStates());
		initDiscretiser(1., 3600, 2.7, 3.7, 0);
		assertArrayEquals(new int[] {0}, discretiser.getAllAvailableStates());
	}

	private void initDiscretiser(double energyResolutionInMWH, long timeResolutionInSteps, double minEnergyInMWH,
			double maxEnergyInMWH, long maxShiftTimeInSteps) {
		discretiser = new StateDiscretiser(energyResolutionInMWH, new TimeSpan(timeResolutionInSteps));
		discretiser.setBoundaries(new double[] {minEnergyInMWH, maxEnergyInMWH}, new TimeSpan(maxShiftTimeInSteps));
	}

	@Test
	public void getAllAvailableStates_oneTimeState_returnsBalancedStateOnly() {
		initDiscretiser(1., 3600, -2., 10., 1800);
		assertArrayEquals(new int[] {2}, discretiser.getAllAvailableStates());
	}

	@Test
	public void getAllAvailableStates_multipleTimeStates_returnsValidStatesOnly() {
		initDiscretiser(1., 10, -1., 1., 40);
		assertArrayEquals(new int[] {1, 3, 5, 6, 8, 9, 11, 12, 14}, discretiser.getAllAvailableStates());
	}

	@ParameterizedTest
	@CsvSource(value = {"4.2:2", "1.99999999:1", "1.9:0", "11.:5",}, delimiter = ':')
	public void discretiseEnergyDelta_returnsExpected(double energyDelta, int expected) {
		initDiscretiser(2., 10, 0, 0, 10);
		assertEquals(expected, discretiser.discretiseEnergyDelta(energyDelta));
	}

	@ParameterizedTest
	@CsvSource(value = {"0:-4.", "1:-2.", "2:0.", "7:10."}, delimiter = ':')
	public void energyIndexToEnergyInMWH_returnsExpected(int energyIndex, double expected) {
		initDiscretiser(2., 10, -4, 10, 10);
		assertEquals(expected, discretiser.energyIndexToEnergyInMWH(energyIndex), 1E-10);
	}

	@ParameterizedTest
	@CsvSource(value = {"0:-3.", "1:0.", "4:9.", "5:-3.", "7:3.", "18:6.", "24:9.",}, delimiter = ':')
	public void getEnergyOfStateInMWH_withTimeConstraint_returnsExpected(int stateIndex, double expected) {
		initDiscretiser(3., 10, -4, 10, 50);
		assertEquals(expected, discretiser.getEnergyOfStateInMWH(stateIndex), 1E-10);
	}

	@ParameterizedTest
	@CsvSource(value = {"0:-4.", "1:-2.", "2:0.", "7:10."}, delimiter = ':')
	public void getEnergyOfStateInMWH_noTimeConstraint_returnsExpected(int stateIndex, double expected) {
		initDiscretiser(2., 10, -4, 10, 10);
		assertEquals(expected, discretiser.getEnergyOfStateInMWH(stateIndex), 1E-10);
	}

	@ParameterizedTest
	@CsvSource(value = {"0:1:1", "1:0:-1", "0:5:5", "0:6:0", "2:8:0", "2:9:1", "3:19:-2"}, delimiter = ':')
	public void getEnergyIndexDelta_returnsExpected(int initialStateIndex, int finalStateIndex, int expected) {
		initDiscretiser(2.5, 10, -4, 10, 50);
		assertEquals(expected, discretiser.getEnergyIndexDelta(initialStateIndex, finalStateIndex));
	}

	@Test
	public void getFollowUpStates_energyOnly_returnExpected() {
		initDiscretiser(3, 10, -4, 10, 0);
		assertArrayEquals(new int[] {0, 1, 2, 3}, discretiser.getFollowUpStates(0, -3., 6.));
		assertArrayEquals(new int[] {1, 2}, discretiser.getFollowUpStates(0, -1, 5.5));
	}
}
