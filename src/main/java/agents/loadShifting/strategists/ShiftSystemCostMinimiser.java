// SPDX-FileCopyrightText: 2024-2026 German Aerospace Center <amiris@dlr.de>
//
// SPDX-License-Identifier: Apache-2.0
package agents.loadShifting.strategists;

import java.util.ArrayList;
import java.util.HashMap;
import agents.loadShifting.LoadShiftingPortfolio;
import agents.loadShifting.strategists.LoadShiftStateManager.LoadShiftState;
import agents.markets.meritOrder.sensitivities.MarginalCostSensitivity;
import agents.markets.meritOrder.sensitivities.MeritOrderSensitivity;
import de.dlr.gitlab.fame.agent.input.ParameterData;
import de.dlr.gitlab.fame.agent.input.ParameterData.MissingDataException;
import de.dlr.gitlab.fame.time.TimePeriod;

/** Determines a scheduling strategy for a {@link LoadShiftingPortfolio} in order to minimise overall system costs.
 * 
 * @author Johannes Kochems, Christoph Schimeczek */
public class ShiftSystemCostMinimiser extends LoadShiftingStrategist {
	private final int maximumShiftTime;
	private final LoadShiftStateManager stateManager;

	/** costSum[t][d][i]: summed marginal cost to period t being in internal energy state i for a duration of d */
	private final double[][][] followUpCostSum;
	/** bestNextState[t][d][i]: best next internal load shift state when current energy state is i for a duration d in period t */
	private final LoadShiftState[][][] bestNextState;

	/** Instantiate {@link ShiftSystemCostMinimiser}
	 * 
	 * @param generalInput parameters associated with strategists in general
	 * @param specificInput for {@link ShiftSystemCostMinimiser}
	 * @param loadShiftingPortfolio for which schedules are to be created
	 * @throws MissingDataException if any required input is missing */
	public ShiftSystemCostMinimiser(ParameterData generalInput, ParameterData specificInput,
			LoadShiftingPortfolio loadShiftingPortfolio) throws MissingDataException {
		super(generalInput, specificInput, loadShiftingPortfolio);
		stateManager = new LoadShiftStateManager(loadShiftingPortfolio);
		maximumShiftTime = loadShiftingPortfolio.getMaximumShiftTimeInHours();
		followUpCostSum = new double[forecastSteps][maximumShiftTime][stateManager.getNumberOfEnergyStates()];
		bestNextState = new LoadShiftState[forecastSteps][maximumShiftTime][stateManager.getNumberOfEnergyStates()];
	}

	@Override
	public void updateSchedule(TimePeriod startTime, double currentEnergyShiftStorageLevelInMWH, int currentShiftTime) {
		clearPlanningArrays();
		optimiseDispatch(startTime);
		updateScheduleArrays(currentEnergyShiftStorageLevelInMWH, currentShiftTime);
	}

	/** replaces all entries in the planning arrays with 0 or Integer.MIN_VALUE */
	private void clearPlanningArrays() {
		for (int t = 0; t < forecastSteps; t++) {
			for (int shiftTime = 0; shiftTime < maximumShiftTime; shiftTime++) {
				for (int initialState = 0; initialState < stateManager.getNumberOfEnergyStates(); initialState++) {
					followUpCostSum[t][shiftTime][initialState] = 0.0;
					bestNextState[t][shiftTime][initialState] = LoadShiftStateManager.INFEASIBLE_STATE;
				}
			}
		}
	}

	/** update less costly final state for each possible initial state in every period */
	private void optimiseDispatch(TimePeriod startTime) {
		ArrayList<LoadShiftState> initialStates = new ArrayList<>();
		HashMap<LoadShiftState, Double> nextFeasibleStates = new HashMap<>();
		boolean isLastPeriod = true;
		for (int k = 0; k < forecastSteps; k++) {
			int period = forecastSteps - k - 1; // step backwards in time
			int nextPeriod = period + 1;
			TimePeriod timePeriod = startTime.shiftByDuration(period);
			double[] costSteps = calcCostSteps(timePeriod);
			stateManager.insertInitialStates(initialStates);
			double specificShiftCostsInEURperMWH = portfolio.getVariableShiftCostsInEURPerMWH(timePeriod.getStartTime());

			for (LoadShiftState initialState : initialStates) {
				TimePeriod nextTimePeriod = timePeriod.shiftByDuration(1);
				stateManager.insertNextFeasibleStates(initialState, nextFeasibleStates, nextTimePeriod, isLastPeriod);
				double currentLowestCost = Double.MAX_VALUE;
				LoadShiftState bestFinalState = LoadShiftStateManager.INFEASIBLE_STATE;
				for (LoadShiftState finalState : nextFeasibleStates.keySet()) {
					double cost;
					if (stateManager.isInfeasibleTransition(initialState, finalState, nextTimePeriod)) {
						cost = LoadShiftStateManager.PENALTY;
					} else {
						int powerStateDelta = finalState.calculateStateDelta(initialState);
						double absPowerDeltaInMWH = Math.abs(powerStateDelta) * portfolio.getEnergyResolutionInMWH();
						double variableShiftCosts = specificShiftCostsInEURperMWH * absPowerDeltaInMWH;
						int costStepIndex = (stateManager.getNumberOfPowerStates() - 1) / 2 + powerStateDelta;
						cost = costSteps[costStepIndex] + getFollowUpCost(nextPeriod, finalState) + variableShiftCosts
								+ nextFeasibleStates.get(finalState);
					}
					if (cost < currentLowestCost) {
						currentLowestCost = cost;
						bestFinalState = finalState;
					}
				}
				followUpCostSum[period][initialState.shiftTime][initialState.energyState] = currentLowestCost;
				bestNextState[period][initialState.shiftTime][initialState.energyState] = bestFinalState;
			}
			isLastPeriod = false;
		}
	}

	private double[] calcCostSteps(TimePeriod timePeriod) {
		MarginalCostSensitivity sensitivity = (MarginalCostSensitivity) getSensitivityForPeriod(timePeriod);
		if (sensitivity != null) {
			return sensitivity.getValuesInSteps((stateManager.getNumberOfPowerStates() - 1) / 2);
		} else {
			return new double[stateManager.getNumberOfPowerStates()];
		}
	}

	/** @return minimal cost of best strategy until given state */
	private double getFollowUpCost(int hour, LoadShiftState state) {
		return hour < forecastSteps ? followUpCostSum[hour][state.shiftTime][state.energyState] : 0;
	}

	/** Update the schedule arrays by calculating energy, energy delta and retrieving shift times as well as setting prices based on
	 * the energy state delta. */
	private void updateScheduleArrays(double currentEnergyShiftStorageLevelInMWH, int currentShiftTime) {
		double energyPerState = portfolio.getPowerInMW() / ((stateManager.getNumberOfPowerStates() - 1) / 2);
		int initialEnergyState = (int) Math.round(currentEnergyShiftStorageLevelInMWH / energyPerState)
				+ stateManager.getZeroEnergyStateIndex();
		for (int period = 0; period < scheduleDurationPeriods; period++) {
			scheduledInitialEnergyInMWH[period] = energyPerState * initialEnergyState - portfolio.getEnergyLimitDownInMWH();
			LoadShiftState nextState = bestNextState[period][currentShiftTime][initialEnergyState];
			int energyStateDelta = nextState.energyState - initialEnergyState;
			double energyDelta = energyStateDelta * energyPerState;
			demandScheduleInMWH[period] = energyDelta;
			if (energyStateDelta == 0) {
				priceScheduleInEURperMWH[period] = Double.NaN;
			} else {
				priceScheduleInEURperMWH[period] = energyDelta > 0 ? Double.MAX_VALUE : -Double.MAX_VALUE;
			}
			initialEnergyState = nextState.energyState;
			currentShiftTime = nextState.shiftTime;
		}
	}

	/** @return a {@link MarginalCostSensitivity} item */
	@Override
	protected MeritOrderSensitivity createBlankSensitivity() {
		return new MarginalCostSensitivity();
	}
}
