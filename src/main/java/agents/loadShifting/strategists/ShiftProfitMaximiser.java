// SPDX-FileCopyrightText: 2024-2026 German Aerospace Center <amiris@dlr.de>
//
// SPDX-License-Identifier: Apache-2.0
package agents.loadShifting.strategists;

import java.util.ArrayList;
import java.util.HashMap;
import agents.loadShifting.LoadShiftingPortfolio;
import agents.loadShifting.strategists.LoadShiftStateManager.LoadShiftState;
import agents.markets.meritOrder.Constants;
import agents.markets.meritOrder.sensitivities.MeritOrderSensitivity;
import agents.markets.meritOrder.sensitivities.PriceSensitivity;
import agents.markets.meritOrder.sensitivities.StepPower;
import de.dlr.gitlab.fame.agent.input.ParameterData;
import de.dlr.gitlab.fame.agent.input.ParameterData.MissingDataException;
import de.dlr.gitlab.fame.time.TimePeriod;

/** Determines a scheduling strategy for a {@link LoadShiftingPortfolio} in order to maximise profits
 * 
 * @author Johannes Kochems, Christoph Schimeczek */
public class ShiftProfitMaximiser extends LoadShiftingStrategist {
	private final double purchaseLeviesAndTaxesInEURperMWH;
	private final int maximumShiftTime;
	private final LoadShiftStateManager stateManager;

	/** incomeSum[t][d][i]: summed marginal cost to period t being in internal energy state i for a duration of d */
	private final double[][][] incomeSum;
	/** bestNextState[t][d][i]: best next internal load shift state when current energy state is i for a duration d in period t */
	private final LoadShiftState[][][] bestNextState;

	/** Instantiate {@link ShiftProfitMaximiser}
	 * 
	 * @param generalInput parameters associated with strategists in general
	 * @param specificInput for {@link ShiftProfitMaximiser}
	 * @param loadShiftingPortfolio for which schedules are to be created
	 * @throws MissingDataException if any required input is missing */
	public ShiftProfitMaximiser(ParameterData generalInput, ParameterData specificInput,
			LoadShiftingPortfolio loadShiftingPortfolio) throws MissingDataException {
		super(generalInput, specificInput, loadShiftingPortfolio);
		stateManager = new LoadShiftStateManager(loadShiftingPortfolio);
		maximumShiftTime = loadShiftingPortfolio.getMaximumShiftTimeInHours();
		purchaseLeviesAndTaxesInEURperMWH = specificInput.getDoubleOrDefault("PurchaseTaxesAndLevies", 0.);
		incomeSum = new double[forecastSteps][maximumShiftTime][stateManager.getNumberOfEnergyStates()];
		bestNextState = new LoadShiftState[forecastSteps][maximumShiftTime][stateManager
				.getNumberOfEnergyStates()];
	}

	@Override
	public void updateSchedule(TimePeriod startTime, double currentEnergyShiftStorageLevelInMWH, int currentShiftTime) {
		clearPlanningArrays();
		optimiseDispatch(startTime);
		updateScheduleArrays(startTime, currentEnergyShiftStorageLevelInMWH, currentShiftTime);
	}

	/** replaces all entries in the planning arrays with 0 or Integer.MIN_VALUE */
	private void clearPlanningArrays() {
		for (int t = 0; t < forecastSteps; t++) {
			for (int shiftTime = 0; shiftTime < maximumShiftTime; shiftTime++) {
				for (int initialState = 0; initialState < stateManager.getNumberOfEnergyStates(); initialState++) {
					incomeSum[t][shiftTime][initialState] = 0.0;
					bestNextState[t][shiftTime][initialState] = LoadShiftStateManager.INFEASIBLE_STATE;
				}
			}
		}
	}

	/** update most profitable final state for each possible initial state in every period */
	private void optimiseDispatch(TimePeriod startTime) {
		ArrayList<LoadShiftState> initialStates = new ArrayList<>();
		HashMap<LoadShiftState, Double> nextFeasibleStates = new HashMap<>();
		boolean isLastPeriod = true;
		for (int k = 0; k < forecastSteps; k++) {
			int period = forecastSteps - k - 1; // step backwards in time
			int nextPeriod = period + 1;
			TimePeriod timePeriod = startTime.shiftByDuration(period);
			double[] chargePrices = calcChargePrices(timePeriod);
			StepPower stepPower = calcStepPower(timePeriod);
			stateManager.insertInitialStates(initialStates);
			double specificShiftCostsInEURperMWH = portfolio.getVariableShiftCostsInEURPerMWH(timePeriod.getStartTime());

			for (LoadShiftState initialState : initialStates) {
				TimePeriod nextTimePeriod = timePeriod.shiftByDuration(1);
				stateManager.insertNextFeasibleStates(initialState, nextFeasibleStates, nextTimePeriod, isLastPeriod);
				double currentBestIncome = -Double.MAX_VALUE;
				LoadShiftState bestFinalState = LoadShiftStateManager.INFEASIBLE_STATE;
				for (LoadShiftState finalState : nextFeasibleStates.keySet()) {
					double income;
					if (stateManager.isInfeasibleTransition(initialState, finalState, nextTimePeriod)) {
						income = -LoadShiftStateManager.PENALTY;
					} else {
						int powerStateDelta = finalState.calculateStateDelta(initialState);
						double absPowerDeltaInMWH = Math.abs(powerStateDelta) * portfolio.getEnergyResolutionInMWH();
						double variableShiftCosts = specificShiftCostsInEURperMWH * absPowerDeltaInMWH;
						double incomeTransition = calcIncomeTransition(powerStateDelta, chargePrices, stepPower);
						income = incomeTransition + getBestIncome(nextPeriod, finalState)
								- variableShiftCosts - nextFeasibleStates.get(finalState);
					}
					if (income > currentBestIncome) {
						currentBestIncome = income;
						bestFinalState = finalState;
					}
				}
				incomeSum[period][initialState.shiftTime][initialState.energyState] = currentBestIncome;
				bestNextState[period][initialState.shiftTime][initialState.energyState] = bestFinalState;
			}
			isLastPeriod = false;
		}
	}

	/** @return price steps for charging & discharging in the specified {@link TimePeriod} */
	private double[] calcChargePrices(TimePeriod timePeriod) {
		final PriceSensitivity sensitivity = (PriceSensitivity) getSensitivityForPeriod(timePeriod);
		if (sensitivity != null) {
			return sensitivity.getValuesInSteps((stateManager.getNumberOfPowerStates() - 1) / 2);
		} else {
			return new double[stateManager.getNumberOfPowerStates()];
		}
	}

	/** @return power steps for charging & discharging in the specified {@link TimePeriod} */
	private StepPower calcStepPower(TimePeriod timePeriod) {
		final PriceSensitivity sensitivity = (PriceSensitivity) getSensitivityForPeriod(timePeriod);
		if (sensitivity != null) {
			return sensitivity.getStepPowers((stateManager.getNumberOfPowerStates() - 1) / 2);
		} else {
			return new StepPower(0, 0, (stateManager.getNumberOfPowerStates() - 1) / 2);
		}
	}

	/** @return income for a state transition under specified chargePrices */
	private double calcIncomeTransition(int stateDelta, double[] chargePrices, StepPower stepPower) {
		int priceArrayIndex = (stateManager.getNumberOfPowerStates() - 1) / 2 + stateDelta;
		double energyDelta = stepPower.getPower(stateDelta);
		double price = chargePrices[priceArrayIndex];
		double chargeLeviesAndTaxes = energyDelta > 0 ? energyDelta * purchaseLeviesAndTaxesInEURperMWH : 0;
		return -energyDelta * price - chargeLeviesAndTaxes;
	}

	/** @return income of best strategy until given state */
	private double getBestIncome(int hour, LoadShiftState state) {
		return hour < forecastSteps ? incomeSum[hour][state.shiftTime][state.energyState] : 0;
	}

	/** Update the schedule arrays by calculating energy, energy delta and retrieving shift times as well as setting prices based on
	 * the energy state delta. */
	private void updateScheduleArrays(TimePeriod startTime, double currentEnergyShiftStorageLevelInMWH,
			int currentShiftTime) {
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
				TimePeriod timePeriod = startTime.shiftByDuration(period);
				priceScheduleInEURperMWH[period] = calcPriceInPeriod(timePeriod, energyStateDelta);
			}
			initialEnergyState = nextState.energyState;
			currentShiftTime = nextState.shiftTime;
		}
	}

	/** @return electricity price in the specified {@link TimePeriod} for the specified state transition */
	private double calcPriceInPeriod(TimePeriod timePeriod, int stateDelta) {
		return stateDelta > 0 ? Constants.SCARCITY_PRICE_IN_EUR_PER_MWH : Constants.MINIMAL_PRICE_IN_EUR_PER_MWH;
	}

	/** @return a {@link PriceSensitivity} item */
	@Override
	protected MeritOrderSensitivity createBlankSensitivity() {
		return new PriceSensitivity();
	}
}
