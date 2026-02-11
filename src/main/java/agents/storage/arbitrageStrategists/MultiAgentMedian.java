// SPDX-FileCopyrightText: 2023-2026 German Aerospace Center <amiris@dlr.de>
//
// SPDX-License-Identifier: Apache-2.0
package agents.storage.arbitrageStrategists;

import java.util.Arrays;
import agents.markets.meritOrder.books.DemandOrderBook;
import agents.markets.meritOrder.books.SupplyOrderBook;
import agents.markets.meritOrder.sensitivities.MeritOrderSensitivity;
import agents.markets.meritOrder.sensitivities.PriceNoSensitivity;
import agents.storage.Device;
import de.dlr.gitlab.fame.agent.input.Make;
import de.dlr.gitlab.fame.agent.input.ParameterData;
import de.dlr.gitlab.fame.agent.input.Tree;
import de.dlr.gitlab.fame.agent.input.ParameterData.MissingDataException;
import de.dlr.gitlab.fame.time.TimePeriod;
import de.dlr.gitlab.fame.time.TimeStamp;
import util.Polynomial;
import util.Util;

/** Creates simple storage dispatch strategies based on an electricity price forecast and its median; Charges whenever the price
 * is below (median - loss_safety) and discharges when prices are above (median + loss_safety). These price limits also resemble
 * the used bid price. Impact of own (dis-)charge is not considered during planning, i.e., large storage operators using this
 * strategy will likely have significant (own) price impacts and might not be able to follow their schedule as planned herein.
 * Thus, many reschedule might be required.
 * 
 * @author Christoph Schimeczek */
public class MultiAgentMedian extends ArbitrageStrategist {
	/** Input parameters for the {@link MultiAgentMedian} strategist */
	public static final Tree parameters = Make.newTree().optional()
			.add(Make.newDouble("AssessmentFunctionPrefactors").optional().list()
					.help("Prefactors `a`,`b`,`c`, ... of a polynomial `a + bx + cx² + ...`, "
							+ "where `x` is the difference between the hourly price and the price median. "
							+ "Higher prefactors favour (dis-)charge at more extreme prices."))
			.buildTree();

	/** Denotes actions in corresponding sections of the planning interval */
	private enum State {
		CHARGE, DISCHARGE, IDLE
	};

	private final Polynomial assessmentFunction;
	private final double[] forecastPrices = new double[forecastSteps];
	private final double[] internalChargingPowersInMW = new double[forecastSteps];
	private final double[] assessmentValues = new double[forecastSteps];
	private double priceMedian;
	private double maxChargePrice;
	private double minDischargePrice;

	/** Creates a {@link MultiAgentMedian} strategist
	 * 
	 * @param generalInput general parameters associated with strategists
	 * @param specificInput specific parameters for this strategist
	 * @param storage device to be optimised
	 * @throws MissingDataException if any required input is missing */
	public MultiAgentMedian(ParameterData generalInput, ParameterData specificInput, Device storage)
			throws MissingDataException {
		super(generalInput, storage);
		this.assessmentFunction = new Polynomial(specificInput.getList("AssessmentFunctionPrefactors", Double.class));
	}

	@Override
	public void updateSchedule(TimePeriod timePeriod) {
		updatePriceForecast(timePeriod);
		updateBidPriceLimits();
		updateAssessmentValues(priceMedian);
		Arrays.fill(internalChargingPowersInMW, 0);
		double initialEnergyInStorageInMWh = storage.getCurrentEnergyInStorageInMWH();
		optimiseDispatch(initialEnergyInStorageInMWh);
		updateScheduleArrays(initialEnergyInStorageInMWh);
	}

	/** updates {@link #forecastPrices} based on forecasted prices */
	private void updatePriceForecast(TimePeriod firstPeriod) {
		for (int period = 0; period < forecastSteps; period++) {
			TimePeriod timePeriod = firstPeriod.shiftByDuration(period);
			forecastPrices[period] = getPriceForecastOrZero(timePeriod);
		}
	}

	/** @return actual price forecast, if existing, or Zero in any other case */
	private double getPriceForecastOrZero(TimePeriod timePeriod) {
		PriceNoSensitivity priceObject = ((PriceNoSensitivity) getSensitivityForPeriod(timePeriod));
		double priceForecast = 0;
		if (priceObject != null && !Double.isNaN(priceObject.getPriceForecast())) {
			priceForecast = priceObject.getPriceForecast();
		}
		return priceForecast;
	}

	/** Set median price, maximum charge price and minimum discharge price, considering losses for charging & discharging */
	private void updateBidPriceLimits() {
		priceMedian = Util.calcMedian(forecastPrices);
		double roundTripEfficiency = storage.getChargingEfficiency() * storage.getDischargingEfficiency();
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
		double maxInternalEnergyInMWH = storage.getEnergyStorageCapacityInMWH();
		double energyLevelInMWH = initialEnergyInStorageInMWh;
		int intervalBegin = 0;
		State previousState = calcStateInPeriod(0, priceMedian);
		for (int period = 1; period < forecastSteps; period++) {
			State state = calcStateInPeriod(period, priceMedian);
			if (isFinalisingInterval(previousState, state) || isLastElementInForecast(period)) {
				if (previousState == State.CHARGE) {
					double energyToCharge = maxInternalEnergyInMWH - energyLevelInMWH;
					double chargedEnergy = updateChargeSchedule(energyToCharge, intervalBegin, period);
					energyLevelInMWH += chargedEnergy;
				} else if (previousState == State.DISCHARGE) {
					double chargedEnergy = updateChargeSchedule(-energyLevelInMWH, intervalBegin, period);
					energyLevelInMWH += chargedEnergy;
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
		double maxChargingPowerInMW = storage.getInternalPowerInMW();
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
		for (int element = 0; element < scheduleDurationPeriods; element++) {
			double selfDischargeInMWH = storage.calcInternalSelfDischargeInMWH(currentInternalEnergyInMWh);
			double nextInternalEnergyInMWH = currentInternalEnergyInMWh + internalChargingPowersInMW[element]
					- selfDischargeInMWH;
			nextInternalEnergyInMWH = ensureWithinEnergyBounds(nextInternalEnergyInMWH);
			double availableEnergyDeltaInMWH = nextInternalEnergyInMWH - currentInternalEnergyInMWh + selfDischargeInMWH;
			double externalEnergyInMWH = storage.internalToExternalEnergy(availableEnergyDeltaInMWH);

			demandScheduleInMWH[element] = externalEnergyInMWH;
			if (externalEnergyInMWH < 0) {
				priceScheduleInEURperMWH[element] = minDischargePrice;
			} else if (externalEnergyInMWH > 0) {
				priceScheduleInEURperMWH[element] = maxChargePrice;
			} else {
				priceScheduleInEURperMWH[element] = forecastPrices[element];
			}
			scheduledInitialInternalEnergyInMWH[element] = currentInternalEnergyInMWh;
			currentInternalEnergyInMWh = nextInternalEnergyInMWH;
		}
	}

	@Override
	protected MeritOrderSensitivity createBlankSensitivity() {
		return new PriceNoSensitivity();
	}

	@Override
	public double getChargingPowerForecastInMW(TimeStamp targetTime) {
		throw new RuntimeException(ERR_PROVIDE_FORECAST + StrategistType.MULTI_AGENT_MEDIAN);
	}

	@Override
	public void storeMeritOrderForesight(TimePeriod _1, SupplyOrderBook _2, DemandOrderBook _3) {
		throw new RuntimeException(ERR_USE_PRICE_FORECAST + StrategistType.MULTI_AGENT_MEDIAN);
	}
}