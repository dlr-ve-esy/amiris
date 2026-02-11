// SPDX-FileCopyrightText: 2024-2026 German Aerospace Center <amiris@dlr.de>
//
// SPDX-License-Identifier: Apache-2.0
package agents.electrolysis;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.TreeMap;
import agents.flexibility.BidSchedule;
import agents.markets.meritOrder.books.DemandOrderBook;
import agents.markets.meritOrder.books.SupplyOrderBook;
import agents.markets.meritOrder.sensitivities.MeritOrderSensitivity;
import agents.markets.meritOrder.sensitivities.PriceNoSensitivity;
import communications.message.PpaInformation;
import de.dlr.gitlab.fame.agent.input.ParameterData;
import de.dlr.gitlab.fame.agent.input.ParameterData.MissingDataException;
import de.dlr.gitlab.fame.time.TimePeriod;
import de.dlr.gitlab.fame.time.TimeStamp;

/** Strategist for operation of an electrolysis unit operation plus corresponding bidding strategies for purchasing grey
 * electricity and selling green electricity. This Strategist tries to maximise profits while **not** jeopardizing the monthly
 * green hydrogen production equivalence. It considers forecasts of:
 * <ul>
 * <li>costs for buying green electricity from a PPA contract partner,</li>
 * <li>costs for buying grey electricity from the day-ahead market,</li>
 * <li>rewards of selling green electricity to the day-head market,</li>
 * <li>rewards of selling green hydrogen to the fuels market,</li>
 * <li>amount of available green electricity produced by a PPA contract partner,</li>
 * <li>operational restrictions of the employed electrolysis unit.</li>
 * </ul>
 * Based on these forecasts a multi-layered operation and bidding strategy is devised, considering:
 * <ul>
 * <li>The sum of produced hydrogen is (just) below the sum of its green electricity equivalent produced by the PPA contract
 * partner (monthly equivalence).</li>
 * <li>If electricity prices exceed their corresponding hydrogen value, all green electricity is sold.</li>
 * <li>Surplus green electricity is sold, if the electricity price is above the negative corresponding hydrogen value.</li>
 * <li>Green electricity is curtailed if electricity prices are below that threshold and monthly equivalence is not
 * endangered.</li>
 * </ul>
 * Months are considered to have an identical number of hours.
 * 
 * @author Christoph Schimeczek, Johannes Kochems */
public class MonthlyEquivalence extends ElectrolyzerStrategist {
	static final String ERR_PPA_MISSING = "PpaInformationForecast missing for time period: ";
	static final String ERR_NOT_INTENDED = "Method not intended for strategist type: ";
	static final double TOLERANCE = 1E-3;

	private final TreeMap<TimePeriod, PpaInformation> ppaForesight = new TreeMap<>();
	private double greenElectricitySurplusTotal = 0;
	private TimePeriod lastTimePeriodInCurrentMonth;
	/** Replaces internal energy schedule: the relevant state to monitor in the schedule is the green electricity surplus */
	private final double[] scheduledGreenElectricitySurplus = new double[scheduleDurationPeriods];

	private PpaInformation[] ppaInformationForecasts;
	private double[] purchasedElectricityInMWH;
	private double[] bidPricesInEURperMWH;

	/** Create new {@link MonthlyEquivalence}
	 * 
	 * @param input parameters associated with this strategist
	 * @throws MissingDataException if any required input is missing */
	protected MonthlyEquivalence(ParameterData input) throws MissingDataException {
		super(input);
		allocatePlanningArrays();
	}

	/** Allocate arrays that are used for bidding strategy planning */
	private void allocatePlanningArrays() {
		ppaInformationForecasts = new PpaInformation[forecastSteps];
		purchasedElectricityInMWH = new double[forecastSteps];
		bidPricesInEURperMWH = new double[forecastSteps];
	}

	/** Returns list of times at which PPA forecasts are missing but needed for schedule planning
	 * 
	 * @param firstTime first time period to be covered by a created schedule
	 * @return List of {@link TimeStamp}s at which {@link PpaInformation} is not yet defined */
	public ArrayList<TimeStamp> getTimesMissingPpaForecast(TimePeriod firstTime) {
		return getMissingForecastTimes(ppaForesight, firstTime);
	}

	/** Stores given {@link PpaInformation} at the specified {@link TimePeriod}
	 * 
	 * @param timePeriod at which the PpaInformation is valid
	 * @param ppaInformation to be stored for later reference */
	public void storePpaForecast(TimePeriod timePeriod, PpaInformation ppaInformation) {
		ppaForesight.put(timePeriod, ppaInformation);
	}

	/** Returns {@link PpaInformation} stored earlier for the given {@link TimePeriod}
	 * 
	 * @param timePeriod to search for
	 * @return previously stored PpaInformation for the specified TimePeriod or null if no data is available at the specified
	 *         TimePeriod */
	public PpaInformation getPpaForPeriod(TimePeriod timePeriod) {
		return ppaForesight.get(timePeriod);
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

	@Override
	public BidSchedule getValidSchedule(TimeStamp targetTime) {
		if (schedule == null || !schedule.isApplicable(targetTime, greenElectricitySurplusTotal)) {
			clearSensitivitiesBefore(targetTime);
			TimePeriod targetTimeSegment = new TimePeriod(targetTime, OPERATION_PERIOD);
			schedule = createSchedule(targetTimeSegment);
		}
		return schedule;
	}

	@Override
	protected void updateSchedule(TimePeriod timePeriod) {
		clearPlanningArrays();
		updateElectricityPriceForecasts(timePeriod);
		updateOpportunityCosts(timePeriod);
		updatePpaInformationForecast(timePeriod);
		updateStepTimes(timePeriod);
		schedulePpaProductionWithPositiveOpportunity(timePeriod);
		scheduleGreyElectricityPurchase(timePeriod);
		rescheduleForExtremelyNegativePrices(timePeriod);
		updateScheduleArrays(greenElectricitySurplusTotal);
	}

	/** Reset all entries to Zero in relevant planning arrays */
	private void clearPlanningArrays() {
		Arrays.fill(purchasedElectricityInMWH, 0);
		Arrays.fill(bidPricesInEURperMWH, 0);
		Arrays.fill(electricDemandOfElectrolysisInMW, 0);
	}

	/** Read {@link PpaInformation} forecasts for all planning times and store them to {@link #ppaInformationForecasts} */
	private void updatePpaInformationForecast(TimePeriod startTime) {
		for (int period = 0; period < forecastSteps; period++) {
			TimePeriod timePeriod = startTime.shiftByDuration(period);
			var ppa = getPpaForPeriod(timePeriod);
			if (ppa == null) {
				throw new RuntimeException(ERR_PPA_MISSING + timePeriod);
			}
			ppaInformationForecasts[period] = ppa;
		}
	}

	/** Schedule production of electrolyzer and market offers for times with renewable electricity provision from PPA */
	private void schedulePpaProductionWithPositiveOpportunity(TimePeriod firstPeriod) {
		for (int index = 0; index < calcNumberOfPlanningSteps(firstPeriod); index++) {
			double ppaProduction = ppaInformationForecasts[index].yieldPotentialInMWH;
			if (electricityPriceForecasts[index] > hydrogenSaleOpportunityCostsPerElectricMWH[index]) {
				purchasedElectricityInMWH[index] = -ppaProduction;
				bidPricesInEURperMWH[index] = hydrogenSaleOpportunityCostsPerElectricMWH[index];
			} else {
				electricDemandOfElectrolysisInMW[index] = electrolyzer.calcCappedElectricDemandInMW(ppaProduction,
						stepTimes[index]);
				if (electricityPriceForecasts[index] >= 0) {
					double surplus = ppaProduction - electricDemandOfElectrolysisInMW[index];
					purchasedElectricityInMWH[index] = -surplus;
					bidPricesInEURperMWH[index] = 0;
				}
			}
		}
	}

	/** @return Number of planning steps limited to either end of forecast period or end of month */
	private int calcNumberOfPlanningSteps(TimePeriod firstPeriod) {
		long stepDelta = lastTimePeriodInCurrentMonth.getStartTime().getStep() - firstPeriod.getStartTime().getStep();
		int stepsUntilEndOfMonth = (int) (stepDelta / OPERATION_PERIOD.getSteps()) + 1;
		return Math.min(forecastSteps, stepsUntilEndOfMonth);
	}

	/** Plan grey electricity purchases based on projected green electricity sales */
	private void scheduleGreyElectricityPurchase(TimePeriod firstPeriod) {
		double surplusPosition = calcSurplusPosition(firstPeriod);
		if (surplusPosition >= 0) {
			schedulePurchaseForFullLoadOperation(firstPeriod);
		} else {
			schedulePurchaseForSurplusDeficit(firstPeriod);
			scheduleSalesForNegativePrices(firstPeriod);
		}
	}

	/** @return expected surplus from selling green electricity compared to purchasing grey electricity when running the
	 *         electrolyzer at full capacity */
	private double calcSurplusPosition(TimePeriod firstPeriod) {
		double surplusPosition = greenElectricitySurplusTotal;
		for (int index = 0; index < calcNumberOfPlanningSteps(firstPeriod); index++) {
			surplusPosition -= purchasedElectricityInMWH[index];
			if (electricityPriceForecasts[index] < hydrogenSaleOpportunityCostsPerElectricMWH[index]) {
				surplusPosition -= getRemainingPowerInMW(index);
			}
		}
		return surplusPosition;
	}

	/** Assuming an overall surplus of sold green electricity, maximise hydrogen production */
	private void schedulePurchaseForFullLoadOperation(TimePeriod firstPeriod) {
		for (int index = 0; index < calcNumberOfPlanningSteps(firstPeriod); index++) {
			if (electricityPriceForecasts[index] < hydrogenSaleOpportunityCostsPerElectricMWH[index]) {
				double remainingPowerInMW = getRemainingPowerInMW(index);
				if (remainingPowerInMW > 0) {
					purchasedElectricityInMWH[index] = remainingPowerInMW;
					bidPricesInEURperMWH[index] = hydrogenSaleOpportunityCostsPerElectricMWH[index];
					electricDemandOfElectrolysisInMW[index] += purchasedElectricityInMWH[index];
				}
			}
		}
	}

	/** Assuming an overall deficit of sold green electricity, maximise profits while maintaining monthly equivalence */
	private void schedulePurchaseForSurplusDeficit(TimePeriod firstPeriod) {
		double netSurplus = calcNetGreenElectricitySurplus(firstPeriod);
		while (netSurplus > 0) {
			int bestHour = getHourWithHighestEconomicPotential(calcNumberOfPlanningSteps(firstPeriod));
			if (bestHour < 0) {
				break;
			}
			double powerToPurchase = Math.min(netSurplus, getRemainingPowerInMW(bestHour));
			netSurplus -= powerToPurchase;
			purchasedElectricityInMWH[bestHour] += powerToPurchase;
			bidPricesInEURperMWH[bestHour] = hydrogenSaleOpportunityCostsPerElectricMWH[bestHour];
			electricDemandOfElectrolysisInMW[bestHour] += powerToPurchase;
		}
	}

	/** @return surplus from selling green electricity compared to buying grey electricity */
	private double calcNetGreenElectricitySurplus(TimePeriod firstPeriod) {
		double netSurplus = greenElectricitySurplusTotal;
		for (int index = 0; index < calcNumberOfPlanningSteps(firstPeriod); index++) {
			netSurplus -= purchasedElectricityInMWH[index];
		}
		return netSurplus;
	}

	/** Schedule sales of green electricity at negative electricity prices if a corresponding purchasing option exists that has
	 * higher economic potential compared to selling at negative prices */
	private void scheduleSalesForNegativePrices(TimePeriod firstPeriod) {
		int endOfHorizon = calcNumberOfPlanningSteps(firstPeriod);
		int bestBuyHour = getHourWithHighestEconomicPotential(endOfHorizon);
		int bestSellHour = getHourWithLowestSaleCosts(endOfHorizon);
		while (bestBuyHour >= 0 && bestSellHour >= 0) {
			double economicPotential = getEconomicHydrogenPotential(bestBuyHour);
			if (-electricityPriceForecasts[bestSellHour] < economicPotential) {
				double toPurchaseAndSell = Math.min(getUnusedResPotential(bestSellHour), getRemainingPowerInMW(bestBuyHour));
				purchasedElectricityInMWH[bestBuyHour] += toPurchaseAndSell;
				electricDemandOfElectrolysisInMW[bestBuyHour] += toPurchaseAndSell;
				bidPricesInEURperMWH[bestBuyHour] = hydrogenSaleOpportunityCostsPerElectricMWH[bestBuyHour];
				purchasedElectricityInMWH[bestSellHour] -= toPurchaseAndSell;
				bidPricesInEURperMWH[bestSellHour] = -economicPotential;
			} else {
				break;
			}
			bestBuyHour = getHourWithHighestEconomicPotential(endOfHorizon);
			bestSellHour = getHourWithLowestSaleCosts(endOfHorizon);
		}
	}

	/** @returns hour with the highest negative price (i.e. smallest absolute value) of those that still have unused renewable
	 *          electricity production capacity, -1 if no such hour exists */
	private int getHourWithLowestSaleCosts(int endOfHorizon) {
		int bestHour = -1;
		double bestPrice = -Double.MAX_VALUE;
		for (int hour = 0; hour < endOfHorizon; hour++) {
			if (electricityPriceForecasts[hour] < 0 && getUnusedResPotential(hour) > 0) {
				if (electricityPriceForecasts[hour] > bestPrice) {
					bestPrice = electricityPriceForecasts[hour];
					bestHour = hour;
				}
			}
		}
		return bestHour;
	}

	/** @return green electricity production potential in given hour that is not yet used by electrolyzer or sold at market */
	private double getUnusedResPotential(int hour) {
		double yieldPotential = ppaInformationForecasts[hour].yieldPotentialInMWH;
		return Math.max(0, Math.min(yieldPotential, yieldPotential - electricDemandOfElectrolysisInMW[hour]
				+ purchasedElectricityInMWH[hour]));
	}

	/** Curtail renewable generation and buy at exchange for negative prices below the negative of the PPA price */
	private void rescheduleForExtremelyNegativePrices(TimePeriod firstPeriod) {
		int endOfHorizon = calcNumberOfPlanningSteps(firstPeriod);
		double netSurplus = calcNetGreenElectricitySurplus(firstPeriod);
		netSurplus = curtailWithNetSurplus(netSurplus, endOfHorizon, firstPeriod);
		if (netSurplus <= TOLERANCE) {
			curtailWithoutNetSurplus(endOfHorizon);
		}
	}

	/** Curtails green electricity production in hours with very low prices and buys green electricity instead */
	private double curtailWithNetSurplus(double netSurplus, int endOfHorizon, TimePeriod firstPeriod) {
		while (netSurplus > 0) {
			int bestHour = getBestCurtailmentHour(endOfHorizon);
			if (bestHour < 0) {
				break;
			}
			double usedResPotential = getUsedResPotentialFor(bestHour);
			double curtailedEnergy = Math.min(usedResPotential, netSurplus);
			purchasedElectricityInMWH[bestHour] += curtailedEnergy;
			// TODO: Need multiple bids with different price limits
			bidPricesInEURperMWH[bestHour] = hydrogenSaleOpportunityCostsPerElectricMWH[bestHour];
			netSurplus = calcNetGreenElectricitySurplus(firstPeriod);
		}
		return netSurplus;
	}

	/** @returns hour with highest difference between electricity price and negative PPA price, -1 if no such hour exists */
	private int getBestCurtailmentHour(int endOfHorizon) {
		int bestHour = -1;
		double bestPriceDifference = 0;
		for (int hour = 0; hour < endOfHorizon; hour++) {
			double priceDifference = getCurtailmentPriceDifferenceFor(hour);
			if (priceDifference > 0 && getUsedResPotentialFor(hour) > 0) {
				if (priceDifference > bestPriceDifference) {
					bestPriceDifference = priceDifference;
					bestHour = hour;
				}
			}
		}
		return bestHour;
	}

	/** @return price difference between curtailment of already purchased electricity from PPA partner and the spot market */
	private double getCurtailmentPriceDifferenceFor(int hour) {
		return -electricityPriceForecasts[hour] - ppaInformationForecasts[hour].priceInEURperMWH;
	}

	/** @return green electricity production potential used by either the electrolyzer or sold at market */
	private double getUsedResPotentialFor(int hour) {
		return ppaInformationForecasts[hour].yieldPotentialInMWH - getUnusedResPotential(hour);
	}

	/** Curtails green electricity production in hours with very low prices and reduces hydrogen production in other hours for
	 * compensation */
	private void curtailWithoutNetSurplus(int endOfHorizon) {
		int bestCurtailmentHour = getBestCurtailmentHour(endOfHorizon);
		int lowestEconomicPotentialHour = getHourWithLowestEconomicPotential(endOfHorizon);
		while (bestCurtailmentHour >= 0 && lowestEconomicPotentialHour >= 0) {
			double purchasedElectricity = purchasedElectricityInMWH[lowestEconomicPotentialHour];
			double usedRenewableElectricity = getUsedResPotentialFor(bestCurtailmentHour);
			double rescheduledAmount = Math.min(purchasedElectricity, usedRenewableElectricity);
			purchasedElectricityInMWH[lowestEconomicPotentialHour] -= rescheduledAmount;
			electricDemandOfElectrolysisInMW[lowestEconomicPotentialHour] -= rescheduledAmount;
			purchasedElectricityInMWH[bestCurtailmentHour] += rescheduledAmount;
			// TODO: Need multiple bids with different price limits
			bidPricesInEURperMWH[bestCurtailmentHour] = hydrogenSaleOpportunityCostsPerElectricMWH[bestCurtailmentHour];
			bestCurtailmentHour = getBestCurtailmentHour(endOfHorizon);
			lowestEconomicPotentialHour = getHourWithLowestEconomicPotential(endOfHorizon);
		}
	}

	/** @param endOfHorizon last step of planning horizon
	 * @return Hour with lowest, but positive economic potential among those with scheduled purchase; -1 if no such hour exists */
	protected int getHourWithLowestEconomicPotential(int endOfHorizon) {
		int bestHour = -1;
		double lowestPotential = Double.MAX_VALUE;
		for (int hour = 0; hour < endOfHorizon; hour++) {
			double economicPotential = getEconomicHydrogenPotential(hour);
			if (economicPotential < lowestPotential && purchasedElectricityInMWH[hour] > 0) {
				lowestPotential = economicPotential;
				bestHour = hour;
			}
		}
		return bestHour;
	}

	/** transfer optimised dispatch to schedule arrays */
	private void updateScheduleArrays(double greenElectricitySurplus) {
		for (int hour = 0; hour < scheduleDurationPeriods; hour++) {
			demandScheduleInMWH[hour] = purchasedElectricityInMWH[hour];
			priceScheduleInEURperMWH[hour] = bidPricesInEURperMWH[hour];
			scheduledGreenElectricitySurplus[hour] = greenElectricitySurplus;
			greenElectricitySurplus -= purchasedElectricityInMWH[hour];
		}
	}

	@Override
	public void updateProducedHydrogenTotal(double producedHydrogenInMWH) {
		throw new RuntimeException(ERR_NOT_INTENDED + this.getClass().getSimpleName());
	}

	/** Adds the given green electricity surplus (or deficit if negative) to the running total of the current months green
	 * electricity surplus
	 * 
	 * @param greenElectricitySurplusInMWH amount of green electricity surplus to add */
	public void updateGreenElectricitySurplus(double greenElectricitySurplusInMWH) {
		greenElectricitySurplusTotal += greenElectricitySurplusInMWH;
	}

	/** Resets the current status of the Strategist to a new month: deletes the schedule and resets the green electricity surplus to
	 * Zero
	 * 
	 * @param beginOfNextMonth first TimeStamp at the beginning of the next month */
	public void resetMonthly(TimeStamp beginOfNextMonth) {
		schedule = null;
		greenElectricitySurplusTotal = 0;
		lastTimePeriodInCurrentMonth = new TimePeriod(beginOfNextMonth.earlierBy(OPERATION_PERIOD), OPERATION_PERIOD);
	}

	/** Replaces internal energy schedule: the relevant state to monitor in the schedule is the green electricity surplus */
	@Override
	protected double[] getInternalEnergySchedule() {
		return scheduledGreenElectricitySurplus;
	}
}
