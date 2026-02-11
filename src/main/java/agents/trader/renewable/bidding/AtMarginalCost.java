// SPDX-FileCopyrightText: 2023-2026 German Aerospace Center <amiris@dlr.de>
//
// SPDX-License-Identifier: Apache-2.0
package agents.trader.renewable.bidding;

import agents.trader.ClientData;
import de.dlr.gitlab.fame.time.TimeStamp;

/** Calculates bid prices to equal marginal cost
 * 
 * @author Johannes Kochems, Christoph Schimeczek */
public class AtMarginalCost implements BiddingStrategy {

	@Override
	public double calcBiddingPrice(double marginalCostInEURperMWH, TimeStamp _1, ClientData _2) {
		return marginalCostInEURperMWH;
	}
}
