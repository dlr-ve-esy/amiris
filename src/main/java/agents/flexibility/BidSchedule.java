// SPDX-FileCopyrightText: 2021-2026 German Aerospace Center <amiris@dlr.de>
//
// SPDX-License-Identifier: Apache-2.0
package agents.flexibility;

import de.dlr.gitlab.fame.time.TimePeriod;
import de.dlr.gitlab.fame.time.TimeSpan;
import de.dlr.gitlab.fame.time.TimeStamp;

/** Represents a bidding schedule for purchasing / selling energy "stored" in a flexibility device
 * 
 * @author Christoph Schimeczek */
public class BidSchedule {
	static final String ERROR_OVERRIDE = "Arrays in schedule may not be overridden.";
	static final String ERROR_LENGTH = "Array length does not match schedule duration count in periods.";

	private static final double MAX_ABSOLUTE_ENERGY_DEVIATION_IN_MWH = 1.E-3;

	private final TimeStamp timeOfFirstElement;
	private final int durationInPeriods;
	private final TimeSpan period;

	/** Energy amount to be traded at electricity market - positive values: purchasing, negative values: selling */
	private double[] requestedEnergyPerPeriodInMWH;
	/** Bidding price of the buy / sell offer */
	private double[] biddingPricePerPeriodInEURperMWH;
	/** Energy level of the flexibility device expected at each period */
	protected double[] expectedInitialInternalEnergyPerPeriodInMWH;
	/** Expected electricity prices based on forecast and flexibility dispatch */
	protected double[] expectedElectricityPricesInEURperMWH;

	/** Creates a {@link BidSchedule}
	 * 
	 * @param timePeriod defines first time at which this schedule is valid and length of each period
	 * @param durationInPeriods number of time periods covered by this schedule, i.e. multiples of timeSegment durations */
	public BidSchedule(TimePeriod timePeriod, int durationInPeriods) {
		this.timeOfFirstElement = timePeriod.getStartTime();
		this.durationInPeriods = durationInPeriods;
		this.period = timePeriod.getDuration();
	}

	/** Sets amount of energy requested at electricity market - positive values: purchasing, negative values: selling
	 * 
	 * @param requestedEnergyPerPeriodInMWH to be saved
	 * @throws RuntimeException if this value was set previously or if its length does not match {@link #durationInPeriods} */
	public void setRequestedEnergyPerPeriod(double[] requestedEnergyPerPeriodInMWH) {
		ensureIsNull(this.requestedEnergyPerPeriodInMWH);
		ensureCorrectLength(requestedEnergyPerPeriodInMWH);
		this.requestedEnergyPerPeriodInMWH = requestedEnergyPerPeriodInMWH.clone();
	}

	/** @throws RuntimeException if given object is not null */
	private void ensureIsNull(double[] object) {
		if (object != null) {
			throw new RuntimeException(ERROR_OVERRIDE);
		}
	}

	/** @throws RuntimeException if given array's length is not equal to {@link #durationInPeriods} */
	private void ensureCorrectLength(double[] object) {
		if (object.length != durationInPeriods) {
			throw new RuntimeException(ERROR_LENGTH);
		}
	}

	/** Sets bidding price at electricity market
	 * 
	 * @param biddingPricePerPeriodInEURperMWH bidding schedule
	 * @throws RuntimeException if this value was set previously or if its length does not match {@link #durationInPeriods} */
	public void setBidsScheduleInEURperMWH(double[] biddingPricePerPeriodInEURperMWH) {
		ensureIsNull(this.biddingPricePerPeriodInEURperMWH);
		ensureCorrectLength(biddingPricePerPeriodInEURperMWH);
		this.biddingPricePerPeriodInEURperMWH = biddingPricePerPeriodInEURperMWH.clone();
	}

	/** Sets energy level of the flexibility device expected at each period
	 * 
	 * @param expectedInitialInternalEnergyPerPeriodInMWH initial energy schedule
	 * @throws RuntimeException if this value was set previously or if its length does not match {@link #durationInPeriods} */
	public void setExpectedInitialInternalEnergyScheduleInMWH(double[] expectedInitialInternalEnergyPerPeriodInMWH) {
		ensureIsNull(this.expectedInitialInternalEnergyPerPeriodInMWH);
		ensureCorrectLength(expectedInitialInternalEnergyPerPeriodInMWH);
		this.expectedInitialInternalEnergyPerPeriodInMWH = expectedInitialInternalEnergyPerPeriodInMWH.clone();
	}

	/** Set expected electricity price depending on the forecasted merit order and flexibility device dispatch
	 * 
	 * @param expectedElectricityPricesInEURperMWH prices expected when dispatching
	 * @throws RuntimeException if this value was set previously or if its length does not match {@link #durationInPeriods} */
	public void setExpectedElectricityPricesInEURperMWH(double[] expectedElectricityPricesInEURperMWH) {
		ensureIsNull(this.expectedElectricityPricesInEURperMWH);
		ensureCorrectLength(expectedElectricityPricesInEURperMWH);
		this.expectedElectricityPricesInEURperMWH = expectedElectricityPricesInEURperMWH.clone();
	}

	/** Returns true if this schedule is defined for the given time, and if the given energy level matches the scheduled energy one
	 * 
	 * @param time TimeStamp which the schedule is checked for
	 * @param currentEnergyLevelInMWH of connected flexibility device to be compared to the planned energy level at given time
	 * @return true if the schedule is applicable at the specified time and matches the given energy in storage */
	public boolean isApplicable(TimeStamp time, double currentEnergyLevelInMWH) {
		boolean isWithinTimeFrame = isWithinTimeFrame(time);
		boolean matchesTimeElement = matchesBeginningOfTimeElement(time);
		return isWithinTimeFrame && matchesTimeElement && energyLevelWithinTolerance(time, currentEnergyLevelInMWH);
	}

	/** @return true if given {@link TimeStamp} is within the time frame of this schedule */
	private boolean isWithinTimeFrame(TimeStamp time) {
		int periodShiftCount = (durationInPeriods - 1);
		TimeSpan shiftDuration = new TimeSpan(periodShiftCount * period.getSteps());
		TimeStamp latestTime = timeOfFirstElement.laterBy(shiftDuration);
		return time.isGreaterEqualTo(timeOfFirstElement) && time.isLessEqualTo(latestTime);
	}

	/** @return true if given {@link TimeStamp} matches any scheduling element beginning of this schedule exactly */
	private boolean matchesBeginningOfTimeElement(TimeStamp time) {
		long timeDelta = time.getStep() - timeOfFirstElement.getStep();
		return timeDelta % period.getSteps() == 0;
	}

	/** @return true if the schedule is applicable to the specified hour with the specified energy in storage
	 * @param time for which to check the expected energy level
	 * @param storageEnergyLevelInMWH actual energy level to be compared with the planned one */
	protected boolean energyLevelWithinTolerance(TimeStamp time, double storageEnergyLevelInMWH) {
		double plannedEnergyInStorage = expectedInitialInternalEnergyPerPeriodInMWH[calcElementInSchedule(time)];
		double absoluteDeviation = Math.abs(plannedEnergyInStorage - storageEnergyLevelInMWH);
		return absoluteDeviation < MAX_ABSOLUTE_ENERGY_DEVIATION_IN_MWH;
	}

	/** @return element number in this {@link BidSchedule} corresponding to the given {@link TimeStamp}
	 * @param time for which to search the element */
	protected int calcElementInSchedule(TimeStamp time) {
		return (int) ((time.getStep() - timeOfFirstElement.getStep()) / period.getSteps());
	}

	/** Returns energy to be purchased at the market - returns 0 if no electricity shall be purchased
	 * 
	 * @param time must match schedule time element, use {@link #isApplicable(TimeStamp, double)} to check for validity
	 * @return positive amount of energy to be purchased in MWh at the specified time */
	public double getScheduledEnergyPurchaseInMWH(TimeStamp time) {
		double energyPurchaseAmountInMWH = requestedEnergyPerPeriodInMWH[calcElementInSchedule(time)];
		return energyPurchaseAmountInMWH > 0 ? energyPurchaseAmountInMWH : 0;
	}

	/** Returns energy to be sold at the market - returns 0 if no electricity shall be sold
	 * 
	 * @param time must match schedule time element, use {@link #isApplicable(TimeStamp, double)} to check for validity
	 * @return positive amount of energy to be sold in MWh at the specified time */
	public double getScheduledEnergySalesInMWH(TimeStamp time) {
		double energyPurchaseAmountInMWH = requestedEnergyPerPeriodInMWH[calcElementInSchedule(time)];
		return energyPurchaseAmountInMWH < 0 ? -energyPurchaseAmountInMWH : 0;
	}

	/** Returns bidding price for purchasing and selling energy
	 * 
	 * @param time must match schedule time element, use {@link #isApplicable(TimeStamp, double)} to check for validity
	 * @return bidding price in EUR / MWh for the specified time */
	public double getScheduledBidInHourInEURperMWH(TimeStamp time) {
		return biddingPricePerPeriodInEURperMWH[calcElementInSchedule(time)];
	}

	/** Returns beginning time of this {@link BidSchedule}
	 * 
	 * @return start time */
	public TimeStamp getTimeOfFirstElement() {
		return timeOfFirstElement;
	}

	/** Returns expected electricity price depending on the forecasted merit order and flexibility device dispatch, if set. Returns
	 * NaN in case no electricity price expectation values are set.
	 * 
	 * @param time must match schedule time element, use {@link #isApplicable(TimeStamp, double)} to check for validity
	 * @return expected electricity price */
	public double getExpectedElectricityPricesInEURperMWH(TimeStamp time) {
		if (expectedElectricityPricesInEURperMWH != null) {
			return expectedElectricityPricesInEURperMWH[calcElementInSchedule(time)];
		}
		return Double.NaN;
	}
}