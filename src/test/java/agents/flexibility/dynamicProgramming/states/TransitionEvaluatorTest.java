// SPDX-FileCopyrightText: 2026 German Aerospace Center <amiris@dlr.de>
//
// SPDX-License-Identifier: Apache-2.0
package agents.flexibility.dynamicProgramming.states;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import agents.flexibility.GenericDeviceCache;
import agents.flexibility.dynamicProgramming.assessment.AssessmentFunction;
import de.dlr.gitlab.fame.time.TimeStamp;

/** Tests for {@link TransitionEvaluator} */
public class TransitionEvaluatorTest {
	private static final TimeStamp THE_TIME = new TimeStamp(0);

	private StateDiscretiser discretiser;
	private GenericDeviceCache deviceCache;
	private AssessmentFunction assessmentFunction;
	private TransitionEvaluator evaluator;

	@BeforeEach
	public void setUp() {
		this.discretiser = mock(StateDiscretiser.class);
		this.deviceCache = mock(GenericDeviceCache.class);
		this.assessmentFunction = mock(AssessmentFunction.class);
		this.evaluator = new TransitionEvaluator(discretiser, deviceCache, assessmentFunction);
	}

	@ParameterizedTest
	@CsvSource({"true", "false"})
	public void prepareFor_callsPrepareForOnAssessmentFunction(boolean hasSelfDischarge) {
		evaluator.prepareFor(THE_TIME, hasSelfDischarge);
		verify(assessmentFunction, times(1)).prepareFor(THE_TIME);
	}

	@ParameterizedTest
	@CsvSource({"true", "false"})
	public void prepareFor_callsPrepareForOnDeviceCache(boolean hasSelfDischarge) {
		evaluator.prepareFor(THE_TIME, hasSelfDischarge);
		verify(deviceCache, times(1)).prepareFor(THE_TIME);
	}

	@Test
	public void prepareFor_noSelfDischarge_cachesTransitions() {
		evaluator.prepareFor(THE_TIME, false);
		verify(assessmentFunction, atLeast(1)).assessTransition(anyDouble());
	}

	@Test
	public void prepareFor_selfDischarge_doesNotcacheTransitions() {
		evaluator.prepareFor(THE_TIME, true);
		verify(assessmentFunction, times(0)).assessTransition(anyDouble());
	}

	@ParameterizedTest
	@CsvSource(value = {"1.:1.:0:0:0.", "1.:1.:1:0:1.", "1.:2.:1:0:2.", "2.:3.:3:1:12.", "2.:3.:-1:3:-24."},
			delimiter = ':')
	public void getTransitionValue_uncached_returnsCorrectValue(double energyResolution, double valuePerMWH,
			int initialIndex, int finalIndex, double expectedValue) {
		mockDiscretisation(energyResolution);
		mockTransition();
		mockAssessment(valuePerMWH);
		evaluator.prepareFor(THE_TIME, true);
		double result = evaluator.getTransitionValueFor(initialIndex, finalIndex);
		assertEquals(expectedValue, result, 1E-12);
	}

	/** Prepares {@link #discretiser} to translate energy indices to energy content */
	private void mockDiscretisation(double energyResolution) {
		when(discretiser.energyIndexToEnergyInMWH(anyInt()))
				.thenAnswer(input -> (int) input.getArgument(0) * energyResolution);
		when(discretiser.discretiseEnergyDelta(anyDouble()))
				.thenAnswer(input -> (int) ((double) input.getArgument(0) / energyResolution));
	}

	private void mockTransition() {
		when(deviceCache.simulateTransition(anyDouble(), anyDouble()))
				.thenAnswer(input -> (double) input.getArgument(1) - (double) input.getArgument(0));
	}

	private void mockAssessment(double valuePerMWH) {
		when(assessmentFunction.assessTransition(anyDouble()))
				.thenAnswer(input -> -(double) input.getArgument(0) * valuePerMWH);
	}

	@ParameterizedTest
	@CsvSource(value = {"1.:1.:0:0:0.", "1.:1.:1:0:1.", "1.:2.:1:0:2.", "2.:3.:3:1:12.", "2.:3.:-1:3:-24."},
			delimiter = ':')
	public void getTransitionValue_cached_returnsCorrectValue(double energyResolution, double valuePerMWH,
			int initialIndex, int finalIndex, double expectedValue) {
		mockDiscretisation(energyResolution);
		mockTransition();
		mockAssessment(valuePerMWH);
		when(deviceCache.getMaxNetChargingEnergyInMWH()).thenReturn(10.);
		when(deviceCache.getMaxNetDischargingEnergyInMWH()).thenReturn(-10.);
		evaluator.prepareFor(THE_TIME, false);
		double result = evaluator.getTransitionValueFor(initialIndex, finalIndex);
		assertEquals(expectedValue, result, 1E-12);
	}

	@ParameterizedTest
	@CsvSource(value = {"-2:-12:2:0:2", "0:-12:2:0:2", "12:2:0:2:-2", "12:0:0:2:-2", "-2:-12:0:0:0"}, delimiter = ':')
	public void getTransitionValue_cachedWithOutflow_returnsCorrectValue(double maxChargingInMWH,
			double maxDischargingInMWH, int initialIndex, int finalIndex, double expectedValue) {
		mockDiscretisation(1);
		mockTransition();
		mockAssessment(1);
		when(deviceCache.getMaxNetChargingEnergyInMWH()).thenReturn(maxChargingInMWH);
		when(deviceCache.getMaxNetDischargingEnergyInMWH()).thenReturn(maxDischargingInMWH);
		evaluator.prepareFor(THE_TIME, false);
		double result = evaluator.getTransitionValueFor(initialIndex, finalIndex);
		assertEquals(expectedValue, result, 1E-12);
	}
}
