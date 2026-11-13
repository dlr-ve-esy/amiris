package agents.electrolysis.sensitivity;

import java.util.ArrayList;
import java.util.Arrays;
import agents.electrolysis.Electrolyzer;
import agents.flexibility.dynamicProgramming.states.StateManager;
import de.dlr.gitlab.fame.agent.input.Make;
import de.dlr.gitlab.fame.agent.input.ParameterData;
import de.dlr.gitlab.fame.agent.input.ParameterData.MissingDataException;
import de.dlr.gitlab.fame.agent.input.Tree;
import de.dlr.gitlab.fame.data.TimeSeries;
import de.dlr.gitlab.fame.time.Constants;
import de.dlr.gitlab.fame.time.TimePeriod;
import de.dlr.gitlab.fame.time.TimeSpan;
import de.dlr.gitlab.fame.time.TimeStamp;

public class ProductionTarget extends ElectrolyzerStrategist {
	static final double ENERGY_OFFSET = 1E-3;

	static final String GROUP_PRODUCTION = "ProductionTarget";
	static final String PARAM_TARGET = "HydrogenProductionTargetInMWH";
	static final String PARAM_INTERVAL = "ProductionTargetIntervalInHours";

	static final Tree parameters = Make.newTree().optional().add(
			Make.newSeries(PARAM_TARGET), Make.newInt(PARAM_INTERVAL)).buildTree();

	private final TimeSeries productionTargets;
	private final int productionInterval;
	private TimePeriod productionPeriod;
	private double[] energyScheduleDummy = new double[scheduleDurationPeriods];

	protected ProductionTarget(ParameterData input, Electrolyzer electrolyzer, TimeSpan operationSpan)
			throws MissingDataException {
		super(input, electrolyzer, operationSpan);
		ParameterData production = input.getGroup(GROUP_PRODUCTION);
		productionTargets = production.getTimeSeries(PARAM_TARGET);
		productionInterval = production.getInteger(PARAM_INTERVAL);
	}

	@Override
	public ArrayList<TimeStamp> getPlanningTimes(TimePeriod startingPeriod) {
		return StateManager.createPlanningTimes(startingPeriod, planningHorizonInHours);
	}

	@Override
	protected void updateSchedule(TimePeriod timePeriod) {
		updateIntervalStart(timePeriod);
		resetElectricityPrices(timePeriod);
		updateOpportunityCosts(timePeriod);
		updateBiddingPriceLimits();
		updateStepTimes(timePeriod);
		Arrays.fill(electricDemandOfElectrolysisInMW, 0);
		double totalProductionTargetInForecastPeriod = calcProductionTargetWithinForecastPeriod(timePeriod);
		optimiseDispatch(totalProductionTargetInForecastPeriod);
		updateScheduleArrays();
	}

	/** updates {@link #productionPeriod} and hydrogen production (deficit or surplus) at the beginning of an interval */
	private void updateIntervalStart(TimePeriod timePeriod) {
		if (productionPeriod == null) {
			TimeStamp startTime = timePeriod.getStartTime();
			TimeSpan duration = new TimeSpan(productionInterval * Constants.STEPS_PER_HOUR);
			productionPeriod = new TimePeriod(startTime, duration);
		}
		if (productionPeriod.getLastTime().isLessEqualTo(timePeriod.getStartTime())) {
			double previousPeriodProductionTarget = productionTargets
					.getValueEarlierEqual(timePeriod.getStartTime().earlierByOne());
			double missingProductionLastInterval = previousPeriodProductionTarget - actualProducedHydrogen;
			actualProducedHydrogen = -missingProductionLastInterval;
			productionPeriod = productionPeriod.shiftByDuration(1);
		}
	}

	/** updates maximum bidding prices for electricity based on opportunity costs for hydrogen */
	private void updateBiddingPriceLimits() {
		for (int period = 0; period < scheduleDurationPeriods; period++) {
			priceScheduleInEURperMWH[period] = hydrogenSaleOpportunityCostsPerElectricMWH[period];
		}
	}

	/** @return total production target within the forecast period - possibly across production intervals */
	private double calcProductionTargetWithinForecastPeriod(TimePeriod timePeriod) {
		int remainingTimeInInterval = getRemainingHoursInProductionInterval(timePeriod);

		double targetProductionPerHour;
		if (remainingTimeInInterval < forecastSteps) {
			targetProductionPerHour = calcAverageProductionAcrossIntervals(timePeriod, remainingTimeInInterval);
		} else {
			targetProductionPerHour = calcMissingProductionCurrentInterval(timePeriod) / remainingTimeInInterval;
		}
		return forecastSteps * targetProductionPerHour;
	}

	/** @return remaining hours in current production interval */
	private int getRemainingHoursInProductionInterval(TimePeriod timePeriod) {
		long timeDeltaInSeconds = timePeriod.getStartTime().getStep() - productionPeriod.getStartTime().getStep();
		int hoursSpentInInterval = (int) (timeDeltaInSeconds / OPERATION_PERIOD.getSteps());
		return productionInterval - hoursSpentInInterval;
	}

	/** @return missing hydrogen production for target until end of current production interval */
	private double calcMissingProductionCurrentInterval(TimePeriod timePeriod) {
		double currentProductionTargetInMWH = productionTargets.getValueEarlierEqual(timePeriod.getStartTime());
		return currentProductionTargetInMWH - actualProducedHydrogen;
	}

	/** @return average hydrogen production required to meet targets during forecast period across production interval borders */
	private double calcAverageProductionAcrossIntervals(TimePeriod timePeriod, int remainingTimeInCurrentInterval) {
		double averageProductionThisInterval = calcMissingProductionCurrentInterval(timePeriod)
				/ (double) remainingTimeInCurrentInterval;
		double nextProductionTargetInMWH = productionTargets.getValueLaterEqual(timePeriod.getStartTime().laterByOne());
		double averageProductionNextInterval = nextProductionTargetInMWH / productionInterval;
		double shareCurrentInterval = remainingTimeInCurrentInterval / (double) forecastSteps;
		double shareNextInterval = 1.0 - shareCurrentInterval;
		return shareCurrentInterval * averageProductionThisInterval + shareNextInterval * averageProductionNextInterval;
	}

	/** distribute conversion power such that costs are minimal assuming rising prices due to additional demand */
	private void optimiseDispatch(double hydrogenProductionTargetInMWH) {
		hourLoop: while (hydrogenProductionTargetInMWH > 0) {
			int bestHour = getHourWithHighestEconomicPotential(forecastSteps);
			if (bestHour < 0) {
				break hourLoop;
			}
			double energyLeftAtCurrentPriceInMWH = assessmentFunction.getRemainingAmountAtSamePrice(stepTimes[bestHour],
					electricDemandOfElectrolysisInMW[bestHour] + ENERGY_OFFSET) + ENERGY_OFFSET;
			double maxElectricPowerInMW = Math.min(getRemainingPowerInMW(bestHour), energyLeftAtCurrentPriceInMWH);
			double maxHydrogenAmountInMWH = electrolyzer.calcHydrogenEnergy(maxElectricPowerInMW);
			double hydrogenAmountInMWH = Math.min(hydrogenProductionTargetInMWH, maxHydrogenAmountInMWH);
			hydrogenProductionTargetInMWH -= hydrogenAmountInMWH;

			double electricEnergyInMWH = electrolyzer.calcElectricEnergy(hydrogenAmountInMWH);
			electricDemandOfElectrolysisInMW[bestHour] += electricEnergyInMWH;
			updateElectricityPrice(bestHour, electricDemandOfElectrolysisInMW[bestHour] + ENERGY_OFFSET);
		}
	}

	/** transfer optimised dispatch to schedule arrays */
	private void updateScheduleArrays() {
		double initialHydrogenProductionInMWH = actualProducedHydrogen;
		for (int hour = 0; hour < scheduleDurationPeriods; hour++) {
			demandScheduleInMWH[hour] = electricDemandOfElectrolysisInMW[hour];
			scheduledChargedHydrogenTotal[hour] = initialHydrogenProductionInMWH;
			initialHydrogenProductionInMWH += electrolyzer.calcHydrogenEnergy(demandScheduleInMWH[hour]);
		}
	}

	@Override
	protected double[] getInternalEnergySchedule() {
		return energyScheduleDummy;
	}

}
