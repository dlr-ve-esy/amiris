// SPDX-FileCopyrightText: 2025-2026 German Aerospace Center <amiris@dlr.de>
//
// SPDX-License-Identifier: Apache-2.0
package agents.flexibility.dynamicProgramming.states;

import java.util.ArrayList;
import agents.flexibility.GenericDevice;
import agents.flexibility.GenericDeviceCache;
import agents.flexibility.dynamicProgramming.Optimiser;
import agents.flexibility.dynamicProgramming.assessment.AssessmentFunction;
import de.dlr.gitlab.fame.time.TimePeriod;
import de.dlr.gitlab.fame.time.TimeSpan;
import de.dlr.gitlab.fame.time.TimeStamp;

/** States of a device are represented along one dimension, representing its energy content or state of charge
 * 
 * @author Christoph Schimeczek, Felix Nitsch, Johannes Kochems */
public class EnergyStateManager implements StateManager {
	private static final TimeSpan MAX_SHIFT_TIME = new TimeSpan(0);

	private final GenericDevice device;
	private final GenericDeviceCache deviceCache;
	private final double planningHorizonInHours;

	private final StateDiscretiser stateDiscretiser;
	private final TransitionEvaluator transitionEvaluator;
	private final StateEvaluations stateEvaluations;

	private int numberOfTimeSteps;
	private boolean hasSelfDischarge;
	private TimeStamp timeAtFinalState;

	public EnergyStateManager(GenericDevice device, AssessmentFunction assessmentFunction, double planningHorizonInHours,
			double energyResolutionInMWH, WaterValues waterValues) {
		this.device = device;
		this.deviceCache = new GenericDeviceCache(device);
		this.stateDiscretiser = new StateDiscretiser(energyResolutionInMWH, false);
		this.transitionEvaluator = new TransitionEvaluator(stateDiscretiser, deviceCache, assessmentFunction);
		this.stateEvaluations = new StateEvaluations(stateDiscretiser, deviceCache, assessmentFunction, waterValues);
		this.planningHorizonInHours = planningHorizonInHours;
	}

	@Override
	public void initialise(TimePeriod startingPeriod) {
		deviceCache.setPeriod(startingPeriod);
		stateDiscretiser.setTimeResolution(startingPeriod.getDuration());
		numberOfTimeSteps = Optimiser.calcHorizonInPeriodSteps(startingPeriod, planningHorizonInHours);
		double[] energyBoundaries = StateManager.analyseAvailableEnergyLevels(device, numberOfTimeSteps, startingPeriod);
		stateDiscretiser.setBoundaries(energyBoundaries, MAX_SHIFT_TIME);
		hasSelfDischarge = StateManager.hasSelfDischarge(device, numberOfTimeSteps, startingPeriod);
		stateEvaluations.initialise(startingPeriod, numberOfTimeSteps, stateDiscretiser.getStateCount());
	}

	@Override
	public void prepareFor(TimeStamp time) {
		timeAtFinalState = time;
		transitionEvaluator.prepareFor(time, hasSelfDischarge);
		stateEvaluations.prepareFor(time);
	}

	@Override
	public boolean useStateList() {
		return false;
	}

	@Override
	public int[] getInitialStates() {
		TimeStamp timeAtInitialState = timeAtFinalState.earlierBy(stateDiscretiser.getTimeResolution());
		return stateDiscretiser.getEnergyStateLimits(device.getEnergyContentLowerLimitInMWH(timeAtInitialState),
				device.getEnergyContentUpperLimitInMWH(timeAtInitialState));
	}

	@Override
	public int[] getFinalStates(int initialStateIndex) {
		final double initialEnergyContentInMWH = stateDiscretiser.getEnergyOfStateInMWH(initialStateIndex);
		final double lowestEnergyContentInMWH = deviceCache.getMinTargetEnergyContentInMWH(initialEnergyContentInMWH);
		final double highestEnergyContentInMWH = deviceCache.getMaxTargetEnergyContentInMWH(initialEnergyContentInMWH);
		return stateDiscretiser.getEnergyStateLimits(lowestEnergyContentInMWH, highestEnergyContentInMWH);
	}

	@Override
	public double getTransitionValueFor(int initialStateIndex, int finalStateIndex) {
		return transitionEvaluator.getTransitionValueFor(initialStateIndex, finalStateIndex);
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
		return stateEvaluations.buildDispatchSchedule(schedulingSteps, device.getCurrentInternalEnergyInMWH(), 0L);
	}

	@Override
	public ArrayList<TimeStamp> getPlanningTimes(TimePeriod startingPeriod) {
		return StateManager.createPlanningTimes(startingPeriod, planningHorizonInHours);
	}
}
