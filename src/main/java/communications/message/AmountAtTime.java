// SPDX-FileCopyrightText: 2021-2026 German Aerospace Center <amiris@dlr.de>
//
// SPDX-License-Identifier: Apache-2.0
package communications.message;

import de.dlr.gitlab.fame.protobuf.Agent.ProtoDataItem;
import de.dlr.gitlab.fame.protobuf.Agent.ProtoDataItem.Builder;
import de.dlr.gitlab.fame.communication.message.DataItem;
import de.dlr.gitlab.fame.time.TimeStamp;

/** Specifies an arbitrary amount at a specific time
 * 
 * @author Christoph Schimeczek, Marc Deissenroth */
public class AmountAtTime extends PointInTime {
	/** The actual amount to be exchanged between the contract parties */
	public final double amount;

	/** Creates a new {@link AmountAtTime} message
	 * 
	 * @param timeStamp to which the specified amount is associated with
	 * @param amount value associated with the given timeStamp */
	public AmountAtTime(TimeStamp timeStamp, double amount) {
		super(timeStamp);
		this.amount = amount;
	}

	/** Mandatory for deserialisation of {@link DataItem}s
	 * 
	 * @param proto protobuf representation */
	public AmountAtTime(ProtoDataItem proto) {
		super(proto);
		this.amount = proto.getDoubleValues(0);
	}

	@Override
	protected void fillDataFields(Builder builder) {
		super.fillDataFields(builder);
		builder.addDoubleValues(amount);
	}
}