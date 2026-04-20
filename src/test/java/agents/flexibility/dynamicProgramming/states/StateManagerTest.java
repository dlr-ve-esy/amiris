// SPDX-FileCopyrightText: 2025-2026 German Aerospace Center <amiris@dlr.de>
//
// SPDX-License-Identifier: Apache-2.0
package agents.flexibility.dynamicProgramming.states;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import agents.flexibility.GenericDevice;
import de.dlr.gitlab.fame.time.TimePeriod;
import de.dlr.gitlab.fame.time.TimeSpan;
import de.dlr.gitlab.fame.time.TimeStamp;

/** Tests for static methods in {@link StateManager} */
public class StateManagerTest {
	TimePeriod samplePeriod = new TimePeriod(new TimeStamp(0), new TimeSpan(10));

	@ParameterizedTest
	@CsvSource(value = {"0:10:0", "-5:5:1", "8:2:15", "9:3:1"}, delimiter = ':')
	public void getCurrentOptimisationTimeIndex_returnsExpected(int periodStart, int periodLength, int shifts) {
		TimePeriod period = new TimePeriod(new TimeStamp(periodStart), new TimeSpan(periodLength));
		TimeStamp time = period.shiftByDuration(shifts).getStartTime();
		assertEquals(shifts, StateManager.getCurrentOptimisationTimeIndex(time, period));
	}

	@ParameterizedTest
	@CsvSource(value = {"0:10:0:0", "0:10:1:10", "-3:2:2:1", "99:1:1:100"}, delimiter = ':')
	public void getTimeByIndex_returnsExpected(int periodStart, int periodLength, int shifts, long expected) {
		TimePeriod period = new TimePeriod(new TimeStamp(periodStart), new TimeSpan(periodLength));
		assertEquals(expected, StateManager.getTimeByIndex(period, shifts).getStep());
	}

	@Test
	public void hasSelfDischarge_noSelfDischarge_returnsFalse() {
		GenericDevice device = mockDeviceSelfDischarge(0, 0, 0, 0, 0, 1);
		assertFalse(StateManager.hasSelfDischarge(device, 5, samplePeriod));
	}

	/** @returns mocked {@link GenericDevice} that returns given self-discharge rates on request */
	private GenericDevice mockDeviceSelfDischarge(double... selfDischargeRates) {
		GenericDevice mockDevice = mock(GenericDevice.class);
		when(mockDevice.getSelfDischargeRate(any(TimeStamp.class))).thenAnswer(new Answer<Double>() {
			private int count = -1;

			public Double answer(InvocationOnMock invocation) {
				return selfDischargeRates[++count];
			}
		});
		return mockDevice;
	}

	@Test
	public void hasSelfDischarge_withSelfDischarge_returnsTrue() {
		GenericDevice device = mockDeviceSelfDischarge(0, 0, 0.1, 0, 0, 0);
		assertTrue(StateManager.hasSelfDischarge(device, 5, samplePeriod));
	}

	@ParameterizedTest
	@CsvSource(value = {"1:10:15", "2:0:15", "3:-10:15", "4:-10:20", "5:-20:20"}, delimiter = ':')
	public void analyseAvailableEnergyLevels_returnsExpected(int periodCount, double expectedLower,
			double expectedUpper) {
		GenericDevice device = mockDeviceLimits(new double[] {10, 0, -10, -10, -20}, new double[] {15, 10, 0, 20, -10});
		double[] result = StateManager.analyseAvailableEnergyLevels(device, periodCount, samplePeriod);
		assertArrayEquals(new double[] {expectedLower, expectedUpper}, result);
	}

	/** Returns mocked {@link GenericDevice} that returns given lower and upper energy content limits */
	private GenericDevice mockDeviceLimits(double[] lowerLimits, double[] upperLimits) {
		GenericDevice mockDevice = mock(GenericDevice.class);

		when(mockDevice.getEnergyContentLowerLimitInMWH(any(TimeStamp.class))).thenAnswer(new Answer<Double>() {
			private int count = -1;

			public Double answer(InvocationOnMock invocation) {
				return lowerLimits[++count];
			}
		});

		when(mockDevice.getEnergyContentUpperLimitInMWH(any(TimeStamp.class))).thenAnswer(new Answer<Double>() {
			private int count = -1;

			public Double answer(InvocationOnMock invocation) {
				return upperLimits[++count];
			}
		});
		return mockDevice;
	}

}
