// SPDX-FileCopyrightText: 2025 German Aerospace Center <amiris@dlr.de>
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
	private static final String ERR_SELF_DISCHARGE = "Self-discharge is not compatible with state manager of type: ";
	private static final String ERR_WATER_VALUES = "Water values are not compatible with state manager of type: ";

	private final GenericDevice device;
	private final GenericDeviceCache deviceCache;
	private final AssessmentFunction assessmentFunction;
	private final double planningHorizonInHours;
	private final double energyResolutionInMWH;
	private final WaterValues waterValues;

	private StateDiscretiser stateDiscretiser;
	private int numberOfTimeSteps;
	private TimePeriod startingPeriod;
	private int currentOptimisationTimeIndex;

	private int[][] bestNextState;
	private double[][] bestValue;
	private double[] transitionValuesCharging;
	private double[] transitionValuesDischarging;

	public EnergyAndTimeStateManager(GenericDevice device, AssessmentFunction assessmentFunction,
			double planningHorizonInHours,
			double energyResolutionInMWH, WaterValues waterValues) {
		this.device = device;
		this.deviceCache = new GenericDeviceCache(device);
		this.assessmentFunction = assessmentFunction;
		this.planningHorizonInHours = planningHorizonInHours;
		this.energyResolutionInMWH = energyResolutionInMWH;
		this.waterValues = waterValues;
	}

	@Override
	public void initialise(TimePeriod startingPeriod) {
		this.numberOfTimeSteps = Optimiser.calcHorizonInPeriodSteps(startingPeriod, planningHorizonInHours);
		this.startingPeriod = startingPeriod;
		deviceCache.setPeriod(startingPeriod);
		stateDiscretiser = new StateDiscretiser(energyResolutionInMWH, startingPeriod.getDuration());
		double[] energyBoundaries = StateManager.analyseAvailableEnergyLevels(device, numberOfTimeSteps, startingPeriod);
		stateDiscretiser.setBoundaries(energyBoundaries, device.getMaximumShiftTimeInSteps());
		raiseOnSelfDischarge();
		raiseOnWaterValues();
		bestNextState = new int[numberOfTimeSteps][stateDiscretiser.getNumberOfStates()];
		bestValue = new double[numberOfTimeSteps][stateDiscretiser.getNumberOfStates()];
	}

	private void raiseOnSelfDischarge() {
		if (StateManager.hasSelfDischarge(device, numberOfTimeSteps, startingPeriod)) {
			new RuntimeException(ERR_SELF_DISCHARGE + Type.ENERGY_AND_TIME);
		}
	}

	private void raiseOnWaterValues() {
		if (waterValues.hasData()) {
			new RuntimeException(ERR_WATER_VALUES + Type.ENERGY_AND_TIME);
		}
	}

	@Override
	public void prepareFor(TimeStamp time) {
		assessmentFunction.prepareFor(time);
		deviceCache.prepareFor(time);
		currentOptimisationTimeIndex = StateManager.getCurrentOptimisationTimeIndex(time, startingPeriod);
		cacheTransitions();
	}

	/** Cache values of transitions */
	private void cacheTransitions() {
		int maxChargingSteps = stateDiscretiser.discretiseEnergyDelta(deviceCache.getMaxNetChargingEnergyInMWH());
		transitionValuesCharging = new double[maxChargingSteps + 1];
		for (int chargingSteps = 0; chargingSteps <= maxChargingSteps; chargingSteps++) {
			transitionValuesCharging[chargingSteps] = calcEnergyValueFor(0, chargingSteps);
		}
		int maxDischargingSteps = stateDiscretiser.discretiseEnergyDelta(-deviceCache.getMaxNetDischargingEnergyInMWH());
		transitionValuesDischarging = new double[maxDischargingSteps + 1];
		for (int dischargingSteps = 0; dischargingSteps <= maxDischargingSteps; dischargingSteps++) {
			transitionValuesDischarging[dischargingSteps] = calcEnergyValueFor(0, -dischargingSteps);
		}
	}

	/** @return calculated value of transition */
	private double calcEnergyValueFor(int initialStateIndex, int finalStateIndex) {
		double externalEnergyDeltaInMWH = deviceCache.simulateTransition(
				stateDiscretiser.energyIndexToEnergy(initialStateIndex), stateDiscretiser.energyIndexToEnergy(finalStateIndex));
		return assessmentFunction.assessTransition(externalEnergyDeltaInMWH);
	}

	@Override
	public boolean useStateList() {
		return true;
	}

	@Override
	public int[] getInitialStates() {
		return stateDiscretiser.getAllAvailableStates();
	}

	@Override
	public int[] getFinalStates(int initialStateIndex) {
		final double initialEnergyContentInMWH = stateDiscretiser.getEnergyOfStateInMWH(initialStateIndex);
		final double lowestEnergyContentInMWH = deviceCache.getMinTargetEnergyContentInMWH(initialEnergyContentInMWH);
		final double highestEnergyContentInMWH = deviceCache.getMaxTargetEnergyContentInMWH(initialEnergyContentInMWH);
		return stateDiscretiser.getFollowUpStates(initialStateIndex, lowestEnergyContentInMWH, highestEnergyContentInMWH);
	}

	@Override
	public double getTransitionValueFor(int initialStateIndex, int finalStateIndex) {
		// Include penalty and prolonging cost
		return 0;
	}

	@Override
	public double[] getBestValuesNextPeriod() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void updateBestFinalState(int initialStateIndex, int bestFinalStateIndex, double bestAssessmentValue) {
		// TODO Auto-generated method stub

	}

	@Override
	public int getNumberOfForecastTimeSteps() {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public DispatchSchedule getBestDispatchSchedule(int schedulingSteps) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public ArrayList<TimeStamp> getPlanningTimes(TimePeriod startingPeriod) {
		// TODO Auto-generated method stub
		return null;
	}

}
