// SPDX-FileCopyrightText: 2022 German Aerospace Center <amiris@dlr.de>
//
// SPDX-License-Identifier: Apache-2.0
package agents.markets.meritOrder.sensitivities;

import java.util.ArrayList;
import java.util.Comparator;
import agents.markets.meritOrder.books.DemandOrderBook;
import agents.markets.meritOrder.books.OrderBook;
import agents.markets.meritOrder.books.OrderBookItem;
import agents.markets.meritOrder.books.SupplyOrderBook;

/** Represents changes of a merit-order derived value (e.g. electricity price or system cost) when the awarded power for supply or
 * demand are changed
 *
 * @author Christoph Schimeczek */
public abstract class MeritOrderSensitivity {
	protected double externalChargingPowerInMW;
	protected double externalDischargingPowerInMW;
	protected ArrayList<SensitivityItem> chargingItems = new ArrayList<>();
	protected ArrayList<SensitivityItem> dischargingItems = new ArrayList<>();

	/** sets maximum charging and discharging powers in MW according to specified values
	 * 
	 * @param maxChargePowerInMW to be set
	 * @param maxDischargePowerInMW to be set */
	public final void updatePowers(double maxChargePowerInMW, double maxDischargePowerInMW) {
		this.externalChargingPowerInMW = maxChargePowerInMW;
		this.externalDischargingPowerInMW = maxDischargePowerInMW;
	}

	/** updates sensitivities from given order books
	 * 
	 * @param supplyBook order book with supply orders after clearing
	 * @param demandBook order book with demand orders after clearing */
	public final void updateSensitivities(SupplyOrderBook supplyBook, DemandOrderBook demandBook) {
		clear();
		selectOrderBookItems(supplyBook, demandBook);
		chargingItems.sort(getComparator());
		dischargingItems.sort(getComparator().reversed());
		setCumulativeValues(chargingItems);
		setCumulativeValues(dischargingItems);
		chargingItems.removeIf(i -> i.getCumulatedLowerPower() > externalChargingPowerInMW);
		dischargingItems.removeIf(i -> i.getCumulatedLowerPower() > externalDischargingPowerInMW);
	}

	/** clears stored sensitivity data */
	private void clear() {
		chargingItems.clear();
		dischargingItems.clear();
	}

	/** pick from given supply and / or demand {@link OrderBook}s - and add picked one(s) to this sensitivity
	 * 
	 * @param supplyBook order book with supply orders after clearing
	 * @param demandBook order book with demand orders after clearing */
	protected abstract void selectOrderBookItems(SupplyOrderBook supplyBook, DemandOrderBook demandBook);

	/** Returns true if the given item as a positive block power
	 * 
	 * @param item to inspect
	 * @return true if block power of given {@link OrderBookItem} is larger than Zero */
	protected final boolean hasPositiveBlockPower(OrderBookItem item) {
		return item.getBlockPower() > 0;
	}

	/** Adds entries of given {@link OrderBook} (depending on its type) to either charging or discharging sensitivity
	 * 
	 * @param book to be read out */
	protected void extractOrders(OrderBook book) {
		boolean isSupplyBook = book instanceof SupplyOrderBook;
		for (OrderBookItem item : book.getOrderBookItems()) {
			if (!hasPositiveBlockPower(item)) {
				continue;
			}
			if (isSupplyBook) {
				addSupplyItem(item);
			} else {
				addDemandItem(item);
			}
		}
	}

	/** adds notAwardedPower to {@link #chargingItems} and awardedPower to {@link #dischargingItems} */
	private void addSupplyItem(OrderBookItem item) {
		double notAwardedPower = item.getNotAwardedPower();
		double awardedPower = item.getAwardedPower();
		if (notAwardedPower > 0) {
			chargingItems.add(new SensitivityItem(notAwardedPower, item.getOfferPrice(), item.getMarginalCost()));
		}
		if (awardedPower > 0) {
			dischargingItems.add(new SensitivityItem(awardedPower, item.getOfferPrice(), item.getMarginalCost()));
		}
	}

	/** adds awardedPower to {@link #chargingItems} and notAwardedPower to {@link #dischargingItems} */
	private void addDemandItem(OrderBookItem item) {
		double notAwardedPower = item.getNotAwardedPower();
		double awardedPower = item.getAwardedPower();
		if (notAwardedPower > 0) {
			dischargingItems.add(new SensitivityItem(notAwardedPower, item.getOfferPrice(), item.getMarginalCost()));
		}
		if (awardedPower > 0) {
			chargingItems.add(new SensitivityItem(awardedPower, item.getOfferPrice(), item.getMarginalCost()));
		}
	}

	/** @return {@link Comparator} for {@link SensitivityItem}s to be used by this Sensitivity type */
	protected abstract Comparator<SensitivityItem> getComparator();

	/** sets cumulative power and monetary value of given sorted {@link SensitivityItem}s */
	private void setCumulativeValues(ArrayList<SensitivityItem> items) {
		double cumulatedPower = 0;
		double monetaryValueOffset = 0;
		for (SensitivityItem item : items) {
			item.setCumulatedLowerPower(cumulatedPower);
			item.setMonetaryOffset(monetaryValueOffset);
			cumulatedPower += item.getPower();
			monetaryValueOffset += calcMonetaryValue(item);
		}
	}

	/** Calculates monetary value of given item
	 * 
	 * @param item to assess
	 * @return monetary value of this {@link SensitivityItem} according to this Sensitivity type */
	protected abstract double calcMonetaryValue(SensitivityItem item);

	/** Calculate sensitivity in equally distributed steps of power
	 * 
	 * @param numberOfTransitionSteps to determine the granularity of the created sensitivity
	 * @return values of Sensitivity in (2 * numberOfTransitionSteps + 1) steps, equally dividing max charging and discharging
	 *         powers; first entry corresponds to maximum discharging power, while the last entry resembles sensitivity value at
	 *         maximum charging power */
	public double[] getValuesInSteps(int numberOfTransitionSteps) {
		double[] values = new double[2 * numberOfTransitionSteps + 1];

		double chargingPowerPerStep = externalChargingPowerInMW / numberOfTransitionSteps;
		int maxChargingIndex = chargingItems.size();
		int chargingIndex = 0;
		values[numberOfTransitionSteps] = 0.0;
		for (int step = 1; step <= numberOfTransitionSteps; step++) {
			double power = chargingPowerPerStep * step;
			while (chargingIndex < maxChargingIndex && chargingItems.get(chargingIndex).getCumulatedUpperPower() < power) {
				chargingIndex++;
			}
			int indexInArray = numberOfTransitionSteps + step;
			if (chargingIndex < maxChargingIndex) {
				values[indexInArray] = calcValueOfItemAtPower(chargingItems.get(chargingIndex), power);
			} else {
				values[indexInArray] = Double.NaN;
			}
		}

		double dischargingPowerPerStep = externalDischargingPowerInMW / numberOfTransitionSteps;
		int dischargingIndex = 0;
		int maxDischargingIndex = dischargingItems.size();
		for (int step = 1; step <= numberOfTransitionSteps; step++) {
			double power = dischargingPowerPerStep * step;
			while (dischargingIndex < maxDischargingIndex
					&& dischargingItems.get(dischargingIndex).getCumulatedUpperPower() < power) {
				dischargingIndex++;
			}
			int indexInArray = numberOfTransitionSteps - step;
			if (dischargingIndex < maxDischargingIndex) {
				values[indexInArray] = calcValueOfItemAtPower(dischargingItems.get(dischargingIndex), -power);
			} else {
				values[indexInArray] = Double.NaN;
			}
		}
		return values;
	}

	/** Returns power per step depending on given transition step count and stored charging / discharging powers
	 * 
	 * @param numberOfTransitionSteps to consider
	 * @return {@link StepPower} corresponding to step values returned by {@link #getValuesInSteps(int)} */
	public final StepPower getStepPowers(int numberOfTransitionSteps) {
		return new StepPower(externalChargingPowerInMW, externalDischargingPowerInMW, numberOfTransitionSteps);
	}

	/** Calculates value of given sensitivity item at specified power
	 * 
	 * @param item that is to be evaluated
	 * @param power to be applied
	 * @return value of given {@link SensitivityItem} at specified power according to this Sensitivity type;<br>
	 *         when power &gt; 0: <b>charging</b>, otherwise <b>discharging</b> */
	protected abstract double calcValueOfItemAtPower(SensitivityItem item, double power);

	/** Stores fixed electricity price forecast value
	 * 
	 * @param electricityPriceForecast value to store */
	public abstract void updatePriceForecast(double electricityPriceForecast);

	/** @return true if sensitivities have not been set yet */
	public boolean isEmpty() {
		return chargingItems.isEmpty() && dischargingItems.isEmpty();
	}
}