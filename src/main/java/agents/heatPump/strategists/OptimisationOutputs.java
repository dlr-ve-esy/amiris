// SPDX-FileCopyrightText: 2026 German Aerospace Center <amiris@dlr.de>
//
// SPDX-License-Identifier: Apache-2.0
package agents.heatPump.strategists;

import java.util.ArrayList;

/** Comprises output data from the external model, which is called by the heat pump strategist type {@link StrategistExternal}, to
 * be used back in AMIRIS.
 * 
 * @author Evelyn Sperber, Christoph Schimeczek */
public class OptimisationOutputs {
	private ArrayList<Double> electricity_demand;

	/** @return aggregated electricity demand from grid of heat pumps */
	public ArrayList<Double> getElectricity_demand() {
		return electricity_demand;
	}

	/** Sets aggregated electricity demand from grid of heat pumps
	 * 
	 * @param electricity_demand to be set */
	public void setElectricity_demand(ArrayList<Double> electricity_demand) {
		this.electricity_demand = electricity_demand;
	}
}
