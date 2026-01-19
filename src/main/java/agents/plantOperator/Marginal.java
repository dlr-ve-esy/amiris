// SPDX-FileCopyrightText: 2026 German Aerospace Center <amiris@dlr.de>
//
// SPDX-License-Identifier: Apache-2.0
package agents.plantOperator;

import java.util.Comparator;
import de.dlr.gitlab.fame.communication.transfer.ComponentCollector;
import de.dlr.gitlab.fame.communication.transfer.ComponentProvider;
import de.dlr.gitlab.fame.communication.transfer.Portable;

/** Transmits a single power-marginalCost pair
 *
 * @author Christoph Schimeczek */
public class Marginal implements Portable {
	/** Sorts by costs in ascending order */
	public static Comparator<Marginal> byCostAscending = new Comparator<Marginal>() {
		@Override
		public int compare(Marginal m1, Marginal m2) {
			return Double.compare(m1.marginalCostInEURperMWH, m2.marginalCostInEURperMWH);
		}
	};

	/** the actual power potential */
	private double powerPotentialInMW;
	/** the actual marginal cost value */
	private double marginalCostInEURperMWH;

	/** required for {@link Portable}s */
	public Marginal() {};

	/** Constructs new {@link Marginal}
	 * 
	 * @param powerPotentialInMW true power production potential by electricity generation unit in MW
	 * @param marginalCostInEURperMWH marginal power production costs in EUR per MWh */
	public Marginal(double powerPotentialInMW, double marginalCostInEURperMWH) {
		this.powerPotentialInMW = powerPotentialInMW;
		this.marginalCostInEURperMWH = marginalCostInEURperMWH;
	}

	@Override
	public void addComponentsTo(ComponentCollector collector) {
		collector.storeDoubles(powerPotentialInMW, marginalCostInEURperMWH);
	}

	@Override
	public void populate(ComponentProvider provider) {
		powerPotentialInMW = provider.nextDouble();
		marginalCostInEURperMWH = provider.nextDouble();
	}

	/** @return the actual power potential */
	public double getPowerPotentialInMW() {
		return powerPotentialInMW;
	}

	/** @return the actual marginal cost value */
	public double getMarginalCostInEURperMWH() {
		return marginalCostInEURperMWH;
	}
}