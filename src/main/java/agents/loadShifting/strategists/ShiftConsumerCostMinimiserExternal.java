// SPDX-FileCopyrightText: 2024-2026 German Aerospace Center <amiris@dlr.de>
//
// SPDX-License-Identifier: Apache-2.0
package agents.loadShifting.strategists;

import endUser.EndUserTariff;
import agents.loadShifting.LoadShiftingPortfolio;
import agents.loadShifting.strategists.OptimisationInputs.Solver;
import agents.markets.meritOrder.Constants;
import agents.markets.meritOrder.sensitivities.MeritOrderSensitivity;
import agents.markets.meritOrder.sensitivities.PriceNoSensitivity;
import agents.storage.arbitrageStrategists.ArbitrageStrategist;
import de.dlr.gitlab.fame.agent.input.Input;
import de.dlr.gitlab.fame.agent.input.Make;
import de.dlr.gitlab.fame.agent.input.ParameterData;
import de.dlr.gitlab.fame.agent.input.ParameterData.MissingDataException;
import de.dlr.gitlab.fame.agent.input.Tree;
import de.dlr.gitlab.fame.data.TimeSeries;
import de.dlr.gitlab.fame.time.TimePeriod;
import de.dlr.gitlab.fame.time.TimeStamp;
import util.UrlModelService;

/** Determines a scheduling strategy for a {@link LoadShiftingPortfolio} in order to minimise overall costs for energy
 * consumption. Calls an external optimisation model for scheduling demand response dispatch.
 * 
 * @author Johannes Kochems, Christoph Schimeczek */
public class ShiftConsumerCostMinimiserExternal extends LoadShiftingStrategist {
	/** Input parameters for {@link ShiftConsumerCostMinimiserExternal} */
	@Input public static final Tree apiParameters = Make.newTree()
			.add(Make.newString("ServiceUrl"), Make.newInt("UseAnnualLimit"), Make.newEnum("Solver", Solver.class),
					Make.newSeries("PriceSensitivityEstimate"))
			.buildTree();

	private final EndUserTariff tariffStrategist;
	private final UrlModelService<OptimisationInputs, OptimisationResult> optimiserApi;
	private final boolean activateAnnualLimits;
	private final Solver solver;
	private final TimeSeries priceSensitivity;
	private double variableShiftingCostsFromOptimiser;

	/** Instantiate {@link ShiftConsumerCostMinimiserExternal}
	 * 
	 * @param generalInput parameters associated with strategists in general
	 * @param specificInput for {@link ShiftConsumerCostMinimiserExternal}
	 * @param endUserTariff determines prices for end users of electricity
	 * @param loadShiftingPortfolio for which schedules are to be created
	 * @throws MissingDataException if any required input is missing */
	public ShiftConsumerCostMinimiserExternal(ParameterData generalInput, ParameterData specificInput,
			EndUserTariff endUserTariff, LoadShiftingPortfolio loadShiftingPortfolio) throws MissingDataException {
		super(generalInput, specificInput, loadShiftingPortfolio);
		this.tariffStrategist = endUserTariff;
		optimiserApi = new UrlModelService<OptimisationInputs, OptimisationResult>(
				specificInput.getString("ServiceUrl")) {};
		activateAnnualLimits = specificInput.getInteger("UseAnnualLimit") >= 1;
		solver = specificInput.getEnum("Solver", Solver.class);
		priceSensitivity = specificInput.getTimeSeries("PriceSensitivityEstimate");
	}

	@Override
	protected void updateSchedule(TimePeriod startTime, double currentEnergyShiftStorageLevelInMWH,
			int currentShiftTime) {
		OptimisationInputs inputs = prepareInputs(startTime);
		OptimisationResult optimisationResult = optimiserApi.call(inputs);
		variableShiftingCostsFromOptimiser = optimisationResult.getOverallVariableCosts();
		updateScheduleArrays(startTime, currentEnergyShiftStorageLevelInMWH, currentShiftTime, optimisationResult);
	}

	private OptimisationInputs prepareInputs(TimePeriod startTime) {
		OptimisationInputs inputs = new OptimisationInputs();
		inputs.setActivate_annual_limits(activateAnnualLimits);
		inputs.setAvailability_down(convertToArray(portfolio.getDowerDownAvailabilities(), startTime));
		inputs.setAvailability_up(convertToArray(portfolio.getPowerUpAvailabilities(), startTime));
		inputs.setEfficiency(portfolio.getEfficiency());
		inputs.setEnergy_price(getConsumerPriceForecasts(startTime));
		inputs.setInterference_time(portfolio.getInterferenceTimeInHours());
		inputs.setMax_capacity_down(portfolio.getPowerInMW());
		inputs.setMax_capacity_up(portfolio.getPowerInMW());
		inputs.setMax_shifting_time(portfolio.getMaximumShiftTimeInHours());
		inputs.setNormalized_baseline_load(convertToArray(portfolio.getBaselineLoadSeries(), startTime));
		inputs.setPeak_demand_before(portfolio.getBaselinePeakLoad());
		inputs.setPeak_load_price(tariffStrategist.calcCapacityRelatedPriceInEURPerMW(startTime.getStartTime()));
		inputs.setSolver(solver);
		inputs.setVariable_costs_down(convertToArray(portfolio.getVariableShiftCostsInEURPerMWHSeries(), startTime));
		inputs.setVariable_costs_up(convertToArray(portfolio.getVariableShiftCostsInEURPerMWHSeries(), startTime));
		inputs.setMax_activations(portfolio.getMaximumActivations());
		inputs.setInitial_energy_level(portfolio.getCurrentEnergyShiftStorageLevelInMWH());
		inputs.setPrice_sensitivity(convertToArray(priceSensitivity, startTime));
		return inputs;
	}

	/** @return array of forecastSteps values from given time series beginning with given time periods */
	private double[] convertToArray(TimeSeries timeSeries, TimePeriod firstPeriod) {
		double[] array = new double[forecastSteps];
		for (int shiftIndex = 0; shiftIndex < forecastSteps; shiftIndex++) {
			TimeStamp time = firstPeriod.shiftByDuration(shiftIndex).getStartTime();
			array[shiftIndex] = timeSeries.getValueLinear(time);
		}
		return array;
	}

	/** @return forecasted consumer prices based on forecasted prices */
	private double[] getConsumerPriceForecasts(TimePeriod startTime) {
		double[] consumerPrices = new double[forecastSteps];
		for (int period = 0; period < forecastSteps; period++) {
			TimePeriod timePeriod = startTime.shiftByDuration(period);
			PriceNoSensitivity priceObject = ((PriceNoSensitivity) getSensitivityForPeriod(timePeriod));
			double exchangePriceForecast = priceObject != null ? priceObject.getPriceForecast() : 0;
			consumerPrices[period] = tariffStrategist.calcSalePriceInEURperMWH(exchangePriceForecast,
					startTime.getStartTime());
		}
		return consumerPrices;
	}

	/** updates schedule arrays
	 * <ul>
	 * <li>{@link ArbitrageStrategist#periodChargingScheduleInMW}</li>
	 * <li>{@link ArbitrageStrategist#periodPriceScheduleInEURperMWH}</li>
	 * <li>{@link ArbitrageStrategist#periodScheduledInitialInternalEnergyInMWH}</li>
	 * </ul>
	 * based on the optimised dispatch */
	private void updateScheduleArrays(TimePeriod startTime, double currentEnergyShiftStorageLevelInMWH,
			int currentShiftTime, OptimisationResult optimisationResult) {
		for (int period = 0; period < scheduleDurationPeriods; period++) {
			scheduledInitialEnergyInMWH[period] = currentEnergyShiftStorageLevelInMWH;
			double energyDelta = optimisationResult.getUpshift(period) - optimisationResult.getDownshift(period);
			demandScheduleInMWH[period] = energyDelta;
			if (demandScheduleInMWH[period] == 0) {
				priceScheduleInEURperMWH[period] = Double.NaN;
			} else {
				priceScheduleInEURperMWH[period] = calcBidPriceInPeriod(energyDelta);
			}
			currentEnergyShiftStorageLevelInMWH += energyDelta;
		}
	}

	/** @return bid price for given energy delta */
	private double calcBidPriceInPeriod(double energyDelta) {
		return energyDelta > 0 ? Constants.SCARCITY_PRICE_IN_EUR_PER_MWH : Constants.MINIMAL_PRICE_IN_EUR_PER_MWH;
	}

	@Override
	protected MeritOrderSensitivity createBlankSensitivity() {
		return new PriceNoSensitivity();
	}

	@Override
	/** @return variable shifting costs from scheduling using external optimisation micro-model */
	public double getVariableShiftingCostsFromOptimiser() {
		return variableShiftingCostsFromOptimiser;
	}
}
