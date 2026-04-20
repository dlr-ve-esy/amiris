// SPDX-FileCopyrightText: 2021-2026 German Aerospace Center <amiris@dlr.de>
//
// SPDX-License-Identifier: Apache-2.0
package agents.markets.meritOrder.sensitivities;

import java.util.Comparator;
import agents.markets.meritOrder.books.DemandOrderBook;
import agents.markets.meritOrder.books.SupplyOrderBook;

/** Reflects the sensitivity of the merit order price to bid changes
 * 
 * @author Christoph Schimeczek */
public class PriceSensitivity extends MeritOrderSensitivity {
	@Override
	protected void selectOrderBookItems(SupplyOrderBook supplyBookForecast, DemandOrderBook demandBookForecast) {
		extractOrders(supplyBookForecast);
		extractOrders(demandBookForecast);
	}

	@Override
	protected Comparator<SensitivityItem> getComparator() {
		return SensitivityItem.BY_PRICE;
	}

	@Override
	protected double calcMonetaryValue(SensitivityItem item) {
		return Double.NaN;
	}

	@Override
	protected double calcValueOfItemAtPower(SensitivityItem item, double power) {
		return item.getPrice();
	}

	/** Calculates expected energy price at given (dis-)charging amount
	 * 
	 * @param externalEnergyDelta &gt; 0: charging, &lt; 0: discharging
	 * @return expected energy price at given (dis-)charging amount */
	public double calcPriceForExternalEnergyDelta(double externalEnergyDelta) {
		int index = 0;
		SensitivityItem sensitivityItem;
		if (externalEnergyDelta > 0) {
			while (chargingItems.get(index).getCumulatedUpperPower() < externalEnergyDelta) {
				index++;
				if (index >= chargingItems.size()) {
					throw new RuntimeException("Sensitivity: No price could be determined for flexibility charging.");
				}
			}
			sensitivityItem = chargingItems.get(index);
		} else {
			while (dischargingItems.get(index).getCumulatedUpperPower() < -externalEnergyDelta) {
				index++;
				if (index >= dischargingItems.size()) {
					throw new RuntimeException("Sensitivity: No price could be found for flexibility discharging.");
				}
			}
			sensitivityItem = dischargingItems.get(index);
		}
		return sensitivityItem.getPrice();
	}

	/** @return price without any (dis-)charging activity in €/MWh */
	public double getPriceWithoutCharging() {
		return chargingItems.get(0).getPrice();
	}

	@Override
	public void updatePriceForecast(double electricityPriceForecast) {
		throw new RuntimeException("PriceSensitivity is incompatible to direct electricity price forecasts");
	}
}