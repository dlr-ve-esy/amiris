// SPDX-FileCopyrightText: 2026 German Aerospace Center <amiris@dlr.de>
//
// SPDX-License-Identifier: Apache-2.0
package agents.flexibility.dynamicProgramming.assessment;

import agents.flexibility.GenericDevice;
import agents.flexibility.dynamicProgramming.Optimiser.Target;
import agents.forecast.sensitivity.SensitivityForecastProvider.ForecastType;
import communications.portable.Sensitivity.InterpolationType;

/** Maximise profits of transitions using a merit order forecast and estimating the impact of transitions on profits caused by own
 * dispatch and dispatch of competitors.
 * 
 * @author Christoph Schimeczek */
public class MaxProfit extends SensitivityBasedAssessment {
	public MaxProfit(GenericDevice device) {
		super(device);
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
}
