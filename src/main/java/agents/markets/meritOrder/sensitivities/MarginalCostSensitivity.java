// SPDX-FileCopyrightText: 2021-2026 German Aerospace Center <amiris@dlr.de>
//
// SPDX-License-Identifier: Apache-2.0
package agents.markets.meritOrder.sensitivities;

import java.util.Comparator;
import agents.markets.meritOrder.books.DemandOrderBook;
import agents.markets.meritOrder.books.SupplyOrderBook;

/** Reflects the sensitivity of supply-side marginal cost with respect to changes in demand
 * 
 * @author Christoph Schimeczek */
public class MarginalCostSensitivity extends MeritOrderSensitivity {
	@Override
	protected void selectOrderBookItems(SupplyOrderBook supplyBookForecast, DemandOrderBook demandBookForecast) {
		extractOrders(supplyBookForecast);
	}

	@Override
	protected Comparator<SensitivityItem> getComparator() {
		return SensitivityItem.BY_PRICE_THEN_POWER;
	}

	/** @return the monetary value as a product of items power and marginal costs */
	@Override
	protected double calcMonetaryValue(SensitivityItem item) {
		return item.getPower() * item.getMarginal();
	}

	@Override
	protected double calcValueOfItemAtPower(SensitivityItem item, double power) {
		if (power > 0) {
			return item.getMonetaryOffset() + (power - item.getCumulatedLowerPower()) * item.getMarginal();
		} else {
			return -item.getMonetaryOffset() + (power + item.getCumulatedLowerPower()) * item.getMarginal();
		}
	}

	@Override
	public void updatePriceForecast(double electricityPriceForecast) {
		throw new RuntimeException("PriceSensitivity is incompatible to direct electricity price forecasts");
	}
}