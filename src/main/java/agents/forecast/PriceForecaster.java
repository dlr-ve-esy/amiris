// SPDX-FileCopyrightText: 2025 German Aerospace Center <amiris@dlr.de>
//
// SPDX-License-Identifier: Apache-2.0
package agents.forecast;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import de.dlr.gitlab.fame.agent.input.DataProvider;
import de.dlr.gitlab.fame.agent.input.ParameterData.MissingDataException;

/** Calculates and provides perfect foresight electricity price forecasts;<br>
 * Functionality was moved to {@link MarketForecaster}, thus this class is obsolete and will be removed
 * 
 * @deprecated
 * @author Christoph Schimeczek, Evelyn Sperber, Farzad Sarfarazi, Kristina Nienhaus */
@Deprecated
public class PriceForecaster extends MarketForecaster {
	private static Logger logger = LoggerFactory.getLogger(MeritOrderForecaster.class);

	static final String WARN_DEPRECATED = "PriceForecaster is deprecated and will be removed in a future version. Use MarketForecaster instead.";

	/** Create a {@link PriceForecaster}
	 * 
	 * @param dataProvider provides input from config file
	 * @throws MissingDataException if any required data is not provided */
	public PriceForecaster(DataProvider dataProvider) throws MissingDataException {
		super(dataProvider);
		logger.warn(WARN_DEPRECATED);
	}
}