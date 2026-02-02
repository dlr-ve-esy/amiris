// SPDX-FileCopyrightText: 2026 German Aerospace Center <amiris@dlr.de>
//
// SPDX-License-Identifier: Apache-2.0
package communications.portable;

import java.util.HashMap;
import java.util.Map.Entry;
import de.dlr.gitlab.fame.communication.transfer.ComponentCollector;
import de.dlr.gitlab.fame.communication.transfer.ComponentProvider;
import de.dlr.gitlab.fame.communication.transfer.Portable;
import de.dlr.gitlab.fame.data.TimeSeries;

/** Net transfer capacities over time of supply from one market zone to other market zone(s)
 * 
 * @author Christoph Schimeczek, Felix Nitsch, Johannes Kochems, Milena Sipovac */
public class TransmissionCapacitySeries implements Portable {
	private HashMap<String, TimeSeries> transmissionCapacities;

	/** required for {@link Portable}s */
	public TransmissionCapacitySeries() {}

	/** Create new {@link TransmissionCapacitySeries}
	 * 
	 * @param transmissionCapacities to be sent */
	public TransmissionCapacitySeries(HashMap<String, TimeSeries> transmissionCapacities) {
		this.transmissionCapacities = transmissionCapacities;
	}

	@Override
	public void addComponentsTo(ComponentCollector collector) {
		collector.storeInts(transmissionCapacities.size());
		for (Entry<String, TimeSeries> entry : transmissionCapacities.entrySet()) {
			collector.storeStrings(entry.getKey());
			collector.storeTimeSeries(entry.getValue());
		}
	}

	@Override
	public void populate(ComponentProvider provider) {
		int numberOfElements = provider.nextInt();
		transmissionCapacities = new HashMap<>(numberOfElements);
		for (int index = 0; index < numberOfElements; index++) {
			transmissionCapacities.put(provider.nextString(), provider.nextTimeSeries());
		}
	}
}
