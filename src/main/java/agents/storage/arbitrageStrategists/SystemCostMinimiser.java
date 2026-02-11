// SPDX-FileCopyrightText: 2021-2026 German Aerospace Center <amiris@dlr.de>
//
// SPDX-License-Identifier: Apache-2.0
package agents.storage.arbitrageStrategists;

import agents.markets.meritOrder.sensitivities.MarginalCostSensitivity;
import agents.markets.meritOrder.sensitivities.MeritOrderSensitivity;
import agents.storage.Device;
import de.dlr.gitlab.fame.agent.input.ParameterData;
import de.dlr.gitlab.fame.agent.input.ParameterData.MissingDataException;
import de.dlr.gitlab.fame.time.TimePeriod;

/** Strategy to minimise system cost (i.e. supply-side marginals) via dynamic programming, running backwards in time. System costs
 * are minimised by finding the best sequence of states.
 * 
 * @author Christoph Schimeczek, Felix Nitsch, A. Achraf El Ghazi, Evelyn Sperber */
public class SystemCostMinimiser extends DynamicProgrammingStrategist {
	/** costSum[t][i]: summed marginal cost of the best sequence of states starting in period t and internal state i */
	private final double[][] followUpCostSum;

	/** Creates a {@link SystemCostMinimiser}
	 * 
	 * @param generalInput general parameters associated with strategists
	 * @param specificInput specific parameters for this strategist
	 * @param storage device to be optimised
	 * @throws MissingDataException if any required input is missing */
	public SystemCostMinimiser(ParameterData generalInput, ParameterData specificInput, Device storage)
			throws MissingDataException {
		super(generalInput, specificInput, storage);
		followUpCostSum = new double[forecastSteps][numberOfEnergyStates];
	}

	@Override
	protected void clearPlanningArrays() {
		for (int t = 0; t < forecastSteps; t++) {
			for (int initialState = 0; initialState < numberOfEnergyStates; initialState++) {
				followUpCostSum[t][initialState] = 0.0;
				bestNextState[t][initialState] = Integer.MIN_VALUE;
			}
		}
	}

	/** calculates least costly and reachable final state for every initial state in every period */
	@Override
	protected void optimiseDispatch(TimePeriod startPeriod) {
		for (int k = 0; k < forecastSteps; k++) {
			int step = forecastSteps - k - 1; // step backwards in time
			int nextStep = step + 1;
			TimePeriod timePeriod = startPeriod.shiftByDuration(step);
			double[] marginalCostDeltaPerStep = calcCostSteps(timePeriod);

			for (int initialState = 0; initialState < numberOfEnergyStates; initialState++) {
				double currentLowestCost = Double.MAX_VALUE;
				int bestFinalState = Integer.MIN_VALUE;
				int firstFinalState = calcFinalStateLowerBound(initialState);
				int lastFinalState = calcFinalStateUpperBound(initialState);
				for (int finalState = firstFinalState; finalState <= lastFinalState; finalState++) {
					int stateDelta = finalState - initialState;
					int costStepIndex = numberOfTransitionStates + stateDelta;
					double cost = marginalCostDeltaPerStep[costStepIndex] + getFollowUpCost(nextStep, finalState);
					if (cost < currentLowestCost) {
						currentLowestCost = cost;
						bestFinalState = finalState;
					}
				}
				if (bestFinalState == Integer.MIN_VALUE) {
					throw new RuntimeException("No valid storage strategy found!");
				}
				followUpCostSum[step][initialState] = currentLowestCost;
				bestNextState[step][initialState] = bestFinalState;
			}
		}
	}

	/** @return marginal cost delta for each charging or discharging option, i.e. 2 * transitionStates + 1 options */
	private double[] calcCostSteps(TimePeriod timePeriod) {
		MarginalCostSensitivity sensitivity = (MarginalCostSensitivity) getSensitivityForPeriod(timePeriod);
		if (sensitivity != null) {
			return sensitivity.getValuesInSteps(numberOfTransitionStates);
		} else {
			return new double[2 * numberOfTransitionStates + 1];
		}
	}

	/** @return lowest revenues of best strategy starting in given hour at given state */
	private double getFollowUpCost(int hour, int state) {
		return hour < forecastSteps ? followUpCostSum[hour][state] : 0;
	}

	@Override
	protected double calcBidPrice(TimePeriod __, double externalEnergyDelta) {
		if (externalEnergyDelta == 0) {
			return Double.NaN;
		} else {
			return externalEnergyDelta > 0 ? Double.MAX_VALUE : -Double.MAX_VALUE;
		}
	}

	/** @return a blank MarginalCostSensitivity item */
	@Override
	protected MeritOrderSensitivity createBlankSensitivity() {
		return new MarginalCostSensitivity();
	}

	@Override
	public void storeElectricityPriceForecast(TimePeriod timePeriod, double electricityPriceForecastInEURperMWH) {
		throw new RuntimeException(ERR_USE_PRICE_FORECAST + getClass().getSimpleName());
	}
}