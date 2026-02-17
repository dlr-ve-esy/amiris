// SPDX-FileCopyrightText: 2026 German Aerospace Center <amiris@dlr.de>
//
// SPDX-License-Identifier: Apache-2.0
package communications.portable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static testUtils.Exceptions.assertThrowsMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import agents.forecast.sensitivity.MarketClearingAssessment;
import agents.markets.meritOrder.MarketClearingResult;
import communications.portable.Sensitivity.InterpolationType;

/** Tests for {@link Sensitivity}
 * 
 * @author Christoph Schimeczek */
public class SensitivityTest {
	private final double[] emptyArray = new double[] {};

	private Sensitivity sensitivity;

	public class MarketClearingAssessmentSimple implements MarketClearingAssessment {
		final double[] demandPowers;
		final double[] demandValues;
		final double[] supplyPowers;
		final double[] supplyValues;

		public MarketClearingAssessmentSimple(double[] demandPowers, double[] demandValues, double[] supplyPowers,
				double[] supplyValues) {
			this.demandPowers = demandPowers;
			this.demandValues = demandValues;
			this.supplyPowers = supplyPowers;
			this.supplyValues = supplyValues;
		}

		@Override
		public void assess(MarketClearingResult clearingResult) {}

		@Override
		public double[] getDemandSensitivityPowers() {
			return demandPowers;
		}

		@Override
		public double[] getDemandSensitivityValues() {
			return demandValues;
		}

		@Override
		public double[] getSupplySensitivityPowers() {
			return supplyPowers;
		}

		@Override
		public double[] getSupplySensitivityValues() {
			return supplyValues;
		}
	}

	/** @return new MarketClearingAssessment */
	private MarketClearingAssessment buildAssessment(double[] demandPowers, double[] demandValues, double[] supplyPowers,
			double[] supplyValues) {
		return new MarketClearingAssessmentSimple(demandPowers, demandValues, supplyPowers, supplyValues);
	}

	@ParameterizedTest
	@CsvSource(value = {"-5", "-4.2", "0", "42.1", "50"})
	public void construct_getMultiplier_returnsSetMultiplier(double multiplier) {
		MarketClearingAssessment assessment = buildAssessment(emptyArray, emptyArray, emptyArray, emptyArray);
		sensitivity = new Sensitivity(assessment, multiplier);
		assertEquals(multiplier, sensitivity.getMultiplier(), 1E-12);
	}

	@ParameterizedTest
	@CsvSource(value = {"-5:1.2", "-4.2:0", "0:-1", "42.1:-2", "50:3"}, delimiter = ':')
	public void updateMultiplier_getMultiplier_returnsNewMultiplier(double originalMultiplier, double newMultiplier) {
		MarketClearingAssessment assessment = buildAssessment(emptyArray, emptyArray, emptyArray, emptyArray);
		sensitivity = new Sensitivity(assessment, originalMultiplier);
		sensitivity.updateMultiplier(newMultiplier);
		assertEquals(newMultiplier, sensitivity.getMultiplier(), 1E-12);
	}

	@Test
	public void getValue_noRequestedEnergy_returnZero() {
		MarketClearingAssessment assessment = buildAssessment(emptyArray, emptyArray, emptyArray, emptyArray);
		sensitivity = new Sensitivity(assessment, 2.0);
		assertEquals(0, sensitivity.getValue(0), 1E-12);
	}

	@ParameterizedTest
	@CsvSource(value = {"-5", "-4.2", "0", "42.1", "50"})
	public void getValue_multiplierZero_returnsZero(double requestedEnergy) {
		MarketClearingAssessment assessment = buildAssessment(emptyArray, emptyArray, emptyArray, emptyArray);
		sensitivity = new Sensitivity(assessment, 0);
		assertEquals(0, sensitivity.getValue(requestedEnergy), 1E-12);
	}

	@Test
	public void getValue_demandPowersExceeded_returnNaN() {
		MarketClearingAssessment assessment = buildAssessment(emptyArray, emptyArray, emptyArray, emptyArray);
		sensitivity = new Sensitivity(assessment, 2.0);
		assertTrue(Double.isNaN(sensitivity.getValue(1.0)));
	}

	@Test
	public void getValue_supplyPowersExceeded_returnNaN() {
		MarketClearingAssessment assessment = buildAssessment(emptyArray, emptyArray, emptyArray, emptyArray);
		sensitivity = new Sensitivity(assessment, 2.0);
		assertTrue(Double.isNaN(sensitivity.getValue(-1.0)));
	}

	@Test
	public void getValue_interpolationTypeMissing_throws() {
		MarketClearingAssessment assessment = buildAssessment(array(0, 10), array(0, 10), emptyArray, emptyArray);
		sensitivity = new Sensitivity(assessment, 2.0);
		assertThrowsMessage(RuntimeException.class, Sensitivity.ERR_INTERPOLATION_TYPE_MISSING,
				() -> sensitivity.getValue(1.0));
	}

	/** returns given double values as array */
	private double[] array(double... vals) {
		return vals;
	}

	@ParameterizedTest
	@CsvSource(value = {"7:700", "4:640", "2:38", "1.5:28.5", "0.5:0.5"}, delimiter = ':')
	public void getValue_multiplierOneDirect_returnsCorrectValue(double requestedEnergy, double expectedValue) {
		MarketClearingAssessment assessment = buildAssessment(array(0, 1, 2, 5, 10), array(0, 1, 20, 500, 1000),
				array(0, 1, 2, 5, 10), array(0, 1, 20, 500, 1000));
		sensitivity = new Sensitivity(assessment, 1.0);
		sensitivity.setInterpolationType(InterpolationType.DIRECT);
		assertEquals(expectedValue, sensitivity.getValue(requestedEnergy), 1E-12);
		assertEquals(expectedValue, sensitivity.getValue(-requestedEnergy), 1E-12);
	}

	@ParameterizedTest
	@CsvSource(value = {"3.5:700", "2:640", "1:38", "0.75:28.5", "0.25:0.5"}, delimiter = ':')
	public void getValue_multiplierTwoDirect_returnsCorrectValue(double requestedEnergy, double expectedValue) {
		MarketClearingAssessment assessment = buildAssessment(array(0, 1, 2, 5, 10), array(0, 1, 20, 500, 1000),
				array(0, 1, 2, 5, 10), array(0, 1, 20, 500, 1000));
		sensitivity = new Sensitivity(assessment, 2.0);
		sensitivity.setInterpolationType(InterpolationType.DIRECT);
		assertEquals(expectedValue, sensitivity.getValue(requestedEnergy), 1E-12);
		assertEquals(expectedValue, sensitivity.getValue(-requestedEnergy), 1E-12);
	}

	@ParameterizedTest
	@CsvSource(value = {"7:700", "4:340", "2:20", "1.5:10.5", "0.5:0.5"}, delimiter = ':')
	public void getValue_multiplierOneCumulative_returnsCorrectValue(double requestedEnergy, double expectedValue) {
		MarketClearingAssessment assessment = buildAssessment(array(0, 1, 2, 5, 10), array(0, 1, 20, 500, 1000),
				array(0, 1, 2, 5, 10), array(0, 1, 20, 500, 1000));
		sensitivity = new Sensitivity(assessment, 1.0);
		sensitivity.setInterpolationType(InterpolationType.CUMULATIVE);
		assertEquals(expectedValue, sensitivity.getValue(requestedEnergy), 1E-12);
		assertEquals(expectedValue, sensitivity.getValue(-requestedEnergy), 1E-12);
	}

	@ParameterizedTest
	@CsvSource(value = {"3.5:700", "2:340", "1:20", "0.75:10.5", "0.25:0.5"}, delimiter = ':')
	public void getValue_multiplierTwoCumulative_returnsCorrectValue(double requestedEnergy, double expectedValue) {
		MarketClearingAssessment assessment = buildAssessment(array(0, 1, 2, 5, 10), array(0, 1, 20, 500, 1000),
				array(0, 1, 2, 5, 10), array(0, 1, 20, 500, 1000));
		sensitivity = new Sensitivity(assessment, 2.0);
		sensitivity.setInterpolationType(InterpolationType.CUMULATIVE);
		assertEquals(expectedValue, sensitivity.getValue(requestedEnergy), 1E-12);
		assertEquals(expectedValue, sensitivity.getValue(-requestedEnergy), 1E-12);
	}

	@ParameterizedTest
	@CsvSource(value = {"7:100", "4:160", "2:19", "1.5:19", "0.5:1"}, delimiter = ':')
	public void getPriceInEURperMWH_multiplierOne_returnsCorrectValue(double requestedEnergy, double expectedValue) {
		MarketClearingAssessment assessment = buildAssessment(array(0, 1, 2, 5, 10), array(0, 1, 20, 500, 1000),
				array(0, 1, 2, 5, 10), array(0, 1, 20, 500, 1000));
		sensitivity = new Sensitivity(assessment, 1.0);
		assertEquals(expectedValue, sensitivity.getPriceInEURperMWH(requestedEnergy), 1E-12);
		assertEquals(expectedValue, sensitivity.getPriceInEURperMWH(-requestedEnergy), 1E-12);
	}

	@ParameterizedTest
	@CsvSource(value = {"3.5:100", "2:160", "1:19", "0.75:19", "0.25:1"}, delimiter = ':')
	public void getPriceInEURperMWH_multiplierTwo_returnsCorrectValue(double requestedEnergy, double expectedValue) {
		MarketClearingAssessment assessment = buildAssessment(array(0, 1, 2, 5, 10), array(0, 1, 20, 500, 1000),
				array(0, 1, 2, 5, 10), array(0, 1, 20, 500, 1000));
		sensitivity = new Sensitivity(assessment, 2.0);
		assertEquals(expectedValue, sensitivity.getPriceInEURperMWH(requestedEnergy), 1E-12);
		assertEquals(expectedValue, sensitivity.getPriceInEURperMWH(-requestedEnergy), 1E-12);
	}

	@Test
	public void getPriceInEURperMWH_demandPowersExceeded_returnNaN() {
		MarketClearingAssessment assessment = buildAssessment(emptyArray, emptyArray, emptyArray, emptyArray);
		sensitivity = new Sensitivity(assessment, 2.0);
		assertTrue(Double.isNaN(sensitivity.getPriceInEURperMWH(1.0)));
	}

	@Test
	public void getPriceInEURperMWH_supplyPowersExceeded_returnNaN() {
		MarketClearingAssessment assessment = buildAssessment(emptyArray, emptyArray, emptyArray, emptyArray);
		sensitivity = new Sensitivity(assessment, 2.0);
		assertTrue(Double.isNaN(sensitivity.getPriceInEURperMWH(-1.0)));
	}
}
