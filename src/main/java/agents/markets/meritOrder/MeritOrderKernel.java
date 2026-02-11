// SPDX-FileCopyrightText: 2021-2026 German Aerospace Center <amiris@dlr.de>
//
// SPDX-License-Identifier: Apache-2.0
package agents.markets.meritOrder;

import java.util.ArrayList;
import agents.markets.meritOrder.books.DemandOrderBook;
import agents.markets.meritOrder.books.OrderBookItem;
import agents.markets.meritOrder.books.SupplyOrderBook;

/** Clears the energy market by matching demand and supply curves
 * 
 * @author Martin Klein, Christoph Schimeczek, A. Achraf El Ghazi */
public class MeritOrderKernel {

	/** MeritOrderKernel could not complete clearing. */
	public static class MeritOrderClearingException extends Exception {
		private static final long serialVersionUID = 1L;

		/** Creates a new instances
		 * 
		 * @param errorMessage to be conveyed */
		public MeritOrderClearingException(String errorMessage) {
			super(errorMessage);
		}
	}

	static final String ERR_NON_POSITIVE_ORDER_BOOK = "MarketClearing failed: Demand or Supply order book not strictly positive.";

	/** The function takes two sorted (ascending by cumulatedPower) OrderBooks for demand (descending by offerPrice) and supply
	 * (ascending by offerPrice). It is assumed that the price of the first element from demand exceeds that of the first supply
	 * element. Additionally, the OrderBooks need to contain a final bid reaching towards (minus) infinity for supply (demand) to
	 * ensure the cut of the curves. Sorting and bid structure is enforced in the OrderBook class. The algorithm begins with the
	 * lowermost element from demand and supply. It compares the demand and supply price from these elements. In case the demand
	 * price is lower than the supply price, the condition for a cut of the discrete functions is met. If no cut is found, the next
	 * element from demand and/or supply is selected, whichever has the lower cumulatedPower. Then the cut condition is evaluated
	 * again.
	 * 
	 * @param supply sorted supply orders
	 * @param demand sorted demand orders
	 * @return market clearing data, i.e. awarded power and price
	 * @throws MeritOrderClearingException in case the order books resemble no valid market */
	public static ClearingDetails clearMarketSimple(SupplyOrderBook supply, DemandOrderBook demand)
			throws MeritOrderClearingException {
		ArrayList<OrderBookItem> supplyBids = supply.getOrderBookItems();
		ArrayList<OrderBookItem> demandBids = demand.getOrderBookItems();

		double lastSupplyPrice = 0;
		double lastSupplyPower = 0;
		double lastDemandPower = 0;

		ensureOrderBookPositiveEnergy(supplyBids);
		ensureOrderBookPositiveEnergy(demandBids);

		int supplyIndex = 0;
		int demandIndex = 0;
		// Market clearing details
		int priceSettingDemandIdx = 0;
		OrderBookItem priceSettingDemand;
		int priceSettingSupplyIdx = 0;
		OrderBookItem priceSettingSupply;
		double minPriceSettingDemand = 0;

		while (true) {
			OrderBookItem supplyEntry = supplyBids.get(supplyIndex);
			OrderBookItem demandEntry = demandBids.get(demandIndex);

			double supplyPrice = supplyEntry.getOfferPrice();
			double demandPrice = demandEntry.getOfferPrice();
			double supplyPower = supplyEntry.getCumulatedPowerUpperValue();
			double demandPower = demandEntry.getCumulatedPowerUpperValue();

			boolean cutFound = demandPrice < supplyPrice;
			boolean cutAtSamePrice = demandPrice == supplyPrice;
			if (cutFound) {
				boolean supplyBlockIsCut = lastSupplyPower < lastDemandPower;
				boolean cutAtSamePower = lastSupplyPower == lastDemandPower;

				if (supplyBlockIsCut) {
					// Price setting bids and price setting demand power of the price setting demand bid
					priceSettingDemandIdx = demandIndex - 1;
					priceSettingDemand = demandBids.get(priceSettingDemandIdx);
					priceSettingSupplyIdx = supplyIndex;
					priceSettingSupply = supplyEntry;

					minPriceSettingDemand = priceSettingDemand.getCumulatedPowerUpperValue()
							- priceSettingSupply.getCumulatedPowerLowerValue();
					return new ClearingDetails(lastDemandPower, supplyPrice, priceSettingDemandIdx, priceSettingSupplyIdx,
							minPriceSettingDemand);
				} else if (cutAtSamePower) {
					// Consistent with virtual shift to the left of demand curve
					// Price setting bids and price setting demand power of the price setting demand bid
					priceSettingDemandIdx = demandIndex;
					priceSettingDemand = demandEntry;
					priceSettingSupplyIdx = supplyIndex;
					priceSettingSupply = supplyEntry;

					minPriceSettingDemand = priceSettingDemand.getCumulatedPowerUpperValue()
							- priceSettingSupply.getCumulatedPowerLowerValue();
					return new ClearingDetails(lastSupplyPower, Math.max(demandPrice, lastSupplyPrice), priceSettingDemandIdx,
							priceSettingSupplyIdx, minPriceSettingDemand);
				} else { // demandBlockIsCut
					// Price setting bids and price setting demand power of the price setting demand bid
					priceSettingDemandIdx = demandIndex;
					priceSettingDemand = demandEntry;
					priceSettingSupplyIdx = supplyIndex;
					priceSettingSupply = supplyEntry;

					minPriceSettingDemand = priceSettingDemand.getCumulatedPowerUpperValue()
							- priceSettingSupply.getCumulatedPowerLowerValue();
					return new ClearingDetails(lastSupplyPower, demandPrice, priceSettingDemandIdx, priceSettingSupplyIdx,
							minPriceSettingDemand);
				}
			} else if (cutAtSamePrice) {
				// Price setting bids and price setting demand power of the price setting demand bid
				priceSettingDemandIdx = demandIndex;
				priceSettingDemand = demandBids.get(priceSettingDemandIdx);
				priceSettingSupplyIdx = supplyIndex;
				priceSettingSupply = supplyEntry;

				minPriceSettingDemand = priceSettingDemand.getCumulatedPowerUpperValue()
						- priceSettingSupply.getCumulatedPowerLowerValue();
				return new ClearingDetails(Math.min(supplyPower, demandPower), demandPrice, priceSettingDemandIdx,
						priceSettingSupplyIdx, minPriceSettingDemand);
			} else { // No cut so far
				if (supplyPower >= demandPower) {
					lastDemandPower = demandPower;
					demandIndex++;
				}
				if (supplyPower <= demandPower) {
					lastSupplyPrice = supplyPrice;
					lastSupplyPower = supplyPower;
					supplyIndex++;
				}
			}
		}
	}

	/** Ensures that the given order book has positive cumulative power, otherwise throws exception
	 * 
	 * @param orderBook to be checked for the cumulated power of the last bid
	 * @throws MeritOrderClearingException if order book power maximum is non-positive */
	private static void ensureOrderBookPositiveEnergy(ArrayList<OrderBookItem> orderBook)
			throws MeritOrderClearingException {
		if (orderBook.get(orderBook.size() - 1).getCumulatedPowerUpperValue() <= 0) {
			throw new MeritOrderClearingException(ERR_NON_POSITIVE_ORDER_BOOK);
		}
	}
}