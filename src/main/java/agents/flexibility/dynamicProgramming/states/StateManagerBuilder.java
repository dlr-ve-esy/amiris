// SPDX-FileCopyrightText: 2026 German Aerospace Center <amiris@dlr.de>
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
	public static final Tree parameters = Make.newTree().add(Make.newEnum("Type", Type.class),
			Make.newDouble("PlanningHorizonInHours"), Make.newDouble("EnergyResolutionInMWH"))
			.addAs("WaterValues", WaterValues.parameters)
			.buildTree();

	/** Available {StateManager}s */
	enum Type {
		/** Energy states of a device are represented in one dimension */
		STATE_OF_CHARGE,
		/** Energy states and current shifting time of a device are represented in two dimensions */
		ENERGY_AND_TIME
	}

	public static final String ERR_NOT_IMPLEMENTED = "StateManager is not implemented: ";

	public static StateManager build(GenericDevice device, AssessmentFunction assessment, ParameterData input)
			throws MissingDataException {
		Type type = input.getEnum("Type", Type.class);
		switch (type) {
			case STATE_OF_CHARGE:
				return new EnergyStateManager(device, assessment, input.getDouble("PlanningHorizonInHours"),
						input.getDouble("EnergyResolutionInMWH"), new WaterValues(input.getOptionalGroupList("WaterValues")));
			case ENERGY_AND_TIME:
				return new EnergyAndTimeStateManager(device, assessment, input.getDouble("PlanningHorizonInHours"),
						input.getDouble("EnergyResolutionInMWH"), new WaterValues(input.getOptionalGroupList("WaterValues")));
			default:
				throw new RuntimeException(ERR_NOT_IMPLEMENTED + type);
		}
	}
}
