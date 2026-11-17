// SPDX-FileCopyrightText: 2026 German Aerospace Center <amiris@dlr.de>
//
// SPDX-License-Identifier: Apache-2.0
package agents.electrolysis.sensitivity;

import java.util.ArrayList;
import java.util.TreeMap;
import agents.electrolysis.Electrolyzer;
import agents.flexibility.BidSchedule;
import agents.flexibility.dynamicProgramming.DispatchPlanningError;
import agents.forecast.sensitivity.SensitivityForecastProvider.ForecastType;
import agents.policy.hydrogen.PolicyItem;
import de.dlr.gitlab.fame.agent.input.Make;
import de.dlr.gitlab.fame.agent.input.ParameterData;
import de.dlr.gitlab.fame.agent.input.ParameterData.MissingDataException;
import de.dlr.gitlab.fame.agent.input.Tree;
import de.dlr.gitlab.fame.time.TimePeriod;
import de.dlr.gitlab.fame.time.TimeSpan;
import de.dlr.gitlab.fame.time.TimeStamp;

/** Base class for electrolysis strategists using sensitivity forecasts
 * 
 * @author Christoph Schimeczek, Johannes Kochems */
public abstract class ElectrolyzerStrategist extends Strategist {
	/** The type of strategist for electrolysis unit operation */
	static enum StrategistType {
		/** Schedules based on a moving target of hydrogen production totals and electricity prices */
		PRODUCTION_TARGET,
		/** Schedules green hydrogen dispatch for monthly equivalence between electrolysis demand and renewable generation */
		GREEN_HYDROGEN_MONTHLY
	}

	static final String GROUP_STRATEGY = "Strategy";
	static final String PARAM_FORESIGHT = "PlanningHorizonInHours";
	static final String PARAM_TYPE = "Type";

	public static final Tree parameters = Make.newTree()
			.addAs(ProductionTarget.GROUP_PRODUCTION, ProductionTarget.parameters)
			.add(Make.newDouble(PARAM_FORESIGHT), Make.newEnum(PARAM_TYPE, StrategistType.class))
			.buildTree();

	/** Planned production schedule for hydrogen in thermal MWh */
	protected double[] scheduledChargedHydrogenTotal;
	/** total actual hydrogen produced in thermal MWH */
	protected double actualProducedHydrogen = 0;
	/** Maximum willingness to pay for electricity compared to selling hydrogen */
	protected double[] hydrogenSaleOpportunityCostsPerElectricMWH;
	/** Electric power dedicated to electrolysis unit */
	protected double[] electricDemandOfElectrolysisInMW;
	/** Electricity price forecasts for */
	protected double[] electricityPriceForecasts;
	/** Time stamps at the beginning of each scheduling period */
	protected TimeStamp[] stepTimes;
	/** The associated electrolysis unit */
	protected Electrolyzer electrolyzer;
	/** The Dispatch schedule controlled / created by this Strategist */
	protected BidSchedule schedule;
	/** Number of time intervals for forecasting */
	protected int forecastSteps;
	/** Length of planning time in hours */
	protected double planningHorizonInHours;

	private TreeMap<TimePeriod, Double> hydrogenPrices = new TreeMap<>();
	private PolicyItem policyItem;

	protected ElectrolyzerStrategist(ParameterData input, Electrolyzer electrolyzer, TimeSpan operationSpan)
			throws MissingDataException {
		super(input, electrolyzer, operationSpan);
		this.electrolyzer = electrolyzer;
		ParameterData strategy = input.getGroup(GROUP_STRATEGY);
		planningHorizonInHours = strategy.getDouble(PARAM_FORESIGHT);
		forecastSteps = calcMarketingIntervals(operationSpan, planningHorizonInHours);
		allocatePlanningArrays();
	}

	private void allocatePlanningArrays() {
		electricityPriceForecasts = new double[forecastSteps];
		hydrogenSaleOpportunityCostsPerElectricMWH = new double[forecastSteps];
		electricDemandOfElectrolysisInMW = new double[forecastSteps];
		stepTimes = new TimeStamp[forecastSteps];
	}

	/** Creates new electrolysis {@link Strategist} based on its associated input group
	 * 
	 * @param input parameters associated with electrolysis strategists
	 * @param electrolyzer to be assigned to the new strategist
	 * @param operationSpan of the electricity products
	 * @return new {@link Strategist} created from the given input
	 * @throws MissingDataException if any required input is missing */
	public static ElectrolyzerStrategist newStrategist(ParameterData input, Electrolyzer electrolyzer,
			TimeSpan operationSpan)
			throws MissingDataException {
		StrategistType type = input.getGroup(GROUP_STRATEGY).getEnum(PARAM_TYPE, StrategistType.class);
		switch (type) {
			case PRODUCTION_TARGET:
				return new ProductionTarget(input, electrolyzer, operationSpan);
			default:
				throw new RuntimeException(ERR_UNKNOWN_STRATEGIST + type);
		}
	}

	/** Calculates electricity price equivalent of opportunity costs for selling hydrogen with expected prices
	 * 
	 * @param startTime first time period that update is done for */
	protected void updateOpportunityCosts(TimePeriod startTime) {
		for (int period = 0; period < forecastSteps; period++) {
			TimePeriod timePeriod = startTime.shiftByDuration(period);
			double hydrogenPriceInEURperThermalMWH = getHydrogenPriceForPeriod(timePeriod);
			double supportRateInEURperThermalMWH = getHydrogenSupportRateForPeriod(timePeriod);
			hydrogenSaleOpportunityCostsPerElectricMWH[period] = (hydrogenPriceInEURperThermalMWH
					+ supportRateInEURperThermalMWH)
					* electrolyzer.getConversionFactor()
					- electrolyzer.getVariableCostSeries().getValueLinear(timePeriod.getStartTime());
		}
	}

	/** Returns hydrogen price forecast associated with given TimePeriod
	 * 
	 * @param timePeriod to search for associated hydrogen price
	 * @return hydrogen price forecast in EUR per thermal MWh or, if not present, {@link Double#MAX_VALUE} */
	protected double getHydrogenPriceForPeriod(TimePeriod timePeriod) {
		Double priceForecast = hydrogenPrices.get(timePeriod);
		return priceForecast != null ? priceForecast : Double.MAX_VALUE;
	}

	/** Returns the hydrogen support rate in EUR per MWh if defined, else Zero
	 * 
	 * @param timePeriod to search for associated support rate
	 * @return hydrogen support rate in EUR per MWh */
	protected double getHydrogenSupportRateForPeriod(TimePeriod timePeriod) {
		if (policyItem != null) {
			return policyItem.calcInfeedSupportRate(timePeriod.getStartTime());
		}
		return 0.;
	}

	/** set start time of each hour in forecast interval
	 * 
	 * @param startTime first time period that update is done for */
	protected void updateStepTimes(TimePeriod startTime) {
		for (int hour = 0; hour < forecastSteps; hour++) {
			stepTimes[hour] = startTime.shiftByDuration(hour).getStartTime();
		}
	}

	/** Set electricity prices for each hour in forecast interval to their baseline value
	 * 
	 * @param startTime first time period that update is done for */
	protected void resetElectricityPrices(TimePeriod startTime) {
		for (int hour = 0; hour < forecastSteps; hour++) {
			electricityPriceForecasts[hour] = assessmentFunction.getElectricityPriceAt(stepTimes[hour], 0);
		}
	}

	/** Set electricity price for specified hour in forecast interval, assuming given total external energy
	 * 
	 * @param hour to evaluate
	 * @param externalEnergyInMWH charging if positive, discharging when negative */
	protected void updateElectricityPrice(int hour, double externalEnergyInMWH) {
		electricityPriceForecasts[hour] = assessmentFunction.getElectricityPriceAt(stepTimes[hour], externalEnergyInMWH);
	}

	/** Returns a valid schedule for the given target time
	 * 
	 * @param targetTime to return a valid schedule for
	 * @return the previous schedule (if still valid) or a newly created one
	 * @throws DispatchPlanningError */
	public BidSchedule getValidSchedule(TimeStamp targetTime) throws DispatchPlanningError {
		if (schedule == null || !schedule.isApplicable(targetTime, actualProducedHydrogen)) {
			clearSensitivitiesBefore(targetTime);
			TimePeriod targetTimeSegment = new TimePeriod(targetTime, OPERATION_PERIOD);
			schedule = createSchedule(targetTimeSegment);
		}
		return schedule;
	}

	@Override
	protected double[] getInternalEnergySchedule() {
		return scheduledChargedHydrogenTotal;
	}

	/** Updates produced hydrogen total by adding the given amount
	 * 
	 * @param producedHydrogenInMWH amount of produced hydrogen to add to the total */
	public void updateProducedHydrogenTotal(double producedHydrogenInMWH) {
		actualProducedHydrogen += producedHydrogenInMWH;
	}

	/** Returns list of times at which hydrogen price forecasts are missing needed for schedule planning
	 * 
	 * @param firstTime first time period to be covered by a created schedule
	 * @return List of {@link TimeStamp}s at which hydrogen prices is not yet defined */
	public ArrayList<TimeStamp> getMissingHydrogenPriceForecastTimes(TimePeriod firstTime) {
		return getMissingForecastTimes(hydrogenPrices, firstTime);
	}

	/** Returns list of times at which given TreeMap are requested for schedule planning
	 * 
	 * @param map to be inspected for existing TimeSegment keys
	 * @param firstTime first time period to be covered by a created schedule
	 * @return List of {@link TimeStamp}s at which entries are requested */
	protected ArrayList<TimeStamp> getMissingForecastTimes(TreeMap<TimePeriod, ?> map, TimePeriod firstTime) {
		ArrayList<TimeStamp> requestedTimes = new ArrayList<>();
		for (int period = 0; period < forecastSteps; period++) {
			TimePeriod timeSegment = firstTime.shiftByDuration(period);
			if (!map.containsKey(timeSegment)) {
				requestedTimes.add(timeSegment.getStartTime());
			}
		}
		return requestedTimes;
	}

	/** Stores given hydrogen price forecast for the associated TimePeriod: price-forecasting method
	 * 
	 * @param timePeriod associated with the forecast data
	 * @param priceForecastInEURperThermalMWH forecast for the hydrogen price in EUR per thermal MWh */
	public void storeHydrogenPriceForecast(TimePeriod timePeriod, double priceForecastInEURperThermalMWH) {
		hydrogenPrices.put(timePeriod, priceForecastInEURperThermalMWH);
	}

	/** @param endOfHorizon last step of planning horizon
	 * @return Hour with highest economic potential among those with remaining production capabilities; -1 of no capability left in
	 *         any hour */
	protected int getHourWithHighestEconomicPotential(int endOfHorizon) {
		int bestHour = -1;
		double highestPotential = 0; // start with 0 to ignore hours with negative economic potential
		for (int hour = 0; hour < endOfHorizon; hour++) {
			double economicPotential = getEconomicHydrogenPotential(hour);
			if (economicPotential > highestPotential && getRemainingPowerInMW(hour) > 0) {
				highestPotential = economicPotential;
				bestHour = hour;
			}
		}
		return bestHour;
	}

	/** @param hour to evaluate
	 * @return remaining (unused) electrolyzer electric capacity in MW for given hour */
	protected double getRemainingPowerInMW(int hour) {
		return Math.max(0., electrolyzer.getPeakPower(stepTimes[hour]) - electricDemandOfElectrolysisInMW[hour]);
	}

	/** @param hour to evaluate
	 * @return Economic potential in given hour to purchase electricity and sell hydrogen */
	protected double getEconomicHydrogenPotential(int hour) {
		return hydrogenSaleOpportunityCostsPerElectricMWH[hour] - electricityPriceForecasts[hour];
	}

	/** Set policy item
	 * 
	 * @param policyItem to be set */
	public void setSupportRateInEURperMWH(PolicyItem policyItem) {
		this.policyItem = policyItem;
	}

	/** @return ForecastType for electrolyzer strategist's assessment function */
	public ForecastType getSensitivityType() {
		return assessmentFunction.getSensitivityType();
	}

	/** @return latest multiplier of assessment function */
	public double getMultiplier() {
		return assessmentFunction.getMultiplier();
	}
}
