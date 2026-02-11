// SPDX-FileCopyrightText: 2024-2026 German Aerospace Center <amiris@dlr.de>
//
// SPDX-License-Identifier: Apache-2.0
package agents.markets.meritOrder;

/** Detailed result of market clearing
 * 
 * @author Christoph Schimeczek, A. Achraf El Ghazi */
public class ClearingDetails {
	/** total traded energy on market */
	public final double tradedEnergyInMWH;
	/** settled market price after clearing */
	public final double marketPriceInEURperMWH;

	/** The first bid in the demand-bid curve that is below or equal the supply-bid curve. Here: the bids of the demand-bid curve
	 * are sorted ascending by cumulatedPower and then descending by offerPrice, and the bids of the supply-bid curve are sorted
	 * ascending by cumulatedPower and then by offerPrice */
	public final Integer priceSettingDemandBidIdx;
	/** The first bid in the supply-bid curve that is over or equal the demand-bid curve. Here: the bids of the demand-bid curve are
	 * sorted ascending by cumulatedPower and then descending by offerPrice, and the bids of the supply-bid curve are sorted
	 * ascending by cumulatedPower and then by offerPrice */
	public final Integer priceSettingSupplyBidIdx;
	/** The maximum demand amount that can be reduced from the entire demand without reducing the clearing-price */
	public final double minPriceSettingDemand;

	/** Creates {@link ClearingDetails}
	 * 
	 * @param tradedEnergyInMWH total traded energy (in MWH)
	 * @param marketPriceInEURperMWH electricity clearing price (in EUR per MWH) */
	public ClearingDetails(double tradedEnergyInMWH, double marketPriceInEURperMWH) {
		this.tradedEnergyInMWH = tradedEnergyInMWH;
		this.marketPriceInEURperMWH = marketPriceInEURperMWH;
		this.priceSettingDemandBidIdx = null;
		this.priceSettingSupplyBidIdx = null;
		this.minPriceSettingDemand = Double.NaN;
	}

	/** Creates {@link ClearingDetails}
	 * 
	 * @param tradedEnergyInMWH total traded energy (in MWH)
	 * @param marketPriceInEURperMWH electricity clearing price (in EUR per MWH)
	 * @param priceSettingDemandBidIdx index of the price setting demand bid, i.e., last awarded demand bid
	 * @param priceSettingSupplyBidIdx index of the price setting supply bid, i.e., last awarded supply bid
	 * @param minPriceSettingDemand the maximal amount that the demand can be reduced without causing a price change */
	public ClearingDetails(double tradedEnergyInMWH, double marketPriceInEURperMWH,
			Integer priceSettingDemandBidIdx,
			Integer priceSettingSupplyBidIdx,
			Double minPriceSettingDemand) {
		this.tradedEnergyInMWH = tradedEnergyInMWH;
		this.marketPriceInEURperMWH = marketPriceInEURperMWH;
		this.priceSettingDemandBidIdx = priceSettingDemandBidIdx;
		this.priceSettingSupplyBidIdx = priceSettingSupplyBidIdx;
		this.minPriceSettingDemand = minPriceSettingDemand;
	}

}
