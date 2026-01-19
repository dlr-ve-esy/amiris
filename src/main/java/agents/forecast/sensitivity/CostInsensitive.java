// SPDX-FileCopyrightText: 2026 German Aerospace Center <amiris@dlr.de>
//
// SPDX-License-Identifier: Apache-2.0
package agents.forecast.sensitivity;

import agents.markets.meritOrder.MarketClearingResult;

/** Assessment of cost / revenues associated with added demand / supply; assumes the price does not change
 * 
 * @author Christoph Schimeczek */
public class CostInsensitive implements MarketClearingAssessment {
	private static final double MAX_ENERGY_IN_MWH = 1E10;
	private double electricityPriceInEURperMWH = 0;

	/** Directly set the electricity price used for sensitivity calculations
	 * 
	 * @param electricityPriceInEURperMWH to be used */
	public void setPrice(double electricityPriceInEURperMWH) {
		this.electricityPriceInEURperMWH = electricityPriceInEURperMWH;
	}

	@Override
	public void assess(MarketClearingResult clearingResult) {
		electricityPriceInEURperMWH = clearingResult.getMarketPriceInEURperMWH();
	}

	@Override
	public double[] getDemandSensitivityPowers() {
		return new double[] {0., MAX_ENERGY_IN_MWH};
	}

	@Override
	public double[] getDemandSensitivityValues() {
		return new double[] {0., electricityPriceInEURperMWH * MAX_ENERGY_IN_MWH};
	}

	@Override
	public double[] getSupplySensitivityPowers() {
		return new double[] {0., MAX_ENERGY_IN_MWH};
	}

	@Override
	public double[] getSupplySensitivityValues() {
		return new double[] {0., electricityPriceInEURperMWH * MAX_ENERGY_IN_MWH};
	}
}
