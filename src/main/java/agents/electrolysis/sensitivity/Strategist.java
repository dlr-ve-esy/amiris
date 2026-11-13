// SPDX-FileCopyrightText: 2021-2026 German Aerospace Center <amiris@dlr.de>
//
// SPDX-License-Identifier: Apache-2.0
package agents.electrolysis.sensitivity;

import java.util.ArrayList;
import agents.electrolysis.Electrolyzer;
import agents.flexibility.BidSchedule;
import agents.flexibility.dynamicProgramming.DispatchPlanningError;
import agents.flexibility.dynamicProgramming.assessment.AssessmentFunction;
import agents.flexibility.dynamicProgramming.assessment.AssessmentFunctionBuilder;
import agents.markets.meritOrder.Constants;
import agents.markets.meritOrder.sensitivities.MeritOrderSensitivity;
import de.dlr.gitlab.fame.agent.input.Make;
import de.dlr.gitlab.fame.agent.input.ParameterData;
import de.dlr.gitlab.fame.agent.input.ParameterData.MissingDataException;
import de.dlr.gitlab.fame.agent.input.Tree;
import de.dlr.gitlab.fame.communication.message.Message;
import de.dlr.gitlab.fame.time.Constants.Interval;
import de.dlr.gitlab.fame.time.TimePeriod;
import de.dlr.gitlab.fame.time.TimeSpan;
import de.dlr.gitlab.fame.time.TimeStamp;

/** Base class for strategists that operate competing flexible electrolysis units
 * 
 * @author Christoph Schimeczek, Felix Nitsch, Johannes Kochems */
public abstract class Strategist {
	/** Error message if used {@link Strategist} type is not implemented */
	protected static final String ERR_UNKNOWN_STRATEGIST = "This type of flexibility strategist is not implemented: ";

	/** Hard coded time granularity of {@link Strategist} */
	public final static TimeSpan OPERATION_PERIOD = new TimeSpan(1, Interval.HOURS);

	static final String GROUP_ASSESSMENT = "Assessment";

	static final String PARAM_DURATION = "ScheduleDurationInHours";
	static final String PARAM_TOLERANCE = "BidToleranceInEURperMWH";
	static final String GROUP_BIDS = "Bidding";

	// TODO: Add monthly equivalence input and class
	public static final Tree parameters = Make.newTree().addAs(GROUP_ASSESSMENT, AssessmentFunctionBuilder.parameters)
			.add(Make.newGroup(GROUP_BIDS).add(Make.newDouble(PARAM_DURATION), Make.newDouble(PARAM_TOLERANCE).optional()))
			.addAs(ElectrolyzerStrategist.GROUP_STRATEGY, ElectrolyzerStrategist.parameters)
			.addAs(ProductionTarget.GROUP_PRODUCTION, ProductionTarget.parameters)
			.buildTree();

	/** number of time intervals of the created schedules */
	protected final int scheduleDurationPeriods;
	/** safety margins at bidding */
	private final double bidTolerance;

	/** schedule for the electricity demand (or charging) schedule */
	protected double[] demandScheduleInMWH;
	/** schedule for the expected electricity prices */
	protected double[] priceScheduleInEURperMWH;
	/** schedule for the electricity bid prices */
	protected double[] scheduledBidPricesInEURperMWH;

	protected AssessmentFunction assessmentFunction;

	/** Creates new Strategist based on the given input
	 * 
	 * @param input parameters associated with strategists
	 * @param electrolyzer for which to create dispatch strategies
	 * @param operationSpan time interval of a dispatch product
	 * @throws MissingDataException if any required input is missing */
	protected Strategist(ParameterData input, Electrolyzer electrolyzer, TimeSpan operationSpan)
			throws MissingDataException {
		ParameterData bidding = input.getGroup(GROUP_BIDS);
		scheduleDurationPeriods = calcMarketingIntervals(operationSpan, bidding.getDouble(PARAM_DURATION));
		bidTolerance = bidding.getDoubleOrDefault(PARAM_TOLERANCE, 1E-3);
		assessmentFunction = AssessmentFunctionBuilder.build(input.getGroup(GROUP_ASSESSMENT),
				electrolyzer.getVariableCostSeries());
		allocateSchedulingArrays();
	}

	/** Returns how many full marketing intervals can be fitted into horizonInHours
	 * 
	 * @param marketingInterval time span of marketingInterval
	 * @param horizonInHours to be considered
	 * @return how many full marketing intervals can be fitted into horizonInHours */
	public static int calcMarketingIntervals(TimeSpan marketingInterval, double horizonInHours) {
		double intervalsPerHour = (double) de.dlr.gitlab.fame.time.Constants.STEPS_PER_HOUR / marketingInterval.getSteps();
		return (int) (horizonInHours * intervalsPerHour);
	}

	/** Initialises general permanent arrays used for schedule preparation */
	private void allocateSchedulingArrays() {
		demandScheduleInMWH = new double[scheduleDurationPeriods];
		priceScheduleInEURperMWH = new double[scheduleDurationPeriods];
		scheduledBidPricesInEURperMWH = new double[scheduleDurationPeriods];
	}

	/** Removes any stored MeritOrderSensitivity whose associated TimePeriod ends before the given time
	 *
	 * @param time limiting TimeStamp - earlier events to be deleted */
	public void clearSensitivitiesBefore(TimeStamp time) {
		assessmentFunction.clearBefore(time);
	}

	/** Returns list of times at which sensitivity forecasts are missing but needed for scheduling
	 * 
	 * @param startingPeriod first period of planning interval
	 * @return List of {@link TimeStamp}s at which {@link MeritOrderSensitivity} is not yet defined */
	public ArrayList<TimeStamp> getMissingForecastTimes(TimePeriod startingPeriod) {
		return assessmentFunction.getMissingForecastTimes(getPlanningTimes(startingPeriod));
	}

	/** Returns starting time of each planning interval in the planning horizon
	 * 
	 * @param startingPeriod first interval of the planning horizon
	 * @return list of starting times */
	abstract ArrayList<TimeStamp> getPlanningTimes(TimePeriod startingPeriod);

	/** Stores forecasts for dispatch assessment
	 * 
	 * @param messages to be scraped for forecast data */
	public void storeForecast(ArrayList<Message> messages) {
		assessmentFunction.storeForecast(messages);
	}

	/** Creates a {@link BidSchedule} for the connected flexibility
	 * 
	 * @param timePeriod first TimePeriod element of the schedule to be created
	 * @return created {@link BidSchedule} for the specified {@link TimePeriod} */
	public BidSchedule createSchedule(TimePeriod timePeriod) throws DispatchPlanningError {
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

	/** Updates schedule arrays starting at the given TimePeriod
	 * 
	 * @param timePeriod first period of the schedule to be created */
	protected abstract void updateSchedule(TimePeriod timePeriod);

	/** @return array representing the expected internal energy state of the controlled flexibility */
	protected abstract double[] getInternalEnergySchedule();
}