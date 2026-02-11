// SPDX-FileCopyrightText: 2021-2026 German Aerospace Center <amiris@dlr.de>
//
// SPDX-License-Identifier: Apache-2.0
package communications.message;

import de.dlr.gitlab.fame.communication.message.DataItem;
import de.dlr.gitlab.fame.protobuf.Agent.ProtoDataItem;
import de.dlr.gitlab.fame.protobuf.Agent.ProtoDataItem.Builder;

/** Transmitting Data concerning a fuel type */
public class FuelData extends DataItem {
	/** The type of fuel in question */
	public final String fuelType;

	/** Creates a new {@link FuelData} instance
	 * 
	 * @param fuelType the type of fuel in question */
	public FuelData(String fuelType) {
		this.fuelType = fuelType;
	}

	/** Mandatory for deserialisation of {@link DataItem}s
	 * 
	 * @param proto protobuf representation */
	public FuelData(ProtoDataItem proto) {
		fuelType = proto.getStringValues(0);
	}

	@Override
	protected void fillDataFields(Builder builder) {
		builder.addStringValues(fuelType);
	}
}