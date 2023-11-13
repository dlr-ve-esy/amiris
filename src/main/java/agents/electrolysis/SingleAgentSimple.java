// SPDX-FileCopyrightText: 2023 German Aerospace Center <amiris@dlr.de>
//
// SPDX-License-Identifier: Apache-2.0
package agents.electrolysis;

import java.util.Arrays;
import agents.markets.meritOrder.Constants;
import agents.markets.meritOrder.books.DemandOrderBook;
import agents.markets.meritOrder.books.SupplyOrderBook;
import agents.markets.meritOrder.sensitivities.MeritOrderSensitivity;
import agents.markets.meritOrder.sensitivities.PriceNoSensitivity;
import de.dlr.gitlab.fame.agent.input.Make;
import de.dlr.gitlab.fame.agent.input.ParameterData;
import de.dlr.gitlab.fame.agent.input.ParameterData.MissingDataException;
import de.dlr.gitlab.fame.agent.input.Tree;
import de.dlr.gitlab.fame.data.TimeSeries;
import de.dlr.gitlab.fame.time.TimePeriod;
import de.dlr.gitlab.fame.time.TimeSpan;
import de.dlr.gitlab.fame.time.TimeStamp;
import util.Polynomial;

/** Dispatches an electrolysis unit with the aim to minimise its cost using an electricity price forecast and a hydrogen
 * production goal
 * 
 * @author Christoph Schimeczek */
public class SingleAgentSimple extends ElectrolyzerStrategist {
	public static final Tree parameters = Make.newTree().add(
			Make.newSeries("HydrogenProductionTargetInMWH").help("How much hydrogen to produce per production interval"),
			Make.newInt("ProductionTargetIntervalInHours").help("How many hours a production interval spans"),
			Make.newDouble("PriceSensitivityFunction").list().help("Price change per additional load in EUR per MWH per MWH"),
			Make.newDouble("PowerStepInMW")).buildTree();

	private final TimeSeries productionTargets;
	private final int productionInterval;
	private final Polynomial priceSensitivity;
	private final double powerStepInMW;
	private double[] electricityPriceForecasts;
	private double[] hydrogenSaleOpportunityCostsPerElectricMWH;
	private double[] electricDemandOfElectrolysisInMW;
	private TimeStamp[] stepTimes;
	private TimePeriod productionPeriod;

	/** Create new {@link SingleAgentSimple}
	 * 
	 * @param generalInput parameter group associated with flexibility strategists in general
	 * @param specificInput parameter group associated with this strategist in specific
	 * @throws MissingDataException if any required input data is missing */
	public SingleAgentSimple(ParameterData generalInput, ParameterData specificInput) throws MissingDataException {
		super(generalInput);
		productionTargets = specificInput.getTimeSeries("HydrogenProductionTargetInMWH");
		productionInterval = specificInput.getInteger("ProductionTargetIntervalInHours");
		priceSensitivity = new Polynomial(specificInput.getList("PriceSensitivityFunction", Double.class));
		powerStepInMW = specificInput.getDouble("PowerStepInMW");
		allocatePlanningResources();
	}

	private void allocatePlanningResources() {
		electricityPriceForecasts = new double[forecastSteps];
		electricDemandOfElectrolysisInMW = new double[forecastSteps];
		stepTimes = new TimeStamp[forecastSteps];
		hydrogenSaleOpportunityCostsPerElectricMWH = new double[forecastSteps];
		Arrays.fill(priceScheduleInEURperMWH, Constants.SCARCITY_PRICE_IN_EUR_PER_MWH);
	}

	@Override
	protected void updateSchedule(TimePeriod timePeriod) {
		updateIntervalStart(timePeriod);
		updateElectricityPriceForecasts(timePeriod);
		updateOpportunityCosts(timePeriod);
		updateBiddingPriceLimits();
		updateStepTimes(timePeriod);
		Arrays.fill(electricDemandOfElectrolysisInMW, 0);
		double totalProductionTargetInForecastPeriod = calcProductionTargetWithinForecastPeriod(timePeriod);
		optimiseDispatch(totalProductionTargetInForecastPeriod);
		updateScheduleArrays(actualProducedHydrogen);
	}

	/** Updates {@link #productionPeriod} and hydrogen production (deficit or surplus) at the beginning of an interval */
	private void updateIntervalStart(TimePeriod timePeriod) {
		if (productionPeriod == null) {
			TimeStamp startTime = timePeriod.getStartTime();
			TimeSpan duration = new TimeSpan(productionInterval * OPERATION_PERIOD.getSteps());
			productionPeriod = new TimePeriod(startTime, duration);
		}
		if (productionPeriod.getLastTime().isLessEqualTo(timePeriod.getStartTime())) {
			double previousPeriodProductionTarget = productionTargets
					.getValueLowerEqual(timePeriod.getStartTime().earlierByOne());
			double missingProductionLastInterval = previousPeriodProductionTarget - actualProducedHydrogen;
			actualProducedHydrogen = -missingProductionLastInterval;
			productionPeriod = productionPeriod.shiftByDuration(1);
		}
	}

	/** copies forecasted prices from sensitivities */
	private void updateElectricityPriceForecasts(TimePeriod startTime) {
		for (int period = 0; period < forecastSteps; period++) {
			TimePeriod timePeriod = startTime.shiftByDuration(period);
			PriceNoSensitivity priceObject = ((PriceNoSensitivity) getSensitivityForPeriod(timePeriod));
			electricityPriceForecasts[period] = priceObject != null ? priceObject.getPriceForecast() : 0;
		}
	}

	/** calculates electricity price equivalent of opportunity costs for selling hydrogen with expected prices */
	private void updateOpportunityCosts(TimePeriod startTime) {
		for (int period = 0; period < forecastSteps; period++) {
			TimePeriod timePeriod = startTime.shiftByDuration(period);
			double hydrogenPriceInEURperThermalMWH = getHydrogenPriceForPeriod(timePeriod);
			hydrogenSaleOpportunityCostsPerElectricMWH[period] = hydrogenPriceInEURperThermalMWH
					* electrolyzer.getConversionFactor();
		}
	}

	/** updates maximum bidding prices for electricity based on opportunity costs for hydrogen */
	private void updateBiddingPriceLimits() {
		for (int period = 0; period < scheduleDurationPeriods; period++) {
			priceScheduleInEURperMWH[period] = hydrogenSaleOpportunityCostsPerElectricMWH[period];
		}
	}

	/** set start time of each hour in forecast interval */
	private void updateStepTimes(TimePeriod timePeriod) {
		for (int hour = 0; hour < forecastSteps; hour++) {
			stepTimes[hour] = timePeriod.shiftByDuration(hour).getStartTime();
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
		double currentProductionTargetInMWH = productionTargets.getValueLowerEqual(timePeriod.getStartTime());
		return currentProductionTargetInMWH - actualProducedHydrogen;
	}

	/** @return average hydrogen production required to meet targets during forecast period across production interval borders */
	private double calcAverageProductionAcrossIntervals(TimePeriod timePeriod, int remainingTimeInCurrentInterval) {
		double averageProductionThisInterval = calcMissingProductionCurrentInterval(timePeriod)
				/ (double) remainingTimeInCurrentInterval;
		double nextProductionTargetInMWH = productionTargets.getValueHigherEqual(timePeriod.getStartTime().laterByOne());
		double averageProductionNextInterval = nextProductionTargetInMWH / productionInterval;
		double shareCurrentInterval = remainingTimeInCurrentInterval / (double) forecastSteps;
		double shareNextInterval = 1.0 - shareCurrentInterval;
		return shareCurrentInterval * averageProductionThisInterval + shareNextInterval * averageProductionNextInterval;
	}

	/** distribute conversion power such that costs are minimal assuming rising prices due to additional demand */
	private void optimiseDispatch(double hydrogenProductionTargetInMWH) {
		hourLoop: while (hydrogenProductionTargetInMWH > 0) {
			int bestHour = getHourWithLowestPriceAndProductionCapability();
			if (bestHour < 0) {
				break hourLoop;
			}
			double maxElectricPowerInMW = Math.min(getRemainingPowerInMW(bestHour), powerStepInMW);
			double maxHydrogenAmountInMWH = electrolyzer.calcHydrogenEnergy(maxElectricPowerInMW);
			double hydrogenAmountInMWH = Math.min(hydrogenProductionTargetInMWH, maxHydrogenAmountInMWH);
			hydrogenProductionTargetInMWH -= hydrogenAmountInMWH;

			double electricEnergyInMWH = electrolyzer.calcElectricEnergy(hydrogenAmountInMWH);
			electricDemandOfElectrolysisInMW[bestHour] += electricEnergyInMWH;
			electricityPriceForecasts[bestHour] += calcPriceIncrease(electricityPriceForecasts[bestHour],
					electricEnergyInMWH);
		}
	}

	/** @return Hour with lowest price among those with remaining production capabilities; -1 of no capability left in any hour */
	private int getHourWithLowestPriceAndProductionCapability() {
		int bestHour = -1;
		double highestPotential = 0; // start with 0 to ignore hours with negative economic potential
		for (int hour = 0; hour < forecastSteps; hour++) {
			double economicPotential = hydrogenSaleOpportunityCostsPerElectricMWH[hour] - electricityPriceForecasts[hour];
			if (economicPotential > highestPotential && getRemainingPowerInMW(hour) > 0) {
				highestPotential = economicPotential;
				bestHour = hour;
			}
		}
		return bestHour;
	}

	/** @return remaining electric power in MW for given hour */
	private double getRemainingPowerInMW(int hour) {
		return Math.max(0., electrolyzer.getPeakPower(stepTimes[hour]) - electricDemandOfElectrolysisInMW[hour]);
	}

	/** @return estimate price increase due to additional demand */
	private double calcPriceIncrease(double currentPrice, double additionalDemandInMWH) {
		return priceSensitivity.evaluateAt(currentPrice) * additionalDemandInMWH;
	}

	/** transfer optimised dispatch to schedule arrays */
	private void updateScheduleArrays(double initialHydrogenProductionInMWH) {
		for (int hour = 0; hour < scheduleDurationPeriods; hour++) {
			demandScheduleInMWH[hour] = electricDemandOfElectrolysisInMW[hour];
			scheduledChargedHydrogenTotal[hour] = initialHydrogenProductionInMWH;
			initialHydrogenProductionInMWH += electrolyzer.calcHydrogenEnergy(demandScheduleInMWH[hour]);
		}
	}

	@Override
	/** @return an empty {@link MeritOrderSensitivity} item of the type used by this {@link Strategist}-type */
	protected MeritOrderSensitivity createBlankSensitivity() {
		return new PriceNoSensitivity();
	}

	@Override
	public void storeMeritOrderForesight(TimePeriod timePeriod, SupplyOrderBook supplyForecast,
			DemandOrderBook demandForecast) {
		throw new RuntimeException(ERR_USE_MERIT_ORDER_FORECAST + StrategistType.DISPATCH_FILE);
	}
}
