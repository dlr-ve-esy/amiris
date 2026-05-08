// SPDX-FileCopyrightText: 2021-2026 German Aerospace Center <amiris@dlr.de>
//
// SPDX-License-Identifier: Apache-2.0
package agents.trader;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;
import agents.markets.DayAheadMarket;
import agents.markets.DayAheadMarketTrader;
import agents.plantOperator.Marginal;
import communications.portable.MarginalsAtTime;
import de.dlr.gitlab.fame.agent.Agent;
import de.dlr.gitlab.fame.agent.input.DataProvider;
import de.dlr.gitlab.fame.communication.Contract;
import de.dlr.gitlab.fame.communication.Product;
import de.dlr.gitlab.fame.communication.message.Message;
import de.dlr.gitlab.fame.time.TimeStamp;

/** Abstract base class for all traders at {@link DayAheadMarket}
 *
 * @author Christoph Schimeczek */
public abstract class Trader extends Agent implements DayAheadMarketTrader {
	static final String ERR_NO_CONTRACT_IN_LIST = "No contract found for agent: ";
	static final String ERR_DELIVERY_TIME_MISMATCH = "ExtractMarginalsAtTime() required identical delivery time stamps for all messages.";

	/** Products of {@link Trader}s */
	@Product
	public static enum Products {
		/** Forecasts of Bids sent to Forecasters */
		BidsForecast,
	}

	/** Creates a {@link Trader}
	 * 
	 * @param dataProvider provides input from config */
	public Trader(DataProvider dataProvider) {
		super(dataProvider);
	}

	/** Reads {@link MarginalsAtTime} from given messages and sorts them by the time stamp they are valid at
	 * 
	 * @param messages containing MarginalsAtTime - to be sorted by time stamp
	 * @return a Map of MarginalsAtTime sorted by the {@link TimeStamp} they are valid at */
	protected TreeMap<TimeStamp, ArrayList<MarginalsAtTime>> sortMarginalsByTimeStamp(ArrayList<Message> messages) {
		TreeMap<TimeStamp, ArrayList<MarginalsAtTime>> marginalsByTimeStamp = new TreeMap<>();
		for (Message message : messages) {
			MarginalsAtTime marginals = message.getFirstPortableItemOfType(MarginalsAtTime.class);
			TimeStamp timeStamp = marginals.getDeliveryTime();
			marginalsByTimeStamp.computeIfAbsent(timeStamp, __ -> new ArrayList<>()).add(marginals);
		}
		return marginalsByTimeStamp;
	}

	/** Reads {@link MarginalsAtTime} from given messages and ensures they are valid at the same time stamp
	 * 
	 * @param messages containing MarginalsAtTime to be extracted
	 * @return List of MarginalsAtTime contained in given messages
	 * @throws RuntimeException if delivery time does not match for all marginals */
	protected ArrayList<MarginalsAtTime> extractMarginalsAtTime(ArrayList<Message> messages) {
		ArrayList<MarginalsAtTime> result = new ArrayList<>(messages.size());
		TimeStamp time = null;
		for (Message message : messages) {
			MarginalsAtTime marginalsAtTime = message.getFirstPortableItemOfType(MarginalsAtTime.class);
			result.add(marginalsAtTime);
			if (time == null) {
				time = marginalsAtTime.getDeliveryTime();
			} else {
				if (time != marginalsAtTime.getDeliveryTime()) {
					throw new RuntimeException(ERR_DELIVERY_TIME_MISMATCH);
				}
			}
		}
		return result;
	}

	/** Return list of all marginals combined from given data and sorted by marginal cost, ascending
	 * 
	 * @param allMarginals to have their {@link Marginal}s extracted and then sorted by costs
	 * @return {@link Marginal}s extracted from given data sorted in ascending order of their marginal cost */
	protected ArrayList<Marginal> getSortedMarginalList(ArrayList<MarginalsAtTime> allMarginals) {
		ArrayList<Marginal> marginals = new ArrayList<>();
		for (MarginalsAtTime marginalsAtTime : allMarginals) {
			marginals.addAll(marginalsAtTime.getMarginals());
		}
		marginals.sort(Marginal.byCostAscending);
		return marginals;
	}

	/** Returns first contract that has the provided receiver's agentID
	 * 
	 * @param contracts whose receivers are searched for given id
	 * @param receiverId to search for
	 * @return first matching contract from given list of {@link Contract}s where the given agentID is receiver */
	protected Contract getContractWithReceiver(List<Contract> contracts, long receiverId) {
		for (Contract contract : contracts) {
			if (receiverId == contract.getReceiverId()) {
				return contract;
			}
		}
		throw new RuntimeException(ERR_NO_CONTRACT_IN_LIST + receiverId);
	}
}