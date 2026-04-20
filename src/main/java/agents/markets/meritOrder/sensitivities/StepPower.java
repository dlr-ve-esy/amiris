// SPDX-FileCopyrightText: 2021-2026 German Aerospace Center <amiris@dlr.de>
//
// SPDX-License-Identifier: Apache-2.0
package agents.markets.meritOrder.sensitivities;

/** Power in MW per charging / discharging step connected to a {@link MeritOrderSensitivity}
 * 
 * @author Christoph Schimeczek */
public class StepPower {
	static final String ERR_INVALID_STEPS = "StepDelta must not exceed number of transitions steps";

	private final double chargingStepPowerInMW;
	private final double dischargingStepPowerInMW;
	private final int numberOfTransitionSteps;

	/** Creates a {@link StepPower}
	 * 
	 * @param externalChargingPowerInMW maximum effective charging power (&ge; 0)
	 * @param externalDischargingPowerInMW maximum effective discharging power (&ge; 0)
	 * @param numberOfTransitionSteps number of modelled transition steps for each of the two energy flow directions */
	public StepPower(double externalChargingPowerInMW, double externalDischargingPowerInMW, int numberOfTransitionSteps) {
		this.chargingStepPowerInMW = externalChargingPowerInMW / numberOfTransitionSteps;
		this.dischargingStepPowerInMW = externalDischargingPowerInMW / numberOfTransitionSteps;
		this.numberOfTransitionSteps = numberOfTransitionSteps;
	}

	/** Returns power matching given step delta
	 * 
	 * @param stepDelta for charging / discharging
	 *          <ul>
	 *          <li>&gt; 0: charging &rarr; power &gt; 0</li>
	 *          <li>&lt; 0: discharging &rarr; power &lt; 0</li>
	 *          </ul>
	 * @return power in MW corresponding to number of charging / discharging steps
	 * @throws IllegalArgumentException if absolute value of stepDelta is larger than numberOfTransitionSteps */
	public double getPower(int stepDelta) {
		if (Math.abs(stepDelta) > numberOfTransitionSteps) {
			throw new IllegalArgumentException(ERR_INVALID_STEPS);
		}
		double powerPerStep = (stepDelta >= 0 ? chargingStepPowerInMW : dischargingStepPowerInMW);
		return stepDelta * powerPerStep;
	}
}