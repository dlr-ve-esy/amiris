// SPDX-FileCopyrightText: 2026 German Aerospace Center <amiris@dlr.de>
//
// SPDX-License-Identifier: Apache-2.0
package agents.markets.meritOrder.books;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.ListIterator;
import agents.markets.meritOrder.Bid;

/** {@link OrderBook} that manages all {@link OrderBookItem}s from supply-{@link Bid}s
 * 
 * @author Martin Klein, Christoph Schimeczek */
public class SupplyOrderBook extends OrderBook implements Cloneable {
	@Override
	protected double getLastBidValue() {
		return Double.MAX_VALUE;
	}

	@Override
	protected Comparator<OrderBookItem> getSortComparator() {
		return OrderBookItem.BY_PRICE;
	}

	/** @return most expensive (real) bid (i.e. with block power is larger than 0); may only be called after sorting */
	public OrderBookItem getHighestItem() {
		ensureSortedOrThrow("Bids have not yet been sorted - most expensive bid is not yet known!");
		ListIterator<OrderBookItem> iterator = orderBookItems.listIterator(orderBookItems.size());
		while (iterator.hasPrevious()) {
			OrderBookItem item = iterator.previous();
			if (item.getBlockPower() > 0) {
				return item;
			}
		}
		throw new RuntimeException("Could not find valid bid with blockPower > 0!");
	}

	/** Can only be called once the book is updated after market clearing
	 * 
	 * @return one of possibly many {@link OrderBookItem}s that were price setting<br>
	 *         may only be called after sorting and awarding */
	public OrderBookItem getLastAwardedItem() {
		ensureSortedOrThrow("Bids have not yet been sorted - awarded power is yet unknown!");
		OrderBookItem comparedTo = new OrderBookItem(new Bid(0, awardedPrice, 0), Long.MIN_VALUE);
		int indexOfSearchedItem = Collections.binarySearch(orderBookItems, comparedTo, OrderBookItem.BY_PRICE);
		if (indexOfSearchedItem < 0) {
			indexOfSearchedItem = -(indexOfSearchedItem + 1);
		}
		return orderBookItems.get(indexOfSearchedItem);
	}

	/** @return a deep copy of SupplyOrderBook caller */
	public SupplyOrderBook clone() {
		SupplyOrderBook newSupplyOrderBook = new SupplyOrderBook();
		newSupplyOrderBook.awardedCumulativePower = this.awardedCumulativePower;
		newSupplyOrderBook.awardedPrice = this.awardedPrice;
		newSupplyOrderBook.isSorted = this.isSorted;
		newSupplyOrderBook.orderBookItems = new ArrayList<OrderBookItem>();
		for (OrderBookItem orderBookItem : this.orderBookItems) {
			OrderBookItem newItem = new OrderBookItem(orderBookItem.getBid().clone(), orderBookItem.getTraderUuid());
			newSupplyOrderBook.orderBookItems.add(newItem);
		}
		return newSupplyOrderBook;
	}
}
