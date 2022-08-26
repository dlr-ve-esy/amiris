// SPDX-FileCopyrightText: 2022 German Aerospace Center <amiris@dlr.de>
//
// SPDX-License-Identifier: Apache-2.0
package communications.message;

import de.dlr.gitlab.fame.protobuf.Agent.ProtoDataItem;
import de.dlr.gitlab.fame.time.TimeStamp;

/** An {@link AmountAtTime} specialising in fuel cost
 *
 * @author Christoph Schimeczek */
public class FuelCost extends AmountAtTime {
	public FuelCost(TimeStamp timeStamp, double amount) {
		super(timeStamp, amount);
	}

	public FuelCost(ProtoDataItem proto) {
		super(proto);
	}
}