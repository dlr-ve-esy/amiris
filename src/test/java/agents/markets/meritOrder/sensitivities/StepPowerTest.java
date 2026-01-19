// SPDX-FileCopyrightText: 2026 German Aerospace Center <amiris@dlr.de>
//
// SPDX-License-Identifier: Apache-2.0
package agents.markets.meritOrder.sensitivities;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static testUtils.Exceptions.assertThrowsMessage;
import org.junit.jupiter.api.Test;

public class StepPowerTest {

	@Test
	public void getPower_tooNegativeSteps_throws() {
		StepPower stepPower = new StepPower(10, 100, 10);
		assertThrowsMessage(IllegalArgumentException.class, StepPower.ERR_INVALID_STEPS, () -> stepPower.getPower(-13));
	}

	@Test
	public void getPower_tooPositiveSteps_throws() {
		StepPower stepPower = new StepPower(10, 100, 10);
		assertThrowsMessage(IllegalArgumentException.class, StepPower.ERR_INVALID_STEPS, () -> stepPower.getPower(13));
	}

	@Test
	public void getPower_negativeSteps_returnNegativePower() {
		StepPower stepPower = new StepPower(10, 100, 10);
		assertEquals(-50, stepPower.getPower(-5), 1E-10);
	}

	@Test
	public void getPower_positiveSteps_returnPositivePower() {
		StepPower stepPower = new StepPower(10, 100, 10);
		assertEquals(5, stepPower.getPower(5), 1E-10);
	}
}
