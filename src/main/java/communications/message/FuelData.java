// SPDX-FileCopyrightText: 2022 German Aerospace Center <amiris@dlr.de>
//
// SPDX-License-Identifier: Apache-2.0
package communications.message;

import agents.markets.FuelsMarket.FuelType;
import de.dlr.gitlab.fame.protobuf.Agent.ProtoDataItem;
import de.dlr.gitlab.fame.protobuf.Agent.ProtoDataItem.Builder;
import de.dlr.gitlab.fame.communication.message.DataItem;

/** Transmitting Data concerning a {@link FuelType} */
public class FuelData extends DataItem {
	public final FuelType fuelType;

	public FuelData(FuelType fuelType) {
		this.fuelType = fuelType;
	}

	/** Mandatory for deserialisation of {@link DataItem}s
	 * 
	 * @param proto protobuf representation */
	public FuelData(ProtoDataItem proto) {
		int ordinal = proto.getIntValue(0);
		fuelType = FuelType.values()[ordinal];
	}

	@Override
	protected void fillDataFields(Builder builder) {
		builder.addIntValue(fuelType.ordinal());
	}
}