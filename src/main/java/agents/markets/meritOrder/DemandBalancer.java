// SPDX-FileCopyrightText: 2026 German Aerospace Center <amiris@dlr.de>
//
// SPDX-License-Identifier: Apache-2.0
package agents.markets.meritOrder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import agents.markets.DayAheadMarket;
import agents.markets.MarketCoupling;
import agents.markets.meritOrder.MeritOrderKernel.MeritOrderClearingException;
import agents.markets.meritOrder.books.DemandOrderBook;
import agents.markets.meritOrder.books.OrderBookItem;
import agents.markets.meritOrder.books.SupplyOrderBook;
import agents.markets.meritOrder.books.TransferOrderBook;
import communications.portable.CouplingData;

/** Encapsulates the actual market coupling algorithm; Dispatch the demand among energy exchanges in order to maximise the total
 * welfare. To this end, the algorithm reduces price differences of connected markets by transferring demand bids.
 * 
 * @author A. Achraf El Ghazi, Felix Nitsch */
public class DemandBalancer {
	private static final Logger logger = LoggerFactory.getLogger(DemandBalancer.class);
	/** Minimal amount of energy to be shifted between markets */
	static final double MIN_SHIFT_AMOUNT_IN_MWH = 0.1;
	/** Threshold at which markets are regarded as effectively empty */
	static final double MIN_TRADED_ENERGY_IN_MWH = 1E-6;

	private class DemandShiftResult {
		public Long expensiveMarketId;
		public Long cheapMarketId;
		public Double shiftedDemand;
		public DemandOrderBook newDemandOfOrigin;
		public DemandOrderBook newDemandOfTarget;
		public TransferOrderBook transferBook;

		public DemandShiftResult(Long expensiveMarketId, Long cheapMarketId, Double shiftedDemand,
				DemandOrderBook newDemandOfOrigin, DemandOrderBook newDemandOfTarget,
				TransferOrderBook transferBook) {
			this.expensiveMarketId = expensiveMarketId;
			this.cheapMarketId = cheapMarketId;
			this.shiftedDemand = shiftedDemand;
			this.newDemandOfOrigin = newDemandOfOrigin;
			this.newDemandOfTarget = newDemandOfTarget;
			this.transferBook = transferBook;
		}
	}

	/** Sets the offset, that is added to the maximal demand shift, that does not lead to price change of the involved markets. The
	 * addition of this offset first guarantee price change */
	private static final String CLEARING_ID = "MarketCoupling - DemandBalancer:";
	private final double minEffectiveDemandOffset;
	private Map<Long, CouplingData> couplingRequests;
	private Map<Long, ClearingDetails> clearingResults = new HashMap<>();

	/** Creates new {@link DemandBalancer}
	 * 
	 * @param minEffectiveDemandOffset added to the demand shift in order to enforce price changes */
	public DemandBalancer(double minEffectiveDemandOffset) {
		this.minEffectiveDemandOffset = minEffectiveDemandOffset;
	}

	/** Maximises the overall welfare by balancing the demand among all participant {@link DayAheadMarket}s under given transmission
	 * capacity constraints.<br>
	 * The algorithm guarantees correctness and termination, within some tolerance parameters, by computing and applying two
	 * criteria: (1) shifting only the minimal-effective-demand at a time, and (2) processing always the most-effective-pair of
	 * {EnergyExchange}s first. The minimal-effective-demand is the maximum demand that can be shifted from the more expensive
	 * EnergyExchange to the less one without reducing the clearing-price of the more expensive one plus
	 * {@link #minEffectiveDemandOffset}. The most-effective-pair are the two {@link DayAheadMarket}s with the largest
	 * market-clearing-price difference and allow to shift the minimal-effective-demand from the more expensive to the less
	 * (maximal-non-effective-demand). The tolerance parameters are: + {@link #minEffectiveDemandOffset} which sets how much demand
	 * is added to the computed maximal-non-effective-demand to achieve price change via demand shifting.
	 * 
	 * @param couplingRequests map of market id to CouplingData of all markets that have to be coupled - to be updated by this
	 *          method */
	public void balance(Map<Long, CouplingData> couplingRequests) {
		clearingResults.clear();
		this.couplingRequests = couplingRequests;
		try {
			Map<Long, List<Long>> couplingPartners = calculateCouplingPartners();
			initialiseClearingResults(couplingPartners);
			logger.trace("Start optimization (energy cost: " + calcEnergyCost() + ")");

			DemandShiftResult demandShiftResult = null;
			while (true) {
				demandShiftResult = getNextCouplingPair(couplingPartners);
				if (demandShiftResult == null) {
					break;
				}
				applyDemandShiftFromTo(demandShiftResult);
			}
		} catch (MeritOrderClearingException e) {
			throw new RuntimeException(CLEARING_ID + " " + e.getMessage());
		}
	}

	/** @return for each candidate EnergyExchange a list of partner EnergyExchange(s) it can get electricity from */
	private Map<Long, List<Long>> calculateCouplingPartners() {
		Map<Long, List<Long>> couplingPartners = new HashMap<>();
		for (long candidateId : couplingRequests.keySet()) {
			CouplingData candidateData = couplingRequests.get(candidateId);
			List<Long> partners = new ArrayList<>();
			for (long partnerId : couplingRequests.keySet()) {
				if (partnerId != candidateId) {
					CouplingData partnerData = couplingRequests.get(partnerId);
					double transmissionCapacity = partnerData.getTransmissionTo(candidateData.getOrigin());
					if (transmissionCapacity > 0) {
						partners.add(partnerId);
					}
				}
			}
			couplingPartners.put(candidateId, partners);
		}
		return couplingPartners;
	}

	/** initialises {@link #clearingResults} for all energy exchanges in this market coupling process
	 * 
	 * @throws MeritOrderClearingException if market clearing failed */
	private void initialiseClearingResults(Map<Long, List<Long>> couplingPartners) throws MeritOrderClearingException {
		for (Long id : couplingPartners.keySet()) {
			getClearingResult(id);
		}
	}

	/** @return energy cost (traded energy * price) among all energy exchanges involved in the market coupling */
	private double calcEnergyCost() {
		double energyCost = 0.0;
		for (ClearingDetails result : clearingResults.values()) {
			energyCost += result.tradedEnergyInMWH * result.marketPriceInEURperMWH;
		}
		return energyCost;
	}

	/** Finds the next best EnergyExchange(s) pair and calculates their demand redistribution
	 * 
	 * @param couplingPartners all potential coupling partners for each candidate exchange
	 * @return the next best pair of EnergyExchanges and their demand redistribution or null if no valid pair can be found
	 * @throws MeritOrderClearingException if market clearing failed */
	private DemandShiftResult getNextCouplingPair(Map<Long, List<Long>> couplingPartners)
			throws MeritOrderClearingException {
		DemandShiftResult bestDemandShift = null;
		double largestPriceDiff = 0;
		for (Long candidateId : couplingRequests.keySet()) {
			DemandShiftResult tmpDemandShift = getBestCouplingPartner(candidateId, couplingPartners.get(candidateId));
			if (tmpDemandShift != null) {
				double priceDifference = calcPriceDifference(tmpDemandShift.expensiveMarketId, tmpDemandShift.cheapMarketId);
				if (priceDifference > largestPriceDiff) {
					bestDemandShift = tmpDemandShift;
					largestPriceDiff = priceDifference;
				}
			}
		}
		return bestDemandShift;
	}

	/** Returns the best demand shift for a given candidate EnergyExchange.<br>
	 * Ensures that the price of the selected partner exchange is smaller or equal to the price of the candidate EnergyExchange.
	 * 
	 * @param candidateId ID of candidate exchange to find the best demand shifting for
	 * @param couplingPartners list of all available coupling partners for the given candidate
	 * @return best demand shift or null if no valid demand shifting partner is available
	 * @throws MeritOrderClearingException if market clearing failed */
	private DemandShiftResult getBestCouplingPartner(Long candidateId, List<Long> couplingPartners)
			throws MeritOrderClearingException {
		DemandShiftResult bestDemandShift = null;
		double candidatePrice = getClearingResult(candidateId).marketPriceInEURperMWH;
		double largestPriceDiff = 0;
		for (Long partnerId : couplingPartners) {
			double partnerPrice = getClearingResult(partnerId).marketPriceInEURperMWH;
			DemandShiftResult tmpDemandShift = calcMinDemandShiftCausingPriceChange(candidateId, partnerId);
			if (tmpDemandShift != null) {
				double priceDifference = candidatePrice - partnerPrice;
				if (priceDifference > largestPriceDiff) {
					bestDemandShift = tmpDemandShift;
					largestPriceDiff = priceDifference;
				}
			}
		}
		return bestDemandShift;
	}

	/** Returns the market clearing result of the specified EnergyExchange. For the actual computation of the market clearing it
	 * uses {@link MarketCoupling #calculateMarketClearing(DemandOrderBook, SupplyOrderBook)}. However, before performing the actual
	 * computation it checks if a market clearing for the specified EnergyExchange was already computed, if so, it returns it. The
	 * result of any new market clearing computation is stored in {@link MarketCoupling #clearingResults}.
	 * 
	 * @param exchangeId to get the market clearing result for
	 * @return the market clearing result of the given exchange
	 * @throws MeritOrderClearingException if market clearing failed */
	private ClearingDetails getClearingResult(Long exchangeId) throws MeritOrderClearingException {
		ClearingDetails clearingResult = clearingResults.get(exchangeId);
		if (clearingResult == null) {
			CouplingData request = couplingRequests.get(exchangeId);
			clearingResult = MarketClearing.internalClearing(request.getSupplyOrderBook(), request.getDemandOrderBook());
			clearingResults.put(exchangeId, clearingResult);
		}
		return clearingResult;
	}

	/** Computes the minimal demand shift from the more expensive DemandOrderBook to the less expensive one that causes a price
	 * change. To this end, the maximum demand shift without price change is calculated and is incremented by
	 * MIN_EFFECTIVE_DEMAND_OFFSET.
	 * 
	 * @param expensiveMarketId agentId of {@link DayAheadMarket} with higher price
	 * @param cheapMarketId agentId of {@link DayAheadMarket} with lower price
	 * @return result of the applied demand shift for both involved {@link DayAheadMarket}s or null if no meaningful shifting can be
	 *         applied
	 * @throws MeritOrderClearingException if market clearing failed */
	private DemandShiftResult calcMinDemandShiftCausingPriceChange(Long expensiveMarketId, Long cheapMarketId)
			throws MeritOrderClearingException {
		CouplingData expensiveMarketData = couplingRequests.get(expensiveMarketId);
		CouplingData cheapMarketData = couplingRequests.get(cheapMarketId);
		double transmissionCapacity = cheapMarketData.getTransmissionTo(expensiveMarketData.getOrigin());
		if (transmissionCapacity <= 0) {
			return null;
		}

		ClearingDetails clearingOfExpensive = getClearingResult(expensiveMarketId);
		ClearingDetails clearingOfCheap = getClearingResult(cheapMarketId);
		if (clearingOfExpensive.marketPriceInEURperMWH <= clearingOfCheap.marketPriceInEURperMWH) {
			return null;
		}

		if (clearingOfExpensive.minPriceSettingDemand <= 0) {
			return null;
		}

		if (clearingOfExpensive.tradedEnergyInMWH < MIN_TRADED_ENERGY_IN_MWH) {
			return null;
		}

		double toShiftDemand = clearingOfExpensive.minPriceSettingDemand + minEffectiveDemandOffset;
		toShiftDemand = Math.min(transmissionCapacity, toShiftDemand);

		double availableSupplyOfCheap = cheapMarketData.getSupplyOrderBook().getCumulatePowerOfItems();
		double requestedDemandOfCheap = cheapMarketData.getDemandOrderBook().getCumulatePowerOfItems();
		if (availableSupplyOfCheap <= requestedDemandOfCheap) {
			return null;
		}

		if (availableSupplyOfCheap < requestedDemandOfCheap + toShiftDemand) {
			toShiftDemand = availableSupplyOfCheap - requestedDemandOfCheap;
		}
		toShiftDemand = Math.floor(toShiftDemand * 10) / 10.0;
		if (toShiftDemand < MIN_SHIFT_AMOUNT_IN_MWH) {
			return null;
		}

		int priceSettingDemandBidIdx = clearingOfExpensive.priceSettingDemandBidIdx;
		DemandShiftResult demandShiftResult = shiftDemand(expensiveMarketId, cheapMarketId, toShiftDemand,
				priceSettingDemandBidIdx, expensiveMarketData.getDemandOrderBook(), cheapMarketData.getDemandOrderBook());
		ClearingDetails newClearingOfExpensive = MarketClearing.internalClearing(expensiveMarketData.getSupplyOrderBook(),
				demandShiftResult.newDemandOfOrigin);
		ClearingDetails newClearingOfCheap = MarketClearing.internalClearing(cheapMarketData.getSupplyOrderBook(),
				demandShiftResult.newDemandOfTarget);
		if (newClearingOfExpensive.marketPriceInEURperMWH < newClearingOfCheap.marketPriceInEURperMWH) {
			return null;
		}
		return demandShiftResult;
	}

	/** Shifts the given amount of demand from the expensive DemandOrderBook to the cheap one. The shift begins at the demand bid
	 * with the given index and proceeds backwards. The shifting result does not affect the given {DemandOrderBook}s, it is rather
	 * returned as a DemandShiftResult object.
	 * 
	 * @param expensiveMarketId of the expensive market
	 * @param cheapMarketId of the cheap market
	 * @param demandToShift amount of demand to shift
	 * @param startingBidIndex index of the demand-setting bid
	 * @param demandBookExpensive to shift demand from
	 * @param demandBookCheap to shift demand to
	 * @return result of the demand shift */
	private DemandShiftResult shiftDemand(Long expensiveMarketId, Long cheapMarketId, double demandToShift,
			int startingBidIndex, DemandOrderBook demandBookExpensive, DemandOrderBook demandBookCheap) {
		DemandOrderBook newDemandBookExpensive = new DemandOrderBook();
		DemandOrderBook newDemandBookCheap = demandBookCheap.clone();
		TransferOrderBook transferBook = new TransferOrderBook();
		List<OrderBookItem> orderBookItems = demandBookExpensive.clone().getOrderBookItems();

		shiftDemand_nonAwardedBids(orderBookItems, startingBidIndex, newDemandBookExpensive);
		shiftDemand_AwardedBids(orderBookItems, startingBidIndex, demandToShift, newDemandBookExpensive,
				newDemandBookCheap, transferBook);

		return new DemandShiftResult(expensiveMarketId, cheapMarketId, demandToShift, newDemandBookExpensive,
				newDemandBookCheap, transferBook);
	}

	/** Handles the non-awarded demand bids, i.e., demand bids right from the cut of the demand and supply curves in the expensive
	 * merit-order.
	 * 
	 * @param orderBookItems list of demand bids to handle
	 * @param startingBidIndex index of the demand-setting bid
	 * @param newDemandBookExpensive reference to the new DemandOrderBook of the market from where the demand is shifted */
	private void shiftDemand_nonAwardedBids(List<OrderBookItem> orderBookItems, int startingBidIndex,
			DemandOrderBook newDemandBookExpensive) {
		for (int i = orderBookItems.size() - 1; i > startingBidIndex; i--) {
			OrderBookItem item = orderBookItems.get(i);
			newDemandBookExpensive.addBid(item.getBid(), item.getTraderUuid());
		}
	}

	/** Handles the awarded demand bids, i.e., demand bids left from the cut of the demand and supply curves in the merit-order.
	 * 
	 * @param orderBookItems list of demand bids to handle
	 * @param startingBidIndex index of the demand-setting bid
	 * @param demandToShift demand amount that has to be shifted
	 * @param newDemandBookExpensive reference to the new DemandOrderBook of the market from where the demand is shifted
	 * @param newDemandBookCheap reference to the new DemandOrderBook of the market to where the demand is shifted
	 * @param transferBook reference to the new transfer book that stores the shifted bids */
	private void shiftDemand_AwardedBids(List<OrderBookItem> orderBookItems, int startingBidIndex, double demandToShift,
			DemandOrderBook newDemandBookExpensive, DemandOrderBook newDemandBookCheap, TransferOrderBook transferBook) {
		double currentShiftedDemand = 0;
		double previousShiftedDemand = 0;
		for (int i = startingBidIndex; i >= 0; i--) {
			OrderBookItem item = orderBookItems.get(i);
			Bid bid = item.getBid();
			double thisDemand = bid.getEnergyAmountInMWH();
			if (thisDemand == 0) {
				continue;
			}
			if (currentShiftedDemand < demandToShift) {
				previousShiftedDemand = currentShiftedDemand;
				currentShiftedDemand += thisDemand;
				if (currentShiftedDemand > demandToShift) {
					Bid[] bids = splitBid(bid, demandToShift - previousShiftedDemand);
					newDemandBookExpensive.addBid(bids[0], item.getTraderUuid());
					newDemandBookCheap.addBid(bids[1], item.getTraderUuid());
					transferBook.addBid(bids[1], item.getTraderUuid());
					currentShiftedDemand = demandToShift;
				} else {
					newDemandBookCheap.addBid(bid, item.getTraderUuid());
					transferBook.addBid(bid, item.getTraderUuid());
				}
			} else {
				newDemandBookExpensive.addBid(bid, item.getTraderUuid());
			}
		}
	}

	/** Splits a given bid into two bids based on the given parameters. The sum of the energy amounts of the resulting bids equals
	 * the original Bid. All other parameters are copied from the original bid.
	 * 
	 * @param bidToSplit bid to split
	 * @param energyToShift total amount of energy assigned to the shifted bid
	 * @return an array with two bids. At index 0: the bid part that remain in the more expensive market and at index 1: the bid
	 *         part that will be shifted to the less expensive market */
	private Bid[] splitBid(Bid bidToSplit, double energyToShift) {
		double bidPartToRemain = bidToSplit.getEnergyAmountInMWH() - energyToShift;
		Bid remainingBid = bidToSplit;
		Bid shiftingBid = bidToSplit.clone();
		remainingBid.setEnergyAmountInMWH(bidPartToRemain);
		shiftingBid.setEnergyAmountInMWH(energyToShift);
		return new Bid[] {remainingBid, shiftingBid};
	}

	/** @return price difference between candidate and partner markets
	 * @throws MeritOrderClearingException if market clearing failed */
	private double calcPriceDifference(Long candidateId, Long partnerId) throws MeritOrderClearingException {
		return getClearingResult(candidateId).marketPriceInEURperMWH - getClearingResult(partnerId).marketPriceInEURperMWH;
	}

	/** Shifts the energy demand specified in the right argument of the demandShiftResult triple from the bestExpId EnergyExchange
	 * to the bestCheapExId EnergyExchange. The resulting new demand order books for the bestExpId and the bestCheapExId
	 * EnergyExchange(s) are already provided by the demandShiftResult argument
	 * 
	 * @param demandShiftResult demand shift result to be applied
	 * @param expensiveExchangeId exchange to shift demand from
	 * @param cheapExchangeId exchange to shift demand to
	 * @throws MeritOrderClearingException if market clearing failed */
	private void applyDemandShiftFromTo(DemandShiftResult demandShiftResult) throws MeritOrderClearingException {
		CouplingData dataExpensive = couplingRequests.get(demandShiftResult.expensiveMarketId);
		CouplingData dataCheap = couplingRequests.get(demandShiftResult.cheapMarketId);
		SupplyOrderBook supplyBookExpensive = dataExpensive.getSupplyOrderBook();
		SupplyOrderBook supplyBookCheap = dataCheap.getSupplyOrderBook();
		double transmissionCapacity = dataCheap.getTransmissionTo(dataExpensive.getOrigin());

		DemandOrderBook newDemandBookExpensive = demandShiftResult.newDemandOfOrigin;
		DemandOrderBook newDemandBookCheap = demandShiftResult.newDemandOfTarget;
		TransferOrderBook transferBook = demandShiftResult.transferBook;
		double shiftedDemand = demandShiftResult.shiftedDemand;

		ClearingDetails newClearingExpensive = MarketClearing.internalClearing(supplyBookExpensive, newDemandBookExpensive);
		ClearingDetails newClearingCheap = MarketClearing.internalClearing(supplyBookCheap, newDemandBookCheap);

		ClearingDetails clearingResultExpensive = clearingResults.get(demandShiftResult.expensiveMarketId);
		ClearingDetails clearingResultCheap = clearingResults.get(demandShiftResult.cheapMarketId);

		dataExpensive.setDemandOrderBook(newDemandBookExpensive);
		dataExpensive.updateImportBook(transferBook);
		clearingResults.put(demandShiftResult.expensiveMarketId, newClearingExpensive);

		dataCheap.setDemandOrderBook(newDemandBookCheap);
		double newTransmissionCapacity = transmissionCapacity - shiftedDemand;
		dataCheap.updateTransmissionBook(dataExpensive.getOrigin(), newTransmissionCapacity);
		dataCheap.updateExportBook(transferBook);
		clearingResults.put(demandShiftResult.cheapMarketId, newClearingCheap);

		logger.trace(oneOptimizationStepSummary(
				demandShiftResult.expensiveMarketId, demandShiftResult.cheapMarketId, shiftedDemand,
				clearingResultExpensive.marketPriceInEURperMWH, newClearingExpensive.marketPriceInEURperMWH,
				clearingResultCheap.marketPriceInEURperMWH, newClearingCheap.marketPriceInEURperMWH,
				transmissionCapacity, newTransmissionCapacity));
	}

	/** Returns a string summary of one optimization step
	 * 
	 * @param expensiveMarketId ID of the market that demand is shifted from
	 * @param cheapMarketId ID of the market that demand is shifted to
	 * @param shiftedDemand shifted demand
	 * @param expensiveMarketPriceInEURperMWH electricity price in the market that demand is shifted from before shifting
	 * @param newExpensiveMarketPriceInEURperMWH electricity price in the market that demand is shifted from after shifting
	 * @param cheapMarketPriceInEURperMWH electricity price in the market that demand is shifted to before shifting
	 * @param newCheapMarketPriceInEURperMWH electricity price in the market that demand is shifted to afer shifting
	 * @param transmissionCapacity available electricity transmission capacity from the market that demand is shifted to to the
	 *          demand is shifted from before shifting
	 * @param newTransmissionCapacity available electricity transmission capacity from the market that demand is shifted to to the
	 *          demand is shifted from after shifting
	 * @return */
	private String oneOptimizationStepSummary(
			long expensiveMarketId, long cheapMarketId, double shiftedDemand,
			double expensiveMarketPriceInEURperMWH, double newExpensiveMarketPriceInEURperMWH,
			double cheapMarketPriceInEURperMWH, double newCheapMarketPriceInEURperMWH,
			double transmissionCapacity, double newTransmissionCapacity) {
		return "- Done, best demandShift (" + calcEnergyCost() + "): "
				+ expensiveMarketId + " -(" + shiftedDemand + ")-> " + cheapMarketId
				+ ": (" + expensiveMarketPriceInEURperMWH + ", " + newExpensiveMarketPriceInEURperMWH + ")"
				+ ": (" + cheapMarketPriceInEURperMWH + ", " + newCheapMarketPriceInEURperMWH + ")"
				+ ": (" + transmissionCapacity + ", " + newTransmissionCapacity + ")";
	}
}
