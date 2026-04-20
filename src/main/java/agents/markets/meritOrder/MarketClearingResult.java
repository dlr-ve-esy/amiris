// SPDX-FileCopyrightText: 2021-2026 German Aerospace Center <amiris@dlr.de>
//
// SPDX-License-Identifier: Apache-2.0
package agents.markets.meritOrder;

import agents.markets.meritOrder.books.DemandOrderBook;
import agents.markets.meritOrder.books.OrderBookItem;
import agents.markets.meritOrder.books.SupplyOrderBook;
import agents.markets.meritOrder.books.OrderBook.DistributionMethod;

/** Holds market clearing results, i.e., clearing price, sold energy, and aggregated curves in @link DemandOrderBook} and
 * {@link SupplyOrderBook}
 *
 * @author Farzad Sarfarazi, Christoph Schimeczek, A. Achraf El Ghazi, Johannes Kochems */
public class MarketClearingResult {
	private double tradedEnergyInMWH;
	private double marketPriceInEURperMWH;
	private DemandOrderBook demandBook;
	private SupplyOrderBook supplyBook;

	/** Instantiate with price and awarded energy; books are set separately
	 * 
	 * @param tradedEnergyInMWH total awarded energy
	 * @param marketPriceInEURperMWH uniform market clearing price */
	public MarketClearingResult(double tradedEnergyInMWH, double marketPriceInEURperMWH) {
		this.tradedEnergyInMWH = tradedEnergyInMWH;
		this.marketPriceInEURperMWH = marketPriceInEURperMWH;
	}

	/** Instantiate with tradedEnergyInMWH, marketPriceInEURperMWH, priceSettingDemandBidIdx, priceSettingSupplyBidIdx, and
	 * minPriceSettingDemand from a ClearingResult object; and with demandBook, and supplyBook. The priceSettingDemandBidIdx,
	 * priceSettingSupplyBidIdx, and minPriceSettingDemand are optional in a ClearingResult object, they are only set if not null.
	 * 
	 * @param clearingResult result of market clearing
	 * @param demandBook book of demand bids
	 * @param supplyBook book of supply bids */
	public MarketClearingResult(ClearingDetails clearingResult, DemandOrderBook demandBook, SupplyOrderBook supplyBook) {
		tradedEnergyInMWH = clearingResult.tradedEnergyInMWH;
		marketPriceInEURperMWH = clearingResult.marketPriceInEURperMWH;
		this.demandBook = demandBook;
		this.supplyBook = supplyBook;
	}

	/** Set and update books, i.e. award contained bids according to their individual results
	 * 
	 * @param supplyBook Supply book used to clear the market
	 * @param demandBook Demand book used to clear the market
	 * @param distributionMethod defines method of how to award energy when multiple price-setting bids occur */
	public void setBooks(SupplyOrderBook supplyBook, DemandOrderBook demandBook, DistributionMethod distributionMethod) {
		this.demandBook = demandBook;
		this.supplyBook = supplyBook;
		updateBooks(distributionMethod);
	}

	/** update books, i.e. award contained bids according to their individual results using the provided distribution method */
	private void updateBooks(DistributionMethod distributionMethod) {
		supplyBook.updateAwardedPowerInBids(tradedEnergyInMWH, marketPriceInEURperMWH, distributionMethod);
		demandBook.updateAwardedPowerInBids(tradedEnergyInMWH, marketPriceInEURperMWH, distributionMethod);
	}

	/** @return updated demand order book used to clear the market */
	public DemandOrderBook getDemandBook() {
		return demandBook;
	}

	/** @return updated supply order book used to clear the market */
	public SupplyOrderBook getSupplyBook() {
		return supplyBook;
	}

	/** @return total awarded energy */
	public double getTradedEnergyInMWH() {
		return tradedEnergyInMWH;
	}

	/** @return uniform market clearing price */
	public double getMarketPriceInEURperMWH() {
		return marketPriceInEURperMWH;
	}

	/** @return total system cost from generation based on awarded bids and their associated marginal cost */
	public double getSystemCostTotalInEUR() {
		double totalSystemCost = 0;
		for (OrderBookItem item : supplyBook.getOrderBookItems()) {
			double awardedPower = item.getAwardedPower();
			double marginalCost = item.getMarginalCost();
			if (Double.isFinite(awardedPower) && Double.isFinite(marginalCost)) {
				totalSystemCost += awardedPower * marginalCost;
			}
		}
		return totalSystemCost;
	}

	/** @param marketPriceInEURperMWH the marketPriceInEURperMWH to set */
	public void setMarketPriceInEURperMWH(double marketPriceInEURperMWH) {
		this.marketPriceInEURperMWH = marketPriceInEURperMWH;
	}
}