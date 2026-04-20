// SPDX-FileCopyrightText: 2025-2026 German Aerospace Center <amiris@dlr.de>
//
// SPDX-License-Identifier: Apache-2.0
package agents.flexibility.dynamicProgramming.states;

import agents.flexibility.GenericDevice;
import agents.flexibility.dynamicProgramming.assessment.AssessmentFunction;
import de.dlr.gitlab.fame.agent.input.Make;
import de.dlr.gitlab.fame.agent.input.ParameterData;
import de.dlr.gitlab.fame.agent.input.ParameterData.MissingDataException;
import de.dlr.gitlab.fame.agent.input.Tree;

/** Builds {@link StateManager} from provided input parameters
 * 
 * @author Christoph Schimeczek, Felix Nitsch, Johannes Kochems */
public class StateManagerBuilder {
	static final String PARAM_TYPE = "Type";
	static final String PARAM_HORIZON = "PlanningHorizonInHours";
	static final String PARAM_RESOLUTION = "EnergyResolutionInMWH";

	static final String GROUP_WATER_VALUES = "WaterValues";

	/** Available {StateManager}s */
	enum Type {
		/** Energy states of a device are represented in one dimension */
		STATE_OF_CHARGE,
		/** Energy states and current shifting time of a device are represented in two dimensions */
		ENERGY_AND_TIME
	}

	public static final Tree parameters = Make.newTree().add(Make.newEnum(PARAM_TYPE, Type.class),
			Make.newDouble(PARAM_HORIZON), Make.newDouble(PARAM_RESOLUTION))
			.addAs(GROUP_WATER_VALUES, WaterValues.parameters)
			.buildTree();

	public static final String ERR_NOT_IMPLEMENTED = "StateManager is not implemented: ";

	public static StateManager build(GenericDevice device, AssessmentFunction assessment, ParameterData input)
			throws MissingDataException {
		Type type = input.getEnum(PARAM_TYPE, Type.class);
		double planningHorizon = input.getDouble(PARAM_HORIZON);
		double energyResolution = input.getDouble(PARAM_RESOLUTION);
		WaterValues waterValues = new WaterValues(input.getOptionalGroupList(GROUP_WATER_VALUES));
		switch (type) {
			case STATE_OF_CHARGE:
				return new EnergyStateManager(device, assessment, planningHorizon, energyResolution, waterValues);
			case ENERGY_AND_TIME:
				return new EnergyAndTimeStateManager(device, assessment, planningHorizon, energyResolution, waterValues);
			default:
				throw new RuntimeException(ERR_NOT_IMPLEMENTED + type);
		}
	}
}
