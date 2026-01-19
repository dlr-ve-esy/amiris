// SPDX-FileCopyrightText: 2026 German Aerospace Center <amiris@dlr.de>
//
// SPDX-License-Identifier: Apache-2.0
package agents.markets.meritOrder;

import agents.markets.DayAheadMarket;

/** Defines both minimal and maximal allowed bidding prices at the {@link DayAheadMarket} */
public class Constants {
	/** Minimum allowed bidding price at {@link DayAheadMarket}s */
	public static final double MINIMAL_PRICE_IN_EUR_PER_MWH = -500;
	/** Maximum allowed bidding price at {@link DayAheadMarket}s */
	public static final double SCARCITY_PRICE_IN_EUR_PER_MWH = 4000;
}