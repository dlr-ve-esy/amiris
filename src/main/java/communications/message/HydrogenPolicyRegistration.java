// SPDX-FileCopyrightText: 2026 German Aerospace Center <amiris@dlr.de>
//
// SPDX-License-Identifier: Apache-2.0
package communications.message;

import agents.policy.hydrogen.HydrogenSupportClient;
import agents.policy.hydrogen.HydrogenSupportProvider;
import agents.policy.hydrogen.PolicyItem.SupportInstrument;
import de.dlr.gitlab.fame.communication.message.DataItem;
import de.dlr.gitlab.fame.protobuf.Agent.ProtoDataItem;
import de.dlr.gitlab.fame.protobuf.Agent.ProtoDataItem.Builder;

/** Registration message from a {@link HydrogenSupportClient} to a {@link HydrogenSupportProvider}
 * 
 * @author Johannes Kochems, Christoph Schimeczek */
public class HydrogenPolicyRegistration extends DataItem {
	/** the set type - or null if no set type is available */
	public final String setType;
	/** the support instrument for the technology set */
	public final SupportInstrument supportInstrument;

	/** Create new {@link TechnologySet}
	 * 
	 * @param technologySetType clients technology set
	 * @param supportInstrument support instrument the client applies for */
	public HydrogenPolicyRegistration(String technologySetType, SupportInstrument supportInstrument) {
		this.setType = technologySetType;
		this.supportInstrument = supportInstrument;
	}

	/** Mandatory for deserialisation of {@link DataItem}s
	 * 
	 * @param proto protobuf representation */
	public HydrogenPolicyRegistration(ProtoDataItem proto) {
		supportInstrument = getEnumOrNull(SupportInstrument.values(), proto.getIntValues(0));
		setType = getStringOrNull(proto.getStringValues(0));
	}

	/** @return if given index >= 0: item with corresponding index in given array of choices, null otherwise */
	private <T extends Enum<T>> T getEnumOrNull(T[] choices, int index) {
		if (index >= 0) {
			return choices[index];
		} else {
			return null;
		}
	}

	/** @return given string if not empty, else null */
	private String getStringOrNull(String string) {
		return string == "" ? null : string;
	}

	@Override
	protected void fillDataFields(Builder builder) {
		builder.addIntValues(supportInstrument == null ? -1 : supportInstrument.ordinal());
		builder.addStringValues(setType == null ? "" : setType);
	}
}
