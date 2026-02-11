// SPDX-FileCopyrightText: 2024-2026 German Aerospace Center <amiris@dlr.de>
//
// SPDX-License-Identifier: Apache-2.0
package agents.markets.meritOrder.books;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;
import java.util.stream.Collectors;
import agents.markets.DayAheadMarket;
import agents.markets.meritOrder.Bid;
import de.dlr.gitlab.fame.communication.transfer.ComponentCollector;
import de.dlr.gitlab.fame.communication.transfer.ComponentProvider;
import de.dlr.gitlab.fame.communication.transfer.Portable;

/** Handles a list of imported/exported Bids in a {@link DayAheadMarket} for a single time frame of trading.
 * 
 * @author A. Achraf El Ghazi, Felix Nitsch, Christoph Schimeczek */
public class TransferOrderBook implements Portable, Cloneable {
	private HashMap<Long, List<Bid>> bidsByTrader = new HashMap<>();
	private static final List<Bid> EMPTY_BIDS = Collections.emptyList();

	/** required for {@link Portable}s */
	public TransferOrderBook() {}

	/** Adds given {@link Bid} to this {@link TransferOrderBook}
	 * 
	 * @param bid to be added to TransferOrderBook
	 * @param traderId associated with the bid */
	public void addBid(Bid bid, long traderId) {
		bidsByTrader.computeIfAbsent(traderId, __ -> new ArrayList<Bid>()).add(bid);
	}

	@Override
	public void addComponentsTo(ComponentCollector collector) {
		collector.storeInts(bidsByTrader.keySet().size());
		for (Entry<Long, List<Bid>> entry : bidsByTrader.entrySet()) {
			collector.storeLongs(entry.getKey());
			collector.storeInts(entry.getValue().size());
			for (Bid bid : entry.getValue()) {
				collector.storeComponents(bid);
			}
		}
	}

	@Override
	public void populate(ComponentProvider provider) {
		int numberOfTraders = provider.nextInt();
		for (int i = 0; i < numberOfTraders; i++) {
			long traderID = provider.nextLong();
			int itemCount = provider.nextInt();
			List<Bid> bids = bidsByTrader.computeIfAbsent(traderID, __ -> new ArrayList<Bid>());
			for (int j = 0; j < itemCount; j++) {
				bids.add(provider.nextComponent(Bid.class));
			}
		}
	}

	/** @return a deep copy of this TransferOrderBook */
	public TransferOrderBook clone() {
		TransferOrderBook transferOrderBook = new TransferOrderBook();
		for (Entry<Long, List<Bid>> entry : bidsByTrader.entrySet()) {
			List<Bid> clonedBids = entry.getValue().stream().map(bid -> bid.clone()).collect(Collectors.toList());
			transferOrderBook.addTraderBids(entry.getKey(), clonedBids);
		}
		return transferOrderBook;
	}

	/** add the given bids to the specified trader's list of bids
	 * 
	 * @param traderId associated with the bids
	 * @param bids to be added to the associated trader's list of bids */
	public void addTraderBids(long traderId, List<Bid> bids) {
		bidsByTrader.computeIfAbsent(traderId, __ -> new ArrayList<Bid>()).addAll(bids);
	}

	/** @return traderIDs that have bids associated in this TransferOrderBook */
	public Set<Long> getTraders() {
		return bidsByTrader.keySet();
	}

	/** Get all bids of a specific trader
	 * 
	 * @param traderUuid of which the bids are requested
	 * @return bids associated with the given traderUuid */
	public List<Bid> getBidsOf(long traderUuid) {
		return bidsByTrader.get(traderUuid);
	}

	/** Calculate offered energy total across all bids of this order book for given trader
	 * 
	 * @param traderUuid UUID of trader to calculate energy amount for
	 * @return sum of total offered energy in MWH of given trader */
	public double getEnergySumForTrader(long traderUuid) {
		return bidsByTrader.getOrDefault(traderUuid, EMPTY_BIDS).stream().mapToDouble(bid -> bid.getEnergyAmountInMWH())
				.sum();
	}

	/** Computes the total energy of all items of this TransferOrderBook
	 * 
	 * @return the total energy of all bid block powers in MWh */
	public double getAccumulatedEnergyInMWH() {
		return bidsByTrader.values().stream()
				.mapToDouble(bids -> bids.stream().mapToDouble(bid -> bid.getEnergyAmountInMWH()).sum())
				.sum();
	}
}
