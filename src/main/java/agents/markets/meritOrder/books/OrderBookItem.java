// SPDX-FileCopyrightText: 2021-2026 German Aerospace Center <amiris@dlr.de>
//
// SPDX-License-Identifier: Apache-2.0
package agents.markets.meritOrder.books;

import java.util.Comparator;
import agents.markets.meritOrder.Bid;
import de.dlr.gitlab.fame.communication.transfer.ComponentCollector;
import de.dlr.gitlab.fame.communication.transfer.ComponentProvider;
import de.dlr.gitlab.fame.communication.transfer.Portable;

/** An item of an {@link OrderBook}, either bid or ask
 * 
 * @author Christoph Schimeczek, Martin Klein , Evelyn Sperber, Farzad Sarfarazi, A. Achraf El Ghazi */
public class OrderBookItem implements Portable {
	static final String ERR_NEGATIVE_POWER = "OrderBookItems with negative power received from Trader: ";

	static final Comparator<OrderBookItem> BY_PRICE = Comparator.comparing(item -> item.getOfferPrice());
	private Bid bid;
	private double cumulatedPowerUpperValue = Double.NaN;
	private double awardedPower = Double.NaN;
	private long traderUuid;

	/** required for {@link Portable}s */
	public OrderBookItem() {}

	/** Creates an {@link OrderBookItem} based in given Bid
	 * 
	 * @param bid associated with this order book item
	 * @param traderUuid id of the trader associated with the bids */
	public OrderBookItem(Bid bid, long traderUuid) {
		this.bid = bid;
		this.traderUuid = traderUuid;
		if (bid.getEnergyAmountInMWH() < 0.) {
			throw new RuntimeException(ERR_NEGATIVE_POWER + traderUuid);
		}
	}

	/** @return the bid of the OrderBookItem */
	public Bid getBid() {
		return bid;
	}

	/** @return actual awarded power; call only after market clearing */
	public double getAwardedPower() {
		return awardedPower;
	}

	/** @return left over power which hasn't been awarded; call only after market clearing */
	public double getNotAwardedPower() {
		return getBlockPower() - awardedPower;
	}

	/** Sets the actual awarded power in the context of the containing {@link OrderBook}
	 * 
	 * @param awardedPower of this bid; any value between 0 and {@link #getBlockPower()} */
	public void setAwardedPower(double awardedPower) {
		this.awardedPower = awardedPower;
	}

	/** @return sum of all previous bids in the merit-order <b>plus</b> the amount of power in this item's {@link Bid} */
	public double getCumulatedPowerUpperValue() {
		return cumulatedPowerUpperValue;
	}

	/** Sets the cumulated power upper value in the context of the containing {@link OrderBook}
	 * 
	 * @param cumulatedPower sum of all previous bids in the merit-order plus the amount of power in this item's {@link Bid} */
	public void setCumulatedPowerUpperValue(double cumulatedPower) {
		this.cumulatedPowerUpperValue = cumulatedPower;
	}

	/** @return sum of all previous bids in the merit-order <b>without</b> the amount of power in this item's {@link Bid} */
	public double getCumulatedPowerLowerValue() {
		return cumulatedPowerUpperValue - getBlockPower();
	}

	/** @return the amount of power of the associated {@link Bid} */
	public double getBlockPower() {
		return bid.getEnergyAmountInMWH();
	}

	/** @return maximum / minimum offered price associated with this {@link Bid}, depending on its type */
	public double getOfferPrice() {
		return bid.getOfferPriceInEURperMWH();
	}

	/** @return actual marginal cost - if provided with the associated {@link Bid} */
	public double getMarginalCost() {
		return bid.getMarginalCost();
	}

	@Override
	public String toString() {
		return awardedPower + " Mwh / " + bid + " from trader" + traderUuid;
	}

	/** @return ID of the traded that issued the associated {@link Bid} */
	public long getTraderUuid() {
		return traderUuid;
	}

	@Override
	/** required for {@link Portable}s */
	public void addComponentsTo(ComponentCollector collector) {
		collector.storeComponents(bid);
		collector.storeDoubles(cumulatedPowerUpperValue, awardedPower);
		collector.storeLongs(traderUuid);
	}

	@Override
	/** required for {@link Portable}s */
	public void populate(ComponentProvider provider) {
		bid = provider.nextComponent(Bid.class);
		cumulatedPowerUpperValue = provider.nextDouble();
		awardedPower = provider.nextDouble();
		traderUuid = provider.nextLong();
	}
}
