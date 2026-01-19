// SPDX-FileCopyrightText: 2026 German Aerospace Center <amiris@dlr.de>
//
// SPDX-License-Identifier: Apache-2.0
package agents.forecast.sensitivity;

import agents.markets.DayAheadMarket;
import de.dlr.gitlab.fame.agent.AgentAbility;
import de.dlr.gitlab.fame.communication.Product;

/** A client to {@link SensitivityForecaster}
 * 
 * @author Johannes Kochems, Christoph Schimeczek */
public interface SensitivityForecastClient extends AgentAbility {
	/** Products of {@link SensitivityForecastClient}s */
	@Product
	enum Products {
		/** A registration message specifying the type of forecast needed and the client's installed power */
		ForecastRegistration,
		/** A report on the client's awarded energy at the local {@link DayAheadMarket} at a previous time */
		NetAward,
		/** A request for a sensitivity forecast, specifying a time for which the forecast is required */
		SensitivityRequest,
	}
}
