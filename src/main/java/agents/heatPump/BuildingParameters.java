// SPDX-FileCopyrightText: 2024-2026 German Aerospace Center <amiris@dlr.de>
//
// SPDX-License-Identifier: Apache-2.0
package agents.heatPump;

import de.dlr.gitlab.fame.agent.input.Make;
import de.dlr.gitlab.fame.agent.input.ParameterData;
import de.dlr.gitlab.fame.agent.input.ParameterData.MissingDataException;
import de.dlr.gitlab.fame.agent.input.Tree;

/** Encapsulates building parameters
 * 
 * @author Evelyn Sperber */
public class BuildingParameters {
	/** Input parameters required for constructing {@link BuildingParameters} */
	public static final Tree parameters = Make.newTree().optional().add(Make.newDouble("Ria"), Make.newDouble("Ci"),
			Make.newDouble("Ai"), Make.newDouble("HeatingLimitTemperatureInC"), Make.newDouble("InternalHeatGainsInKW"))
			.buildTree();

	private double Ria;
	private double Ci;
	private double Ai;
	private double heatingLimitTemperatureInC;
	private double internalHeatGainsInKW;

	/** Creates {@link BuildingParameters}
	 * 
	 * @param data input data from config
	 * @throws MissingDataException if any required data is not provided */
	public BuildingParameters(ParameterData data) throws MissingDataException {
		Ria = data.getDouble("Ria");
		Ci = data.getDouble("Ci");
		Ai = data.getDouble("Ai");
		heatingLimitTemperatureInC = data.getDouble("HeatingLimitTemperatureInC");
		internalHeatGainsInKW = data.getDouble("InternalHeatGainsInKW");
	}

	/** @return Ria - the resistance between interior and ambient */
	public double getRia() {
		return Ria;
	}

	/** @return Ci - the thermal capacity of the interior */
	public double getCi() {
		return Ci;
	}

	/** @return Ai - the effective window area for solar gains */
	public double getAi() {
		return Ai;
	}

	/** @return the heating limit temperature */
	public double getHeatingLimitTemperatureInC() {
		return heatingLimitTemperatureInC;
	}

	/** @return the internal heat gains */
	public double getInternalHeatGainsInKW() {
		return internalHeatGainsInKW;
	}
}
