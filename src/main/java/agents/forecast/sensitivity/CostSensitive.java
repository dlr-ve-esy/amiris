// SPDX-FileCopyrightText: 2026 German Aerospace Center <amiris@dlr.de>
//
// SPDX-License-Identifier: Apache-2.0
package agents.forecast.sensitivity;

import java.util.ArrayList;
import java.util.Comparator;
import agents.markets.meritOrder.MarketClearingResult;
import agents.markets.meritOrder.books.OrderBook;
import agents.markets.meritOrder.books.OrderBookItem;
import agents.markets.meritOrder.books.SupplyOrderBook;
import agents.markets.meritOrder.sensitivities.SensitivityItem;

/** Provides full merit order assessment for cost / revenues associated with added demand / supply
 * 
 * @author Johannes Kochems, Christoph Schimeczek */
public class CostSensitive implements MarketClearingAssessment {
	/** list of changes (in terms of cumulated power and price) in the merit order for all possible charging events of the
	 * associated flexibility device */
	protected ArrayList<SensitivityItem> additionalLoadItems = new ArrayList<>();
	/** list of changes (in terms of cumulated power and price) in the merit order for all possible discharging events of the
	 * associated flexibility device */
	protected ArrayList<SensitivityItem> additionalSupplyItems = new ArrayList<>();

	@Override
	public final void assess(MarketClearingResult clearingResult) {
		extractOrders(clearingResult.getSupplyBook());
		extractOrders(clearingResult.getDemandBook());
		additionalLoadItems.sort(getComparator());
		additionalSupplyItems.sort(getComparator().reversed());
		setCumulativeValues(additionalLoadItems);
		setCumulativeValues(additionalSupplyItems);
	}

	/** Adds entries of given {@link OrderBook} (depending on its type) to either charging or discharging sensitivity
	 * 
	 * @param book to be read out */
	protected void extractOrders(OrderBook book) {
		boolean isSupplyBook = book instanceof SupplyOrderBook;
		for (OrderBookItem item : book.getOrderBookItems()) {
			if (item.getBlockPower() <= 0) {
				continue;
			}
			if (isSupplyBook) {
				addSupplyItem(item);
			} else {
				addDemandItem(item);
			}
		}
	}

	/** Adds not-awarded supply power to {@link #additionalLoadItems} as additional load might lead to awarding them, and awarded
	 * supply power to {@link #additionalSupplyItems} as additional supply might lead to no-longer awarding them. */
	private void addSupplyItem(OrderBookItem item) {
		double notAwardedPower = item.getNotAwardedPower();
		double awardedPower = item.getAwardedPower();
		if (notAwardedPower > 0) {
			additionalLoadItems.add(new SensitivityItem(notAwardedPower, item.getOfferPrice(), 0));
		}
		if (awardedPower > 0) {
			additionalSupplyItems.add(new SensitivityItem(awardedPower, item.getOfferPrice(), 0.));
		}
	}

	/** Adds awarded demand power to {@link #additionalLoadItems} as additional load might lead to no-longer awarding them, and
	 * not-awarded demand power to {@link #additionalSupplyItems} as additional supply might lead to awarding them. */
	private void addDemandItem(OrderBookItem item) {
		double notAwardedPower = item.getNotAwardedPower();
		double awardedPower = item.getAwardedPower();
		if (notAwardedPower > 0) {
			additionalSupplyItems.add(new SensitivityItem(notAwardedPower, item.getOfferPrice(), 0.));
		}
		if (awardedPower > 0) {
			additionalLoadItems.add(new SensitivityItem(awardedPower, item.getOfferPrice(), 0.));
		}
	}

	/** @return {@link Comparator} for {@link SensitivityItem}s to be used by this Sensitivity type */
	private Comparator<SensitivityItem> getComparator() {
		return SensitivityItem.BY_PRICE;
	}

	/** Sets cumulative power and monetary value of given sorted {@link SensitivityItem}s */
	private void setCumulativeValues(ArrayList<SensitivityItem> items) {
		double cumulatedPower = 0;
		double monetaryValueOffset = 0;
		for (SensitivityItem item : items) {
			item.setCumulatedLowerPower(cumulatedPower);
			item.setMonetaryOffset(monetaryValueOffset);
			cumulatedPower += item.getPower();
			item.setUpperMonetaryValue(monetaryValueOffset + calcMonetaryValue(item));
			monetaryValueOffset = item.getUpperMonetaryValue();
		}
	}

	/** Calculates monetary value of given item excluding the monetary offset
	 * 
	 * @param item to assess
	 * @return monetary value of this {@link SensitivityItem} according to this Sensitivity type */
	private double calcMonetaryValue(SensitivityItem item) {
		return item.getPower() * item.getPrice();
	}

	@Override
	public double[] getDemandSensitivityPowers() {
		double[] powers = new double[additionalLoadItems.size() + 1];
		powers[0] = 0;
		for (int i = 1; i < powers.length; i++) {
			powers[i] = additionalLoadItems.get(i - 1).getCumulatedUpperPower();
		}
		return powers;
	}

	@Override
	public double[] getDemandSensitivityValues() {
		double[] values = new double[additionalLoadItems.size() + 1];
		values[0] = 0;
		for (int i = 1; i < values.length; i++) {
			values[i] = additionalLoadItems.get(i - 1).getUpperMonetaryValue();
		}
		return values;
	}

	@Override
	public double[] getSupplySensitivityPowers() {
		double[] powers = new double[additionalSupplyItems.size() + 1];
		powers[0] = 0;
		for (int i = 1; i < powers.length; i++) {
			powers[i] = additionalSupplyItems.get(i - 1).getCumulatedUpperPower();
		}
		return powers;
	}

	@Override
	public double[] getSupplySensitivityValues() {
		double[] values = new double[additionalSupplyItems.size() + 1];
		values[0] = 0;
		for (int i = 1; i < values.length; i++) {
			values[i] = additionalSupplyItems.get(i - 1).getUpperMonetaryValue();
		}
		return values;
	}
}
