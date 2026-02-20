// SPDX-FileCopyrightText: 2025-2026 German Aerospace Center <amiris@dlr.de>
//
// SPDX-License-Identifier: Apache-2.0
package agents.flexibility.dynamicProgramming.states;

import java.util.ArrayList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
	private static final Logger logger = LoggerFactory.getLogger(EnergyStateManager.class);

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
	private boolean hasSelfDischarge;

	private int[][] bestNextState;
	private double[][] bestValue;
	private double[] transitionValuesCharging;
	private double[] transitionValuesDischarging;
	private double[] cachedWaterValuesInEUR;

	public EnergyStateManager(GenericDevice device, AssessmentFunction assessmentFunction, double planningHorizonInHours,
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
		this.startingPeriod = startingPeriod;
		deviceCache.setPeriod(startingPeriod);
		stateDiscretiser = new StateDiscretiser(energyResolutionInMWH, startingPeriod.getDuration(), false);
		numberOfTimeSteps = Optimiser.calcHorizonInPeriodSteps(startingPeriod, planningHorizonInHours);
		double[] energyBoundaries = StateManager.analyseAvailableEnergyLevels(device, numberOfTimeSteps, startingPeriod);
		stateDiscretiser.setBoundaries(energyBoundaries, new TimeSpan(0));
		hasSelfDischarge = StateManager.hasSelfDischarge(device, numberOfTimeSteps, startingPeriod);
		bestNextState = new int[numberOfTimeSteps][stateDiscretiser.getStateCount()];
		bestValue = new double[numberOfTimeSteps][stateDiscretiser.getStateCount()];
		cacheWaterValues();
	}

	/** Caches water values for each possible state and stores them to {@link #cachedWaterValuesInEUR} */
	private void cacheWaterValues() {
		cachedWaterValuesInEUR = new double[stateDiscretiser.getStateCount()];
		if (waterValues.hasData()) {
			TimeStamp targetTime = StateManager.getTimeByIndex(startingPeriod, numberOfTimeSteps);
			for (int index = 0; index < stateDiscretiser.getStateCount(); index++) {
				cachedWaterValuesInEUR[index] = waterValues.getValueInEUR(targetTime,
						stateDiscretiser.energyIndexToEnergyInMWH(index));
			}
		}
	}

	@Override
	public void prepareFor(TimeStamp time) {
		assessmentFunction.prepareFor(time);
		deviceCache.prepareFor(time);
		currentOptimisationTimeIndex = StateManager.getCurrentOptimisationTimeIndex(time, startingPeriod);
		if (!hasSelfDischarge) {
			cacheTransitionValuesNoSelfDischarge();
		}
	}

	/** Cache values of transitions - only applicable without self discharge */
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

	/** @return calculated value of transition */
	private double calcValueFor(int initialStateIndex, int finalStateIndex) {
		final double externalEnergyDeltaInMWH = deviceCache.simulateTransition(
				stateDiscretiser.energyIndexToEnergyInMWH(initialStateIndex),
				stateDiscretiser.energyIndexToEnergyInMWH(finalStateIndex));
		return assessmentFunction.assessTransition(externalEnergyDeltaInMWH);
	}

	@Override
	public boolean useStateList() {
		return false;
	}

	@Override
	public int[] getInitialStates() {
		return stateDiscretiser.getEnergyStateLimits(deviceCache.getEnergyContentLowerLimitInMWH(),
				deviceCache.getEnergyContentUpperLimitInMWH());
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
		return hasSelfDischarge ? calcValueFor(initialStateIndex, finalStateIndex)
				: getCachedValueFor(initialStateIndex, finalStateIndex);
	}

	/** @return cached value of transition, only available without self discharge */
	private double getCachedValueFor(int initialStateIndex, int finalStateIndex) {
		int stateDelta = finalStateIndex - initialStateIndex;
		return stateDelta >= 0 ? transitionValuesCharging[stateDelta] : transitionValuesDischarging[-stateDelta];
	}

	@Override
	public double[] getBestValuesNextPeriod() {
		if (currentOptimisationTimeIndex + 1 < numberOfTimeSteps) {
			return bestValue[currentOptimisationTimeIndex + 1];
		} else {
			return cachedWaterValuesInEUR;
		}
	}

	@Override
	public void updateBestFinalState(int initialStateIndex, int bestFinalStateIndex, double bestAssessmentValue) {
		bestValue[currentOptimisationTimeIndex][initialStateIndex] = bestAssessmentValue;
		bestNextState[currentOptimisationTimeIndex][initialStateIndex] = bestFinalStateIndex;
	}

	@Override
	public int getNumberOfForecastTimeSteps() {
		return numberOfTimeSteps;
	}

	@Override
	public DispatchSchedule getBestDispatchSchedule(int schedulingSteps) {
		double currentInternalEnergyInMWH = device.getCurrentInternalEnergyInMWH();
		double[] externalEnergyDeltaInMWH = new double[schedulingSteps];
		double[] internalEnergiesInMWH = new double[schedulingSteps];
		double[] specificValuesInEURperMWH = new double[schedulingSteps];
		double[] expectedElectricityPriceInEURperMWH = new double[schedulingSteps];

		for (int timeIndex = 0; timeIndex < schedulingSteps; timeIndex++) {
			TimeStamp time = StateManager.getTimeByIndex(startingPeriod, timeIndex);
			deviceCache.prepareFor(time);

			internalEnergiesInMWH[timeIndex] = currentInternalEnergyInMWH;
			int currentEnergyLevelIndex = stateDiscretiser.energyToNearestEnergyIndex(currentInternalEnergyInMWH);
			int nextEnergyLevelIndex = bestNextState[timeIndex][currentEnergyLevelIndex];
			double plannedEnergyDeltaInMWH = (nextEnergyLevelIndex - currentEnergyLevelIndex) * energyResolutionInMWH;

			double nextInternalEnergyInMWH = StateManager.calcNextEnergyInMWH(deviceCache, currentInternalEnergyInMWH,
					plannedEnergyDeltaInMWH);
			externalEnergyDeltaInMWH[timeIndex] = deviceCache.simulateTransition(currentInternalEnergyInMWH,
					nextInternalEnergyInMWH);
			currentInternalEnergyInMWH = nextInternalEnergyInMWH;

			double rawValueDeltaInEUR = getValueOfStorage(timeIndex + 1, nextEnergyLevelIndex)
					- getValueOfStorage(timeIndex + 1, currentEnergyLevelIndex);
			specificValuesInEURperMWH[timeIndex] = StateManager.calcSpecificValueInEURperMWH(plannedEnergyDeltaInMWH,
					rawValueDeltaInEUR);

			expectedElectricityPriceInEURperMWH[timeIndex] = assessmentFunction
					.getElectricityPriceAt(time, externalEnergyDeltaInMWH[timeIndex]);
		}
		return new DispatchSchedule(externalEnergyDeltaInMWH, internalEnergiesInMWH, specificValuesInEURperMWH,
				expectedElectricityPriceInEURperMWH);
	}

	/** @return the value of storage for given time and state index */
	private double getValueOfStorage(int timeIndex, int stateIndex) {
		return timeIndex < numberOfTimeSteps ? bestValue[timeIndex][stateIndex] : cachedWaterValuesInEUR[stateIndex];
	}

	@Override
	public ArrayList<TimeStamp> getPlanningTimes(TimePeriod startingPeriod) {
		int numberOfTimeSteps = Optimiser.calcHorizonInPeriodSteps(startingPeriod, planningHorizonInHours);
		ArrayList<TimeStamp> planningTimes = new ArrayList<>(numberOfTimeSteps);
		for (int step = 0; step < numberOfTimeSteps; step++) {
			planningTimes.add(startingPeriod.shiftByDuration(step).getStartTime());
		}
		return planningTimes;
	}
}
