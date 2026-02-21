// SPDX-FileCopyrightText: 2026 German Aerospace Center <amiris@dlr.de>
//
// SPDX-License-Identifier: Apache-2.0
package agents.flexibility.dynamicProgramming.states;

import agents.flexibility.GenericDevice;
import agents.flexibility.GenericDeviceCache;
import agents.flexibility.dynamicProgramming.assessment.AssessmentFunction;
import de.dlr.gitlab.fame.time.TimeStamp;

/** Evaluates transition - can cache results for similar transitions
 * 
 * @author Christoph Schimeczek */
public class TransitionEvaluator {
	private StateDiscretiser stateDiscretiser;
	private GenericDeviceCache deviceCache;
	private AssessmentFunction assessmentFunction;

	private double[] transitionValuesCharging;
	private double[] transitionValuesDischarging;
	private boolean cachedValuesAvailable;

	/** Instantiates a {@link TransitionEvaluator}
	 * 
	 * @param stateDiscretiser energy content and shift-times to state indices
	 * @param deviceCache caches values for a connected {@link GenericDevice}
	 * @param assessmentFunction assesses values of energy transitions */
	public TransitionEvaluator(StateDiscretiser stateDiscretiser, GenericDeviceCache deviceCache,
			AssessmentFunction assessmentFunction) {
		this.stateDiscretiser = stateDiscretiser;
		this.deviceCache = deviceCache;
		this.assessmentFunction = assessmentFunction;
	}

	/** Sets up for evaluation at the provided time - also prepares the connected {@link AssessmentFunction} and
	 * {@link GenericDeviceCache}.
	 * 
	 * @param time to prepare evaluations for
	 * @param hasSelfDischarge if false, transition values depend only on the energy state delta and can be cached */
	public void prepareFor(TimeStamp time, boolean hasSelfDischarge) {
		assessmentFunction.prepareFor(time);
		deviceCache.prepareFor(time);
		if (hasSelfDischarge) {
			cachedValuesAvailable = false;
			transitionValuesCharging = null;
			transitionValuesDischarging = null;
		} else {
			cachedValuesAvailable = true;
			cacheTransitionValuesNoSelfDischarge();
		}
	}

	/** Caches the transition values for (dis-)charging depending on state deltas */
	private void cacheTransitionValuesNoSelfDischarge() {
		int maxChargingSteps = stateDiscretiser.discretiseEnergyDelta(deviceCache.getMaxNetChargingEnergyInMWH());
		transitionValuesCharging = new double[maxChargingSteps + 1];
		for (int chargingSteps = 0; chargingSteps <= maxChargingSteps; chargingSteps++) {
			transitionValuesCharging[chargingSteps] = calcValueFor(0, chargingSteps);
		}
		int maxDischargingSteps = stateDiscretiser.discretiseEnergyDelta(-deviceCache.getMaxNetDischargingEnergyInMWH());
		transitionValuesDischarging = new double[maxDischargingSteps + 1];
		for (int dischargingSteps = 0; dischargingSteps <= maxDischargingSteps; dischargingSteps++) {
			transitionValuesDischarging[dischargingSteps] = calcValueFor(0, -dischargingSteps);
		}
	}

	/** Returns the value of the transition from the given initial to final state at the time prepared for. Uses cached values if
	 * available.
	 * 
	 * @param initialStateIndex index of state at the begin of a transition
	 * @param finalStateIndex index of state at the end of a transition
	 * @return value of the transition between two states */
	public double getTransitionValueFor(int initialStateIndex, int finalStateIndex) {
		return cachedValuesAvailable ? getCachedValueFor(initialStateIndex, finalStateIndex)
				: calcValueFor(initialStateIndex, finalStateIndex);
	}

	/** @return value for transition from initial to final state */
	private double calcValueFor(int initialStateIndex, int finalStateIndex) {
		final double externalEnergyDeltaInMWH = deviceCache.simulateTransition(
				stateDiscretiser.energyIndexToEnergyInMWH(initialStateIndex),
				stateDiscretiser.energyIndexToEnergyInMWH(finalStateIndex));
		return assessmentFunction.assessTransition(externalEnergyDeltaInMWH);
	}

	/** @return cached value of transition, if {@link #cachedValuesAvailable} */
	private double getCachedValueFor(int initialStateIndex, int finalStateIndex) {
		int stateDelta = finalStateIndex - initialStateIndex;
		return stateDelta >= 0 ? transitionValuesCharging[stateDelta] : transitionValuesDischarging[-stateDelta];
	}
}
