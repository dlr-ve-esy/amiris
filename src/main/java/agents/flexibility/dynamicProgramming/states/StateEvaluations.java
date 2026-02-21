// SPDX-FileCopyrightText: 2026 German Aerospace Center <amiris@dlr.de>
//
// SPDX-License-Identifier: Apache-2.0
package agents.flexibility.dynamicProgramming.states;

import agents.flexibility.GenericDeviceCache;
import agents.flexibility.dynamicProgramming.assessment.AssessmentFunction;
import agents.flexibility.dynamicProgramming.states.StateManager.DispatchSchedule;
import de.dlr.gitlab.fame.time.TimePeriod;
import de.dlr.gitlab.fame.time.TimeStamp;

/** Holds evaluations of states
 * 
 * @author Christoph Schimeczek */
public class StateEvaluations {
	private final StateDiscretiser stateDiscretiser;
	private final GenericDeviceCache deviceCache;
	private final AssessmentFunction assessmentFunction;

	private int numberOfTimeSteps;
	private TimePeriod startingPeriod;

	private int[][] bestNextState;
	private double[][] bestValue;
	private double[] cachedWaterValuesInEUR;

	private int currentOptimisationTimeIndex;

	public StateEvaluations(StateDiscretiser stateDiscretiser, GenericDeviceCache deviceCache,
			AssessmentFunction assessmentFunction) {
		this.stateDiscretiser = stateDiscretiser;
		this.deviceCache = deviceCache;
		this.assessmentFunction = assessmentFunction;
	}

	public void initialise(TimePeriod startingPeriod, int numberOfTimeSteps, int stateCount, WaterValues waterValues) {
		this.startingPeriod = startingPeriod;
		this.numberOfTimeSteps = numberOfTimeSteps;
		bestNextState = new int[numberOfTimeSteps][stateCount];
		bestValue = new double[numberOfTimeSteps][stateCount];
		cachedWaterValuesInEUR = new double[stateCount];
		TimeStamp targetTime = StateManager.getTimeByIndex(startingPeriod, numberOfTimeSteps);
		cacheWaterValues(waterValues, targetTime);
	}

	/** Caches water values for each possible state and stores them to {@link #cachedWaterValuesInEUR} */
	private void cacheWaterValues(WaterValues waterValues, TimeStamp targetTime) {
		if (waterValues != null && waterValues.hasData()) {
			for (int index = 0; index < cachedWaterValuesInEUR.length; index++) {
				cachedWaterValuesInEUR[index] = waterValues.getValueInEUR(targetTime,
						stateDiscretiser.energyIndexToEnergyInMWH(index));
			}
		}
	}

	public void prepareFor(TimeStamp time) {
		currentOptimisationTimeIndex = StateManager.getCurrentOptimisationTimeIndex(time, startingPeriod);
	}

	public double[] getBestValuesNextPeriod() {
		if (currentOptimisationTimeIndex + 1 < numberOfTimeSteps) {
			return bestValue[currentOptimisationTimeIndex + 1];
		} else {
			return cachedWaterValuesInEUR;
		}
	}

	public void updateBestFinalState(int initialStateIndex, int bestFinalStateIndex, double bestAssessmentValue) {
		bestValue[currentOptimisationTimeIndex][initialStateIndex] = bestAssessmentValue;
		bestNextState[currentOptimisationTimeIndex][initialStateIndex] = bestFinalStateIndex;
	}

	public DispatchSchedule buildDispatchSchedule(int schedulingSteps, double initialEnergyLevel,
			long initialShiftTimeSteps) {
		double currentInternalEnergyInMWH = initialEnergyLevel;
		int currentShiftTimeIndex = stateDiscretiser.roundToNearestShiftTimeIndex(initialShiftTimeSteps);

		double[] externalEnergyDeltaInMWH = new double[schedulingSteps];
		double[] internalEnergiesInMWH = new double[schedulingSteps];
		double[] specificValuesInEURperMWH = new double[schedulingSteps];
		double[] expectedElectricityPriceInEURperMWH = new double[schedulingSteps];

		for (int timeIndex = 0; timeIndex < schedulingSteps; timeIndex++) {
			TimeStamp time = StateManager.getTimeByIndex(startingPeriod, timeIndex);
			deviceCache.prepareFor(time);

			internalEnergiesInMWH[timeIndex] = currentInternalEnergyInMWH;
			int currentEnergyLevelIndex = stateDiscretiser.energyToNearestEnergyIndex(currentInternalEnergyInMWH);
			int stateIndex = stateDiscretiser.getStateIndex(currentEnergyLevelIndex, currentShiftTimeIndex);
			int nextStateIndex = bestNextState[timeIndex][stateIndex];
			double plannedEnergyDeltaInMWH = stateDiscretiser.calcEnergyDeltaInMWH(stateIndex, nextStateIndex);
			double nextInternalEnergyInMWH = StateManager.calcNextEnergyInMWH(deviceCache, currentInternalEnergyInMWH,
					plannedEnergyDeltaInMWH);
			externalEnergyDeltaInMWH[timeIndex] = deviceCache.simulateTransition(currentInternalEnergyInMWH,
					nextInternalEnergyInMWH);
			currentInternalEnergyInMWH = nextInternalEnergyInMWH;
			currentShiftTimeIndex = stateDiscretiser.calcShiftTimeIndexFromStateIndex(nextStateIndex);

			double rawValueDeltaInEUR = getValueOfStorage(timeIndex + 1, nextStateIndex)
					- getValueOfStorage(timeIndex + 1, stateIndex);
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

}