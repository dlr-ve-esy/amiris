// SPDX-FileCopyrightText: 2026 German Aerospace Center <amiris@dlr.de>
//
// SPDX-License-Identifier: Apache-2.0
package agents.markets.meritOrder;

import de.dlr.gitlab.fame.communication.transfer.ComponentCollector;
import de.dlr.gitlab.fame.communication.transfer.ComponentProvider;
import de.dlr.gitlab.fame.communication.transfer.Portable;

/** A bid offering or requesting a given amount of energy for a specified price
 * 
 * @author Martin Klein, Christoph Schimeczek, A. Achraf El Ghazi */
public class Bid implements Portable, Cloneable {
	private double offerPriceInEURperMWH;
	private double energyAmountInMWH;
	private double marginalCostInEURperMWH = Double.NaN;

	/** required for {@link Portable}s */
	public Bid() {}

	/** Creates a {@link Bid}
	 *
	 * @param energyAmountInMWH amount of energy requested or offered (depending on Type of this Bid)
	 * @param offerPriceInEURperMWH maximum / minimum offer price (depending on Type of this Bid)
	 * @param marginalCostInEURperMWH actual marginal cost associated with this Bid */
	public Bid(double energyAmountInMWH, double offerPriceInEURperMWH, double marginalCostInEURperMWH) {
		this.offerPriceInEURperMWH = offerPriceInEURperMWH;
		this.energyAmountInMWH = energyAmountInMWH;
		this.marginalCostInEURperMWH = marginalCostInEURperMWH;
	}

	/** Creates a {@link Bid} with marginalCost=NaN
	 * 
	 * @param energyAmountInMWH amount of energy requested or offered (depending on Type of this Bid)
	 * @param offerPriceInEURperMWH maximum / minimum offer price (depending on Type of this Bid) */
	public Bid(double energyAmountInMWH, double offerPriceInEURperMWH) {
		this.offerPriceInEURperMWH = offerPriceInEURperMWH;
		this.energyAmountInMWH = energyAmountInMWH;
	}

	@Override
	public String toString() {
		return energyAmountInMWH + " MWh @ " + offerPriceInEURperMWH + " €/MWh";
	}

	/** @return maximum / minimum offer price (depending on Type of this Bid) */
	public double getOfferPriceInEURperMWH() {
		return offerPriceInEURperMWH;
	}

	/** @return amount of energy requested or offered (depending on Type of this Bid) */
	public double getEnergyAmountInMWH() {
		return energyAmountInMWH;
	}

	/** set amount of energy requested or offered (depending on Type of this Bid)
	 * 
	 * @param energyAmountInMWH energy amount to set (in MHW). */
	public void setEnergyAmountInMWH(double energyAmountInMWH) {
		this.energyAmountInMWH = energyAmountInMWH;
	}

	/** @return actual marginal cost associated with this Bid */
	public double getMarginalCost() {
		return marginalCostInEURperMWH;
	}

	/** required for {@link Portable}s */
	@Override
	public void addComponentsTo(ComponentCollector collector) {
		collector.storeDoubles(offerPriceInEURperMWH, energyAmountInMWH, marginalCostInEURperMWH);
	}

	/** required for {@link Portable}s */
	@Override
	public void populate(ComponentProvider provider) {
		offerPriceInEURperMWH = provider.nextDouble();
		energyAmountInMWH = provider.nextDouble();
		marginalCostInEURperMWH = provider.nextDouble();
	}

	/** @return a deep-copy of {@link Bid} */
	public Bid clone() {
		return new Bid(energyAmountInMWH, offerPriceInEURperMWH, marginalCostInEURperMWH);
	}

	/** @return true if this object's content matches that of the given Bid.
	 * @param bid to check for match. */
	public boolean matches(Bid bid) {
		return bid.offerPriceInEURperMWH == offerPriceInEURperMWH &&
				bid.energyAmountInMWH == energyAmountInMWH &&
				bid.marginalCostInEURperMWH == marginalCostInEURperMWH;
	}
}
