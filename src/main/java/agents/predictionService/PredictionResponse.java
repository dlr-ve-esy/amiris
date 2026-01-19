// SPDX-FileCopyrightText: 2026 German Aerospace Center <amiris@dlr.de>
//
// SPDX-License-Identifier: Apache-2.0
package agents.predictionService;

/** Encapsulates a prediction response of a general prediction model. This class is designed primarily for time series prediction,
 * where each variable value is associated with a time reference (or an ordered index). However, it can also handle the case where
 * the time reference is missing as special case.
 * 
 * @author A. Achraf El Ghazi, Felix Nitsch */
public class PredictionResponse {
	private double netLoadPrediction;

	/** @return net load prediction */
	public double getNetLoadPrediction() {
		return netLoadPrediction;
	}

	/** @param netLoadPrediction net load prediction to set */
	public void setNetLoadPrediction(double netLoadPrediction) {
		this.netLoadPrediction = netLoadPrediction;
	}
}
