// SPDX-FileCopyrightText: 2025-2026 German Aerospace Center <amiris@dlr.de>
//
// SPDX-License-Identifier: Apache-2.0
package agents.flexibility.dynamicProgramming.assessment;

import agents.flexibility.dynamicProgramming.Optimiser.Target;
import agents.forecast.sensitivity.SensitivityForecastProvider.ForecastType;
import communications.portable.Sensitivity.InterpolationType;
import de.dlr.gitlab.fame.data.TimeSeries;

/** Maximise profit of transitions using an electricity price forecast neglecting any price impact of bids
 * 
 * @author Christoph Schimeczek, Felix Nitsch, Johannes Kochems */
public class MaxProfitPriceTaker extends SensitivityBasedAssessment {
	public MaxProfitPriceTaker(TimeSeries variableCostInEURperMWH) {
		super(variableCostInEURperMWH);
	}

	@Override
	public double assessTransition(double externalEnergyDeltaInMWH) {
		double sign = -Math.signum(externalEnergyDeltaInMWH);
		return sign * currentSensitivity.getValue(externalEnergyDeltaInMWH)
				- Math.abs(externalEnergyDeltaInMWH) * currentVariableCostInEURperMWH;
	}

	@Override
	public Target getTargetType() {
		return Target.MAXIMISE;
	}

	@Override
	public ForecastType getSensitivityType() {
		return ForecastType.CostInsensitive;
	}

	@Override
	protected InterpolationType getInterpolationType() {
		return InterpolationType.DIRECT;
	}

	@Override
	public double getSignOfCostValue() {
		return -1.;
	}
}
