// SPDX-FileCopyrightText: 2025-2026 German Aerospace Center <amiris@dlr.de>
//
// SPDX-License-Identifier: Apache-2.0
package agents.flexibility.dynamicProgramming.states;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import de.dlr.gitlab.fame.agent.input.ParameterData;
import de.dlr.gitlab.fame.agent.input.ParameterData.MissingDataException;
import de.dlr.gitlab.fame.data.TimeSeries;
import de.dlr.gitlab.fame.time.TimeStamp;

/** Tests for {@link WaterValues} */
public class WaterValuesTest {
	private WaterValues waterValues;
	private TimeStamp time = new TimeStamp(0);

	@Test
	public void constructWithNull_hasData_ReturnsFalse() throws MissingDataException {
		waterValues = new WaterValues(null);
		assertEquals(false, waterValues.hasData());
	}

	@Test
	public void constructWithEmptyList_hasData_ReturnsFalse() throws MissingDataException {
		waterValues = new WaterValues(new ArrayList<>());
		assertEquals(false, waterValues.hasData());
	}

	@Test
	public void constructWithData_hasData_ReturnsTrue() throws MissingDataException {
		waterValues = new WaterValues(mockInputs(1.0, 5.0));
		assertEquals(true, waterValues.hasData());
	}

	/** Creates a list of mockedParameterData from given values list.
	 * 
	 * @param values {0., 2., 4., ...} entry are energy contents; {1., 3., 5., ...} entry are water values
	 * @throws MissingDataException */
	private List<ParameterData> mockInputs(double... values) throws MissingDataException {
		var inputs = new ArrayList<ParameterData>();
		for (int index = 0; index < values.length / 2; index++) {
			ParameterData parameterData = mock(ParameterData.class);
			when(parameterData.getDouble(WaterValues.PARAM_STORED_ENERGY)).thenReturn(values[index * 2]);
			var series = mockConstantSeries(values[index * 2 + 1]);
			when(parameterData.getTimeSeries(WaterValues.PARAM_WATER_VALUE)).thenReturn(series);
			inputs.add(parameterData);
		}
		return inputs;
	}

	/** @return a mocked TimeSeries with a constant value */
	private TimeSeries mockConstantSeries(double d) {
		var series = mock(TimeSeries.class);
		when(series.getValueLinear(any(TimeStamp.class))).thenReturn(d);
		return series;
	}

	@Test
	public void getValueInEUR_noData_returnsZero() throws MissingDataException {
		waterValues = new WaterValues(null);
		assertEquals(0., waterValues.getValueInEUR(time, 0), 1E-14);
	}

	@Test
	public void getValueInEUR_oneSeriesAtZero_returnsZero() throws MissingDataException {
		waterValues = new WaterValues(mockInputs(0., 5.0));
		assertEquals(0., waterValues.getValueInEUR(time, 0), 1E-14);
	}

	@Test
	public void getValueInEUR_oneSeriesNotZero_interpolates() throws MissingDataException {
		waterValues = new WaterValues(mockInputs(1., 2.0));
		assertEquals(1., waterValues.getValueInEUR(time, 0.5), 1E-14);
	}

	@Test
	public void getValueInEUR_oneSeriesNotZero_extrapolates() throws MissingDataException {
		waterValues = new WaterValues(mockInputs(1., 2.0));
		assertEquals(4., waterValues.getValueInEUR(time, 2.), 1E-14);
	}

	@Test
	public void getValueInEUR_twoSeries_interpolatesOrExtrapolates() throws MissingDataException {
		waterValues = new WaterValues(mockInputs(5., 10., 15., 20.));
		assertEquals(7., waterValues.getValueInEUR(time, 2.), 1E-14);
		assertEquals(12., waterValues.getValueInEUR(time, 7.), 1E-14);
		assertEquals(23., waterValues.getValueInEUR(time, 18.), 1E-14);
	}

	@Test
	public void getValueInEUR_fourSeries_interpolatesOrExtrapolates() throws MissingDataException {
		waterValues = new WaterValues(mockInputs(5., -1., 10., 0., 15., 5., 20., 3.));
		assertEquals(-1.6, waterValues.getValueInEUR(time, 2.), 1E-14);
		assertEquals(-0.8, waterValues.getValueInEUR(time, 6.), 1E-14);
		assertEquals(2., waterValues.getValueInEUR(time, 12.), 1E-14);
		assertEquals(5., waterValues.getValueInEUR(time, 15.), 1E-14);
		assertEquals(4.2, waterValues.getValueInEUR(time, 17.), 1E-14);
		assertEquals(2.6, waterValues.getValueInEUR(time, 21.), 1E-14);
	}
}
