// SPDX-FileCopyrightText: 2021-2026 German Aerospace Center <amiris@dlr.de>
//
// SPDX-License-Identifier: Apache-2.0
package agents.storage.arbitrageStrategists;

import agents.markets.meritOrder.Constants;
import agents.markets.meritOrder.sensitivities.MeritOrderSensitivity;
import agents.markets.meritOrder.sensitivities.PriceNoSensitivity;
import agents.markets.meritOrder.sensitivities.StepPower;
import agents.storage.Device;
import de.dlr.gitlab.fame.agent.input.ParameterData;
import de.dlr.gitlab.fame.agent.input.ParameterData.MissingDataException;
import de.dlr.gitlab.fame.time.TimePeriod;

/** Strategy to maximise profits via dynamic programming, running backwards in time. Profits are maximised by finding the best
 * sequence of states. In contrast to ProfitMaximiser, the optimisation does not account for potential impact on prices, but can
 * be considered as a "price taker".
 * 
 * @author Felix Nitsch, Christoph Schimeczek */
public class ProfitMaximiserPriceTaker extends DynamicProgrammingStrategist {
	/** incomeSum[t][i]: income that can be collected in time step t being in internal state i */
	private final double[][] incomeSum;

	/** Creates a {@link ProfitMaximiserPriceTaker}
	 * 
	 * @param generalInput general parameters associated with strategists
	 * @param specificInput specific parameters for this strategist
	 * @param storage device to be optimised
	 * @throws MissingDataException if any required input is missing */
	public ProfitMaximiserPriceTaker(ParameterData generalInput, ParameterData specificInput, Device storage)
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
		double[] powerDeltasInMW = calcPowerSteps();
		for (int k = 0; k < forecastSteps; k++) {
			int period = forecastSteps - k - 1; // step backwards in time
			int nextPeriod = period + 1;
			TimePeriod timePeriod = firstPeriod.shiftByDuration(period);
			double chargePrice = calcChargePrice(timePeriod);
			for (int initialState = 0; initialState < numberOfEnergyStates; initialState++) {
				double currentBestIncome = -Double.MAX_VALUE;
				int bestFinalState = Integer.MIN_VALUE;

				int firstFinalState = calcFinalStateLowerBound(initialState);
				int lastFinalState = calcFinalStateUpperBound(initialState);
				for (int finalState = firstFinalState; finalState <= lastFinalState; finalState++) {
					int stateDelta = finalState - initialState;
					double incomeTransition = calcIncomeTransition(stateDelta, chargePrice, powerDeltasInMW);
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

	/** @return power steps from max discharging to max charging */
	private double[] calcPowerSteps() {
		StepPower stepPower = new StepPower(storage.getExternalChargingPowerInMW(),
				storage.getExternalDischargingPowerInMW(), numberOfTransitionStates);
		double[] externalPowerStepsInMW = new double[numberOfTransitionStates * 2 + 1];
		for (int i = -numberOfTransitionStates; i <= numberOfTransitionStates; i++) {
			int index = i + numberOfTransitionStates;
			externalPowerStepsInMW[index] = stepPower.getPower(i);
		}
		return externalPowerStepsInMW;
	}

	/** @return price for (dis-)charging in the specified {@link TimePeriod}; assumes price of Zero if no valid forecast exists */
	private double calcChargePrice(TimePeriod timePeriod) {
		final PriceNoSensitivity sensitivity = (PriceNoSensitivity) getSensitivityForPeriod(timePeriod);
		if (sensitivity != null) {
			double priceForecast = sensitivity.getPriceForecast();
			return Double.isNaN(priceForecast) ? 0 : priceForecast;
		} else {
			return 0;
		}
	}

	/** @return income of best strategy starting in given period at given state */
	private double getBestIncome(int period, int state) {
		return period < forecastSteps ? incomeSum[period][state] : 0;
	}

	/** @return income for a state transition under specified chargePrice */
	private double calcIncomeTransition(int stateDelta, double chargePrice, double[] powerDeltasInMW) {
		int arrayIndex = numberOfTransitionStates + stateDelta;
		double externalEnergyDelta = powerDeltasInMW[arrayIndex];
		return -externalEnergyDelta * chargePrice;
	}

	@Override
	protected double calcBidPrice(TimePeriod timePeriod, double externalEnergyDelta) {
		if (externalEnergyDelta == 0) {
			return Double.NaN;
		} else if (externalEnergyDelta < 0) {
			return Constants.MINIMAL_PRICE_IN_EUR_PER_MWH;
		} else {
			return Constants.SCARCITY_PRICE_IN_EUR_PER_MWH;
		}
	}

	@Override
	protected MeritOrderSensitivity createBlankSensitivity() {
		return new PriceNoSensitivity();
	}
}
