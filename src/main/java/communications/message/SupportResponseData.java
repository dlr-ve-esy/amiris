// SPDX-FileCopyrightText: 2021-2026 German Aerospace Center <amiris@dlr.de>
//
// SPDX-License-Identifier: Apache-2.0
package communications.message;

import de.dlr.gitlab.fame.communication.message.DataItem;
import de.dlr.gitlab.fame.protobuf.Agent.ProtoDataItem;
import de.dlr.gitlab.fame.protobuf.Agent.ProtoDataItem.Builder;
import de.dlr.gitlab.fame.time.TimePeriod;
import de.dlr.gitlab.fame.time.TimeSpan;
import de.dlr.gitlab.fame.time.TimeStamp;

/** Holds the response of the support policy agent to a SupportRequestData item, i.e. the actual support payed out
 * 
 * @author Johannes Kochems, Christoph Schimeczek */
public class SupportResponseData extends DataItem {
	/** the technology set */
	public final String setType;
	/** the accounting period for calculating the support payments */
	public final TimePeriod accountingPeriod;
	/** the support payed out */
	public final double payment;
	/** the market premium (if applicable, i.e. a MPVAR, MPFIX or CFD scheme) */
	public final double marketPremium;
	/** Id of the client receiving support */
	public final long clientId;

	/** Create new {@link SupportRequestData}
	 * 
	 * @param supportRequestData original support request
	 * @param payment to the client
	 * @param marketPremium applicable for the client */
	public SupportResponseData(SupportRequestData supportRequestData, double payment, double marketPremium) {
		this.setType = supportRequestData.setType;
		this.accountingPeriod = supportRequestData.accountingPeriod;
		this.clientId = supportRequestData.clientId;
		this.payment = payment;
		this.marketPremium = marketPremium;
	}

	/** Mandatory for deserialisation of {@link DataItem}s
	 * 
	 * @param proto protobuf representation */
	public SupportResponseData(ProtoDataItem proto) {
		this.setType = proto.getStringValues(0);
		TimeStamp startTime = new TimeStamp(proto.getLongValues(0));
		TimeSpan duration = new TimeSpan(proto.getLongValues(1));
		this.clientId = proto.getLongValues(2);
		this.accountingPeriod = new TimePeriod(startTime, duration);
		this.payment = proto.getDoubleValues(0);
		this.marketPremium = proto.getDoubleValues(1);
	}

	@Override
	protected void fillDataFields(Builder builder) {
		builder.addStringValues(setType);
		builder.addLongValues(accountingPeriod.getStartTime().getStep());
		builder.addLongValues(accountingPeriod.getDuration().getSteps());
		builder.addLongValues(clientId);
		builder.addDoubleValues(payment);
		builder.addDoubleValues(marketPremium);
	}
}
