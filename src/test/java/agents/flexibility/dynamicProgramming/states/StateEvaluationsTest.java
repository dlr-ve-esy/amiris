// SPDX-FileCopyrightText: 2026 German Aerospace Center <amiris@dlr.de>
//
// SPDX-License-Identifier: Apache-2.0
package agents.flexibility.dynamicProgramming.states;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import agents.flexibility.GenericDeviceCache;

public class StateEvaluationsTest {
	@ParameterizedTest
	@ValueSource(doubles = {0., 1., 1E12, -5., -1E2})
	public void calcSpecificValueInEURperMWH_closeToZero_returnsZero(double valueDelta) {
		assertEquals(0., StateEvaluations.calcSpecificValueInEURperMWH(StateEvaluations.PRECISION_GUARD / 2., 0), 1E-10);
		assertEquals(0., StateEvaluations.calcSpecificValueInEURperMWH(-StateEvaluations.PRECISION_GUARD / 2., 0), 1E-10);
	}

	@ParameterizedTest
	@CsvSource(value = {"100:1000:10", "-100:-100:1", "100:-10:-0.1", "-10:100:-10"}, delimiter = ':')
	public void calcSpecificValueInEURperMWH_notZeroEnergy_returnsExpected(double energy, double value, double expected) {
		assertEquals(expected, StateEvaluations.calcSpecificValueInEURperMWH(energy, value), 1E-10);
	}

	@ParameterizedTest
	@CsvSource(value = {"0:10:5:5:10", "0:10:2:-2:0", "-10:10:0:-3:-3", "-10:10:-3:5:2"}, delimiter = ':')
	public void calcNextEnergyInMWH_withinBounds_notChanged(double lowerLimit, double upperLimit, double currentEnergy,
			double delta, double expected) {
		GenericDeviceCache device = mockDeviceCacheLimits(lowerLimit, upperLimit);
		assertEquals(expected, StateEvaluations.calcNextEnergyInMWH(device, currentEnergy, delta), 1E-10);
	}

	/** Returns mocked {@link GenericDeviceCache} that returns given lower and upper energy content limits */
	private GenericDeviceCache mockDeviceCacheLimits(double lowerLimit, double upperLimit) {
		GenericDeviceCache mockDevice = mock(GenericDeviceCache.class);
		when(mockDevice.getEnergyContentLowerLimitInMWH()).thenReturn(lowerLimit);
		when(mockDevice.getEnergyContentUpperLimitInMWH()).thenReturn(upperLimit);
		return mockDevice;
	}

	@ParameterizedTest
	@CsvSource(
			value = {"-10:10:5:10:10", "-10:0:-2:3:0", "-10:-5:-7:3:-5", "5:10:7:-5:5", "0:10:3:-5:0", "-5:10:2:-10:-5"},
			delimiter = ':')
	public void calcNextEnergyInMWH_exceedBounds_limited(double lowerLimit, double upperLimit, double currentEnergy,
			double delta, double expected) {
		GenericDeviceCache device = mockDeviceCacheLimits(lowerLimit, upperLimit);
		assertEquals(expected, StateEvaluations.calcNextEnergyInMWH(device, currentEnergy, delta), 1E-10);
	}

	@ParameterizedTest
	@CsvSource(value = {"-10:10:-15:2:-13", "-10:10:-15:-2:-17", "0:10:15:2:17", "0:10:15:-2:13"}, delimiter = ':')
	public void calcNextEnergyInMWH_totallyOutOfBounds_notChanged(double lowerLimit, double upperLimit,
			double currentEnergy, double delta, double expected) {
		GenericDeviceCache device = mockDeviceCacheLimits(lowerLimit, upperLimit);
		assertEquals(expected, StateEvaluations.calcNextEnergyInMWH(device, currentEnergy, delta), 1E-10);
	}
}
