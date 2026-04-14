// SPDX-FileCopyrightText: 2025-2026 German Aerospace Center <amiris@dlr.de>
//
// SPDX-License-Identifier: Apache-2.0
package agents.flexibility.dynamicProgramming.states;

import java.util.ArrayList;
import agents.flexibility.GenericDevice;
import agents.flexibility.GenericDeviceCache;
import agents.flexibility.dynamicProgramming.Optimiser;
import agents.flexibility.dynamicProgramming.assessment.AssessmentFunction;
import agents.flexibility.dynamicProgramming.states.StateManagerBuilder.Type;
import de.dlr.gitlab.fame.time.TimePeriod;
import de.dlr.gitlab.fame.time.TimeStamp;

/** States of a device are represented along two dimensions, representing its energy content (state of charge) and time out of
 * balance (shift time)
 * 
 * @author Christoph Schimeczek, Johannes Kochems */
public class EnergyAndTimeStateManager implements StateManager {
	static final String ERR_SELF_DISCHARGE = "Self-discharge is not compatible with state manager of type: ";

	private final GenericDevice device;
	private final GenericDeviceCache deviceCache;
	private final double planningHorizonInHours;

	private final StateDiscretiser stateDiscretiser;
	private final TransitionEvaluator transitionEvaluator;
	private final StateEvaluations stateEvaluations;

	private int numberOfTimeSteps;
	private TimeStamp timeAtFinalState;

	public EnergyAndTimeStateManager(GenericDevice device, AssessmentFunction assessmentFunction,
			double planningHorizonInHours, double energyResolutionInMWH, WaterValues waterValues) {
		this.device = device;
		this.deviceCache = new GenericDeviceCache(device);
		this.stateDiscretiser = new StateDiscretiser(energyResolutionInMWH, device.hasProlonging());
		this.transitionEvaluator = new TransitionEvaluator(stateDiscretiser, deviceCache, assessmentFunction);
		this.planningHorizonInHours = planningHorizonInHours;
		this.stateEvaluations = new StateEvaluations(stateDiscretiser, deviceCache, assessmentFunction, waterValues);
	}

	@Override
	public void initialise(TimePeriod startingPeriod) {
		deviceCache.setPeriod(startingPeriod);
		stateDiscretiser.setTimeResolution(startingPeriod.getDuration());
		numberOfTimeSteps = Optimiser.calcHorizonInPeriodSteps(startingPeriod, planningHorizonInHours);
		double[] energyBoundaries = StateManager.analyseAvailableEnergyLevels(device, numberOfTimeSteps, startingPeriod);
		stateDiscretiser.setBoundaries(energyBoundaries, device.getMaximumShiftTime());
		raiseOnSelfDischarge(startingPeriod);
		stateEvaluations.initialise(startingPeriod, numberOfTimeSteps, stateDiscretiser.getStateCount());
	}

	/** @throws RuntimeException if self-discharge occurs */
	private void raiseOnSelfDischarge(TimePeriod startingPeriod) {
		if (StateManager.hasSelfDischarge(device, numberOfTimeSteps, startingPeriod)) {
			throw new RuntimeException(ERR_SELF_DISCHARGE + Type.ENERGY_AND_TIME);
		}
	}

	@Override
	public void prepareFor(TimeStamp time) {
		timeAtFinalState = time;
		transitionEvaluator.prepareFor(time, false);
		stateEvaluations.prepareFor(time);
		stateDiscretiser.setShiftEnergyDeltaLimits(deviceCache.getMaxNetDischargingEnergyInMWH(),
				deviceCache.getMaxNetChargingEnergyInMWH());
	}

	@Override
	public boolean useStateList() {
		return true;
	}

	@Override
	public int[] getInitialStates() {
		TimeStamp timeAtInitialState = timeAtFinalState.earlierBy(stateDiscretiser.getTimeResolution());
		return stateDiscretiser.getAvailableStates(device.getEnergyContentLowerLimitInMWH(timeAtInitialState),
				device.getEnergyContentUpperLimitInMWH(timeAtInitialState));
	}

	@Override
	public int[] getFinalStates(int initialStateIndex) {
		final double initialEnergyContentInMWH = stateDiscretiser.getEnergyOfStateInMWH(initialStateIndex);
		final double lowestEnergyContentInMWH = deviceCache.getMinTargetEnergyContentInMWH(initialEnergyContentInMWH);
		final double highestEnergyContentInMWH = deviceCache.getMaxTargetEnergyContentInMWH(initialEnergyContentInMWH);
		if (highestEnergyContentInMWH < lowestEnergyContentInMWH) {
			if (highestEnergyContentInMWH < deviceCache.getEnergyContentLowerLimitInMWH()) {
				return new int[] {STATE_UNDERFLOW};
			} else if (lowestEnergyContentInMWH > deviceCache.getEnergyContentUpperLimitInMWH()) {
				return new int[] {STATE_OVERFLOW};
			} else {
				throw new RuntimeException(ERR_INCONSISTENT);
			}
		}
		return stateDiscretiser.getFollowUpStates(initialStateIndex, lowestEnergyContentInMWH, highestEnergyContentInMWH);
	}

	@Override
	public double getTransitionValueFor(int initialStateIndex, int finalStateIndex) {
		double prolongingCostInEUR = 0;
		if (stateDiscretiser.isProlonged(initialStateIndex, finalStateIndex)) {
			double absoluteInitialEnergyInMWH = Math.abs(stateDiscretiser.getEnergyOfStateInMWH(initialStateIndex));
			double absoluteFinalEnergyInMWH = Math.abs(stateDiscretiser.getEnergyOfStateInMWH(finalStateIndex));
			double prolongedEnergyDeltaInMWH = absoluteInitialEnergyInMWH + absoluteFinalEnergyInMWH
					- Math.abs(absoluteInitialEnergyInMWH - absoluteFinalEnergyInMWH);
			prolongingCostInEUR = prolongedEnergyDeltaInMWH * deviceCache.getVariableCostInEURperMWH();
		}
		int initialEnergyIndex = stateDiscretiser.getEnergyIndexOfStateIndex(initialStateIndex);
		int finalEnergyIndex = stateDiscretiser.getEnergyIndexOfStateIndex(finalStateIndex);
		return transitionEvaluator.getTransitionValueFor(initialEnergyIndex, finalEnergyIndex, prolongingCostInEUR);
	}

	@Override
	public double[] getBestValuesNextPeriod() {
		return stateEvaluations.getBestValuesNextPeriod();
	}

	@Override
	public void updateBestFinalState(int initialStateIndex, int bestFinalStateIndex, double bestAssessmentValue) {
		stateEvaluations.updateBestFinalState(initialStateIndex, bestFinalStateIndex, bestAssessmentValue);
	}

	@Override
	public int getNumberOfForecastTimeSteps() {
		return numberOfTimeSteps;
	}

	@Override
	public DispatchSchedule getBestDispatchSchedule(int schedulingSteps) {
		return stateEvaluations.buildDispatchSchedule(schedulingSteps, device.getCurrentInternalEnergyInMWH(),
				device.getCurrentShiftTimeInSteps());
	}

	@Override
	public ArrayList<TimeStamp> getPlanningTimes(TimePeriod startingPeriod) {
		return StateManager.createPlanningTimes(startingPeriod, planningHorizonInHours);
	}
}
