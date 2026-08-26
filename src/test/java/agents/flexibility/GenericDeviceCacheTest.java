// SPDX-FileCopyrightText: 2025-2026 German Aerospace Center <amiris@dlr.de>
//
// SPDX-License-Identifier: Apache-2.0
package agents.flexibility;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import agents.flexibility.GenericDevice.StateViolation;
import de.dlr.gitlab.fame.agent.input.ParameterData.MissingDataException;
import de.dlr.gitlab.fame.time.Constants.Interval;
import de.dlr.gitlab.fame.time.TimePeriod;
import de.dlr.gitlab.fame.time.TimeSpan;
import de.dlr.gitlab.fame.time.TimeStamp;

public class GenericDeviceCacheTest {
	@Mock private GenericDevice mockDevice;
	private AutoCloseable closable;

	private GenericDeviceCache deviceCache;
	private static final TimeSpan ONE_HOUR = new TimeSpan(1, Interval.HOURS);
	private static final TimeSpan TWO_HOURS = new TimeSpan(2, Interval.HOURS);
	private static final TimeSpan QUARTER_HOUR = new TimeSpan(15, Interval.MINUTES);

	@BeforeEach
	public void setUp() throws MissingDataException {
		closable = MockitoAnnotations.openMocks(this);
	}

	@AfterEach
	public void clear() throws Exception {
		closable.close();
	}

	private void setupGenericDeviceCache(double chargingPowerInMW, double dischargingPowerInMW,
			double chargingEfficiency, double dischargingEfficiency, double energyContentUpperLimitInMWH,
			double energyContentLowerLimitInMWH, double selfDischargeRatePerHour, double netInflowPowerInMW) {
		when(mockDevice.getChargingEfficiency(any())).thenReturn(chargingEfficiency);
		when(mockDevice.getDischargingEfficiency(any())).thenReturn(dischargingEfficiency);
		when(mockDevice.getEnergyContentUpperLimitInMWH(any())).thenReturn(energyContentUpperLimitInMWH);
		when(mockDevice.getEnergyContentLowerLimitInMWH(any())).thenReturn(energyContentLowerLimitInMWH);
		when(mockDevice.getNetInflowInMW(any())).thenReturn(netInflowPowerInMW);
		when(mockDevice.getSelfDischargeRate(any())).thenReturn(selfDischargeRatePerHour);
		when(mockDevice.getExternalChargingPowerInMW(any())).thenReturn(chargingPowerInMW);
		when(mockDevice.getExternalDischargingPowerInMW(any())).thenReturn(dischargingPowerInMW);
		deviceCache = new GenericDeviceCache(mockDevice);
	}

	private void cacheFor(TimeSpan timeSpan) {
		TimeStamp startTime = new TimeStamp(0);
		deviceCache.setPeriod(new TimePeriod(startTime, timeSpan));
		deviceCache.prepareFor(startTime);
	}

	@Test
	public void getMaxChargingTargetNoEnergyLimitMWH_noSelfDischargeNoInflowInitialZero_equalsNetCharging() {
		setupGenericDeviceCache(100, 0, 1, 1, 500, 0, 0, 0);
		cacheFor(ONE_HOUR);
		assertEquals(100, deviceCache.getMaxChargingTargetNoEnergyLimitMWH(0), 1E-12);

		cacheFor(TWO_HOURS);
		assertEquals(200, deviceCache.getMaxChargingTargetNoEnergyLimitMWH(0), 1E-12);

		setupGenericDeviceCache(100, 0, 1, 1, 500, 0, 0, 0);
		cacheFor(QUARTER_HOUR);
		assertEquals(25, deviceCache.getMaxChargingTargetNoEnergyLimitMWH(0), 1E-12);
	}

	@Test
	public void getMaxChargingTargetNoEnergyLimitMWH_noSelfDischargeNoInflowInitialNonzero_addsInitial() {
		setupGenericDeviceCache(100, 0, 1, 1, 500, 0, 0, 0);
		cacheFor(ONE_HOUR);
		assertEquals(110, deviceCache.getMaxChargingTargetNoEnergyLimitMWH(10), 1E-12);

		cacheFor(TWO_HOURS);
		assertEquals(190, deviceCache.getMaxChargingTargetNoEnergyLimitMWH(-10), 1E-12);

		cacheFor(QUARTER_HOUR);
		assertEquals(35, deviceCache.getMaxChargingTargetNoEnergyLimitMWH(10), 1E-12);
	}

	@Test
	public void getMaxChargingTargetNoEnergyLimitMWH_noSelfDischargeWithInflowInitialZero_addsInflow() {
		setupGenericDeviceCache(100, 0, 1, 1, 500, 0, 0, 20);
		cacheFor(ONE_HOUR);
		assertEquals(120, deviceCache.getMaxChargingTargetNoEnergyLimitMWH(0), 1E-12);

		cacheFor(TWO_HOURS);
		assertEquals(240, deviceCache.getMaxChargingTargetNoEnergyLimitMWH(0), 1E-12);

		cacheFor(QUARTER_HOUR);
		assertEquals(30, deviceCache.getMaxChargingTargetNoEnergyLimitMWH(0), 1E-12);
	}

	@Test
	public void getMaxChargingTargetNoEnergyLimitMWH_withSelfDischargeNoInflow_subtractsSelfDischarge() {
		setupGenericDeviceCache(200, 0, 1, 1, 500, 0, 0.1, 0);
		cacheFor(ONE_HOUR);
		assertEquals(200, deviceCache.getMaxChargingTargetNoEnergyLimitMWH(0), 1E-2);
		assertEquals(290, deviceCache.getMaxChargingTargetNoEnergyLimitMWH(100), 1E-2);

		cacheFor(TWO_HOURS);
		assertEquals(440.5, deviceCache.getMaxChargingTargetNoEnergyLimitMWH(50), 1E-2);

		cacheFor(QUARTER_HOUR);
		assertEquals(244.801, deviceCache.getMaxChargingTargetNoEnergyLimitMWH(200), 1E-2);
	}

	@Test
	public void getMaxChargingTargetNoEnergyLimitMWH_underflow_returnsValueBelowLowerLimit() {
		setupGenericDeviceCache(0, 0, 1, 1, 10, 0, 0, -10);
		cacheFor(ONE_HOUR);
		assertEquals(-10, deviceCache.getMaxChargingTargetNoEnergyLimitMWH(0), 1E-2);
	}

	@Test
	public void getMaxChargingTargetNoEnergyLimitMWH_overflow_returnsValueAboveUpperLimit() {
		setupGenericDeviceCache(0, 0, 1, 1, 0, 0, 0, 10);
		cacheFor(ONE_HOUR);
		assertEquals(10, deviceCache.getMaxChargingTargetNoEnergyLimitMWH(0), 1E-2);
	}

	@Test
	public void simulateTransition_useMaxTargetEnergy_returnsMaxPower() {
		setupGenericDeviceCache(200, 0, 1, 0, 500, 0, 0.1, 0);
		cacheFor(TWO_HOURS);
		assertEquals(400, deviceCache.simulateTransition(0, 400), 1E-12);
	}

	@Test
	public void getMaxChargingTargetNoEnergyLimitMWH_withSelfDischargeInflowAndInitial_correctCalc() {
		setupGenericDeviceCache(200, 0, 1, 0, 500, 0, 0.1, 50);
		cacheFor(ONE_HOUR);
		assertEquals(232, deviceCache.getMaxChargingTargetNoEnergyLimitMWH(-20), 1E-2);

		setupGenericDeviceCache(200, 0, 1, 1, 500, 0, 0.1, -50);
		cacheFor(TWO_HOURS);
		assertEquals(372.9, deviceCache.getMaxChargingTargetNoEnergyLimitMWH(90), 1E-2);
		cacheFor(QUARTER_HOUR);
		assertEquals(71.590, deviceCache.getMaxChargingTargetNoEnergyLimitMWH(35), 1E-2);
	}

	@Test
	public void getMaxDischargingTargetNoEnergyLimitInMWH_noSelfDischargeNoInflowInitialZero_equalsNetCharging() {
		setupGenericDeviceCache(0, 100, 1, 1, 0, -500, 0, 0);
		cacheFor(ONE_HOUR);
		assertEquals(-100, deviceCache.getMaxDischargingTargetNoEnergyLimitInMWH(0), 1E-12);
		cacheFor(TWO_HOURS);
		assertEquals(-200, deviceCache.getMaxDischargingTargetNoEnergyLimitInMWH(0), 1E-12);
		cacheFor(QUARTER_HOUR);
		assertEquals(-25, deviceCache.getMaxDischargingTargetNoEnergyLimitInMWH(0), 1E-12);
	}

	@Test
	public void getMaxDischargingTargetNoEnergyLimitInMWH_noSelfDischargeNoInflowInitialNonzero_addsInitial() {
		setupGenericDeviceCache(0, 100, 1, 1, 0, -500, 0, 0);
		cacheFor(ONE_HOUR);
		assertEquals(100, deviceCache.getMaxDischargingTargetNoEnergyLimitInMWH(200), 1E-12);
		cacheFor(TWO_HOURS);
		assertEquals(0, deviceCache.getMaxDischargingTargetNoEnergyLimitInMWH(200), 1E-12);
		cacheFor(QUARTER_HOUR);
		assertEquals(-15, deviceCache.getMaxDischargingTargetNoEnergyLimitInMWH(10), 1E-12);
	}

	@Test
	public void getMaxDischargingTargetNoEnergyLimitInMWH_noSelfDischargeWithInflowInitialZero_addsInflow() {
		setupGenericDeviceCache(0, 100, 1, 1, 0, -500, 0, -20);
		cacheFor(ONE_HOUR);
		assertEquals(-120, deviceCache.getMaxDischargingTargetNoEnergyLimitInMWH(0), 1E-12);
		cacheFor(TWO_HOURS);
		assertEquals(-240, deviceCache.getMaxDischargingTargetNoEnergyLimitInMWH(0), 1E-12);
		cacheFor(QUARTER_HOUR);
		assertEquals(-30, deviceCache.getMaxDischargingTargetNoEnergyLimitInMWH(0), 1E-12);
	}

	@Test
	public void getMaxDischargingTargetNoEnergyLimitInMWH_withSelfDischargeNoInflowInitialZero_subtractsSelfDischarge() {
		setupGenericDeviceCache(0, 200, 1, 1, 0, -500, 0.1, 0);
		cacheFor(ONE_HOUR);
		assertEquals(-209, deviceCache.getMaxDischargingTargetNoEnergyLimitInMWH(-10), 1E-2);
		cacheFor(TWO_HOURS);
		assertEquals(-367.600, deviceCache.getMaxDischargingTargetNoEnergyLimitInMWH(40), 1E-2);
		cacheFor(QUARTER_HOUR);
		assertEquals(47.400, deviceCache.getMaxDischargingTargetNoEnergyLimitInMWH(100), 1E-2);
	}

	@Test
	public void simulateTransition_useMinTargetEnergy_returnsMinPower() {
		setupGenericDeviceCache(0, 200, 0, 1, 0, -500, 0.1, 0);
		cacheFor(TWO_HOURS);
		assertEquals(-400, deviceCache.simulateTransition(0, -400), 1E-12);

		cacheFor(QUARTER_HOUR);
		assertEquals(-50, deviceCache.simulateTransition(0, -50), 1E-12);
	}

	@Test
	public void getMaxDischargingTargetNoEnergyLimitInMWH_withSelfDischargeInflowAndInitial_correctCalc() {
		setupGenericDeviceCache(0, 200, 1, 1, 0, -500, 0.1, 50);
		cacheFor(ONE_HOUR);
		assertEquals(-132, deviceCache.getMaxDischargingTargetNoEnergyLimitInMWH(20), 1E-2);

		setupGenericDeviceCache(0, 200, 1, 1, 0, -500, 0.1, -50);
		cacheFor(QUARTER_HOUR);
		assertEquals(-13.800, deviceCache.getMaxDischargingTargetNoEnergyLimitInMWH(50), 1E-2);
	}

	@Test
	public void getMaxDischargingTargetNoEnergyLimitInMWH_overflow_returnsAboveUpperLimit() {
		setupGenericDeviceCache(0, 0, 1, 1, 2, 0, 0, 10);
		cacheFor(ONE_HOUR);
		assertEquals(10, deviceCache.getMaxDischargingTargetNoEnergyLimitInMWH(0), 1E-2);
	}

	@Test
	public void getMinTargetEnergyContentInMWH_underflow_returnsBelowLowerLimit() {
		setupGenericDeviceCache(0, 0, 1, 1, 0, 0, 0, -10);
		cacheFor(ONE_HOUR);
		assertEquals(-10, deviceCache.getMaxDischargingTargetNoEnergyLimitInMWH(0), 1E-2);
	}

	@Test
	public void simulateTransition_noSelfDischargeFullEfficiency_correctCalc() {
		setupGenericDeviceCache(500, 500, 1, 1, 0, 0, 0, 10);
		cacheFor(ONE_HOUR);
		assertEquals(40, deviceCache.simulateTransition(50, 100), 1E-12);
		cacheFor(QUARTER_HOUR);
		assertEquals(107.5, deviceCache.simulateTransition(-10, 100), 1E-12);
		cacheFor(TWO_HOURS);
		assertEquals(-10, deviceCache.simulateTransition(90, 100), 1E-12);
	}

	@Test
	public void simulateTransition_largeInflow_resultWithinGridPowerLimits() {
		setupGenericDeviceCache(100, 100, 1, 1, 0, 0, 0, 200);
		cacheFor(ONE_HOUR);
		assertEquals(-100, deviceCache.simulateTransition(50, 50), 1E-12);
	}

	@Test
	public void simulateTransition_largeOutflow_resultWithinGridPowerLimits() {
		setupGenericDeviceCache(100, 100, 1, 1, 0, 0, 0, -200);
		cacheFor(ONE_HOUR);
		assertEquals(100, deviceCache.simulateTransition(50, 50), 1E-12);
	}

	@Test
	public void simulateTransition_noSelfDischargeImperfectEfficiency_correctCalc() {
		setupGenericDeviceCache(1000, 1000, 0.5, 0.8, 0, 0, 0, 10);
		cacheFor(ONE_HOUR);
		assertEquals(80, deviceCache.simulateTransition(50, 100), 1E-12);
		cacheFor(QUARTER_HOUR);
		assertEquals(215, deviceCache.simulateTransition(-10, 100), 1E-12);
		cacheFor(TWO_HOURS);
		assertEquals(-8, deviceCache.simulateTransition(90, 100), 1E-12);
	}

	@Test
	public void simulateTransition_withSelfDischargeImperfectEfficiency_correctCalc() {
		setupGenericDeviceCache(1000, 1000, 0.5, 0.8, 0, 0, 0.1, 10);
		cacheFor(ONE_HOUR);
		assertEquals(90, deviceCache.simulateTransition(50, 100), 1E-2);
		cacheFor(QUARTER_HOUR);
		assertEquals(214.480, deviceCache.simulateTransition(-10, 100), 1E-2);
		cacheFor(TWO_HOURS);
		assertEquals(14.2, deviceCache.simulateTransition(90, 100), 1E-2);
		cacheFor(ONE_HOUR);
		assertEquals(-40, deviceCache.simulateTransition(100, 50), 1E-2);
	}

	@Test
	public void getMaxNetChargingEnergyInMWH_correctValue() {
		setupGenericDeviceCache(100, 0, 0.9, 0, 0, 0, 0, 22);
		cacheFor(ONE_HOUR);
		assertEquals(112, deviceCache.getMaxNetChargingEnergyInMWH(), 1E-12);

		cacheFor(TWO_HOURS);
		assertEquals(224, deviceCache.getMaxNetChargingEnergyInMWH(), 1E-12);

		cacheFor(QUARTER_HOUR);
		assertEquals(28, deviceCache.getMaxNetChargingEnergyInMWH(), 1E-12);
	}

	@Test
	public void getMaxNetDischargingEnergy_correctValue() {
		setupGenericDeviceCache(0, 100, 0, 0.5, 0, 0, 0, 24);
		cacheFor(ONE_HOUR);
		assertEquals(-176, deviceCache.getMaxNetDischargingEnergyInMWH(), 1E-12);

		cacheFor(TWO_HOURS);
		assertEquals(-352, deviceCache.getMaxNetDischargingEnergyInMWH(), 1E-12);

		cacheFor(QUARTER_HOUR);
		assertEquals(-44, deviceCache.getMaxNetDischargingEnergyInMWH(), 1E-12);
	}

	@ParameterizedTest
	@CsvSource(value = {"0", "1E-12", "-1E-12"})
	public void canHoldEnergyLevelWithoutAction_negligibleInflow_returnsTrue(double inflow) {
		setupGenericDeviceCache(0, 100, 0, 0.5, 0, 0, 0, inflow);
		cacheFor(ONE_HOUR);
		assertTrue(deviceCache.canHoldEnergyLevelWithoutAction());
	}

	@Test
	public void canHoldEnergyLevelWithoutAction_inflowCut_returnsTrue() {
		setupGenericDeviceCache(0, 100, 0, 0.5, 0, 0, 0, 42);
		when(mockDevice.onOverflow()).thenReturn(StateViolation.CUT);
		cacheFor(ONE_HOUR);
		assertTrue(deviceCache.canHoldEnergyLevelWithoutAction());
	}

	@Test
	public void canHoldEnergyLevelWithoutAction_inflowNotCut_returnsFalse() {
		setupGenericDeviceCache(0, 100, 0, 0.5, 0, 0, 0, 42);
		when(mockDevice.onOverflow()).thenReturn(StateViolation.ERROR);
		cacheFor(ONE_HOUR);
		assertFalse(deviceCache.canHoldEnergyLevelWithoutAction());
	}

	@Test
	public void canHoldEnergyLevelWithoutAction_outflowCut_returnsTrue() {
		setupGenericDeviceCache(0, 100, 0, 0.5, 0, 0, 0, -42);
		when(mockDevice.onUnderflow()).thenReturn(StateViolation.CUT);
		cacheFor(ONE_HOUR);
		assertTrue(deviceCache.canHoldEnergyLevelWithoutAction());
	}

	@Test
	public void canHoldEnergyLevelWithoutAction_outflowNotCut_returnsFalse() {
		setupGenericDeviceCache(0, 100, 0, 0.5, 0, 0, 0, -42);
		when(mockDevice.onUnderflow()).thenReturn(StateViolation.ERROR);
		cacheFor(ONE_HOUR);
		assertFalse(deviceCache.canHoldEnergyLevelWithoutAction());
	}
}
