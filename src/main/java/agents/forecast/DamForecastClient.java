// SPDX-FileCopyrightText: 2025-2026 German Aerospace Center <amiris@dlr.de>
//
// SPDX-License-Identifier: Apache-2.0
package agents.forecast;

import de.dlr.gitlab.fame.agent.AgentAbility;
import de.dlr.gitlab.fame.communication.Product;

/** An {@link AgentAbility} that allows to ask {@link DamForecastProvider}s for forecasts of the Day-Ahead Market (DAM)
 * 
 * @author Christoph Schimeczek, Felix Nitsch, Johannes Kochems */
public interface DamForecastClient extends AgentAbility {
	@Product
	public static enum Products {
		/** Request for a merit-order forecast */
		MeritOrderForecastRequest,
		/** Request for a price forecast */
		PriceForecastRequest,
	}
}
