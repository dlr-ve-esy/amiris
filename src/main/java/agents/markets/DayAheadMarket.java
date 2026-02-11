// SPDX-FileCopyrightText: 2023-2026 German Aerospace Center <amiris@dlr.de>
//
// SPDX-License-Identifier: Apache-2.0
package agents.markets;

import java.util.ArrayList;
import java.util.List;
import agents.markets.meritOrder.MarketClearing;
import communications.message.ClearingTimes;
import de.dlr.gitlab.fame.agent.Agent;
import de.dlr.gitlab.fame.agent.input.DataProvider;
import de.dlr.gitlab.fame.agent.input.Input;
import de.dlr.gitlab.fame.agent.input.Make;
import de.dlr.gitlab.fame.agent.input.ParameterData;
import de.dlr.gitlab.fame.agent.input.ParameterData.MissingDataException;
import de.dlr.gitlab.fame.agent.input.Tree;
import de.dlr.gitlab.fame.communication.Contract;
import de.dlr.gitlab.fame.communication.Product;
import de.dlr.gitlab.fame.communication.message.Message;
import de.dlr.gitlab.fame.service.output.Output;
import de.dlr.gitlab.fame.time.TimeSpan;

/** Common market clearing routines for day-ahead energy markets. Unlike at actual day-ahead markets, market clearing is currently
 * implemented on an <b>hour-per-hour</b> basis.
 * 
 * @author Christoph Schimeczek, A. Achraf El Ghazi, Felix Nitsch, Johannes Kochems */
public abstract class DayAheadMarket extends Agent {
	static final String UNKNOWN_BID_TYPE = " is an unknown type of bid: ";
	static final String LONE_LIST = "At most one element is expected in this list: ";

	@Input private static final Tree parameters = Make.newTree()
			.addAs("Clearing", MarketClearing.parameters)
			.add(Make.newInt("GateClosureInfoOffsetInSeconds")).buildTree();

	/** Products of {@link DayAheadMarket}s */
	@Product
	public static enum Products {
		/** Awarded energy and price per bidding trader */
		Awards,
		/** States when the market clearing is performed and which time intervals it covers */
		GateClosureInfo
	};

	/** Output columns for all types of {@link DayAheadMarket}s */
	@Output
	protected static enum OutputFields {
		/** Total power awarded at last market clearing */
		AwardedEnergyInMWH,
		/** Market clearing price achieved at last market clearing */
		ElectricityPriceInEURperMWH,
		/** System cost for generating the power awarded at last market clearing */
		DispatchSystemCostInEUR,
	};

	private final TimeSpan gateClosureInfoOffset;
	/** Algorithm that performs the market clearing */
	protected final MarketClearing marketClearing;
	/** List of times the market will be cleared at the next clearing event */
	protected ClearingTimes clearingTimes;

	/** Creates an {@link DayAheadMarket}
	 * 
	 * @param dataProvider provides input from config
	 * @throws MissingDataException if any required data is not provided */
	public DayAheadMarket(DataProvider dataProvider) throws MissingDataException {
		super(dataProvider);
		ParameterData input = parameters.join(dataProvider);
		marketClearing = new MarketClearing(input.getGroup("Clearing"));
		gateClosureInfoOffset = new TimeSpan(input.getInteger("GateClosureInfoOffsetInSeconds"));

		/** Sends out ClearingTimes */
		call(this::sendGateClosureInfo).on(Products.GateClosureInfo);
	}

	/** Sends info upon next gate closure to connected traders
	 * 
	 * @param input n/a
	 * @param contracts connected traders to inform */
	private void sendGateClosureInfo(ArrayList<Message> input, List<Contract> contracts) {
		updateClearingTimes();
		for (Contract contract : contracts) {
			fulfilNext(contract, clearingTimes);
		}
	}

	/** Updates the clearing times: adds a single TimeStamp for the next clearing */
	private void updateClearingTimes() {
		clearingTimes = new ClearingTimes(now().laterBy(gateClosureInfoOffset));
	}

	/** @return String identifying the agent and time of market clearing */
	protected String getClearingEventId() {
		return this + " " + now();
	}
}
