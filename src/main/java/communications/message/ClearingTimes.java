// SPDX-FileCopyrightText: 2022-2026 German Aerospace Center <amiris@dlr.de>
//
// SPDX-License-Identifier: Apache-2.0
package communications.message;

import java.util.Arrays;
import java.util.List;
import de.dlr.gitlab.fame.communication.message.DataItem;
import de.dlr.gitlab.fame.protobuf.Agent.ProtoDataItem;
import de.dlr.gitlab.fame.protobuf.Agent.ProtoDataItem.Builder;
import de.dlr.gitlab.fame.time.TimeStamp;

/** Transfers 1..N TimeStamps
 *
 * @author Christoph Schimeczek, A. Achraf El Ghazi */
public class ClearingTimes extends DataItem {
	static final String ERR_NO_TIMES = "ClearingTimes must contain at least one value but is empty!";
	private final TimeStamp[] times;

	/** Constructs a new {@link ClearingTimes}
	 * 
	 * @param times any (positive) amount of {@link TimeStamp}s to be transfered
	 * @throws RuntimeException if no TimeStamp at all is provided */
	public ClearingTimes(TimeStamp... times) {
		if (times.length == 0) {
			throw new RuntimeException(ERR_NO_TIMES);
		}
		this.times = new TimeStamp[times.length];
		for (int i = 0; i < times.length; i++) {
			this.times[i] = times[i];
		}
	}

	@Override
	protected void fillDataFields(Builder builder) {
		builder.addIntValues(times.length);
		for (TimeStamp time : times) {
			builder.addLongValues(time.getStep());
		}
	}

	/** Mandatory for deserialisation of {@link DataItem}s
	 * 
	 * @param proto protobuf representation */
	public ClearingTimes(ProtoDataItem proto) {
		int itemCount = proto.getIntValues(0);
		times = new TimeStamp[itemCount];
		for (int i = 0; i < itemCount; i++) {
			times[i] = new TimeStamp(proto.getLongValues(i));
		}
	}

	/** @return List of TimeStamps */
	public List<TimeStamp> getTimes() {
		return Arrays.asList(times);
	}
}