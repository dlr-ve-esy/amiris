// SPDX-FileCopyrightText: 2025-2026 German Aerospace Center <amiris@dlr.de>
//
// SPDX-License-Identifier: Apache-2.0
package agents.flexibility.dynamicProgramming.assessment;

import agents.flexibility.dynamicProgramming.Optimiser.Target;
import agents.forecast.sensitivity.SensitivityForecastProvider.ForecastType;
import communications.portable.Sensitivity.InterpolationType;
import de.dlr.gitlab.fame.data.TimeSeries;

/** Maximise profits of transitions using a merit order forecast and estimating the impact of transitions on profits caused by own
 * dispatch and dispatch of competitors.
 * 
 * @author Christoph Schimeczek */
public class MaxProfit extends SensitivityBasedAssessment {
	public MaxProfit(TimeSeries variableCostInEURperMWH) {
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
		return ForecastType.CostSensitivity;
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
