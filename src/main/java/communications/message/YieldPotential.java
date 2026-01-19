// SPDX-FileCopyrightText: 2026 German Aerospace Center <amiris@dlr.de>
//
// SPDX-License-Identifier: Apache-2.0
package communications.message;

import agents.policy.SupportPolicy.EnergyCarrier;
import de.dlr.gitlab.fame.communication.message.DataItem;
import de.dlr.gitlab.fame.protobuf.Agent.ProtoDataItem;
import de.dlr.gitlab.fame.protobuf.Agent.ProtoDataItem.Builder;
import de.dlr.gitlab.fame.time.TimeStamp;

/** Yield potential data associated with an energy carrier
 * 
 * @author Johannes Kochems */
public class YieldPotential extends AmountAtTime {
	/** the energy carrier */
	public final EnergyCarrier energyCarrier;

	/** Create new {@link YieldPotential}
	 * 
	 * @param timeStamp at which the electricity could be produced
	 * @param amount of electricity that could be produced in MWh
	 * @param energyCarrier used for the electricity production */
	public YieldPotential(TimeStamp timeStamp, double amount, EnergyCarrier energyCarrier) {
		super(timeStamp, amount);
		this.energyCarrier = energyCarrier;
	}

	/** Mandatory for deserialisation of {@link DataItem}s
	 * 
	 * @param proto protobuf representation */
	public YieldPotential(ProtoDataItem proto) {
		super(proto);
		this.energyCarrier = EnergyCarrier.values()[proto.getIntValues(0)];
	}

	@Override
	protected void fillDataFields(Builder builder) {
		super.fillDataFields(builder);
		builder.addIntValues(energyCarrier.ordinal());
	}
}
