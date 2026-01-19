// SPDX-FileCopyrightText: 2026 German Aerospace Center <amiris@dlr.de>
//
// SPDX-License-Identifier: Apache-2.0
package communications.message;

import de.dlr.gitlab.fame.communication.message.DataItem;
import de.dlr.gitlab.fame.protobuf.Agent.ProtoDataItem;
import de.dlr.gitlab.fame.protobuf.Agent.ProtoDataItem.Builder;
import de.dlr.gitlab.fame.time.TimeStamp;

/** Offer or request for an amount of fuel of a certain type at a given time
 * 
 * @author Christoph Schimeczek, Johannes Kochems */
public class FuelBid extends AmountAtTime {
	/** Type of fuel bid */
	public enum BidType {
		/** Offering fuel for sale */
		Supply,
		/** Requesting fuel to buy */
		Demand
	}

	/** whether fuel is to be offered or purchased */
	public final BidType bidType;
	/** the type of fuel this bid is associated with */
	public final String fuelType;

	/** Constructs a new {@link FuelBid}
	 * 
	 * @param timeStamp for which this fuel bid is valid at
	 * @param amount of fuel to be sold or purchased in thermal MWH
	 * @param bidType whether fuel is to be offered or purchased
	 * @param fuelType type of fuel this bid is associated with */
	public FuelBid(TimeStamp timeStamp, double amount, BidType bidType, String fuelType) {
		super(timeStamp, amount);
		if (validAt == null) {
			throw new RuntimeException("Null is not allowed!");
		}
		this.bidType = bidType;
		this.fuelType = fuelType;
	}

	/** Mandatory for deserialisation of {@link DataItem}s
	 * 
	 * @param proto protobuf representation */
	public FuelBid(ProtoDataItem proto) {
		super(proto);
		bidType = BidType.values()[proto.getIntValues(0)];
		fuelType = proto.getStringValues(0);
	}

	@Override
	protected void fillDataFields(Builder builder) {
		super.fillDataFields(builder);
		builder.addIntValues(bidType.ordinal());
		builder.addStringValues(fuelType);
	}
}
