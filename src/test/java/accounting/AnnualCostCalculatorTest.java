// SPDX-FileCopyrightText: 2023-2026 German Aerospace Center <amiris@dlr.de>
//
// SPDX-License-Identifier: Apache-2.0
package accounting;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import de.dlr.gitlab.fame.agent.input.ParameterData;
import de.dlr.gitlab.fame.agent.input.ParameterData.MissingDataException;

public class AnnualCostCalculatorTest {

	@ParameterizedTest
	@NullAndEmptySource
	@ValueSource(strings = {"irrelevant", "myGroup"})
	public void build_inputIsNull_returnsDefault(String groupName) {
		AnnualCostCalculator result = AnnualCostCalculator.build(null, groupName);
		assertAllValuesAreZero(result);
	}

	/** asserts that all values of the given AnnualCostCalculator are equal to Zero */
	private void assertAllValuesAreZero(AnnualCostCalculator result) {
		assertEquals(0., result.getInvestmentExpensesInEURperMW(), 1E-12);
		assertEquals(0., result.getFixedCostsInEURperYearMW(), 1E-12);
		assertEquals(0., result.getAnnuityFactor(), 1E-12);
	}

	@Test
	public void build_inputGroupIsMissing_returnsDefault() throws MissingDataException {
		ParameterData inputMock = mock(ParameterData.class);
		when(inputMock.getGroup(any(String.class))).thenThrow(MissingDataException.class);
		AnnualCostCalculator result = AnnualCostCalculator.build(null, "missingGroup");
		assertAllValuesAreZero(result);
	}

	@ParameterizedTest
	@ValueSource(doubles = {0., 15., 1E10})
	public void build_inputInvestment_present(double value) {
		ParameterData input = createInputMock(value, null, null);
		AnnualCostCalculator result = AnnualCostCalculator.build(input, "group");
		assertEquals(value, result.getInvestmentExpensesInEURperMW(), 1E-12);
		assertEquals(0., result.getFixedCostsInEURperYearMW(), 1E-12);
		assertEquals(0., result.getAnnuityFactor(), 1E-12);
	}

	/** @return mocked ParameterData input for 'build' function of AnnualCostCalculator with given values */
	private ParameterData createInputMock(Double invest, Double annuity, Double fixed) {
		ParameterData inputMock = mock(ParameterData.class);
		ParameterData groupMock = mock(ParameterData.class);
		try {
			when(inputMock.getGroup(any(String.class))).thenReturn(groupMock);
		} catch (MissingDataException e) {}
		if (invest != null) {
			when(groupMock.getDoubleOrDefault(eq("InvestmentExpensesInEURperMW"), any(Double.class))).thenReturn(invest);
		}
		if (annuity != null) {
			when(groupMock.getDoubleOrDefault(eq("AnnuityFactor"), any(Double.class))).thenReturn(annuity);
		}
		if (fixed != null) {
			when(groupMock.getDoubleOrDefault(eq("AnnualFixedCostsInEURperMW"), any(Double.class))).thenReturn(fixed);
		}
		return inputMock;
	}

	@ParameterizedTest
	@ValueSource(doubles = {0., 15., 1E10})
	public void build_inputAnnuity_present(double value) {
		ParameterData input = createInputMock(null, value, null);
		AnnualCostCalculator result = AnnualCostCalculator.build(input, "group");
		assertEquals(value, result.getAnnuityFactor(), 1E-12);
		assertEquals(0., result.getFixedCostsInEURperYearMW(), 1E-12);
		assertEquals(0., result.getInvestmentExpensesInEURperMW(), 1E-12);
	}

	@ParameterizedTest
	@ValueSource(doubles = {0., 15., 1E10})
	public void build_inputFixedCost_present(double value) {
		ParameterData input = createInputMock(null, null, value);
		AnnualCostCalculator result = AnnualCostCalculator.build(input, "group");
		assertEquals(value, result.getFixedCostsInEURperYearMW(), 1E-12);
		assertEquals(0., result.getInvestmentExpensesInEURperMW(), 1E-12);
		assertEquals(0., result.getAnnuityFactor(), 1E-12);
	}

	@ParameterizedTest
	@CsvSource({"10, 0.1, 20, 20", "1, 1, 15, 15", "100, 0.1, 10, 100"})
	public void calcInvestmentAnnuityInEUR_correct(double invest, double annuity, double capacity, double expected) {
		ParameterData input = createInputMock(invest, annuity, null);
		AnnualCostCalculator calculator = AnnualCostCalculator.build(input, "group");
		assertEquals(expected, calculator.calcInvestmentAnnuityInEUR(capacity), 1E-12);
	}

	@ParameterizedTest
	@CsvSource({"10, 1, 10", "0, 2, 0", "10, 15, 150"})
	public void calcFixedCostInEUR_correct(double fixed, double capacity, double expected) {
		ParameterData input = createInputMock(null, null, fixed);
		AnnualCostCalculator calculator = AnnualCostCalculator.build(input, "group");
		assertEquals(expected, calculator.calcFixedCostInEUR(capacity), 1E-12);
	}
}
