// SPDX-FileCopyrightText: 2025-2026 German Aerospace Center <amiris@dlr.de>
//
// SPDX-License-Identifier: Apache-2.0
package agents.flexibility;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import de.dlr.gitlab.fame.agent.input.ParameterData;
import de.dlr.gitlab.fame.agent.input.ParameterData.MissingDataException;
import de.dlr.gitlab.fame.data.TimeSeries;
import de.dlr.gitlab.fame.protobuf.Input.InputData.TimeSeriesDao;
import de.dlr.gitlab.fame.time.Constants.Interval;
import de.dlr.gitlab.fame.time.TimeSpan;
import de.dlr.gitlab.fame.time.TimeStamp;
import testUtils.LogChecker;

public class GenericDeviceTest {

	@Mock private ParameterData parameterDataMock;
	private static LogChecker logChecker;
	private AutoCloseable closable;

	private GenericDevice device;
	private TimeStamp defaultTime = new TimeStamp(0L);
	private TimeSpan oneHour = new TimeSpan(1, Interval.HOURS);
	private TimeSpan twoHours = new TimeSpan(2, Interval.HOURS);
	private TimeSpan quarterHour = new TimeSpan(15, Interval.MINUTES);

	@BeforeAll
	public static void setupLogCaptor() {
		logChecker = new LogChecker(GenericDevice.class);
	}

	@BeforeEach
	public void setUp() throws MissingDataException {
		closable = MockitoAnnotations.openMocks(this);
	}

	@AfterEach
	public void clear() throws Exception {
		closable.close();
		logChecker.clear();
	}

	@AfterAll
	public static void tearDown() {
		logChecker.close();
	}

	private GenericDevice createGenericDevice(double chargingPowerInMW, double dischargingPowerInMW,
			double chargingEfficiency, double dischargingEfficiency, double energyContentUpperLimitInMWH,
			double energyContentLowerLimitInMWH, double selfDischargeRatePerHour, double netInflowPowerInMW,
			double currentEnergyContentInMWH, double variableCostInEURperMWH, double maximumShiftTimeInHours, int prologing,
			double penaltyCostInEURperMWH) {
		try {
			when(parameterDataMock.getTimeSeries(GenericDevice.PARAM_CHARGING_POWER))
					.thenReturn(createSeries(chargingPowerInMW));
			when(parameterDataMock.getTimeSeries(eq(GenericDevice.PARAM_DISCHARGING_POWER)))
					.thenReturn(createSeries(dischargingPowerInMW));
			when(parameterDataMock.getTimeSeries(GenericDevice.PARAM_CHARGING_EFFICIENCY))
					.thenReturn(createSeries(chargingEfficiency));
			when(parameterDataMock.getTimeSeries(GenericDevice.PARAM_DISCHARGING_EFFICIENCY))
					.thenReturn(createSeries(dischargingEfficiency));
			when(parameterDataMock.getTimeSeries(GenericDevice.PARAM_UPPER_LIMIT))
					.thenReturn(createSeries(energyContentUpperLimitInMWH));
			when(parameterDataMock.getTimeSeries(GenericDevice.PARAM_LOWER_LIMIT))
					.thenReturn(createSeries(energyContentLowerLimitInMWH));
			when(parameterDataMock.getTimeSeries(GenericDevice.PARAM_SELF_DISCHARGE))
					.thenReturn(createSeries(selfDischargeRatePerHour));
			when(parameterDataMock.getTimeSeries(GenericDevice.PARAM_INFLOW)).thenReturn(createSeries(netInflowPowerInMW));
			when(parameterDataMock.getDouble(GenericDevice.PARAM_INITIAL_ENERGY)).thenReturn(currentEnergyContentInMWH);
			when(parameterDataMock.getTimeSeries(GenericDevice.PARAM_VARIABLE_COST))
					.thenReturn(createSeries(variableCostInEURperMWH));
			when(parameterDataMock.getDouble(GenericDevice.PARAM_SHIFT_TIME)).thenReturn(maximumShiftTimeInHours);
			when(parameterDataMock.getInteger(GenericDevice.PARAM_ENABLE_PROLONGING)).thenReturn(prologing);
			when(parameterDataMock.getTimeSeries(GenericDevice.PARAM_PENALTY_COST))
					.thenReturn(createSeries(penaltyCostInEURperMWH));
			return new GenericDevice(parameterDataMock);
		} catch (MissingDataException e) {
			throw new RuntimeException();
		}
	}

	private TimeSeries createSeries(double... values) {
		TimeSeriesDao.Builder builder = TimeSeriesDao.newBuilder();
		for (int i = 0; i < values.length; i++) {
			builder.addTimeSteps(i);
			builder.addValues(values[i]);
		}
		return new TimeSeries(builder.setSeriesId(1).build());
	}

	@Test
	public void getEnergyContentUpperLimitInMWH_returnsConfiguredValue() {
		double targetValue = 42.;
		device = createGenericDevice(0, 0, 0, 0, targetValue, 0, 0, 0, 0, 0, 0, 0, 0);
		assertEquals(targetValue, device.getEnergyContentUpperLimitInMWH(defaultTime), 1E-12);
	}

	@Test
	public void getEnergyContentLowerLimitInMWH_returnsConfiguredValue() {
		double targetValue = 42.;
		device = createGenericDevice(0, 0, 0, 0, 0, targetValue, 0, 0, 0, 0, 0, 0, 0);
		assertEquals(targetValue, device.getEnergyContentLowerLimitInMWH(defaultTime), 1E-12);
	}

	@ParameterizedTest
	@CsvSource(value = {"0", "1"})
	public void hasProlonging_returnsConfiguresValue(int prolonging) {
		device = createGenericDevice(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, prolonging, 0);
		boolean expected = prolonging != 0;
		assertEquals(expected, device.hasProlonging());
	}

	@Test
	public void transition_noSelfDischargeFullEfficiencyNoInflow_correctNewEnergyContent() {
		device = createGenericDevice(100, 100, 1, 1, 500, -500, 0., 0, 0, 0, 0, 0, 0);
		device.transition(defaultTime, 50, oneHour);
		assertEquals(50, device.getCurrentInternalEnergyInMWH(), 1E-12);
	}

	@Test
	public void transition_noSelfDischargeFullEfficiencyNoInflowQuarterHour_correctNewEnergyContent() {
		device = createGenericDevice(100, 100, 1, 1, 500, -500, 0., 0, 0, 0, 0, 0, 0);
		device.transition(defaultTime, 100, quarterHour);
		assertEquals(25, device.getCurrentInternalEnergyInMWH(), 1E-12);
	}

	@Test
	public void transition_noSelfDischargeFullEfficiencyWithInflow_correctNewEnergyContent() {
		device = createGenericDevice(100, 100, 1, 1, 500, -500, 0., 10, 0, 0, 0, 0, 0);
		device.transition(defaultTime, 200, twoHours);
		assertEquals(220, device.getCurrentInternalEnergyInMWH(), 1E-12);
	}

	@Test
	public void transition_noSelfDischargeImperfectEfficiencyNoInflow_correctNewEnergyContent() {
		device = createGenericDevice(100, 100, 0.5, 0.8, 500, -500, 0., 0, 0, 0, 0, 0, 0);
		device.transition(defaultTime, 100, oneHour);
		assertEquals(50, device.getCurrentInternalEnergyInMWH(), 1E-12);

		device = createGenericDevice(100, 100, 0.5, 0.8, 500, -500, 0., 0, 100, 0, 0, 0, 0);
		device.transition(defaultTime, -80, oneHour);
		assertEquals(0, device.getCurrentInternalEnergyInMWH(), 1E-12);
	}

	@Test
	public void transition_withSelfDischargeFullEfficiencyNoInflow_correctNewEnergyContent() {
		device = createGenericDevice(100, 100, 1, 1, 500, -500, 0.1, 0, 100, 0, 0, 0, 0);
		device.transition(defaultTime, 0, twoHours);
		assertEquals(81, device.getCurrentInternalEnergyInMWH(), 1E-12);

		device = createGenericDevice(100, 100, 1, 1, 500, -500, 0.1, 0, 100, 0, 0, 0, 0);
		device.transition(defaultTime, 100, twoHours);
		assertEquals(181, device.getCurrentInternalEnergyInMWH(), 1E-12);
	}

	@Test
	public void transition_withSelfDischargeImperfectEfficiencyWithInflow_correctNewEnergyContent() {
		device = createGenericDevice(100, 100, 0.5, 0.8, 500, -500, 0.1, 10, 100, 0, 0, 0, 0);
		device.transition(defaultTime, 0, twoHours);
		assertEquals(101, device.getCurrentInternalEnergyInMWH(), 1E-12);

		device = createGenericDevice(100, 100, 0.5, 0.8, 500, -500, 0.1, 10, 100, 0, 0, 0, 0);
		device.transition(defaultTime, -40, twoHours);
		assertEquals(51, device.getCurrentInternalEnergyInMWH(), 1E-12);

		device = createGenericDevice(100, 100, 0.5, 0.8, 500, -500, 0.1, 10, 100, 0, 0, 0, 0);
		device.transition(defaultTime, 20, twoHours);
		assertEquals(111, device.getCurrentInternalEnergyInMWH(), 1E-12);
	}

	@Test
	public void transition_chargingPowerExceeded_returnsFixedEnergyDeltas() {
		device = createGenericDevice(100, 100, 0.5, 0.8, 500, -500, 0, 0, 50, 0, 0, 0, 0);
		double result = device.transition(defaultTime, 300, oneHour);
		assertEquals(100., result, 1E-12);
		assertEquals(100., device.getCurrentInternalEnergyInMWH(), 1E-12);
		logChecker.assertLogsContain(GenericDevice.ERR_EXCEED_CHARGING_POWER);
	}

	@Test
	public void transition_dischargingPowerExceeded_returnsFixedEnergyDeltas() {
		device = createGenericDevice(100, 100, 0.5, 0.8, 500, -500, 0, 0, 250, 0, 0, 0, 0);
		double result = device.transition(defaultTime, -120, oneHour);
		assertEquals(-100, result, 1E-12);
		assertEquals(125., device.getCurrentInternalEnergyInMWH(), 1E-12);
		logChecker.assertLogsContain(GenericDevice.ERR_EXCEED_DISCHARGING_POWER);
	}

	@Test
	public void transition_upperEnergyLimitExceeded_returnsFixedEnergyDeltas() {
		device = createGenericDevice(100, 100, 0.5, 0.8, 500, -500, 0, 0, 475, 0, 0, 0, 0);
		double result = device.transition(defaultTime, 150, oneHour);
		assertEquals(50, result, 1E-12);
		assertEquals(500., device.getCurrentInternalEnergyInMWH(), 1E-12);
		logChecker.assertLogsContain(GenericDevice.ERR_EXCEED_UPPER_ENERGY_LIMIT);
	}

	@Test
	public void transition_lowerEnergyLimitExceeded_returnsFixedEnergyDeltas() {
		device = createGenericDevice(100, 100, 0.5, 0.8, 500, -500, 0, 0, -450, 0, 0, 0, 0);
		double result = device.transition(defaultTime, -75, oneHour);
		assertEquals(-40, result, 1E-12);
		assertEquals(-500., device.getCurrentInternalEnergyInMWH(), 1E-12);
		logChecker.assertLogsContain(GenericDevice.ERR_EXCEED_LOWER_ENERGY_LIMIT);
	}

	@Test
	public void transition_negativeSelfDischarge_logsError() {
		device = createGenericDevice(100, 100, 0.5, 0.8, 500, -500, 0.1, 0, -450, 0, 0, 0, 0);
		device.transition(defaultTime, 100, oneHour);
		logChecker.assertLogsContain(GenericDevice.ERR_NEGATIVE_SELF_DISCHARGE);
	}

	@Test
	public void transition_updatesCurrentShiftTime() {
		device = createGenericDevice(100, 100, 0.5, 0.8, 500, -500, 0., 0, 0, 0, 10, 0, 0);
		device.transition(defaultTime, 100, oneHour);
		assertEquals(oneHour.getSteps(), device.getCurrentShiftTimeInSteps());
	}

	@Test
	public void transition_toZero_resetsCurrentShiftTime() {
		device = createGenericDevice(100, 100, 1, 1, 500, -500, 0., 0, 0, 0, 10, 0, 0);
		device.transition(defaultTime, 100, oneHour);
		device.transition(defaultTime, -100, oneHour);
		assertEquals(0, device.getCurrentShiftTimeInSteps());
	}

	@Test
	public void transition_toZero_noProlongingCost() {
		device = createGenericDevice(100, 100, 1, 1, 500, -500, 0., 0, 0, 0, 10, 0, 0);
		device.transition(defaultTime, 100, oneHour);
		device.transition(defaultTime, -100, oneHour);
		assertEquals(0, device.getLastProlongingCostInEUR(), 1E-12);
	}

	@Test
	public void transition_counterShift_reinitialisesCurrentShiftTime() {
		device = createGenericDevice(100, 100, 1, 1, 500, -500, 0., 0, 0, 0, 10, 0, 0);
		device.transition(defaultTime, 25, quarterHour);
		device.transition(defaultTime, -100, oneHour);
		assertEquals(oneHour.getSteps(), device.getCurrentShiftTimeInSteps());
	}

	@Test
	public void transition_counterShift__noProlongingCost() {
		device = createGenericDevice(100, 100, 1, 1, 500, -500, 0., 0, 0, 0, 10, 0, 0);
		device.transition(defaultTime, 25, quarterHour);
		device.transition(defaultTime, -100, oneHour);
		assertEquals(0, device.getLastProlongingCostInEUR(), 1E-12);
	}

	@ParameterizedTest
	@ValueSource(doubles = {25, -25})
	public void transition_exceedsMaximumShiftTimeNoProlonging_logsWarningPenaltyCost(double chargedEnergy) {
		device = createGenericDevice(100, 100, 1, 1, 500, -500, 0., 0, 0, 10, 0.1, 0, 1.);
		device.transition(defaultTime, chargedEnergy, quarterHour);
		logChecker.assertLogsContain(GenericDevice.WARN_SHIFT_TIME_EXCEEDED);
		assertEquals(25, device.getLastProlongingCostInEUR(), 1E-12);
	}

	@Test
	public void transition_exceedsMaximumShiftTimeNoValidProlonging_reinitialisesShiftTimeAndApplyPenalty() {
		device = createGenericDevice(100, 100, 1, 1, 500, -500, 0., 0, 0, 10, 1, 1, 3);
		device.transition(defaultTime, 25, quarterHour);
		device.transition(defaultTime, 0, quarterHour);
		device.transition(defaultTime, 0, quarterHour);
		device.transition(defaultTime, 0, quarterHour);
		device.transition(defaultTime, 0, quarterHour);
		assertEquals(quarterHour.getSteps(), device.getCurrentShiftTimeInSteps());
		assertEquals(3 * 25, device.getLastProlongingCostInEUR(), 1E-12);
	}

	@Test
	public void transition_exceedsMaximumShiftTimeWithValidProlonging_reinitialisesShiftTime() {
		device = createGenericDevice(100, 100, 1, 1, 500, -500, 0., 0, 0, 10, 1, 1, 3);
		device.transition(defaultTime, 12, quarterHour);
		device.transition(defaultTime, 0, quarterHour);
		device.transition(defaultTime, 0, quarterHour);
		device.transition(defaultTime, 0, quarterHour);
		device.transition(defaultTime, 0, quarterHour);
		assertEquals(quarterHour.getSteps(), device.getCurrentShiftTimeInSteps());
		assertEquals(10 * 2 * 12, device.getLastProlongingCostInEUR(), 1E-12);
	}
}
