// SPDX-FileCopyrightText: 2026 German Aerospace Center <amiris@dlr.de>
//
// SPDX-License-Identifier: Apache-2.0
package agents.policy.hydrogen;

import de.dlr.gitlab.fame.agent.AgentAbility;
import de.dlr.gitlab.fame.agent.input.Make;
import de.dlr.gitlab.fame.agent.input.ParameterBuilder;
import de.dlr.gitlab.fame.agent.input.ParameterData;
import de.dlr.gitlab.fame.agent.input.ParameterData.MissingDataException;
import de.dlr.gitlab.fame.communication.Product;

/** A provider of hydrogen-related support policies that can interact with {@link HydrogenSupportClient}s
 * 
 * @author Johannes Kochems, Christoph Schimeczek */
public interface HydrogenSupportProvider extends AgentAbility {
	/** Input parameters related to hydrogen policy sets */
	static ParameterBuilder setParameter = Make.newStringSet("HydrogenPolicySet");

	@Product
	public enum Products {
		/** Info on the support scheme to be applied to a set of clients */
		SupportInfo,
		/** Actual pay-out of the support */
		SupportPayout
	}

	/** Extracts hydrogen policy set from given input
	 * 
	 * @param input to extract the hydrogen policy set from
	 * @return the name of the policy set
	 * @throws MissingDataException if the policy set is not provided */
	public static String readSet(ParameterData input) throws MissingDataException {
		return input.getString("HydrogenPolicySet");
	}
}