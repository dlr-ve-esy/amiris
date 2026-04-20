// SPDX-FileCopyrightText: 2024-2026 German Aerospace Center <amiris@dlr.de>
//
// SPDX-License-Identifier: Apache-2.0
package agents.heatPump.strategists;

import agents.heatPump.HeatPump;
import agents.heatPump.HeatPumpSchedule;
import agents.heatPump.HeatingInputData;
import agents.heatPump.StrategyParameters;
import agents.heatPump.ThermalResponse;
import agents.markets.meritOrder.Constants;
import agents.markets.meritOrder.sensitivities.MeritOrderSensitivity;
import agents.markets.meritOrder.sensitivities.PriceSensitivity;
import agents.storage.Device;
import de.dlr.gitlab.fame.agent.input.ParameterData;
import de.dlr.gitlab.fame.agent.input.ParameterData.MissingDataException;
import de.dlr.gitlab.fame.data.TimeSeries;
import de.dlr.gitlab.fame.time.TimePeriod;

/** Minimises costs for heat pump dispatch by means of passive thermal storage using the thermal response model
 * 
 * @author Christoph Schimeczek, Evelyn Sperber */
public class StrategistMinCostRC extends HeatPumpStrategist {
	/** costsInStep[t][i][j]: costs in step t, if temperature is changed from i to j */
	private final double[][][] costsInStep;
	/** costSum[t][i]: sum of least cost up until hour t to reach state i */
	private final double[][] costSum;
	/** bestNextState[t][i] : optimal next state if the current state in hour t is state i */
	private final int[][] bestNextState;
	private final int numberOfTemperatureSteps;

	/** sum of all levies on retail electricity price (EEG-Umlage etc.) (set to 0 to ignore). */
	private static final double LEVIES = 0.0;
	/** value added tax */
	private static final double VAT = 0.0;

	private ThermalResponse building;

	/** Creates a {@link StrategistMinCostRC}
	 * 
	 * @param basicStrategy basic input data related to the strategist
	 * @param building specifies the building in which the heat pump is operated
	 * @param heatPump specifies the heat pump to be dispatched
	 * @param device the storage used for heat pump dispatch optimisation
	 * @param heatingData input regarding heat-related input time series
	 * @param installedUnits number of installed heat pump units
	 * @param strategyParams strategy parameters for heat pump operation
	 * @throws MissingDataException if any required data is not provided */
	public StrategistMinCostRC(ParameterData basicStrategy, ThermalResponse building, HeatPump heatPump, Device device,
			HeatingInputData heatingData, TimeSeries installedUnits, StrategyParameters strategyParams)
			throws MissingDataException {
		super(basicStrategy, heatPump, heatingData, device, installedUnits, strategyParams);
		this.building = building;
		numberOfTemperatureSteps = strategyParams.getChargingSteps() + 1;
		costsInStep = new double[forecastSteps][numberOfTemperatureSteps][numberOfTemperatureSteps];
		costSum = new double[forecastSteps][numberOfTemperatureSteps];
		bestNextState = new int[forecastSteps][numberOfTemperatureSteps];
	}

	@Override
	protected MeritOrderSensitivity createBlankSensitivity() {
		return new PriceSensitivity();
	}

	/** @return {@link HeatPumpSchedule schedule} for the specified simulation hour<br>
	 *         if foresight data is missing, a dummy strategy is returned that keeps temperature constant */
	@Override
	public HeatPumpSchedule createSchedule(TimePeriod timePeriod) {
		if (temperatureIsOutOfBounds()) {
			updateScheduleOutOfBounds(timePeriod);
			updateBidSchedule();
			HeatPumpSchedule schedule = new HeatPumpSchedule(timePeriod, 1, temperatureResolutionInC);
			schedule.setBidsScheduleInEURperMWH(new double[] {scheduledBidPricesInEURperMWH[0]});
			schedule.setRequestedEnergyPerPeriod(new double[] {demandScheduleInMWH[0]});
			schedule.setExpectedInitialInternalEnergyScheduleInMWH(new double[] {getInternalEnergySchedule()[0]});
			return schedule;
		}
		return super.createSchedule(timePeriod);
	}

	/** @return true if temperature is above or below allowed limits */
	protected boolean temperatureIsOutOfBounds() {
		double temperatureInC = building.getCurrentRoomTemperatureInC();
		return temperatureInC < strategyParams.getMinimalRoomTemperatureInC() * 0.99999
				|| temperatureInC > strategyParams.getMaximalRoomTemperatureInC();
	}

	/** Updates heat pump schedule to lead room temperature back to allowed bounds */
	private void updateScheduleOutOfBounds(TimePeriod timePeriod) {
		double temperatureInC = building.getCurrentRoomTemperatureInC();
		if (temperatureInC < strategyParams.getMinimalRoomTemperatureInC()) {
			double targetTemperatureInC = strategyParams.getMinimalRoomTemperatureInC();
			double ambientTemperatureInC = getAmbientTemperatureInC(timePeriod);
			double solarRadiationInkWperM2 = getSolarRadiationInkWperM2(timePeriod);
			double singleUnitPowerDemandInKW = building.calcHourlyPowerDemandSimpleInKW(temperatureInC,
					targetTemperatureInC, ambientTemperatureInC, solarRadiationInkWperM2);
			demandScheduleInMWH[0] = singleUnitPowerDemandInKW * getUpscalingFactorToAllUnitsInMWperKW(timePeriod);
			hourlyInitialTemperatureInC[0] = temperatureInC;
		} else if (temperatureInC > strategyParams.getMaximalRoomTemperatureInC()) {
			demandScheduleInMWH[0] = 0;
			hourlyInitialTemperatureInC[0] = temperatureInC;
		}
	}

	@Override
	protected void updateSchedule(TimePeriod timeSegment) {
		clearPlanningArrays();
		updateCostsForStateTransitions(timeSegment);
		optimiseDispatch();
		updateScheduleArrays(timeSegment);
	}

	/** Replaces all entries in the planning arrays with MAX_VALUE */
	private void clearPlanningArrays() {
		for (int t = 0; t < forecastSteps; t++) {
			for (int initialState = 0; initialState < numberOfTemperatureSteps; initialState++) {
				costSum[t][initialState] = Double.MAX_VALUE;
				bestNextState[t][initialState] = Integer.MAX_VALUE;
			}
		}
	}

	/** Calculates cost for each allowed temperature transition in every period */
	private void updateCostsForStateTransitions(TimePeriod timeSegment) {
		for (int periodInSchedule = 0; periodInSchedule < forecastSteps; periodInSchedule++) {
			TimePeriod planningTimeSegment = timeSegment.shiftByDuration(periodInSchedule);
			double upscalingFactor = getUpscalingFactorToAllUnitsInMWperKW(planningTimeSegment);
			double infeasibilityCost = heatPump.getMaxElectricalHeatPumpPowerInKW() * upscalingFactor
					* Constants.SCARCITY_PRICE_IN_EUR_PER_MWH * forecastSteps * 2;
			double ambientTemperatureInC = getAmbientTemperatureInC(planningTimeSegment);
			double solarRadiationInkWperM2 = getSolarRadiationInkWperM2(planningTimeSegment);
			PriceSensitivity sensitivity = (PriceSensitivity) getSensitivityForPeriod(planningTimeSegment);

			for (int initialTemperatureStep = 0; initialTemperatureStep < numberOfTemperatureSteps; initialTemperatureStep++) {
				double initialRoomTemperatureInC = calcTemperatureInCFromTemperatureStep(initialTemperatureStep);
				double[] minMaxTemperature = building.calcMinMaxReachableRoomTemperatureInC(initialRoomTemperatureInC,
						ambientTemperatureInC, solarRadiationInkWperM2);
				int firstFinalStep = calcTemperatureStepFromTemperatureInC(
						Math.max(strategyParams.getMinimalRoomTemperatureInC(), minMaxTemperature[0])); // minTemperature
				int lastFinalStep = calcTemperatureStepFromTemperatureInC(
						Math.min(strategyParams.getMaximalRoomTemperatureInC(), minMaxTemperature[1])); // maxTemperature

				for (int finalTemperatureStep = 0; finalTemperatureStep < numberOfTemperatureSteps; finalTemperatureStep++) {
					if (finalTemperatureStep >= firstFinalStep && finalTemperatureStep <= lastFinalStep) {
						double finalTemperatureInC = calcTemperatureInCFromTemperatureStep(finalTemperatureStep);
						double singleUnitPowerDemandInKW = building.calcHourlyPowerDemandSimpleInKW(
								initialRoomTemperatureInC, finalTemperatureInC, ambientTemperatureInC,
								solarRadiationInkWperM2);
						double totalPowerDemandInMW = singleUnitPowerDemandInKW * upscalingFactor;
						double electricityPriceInEURperMWH = (sensitivity
								.calcPriceForExternalEnergyDelta(totalPowerDemandInMW) + LEVIES) * (1 + VAT);
						costsInStep[periodInSchedule][initialTemperatureStep][finalTemperatureStep] = electricityPriceInEURperMWH
								* totalPowerDemandInMW;
					} else {
						costsInStep[periodInSchedule][initialTemperatureStep][finalTemperatureStep] = infeasibilityCost;
					}
				}
			}
		}
	}

	/** @return temperature discretisation step closest to specified temperature */
	private int calcTemperatureStepFromTemperatureInC(double temperatureInC) {
		return (int) Math
				.round(((temperatureInC - strategyParams.getMinimalRoomTemperatureInC()) / temperatureResolutionInC));
	}

	/** @return the room temperature corresponding to the given temperature discretisation step */
	private double calcTemperatureInCFromTemperatureStep(int temperatureStep) {
		return temperatureResolutionInC * temperatureStep + strategyParams.getMinimalRoomTemperatureInC();
	}

	/** Updates best next state for each period and initial temperature */
	private void optimiseDispatch() {
		for (int k = 0; k < forecastSteps; k++) {
			int period = forecastSteps - k - 1; // step backwards in time
			int nextPeriod = period + 1;
			for (int initialTemperatureStep = 0; initialTemperatureStep < numberOfTemperatureSteps; initialTemperatureStep++) {
				double currentLowestCost = Double.MAX_VALUE;
				int bestFinalTemperatureStep = Integer.MIN_VALUE;
				for (int finalTemperatureStep = 0; finalTemperatureStep < numberOfTemperatureSteps; finalTemperatureStep++) {
					double cost = costsInStep[period][initialTemperatureStep][finalTemperatureStep]
							+ getLowestCost(nextPeriod, finalTemperatureStep);
					if (cost < currentLowestCost) {
						currentLowestCost = cost;
						bestFinalTemperatureStep = finalTemperatureStep;
					}
				}
				costSum[period][initialTemperatureStep] = currentLowestCost;
				bestNextState[period][initialTemperatureStep] = bestFinalTemperatureStep;
			}
		}
	}

	/** @return lowest follow-up costs for given final temperature in specified hour */
	private double getLowestCost(int period, int finalTemperatureStep) {
		return period < forecastSteps ? costSum[period][finalTemperatureStep] : 0;
	}

	/** Writes energy demand and planned initial temperatures to corresponding arrays */
	private void updateScheduleArrays(TimePeriod timeSegment) {
		double initialRoomTemperatureInC = building.getCurrentRoomTemperatureInC();
		for (int periodInSchedule = 0; periodInSchedule < scheduleDurationPeriods; periodInSchedule++) {
			TimePeriod planningTimeSegment = timeSegment.shiftByDuration(periodInSchedule);
			double upscalingFactor = getUpscalingFactorToAllUnitsInMWperKW(planningTimeSegment);
			double ambientTemperatureInC = getAmbientTemperatureInC(planningTimeSegment);
			double solarRadiationInkWperM2 = getSolarRadiationInkWperM2(planningTimeSegment);

			double singleUnitPowerDemandInKW;
			if (initialRoomTemperatureInC < strategyParams.getMinimalRoomTemperatureInC()) {
				double targetTemperatureInC = strategyParams.getMinimalRoomTemperatureInC();
				singleUnitPowerDemandInKW = building.calcHourlyPowerDemandSimpleInKW(initialRoomTemperatureInC,
						targetTemperatureInC, ambientTemperatureInC, solarRadiationInkWperM2);
			} else if (initialRoomTemperatureInC > strategyParams.getMaximalRoomTemperatureInC()) {
				singleUnitPowerDemandInKW = 0;
			} else {
				double interpolatedFinalTemperatureInC = interpolateBestNextTemperature(periodInSchedule,
						initialRoomTemperatureInC);
				singleUnitPowerDemandInKW = building.calcHourlyPowerDemandSimpleInKW(initialRoomTemperatureInC,
						interpolatedFinalTemperatureInC, ambientTemperatureInC, solarRadiationInkWperM2);
			}
			demandScheduleInMWH[periodInSchedule] = singleUnitPowerDemandInKW * upscalingFactor;
			hourlyInitialTemperatureInC[periodInSchedule] = initialRoomTemperatureInC;
			initialRoomTemperatureInC = building.calcNextRoomTemperatureInC(initialRoomTemperatureInC,
					ambientTemperatureInC, solarRadiationInkWperM2, singleUnitPowerDemandInKW);
		}
	}

	/** @return interpolated best next temperature based on the two strategies of the next neighbouring discrete temperature
	 *         values */
	private double interpolateBestNextTemperature(int hourInSchedule, double initialTemperatureInC) {
		int nextLowerState = (int) Math.floor(
				((initialTemperatureInC - strategyParams.getMinimalRoomTemperatureInC()) / temperatureResolutionInC));
		int nextHigherState = (int) Math.ceil(
				((initialTemperatureInC - strategyParams.getMinimalRoomTemperatureInC()) / temperatureResolutionInC));
		nextLowerState = Math.max(0, nextLowerState);
		nextHigherState = Math.min(numberOfTemperatureSteps - 1, nextHigherState);
		double nextLowerTemperatureInC = strategyParams.getMinimalRoomTemperatureInC()
				+ nextLowerState * temperatureResolutionInC;
		double nextHigherTemperatureInC = strategyParams.getMinimalRoomTemperatureInC()
				+ nextHigherState * temperatureResolutionInC;

		double finalTemperatureLower = calcTemperatureInCFromTemperatureStep(
				bestNextState[hourInSchedule][nextLowerState]);
		double finalTemperatureHigher = calcTemperatureInCFromTemperatureStep(
				bestNextState[hourInSchedule][nextHigherState]);
		double weightLowerResult = 1 - (initialTemperatureInC - nextLowerTemperatureInC) / temperatureResolutionInC;
		double weightHigherResult = 1 - (nextHigherTemperatureInC - initialTemperatureInC) / temperatureResolutionInC;
		return (weightLowerResult * finalTemperatureLower + weightHigherResult * finalTemperatureHigher)
				/ (weightLowerResult + weightHigherResult);
	}

	@Override
	public double getHeatLoad(TimePeriod currentTimeSegment) {
		return 0; // not needed for this Strategist type
	}
}
