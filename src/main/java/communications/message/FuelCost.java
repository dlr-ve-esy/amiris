// SPDX-FileCopyrightText: 2021-2026 German Aerospace Center <amiris@dlr.de>
//
// SPDX-License-Identifier: Apache-2.0
package communications.message;

import de.dlr.gitlab.fame.communication.message.DataItem;
import de.dlr.gitlab.fame.protobuf.Agent.ProtoDataItem;
import de.dlr.gitlab.fame.time.TimeStamp;

/** An {@link AmountAtTime} specialising in specific fuel cost
 *
 * @author Christoph Schimeczek */
public class FuelCost extends AmountAtTime {
	/** Creates new instance
	 * 
	 * @param timeStamp at which the price is valid
	 * @param fuelPriceInEURperThermalMWH fuel price at the given time in EUR per thermal MWH */
	public FuelCost(TimeStamp timeStamp, double fuelPriceInEURperThermalMWH) {
		super(timeStamp, fuelPriceInEURperThermalMWH);
	}

	/** Mandatory for deserialisation of {@link DataItem}s
	 * 
	 * @param proto protobuf representation */
	public FuelCost(ProtoDataItem proto) {
		super(proto);
	}
}