// SPDX-FileCopyrightText: 2021-2026 German Aerospace Center <amiris@dlr.de>
//
// SPDX-License-Identifier: Apache-2.0
package agents.storage.arbitrageStrategists;

import agents.markets.meritOrder.sensitivities.MeritOrderSensitivity;
import agents.markets.meritOrder.sensitivities.PriceSensitivity;
import agents.markets.meritOrder.sensitivities.StepPower;
import agents.storage.Device;
import de.dlr.gitlab.fame.agent.input.ParameterData;
import de.dlr.gitlab.fame.agent.input.ParameterData.MissingDataException;
import de.dlr.gitlab.fame.time.TimePeriod;

/** Strategy to maximise profits via dynamic programming, running backwards in time. Profits are maximised by finding the best
 * sequence of states. The result fully utilises market power to improve profits.
 * 
 * @author Christoph Schimeczek, Evelyn Sperber */
public class ProfitMaximiser extends DynamicProgrammingStrategist {
	/** incomeSum[t][i]: income that can be collected in time step t being in internal state i */
	private final double[][] incomeSum;

	/** Creates a {@link ProfitMaximiser}
	 * 
	 * @param generalInput general parameters associated with strategists
	 * @param specificInput specific parameters for this strategist
	 * @param storage device to be optimised
	 * @throws MissingDataException if any required input is missing */
	public ProfitMaximiser(ParameterData generalInput, ParameterData specificInput, Device storage)
			throws MissingDataException {
		super(generalInput, specificInput, storage);
		incomeSum = new double[forecastSteps][numberOfEnergyStates];
	}

	@Override
	protected void clearPlanningArrays() {
		for (int t = 0; t < forecastSteps; t++) {
			for (int initialState = 0; initialState < numberOfEnergyStates; initialState++) {
				incomeSum[t][initialState] = 0.0;
				bestNextState[t][initialState] = Integer.MIN_VALUE;
			}
		}
	}

	/** update most profitable final state for each possible initial state in every period */
	@Override
	protected void optimiseDispatch(TimePeriod firstPeriod) {
		for (int k = 0; k < forecastSteps; k++) {
			int period = forecastSteps - k - 1; // step backwards in time
			int nextPeriod = period + 1;
			TimePeriod timePeriod = firstPeriod.shiftByDuration(period);
			double[] chargePrices = calcChargePrices(timePeriod);
			double[] powerDeltasInMW = calcPowerDeltas(timePeriod);

			for (int initialState = 0; initialState < numberOfEnergyStates; initialState++) {
				double currentBestIncome = -Double.MAX_VALUE;
				int bestFinalState = Integer.MIN_VALUE;
				int firstFinalState = calcFinalStateLowerBound(initialState);
				int lastFinalState = calcFinalStateUpperBound(initialState);
				for (int finalState = firstFinalState; finalState <= lastFinalState; finalState++) {
					int stateDelta = finalState - initialState;
					double incomeTransition = calcIncomeTransition(stateDelta, chargePrices, powerDeltasInMW);
					double income = incomeTransition + getBestIncome(nextPeriod, finalState);
					if (income > currentBestIncome) {
						currentBestIncome = income;
						bestFinalState = finalState;
					}
				}
				if (bestFinalState == Integer.MIN_VALUE) {
					throw new RuntimeException("No valid storage strategy found!");
				}
				incomeSum[period][initialState] = currentBestIncome;
				bestNextState[period][initialState] = bestFinalState;
			}
		}
	}

	/** @return price steps for charging & discharging in the specified {@link TimePeriod} */
	private double[] calcChargePrices(TimePeriod timePeriod) {
		final PriceSensitivity sensitivity = (PriceSensitivity) getSensitivityForPeriod(timePeriod);
		if (sensitivity != null) {
			return sensitivity.getValuesInSteps(numberOfTransitionStates);
		} else {
			return new double[2 * numberOfTransitionStates + 1];
		}
	}

	/** @return powers from max discharging to max charging in the specified {@link TimePeriod} */
	private double[] calcPowerDeltas(TimePeriod timePeriod) {
		double[] powerDeltasInMW = new double[numberOfTransitionStates * 2 + 1];
		StepPower stepPower = calcStepPower(timePeriod);
		for (int i = -numberOfTransitionStates; i <= numberOfTransitionStates; i++) {
			int index = i + numberOfTransitionStates;
			powerDeltasInMW[index] = stepPower.getPower(i);
		}
		return powerDeltasInMW;
	}

	/** @return power steps for charging & discharging in the specified {@link TimePeriod} */
	private StepPower calcStepPower(TimePeriod timePeriod) {
		final PriceSensitivity sensitivity = (PriceSensitivity) getSensitivityForPeriod(timePeriod);
		if (sensitivity != null) {
			return sensitivity.getStepPowers(numberOfTransitionStates);
		} else {
			return new StepPower(0, 0, numberOfTransitionStates);
		}
	}

	/** @return income of best strategy starting in given period at given state */
	private double getBestIncome(int period, int state) {
		return period < forecastSteps ? incomeSum[period][state] : 0;
	}

	/** @return income for a state transition under specified chargePrices */
	private double calcIncomeTransition(int stateDelta, double[] chargePrices, double[] powerDeltasInMW) {
		int arrayIndex = numberOfTransitionStates + stateDelta;
		double externalEnergyDelta = powerDeltasInMW[arrayIndex];
		double price = chargePrices[arrayIndex];
		return -externalEnergyDelta * price;
	}

	@Override
	protected double calcBidPrice(TimePeriod timePeriod, double externalEnergyDelta) {
		if (externalEnergyDelta == 0) {
			return Double.NaN;
		} else {
			final PriceSensitivity sensitivity = (PriceSensitivity) getSensitivityForPeriod(timePeriod);
			return sensitivity.calcPriceForExternalEnergyDelta(externalEnergyDelta);
		}
	}

	@Override
	protected MeritOrderSensitivity createBlankSensitivity() {
		return new PriceSensitivity();
	}

	@Override
	public void storeElectricityPriceForecast(TimePeriod timePeriod, double electricityPriceForecastInEURperMWH) {
		throw new RuntimeException(ERR_USE_PRICE_FORECAST + getClass().getSimpleName());
	}
}
