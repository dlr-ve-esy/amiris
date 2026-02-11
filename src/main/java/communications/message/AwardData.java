// SPDX-FileCopyrightText: 2021-2026 German Aerospace Center <amiris@dlr.de>
//
// SPDX-License-Identifier: Apache-2.0
package communications.message;

import de.dlr.gitlab.fame.communication.message.DataItem;
import de.dlr.gitlab.fame.protobuf.Agent.ProtoDataItem;
import de.dlr.gitlab.fame.protobuf.Agent.ProtoDataItem.Builder;
import de.dlr.gitlab.fame.time.TimeStamp;

/** Award information returned from market clearing */
public class AwardData extends DataItem {
	/** the energy awarded from a demand bid */
	public final double demandEnergyInMWH;
	/** the energy awarded from a supply bid */
	public final double supplyEnergyInMWH;
	/** the power price for the particular delivery time */
	public final double powerPriceInEURperMWH;
	/** the begin of the delivery interval (hour) */
	public final TimeStamp beginOfDeliveryInterval;

	/** Creates new instance
	 * 
	 * @param supplyEnergyInMWH awarded supply energy
	 * @param demandEnergyInMWH awarded demand energy
	 * @param powerPriceInEURperMWH market clearing price
	 * @param timeStamp begin of the associated delivery interval */
	public AwardData(double supplyEnergyInMWH, double demandEnergyInMWH, double powerPriceInEURperMWH,
			TimeStamp timeStamp) {
		this.demandEnergyInMWH = demandEnergyInMWH;
		this.supplyEnergyInMWH = supplyEnergyInMWH;
		this.powerPriceInEURperMWH = powerPriceInEURperMWH;
		this.beginOfDeliveryInterval = timeStamp;
	}

	/** Mandatory for deserialisation of {@link DataItem}s
	 * 
	 * @param proto protobuf representation */
	public AwardData(ProtoDataItem proto) {
		demandEnergyInMWH = proto.getDoubleValues(0);
		supplyEnergyInMWH = proto.getDoubleValues(1);
		powerPriceInEURperMWH = proto.getDoubleValues(2);
		beginOfDeliveryInterval = new TimeStamp(proto.getLongValues(0));
	}

	@Override
	protected void fillDataFields(Builder builder) {
		builder.addDoubleValues(demandEnergyInMWH);
		builder.addDoubleValues(supplyEnergyInMWH);
		builder.addDoubleValues(powerPriceInEURperMWH);
		builder.addLongValues(beginOfDeliveryInterval.getStep());
	}
}