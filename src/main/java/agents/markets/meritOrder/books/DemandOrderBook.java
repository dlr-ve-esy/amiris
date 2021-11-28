package agents.markets.meritOrder.books;

import java.util.Comparator;
import agents.markets.meritOrder.Bid;
import agents.markets.meritOrder.Constants;
import agents.markets.meritOrder.Bid.Type;

/** {@link OrderBook} that manages all {@link OrderBookItem}s from demand-{@link Bid}s
 * 
 * @author Martin Klein, Christoph Schimeczek */
public class DemandOrderBook extends OrderBook {
	@Override
	protected Bid getLastBid() {
		return new Bid(0, -Double.MAX_VALUE, 0, Long.MIN_VALUE, Type.Demand);
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

	/** Can only be called once the book is updated after market clearing
	 * 
	 * @return amount of power that the supply is short, i.e. the sum of all demand power not awarded with a higher price than the
	 *         last supply offer */
	public double getAmountOfPowerShortage(OrderBookItem highestSupplyItem) {
		ensureSortedOrThrow("Bids have not yet been sorted - most expensive bid is not yet known!");
		double supplyPrice = highestSupplyItem.getOfferPrice();
		return orderBookItems.stream().filter(i -> (i.getOfferPrice() > supplyPrice) && (i.getNotAwardedPower() > 0))
				.mapToDouble(i -> i.getNotAwardedPower()).sum();
	}
}