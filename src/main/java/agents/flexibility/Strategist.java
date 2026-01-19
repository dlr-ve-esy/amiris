// SPDX-FileCopyrightText: 2026 German Aerospace Center <amiris@dlr.de>
//
// SPDX-License-Identifier: Apache-2.0
package agents.flexibility;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.TreeMap;
import agents.markets.meritOrder.Constants;
import agents.markets.meritOrder.books.DemandOrderBook;
import agents.markets.meritOrder.books.SupplyOrderBook;
import agents.markets.meritOrder.sensitivities.MeritOrderSensitivity;
import agents.storage.arbitrageStrategists.ArbitrageStrategist;
import de.dlr.gitlab.fame.agent.input.Make;
import de.dlr.gitlab.fame.agent.input.ParameterBuilder;
import de.dlr.gitlab.fame.agent.input.ParameterData;
import de.dlr.gitlab.fame.agent.input.ParameterData.MissingDataException;
import de.dlr.gitlab.fame.time.Constants.Interval;
import de.dlr.gitlab.fame.time.TimePeriod;
import de.dlr.gitlab.fame.time.TimeSpan;
import de.dlr.gitlab.fame.time.TimeStamp;

/** Base class for strategists that operate some kind of flexibility, e.g., an energy storage or flexible electrolysis
 * 
 * @author Christoph Schimeczek, Felix Nitsch */
public abstract class Strategist {
	/** Error message if used {@link Strategist} type cannot provide forecasts */
	protected static final String ERR_PROVIDE_FORECAST = "Cannot provide bid forecasts with flexibility strategist of type: ";
	/** Error message if used {@link Strategist} type cannot deal with incoming forecasts */
	protected static final String ERR_USE_PRICE_FORECAST = "Cannot use price forecasts with flexibility strategist of type: ";
	/** Error message if used {@link Strategist} type cannot process merit order forecast */
	protected static final String ERR_USE_MERIT_ORDER_FORECAST = "Cannot use merit order forecasts with flexibility strategist of type: ";
	/** Error message if used {@link Strategist} type is not implemented */
	protected static final String ERR_UNKNOWN_STRATEGIST = "This type of flexibility strategist is not implemented: ";
	/** Error message if used {@link ForecastUpdateType} is not implemented */
	protected static final String ERR_UNKNOWN_UPDATE_TYPE = "This type of forecast update is not implemented: ";

	private static enum ForecastUpdateType {
		/** Forecasts are requested for all time steps, discarding previously received electricity price forecasts. */
		ALL,
		/** Forecasts are only requested for missing time steps, therefore updated incrementally. */
		INCREMENTAL,
	}

	/** Hard coded time granularity of {@link Strategist} */
	public final static TimeSpan OPERATION_PERIOD = new TimeSpan(1, Interval.HOURS);

	/** number of time steps of available forecasts */
	protected final int forecastSteps;
	/** number of time steps of the created schedules */
	protected final int scheduleDurationPeriods;
	/** safety margins at bidding */
	private final double bidTolerance;
	/** forecast update type */
	private final ForecastUpdateType forecastUpdateType;

	/** schedule for the electricity demand (or charging) schedule */
	protected double[] demandScheduleInMWH;
	/** schedule for the expected electricity prices */
	protected double[] priceScheduleInEURperMWH;
	/** schedule for the electricity bid prices */
	protected double[] scheduledBidPricesInEURperMWH;

	private TreeMap<TimePeriod, MeritOrderSensitivity> sensitivities = new TreeMap<>();

	/** Strategist input parameter: number of time steps of the used forecast */
	public static final ParameterBuilder forecastPeriodParam = Make.newInt("ForecastPeriodInHours");
	/** Strategist input parameter: number of time steps of the created schedules */
	public static final ParameterBuilder scheduleDurationParam = Make.newInt("ScheduleDurationInHours");
	/** Strategist input parameter: safety margin at bidding */
	public static final ParameterBuilder bidToleranceParam = Make.newDouble("BidToleranceInEURperMWH").optional();
	/** Strategist input parameter: forecast update type */
	public static final ParameterBuilder forecastUpdateTypeParam = Make
			.newEnum("ForecastUpdateType", ForecastUpdateType.class)
			.optional();

	/** Creates new Strategist based on the given input
	 * 
	 * @param input parameters associated with strategists
	 * @throws MissingDataException if any required input is missing */
	protected Strategist(ParameterData input) throws MissingDataException {
		forecastSteps = input.getInteger("ForecastPeriodInHours");
		scheduleDurationPeriods = input.getInteger("ScheduleDurationInHours");
		bidTolerance = input.getDoubleOrDefault("BidToleranceInEURperMWH", 1E-3);
		forecastUpdateType = input.getEnumOrDefault("ForecastUpdateType", ForecastUpdateType.class,
				ForecastUpdateType.INCREMENTAL);
		allocateSchedulingArrays();
	}

	/** initialises general permanent arrays used for schedule preparation */
	private void allocateSchedulingArrays() {
		demandScheduleInMWH = new double[scheduleDurationPeriods];
		priceScheduleInEURperMWH = new double[scheduleDurationPeriods];
		scheduledBidPricesInEURperMWH = new double[scheduleDurationPeriods];
	}

	/** Removes any stored MeritOrderSensitivity whose associated TimePeriod ends before the given time
	 *
	 * @param time limiting TimeStamp - earlier events to be deleted */
	public void clearSensitivitiesBefore(TimeStamp time) {
		Iterator<TimePeriod> mapIterator = sensitivities.keySet().iterator();
		while (mapIterator.hasNext()) {
			TimePeriod timePeriod = mapIterator.next();
			if (timePeriod.getLastTime().isLessThan(time)) {
				mapIterator.remove();
			} else {
				break;
			}
		}
	}

	/** Returns list of times at which electricity price forecasts are missing needed for schedule planning
	 * 
	 * @param firstTime first time period to be covered by a created schedule
	 * @return List of {@link TimeStamp}s at which {@link MeritOrderSensitivity} is not yet defined */
	public ArrayList<TimeStamp> getTimesMissingElectricityForecasts(TimePeriod firstTime) {
		return getMissingForecastTimes(sensitivities, firstTime);
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
			switch (forecastUpdateType) {
				case INCREMENTAL:
					if (!map.containsKey(timeSegment)) {
						requestedTimes.add(timeSegment.getStartTime());
					}
					break;
				case ALL:
					requestedTimes.add(timeSegment.getStartTime());
					break;
				default:
					throw new RuntimeException(ERR_UNKNOWN_UPDATE_TYPE + forecastUpdateType);
			}
		}
		return requestedTimes;
	}

	/** Stores given supply and demand bid forecasts for the associated TimePeriod: merit-order forecasting method
	 * 
	 * @param timePeriod associated with the forecast data
	 * @param supplyForecast forecasted supply OrderBook
	 * @param demandForecast forecasted demand OrderBook */
	public void storeMeritOrderForesight(TimePeriod timePeriod, SupplyOrderBook supplyForecast,
			DemandOrderBook demandForecast) {
		MeritOrderSensitivity sensitivity = createBlankSensitivity();
		callOnSensitivity(sensitivity, timePeriod);
		sensitivity.updateSensitivities(supplyForecast, demandForecast);
		sensitivities.put(timePeriod, sensitivity);
	}

	/** @return an empty {@link MeritOrderSensitivity} item of the type used by this {@link ArbitrageStrategist}-type */
	protected abstract MeritOrderSensitivity createBlankSensitivity();

	/** optional action called on given MeritOrderSensitivities
	 * 
	 * @param sensitivity to be modified
	 * @param timePeriod that the sensitivity is valid for */
	protected void callOnSensitivity(MeritOrderSensitivity sensitivity, TimePeriod timePeriod) {};

	/** Stores given electricity price forecast for the associated TimePeriod: price-forecasting method
	 * 
	 * @param timePeriod associated with the forecast data
	 * @param electricityPriceForecastInEURperMWH forecast for the electricity price in EUR per MWh */
	public void storeElectricityPriceForecast(TimePeriod timePeriod, double electricityPriceForecastInEURperMWH) {
		MeritOrderSensitivity sensitivity = createBlankSensitivity();
		sensitivity.updatePriceForecast(electricityPriceForecastInEURperMWH);
		sensitivities.put(timePeriod, sensitivity);
	}

	/** Returns MeritOrderSensitivity associated with the given TimePeriod
	 * 
	 * @param timePeriod to search for associated MeritOrderSensitivity
	 * @return MeritOrderSensitivity associated with the given TimePeriod */
	protected MeritOrderSensitivity getSensitivityForPeriod(TimePeriod timePeriod) {
		return sensitivities.get(timePeriod);
	}

	/** Creates a {@link BidSchedule} for the connected flexibility
	 * 
	 * @param timePeriod first TimePeriod element of the schedule to be created
	 * @return created {@link BidSchedule} for the specified {@link TimePeriod} */
	public BidSchedule createSchedule(TimePeriod timePeriod) {
		updateSchedule(timePeriod);
		updateBidSchedule();
		BidSchedule schedule = new BidSchedule(timePeriod, scheduleDurationPeriods);
		schedule.setBidsScheduleInEURperMWH(scheduledBidPricesInEURperMWH);
		schedule.setRequestedEnergyPerPeriod(demandScheduleInMWH);
		schedule.setExpectedInitialInternalEnergyScheduleInMWH(getInternalEnergySchedule());
		return schedule;
	}

	/** Updates the bid schedules considering safety margins for the bid prices and market price limits */
	protected void updateBidSchedule() {
		for (int period = 0; period < scheduleDurationPeriods; period++) {
			if (demandScheduleInMWH[period] > 0) {
				scheduledBidPricesInEURperMWH[period] = Math.min(Constants.SCARCITY_PRICE_IN_EUR_PER_MWH,
						priceScheduleInEURperMWH[period] + bidTolerance);
			} else if (demandScheduleInMWH[period] < 0) {
				scheduledBidPricesInEURperMWH[period] = Math.max(Constants.MINIMAL_PRICE_IN_EUR_PER_MWH,
						priceScheduleInEURperMWH[period] - bidTolerance);
			} else {
				scheduledBidPricesInEURperMWH[period] = 0;
			}
		}
	}

	/** Updates schedule arrays starting at the given TimePeriod with the given initial energy level
	 * 
	 * @param timePeriod first period of the schedule to be created */
	protected abstract void updateSchedule(TimePeriod timePeriod);

	/** @return array representing the expected internal energy state of the controlled flexibility */
	protected abstract double[] getInternalEnergySchedule();
}