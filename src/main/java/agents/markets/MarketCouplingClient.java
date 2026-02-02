// SPDX-FileCopyrightText: 2026 German Aerospace Center <amiris@dlr.de>
//
// SPDX-License-Identifier: Apache-2.0
package agents.markets;

import de.dlr.gitlab.fame.agent.AgentAbility;
import de.dlr.gitlab.fame.communication.Product;

/** Defines Ability to interact with {@link MarketCoupling}
 * 
 * @author Christoph Schimeczek, Felix Nitsch, Johannes Kochems, Milena Sipovac */
public interface MarketCouplingClient extends AgentAbility {
	@Product
	public static enum Products {
		/** Transmission capacities and bids from local exchange */
		TransmissionAndBids
	}
}