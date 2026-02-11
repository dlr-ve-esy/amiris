// SPDX-FileCopyrightText: 2025-2026 German Aerospace Center <amiris@dlr.de>
//
// SPDX-License-Identifier: Apache-2.0
package agents.policy.hydrogen;

import java.util.ArrayList;
import java.util.List;
import agents.policy.hydrogen.PolicyItem.SupportInstrument;
import communications.message.AmountAtTime;
import communications.message.HydrogenPolicyRegistration;
import communications.portable.HydrogenSupportData;
import de.dlr.gitlab.fame.agent.AgentAbility;
import de.dlr.gitlab.fame.agent.input.Make;
import de.dlr.gitlab.fame.agent.input.ParameterData;
import de.dlr.gitlab.fame.agent.input.ParameterData.MissingDataException;
import de.dlr.gitlab.fame.agent.input.Tree;
import de.dlr.gitlab.fame.communication.CommUtils;
import de.dlr.gitlab.fame.communication.Contract;
import de.dlr.gitlab.fame.communication.Product;
import de.dlr.gitlab.fame.communication.message.Message;
import de.dlr.gitlab.fame.service.output.Output;

/** A client of a {@link HydrogenSupportProvider} that it can interact with to request support for hydrogen production
 * 
 * @author Johannes Kochems, Christoph Schimeczek */
public interface HydrogenSupportClient extends AgentAbility {
	/** Input parameters related to requesting hydrogen support */
	static final Tree parameters = Make.newTree().optional()
			.add(Make.newEnum("SupportInstrument", SupportInstrument.class),
					HydrogenSupportProvider.setParameter)
			.buildTree();

	/** Products related to requesting hydrogen-related support */
	@Product
	public enum Products {
		/** Request for support information for contracted technology set(s) */
		SupportInfoRequest,
		/** Request to obtain support payments for contracted technology set(s) */
		SupportPayoutRequest
	}

	/** Available output columns */
	@Output
	public static enum Outputs {
		/** Received support for hydrogen in EUR */
		ReceivedHydrogenSupportInEUR
	}

	/** Extracts data about the parameterised hydrogen policy from given input
	 * 
	 * @param input group related to the hydrogen policy, can be null if no such group
	 * @return registration message to a {@link HydrogenSupportProvider} regarding the policy, or null if input is null
	 * @throws MissingDataException in case mandatory parameters are missing */
	public static HydrogenPolicyRegistration getRegistration(ParameterData input) throws MissingDataException {
		if (input != null) {
			return new HydrogenPolicyRegistration(HydrogenSupportProvider.readSet(input),
					input.getEnum("SupportInstrument", SupportInstrument.class));
		}
		return null;
	}

	/** Standard action to send a registration message to a {@link HydrogenSupportProvider}
	 * 
	 * @param __ not used
	 * @param contracts one Contract to the connected {@link HydrogenSupportProvider} */
	public default void registerSupport(ArrayList<Message> __, List<Contract> contracts) {
		HydrogenPolicyRegistration registrationData = getRegistrationData();
		if (registrationData != null) {
			Contract contract = CommUtils.getExactlyOneEntry(contracts);
			fulfilNext(contract, registrationData);
		}
	}

	/** Returns a {@link HydrogenPolicyRegistration} message if a hydrogen policy is parameterised, null otherwise
	 * 
	 * @return the appropriate {@link HydrogenPolicyRegistration} or null if no hydrogen support is parameterised */
	public abstract HydrogenPolicyRegistration getRegistrationData();

	/** Standard action to read a SupportInfo message from a {@link HydrogenSupportProvider}
	 * 
	 * @param messages a single message about the effective support from a single {@link HydrogenSupportProvider}
	 * @param __ not used */
	public default void digestSupportInfo(ArrayList<Message> messages, List<Contract> __) {
		Message message = CommUtils.getExactlyOneEntry(messages);
		HydrogenSupportData hydrogenSupportData = message.getFirstPortableItemOfType(HydrogenSupportData.class);
		saveSupportData(hydrogenSupportData);
	}

	/** Passes the {@link HydrogenSupportData} extracted from a message to save it in the client
	 * 
	 * @param hydrogenSupportData to be stored in the client */
	public abstract void saveSupportData(HydrogenSupportData hydrogenSupportData);

	/** Standard action to read a support payment message from a connected {@link HydrogenSupportProvider} and store amount of paid
	 * support to column
	 * 
	 * @param messages a single message about the actual support payment from a single {@link HydrogenSupportProvider}
	 * @param __ not used */
	public default void digestSupportPayout(ArrayList<Message> messages, List<Contract> __) {
		AmountAtTime support = CommUtils.getExactlyOneEntry(messages).getDataItemOfType(AmountAtTime.class);
		store(Outputs.ReceivedHydrogenSupportInEUR, support.amount);
	}
}