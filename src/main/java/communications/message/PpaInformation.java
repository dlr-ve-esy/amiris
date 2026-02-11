// SPDX-FileCopyrightText: 2021-2026 German Aerospace Center <amiris@dlr.de>
//
// SPDX-License-Identifier: Apache-2.0
package communications.message;

import de.dlr.gitlab.fame.communication.message.DataItem;
import de.dlr.gitlab.fame.protobuf.Agent.ProtoDataItem;
import de.dlr.gitlab.fame.protobuf.Agent.ProtoDataItem.Builder;
import de.dlr.gitlab.fame.time.TimeStamp;

/** Specifies an arbitrary amount at a specific time
 * 
 * @author Leonard Willeke, Johannes Kochems */
public class PpaInformation extends PointInTime {
	/** The price for which energy is exchanged between the contract parties */
	public final double priceInEURperMWH;
	/** The amount of energy to be exchanged between the contract parties */
	public final double yieldPotentialInMWH;
	/** Marginal costs of the associated renewable plants */
	public final double marginalCostsInEURperMWH;

	/** Creates a new {@link PpaInformation} message
	 * 
	 * @param timeStamp to which the specified amount is associated with
	 * @param price value associated with the given timeStamp
	 * @param yieldPotential energy yield value associated with the given timeStamp
	 * @param marginalCosts marginal costs of the associated renewable plant */
	public PpaInformation(TimeStamp timeStamp, double price, double yieldPotential, double marginalCosts) {
		super(timeStamp);
		this.priceInEURperMWH = price;
		this.yieldPotentialInMWH = yieldPotential;
		this.marginalCostsInEURperMWH = marginalCosts;
	}

	/** Mandatory for deserialisation of {@link DataItem}s
	 * 
	 * @param proto protobuf representation */
	public PpaInformation(ProtoDataItem proto) {
		super(proto);
		this.priceInEURperMWH = proto.getDoubleValues(0);
		this.yieldPotentialInMWH = proto.getDoubleValues(1);
		this.marginalCostsInEURperMWH = proto.getDoubleValues(2);
	}

	@Override
	protected void fillDataFields(Builder builder) {
		super.fillDataFields(builder);
		builder.addDoubleValues(priceInEURperMWH);
		builder.addDoubleValues(yieldPotentialInMWH);
		builder.addDoubleValues(marginalCostsInEURperMWH);
	}
}