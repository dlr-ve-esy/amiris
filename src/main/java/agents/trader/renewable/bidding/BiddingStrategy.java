// SPDX-FileCopyrightText: 2023-2026 German Aerospace Center <amiris@dlr.de>
//
// SPDX-License-Identifier: Apache-2.0
package agents.trader.renewable.bidding;

import agents.trader.ClientData;
import de.dlr.gitlab.fame.time.TimeStamp;

/** Can calculate a bidding prices for marketing of renewable energy
 * 
 * @author Christoph Schimeczek */
public interface BiddingStrategy {

	/** @param marginalCostInEURperMWH cost for the provision of one more MWh of electricity by the associated client
	 * @param time at which to produce and deliver the energy
	 * @param clientData specifications of the client that offers to produce electricity
	 * @return bidding price calculated based on the specified marginal cost, {@link ClientData} at a given time */
	double calcBiddingPrice(double marginalCostInEURperMWH, TimeStamp time, ClientData clientData);
}
