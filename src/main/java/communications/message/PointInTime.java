package communications.message;

import de.dlr.gitlab.fame.protobuf.Agent.ProtoDataItem;
import de.dlr.gitlab.fame.protobuf.Agent.ProtoDataItem.Builder;
import de.dlr.gitlab.fame.communication.message.DataItem;
import de.dlr.gitlab.fame.time.TimeStamp;

/** Transfers but a single TimeStamp
 *
 * @author Christoph Schimeczek */
public class PointInTime extends DataItem {
	public final TimeStamp timeStamp;

	public PointInTime(TimeStamp timeStamp) {
		this.timeStamp = timeStamp;
	}

	@Override
	protected void fillDataFields(Builder builder) {
		builder.addLongValue(timeStamp.getStep());
	}

	/** Mandatory for deserialisation of {@link DataItem}s
	 * 
	 * @param proto protobuf representation */
	public PointInTime(ProtoDataItem proto) {
		this.timeStamp = new TimeStamp(proto.getLongValue(0));
	}
}