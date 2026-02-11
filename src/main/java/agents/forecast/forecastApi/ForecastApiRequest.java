// SPDX-FileCopyrightText: 2025-2026 German Aerospace Center <amiris@dlr.de>
//
// SPDX-License-Identifier: Apache-2.0
package agents.forecast.forecastApi;

import java.util.Map;
import util.JSONable;

/** Encapsulates a forecast request to amiris-priceforecast with the main objective to handle time series predictions
 * 
 * @author Felix Nitsch */
public class ForecastApiRequest implements JSONable {
	private long forecastStartTime;
	private int forecastWindow;
	private Map<Long, Double> pastTargets;
	private Map<Long, Double> residualLoad;

	/** Constructs ForecastApiRequest given a forecastStartTime, forecastWindow, and a pastTargets
	 * 
	 * @param forecastStartTime start time of forecast
	 * @param forecastWindow window of forecast
	 * @param pastTargets realized electricity prices with time steps
	 * @param residualLoad residual load with time steps */
	public ForecastApiRequest(long forecastStartTime, int forecastWindow, Map<Long, Double> pastTargets,
			Map<Long, Double> residualLoad) {
		this.forecastStartTime = forecastStartTime;
		this.forecastWindow = forecastWindow;
		this.pastTargets = pastTargets;
		this.residualLoad = residualLoad;
	}

	/** @return the forecastStartTime */
	public long getForecastStartTime() {
		return forecastStartTime;
	}

	/** @return the forecastWindow */
	public int getForecastWindow() {
		return forecastWindow;
	}

	/** @return the pastTargets */
	public Map<Long, Double> getPastTargets() {
		return pastTargets;
	}

	/** @return the residualLoad */
	public Map<Long, Double> getResidualLoad() {
		return residualLoad;
	}
}
