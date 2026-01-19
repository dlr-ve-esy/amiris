// SPDX-FileCopyrightText: 2026 German Aerospace Center <amiris@dlr.de>
//
// SPDX-License-Identifier: Apache-2.0
package agents.loadShifting.strategists;

import java.util.ArrayList;

import com.fasterxml.jackson.annotation.JsonProperty;

/** Results received from external load shifting optimisation model
 *
 * @author Johannes Kochems, Christoph Schimeczek */
public class OptimisationResult {
	private ArrayList<Double> demandAfter;
	private ArrayList<Double> upshift;
	private ArrayList<Double> downshift;
	private double overallVariableCosts;

	public OptimisationResult() {}

	@JsonProperty("demand_after")
	public ArrayList<Double> getDemandAfter() {
		return demandAfter;
	}

	public void setDemandAfter(ArrayList<Double> demandAfter) {
		this.demandAfter = demandAfter;
	}

	public double getUpshift(int period) {
		return upshift.get(period);
	}

	public void setUpshift(ArrayList<Double> upshift) {
		this.upshift = upshift;
	}

	public double getDownshift(int period) {
		return downshift.get(period);
	}

	public void setDownshift(ArrayList<Double> downshift) {
		this.downshift = downshift;
	}

	@JsonProperty("overall_variable_costs")
	public double getOverallVariableCosts() {
		return overallVariableCosts;
	}

	public void setOverallVariableCosts(double variableCosts) {
		this.overallVariableCosts = variableCosts;
	}
}