// SPDX-FileCopyrightText: 2021-2026 German Aerospace Center <amiris@dlr.de>
//
// SPDX-License-Identifier: Apache-2.0
package communications.message;

import agents.policy.PolicyItem.SupportInstrument;
import agents.policy.SupportPolicy.EnergyCarrier;
import de.dlr.gitlab.fame.communication.message.DataItem;
import de.dlr.gitlab.fame.protobuf.Agent.ProtoDataItem;
import de.dlr.gitlab.fame.protobuf.Agent.ProtoDataItem.Builder;

/** Info needed for registration of a producer for support payments
 * 
 * @author Johannes Kochems, Christoph Schimeczek */
public class TechnologySet extends DataItem {
	/** the set type - or null if no set type is available */
	public final String setType;
	/** the energy carrier */
	public final EnergyCarrier energyCarrier;
	/** the support instrument for the technology set */
	public final SupportInstrument supportInstrument;

	/** Create new {@link TechnologySet}
	 * 
	 * @param technologySetType clients technology set
	 * @param energyCarrier client's type of energy carrier
	 * @param supportInstrument support instrument the client applies for */
	public TechnologySet(String technologySetType, EnergyCarrier energyCarrier, SupportInstrument supportInstrument) {
		this.setType = technologySetType;
		this.energyCarrier = energyCarrier;
		this.supportInstrument = supportInstrument;
	}

	/** Mandatory for deserialisation of {@link DataItem}s
	 * 
	 * @param proto protobuf representation */
	public TechnologySet(ProtoDataItem proto) {
		energyCarrier = EnergyCarrier.values()[proto.getIntValues(0)];
		supportInstrument = getEnumOrNull(SupportInstrument.values(), proto.getIntValues(1));
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
		builder.addIntValues(energyCarrier.ordinal());
		builder.addIntValues(supportInstrument == null ? -1 : supportInstrument.ordinal());
		builder.addStringValues(setType == null ? "" : setType);
	}
}
