// SPDX-FileCopyrightText: 2021-2026 German Aerospace Center <amiris@dlr.de>
//
// SPDX-License-Identifier: Apache-2.0
package agents.markets.meritOrder.sensitivities;

import java.util.Comparator;
import agents.markets.meritOrder.books.DemandOrderBook;
import agents.markets.meritOrder.books.SupplyOrderBook;

/** Despite being of type {@link MeritOrderSensitivity}, these objects store <b>no</b> sensitivity information but only a single
 * electricity price
 *
 * @author Christoph Schimeczek */
public class PriceNoSensitivity extends MeritOrderSensitivity {
	private double priceForecastInEURperMWH = Double.NaN;

	@Override
	protected void selectOrderBookItems(SupplyOrderBook supplyBookForecast, DemandOrderBook demandBookForecast) {
		throw new RuntimeException("Sensitivities not supported by price forecast with errors");
	}

	@Override
	protected Comparator<SensitivityItem> getComparator() {
		throw new RuntimeException("Sensitivities not supported by price forecast with errors");
	}

	@Override
	public double[] getValuesInSteps(int numberOfTransitionSteps) {
		throw new RuntimeException("Sensitivities not supported by price forecast with errors");
	}

	@Override
	protected double calcMonetaryValue(SensitivityItem item) {
		throw new RuntimeException("Sensitivities not supported by price forecast with errors");
	}

	@Override
	protected double calcValueOfItemAtPower(SensitivityItem item, double power) {
		throw new RuntimeException("Sensitivities not supported by price forecast with errors");
	}

	@Override
	public void updatePriceForecast(double electricityPriceForecast) {
		this.priceForecastInEURperMWH = electricityPriceForecast;
	}

	@Override
	public boolean isEmpty() {
		return Double.isNaN(priceForecastInEURperMWH);
	}

	/** @return forecast price in €/MWh */
	public double getPriceForecast() {
		return priceForecastInEURperMWH;
	}
}