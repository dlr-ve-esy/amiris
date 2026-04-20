// SPDX-FileCopyrightText: 2021-2026 German Aerospace Center <amiris@dlr.de>
//
// SPDX-License-Identifier: Apache-2.0
package agents.markets.meritOrder.sensitivities;

import java.util.Comparator;

/** Represents an item of a merit-order sensitivity
 * 
 * @author Christoph Schimeczek */
public class SensitivityItem {
	/** Compares {@link SensitivityItem}s by price */
	public static final Comparator<SensitivityItem> BY_PRICE = Comparator.comparing(item -> item.price);
	/** Compares {@link SensitivityItem}s by power */
	public static final Comparator<SensitivityItem> BY_POWER = Comparator.comparing(item -> item.power);
	/** Compares {@link SensitivityItem}s by price and then power */
	public static final Comparator<SensitivityItem> BY_PRICE_THEN_POWER = BY_PRICE.thenComparing(BY_POWER);

	private final double power;
	private final double price;
	private final double marginal;
	private double cumulatedLowerPower = Double.NaN;
	private double monetaryOffset = Double.NaN;
	private double upperMonetaryValue = Double.NaN;

	/** Creates a {@link SensitivityItem}
	 * 
	 * @param power delta covered by this item
	 * @param price valid at this power delta
	 * @param marginal valid for this power delta */
	public SensitivityItem(double power, double price, double marginal) {
		this.power = power;
		this.price = price;
		this.marginal = marginal;
	}

	/** @return the block power of this item */
	public double getPower() {
		return power;
	}

	/** @return the price in EUR/MWh valid for this power block */
	public double getPrice() {
		return price;
	}

	/** @return the marginal cost in EUR/MWh for this power block */
	public double getMarginal() {
		return marginal;
	}

	/** set the cumulated power at the beginning this power block
	 * 
	 * @param cumulatedPower lower value of cumulated power for this item */
	public void setCumulatedLowerPower(double cumulatedPower) {
		this.cumulatedLowerPower = cumulatedPower;
	}

	/** @return the cumulated power at the beginning this power block */
	public double getCumulatedLowerPower() {
		return cumulatedLowerPower;
	}

	/** @return the cumulated power at the end this power block */
	public double getCumulatedUpperPower() {
		return cumulatedLowerPower + power;
	}

	/** @return the cumulated monetary value at the beginning this power block */
	public double getMonetaryOffset() {
		return monetaryOffset;
	}

	/** set the cumulated monetary value at the beginning this power block
	 * 
	 * @param monetaryOffset total monetary value at the beginning this power block */
	public void setMonetaryOffset(double monetaryOffset) {
		this.monetaryOffset = monetaryOffset;
	}

	public void setUpperMonetaryValue(double upperMonetaryValue) {
		this.upperMonetaryValue = upperMonetaryValue;
	}

	/** @return the cumulated monetary value at the end of this power block */
	public double getUpperMonetaryValue() {
		return upperMonetaryValue;
	}

	@Override
	public String toString() {
		return String.format("(%.2f MW, %.2f EUR/MWh, %.2f EUR/MWh)", cumulatedLowerPower, price, marginal);
	}
}