// SPDX-FileCopyrightText: 2026 German Aerospace Center <amiris@dlr.de>
//
// SPDX-License-Identifier: Apache-2.0
package agents.markets.meritOrder.books;

import java.util.ArrayList;
import java.util.Comparator;
import agents.markets.meritOrder.Bid;
import agents.markets.meritOrder.Constants;

/** {@link OrderBook} that manages all {@link OrderBookItem}s from demand-{@link Bid}s
 * 
 * @author Martin Klein, Christoph Schimeczek, A. Achraf El Ghazi */
public class DemandOrderBook extends OrderBook implements Cloneable {
	@Override
	protected double getLastBidValue() {
		return -Double.MAX_VALUE;
	}

	/** sorts in descending order */
	@Override
	protected Comparator<OrderBookItem> getSortComparator() {
		return OrderBookItem.BY_PRICE.reversed();
	}

	/** @return sum of all items' offered power */
	public double getOfferedPower() {
		return orderBookItems.stream().mapToDouble(i -> i.getBlockPower()).sum();
	}

	/** @return sum of all items' asked power, that is not sheddable, i.e. has a value of lost load greater or equal to the
	 *         {@link Constants#SCARCITY_PRICE_IN_EUR_PER_MWH} */
	public double getUnsheddableDemand() {
		return orderBookItems.stream()
				.filter(i -> (i.getOfferPrice() >= Constants.SCARCITY_PRICE_IN_EUR_PER_MWH))
				.mapToDouble(i -> i.getBlockPower()).sum();
	}

	/** Returns amount of power that the supply is short; can only be called once the book is updated after market clearing
	 * 
	 * @param highestSupplyItem OrderBookItem with highest price and non-zero power from SupplyOrderBook
	 * @return amount of power that the supply is short, i.e. the sum of all demand power not awarded with a higher price than the
	 *         last supply offer */
	public double getAmountOfPowerShortage(OrderBookItem highestSupplyItem) {
		ensureSortedOrThrow("Bids have not yet been sorted - most expensive bid is not yet known!");
		double supplyPrice = highestSupplyItem.getOfferPrice();
		return orderBookItems.stream().filter(i -> (i.getOfferPrice() > supplyPrice) && (i.getNotAwardedPower() > 0))
				.mapToDouble(i -> i.getNotAwardedPower()).sum();
	}

	@Override
	/** @return a deep copy of DemandOrderBook caller */
	public DemandOrderBook clone() {
		DemandOrderBook demandOrderBook = new DemandOrderBook();
		demandOrderBook.awardedCumulativePower = this.awardedCumulativePower;
		demandOrderBook.awardedPrice = this.awardedPrice;
		demandOrderBook.isSorted = false;
		demandOrderBook.orderBookItems = new ArrayList<OrderBookItem>();
		for (OrderBookItem orderBookItem : this.orderBookItems) {
			OrderBookItem newItem = new OrderBookItem(orderBookItem.getBid().clone(), orderBookItem.getTraderUuid());
			demandOrderBook.orderBookItems.add(newItem);
		}
		return demandOrderBook;
	}
}
