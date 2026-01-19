// SPDX-FileCopyrightText: 2026 German Aerospace Center <amiris@dlr.de>
//
// SPDX-License-Identifier: Apache-2.0
package agents.markets;

import java.util.ArrayList;
import java.util.List;
import communications.message.ClearingTimes;
import de.dlr.gitlab.fame.agent.AgentAbility;
import de.dlr.gitlab.fame.communication.Product;
import de.dlr.gitlab.fame.communication.message.Message;
import de.dlr.gitlab.fame.service.output.Output;
import de.dlr.gitlab.fame.time.TimeStamp;

/** Interface for traders at the {@link DayAheadMarket}
 * 
 * @author Christoph Schimeczek */
public interface DayAheadMarketTrader extends AgentAbility {
	/** Error message if {@link ClearingTimes} payload is missing */
	static String ERR_CLEARING_TIMES_MISSING = "None of the given messages contained a ClearingTimes payload.";
	/** Error message if {@link ClearingTimes} payload is ambiguous */
	static String ERR_CLEARING_TIMES_AMBIGUOUS = "More than one of the given messages contained a ClearingTimes payload.";

	/** Products of traders interacting with {@link DayAheadMarket} */
	@Product
	public static enum Products {
		/** Sell/Buy orders to be placed at the {@link DayAheadMarket} */
		Bids
	}

	/** Available output columns */
	@Output
	public static enum OutputColumns {
		/** Energy offered to energy exchange */
		OfferedEnergyInMWH,
		/** Energy awarded by energy exchange */
		AwardedEnergyInMWH,
		/** Energy requested at energy exchange */
		RequestedEnergyInMWH
	}

	/** Searches for a single {@link DayAheadMarket.Products#GateClosureInfo} message in given messages and returns its times
	 * 
	 * @param messages list of messages to search for a single one with {@link ClearingTimes} payload
	 * @return {@link TimeStamp}s contained in the found {@link ClearingTimes} payload
	 * @throws IllegalArgumentException if not exactly one message contained a ClearingTimes payload */
	public default List<TimeStamp> extractTimesFromGateClosureInfoMessages(ArrayList<Message> messages) {
		List<TimeStamp> clearingTimes = null;
		for (Message message : messages) {
			if (isGateClosureInfoMessage(message)) {
				if (clearingTimes != null) {
					throw new IllegalArgumentException(ERR_CLEARING_TIMES_AMBIGUOUS);
				} else {
					clearingTimes = readGateClosureInfoMessage(message);
				}
			}
		}
		if (clearingTimes == null) {
			throw new IllegalArgumentException(ERR_CLEARING_TIMES_MISSING);
		}
		return clearingTimes;
	}

	/** Read a {@link DayAheadMarket.Products#GateClosureInfo} message from a contracted {@link DayAheadMarket}
	 * 
	 * @param message to be read
	 * @return Extracted times at which the market will be cleared */
	public default List<TimeStamp> readGateClosureInfoMessage(Message message) {
		return message.getDataItemOfType(ClearingTimes.class).getTimes();
	}

	/** @return true if message contains a {@link ClearingTimes} payload */
	private boolean isGateClosureInfoMessage(Message message) {
		return message.containsType(ClearingTimes.class);
	}
}
