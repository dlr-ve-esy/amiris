// SPDX-FileCopyrightText: 2026 German Aerospace Center <amiris@dlr.de>
//
// SPDX-License-Identifier: Apache-2.0
package communications.message;

import de.dlr.gitlab.fame.communication.message.DataItem;
import de.dlr.gitlab.fame.protobuf.Agent.ProtoDataItem;
import de.dlr.gitlab.fame.time.TimeStamp;

/** An {@link AmountAtTime} specialising on co2 cost
 *
 * @author Christoph Schimeczek */
public class Co2Cost extends AmountAtTime {
	/** Creates new {@link Co2Cost} instance
	 * 
	 * @param timeStamp at which the costs apply
	 * @param co2PriceInEURperT specific CO2 price in EUR per ton of CO2 emissions */
	public Co2Cost(TimeStamp timeStamp, double co2PriceInEURperT) {
		super(timeStamp, co2PriceInEURperT);
	}

	/** Mandatory for deserialisation of {@link DataItem}s
	 * 
	 * @param proto protobuf representation */
	public Co2Cost(ProtoDataItem proto) {
		super(proto);
	}
}