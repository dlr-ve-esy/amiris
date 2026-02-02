// SPDX-FileCopyrightText: 2026 German Aerospace Center <amiris@dlr.de>
//
// SPDX-License-Identifier: Apache-2.0
package agents.markets;

import java.util.HashMap;
import java.util.Map.Entry;
import agents.markets.meritOrder.books.TransmissionBook;
import communications.message.TransmissionCapacity;
import de.dlr.gitlab.fame.agent.AgentAbility;
import de.dlr.gitlab.fame.communication.Product;
import de.dlr.gitlab.fame.data.TimeSeries;
import de.dlr.gitlab.fame.time.TimeStamp;

/** Defines Ability to interact with {@link MarketCoupling}
 * 
 * @author Christoph Schimeczek, Felix Nitsch, Johannes Kochems, Milena Sipovac */
public interface MarketCouplingClient extends AgentAbility {
	@Product
	public static enum Products {
		/** Transmission capacities and bids for forecasts */
		TransmissionAndBidForecasts,
		/** Transmission capacities and bids from local exchange */
		TransmissionAndBids
	}

	/** Create {@link TransmissionBook} for given time and origin market zone to neighbouring market zones
	 * 
	 * @param originMarketZone name of market zone of which transmission originate from
	 * @param transmissionCapacities maximum capacity time series for transmission to neighbouring market zones
	 * @param time at which to evaluate the transmission capacities
	 * @return book of max transmission capacities to market zones neighbouring the origin market zone */
	public static TransmissionBook buildTransmissionBook(String originMarketZone,
			HashMap<String, TimeSeries> transmissionCapacities, TimeStamp time) {
		TransmissionBook transmissionBook = new TransmissionBook(originMarketZone);
		for (Entry<String, TimeSeries> entry : transmissionCapacities.entrySet()) {
			double transmissionCapacityInMW = entry.getValue().getValueEarlierEqual(time);
			transmissionBook.add(new TransmissionCapacity(entry.getKey(), transmissionCapacityInMW));
		}
		return transmissionBook;
	}
}