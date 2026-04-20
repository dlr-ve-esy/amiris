// SPDX-FileCopyrightText: 2023-2026 German Aerospace Center <amiris@dlr.de>
//
// SPDX-License-Identifier: Apache-2.0
package agents.electrolysis;

import java.util.ArrayList;
import java.util.TreeMap;
import agents.flexibility.BidSchedule;
import agents.flexibility.Strategist;
import agents.markets.meritOrder.sensitivities.PriceNoSensitivity;
import agents.policy.hydrogen.PolicyItem;
import de.dlr.gitlab.fame.agent.input.Make;
import de.dlr.gitlab.fame.agent.input.ParameterData;
import de.dlr.gitlab.fame.agent.input.ParameterData.MissingDataException;
import de.dlr.gitlab.fame.agent.input.Tree;
import de.dlr.gitlab.fame.data.TimeSeries;
import de.dlr.gitlab.fame.time.TimePeriod;
import de.dlr.gitlab.fame.time.TimeStamp;

/** Base class for electrolysis strategists
 * 
 * @author Christoph Schimeczek */
public abstract class ElectrolyzerStrategist extends Strategist {
	/** The type of strategist for electrolysis unit operation */
	static enum StrategistType {
		/** Creates the dispatch schedule according to a given TimeSeries. */
		DISPATCH_FILE,
		/** Schedules based on a moving target of hydrogen production totals and electricity prices */
		SINGLE_AGENT_SIMPLE,
		/** Schedules green hydrogen dispatch for monthly equivalence between electrolysis demand and renewable generation */
		GREEN_HYDROGEN_MONTHLY
	}

	/** Planned production schedule for hydrogen in thermal MWh */
	protected double[] scheduledChargedHydrogenTotal;
	/** total actual hydrogen produced in thermal MWH */
	protected double actualProducedHydrogen = 0;
	/** Electricity price forecasts for planning horizon */
	protected double[] electricityPriceForecasts;
	/** Maximum willingness to pay for electricity compared to selling hydrogen */
	protected double[] hydrogenSaleOpportunityCostsPerElectricMWH;
	/** Electric power dedicated to electrolysis unit */
	protected double[] electricDemandOfElectrolysisInMW;
	/** Time stamps at the beginning of each scheduling period */
	protected TimeStamp[] stepTimes;
	/** The associated electrolysis unit */
	protected Electrolyzer electrolyzer;
	/** The Dispatch schedule controlled / created by this Strategist */
	protected BidSchedule schedule;

	private TreeMap<TimePeriod, Double> hydrogenPrices = new TreeMap<>();
	private TimeSeries priceLimitOverrideInEURperMWH;
	private PolicyItem policyItem;

	/** Input parameters of {@link ElectrolyzerStrategist} */
	public static final Tree parameters = Make.newTree()
			.add(Strategist.forecastPeriodParam, Strategist.scheduleDurationParam, Strategist.bidToleranceParam,
					Make.newEnum("StrategistType", StrategistType.class))
			.addAs("FixedDispatch", FileDispatcher.parameters).addAs("Simple", SingleAgentSimple.parameters)
			.add(Make.newSeries("PriceLimitOverrideInEURperMWH").optional().help("Overrides hydrogen prices"))
			.buildTree();

	/** Create new {@link ElectrolyzerStrategist}
	 * 
	 * @param input parameters associated with strategists
	 * @throws MissingDataException if any required input is missing */
	protected ElectrolyzerStrategist(ParameterData input) throws MissingDataException {
		super(input);
		scheduledChargedHydrogenTotal = new double[scheduleDurationPeriods];
		allocatePlanningArrays();
		priceLimitOverrideInEURperMWH = input.getTimeSeriesOrDefault("PriceLimitOverrideInEURperMWH", null);
	}

	private void allocatePlanningArrays() {
		electricityPriceForecasts = new double[forecastSteps];
		hydrogenSaleOpportunityCostsPerElectricMWH = new double[forecastSteps];
		electricDemandOfElectrolysisInMW = new double[forecastSteps];
		stepTimes = new TimeStamp[forecastSteps];
	}

	/** Creates new electrolysis Strategist based on its associated input group
	 * 
	 * @param input parameters associated with electrolysis strategists
	 * @param electrolyzer to be assigned to the new strategist
	 * @return new Strategist created from the given input
	 * @throws MissingDataException if any required input is missing */
	public static ElectrolyzerStrategist newStrategist(ParameterData input, Electrolyzer electrolyzer)
			throws MissingDataException {
		StrategistType type = input.getEnum("StrategistType", StrategistType.class);
		ElectrolyzerStrategist strategist;
		switch (type) {
			case DISPATCH_FILE:
				strategist = new FileDispatcher(input, input.getGroup("FixedDispatch"));
				break;
			case SINGLE_AGENT_SIMPLE:
				strategist = new SingleAgentSimple(input, input.getGroup("Simple"));
				break;
			case GREEN_HYDROGEN_MONTHLY:
				strategist = new MonthlyEquivalence(input);
				break;
			default:
				throw new RuntimeException(ERR_UNKNOWN_STRATEGIST + type);
		}
		strategist.setElectrolyzer(electrolyzer);
		return strategist;
	}

	/** sets the {@link ElectrolyzerStrategist}'s electrolyzer to the given one
	 * 
	 * @param electrolyzer to be assigned to this strategist */
	protected final void setElectrolyzer(Electrolyzer electrolyzer) {
		this.electrolyzer = electrolyzer;
	}

	/** copies forecasted prices from sensitivities
	 * 
	 * @param startTime first time period that update is done for */
	protected void updateElectricityPriceForecasts(TimePeriod startTime) {
		for (int period = 0; period < forecastSteps; period++) {
			TimePeriod timePeriod = startTime.shiftByDuration(period);
			PriceNoSensitivity priceObject = ((PriceNoSensitivity) getSensitivityForPeriod(timePeriod));
			electricityPriceForecasts[period] = priceObject != null ? priceObject.getPriceForecast() : 0;
		}
	}

	/** calculates electricity price equivalent of opportunity costs for selling hydrogen with expected prices
	 * 
	 * @param startTime first time period that update is done for */
	protected void updateOpportunityCosts(TimePeriod startTime) {
		for (int period = 0; period < forecastSteps; period++) {
			TimePeriod timePeriod = startTime.shiftByDuration(period);
			double hydrogenPriceInEURperThermalMWH = getHydrogenPriceForPeriod(timePeriod);
			double supportRateInEURperThermalMWH = getHydrogenSupportRateForPeriod(timePeriod);
			hydrogenSaleOpportunityCostsPerElectricMWH[period] = (hydrogenPriceInEURperThermalMWH
					+ supportRateInEURperThermalMWH)
					* electrolyzer.getConversionFactor();
		}
	}

	/** set start time of each hour in forecast interval
	 * 
	 * @param startTime first time period that update is done for */
	protected void updateStepTimes(TimePeriod startTime) {
		for (int hour = 0; hour < forecastSteps; hour++) {
			stepTimes[hour] = startTime.shiftByDuration(hour).getStartTime();
		}
	}

	/** Provides forecast for electricity demand at given time;<br>
	 * only available to FileDispatcher is it doesn't use forecasts themselves
	 * 
	 * @param targetTime to calculate the forecast for
	 * @return forecasted electricity demand
	 * @throws RuntimeException if given Strategist cannot provide forecasts */
	public double getElectricDemandForecastInMW(TimeStamp targetTime) {
		throw new RuntimeException(ERR_PROVIDE_FORECAST + getClass().getSimpleName());
	}

	/** Returns a valid schedule for the given target time
	 * 
	 * @param targetTime to return a valid schedule for
	 * @return the previous schedule (if still valid) or a newly created one */
	public BidSchedule getValidSchedule(TimeStamp targetTime) {
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
	public ArrayList<TimeStamp> getMissingHydrogenPriceForecastsTimes(TimePeriod firstTime) {
		return getMissingForecastTimes(hydrogenPrices, firstTime);
	}

	/** Stores given hydrogen price forecast for the associated TimePeriod: price-forecasting method
	 * 
	 * @param timePeriod associated with the forecast data
	 * @param priceForecastInEURperThermalMWH forecast for the hydrogen price in EUR per thermal MWh */
	public void storeHydrogenPriceForecast(TimePeriod timePeriod, double priceForecastInEURperThermalMWH) {
		if (priceLimitOverrideInEURperMWH == null) {
			hydrogenPrices.put(timePeriod, priceForecastInEURperThermalMWH);
		} else {
			hydrogenPrices.put(timePeriod, priceLimitOverrideInEURperMWH.getValueLinear(timePeriod.getStartTime()));
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
}
