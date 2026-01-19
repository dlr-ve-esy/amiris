// SPDX-FileCopyrightText: 2026 German Aerospace Center <amiris@dlr.de>
//
// SPDX-License-Identifier: Apache-2.0
package agents.heatPump.strategists;

import java.util.TreeMap;

import agents.heatPump.HeatPump;
import agents.heatPump.HeatingInputData;
import agents.heatPump.StrategyParameters;
import agents.markets.meritOrder.sensitivities.MeritOrderSensitivity;
import agents.markets.meritOrder.sensitivities.PriceSensitivity;
import agents.storage.Device;
import de.dlr.gitlab.fame.agent.input.ParameterData;
import de.dlr.gitlab.fame.agent.input.ParameterData.MissingDataException;
import de.dlr.gitlab.fame.data.TimeSeries;
import de.dlr.gitlab.fame.time.TimePeriod;
import de.dlr.gitlab.fame.time.TimeStamp;

/** Minimises costs for heat pump dispatch using active storage (hot water tank) for a given heat demand file
 * 
 * @author Evelyn Sperber, Farzad Sarfarazi */
public class StrategistMinCostFile extends HeatPumpStrategist {
	private final int numberOfEnergyStates;
	private final int numberOfTransitionStates;
	/** costSum[t][i]: sum of least cost up until hour t to reach state i */
	private final double[][] costSum;
	/** bestNextState[t][i]: best next internal state identified when current state is i in hour t */
	private final int[][] bestNextState;
	private final double[] storageLoadDelta;
	private double internalPowerPerStep;
	private final double heatPumpPenetrationFactor;
	private final double purchaseLeviesAndTaxesInEURperMWH;
	private TimeSeries totalHeatDemand;
	private TreeMap<TimePeriod, Double> heatPumpHeatDemandInMW = new TreeMap<>();

	/** Creates a {@link StrategistMinCostFile}
	 * 
	 * @param basicStrategy basic input data related to the strategist
	 * @param heatPump specifies the heat pump to be dispatched
	 * @param device the storage used for heat pump dispatch optimisation
	 * @param heatingData input regarding heat-related input time series
	 * @param heatPumpPenetrationFactor share of heat pumps of total heating demand
	 * @param installedUnits number of installed heat pump units
	 * @param strategyParams strategy parameters for heat pump operation
	 * @throws MissingDataException if any required data is not provided */
	public StrategistMinCostFile(ParameterData basicStrategy, HeatPump heatPump, Device device,
			HeatingInputData heatingData,
			double heatPumpPenetrationFactor, TimeSeries installedUnits, StrategyParameters strategyParams)
			throws MissingDataException {
		super(basicStrategy, heatPump, heatingData, device, installedUnits, strategyParams);
		this.numberOfTransitionStates = strategyParams.getChargingSteps();
		this.numberOfEnergyStates = (int) Math.ceil(numberOfTransitionStates * thermalStorage.getEnergyToPowerRatio()) + 1;
		costSum = new double[forecastSteps][numberOfEnergyStates];
		bestNextState = new int[forecastSteps][numberOfEnergyStates];
		storageLoadDelta = new double[2 * numberOfTransitionStates + 1];
		this.heatPumpPenetrationFactor = heatPumpPenetrationFactor;
		this.totalHeatDemand = heatingData.getHeatDemandProfile();
		this.purchaseLeviesAndTaxesInEURperMWH = 0; // TODO: move to config or link to policy. add VAT
	}

	@Override
	protected void updateSchedule(TimePeriod startTime) {
		updateHeatDemandForecast(startTime);
		internalPowerPerStep = thermalStorage.getInternalPowerInMW() / numberOfTransitionStates;
		clearPlanningArrays();
		optimiseDispatch(startTime);
		updateScheduleArrays(startTime, thermalStorage.getCurrentEnergyInStorageInMWH());
	}

	/** Updates heat demand forecast
	 * 
	 * @param startTime to begin forecast updating at */
	public void updateHeatDemandForecast(TimePeriod startTime) {
		for (int t = 0; t < forecastSteps; t++) {
			TimeStamp forecastTime = startTime.shiftByDuration(t).getStartTime();
			double currentHeatPumpHeatDemandInMW = totalHeatDemand.getValueLinear(forecastTime) * heatPumpPenetrationFactor;
			heatPumpHeatDemandInMW.put(startTime.shiftByDuration(t), currentHeatPumpHeatDemandInMW);
		}
	}

	/** Replaces all entries in the planning arrays with MAX_VALUE */
	private void clearPlanningArrays() {
		for (int t = 0; t < forecastSteps; t++) {
			for (int initialState = 0; initialState < numberOfEnergyStates; initialState++) {
				costSum[t][initialState] = Double.MAX_VALUE;
				bestNextState[t][initialState] = Integer.MAX_VALUE;
			}
		}
	}

	/** Update most profitable final state for each possible initial state in every hour */
	private void optimiseDispatch(TimePeriod startTime) {
		for (int k = 0; k < forecastSteps; k++) {
			int period = forecastSteps - k - 1; // step backwards in time
			int nextPeriod = period + 1;
			TimePeriod timeSegment = startTime.shiftByDuration(period);
			updateSystemEnergyDeltas(timeSegment);
			PriceSensitivity sensitivity = (PriceSensitivity) getSensitivityForPeriod(timeSegment);

			for (int initialState = 0; initialState < numberOfEnergyStates; initialState++) {
				double currentLowestCost = Double.MAX_VALUE;
				int bestFinalState = Integer.MIN_VALUE;
				int firstFinalState = calcFinalStateLowerBound(initialState, timeSegment);
				int lastFinalState = calcFinalStateUpperBound(initialState);
				for (int finalState = firstFinalState; finalState <= lastFinalState; finalState++) {
					int stateDelta = finalState - initialState;
					double costTransition = calcCostForStateTransition(stateDelta, sensitivity, timeSegment);
					double cost = costTransition + getLowestCost(nextPeriod, finalState);
					if (cost < currentLowestCost) {
						currentLowestCost = cost;
						bestFinalState = finalState;
					}
				}
				if (bestFinalState == Integer.MIN_VALUE) {
					throw new RuntimeException("No valid heat pump dispatch found!");
				}
				costSum[period][initialState] = currentLowestCost;
				bestNextState[period][initialState] = bestFinalState;
			}
		}
	}

	/** Writes storage load deltas to corresponding arrays */
	private void updateSystemEnergyDeltas(TimePeriod timeSegment) {
		double heatDemand = heatPumpHeatDemandInMW.get(timeSegment);
		for (int step = -numberOfTransitionStates; step <= numberOfTransitionStates; step++) {
			double heatToStorage = thermalStorage.internalToExternalEnergy(internalPowerPerStep * step);
			storageLoadDelta[step + numberOfTransitionStates] = heatDemand + heatToStorage;
		}
	}

	/** @return lower bound (inclusive) of discrete states reachable from specified initialState */
	private int calcFinalStateLowerBound(int initialState, TimePeriod timeSegment) {
		double heatDemand = heatPumpHeatDemandInMW.get(timeSegment);
		double internalEnergyPerState = thermalStorage.getInternalPowerInMW() / numberOfTransitionStates;
		double internalHeatDemand = thermalStorage.externalToInternalEnergy(heatDemand);
		int heatDemandSteps = (int) Math.round(internalHeatDemand / internalEnergyPerState);
		return Math.max(0, initialState - heatDemandSteps);
	}

	/** @return upper bound (inclusive) of discrete states reachable from specified initialState */
	private int calcFinalStateUpperBound(int initialState) {
		return Math.min(numberOfEnergyStates - 1, initialState + numberOfTransitionStates);
	}

	/** @return cost for a state transition under specified chargePrices */
	private double calcCostForStateTransition(int stateDelta, PriceSensitivity sensitivity, TimePeriod startTime) {
		int priceArrayIndex = numberOfTransitionStates + stateDelta;
		double externalHeatDelta = storageLoadDelta[priceArrayIndex];
		double externalPowerDelta = calcPowerDemand(externalHeatDelta, getAmbientTemperatureInC(startTime));
		double price = sensitivity.calcPriceForExternalEnergyDelta(externalPowerDelta);
		double chargeLeviesAndTaxes = externalPowerDelta > 0 ? externalPowerDelta * purchaseLeviesAndTaxesInEURperMWH : 0;
		return externalPowerDelta * price + chargeLeviesAndTaxes;
	}

	/** @return lowest follow-up costs starting in given hour at given state */
	private double getLowestCost(int period, int state) {
		return period < forecastSteps ? costSum[period][state] : 0;
	}

	/** Writes energy demand to corresponding arrays. */
	private void updateScheduleArrays(TimePeriod startTime, double currentEnergyInStorageInMWh) {
		double internalEnergyPerState = thermalStorage.getInternalPowerInMW() / numberOfTransitionStates;
		double ambientTemperatureInC = getAmbientTemperatureInC(startTime);
		int initialState = (int) Math.round(currentEnergyInStorageInMWh / internalEnergyPerState);
		for (int period = 0; period < scheduleDurationPeriods; period++) {
			int nextState = bestNextState[period][initialState];
			int stateDelta = nextState - initialState;
			double externalHeatDelta = thermalStorage.internalToExternalEnergy(stateDelta * internalEnergyPerState);
			demandScheduleInMWH[period] = calcPowerDemand(externalHeatDelta, ambientTemperatureInC);
		}
	}

	/** Calculates power demand from given heat file according to the current COP */
	private double calcPowerDemand(double heatDemandInMW, double ambientTemperaturInC) {
		double COP = heatPump.calcCoefficientOfPerformance(ambientTemperaturInC);
		return heatDemandInMW / COP;
	}

	@Override
	protected MeritOrderSensitivity createBlankSensitivity() {
		return new PriceSensitivity();
	}

	@Override
	public double getHeatLoad(TimePeriod timeSegment) {
		return totalHeatDemand.getValueLinear(timeSegment.getStartTime()) * heatPumpPenetrationFactor;
	}
}
