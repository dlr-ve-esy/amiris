// SPDX-FileCopyrightText: 2025-2026 German Aerospace Center <amiris@dlr.de>
//
// SPDX-License-Identifier: Apache-2.0
package agents.forecast.sensitivity;

import agents.forecast.sensitivity.SensitivityForecastProvider.ForecastType;
import agents.markets.meritOrder.MarketClearingResult;

/** Can assess a market clearing result and return its sensitivity to changes in demand or supply
 * 
 * @author Christoph Schimeczek, Johannes Kochems */
public interface MarketClearingAssessment {
	static final String ERR_NOT_IMPLEMENTED = "No MarketClearingAssessment implemented for forecast type: ";

	/** Assesses given supply and demand books for their sensitivity; results are stored internally.
	 * 
	 * @param clearingResult aggregated curves and market clearing result to assess */
	void assess(MarketClearingResult clearingResult);

	/** Returns power steps for additional demand, between which the sensitivity value changes linearly
	 * 
	 * @return demand power steps in between which the value of demand changes linearly */
	double[] getDemandSensitivityPowers();

	/** Returns values of the sensitivity valid at the additional demand power entry with corresponding index
	 * 
	 * @return values of demand change corresponding to the power step with the same index */
	double[] getDemandSensitivityValues();

	/** Returns power steps for additional supply, between which the sensitivity value changes linearly
	 * 
	 * @return supply power steps in between which the value of demand changes linearly */
	double[] getSupplySensitivityPowers();

	/** Returns values of the sensitivity valid at the additional supply power entry with corresponding index
	 * 
	 * @return values of supply change corresponding to the power step with the same index */
	double[] getSupplySensitivityValues();

	/** Returns the {@link MarketClearingAssessment} suited to provide the requested type of sensitivity forecast
	 * 
	 * @param type of forecast tied to a type of sensitivity
	 * @return a new {@link MarketClearingAssessment} */
	static MarketClearingAssessment build(ForecastType type) {
		switch (type) {
			case CostSensitivity:
				return new CostSensitive();
			case CostInsensitive:
				return new CostInsensitive();
			default:
				throw new RuntimeException(ERR_NOT_IMPLEMENTED + type);
		}
	}
}
