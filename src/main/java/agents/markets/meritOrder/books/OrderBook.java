// SPDX-FileCopyrightText: 2022 German Aerospace Center <amiris@dlr.de>
//
// SPDX-License-Identifier: Apache-2.0
package agents.markets.meritOrder.books;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;
import agents.markets.DayAheadMarket;
import agents.markets.meritOrder.Bid;
import de.dlr.gitlab.fame.communication.transfer.ComponentCollector;
import de.dlr.gitlab.fame.communication.transfer.ComponentProvider;
import de.dlr.gitlab.fame.communication.transfer.Portable;

/** Handles a list of bids or asks at an energy {@link DayAheadMarket} for a single time frame of trading
 * 
 * @author Martin Klein, Christoph Schimeczek */
public abstract class OrderBook implements Portable {

	/** required for {@link Portable}s */
	public OrderBook() {}

	/** Method of how to award energy across multiple price-setting bids */
	public static enum DistributionMethod {
		/** Bids are awarded in the order they appear after sorting the order book */
		FIRST_COME_FIRST_SERVE,
		/** Bids with the same price are awarded in a random order */
		RANDOMIZE,
		/** Bids with the same price are all awarded the same share */
		SAME_SHARES
	};

	protected double awardedPrice = Double.NaN;
	protected double awardedCumulativePower = Double.NaN;
	protected ArrayList<OrderBookItem> orderBookItems = new ArrayList<OrderBookItem>();
	protected boolean isSorted = false;

	/** Adds given {@link Bid} to this {@link OrderBook}; the OrderBook must not be sorted yet
	 * 
	 * @param bid to be added to the unsorted OrderBook */
	public void addBid(Bid bid) {
		ensureNotYetSortedOrThrow("OrderBook is already sorted - cannot add further items.");
		orderBookItems.add(new OrderBookItem(bid));
	}

	/** Ensures the {@link OrderBook} items are not yet {@link #isSorted sorted}
	 * 
	 * @param exceptionMessage message of the thrown exception (if any)
	 * @throws RuntimeException if items are already sorted */
	protected void ensureNotYetSortedOrThrow(String exceptionMessage) {
		if (isSorted) {
			throw new RuntimeException(exceptionMessage);
		}
	}

	/** Ensures the {@link OrderBook} items are already {@link #isSorted sorted}
	 * 
	 * @param exceptionMessage message of the thrown exception (if any)
	 * @throws RuntimeException if items are not yet sorted */
	protected void ensureSortedOrThrow(String exceptionMessage) {
		if (!isSorted) {
			throw new RuntimeException(exceptionMessage);
		}
	}

	/** Adds multiple {@link Bid}s to this {@link OrderBook}; the OrderBook must not be sorted yet
	 * 
	 * @param bids to add to this unsorted OrderBook */
	public void addBids(ArrayList<Bid> bids) {
		ensureNotYetSortedOrThrow("OrderBook is already sorted - cannot add further items.");
		for (Bid bid : bids) {
			orderBookItems.add(new OrderBookItem(bid));
		}
	}

	/** Removes all stored {@link OrderBookItem OrderBookItems} -- sets status to "unsorted" -- sets {@link OrderBook#awardedPrice}
	 * and {@link OrderBook#awardedCumulativePower} to {@link Double#NaN} */
	public void clear() {
		orderBookItems.clear();
		isSorted = false;
		this.awardedPrice = Double.NaN;
		this.awardedCumulativePower = Double.NaN;
	}

	/** @return a list of items, which are sorted and have assigned cumulated power values */
	public ArrayList<OrderBookItem> getOrderBookItems() {
		if (!isSorted) {
			addVirtualLastBid();
			orderBookItems.sort(getSortComparator());
			cumulatePowerOfItems();
			isSorted = true;
		}
		return orderBookItems;
	}

	/** Adds bid with 0 power and very high or low price to orderBookItems, ensuring the crossing of supply and demand */
	private void addVirtualLastBid() {
		addBid(getLastBid());
	}

	/** @return the last virtual {@link Bid} depending on the type of order book */
	protected abstract Bid getLastBid();

	/** @return {@link Comparator} to sort {@link #orderBookItems} with */
	protected abstract Comparator<OrderBookItem> getSortComparator();

	/** Calculates and sets cumulated power value of ordered {@link #orderBookItems} */
	private void cumulatePowerOfItems() {
		double cumulatedPower = 0;
		for (OrderBookItem entry : orderBookItems) {
			cumulatedPower += entry.getBlockPower();
			entry.setCumulatedPowerUpperValue(cumulatedPower);
		}
	}

	/** Updates awarded powers of all contained {@link OrderBookItem}s, based on the given parameters
	 * 
	 * @param totalAwardedPower obtained at market clearing - after this call: equals to sum of all OrderBookItem's awarded power
	 * @param awardedPrice uniform market clearing price
	 * @param method determines, how power is distributed among multiple price-setting bids */
	public void updateAwardedPowerInBids(double totalAwardedPower, double awardedPrice, DistributionMethod method) {
		ensureSortedOrThrow("OrderBook needs to be sorted before this operation can be executed!");
		this.awardedPrice = awardedPrice;
		this.awardedCumulativePower = totalAwardedPower;

		awardNonPriceSettingBids();
		List<OrderBookItem> priceSettingBids = orderBookItems.stream().filter(item -> item.getOfferPrice() == awardedPrice)
				.collect(Collectors.toList());
		priceSettingBids.stream().filter(item -> item.getBlockPower() <= 0).forEach(item -> item.setAwardedPower(0));
		priceSettingBids.removeIf(item -> item.getBlockPower() <= 0);

		if (!priceSettingBids.isEmpty()) {
			awardPriceSettingBids(priceSettingBids, method);
		}
	}

	/** Awards powers for bids that are not price setting and therefore are either fully awarded or not at all */
	private void awardNonPriceSettingBids() {
		Map<Boolean, List<OrderBookItem>> bidsByAwardStatus = orderBookItems.stream()
				.filter(item -> item.getOfferPrice() != awardedPrice).collect(Collectors
						.partitioningBy(item -> item.getCumulatedPowerUpperValue() <= awardedCumulativePower));
		bidsByAwardStatus.get(true).stream().forEach(item -> item.setAwardedPower(item.getBlockPower()));
		bidsByAwardStatus.get(false).stream().forEach(item -> item.setAwardedPower(0));
	}

	/** Distribute remaining power to award among all price-setting bids according to the given method
	 * 
	 * @param priceSettingBids list of all Items that are price setting
	 * @param method determines, how power is distributed among multiple price-setting bids */
	private void awardPriceSettingBids(List<OrderBookItem> priceSettingBids, DistributionMethod method) {
		double availablePower = calcRemaingPowerToDistribute(priceSettingBids);
		switch (method) {
			case FIRST_COME_FIRST_SERVE:
				awardFirstComeFirstServe(availablePower, priceSettingBids);
				break;
			case SAME_SHARES:
				double offeredPowerFromPriceSettingBids = priceSettingBids.stream().mapToDouble(i -> i.getBlockPower())
						.sum();
				double awardShare = availablePower / offeredPowerFromPriceSettingBids;
				awardSameShares(awardShare, priceSettingBids);
				break;
			case RANDOMIZE:
				Collections.shuffle(priceSettingBids);
				awardFirstComeFirstServe(availablePower, priceSettingBids);
				break;
			default:
				throw new RuntimeException("Power awarding method " + method + " not implemented!");
		}
	}

	/** Subtracts already distributed power of non-price-setting bids from total power to award */
	private double calcRemaingPowerToDistribute(List<OrderBookItem> priceSettingBids) {
		double priceSettingBidsPowerLowerValue = priceSettingBids.stream()
				.mapToDouble(OrderBookItem::getCumulatedPowerLowerValue).min().orElseThrow(NoSuchElementException::new);
		return awardedCumulativePower - priceSettingBidsPowerLowerValue;
	}

	/** see {@link DistributionMethod#FIRST_COME_FIRST_SERVE} */
	private void awardFirstComeFirstServe(double availablePower, List<OrderBookItem> priceSettingBids) {
		for (OrderBookItem item : priceSettingBids) {
			double awardedPower = Math.min(item.getBlockPower(), availablePower);
			item.setAwardedPower(awardedPower);
			availablePower -= awardedPower;
		}
	}

	/** see {@link DistributionMethod#SAME_SHARES} */
	private void awardSameShares(double awardShare, List<OrderBookItem> priceSettingBids) {
		for (OrderBookItem entry : priceSettingBids) {
			entry.setAwardedPower(entry.getBlockPower() * awardShare);
		}
	}

	/** Returns list of {@link OrderBookItem}s that belong to the specified trader
	 * 
	 * @param traderID to look for
	 * @return list of Items that associated with specified trader */
	public ArrayList<OrderBookItem> filterForBidsByTrader(long traderID) {
		ArrayList<OrderBookItem> items = new ArrayList<>();
		for (OrderBookItem item : orderBookItems) {
			if (item.getTraderUuid() == traderID) {
				items.add(item);
			}
		}
		return items;
	}

	@Override
	public String toString() {
		StringBuilder builder = new StringBuilder("[");
		for (OrderBookItem item : orderBookItems) {
			builder.append("[" + item.getCumulatedPowerUpperValue() + "," + item.getOfferPrice() + "],");
		}
		builder.append("]");
		return builder.toString();
	}

	/** required for {@link Portable}s */
	@Override
	public void addComponentsTo(ComponentCollector collector) {
		collector.storeDoubles(awardedPrice, awardedCumulativePower);
		for (OrderBookItem item : orderBookItems) {
			collector.storeComponents(item);
		}
		collector.storeBooleans(isSorted);
	}

	/** required for {@link Portable}s */
	@Override
	public void populate(ComponentProvider provider) {
		awardedPrice = provider.nextDouble();
		awardedCumulativePower = provider.nextDouble();
		orderBookItems = provider.nextComponentList(OrderBookItem.class);
		isSorted = provider.nextBoolean();

	}
}