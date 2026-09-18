// SPDX-FileCopyrightText: 2026 German Aerospace Center <amiris@dlr.de>
//
// SPDX-License-Identifier: Apache-2.0
package communications.message;

import de.dlr.gitlab.fame.communication.message.DataItem;
import de.dlr.gitlab.fame.protobuf.AgentMessage.MessageContent.ProtoDataItem;
import de.dlr.gitlab.fame.protobuf.AgentMessage.MessageContent.ProtoDataItem.Builder;
import de.dlr.gitlab.fame.time.TimeStamp;

/** Contains market and support payout information
 * 
 * @author Johannes Kochems */
public class ContractualPayout extends PointInTime {
	/** Total revenues paid out */
	public final double totalRevenuesInEUR;
	/** Support paid out */
	public final double supportInEUR;

	/** Creates a new {@link ContractualPayout} message
	 * 
	 * @param timeStamp contractual payout is valid at
	 * @param totalRevenues total revenues to be paid out
	 * @param support to be paid ou */
	public ContractualPayout(TimeStamp timeStamp, double totalRevenues, double support) {
		super(timeStamp);
		totalRevenuesInEUR = totalRevenues;
		supportInEUR = support;
	}

	/** Mandatory for deserialisation of {@link DataItem}s
	 * 
	 * @param proto protobuf representation */
	public ContractualPayout(ProtoDataItem proto) {
		super(proto);
		totalRevenuesInEUR = proto.getDoubleValues(0);
		supportInEUR = proto.getDoubleValues(1);
	}

	@Override
	protected void fillDataFields(Builder builder) {
		super.fillDataFields(builder);
		builder.addDoubleValues(totalRevenuesInEUR);
		builder.addDoubleValues(supportInEUR);
	}
}
