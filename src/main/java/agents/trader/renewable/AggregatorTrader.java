// SPDX-FileCopyrightText: 2021-2026 German Aerospace Center <amiris@dlr.de>
//
// SPDX-License-Identifier: Apache-2.0
package agents.trader.renewable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;
import java.util.TreeMap;
import agents.forecast.MarketForecaster;
import agents.forecast.PowerForecastError;
import agents.markets.DayAheadMarket;
import agents.markets.DayAheadMarketTrader;
import agents.markets.meritOrder.Bid;
import agents.plantOperator.Marginal;
import agents.plantOperator.PowerPlantOperator;
import agents.plantOperator.PowerPlantScheduler;
import agents.plantOperator.RenewablePlantOperator;
import agents.policy.SupportPolicy;
import agents.policy.SupportPolicy.EnergyCarrier;
import agents.trader.ClientData;
import agents.trader.Trader;
import agents.trader.TraderWithClients;
import communications.message.AmountAtTime;
import communications.message.AwardData;
import communications.message.ClearingTimes;
import communications.message.SupportRequestData;
import communications.message.SupportResponseData;
import communications.message.TechnologySet;
import communications.message.YieldPotential;
import communications.portable.BidsAtTime;
import communications.portable.MarginalsAtTime;
import communications.portable.SupportData;
import de.dlr.gitlab.fame.agent.input.DataProvider;
import de.dlr.gitlab.fame.agent.input.Input;
import de.dlr.gitlab.fame.agent.input.Make;
import de.dlr.gitlab.fame.agent.input.ParameterData;
import de.dlr.gitlab.fame.agent.input.ParameterData.MissingDataException;
import de.dlr.gitlab.fame.agent.input.Tree;
import de.dlr.gitlab.fame.communication.CommUtils;
import de.dlr.gitlab.fame.communication.Contract;
import de.dlr.gitlab.fame.communication.Product;
import de.dlr.gitlab.fame.communication.message.Message;
import de.dlr.gitlab.fame.service.output.Output;
import de.dlr.gitlab.fame.time.TimePeriod;
import de.dlr.gitlab.fame.time.TimeStamp;

/** Aggregates supply capacity and administers support payments to plant operators
 * 
 * @author Johannes Kochems, Christoph Schimeczek, Felix Nitsch, Farzad Sarfarazi, Kristina Nienhaus */
public abstract class AggregatorTrader extends TraderWithClients implements PowerPlantScheduler {
	static final String GROUP_FORECAST_ERROR = "ForecastError";

	@Input private static final Tree parameters = Make.newTree()
			.addAs(GROUP_FORECAST_ERROR, PowerForecastError.parameters)
			.buildTree();

	private static final double BIN_WIDTH = 1E-5;

	static final String ERR_NO_MESSAGE_FOUND = "No client data received for client: ";
	static final String ERR_SUPPORT_INFO = "Support info not implemented: ";
	static final String ERR_NO_CLIENT_FOR_SET = " has no client with technology set type: ";
	static final String ERR_TIMESTAMP_LEFTOVER = "Accounting period mismatch; No payout was obtained for dispatch at time stamp: ";

	/** Columns of the output file */
	@Output
	private static enum OutputColumns {
		/** overall received support payments from policy agent */
		ReceivedSupportInEUR,
		/** overall support refunded to policy agent (in CFD scheme) */
		RefundedSupportInEUR,
		/** overall received market revenues from marketing power plants */
		ReceivedMarketRevenues,
		/** actual electricity generation potential */
		TrueGenerationPotentialInMWH
	}

	/** Products of this Agent */
	@Product
	public static enum Products {
		/** Request for support information for contracted technology set(s) */
		SupportInfoRequest,
		/** Request to obtain support payments for contracted technology set(s) */
		SupportPayoutRequest,
		/** Yield potential of contracted technology set(s) */
		YieldPotential
	}

	/** Helper class to store producer ID along with each Bid */
	private class ProducerBid {
		public final long producerUuid;
		public final double powerPotential;
		public final Bid bid;

		/** Create new {@link ProducerBid} instance
		 * 
		 * @param bid to be associated with additional data
		 * @param producerUuid UUID of the bid's producer
		 * @param powerPotential true power potential */
		public ProducerBid(Bid bid, long producerUuid, double powerPotential) {
			this.bid = bid;
			this.producerUuid = producerUuid;
			this.powerPotential = powerPotential;
		}

		/** @return offer price of underlying bid */
		public double getOfferPrice() {
			return bid.getOfferPriceInEURperMWH();
		}
	}

	/** Map to store all client, i.e. {@link RenewablePlantOperator}, specific data */
	protected final HashMap<Long, ClientData> clientMap = new HashMap<>();

	/** Prepared Bids */
	private final TreeMap<TimeStamp, List<ProducerBid>> preparedBidsByTime = new TreeMap<>();
	/** Stores the power prices from {@link DayAheadMarket} */
	private final TreeMap<TimeStamp, Double> powerPrices = new TreeMap<>();
	/** Adds random errors (normally distributed) to the amount of offered power */
	private PowerForecastError errorGenerator;

	/** Creates an {@link AggregatorTrader}
	 * 
	 * @param dataProvider provides input from config */
	public AggregatorTrader(DataProvider dataProvider) {
		super(dataProvider);
		ParameterData inputData = parameters.join(dataProvider);
		try {
			errorGenerator = new PowerForecastError(inputData.getGroup(GROUP_FORECAST_ERROR), getNextRandomNumberGenerator());
		} catch (MissingDataException e) {
			errorGenerator = null;
		}

		call(this::registerClient).onAndUse(RenewablePlantOperator.Products.SetRegistration);
		call(this::requestSupportInfo).on(Products.SupportInfoRequest);
		call(this::digestSupportInfo).onAndUse(SupportPolicy.Products.SupportInfo);
		call(this::prepareForecastBids).on(Trader.Products.BidsForecast)
				.use(PowerPlantOperator.Products.MarginalCostForecast);
		call(this::prepareBids).on(DayAheadMarketTrader.Products.Bids).use(PowerPlantOperator.Products.MarginalCost);
		call(this::sendYieldPotentials).on(Products.YieldPotential).use(DayAheadMarket.Products.GateClosureInfo);
		call(this::assignDispatch).on(PowerPlantScheduler.Products.DispatchAssignment).use(DayAheadMarket.Products.Awards);
		call(this::requestSupportPayout).on(Products.SupportPayoutRequest);
		call(this::digestSupportPayout).onAndUse(SupportPolicy.Products.SupportPayout);
		call(this::payoutClients).on(PowerPlantScheduler.Products.Payout);
	}

	/** Extract information on {@link TechnologySet} and add it to the client data collection
	 * 
	 * @param messages client registration information: {@link TechnologySet}
	 * @param contracts not used */
	private void registerClient(ArrayList<Message> messages, List<Contract> contracts) {
		for (Contract contract : contracts) {
			long clientId = contract.getSenderId();
			ClientData clientData = searchClientData(messages, clientId);
			clientMap.put(clientId, clientData);
		}
	}

	/** Find client data based on client's Id */
	private ClientData searchClientData(ArrayList<Message> messages, long clientId) {
		for (Message message : messages) {
			if (message.senderId == clientId) {
				TechnologySet technologySet = message.getDataItemOfType(TechnologySet.class);
				double installedPowerInMW = message.getDataItemOfType(AmountAtTime.class).amount;
				return new ClientData(technologySet, installedPowerInMW);
			}
		}
		throw new RuntimeException(ERR_NO_MESSAGE_FOUND + clientId);
	}

	/** Request support details by informing it of the {@link TechnologySet}s and their instrument
	 * 
	 * @param messages not used
	 * @param contracts single partner (typically {@link SupportPolicy}) */
	private void requestSupportInfo(ArrayList<Message> messages, List<Contract> contracts) {
		Contract contract = CommUtils.getExactlyOneEntry(contracts);
		for (ClientData clientData : clientMap.values()) {
			TechnologySet technologySet = clientData.getTechnologySet();
			fulfilNext(contract, technologySet);
		}
	}

	/** Processes the received support information and adds it to the associated client's data
	 * 
	 * @param messages support information, typically from {@link SupportPolicy}
	 * @param contracts not used */
	private void digestSupportInfo(ArrayList<Message> messages, List<Contract> contracts) {
		for (Message message : messages) {
			SupportData supportData = message.getFirstPortableItemOfType(SupportData.class);
			String technologySetType = supportData.getSetType();
			for (ClientData clientData : getClientDataForSetType(technologySetType)) {
				clientData.setSupportData(supportData);
			}
		}
	}

	/** Return all data of clients that match the given setType
	 * 
	 * @param setType to search for
	 * @return client data for given set type */
	protected List<ClientData> getClientDataForSetType(String setType) {
		List<ClientData> clients = new ArrayList<>();
		for (ClientData clientData : clientMap.values()) {
			if (clientData.getTechnologySet().setType.equals(setType)) {
				clients.add(clientData);
			}
		}
		if (clients.isEmpty()) {
			throw new RuntimeException(this + ERR_NO_CLIENT_FOR_SET + setType);
		}
		return clients;
	}

	/** Sends supply {@link Bid} forecasts
	 * 
	 * @param messages marginal cost forecasts to process - typically from {@link RenewablePlantOperator}
	 * @param contractsToFulfill one partner to send bid forecasts to, typically a {@link MarketForecaster} */
	private void prepareForecastBids(ArrayList<Message> messages, List<Contract> contractsToFulfill) {
		Contract contract = CommUtils.getExactlyOneEntry(contractsToFulfill);
		TreeMap<TimeStamp, ArrayList<MarginalsAtTime>> marginalsByTimeStamp = sortMarginalsByTimeStamp(messages);
		for (ArrayList<MarginalsAtTime> marginals : marginalsByTimeStamp.values()) {
			submitHourlyBids(contract, marginals);
		}
	}

	/** Prepares hourly bids based on given marginals and sends them to the contracted partner; if bids were previously calculated,
	 * e.g., during forecast, these bids are reused. If no bids were previously calculated for the TimeStamp associated with the
	 * provided marginals, the producer's bids are calculated based on the marginals and stored for later reuse. This ensures
	 * consistent errors for forecast bids, actual bids, and dispatch assignment.
	 * 
	 * @param contract to fulfil - either with forecaster or day ahead market
	 * @param allMarginals to be used for bid calculation; list must not be empty; entries must all have the same delivery time */
	protected void submitHourlyBids(Contract contract, ArrayList<MarginalsAtTime> allMarginals) {
		ArrayList<Bid> supplyBids = new ArrayList<>();
		TimeStamp targetTime = allMarginals.get(0).getDeliveryTime();

		if (preparedBidsByTime.containsKey(targetTime)) {
			for (ProducerBid producerBid : preparedBidsByTime.get(targetTime)) {
				supplyBids.add(producerBid.bid);
			}
		} else {
			List<ProducerBid> producerBids = new ArrayList<>();
			for (MarginalsAtTime marginalsAtTime : allMarginals) {
				long producerUuid = marginalsAtTime.getProducerUuid();
				for (Marginal marginal : marginalsAtTime.getMarginals()) {
					Bid bid = calcBids(marginal, targetTime, producerUuid);
					producerBids.add(new ProducerBid(bid, producerUuid, marginal.getPowerPotentialInMW()));
					supplyBids.add(bid);
				}
			}
			preparedBidsByTime.put(targetTime, producerBids);
		}
		fulfilNext(contract, new BidsAtTime(targetTime, getId(), supplyBids, null));
	}

	/** Creates a {@link Bid} from given Marginal
	 * 
	 * @param marginal pair of true cost and power potential
	 * @param targetTime associated with the marginal and bid
	 * @param producerUuid id of plant operator associated with marginal
	 * @return created bid */
	protected abstract Bid calcBids(Marginal marginal, TimeStamp targetTime, long producerUuid);

	/** Sends supply {@link Bid}s to {@link DayAheadMarket}
	 * 
	 * @param messages marginal cost to process - typically from {@link RenewablePlantOperator}
	 * @param contracts one {@link DayAheadMarket} to send bids to */
	private void prepareBids(ArrayList<Message> messages, List<Contract> contracts) {
		Contract contract = CommUtils.getExactlyOneEntry(contracts);
		ArrayList<MarginalsAtTime> marginals = extractMarginalsAtTime(messages);
		if (marginals.size() > 0) {
			submitHourlyBids(contract, marginals);
			storeYieldPotentials(marginals);
			storeOfferedEnergy(preparedBidsByTime.get(marginals.get(0).getDeliveryTime()));
		}
	}

	/** Calculate a power with errors from forecast
	 * 
	 * @param truePowerPotential perfect foresight power potential without any errors
	 * @return power potential modified by power forecast error, if applicable - otherwise the given true potential */
	protected double getPowerWithError(double truePowerPotential) {
		return errorGenerator != null ? errorGenerator.calcPowerWithError(truePowerPotential) : truePowerPotential;
	}

	/** Store {@link YieldPotential}s for RES market value calculation **/
	private void storeYieldPotentials(ArrayList<MarginalsAtTime> allMarginals) {
		for (MarginalsAtTime marginalsAtTime : allMarginals) {
			ClientData clientData = clientMap.get(marginalsAtTime.getProducerUuid());
			for (Marginal marginal : marginalsAtTime.getMarginals()) {
				clientData.appendYieldPotential(marginalsAtTime.getDeliveryTime(), marginal.getPowerPotentialInMW());
			}
		}
	}

	/** Store the amount of energy offered */
	private void storeOfferedEnergy(List<ProducerBid> submittedBids) {
		double offeredEnergy = submittedBids.stream().mapToDouble(i -> i.bid.getEnergyAmountInMWH()).sum();
		store(DayAheadMarketTrader.OutputColumns.OfferedEnergyInMWH, offeredEnergy);
	}

	/** Forward yield potential information from clients
	 * 
	 * @param messages one ClearingTimes message
	 * @param contracts one partner, typically {@link SupportPolicy} */
	private void sendYieldPotentials(ArrayList<Message> messages, List<Contract> contracts) {
		Contract contract = CommUtils.getExactlyOneEntry(contracts);
		ClearingTimes clearingTimes = CommUtils.getExactlyOneEntry(messages).getDataItemOfType(ClearingTimes.class);
		for (TimeStamp time : clearingTimes.getTimes()) {
			for (ClientData clientData : clientMap.values()) {
				double yieldPotential = clientData.getYieldPotential().get(time);
				EnergyCarrier energyCarrier = clientData.getTechnologySet().energyCarrier;
				fulfilNext(contract, new YieldPotential(time, yieldPotential, energyCarrier));
			}
		}
	}

	/** Determine capacity to be dispatched based on {@link AwardData} from {@link DayAheadMarket}; dispatch is assigned in
	 * ascending order of bid prices, thus accounting for marginal cost + expected policy payments
	 * 
	 * @param messages single award message from {@link DayAheadMarket}
	 * @param contracts clients (typically {@link RenewablePlantOperator}s) to receive their dispatch assignment */
	private void assignDispatch(ArrayList<Message> messages, List<Contract> contracts) {
		Message message = CommUtils.getExactlyOneEntry(messages);
		AwardData award = message.getDataItemOfType(AwardData.class);
		store(DayAheadMarketTrader.OutputColumns.AwardedEnergyInMWH, award.supplyEnergyInMWH);
		double energyToDispatch = award.supplyEnergyInMWH;
		List<ProducerBid> submittedBids = preparedBidsByTime.remove(award.beginOfDeliveryInterval);
		submittedBids.sort(Comparator.comparingDouble(ProducerBid::getOfferPrice));

		HashMap<Integer, List<ProducerBid>> bidsBinnedByOfferPrice = getBinnedBids(submittedBids);
		double actualProductionPotentialInMWH = 0;
		for (var bids : bidsBinnedByOfferPrice.values()) {
			double productionPotentialInBinInMWH = bids.stream().mapToDouble(b -> b.powerPotential).sum();
			actualProductionPotentialInMWH += productionPotentialInBinInMWH;
			double assignmentShare = Math.min(1., energyToDispatch / productionPotentialInBinInMWH);
			for (ProducerBid bid : bids) {
				double dispatchedEnergy = assignmentShare * bid.powerPotential;
				energyToDispatch = Math.max(0, energyToDispatch - dispatchedEnergy);
				logClientDispatchAndRevenues(dispatchedEnergy, award.powerPriceInEURperMWH, bid.producerUuid,
						award.beginOfDeliveryInterval);
				Contract matchingContract = getContractWithReceiver(contracts, bid.producerUuid);
				fulfilNext(matchingContract, new AmountAtTime(award.beginOfDeliveryInterval, dispatchedEnergy));
			}
		}
		powerPrices.put(award.beginOfDeliveryInterval, award.powerPriceInEURperMWH);
		store(OutputColumns.TrueGenerationPotentialInMWH, actualProductionPotentialInMWH);
	}

	/** Return given bids in offer-price bins - bids with similar offer price are mapped to the same bin
	 * 
	 * @param bids ordered by offer price in ascending order
	 * @return bids in bins by offer price; to counter numerical instability bids within a small tolerance band are assigned the
	 *         same bin */
	private HashMap<Integer, List<ProducerBid>> getBinnedBids(List<ProducerBid> bids) {
		HashMap<Integer, List<ProducerBid>> binnedBids = new HashMap<>();
		double lowestOfferPriceInBin = Double.NEGATIVE_INFINITY;
		int binIndex = -1;
		List<ProducerBid> bidsInBin = new ArrayList<>();
		for (ProducerBid bid : bids) {
			if (bid.getOfferPrice() - BIN_WIDTH > lowestOfferPriceInBin) {
				binIndex++;
				lowestOfferPriceInBin = bid.getOfferPrice();
				bidsInBin = new ArrayList<>();
				binnedBids.put(binIndex, bidsInBin);
			}
			bidsInBin.add(bid);
		}
		return binnedBids;
	}

	/** Logs actual dispatch and revenue for client at given delivery time
	 * 
	 * @param dispatchedEnergy assigned to this bid
	 * @param powerPrice awarded price
	 * @param clientId ID of the power plant to be dispatched
	 * @param deliveryTime at which the electricity is produced by the associated producer */
	protected void logClientDispatchAndRevenues(double dispatchedEnergy, double powerPrice, long clientId,
			TimeStamp deliveryTime) {
		ClientData clientData = clientMap.get(clientId);
		double stepRevenue = powerPrice * dispatchedEnergy;
		clientData.appendStepDispatchAndRevenue(deliveryTime, dispatchedEnergy, stepRevenue);
	}

	/** Request support pay-out - one message per client
	 * 
	 * @param messages not used
	 * @param contracts one partner to ask for policy pay-out, i.e. {@link SupportPolicy} */
	private void requestSupportPayout(ArrayList<Message> messages, List<Contract> contracts) {
		Contract contract = CommUtils.getExactlyOneEntry(contracts);
		TimePeriod accountingPeriod = SupportPolicy.extractAccountingPeriod(now(), contract, 1800L);
		for (Entry<Long, ClientData> entries : clientMap.entrySet()) {
			SupportRequestData supportData = new SupportRequestData(entries, accountingPeriod);
			fulfilNext(contract, supportData);
		}
		powerPrices.headMap(accountingPeriod.getLastTime()).clear();
	}

	/** Collect and store the support revenues per client, i.e. operator of the {@link TechnologySet}
	 * 
	 * @param messages one pay-out message per client
	 * @param contracts not used */
	private void digestSupportPayout(ArrayList<Message> messages, List<Contract> contracts) {
		double receivedSupport = 0.0;
		double refundedSupport = 0.0;
		for (Message message : messages) {
			SupportResponseData supportDataResponse = message.getDataItemOfType(SupportResponseData.class);
			ClientData clientData = clientMap.get(supportDataResponse.clientId);
			clientData.appendSupportRevenue(supportDataResponse.accountingPeriod, supportDataResponse.payment);
			clientData.appendMarketPremium(supportDataResponse.accountingPeriod, supportDataResponse.marketPremium);
			receivedSupport += Math.max(0, supportDataResponse.payment);
			refundedSupport -= Math.min(0, supportDataResponse.payment);
		}
		store(OutputColumns.ReceivedSupportInEUR, receivedSupport);
		store(OutputColumns.RefundedSupportInEUR, refundedSupport);
	}

	/** Forward support pay-out to clients
	 * 
	 * @param messages not used
	 * @param contracts with clients to pay-out, typically {@link RenewablePlantOperator}s */
	private void payoutClients(ArrayList<Message> messages, List<Contract> contracts) {
		double receivedMarketRevenues = 0;
		for (Contract contract : contracts) {
			long plantOperatorId = contract.getReceiverId();
			TimePeriod accountingPeriod = SupportPolicy.extractAccountingPeriod(now(), contract, 1796L); // -3L
			double marketRevenue = calcMarketRevenue(plantOperatorId, accountingPeriod);
			receivedMarketRevenues += marketRevenue;
			double payout = applyPayoutStrategy(plantOperatorId, accountingPeriod, marketRevenue);
			AmountAtTime contractualPayout = new AmountAtTime(now(), payout);
			fulfilNext(contract, contractualPayout);
			clientMap.get(plantOperatorId).clearBefore(now());
		}
		store(OutputColumns.ReceivedMarketRevenues, receivedMarketRevenues);
	}

	/** Define a pay-out strategy for the contractual pay-out (in child classes)
	 * 
	 * @param plantOperatorId the operator to pay
	 * @param accountingPeriod to be considered
	 * @param marketRevenue earned with bids associated with that operator
	 * @return value to pay out to that plant operator */
	protected abstract double applyPayoutStrategy(long plantOperatorId, TimePeriod accountingPeriod,
			double marketRevenue);

	/** Calculate and return the sum of market revenues during a given accounting period for a given client */
	private double calcMarketRevenue(long plantOperatorId, TimePeriod accountingPeriod) {
		ClientData clientData = clientMap.get(plantOperatorId);
		double marketRevenue = 0;
		Iterator<Entry<TimeStamp, Double>> iterator = clientData.getMarketRevenue().entrySet().iterator();
		while (iterator.hasNext()) {
			Entry<TimeStamp, Double> entry = iterator.next();
			if (entry.getKey().isLessThan(accountingPeriod.getStartTime())) {
				throw new RuntimeException(ERR_TIMESTAMP_LEFTOVER + entry.getKey());
			}
			if (entry.getKey().isLessEqualTo(accountingPeriod.getLastTime())) {
				marketRevenue += entry.getValue();
				iterator.remove();
			}
		}
		return marketRevenue;
	}
}
