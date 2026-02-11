// SPDX-FileCopyrightText: 2021-2026 German Aerospace Center <amiris@dlr.de>
//
// SPDX-License-Identifier: Apache-2.0
package communications.message;

import java.util.Map.Entry;
import java.util.TreeMap;
import agents.policy.PolicyItem.SupportInstrument;
import agents.trader.ClientData;
import de.dlr.gitlab.fame.communication.message.DataItem;
import de.dlr.gitlab.fame.protobuf.Agent.ProtoDataItem;
import de.dlr.gitlab.fame.protobuf.Agent.ProtoDataItem.Builder;
import de.dlr.gitlab.fame.time.TimePeriod;
import de.dlr.gitlab.fame.time.TimeSpan;
import de.dlr.gitlab.fame.time.TimeStamp;

/** Holds the support data needed to calculate the support pay-out by the policy agent after a request by an AggregatorTrader.
 * 
 * @author Johannes Kochems, Christoph Schimeczek */
public class SupportRequestData extends DataItem {
	static final String ERR_TIMESTAMP_LEFTOVER = "Accounting period mismatch; No support was requested for dispatch at time stamp: ";

	/** the technology set */
	public final String setType;
	/** the support instrument for the technology set */
	public final SupportInstrument supportInstrument;
	/** infeed per time stamp */
	public final TreeMap<TimeStamp, Double> infeed;
	/** the installed capacity, relevant in case of capacity-based support */
	public final double installedCapacityInMW;
	/** the accounting period for calculating the support payments */
	public final TimePeriod accountingPeriod;
	/** Id of the client receiving support */
	public final long clientId;

	/** Create new {@link SupportRequestData}
	 * 
	 * @param entry data about the client associated with its UUID
	 * @param accountingPeriod the time period for which the client data apply */
	public SupportRequestData(Entry<Long, ClientData> entry, TimePeriod accountingPeriod) {
		this.clientId = entry.getKey();
		ClientData clientData = entry.getValue();
		this.setType = clientData.getTechnologySet().setType;
		this.supportInstrument = clientData.getTechnologySet().supportInstrument;
		this.accountingPeriod = accountingPeriod;
		this.installedCapacityInMW = clientData.getInstalledCapacity();
		this.infeed = clientData.getDispatch();
	}

	/** Mandatory for deserialisation of {@link DataItem}s
	 * 
	 * @param proto protobuf representation */
	public SupportRequestData(ProtoDataItem proto) {
		this.setType = proto.getStringValues(0);
		this.supportInstrument = SupportInstrument.values()[proto.getIntValues(0)];
		this.installedCapacityInMW = proto.getDoubleValues(0);
		TimeStamp startTime = new TimeStamp(proto.getLongValues(0));
		TimeSpan duration = new TimeSpan(proto.getLongValues(1));
		this.clientId = proto.getLongValues(2);
		this.accountingPeriod = new TimePeriod(startTime, duration);
		this.infeed = new TreeMap<>();
		for (int i = 0; i < proto.getIntValues(1); i++) {
			TimeStamp timeStamp = new TimeStamp(proto.getLongValues(i + 3));
			double value = proto.getDoubleValues(i + 1);
			infeed.put(timeStamp, value);
		}
	}

	@Override
	protected void fillDataFields(Builder builder) {
		builder.addStringValues(setType);
		builder.addIntValues(supportInstrument.ordinal());
		builder.addDoubleValues(installedCapacityInMW);
		builder.addLongValues(accountingPeriod.getStartTime().getStep());
		builder.addLongValues(accountingPeriod.getDuration().getSteps());
		builder.addLongValues(clientId);

		int counter = 0;
		for (Entry<TimeStamp, Double> entry : infeed.entrySet()) {
			if (entry.getKey().isLessThan(accountingPeriod.getStartTime())) {
				throw new RuntimeException(ERR_TIMESTAMP_LEFTOVER + entry.getKey());
			}
			if (entry.getKey().isLessEqualTo(accountingPeriod.getLastTime())) {
				counter++;
				builder.addLongValues(entry.getKey().getStep());
				builder.addDoubleValues(entry.getValue());
			}
		}
		builder.addIntValues(counter);
	}

	@Override
	public String toString() {
		return "(" + setType + " " + supportInstrument + " " + installedCapacityInMW + ")";
	}
}
