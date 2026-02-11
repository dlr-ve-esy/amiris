// SPDX-FileCopyrightText: 2024-2026 German Aerospace Center <amiris@dlr.de>
//
// SPDX-License-Identifier: Apache-2.0
package agents.loadShifting.strategists;

import agents.flexibility.BidSchedule;
import agents.loadShifting.LoadShiftingPortfolio;
import agents.markets.meritOrder.Constants;
import agents.markets.meritOrder.sensitivities.MeritOrderSensitivity;
import agents.storage.arbitrageStrategists.FileDispatcher;
import de.dlr.gitlab.fame.agent.input.Make;
import de.dlr.gitlab.fame.agent.input.ParameterData;
import de.dlr.gitlab.fame.agent.input.ParameterData.MissingDataException;
import de.dlr.gitlab.fame.agent.input.Tree;
import de.dlr.gitlab.fame.data.TimeSeries;
import de.dlr.gitlab.fame.time.TimePeriod;
import de.dlr.gitlab.fame.time.TimeStamp;

/** Creates {@link BidSchedule}s from file for a connected loadShiftingPortfolio {@link LoadShiftingPortfolio}
 *
 * @author Johannes Kochems, Christoph Schimeczek */
public class ShiftFileDispatcher extends LoadShiftingStrategist {
	/** Input for the {@link FileDispatcher} */
	public static final Tree parameters = Make.newTree()
			.add(Make.newSeries("EnergySchedule").optional().help(
					"Change of load shifting portfolio energy storage level relative to available charging power. Values should be -1 <= x <= 1."),
					Make.newSeries("ShiftTimeSchedule").optional()
							.help("Change of current shifting time indicating how long a load has already been shifted for."))
			.buildTree();

	static final String WARN_SUSPICIOUS_ENERGY_DISPATCH = "Warning:: LoadShiftingPortfolio violates energy bounds:: Dispatch file may be not suitable";
	static final String WARN_SUSPICIOUS_SHIFT_TIMES = "Warning:: LoadShiftingPortfolio violates shift time restrictions:: Dispatch file may be not suitable";
	static final String WARN_SUSPICIOUS_POWER_DISPATCH = "Warning:: LoadShiftingPortfolio violates power bounds:: Dispatch file may be not suitable";

	private static final double ABSOLUTE_TOLERANCE_IN_MWH = 0.1;

	private TimeSeries tsEnergyDispatch;
	private TimeSeries tsShiftTimes;

	/** Instantiate {@link ShiftFileDispatcher}
	 * 
	 * @param generalInput parameters associated with strategists in general
	 * @param specificInput for {@link ShiftFileDispatcher}
	 * @param loadShiftingPortfolio for which schedules are to be created
	 * @throws MissingDataException if any required input is missing */
	public ShiftFileDispatcher(ParameterData generalInput, ParameterData specificInput,
			LoadShiftingPortfolio loadShiftingPortfolio) throws MissingDataException {
		super(generalInput, specificInput, loadShiftingPortfolio);
		this.tsEnergyDispatch = specificInput.getTimeSeries("EnergySchedule");
		this.tsShiftTimes = specificInput.getTimeSeries("ShiftTimeSchedule");
	}

	/** No {@link MeritOrderSensitivity} needed for {@link ShiftFileDispatcher}, as dispatch is read from file */
	@Override
	protected MeritOrderSensitivity createBlankSensitivity() {
		return null;
	}

	/** Not needed for {@link ShiftFileDispatcher} */
	@Override
	protected void updateSchedule(TimePeriod startPeriod, double currentEnergyShiftStorageLevelInMWH,
			int currentShiftTime) {}

	/** @return {@link BidSchedule schedule} for the connected {@link LoadShiftingPortfolio loadShiftingPortfolio} for the specified
	 *         simulation hour **/
	@Override
	public BidSchedule createSchedule(TimePeriod timePeriod, double currentEnergyShiftStorageLevelInMWH,
			int currentShiftTime) {
		for (int element = 0; element < scheduleDurationPeriods; element++) {
			final TimeStamp planningTime = timePeriod.shiftByDuration(element).getStartTime();
			final double chargePowerInMW = calcChargingPowerAt(planningTime);
			final int currentShiftTimeInHours = (int) Math.round(tsShiftTimes.getValueEarlierEqual(planningTime));

			demandScheduleInMWH[element] = chargePowerInMW;
			scheduledInitialEnergyInMWH[element] = currentEnergyShiftStorageLevelInMWH;
			currentEnergyShiftStorageLevelInMWH += chargePowerInMW;
			issueWarningIfOutsideBounds(currentEnergyShiftStorageLevelInMWH, currentShiftTimeInHours, chargePowerInMW,
					planningTime);
			currentEnergyShiftStorageLevelInMWH = ensureWithinBounds(currentEnergyShiftStorageLevelInMWH);
			setBidPrice(element, chargePowerInMW);
		}
		return buildSchedule(timePeriod);
	}

	/** @return internal charging power in the dispatch file at the given {@link TimeStamp} */
	private double calcChargingPowerAt(TimeStamp planningTime) {
		final double relativeChargePower = tsEnergyDispatch.getValueLinear(planningTime);
		return portfolio.getPowerInMW() * relativeChargePower;
	}

	/** prints a warning message if loadShiftingPortfolio is either significantly outside its energy or shift time constraints */
	private void issueWarningIfOutsideBounds(double currentEnergyShiftStorageLevelInMWH, int currentShiftTime,
			double chargePowerInMW, TimeStamp planningTime) {
		if (isOutsideEnergyTolerance(currentEnergyShiftStorageLevelInMWH)) {
			System.out.println(WARN_SUSPICIOUS_ENERGY_DISPATCH);
		}
		if (isOutsideShiftTimeBounds(currentShiftTime)) {
			System.out.println(WARN_SUSPICIOUS_SHIFT_TIMES);
		}
		if (isOutsidePowerTolerance(chargePowerInMW, planningTime)) {
			System.out.println(WARN_SUSPICIOUS_POWER_DISPATCH);
		}
	}

	/** @return true if the {@link LoadShiftingPortfolio loadShiftingPortfolio} is operated outside its energy constraints by more
	 *         than {@link #ABSOLUTE_TOLERANCE_IN_MWH} */
	private boolean isOutsideEnergyTolerance(double currentEnergyShiftStorageLevelInMWH) {
		final double energyLimitDownInMWH = portfolio.getEnergyLimitDownInMWH();
		final double energyLimitUpInMWH = portfolio.getEnergyLimitUpInMWH();
		final boolean belowLowerLimit = currentEnergyShiftStorageLevelInMWH < -energyLimitDownInMWH
				- ABSOLUTE_TOLERANCE_IN_MWH;
		final boolean aboveUpperLimit = currentEnergyShiftStorageLevelInMWH > energyLimitUpInMWH
				+ ABSOLUTE_TOLERANCE_IN_MWH;
		return belowLowerLimit || aboveUpperLimit;
	}

	/** @return true if the {@link LoadShiftingPortfolio loadShiftingPortfolio} is operated outside its shift time limits */
	private boolean isOutsideShiftTimeBounds(int currentShiftTime) {
		final boolean belowZero = currentShiftTime < 0;
		final boolean aboveMaximum = currentShiftTime > portfolio.getMaximumShiftTimeInHours();
		return belowZero || aboveMaximum;
	}

	/** @return true if the {@link LoadShiftingPortfolio loadShiftingPortfolio} is operated outside its power constraints by more
	 *         than {@link #ABSOLUTE_TOLERANCE_IN_MWH} */
	private boolean isOutsidePowerTolerance(double chargePowerInMW, TimeStamp planningTime) {
		final double powerLimitDownInMW = portfolio.getDowerDownAvailabilities().getValueLinear(planningTime)
				* portfolio.getPowerInMW();
		final double powerLimitUpInMW = portfolio.getPowerUpAvailabilities().getValueLinear(planningTime)
				* portfolio.getPowerInMW();
		final boolean belowLowerLimit = chargePowerInMW < -powerLimitDownInMW - ABSOLUTE_TOLERANCE_IN_MWH;
		final boolean aboveUpperLimit = chargePowerInMW > powerLimitUpInMW + ABSOLUTE_TOLERANCE_IN_MWH;
		return belowLowerLimit || aboveUpperLimit;
	}

	/** @return load shifting energy storage level ensured to be within the bounds of the connected {@link LoadShiftingPortfolio} */
	private double ensureWithinBounds(double currentEnergyShiftStorageLevelInMWH) {
		final double energyLimitDownInMWH = portfolio.getEnergyLimitDownInMWH();
		final double energyLimitUpInMWH = portfolio.getEnergyLimitUpInMWH();
		return Math.max(-energyLimitDownInMWH, Math.min(energyLimitUpInMWH, currentEnergyShiftStorageLevelInMWH));
	}

	/** sets bidding price for given time element and charging power */
	private void setBidPrice(int element, double chargePowerInMW) {
		if (chargePowerInMW > 0) {
			scheduledBidPricesInEURperMWH[element] = Constants.SCARCITY_PRICE_IN_EUR_PER_MWH;
		} else if (chargePowerInMW < 0) {
			scheduledBidPricesInEURperMWH[element] = Constants.MINIMAL_PRICE_IN_EUR_PER_MWH;
		} else {
			scheduledBidPricesInEURperMWH[element] = 0;
		}
	}

	/** @return {@link BidSchedule} for the given time period created from prepared Bid arrays */
	private BidSchedule buildSchedule(TimePeriod timePeriod) {
		final BidSchedule schedule = new BidSchedule(timePeriod, scheduleDurationPeriods);
		schedule.setBidsScheduleInEURperMWH(scheduledBidPricesInEURperMWH);
		schedule.setRequestedEnergyPerPeriod(demandScheduleInMWH);
		schedule.setExpectedInitialInternalEnergyScheduleInMWH(scheduledInitialEnergyInMWH);
		return schedule;
	}
}
