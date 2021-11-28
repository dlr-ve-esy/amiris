package communications.message;

import de.dlr.gitlab.fame.protobuf.Agent.ProtoDataItem;
import de.dlr.gitlab.fame.protobuf.Agent.ProtoDataItem.Builder;
import de.dlr.gitlab.fame.communication.message.DataItem;
import de.dlr.gitlab.fame.time.TimeStamp;

/** Specifies an arbitrary amount at a specific time
 * 
 * @author Christoph Schimeczek, Marc Deissenroth */
public class AmountAtTime extends DataItem {
	/** The time the DataItem is valid at */
	public final TimeStamp validAt;
	/** The actual amount to be exchanged between the contract parties */
	public final double amount;

	/** Constructs a new {@link AmountAtTime} */
	public AmountAtTime(TimeStamp timeStamp, double amount) {
		this.validAt = timeStamp;
		this.amount = amount;
	}

	/** Constructs a new {@link AmountAtTime} from its protobuf representation */
	public AmountAtTime(ProtoDataItem proto) {
		this.validAt = new TimeStamp(proto.getLongValue(0));
		this.amount = proto.getDoubleValue(0);
	}

	@Override
	protected void fillDataFields(Builder builder) {
		builder.addLongValue(validAt.getStep());
		builder.addDoubleValue(amount);
	}
}