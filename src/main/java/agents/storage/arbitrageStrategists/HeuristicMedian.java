// SPDX-FileCopyrightText: 2023-2026 German Aerospace Center <amiris@dlr.de>
//
// SPDX-License-Identifier: Apache-2.0
package agents.storage.arbitrageStrategists;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.TreeMap;
import agents.flexibility.BidSchedule;
import agents.flexibility.GenericDevice;
import agents.flexibility.GenericDeviceCache;
import communications.message.PointInTime;
import communications.portable.Sensitivity;
import communications.portable.Sensitivity.InterpolationType;
import de.dlr.gitlab.fame.communication.message.Message;
import de.dlr.gitlab.fame.time.TimePeriod;
import de.dlr.gitlab.fame.time.TimeStamp;
import util.Polynomial;
import util.Util;

public class HeuristicMedian {
	/** Denotes actions in corresponding sections of the planning interval */
	private enum State {
		CHARGE, DISCHARGE, IDLE
	}

	private final int forecastSteps;
	private final int schedulePeriods;
	private final Polynomial assessmentFunction;
	private final GenericDevice device;
	private final GenericDeviceCache deviceCache;

	private final TreeMap<TimeStamp, Double> forecasts = new TreeMap<>();

	private final double[] forecastPrices;
	private final double[] internalChargingPowersInMW;
	private final double[] assessmentValues;

	private double[] demandScheduleInMWH;
	private double[] scheduledBidPricesInEURperMWH;
	private double[] scheduledInitialInternalEnergyInMWH;

	private double priceMedian;
	private double maxChargePrice;
	private double minDischargePrice;

	/** Instantiate a {@link HeuristicMedian} strategy
	 * 
	 * @param assessmentfunction polynomial to assess price differences
	 * @param device to find dispatch strategies for
	 * @param forecastPeriods number of time periods of the forecast
	 * @param schedulePeriods number of time periods of the schedule */
	public HeuristicMedian(Polynomial assessmentfunction, GenericDevice device, int forecastPeriods,
			int schedulePeriods) {
		this.forecastSteps = forecastPeriods;
		this.schedulePeriods = schedulePeriods;
		this.assessmentFunction = assessmentfunction;
		this.device = device;
		this.deviceCache = new GenericDeviceCache(device);

		forecastPrices = new double[forecastPeriods];
		internalChargingPowersInMW = new double[forecastPeriods];
		assessmentValues = new double[forecastPeriods];

		demandScheduleInMWH = new double[schedulePeriods];
		scheduledBidPricesInEURperMWH = new double[schedulePeriods];
		scheduledInitialInternalEnergyInMWH = new double[schedulePeriods];
	}

	/** Return list of time stamps at which additional information is required, based on time of next clearing event
	 * 
	 * @param nextTime time of next clearing event
	 * @return list of time stamp which require additional information */
	public ArrayList<TimeStamp> getMissingForecastTimes(TimePeriod nextTime) {
		ArrayList<TimeStamp> missingTimes = new ArrayList<>();
		for (int shiftIndex = 0; shiftIndex < forecastSteps; shiftIndex++) {
			TimeStamp time = nextTime.shiftByDuration(shiftIndex).getStartTime();
			if (!forecasts.containsKey(time)) {
				missingTimes.add(time);
			}
		}
		return missingTimes;
	}

	/** Store forecasts for dispatch assessment
	 * 
	 * @param messages to be scraped for forecast data */
	public void storeForecast(ArrayList<Message> messages) {
		for (Message inputMessage : messages) {
			Sensitivity sensitivity = inputMessage.getAllPortableItemsOfType(Sensitivity.class).get(0);
			sensitivity.setInterpolationType(InterpolationType.DIRECT);
			sensitivity.updateMultiplier(1.0);
			TimeStamp time = inputMessage.getDataItemOfType(PointInTime.class).validAt;
			forecasts.put(time, sensitivity.getValue(1.));
		}
	}

	/** Clear entries of forecasts before given time
	 * 
	 * @param time before which elements are cleared */
	public void clearBefore(TimeStamp time) {
		forecasts.headMap(time).clear();
	}

	public BidSchedule createSchedule(TimePeriod timePeriod) {
		updateSchedule(timePeriod);
		BidSchedule schedule = new BidSchedule(timePeriod, schedulePeriods);
		schedule.setBidsScheduleInEURperMWH(scheduledBidPricesInEURperMWH);
		schedule.setRequestedEnergyPerPeriod(demandScheduleInMWH);
		schedule.setExpectedInitialInternalEnergyScheduleInMWH(scheduledInitialInternalEnergyInMWH);
		return schedule;
	}

	public void updateSchedule(TimePeriod timePeriod) {
		deviceCache.setPeriod(timePeriod);
		deviceCache.prepareFor(timePeriod.getStartTime());
		updatePriceForecast(timePeriod);
		updateBidPriceLimits(timePeriod.getStartTime());
		updateAssessmentValues(priceMedian);
		Arrays.fill(internalChargingPowersInMW, 0);
		double initialEnergyInStorageInMWh = device.getCurrentInternalEnergyInMWH();
		optimiseDispatch(initialEnergyInStorageInMWh);
		updateScheduleArrays(initialEnergyInStorageInMWh);
	}

	/** updates {@link #forecastPrices} based on forecasted prices */
	private void updatePriceForecast(TimePeriod firstPeriod) {
		for (int period = 0; period < forecastSteps; period++) {
			TimePeriod timePeriod = firstPeriod.shiftByDuration(period);
			forecastPrices[period] = getPriceForecastOrZero(timePeriod.getStartTime());
		}
	}

	/** @return actual price forecast, if existing, or Zero in any other case */
	private double getPriceForecastOrZero(TimeStamp time) {
		if (forecasts.containsKey(time)) {
			return forecasts.get(time);
		} else {
			return 0.;
		}
	}

	/** Set median price, maximum charge price and minimum discharge price, considering losses for charging & discharging */
	private void updateBidPriceLimits(TimeStamp time) {
		priceMedian = Util.calcMedian(forecastPrices);
		double roundTripEfficiency = deviceCache.getRoundTripEfficiency();
		double lossMargin = priceMedian * (1 - roundTripEfficiency) / (1 + roundTripEfficiency);
		maxChargePrice = priceMedian - lossMargin;
		minDischargePrice = priceMedian + lossMargin;
	}

	/** updates {@link MultiAgentMedian#assessmentValues} based on the current forecasted prices */
	private void updateAssessmentValues(double priceMedian) {
		for (int period = 0; period < forecastSteps; period++) {
			double priceDeltaToMarginal;
			if (forecastPrices[period] < maxChargePrice) {
				priceDeltaToMarginal = maxChargePrice - forecastPrices[period];
				assessmentValues[period] = assessmentFunction.evaluateAt(priceDeltaToMarginal);
			} else if (forecastPrices[period] > minDischargePrice) {
				priceDeltaToMarginal = forecastPrices[period] - minDischargePrice;
				assessmentValues[period] = assessmentFunction.evaluateAt(priceDeltaToMarginal);
			} else {
				assessmentValues[period] = 0;
			}
			assessmentValues[period] = Math.max(0, assessmentValues[period]);
		}
	}

	/** Distributes powers proportional to assessment values */
	private void optimiseDispatch(double initialEnergyInStorageInMWh) {
		double maxInternalEnergyInMWH = deviceCache.getEnergyContentUpperLimitInMWH();
		double minInternalEnergyInMWH = deviceCache.getEnergyContentLowerLimitInMWH();
		double energyLevelInMWH = initialEnergyInStorageInMWh;
		int intervalBegin = 0;
		State previousState = calcStateInPeriod(0, priceMedian);
		for (int period = 1; period < forecastSteps; period++) {
			State state = calcStateInPeriod(period, priceMedian);
			if (isFinalisingInterval(previousState, state) || isLastElementInForecast(period)) {
				if (previousState == State.CHARGE) {
					double energyToCharge = maxInternalEnergyInMWH - energyLevelInMWH;
					energyLevelInMWH += updateChargeSchedule(energyToCharge, intervalBegin, period);
				} else if (previousState == State.DISCHARGE) {
					double energyToDischarge = energyLevelInMWH - minInternalEnergyInMWH;
					energyLevelInMWH += updateChargeSchedule(-energyToDischarge, intervalBegin, period);
				}
				previousState = state;
				intervalBegin = period;
			}
		}
	}

	/** @return planned state of action in the given hour, based on {@link MultiAgentMedian#assessmentValues} */
	private State calcStateInPeriod(int period, double priceMedian) {
		if (forecastPrices[period] < priceMedian && assessmentValues[period] > 0) {
			return State.CHARGE;
		} else if (forecastPrices[period] > priceMedian && assessmentValues[period] > 0) {
			return State.DISCHARGE;
		} else {
			return State.IDLE;
		}
	}

	/** @return true if a specified action interval has ended */
	private boolean isFinalisingInterval(State previousState, State state) {
		switch (previousState) {
			case IDLE:
				return state != State.IDLE;
			case CHARGE:
				return state == State.DISCHARGE;
			case DISCHARGE:
				return state == State.CHARGE;
			default:
				throw new RuntimeException("Action state not implemented.");
		}
	}

	/** @return true if the specified element is the last one of the forecast interval */
	private boolean isLastElementInForecast(int element) {
		return element + 1 == forecastSteps;
	}

	/** Distributes specified energy to (dis-)charge proportional to assessmentValues in the given periods.
	 * 
	 * @return total (dis-)charged energy */
	private double updateChargeSchedule(double energyToCharge, int firstElement, int elementAfterLastElement) {
		double maxChargingPowerInMW = energyToCharge > 0 ? deviceCache.getMaxNetChargingEnergyInMWH()
				: -deviceCache.getMaxNetDischargingEnergyInMWH();
		double remainingEnergyToCharge = energyToCharge;

		distributionLoop: while ((remainingEnergyToCharge * energyToCharge) > 0) {
			double sumOfWeights = 0;
			for (int element = firstElement; element < elementAfterLastElement; element++) {
				if (Math.abs(internalChargingPowersInMW[element]) < maxChargingPowerInMW) {
					sumOfWeights += assessmentValues[element];
				}
			}
			if (sumOfWeights == 0) {
				break distributionLoop;
			}
			double energyPerWeight = remainingEnergyToCharge / sumOfWeights;
			for (int element = firstElement; element < elementAfterLastElement; element++) {
				if (Math.abs(internalChargingPowersInMW[element]) < maxChargingPowerInMW) {
					double chargingPowerInMW = energyPerWeight * assessmentValues[element] + internalChargingPowersInMW[element];
					chargingPowerInMW = Math.max(-maxChargingPowerInMW, Math.min(maxChargingPowerInMW, chargingPowerInMW));
					internalChargingPowersInMW[element] = chargingPowerInMW;
					remainingEnergyToCharge -= chargingPowerInMW;
				}
			}
		}

		double totalChargedEnergy = 0;
		for (int element = firstElement; element < elementAfterLastElement; element++) {
			totalChargedEnergy += internalChargingPowersInMW[element];
		}
		return totalChargedEnergy;
	}

	/** For scheduling period: updates arrays for expected initial energy levels, (dis-)charging power & bidding prices based on the
	 * planned dispatch */
	private void updateScheduleArrays(double currentInternalEnergyInMWh) {
		for (int element = 0; element < schedulePeriods; element++) {
			double nextInternalEnergyInMWH = currentInternalEnergyInMWh + internalChargingPowersInMW[element];
			nextInternalEnergyInMWH = ensureWithinEnergyBounds(nextInternalEnergyInMWH);
			double availableEnergyDeltaInMWH = nextInternalEnergyInMWH - currentInternalEnergyInMWh;
			double externalEnergyInMWH = deviceCache.internalToExternalEnergy(availableEnergyDeltaInMWH);
			demandScheduleInMWH[element] = externalEnergyInMWH;
			if (externalEnergyInMWH < 0) {
				scheduledBidPricesInEURperMWH[element] = minDischargePrice;
			} else if (externalEnergyInMWH > 0) {
				scheduledBidPricesInEURperMWH[element] = maxChargePrice;
			} else {
				scheduledBidPricesInEURperMWH[element] = forecastPrices[element];
			}
			scheduledInitialInternalEnergyInMWH[element] = currentInternalEnergyInMWh;
			currentInternalEnergyInMWh = nextInternalEnergyInMWH;
		}
	}

	/** Corrects given internal energy value if it is below Zero or above maximum capacity.
	 * 
	 * @param internalEnergyInMWH to be corrected (if necessary)
	 * @return internal energy value that is secured to lie within storage bounds */
	protected double ensureWithinEnergyBounds(double internalEnergyInMWH) {
		return Math.max(deviceCache.getEnergyContentLowerLimitInMWH(),
				Math.min(deviceCache.getEnergyContentUpperLimitInMWH(), internalEnergyInMWH));
	}
}