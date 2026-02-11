// SPDX-FileCopyrightText: 2024-2026 German Aerospace Center <amiris@dlr.de>
//
// SPDX-License-Identifier: Apache-2.0
package agents.loadShifting.strategists;

import java.util.ArrayList;
import java.util.HashMap;
import agents.loadShifting.LoadShiftingPortfolio;
import agents.loadShifting.strategists.LoadShiftStateManager.LoadShiftState;
import agents.markets.meritOrder.sensitivities.MeritOrderSensitivity;
import agents.markets.meritOrder.sensitivities.PriceSensitivity;
import agents.markets.meritOrder.sensitivities.StepPower;
import de.dlr.gitlab.fame.agent.input.ParameterData;
import de.dlr.gitlab.fame.agent.input.ParameterData.MissingDataException;
import de.dlr.gitlab.fame.time.TimePeriod;
import endUser.EndUserTariff;

/** Determines a scheduling strategy for a {@link LoadShiftingPortfolio} in order to maximise profits considering end user tariffs
 * 
 * @author Johannes Kochems, Christoph Schimeczek */
public class ShiftProfitMaximiserTariffs extends LoadShiftingStrategist {
	private final int maximumShiftTime;
	private final LoadShiftStateManager stateManager;
	private final EndUserTariff tariff;

	/** incomeSum[t][d][i]: summed marginal cost to period t being in internal energy state i for a duration of d */
	private final double[][][] incomeSum;
	/** bestNextState[t][d][i]: best next internal load shift state, when current energy state is i for a duration d in period t */
	private final LoadShiftState[][][] bestNextState;

	/** Instantiate {@link ShiftProfitMaximiserTariffs}
	 * 
	 * @param generalInput parameters associated with strategists in general
	 * @param specificInput for {@link ShiftProfitMaximiserTariffs}
	 * @param endUserTariff determines prices for end users of electricity
	 * @param loadShiftingPortfolio for which schedules are to be created
	 * @throws MissingDataException if any required input is missing */
	public ShiftProfitMaximiserTariffs(ParameterData generalInput, ParameterData specificInput,
			EndUserTariff endUserTariff, LoadShiftingPortfolio loadShiftingPortfolio) throws MissingDataException {
		super(generalInput, specificInput, loadShiftingPortfolio);
		stateManager = new LoadShiftStateManager(loadShiftingPortfolio);
		maximumShiftTime = loadShiftingPortfolio.getMaximumShiftTimeInHours();
		incomeSum = new double[forecastSteps][maximumShiftTime][stateManager.getNumberOfEnergyStates()];
		bestNextState = new LoadShiftState[forecastSteps][maximumShiftTime][stateManager
				.getNumberOfEnergyStates()];
		this.tariff = endUserTariff;
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
			double[] consumerPrices = calcConsumerPriceAdditions(timePeriod, chargePrices);
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
						double incomeTransition = calcIncomeTransition(nextTimePeriod, powerStateDelta, chargePrices,
								consumerPrices, stepPower);
						income = incomeTransition + getBestIncome(nextPeriod, finalState) - variableShiftCosts
								- nextFeasibleStates.get(finalState);
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
			double[] chargePrices = sensitivity.getValuesInSteps(stateManager.getZeroPowerStateIndex());
			chargePrices[stateManager.getZeroPowerStateIndex()] = sensitivity.getPriceWithoutCharging();
			return chargePrices;
		} else {
			return new double[stateManager.getNumberOfPowerStates()];
		}
	}

	/** @return consumer price additions (price excluding spot market power price) in the specified {@link TimePeriod} */
	private double[] calcConsumerPriceAdditions(TimePeriod timePeriod, double[] chargePrices) {
		double[] consumerPriceAdditions = new double[chargePrices.length];
		for (int i = 0; i < chargePrices.length; i++) {
			consumerPriceAdditions[i] = tariff.calcSalePriceExcludingPowerPriceInEURPerMWH(chargePrices[i],
					timePeriod.getStartTime());
		}
		return consumerPriceAdditions;
	}

	/** @return power steps for charging & discharging in the specified {@link TimePeriod} */
	private StepPower calcStepPower(TimePeriod timePeriod) {
		final PriceSensitivity sensitivity = (PriceSensitivity) getSensitivityForPeriod(timePeriod);
		if (sensitivity != null) {
			return sensitivity.getStepPowers(stateManager.getZeroPowerStateIndex());
		} else {
			return new StepPower(0, 0, stateManager.getZeroPowerStateIndex());
		}
	}

	/** @return income for a state transition under specified chargePrices. Note: Not having to pay consumer prices is interpreted
	 *         as opportunity revenues (saved costs) */
	private double calcIncomeTransition(TimePeriod timePeriod, int stateDelta, double[] chargePrices,
			double[] consumerPriceAdditions, StepPower stepPower) {
		int priceArrayIndex = stateManager.getZeroPowerStateIndex() + stateDelta;
		double energyDelta = stepPower.getPower(stateDelta);
		double baselineLoadInMW = portfolio.getBaselineLoadSeries().getValueEarlierEqual(timePeriod.getStartTime())
				* portfolio.getBaselinePeakLoad();
		double powerPrice = chargePrices[priceArrayIndex];
		double consumerPriceAddition = consumerPriceAdditions[priceArrayIndex];
		if (tariff.isStaticPowerPrice()) {
			powerPrice = tariff.getStaticPowerPrice(timePeriod.getStartTime());
		}
		double increaseInCapacityRelatedPayment = calcIncreaseInCapacityRelatedPayment(timePeriod, energyDelta);
		return (-energyDelta - baselineLoadInMW) * (powerPrice + consumerPriceAddition) - increaseInCapacityRelatedPayment;
	}

	/** Calculate increase in capacity-induced payment obligations due to load shifts. Additional payments occur, if new load peak
	 * results from shifting */
	private double calcIncreaseInCapacityRelatedPayment(TimePeriod timePeriod, double energyDelta) {
		double baselinePeakLoad = portfolio.getBaselinePeakLoad();
		double baselineLoad = portfolio.getBaselineLoadSeries().getValueEarlierEqual(timePeriod.getStartTime())
				* baselinePeakLoad;
		double newLoad = baselineLoad + energyDelta;
		if (newLoad <= baselinePeakLoad) {
			return 0;
		} else {
			return (newLoad - baselinePeakLoad) * tariff.calcCapacityRelatedPriceInEURPerMW(timePeriod.getStartTime());
		}
	}

	/** @return income of best strategy until given state */
	private double getBestIncome(int hour, LoadShiftState state) {
		return hour < forecastSteps ? incomeSum[hour][state.shiftTime][state.energyState] : 0;
	}

	/** Update the schedule arrays by calculating energy, energy delta and retrieving shift times as well as setting prices based on
	 * the energy state delta. */
	private void updateScheduleArrays(TimePeriod startTime, double currentEnergyShiftStorageLevelInMWH,
			int currentShiftTime) {
		double energyPerState = portfolio.getEnergyResolutionInMWH();
		int initialEnergyIndex = (int) Math.round(currentEnergyShiftStorageLevelInMWH / energyPerState)
				+ stateManager.getZeroEnergyStateIndex();
		for (int period = 0; period < scheduleDurationPeriods; period++) {
			scheduledInitialEnergyInMWH[period] = energyPerState * absoluteToRelativeEnergyLevelIndex(initialEnergyIndex);
			LoadShiftState nextState = bestNextState[period][currentShiftTime][initialEnergyIndex];
			int energyStateDelta = nextState.energyState - initialEnergyIndex;
			demandScheduleInMWH[period] = energyStateDelta * energyPerState;
			if (energyStateDelta == 0) {
				priceScheduleInEURperMWH[period] = Double.NaN;
			} else {
				TimePeriod timePeriod = startTime.shiftByDuration(period);
				priceScheduleInEURperMWH[period] = calcPriceInPeriod(timePeriod, energyStateDelta);
			}
			initialEnergyIndex = nextState.energyState;
			currentShiftTime = nextState.shiftTime;
		}
	}

	private int absoluteToRelativeEnergyLevelIndex(int absoluteIndex) {
		return absoluteIndex - stateManager.getZeroEnergyStateIndex();
	}

	/** @return electricity price in the specified {@link TimePeriod} for the specified state transition */
	private double calcPriceInPeriod(TimePeriod timePeriod, int stateDelta) {
		double[] chargePrices = calcBidPrices(timePeriod);
		int priceArrayIndex = stateManager.getZeroPowerStateIndex() + stateDelta;
		return chargePrices[priceArrayIndex];
	}

	private double[] calcBidPrices(TimePeriod timePeriod) {
		final PriceSensitivity sensitivity = (PriceSensitivity) getSensitivityForPeriod(timePeriod);
		if (sensitivity != null) {
			return sensitivity.getValuesInSteps(stateManager.getZeroPowerStateIndex());
		} else {
			return new double[stateManager.getNumberOfPowerStates()];
		}
	}

	/** @return a {@link PriceSensitivity} item */
	@Override
	protected MeritOrderSensitivity createBlankSensitivity() {
		return new PriceSensitivity();
	}
}
