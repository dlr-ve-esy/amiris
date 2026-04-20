// SPDX-FileCopyrightText: 2023-2026 German Aerospace Center <amiris@dlr.de>
//
// SPDX-License-Identifier: Apache-2.0
package agents.forecast;

import de.dlr.gitlab.fame.agent.AgentAbility;
import de.dlr.gitlab.fame.communication.Product;

/** The {@link AgentAbility} to provide forecasts of the Day-Ahead Market (DAM) to {@link DamForecastClient}s */
public interface DamForecastProvider extends AgentAbility {
	@Product
	public static enum Products {
		/** Perfect foresight merit-order forecasts - issue a Request to obtain it */
		MeritOrderForecast,
		/** Price forecast - issue a Request to obtain it */
		PriceForecast,
	}
}
