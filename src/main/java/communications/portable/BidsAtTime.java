// SPDX-FileCopyrightText: 2024-2026 German Aerospace Center <amiris@dlr.de>
//
// SPDX-License-Identifier: Apache-2.0
package communications.portable;

import java.util.List;
import agents.markets.meritOrder.Bid;
import de.dlr.gitlab.fame.communication.transfer.ComponentCollector;
import de.dlr.gitlab.fame.communication.transfer.ComponentProvider;
import de.dlr.gitlab.fame.communication.transfer.Portable;
import de.dlr.gitlab.fame.time.TimeStamp;

/** Summary of multiple bids from one trader for one bidding time period
 * 
 * @author Christoph Schimeczek */
public class BidsAtTime implements Portable {
	/** Bids to be associated with the supply side, i.e. offering electricity */
	private List<Bid> supplyBids;
	/** Bids to be associated with the demand side, i.e. requesting electricity */
	private List<Bid> demandBids;
	/** Time at which the bids shall be valid */
	private TimeStamp deliveryTime;
	/** id of the trader that is associated with the bids */
	private long traderUuid;

	/** required for {@link Portable}s */
	public BidsAtTime() {}

	/** Create a Bid message
	 * 
	 * @param deliveryTime at which the bids shall be valid
	 * @param traderUuid id of the trader that is associated with the bids
	 * @param supplyBids list of supplyBids, may be null or empty
	 * @param demandBids list of demandBids, may be null or empty */
	public BidsAtTime(TimeStamp deliveryTime, long traderUuid, List<Bid> supplyBids, List<Bid> demandBids) {
		this.deliveryTime = deliveryTime;
		this.traderUuid = traderUuid;
		this.supplyBids = supplyBids;
		this.demandBids = demandBids;
	}

	@Override
	public void addComponentsTo(ComponentCollector collector) {
		collector.storeComponents(deliveryTime);
		collector.storeLongs(traderUuid);
		if (supplyBids != null) {
			collector.storeInts(supplyBids.size());
			supplyBids.stream().forEach(bid -> collector.storeComponents(bid));
		} else {
			collector.storeInts(0);
		}
		if (demandBids != null) {
			demandBids.stream().forEach(bid -> collector.storeComponents(bid));
		}
	}

	@Override
	public void populate(ComponentProvider provider) {
		deliveryTime = provider.nextComponent(TimeStamp.class);
		traderUuid = provider.nextLong();
		int supplyBidCount = provider.nextInt();
		List<Bid> allBids = provider.nextComponentList(Bid.class);
		supplyBids = allBids.subList(0, supplyBidCount);
		demandBids = allBids.subList(supplyBidCount, allBids.size());
	}

	/** @return supply bids from the associated trader for the respective bidding time */
	public List<Bid> getSupplyBids() {
		return supplyBids;
	}

	/** @return demand bids from the associated trader for the respective bidding time */
	public List<Bid> getDemandBids() {
		return demandBids;
	}

	/** @return the UUID of the trader associated with the bids */
	public long getTraderUuid() {
		return traderUuid;
	}

	/** @return time for which the bids were created */
	public TimeStamp getDeliveryTime() {
		return deliveryTime;
	}
}
