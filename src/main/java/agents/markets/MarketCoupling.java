// SPDX-FileCopyrightText: 2026 German Aerospace Center <amiris@dlr.de>
//
// SPDX-License-Identifier: Apache-2.0
package agents.markets;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import agents.markets.meritOrder.DemandBalancer;
import agents.markets.meritOrder.books.TransmissionBook;
import communications.message.TransmissionCapacity;
import communications.portable.CouplingData;
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
import de.dlr.gitlab.fame.service.output.ComplexIndex;
import de.dlr.gitlab.fame.service.output.Output;
import de.dlr.gitlab.fame.time.TimeStamp;

/** Market coupling Agent that receives MeritOrderBooks from registered individual DayAheadMarket(s). It computes coupled
 * electricity prices aiming at minimising price differences between markets. Sends individual, coupled prices back to its client
 * markets.
 * 
 * @author A. Achraf El Ghazi, Felix Nitsch, Christoph Schimeczek, Johannes Kochems, Milena Sipovac */
public class MarketCoupling extends Agent {
	static final String MULTIPLE_REQUESTS = "Only one coupling request is allow per market, but multiple received from: ";
	static final String NO_AGENT_FOR_ZONE = "No DayAheadMarket agent found for market zone: ";
	static final double DEFAULT_DEMAND_SHIFT_OFFSET = 1.0;

	/** Products of {@link MarketCoupling} */
	@Product
	public static enum Products {
		/** Result of market coupling forecasts */
		MarketCouplingForecastResult,
		/** Result of the market coupling */
		MarketCouplingResult
	}

	@Input private static final Tree parameters = Make.newTree()
			.add(Make.newDouble("MinimumDemandOffsetInMWH").optional()
					.help("Offset added to the demand shift that ensures a price change at the involved markets."))
			.buildTree();

	@Output
	private static enum OutputColumns {
		/** Complex output; the capacity available for transfer between two markets in MWH */
		AvailableTransferCapacityInMWH,
		/** Complex output; the actual used transfer capacity between two markets in MWH */
		UsedTransferCapacityInMWH
	}

	private static enum TransferKey {
		OriginAgentId, TargetAgentId
	}

	private static final ComplexIndex<TransferKey> availableCapacity = ComplexIndex
			.build(OutputColumns.AvailableTransferCapacityInMWH, TransferKey.class);
	private static final ComplexIndex<TransferKey> usedCapacity = ComplexIndex.build(
			OutputColumns.UsedTransferCapacityInMWH, TransferKey.class);

	private final DemandBalancer demandBalancer;
	private Map<Long, CouplingData> couplingRequests = new HashMap<>();
	private Map<Long, TransmissionBook> initialTransmissionBookByMarket = new HashMap<>();

	/** Creates an {@link MarketCoupling}
	 * 
	 * @param dataProvider provides input from config
	 * @throws MissingDataException if any required data is not provided */
	public MarketCoupling(DataProvider dataProvider) throws MissingDataException {
		super(dataProvider);
		ParameterData input = parameters.join(dataProvider);
		double minEffectiveDemandOffset = input.getDoubleOrDefault("MinimumDemandOffsetInMWH", DEFAULT_DEMAND_SHIFT_OFFSET);
		demandBalancer = new DemandBalancer(minEffectiveDemandOffset);

		call(this::forecastCoupledMarkets).on(Products.MarketCouplingForecastResult)
				.use(MarketCouplingClient.Products.TransmissionAndBidForecasts);
		call(this::clearCoupledAndWriteResults).on(Products.MarketCouplingResult)
				.use(MarketCouplingClient.Products.TransmissionAndBids);
	}

	private void forecastCoupledMarkets(ArrayList<Message> input, List<Contract> contracts) {
		TreeMap<TimeStamp, ArrayList<Message>> messagesByClearingTime = sortMessagesByClearingTimeStamp(input);
		for (ArrayList<Message> messages : messagesByClearingTime.values()) {
			clearCoupledMarkets(messages, contracts, false);
		}
	}

	/** Groups given messages by their targeted time of delivery into an ordered Map
	 * 
	 * @param messages to group by
	 * @return a Map of Messages sorted by {@link TimeStamp} of the associated clearing times */
	protected TreeMap<TimeStamp, ArrayList<Message>> sortMessagesByClearingTimeStamp(ArrayList<Message> messages) {
		TreeMap<TimeStamp, ArrayList<Message>> messageByTimeStamp = new TreeMap<>();
		for (Message message : messages) {
			TimeStamp deliveryTime = message.getFirstPortableItemOfType(CouplingData.class).getClearingTime();
			messageByTimeStamp.computeIfAbsent(deliveryTime, __ -> new ArrayList<Message>()).add(message);
		}
		return messageByTimeStamp;
	}

	/** Action for the joint clearing of coupled markets
	 * <ul>
	 * <li>Ensures that this agent receives only one message from each contracted {@link EnergyExchange}.</li>
	 * <li>Reads the CouplingRequest(s) received from the EnergyExchage(s) and stores them in the {@link MarketCoupling
	 * #couplingRequests} map.</li>
	 * <li>Starts the actual coupled market-clearing algorithm.</li>
	 * <li>Sends result of the coupled market-clearing to contracted EnergyExchanges.</li>
	 * </ul>
	 * 
	 * @param input received CouplingRequests of the contracted EnergyExchanges
	 * @param contracts with said EnergyExchanges */
	private void clearCoupledMarkets(ArrayList<Message> input, List<Contract> contracts, boolean writeOutput) {
		for (Message message : input) {
			CouplingData couplingRequest = message.getFirstPortableItemOfType(CouplingData.class);
			initialTransmissionBookByMarket.put(message.getSenderId(), couplingRequest.getTransmissionBook());
			couplingRequests.put(message.getSenderId(), couplingRequest.clone());
		}
		demandBalancer.balance(couplingRequests);
		if (writeOutput) {
			writeCouplingResults();
		}
		sendCoupledBidsToExchanges(contracts);
	}

	private void clearCoupledAndWriteResults(ArrayList<Message> input, List<Contract> contracts) {
		clearCoupledMarkets(input, contracts, true);
	}

	/** Write results of market coupling to output **/
	private void writeCouplingResults() {
		for (Long originId : couplingRequests.keySet()) {
			CouplingData marketData = couplingRequests.get(originId);
			ArrayList<TransmissionCapacity> transmissionBook = marketData.getTransmissionBook().getTransmissionCapacities();
			ArrayList<TransmissionCapacity> initialTransmissionBook = initialTransmissionBookByMarket.get(originId)
					.getTransmissionCapacities();
			for (int i = 0; i < transmissionBook.size(); i++) {
				double remainingCapacity = transmissionBook.get(i).getRemainingTransferCapacityInMW();
				double initialCapacity = initialTransmissionBook.get(i).getRemainingTransferCapacityInMW();
				String targetMarketZone = transmissionBook.get(i).getTarget();
				Long targetId = getAgentIdOfMarketZone(targetMarketZone);
				store(
						availableCapacity.key(TransferKey.OriginAgentId, originId).key(TransferKey.TargetAgentId, targetId),
						initialCapacity);
				store(
						usedCapacity.key(TransferKey.OriginAgentId, originId).key(TransferKey.TargetAgentId, targetId),
						initialCapacity - remainingCapacity);
			}
		}
	}

	/** Returns the ID of the DayAheadMarket for the given MarketZone
	 * 
	 * @param marketZone to get the exchange ID for
	 * @return the DayAheadMarket agent ID of the given market zone */
	private Long getAgentIdOfMarketZone(String marketZone) {
		for (Long exchangeId : couplingRequests.keySet()) {
			String candidateZone = couplingRequests.get(exchangeId).getOrigin();
			if (candidateZone.equals(marketZone)) {
				return exchangeId;
			}
		}
		throw new RuntimeException(NO_AGENT_FOR_ZONE + marketZone);
	}

	/** Sends the optimised demand and supply order books to the contracted EnergyExchange(s)
	 * 
	 * @param contracts received contracts */
	private void sendCoupledBidsToExchanges(List<Contract> contracts) {
		for (Contract contract : contracts) {
			long id = contract.getReceiverId();
			fulfilNext(contract, couplingRequests.get(id));
		}
	}
}
