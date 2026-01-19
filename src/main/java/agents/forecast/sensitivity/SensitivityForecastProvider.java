// SPDX-FileCopyrightText: 2026 German Aerospace Center <amiris@dlr.de>
//
// SPDX-License-Identifier: Apache-2.0
package agents.forecast.sensitivity;

import de.dlr.gitlab.fame.agent.AgentAbility;
import de.dlr.gitlab.fame.communication.Product;

/** A provider of market clearing sensitivity forecasts */
public interface SensitivityForecastProvider extends AgentAbility {
	/** Products of a {@link SensitivityForecastProvider} */
	@Product
	enum Products {
		/** A forecast of a market clearing sensitivity; type of sensitivity depends on the registered needs of the client */
		SensitivityForecast
	}

	/** Available types of market clearing sensitivity forecasts */
	enum ForecastType {
		/** Monetary value of charging / discharging based on the market clearing price - ignoring any changes to the price that a
		 * modified market clearing result would cause */
		CostInsensitive,
		/** Monetary value of charging / discharging based on the market clearing price - considering changes to the price caused by
		 * additional demand or supply */
		CostSensitivity,
	}
}
