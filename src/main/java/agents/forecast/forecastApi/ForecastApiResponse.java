// SPDX-FileCopyrightText: 2026 German Aerospace Center <amiris@dlr.de>
//
// SPDX-License-Identifier: Apache-2.0
package agents.forecast.forecastApi;

import java.util.List;
import java.util.TreeMap;

/** Encapsulates a forecast response from amiris-priceforecast with the main objective to handle time series predictions
 * 
 * @author Felix Nitsch */
public class ForecastApiResponse {
	private List<TreeMap<Long, Double>> forecastMeans;
	private List<TreeMap<Long, Double>> forecastVariances;

	/** @return multiple samples (individual forecasts) for the target mean value time series */
	public List<TreeMap<Long, Double>> getForecastMeans() {
		return forecastMeans;
	}

	/** @param forecastMeans the forecastTargetSeries to set */
	public void setForecastMeans(List<TreeMap<Long, Double>> forecastMeans) {
		this.forecastMeans = forecastMeans;
	}

	/** @return multiple samples (individual forecasts) for the target variance time series */
	public List<TreeMap<Long, Double>> getForecastVariances() {
		return forecastVariances;
	}

	/** @param forecastVariances the forecastVariances to set */
	public void setForecastVariances(List<TreeMap<Long, Double>> forecastVariances) {
		this.forecastVariances = forecastVariances;
	}
}
