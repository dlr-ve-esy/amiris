// SPDX-FileCopyrightText: 2024-2026 German Aerospace Center <amiris@dlr.de>
//
// SPDX-License-Identifier: Apache-2.0
package communications.portable;

import java.util.Arrays;
import java.util.List;
import agents.plantOperator.Marginal;
import de.dlr.gitlab.fame.communication.message.DataItem;
import de.dlr.gitlab.fame.communication.transfer.ComponentCollector;
import de.dlr.gitlab.fame.communication.transfer.ComponentProvider;
import de.dlr.gitlab.fame.communication.transfer.Portable;
import de.dlr.gitlab.fame.time.TimeStamp;

/** Summary of multiple {@link Marginal}s associated with the same producer and delivery time
 * 
 * @author Christoph Schimeczek */
public class MarginalsAtTime implements Portable {
	/** Which power plant agent is associated with these marginals */
	private long producerUuid;
	/** Begin of the delivery interval */
	private TimeStamp deliveryTime;
	/** Marginals of electricity production associated with its producer */
	private List<Marginal> marginals;

	/** required for {@link Portable}s */
	public MarginalsAtTime() {}

	/** Constructs new {@link MarginalsAtTime} {@link DataItem}
	 * 
	 * @param producerUuid unique ID of power production agent
	 * @param deliveryTime time at which the power production can be offered
	 * @param marginals any number of {@link Marginal}s associated with the given producer and at given time */
	public MarginalsAtTime(long producerUuid, TimeStamp deliveryTime, List<Marginal> marginals) {
		this.producerUuid = producerUuid;
		this.deliveryTime = deliveryTime;
		this.marginals = marginals;
	}

	/** Constructs new {@link MarginalsAtTime} {@link DataItem}
	 * 
	 * @param producerUuid unique ID of power production agent
	 * @param deliveryTime time at which the power production can be offered
	 * @param marginals any number of {@link Marginal}s associated with the given producer and at given time */
	public MarginalsAtTime(long producerUuid, TimeStamp deliveryTime, Marginal... marginals) {
		this.producerUuid = producerUuid;
		this.deliveryTime = deliveryTime;
		this.marginals = Arrays.asList(marginals);
	}

	@Override
	public void addComponentsTo(ComponentCollector collector) {
		collector.storeLongs(producerUuid);
		collector.storeComponents(deliveryTime);
		for (Marginal marginal : marginals) {
			collector.storeComponents(marginal);
		}
	}

	@Override
	public void populate(ComponentProvider provider) {
		producerUuid = provider.nextLong();
		deliveryTime = provider.nextComponent(TimeStamp.class);
		marginals = provider.nextComponentList(Marginal.class);
	}

	/** @return UUID of power plant associated with the marginals herein */
	public long getProducerUuid() {
		return producerUuid;
	}

	/** @return the begin of the delivery interval */
	public TimeStamp getDeliveryTime() {
		return deliveryTime;
	}

	/** @return marginals of electricity production associated with its producer */
	public List<Marginal> getMarginals() {
		return marginals;
	}

	/** @return sum of all power potentials in the marginals */
	public double getTotalPowerPotentialInMW() {
		return marginals.stream().mapToDouble(e -> e.getPowerPotentialInMW()).sum();
	}
}
