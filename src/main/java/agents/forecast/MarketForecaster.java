// SPDX-FileCopyrightText: 2021-2026 German Aerospace Center <amiris@dlr.de>
//
// SPDX-License-Identifier: Apache-2.0
package agents.forecast;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;
import java.util.TreeMap;
import agents.markets.DayAheadMarket;
import agents.markets.DayAheadMarketMultiZone;
import agents.markets.MarketCoupling;
import agents.markets.MarketCouplingClient;
import agents.markets.meritOrder.MarketClearing;
import agents.markets.meritOrder.MarketClearingResult;
import agents.markets.meritOrder.books.DemandOrderBook;
import agents.markets.meritOrder.books.SupplyOrderBook;
import agents.markets.meritOrder.books.TransmissionBook;
import agents.trader.Trader;
import communications.message.AmountAtTime;
import communications.message.ClearingTimes;
import communications.message.PointInTime;
import communications.portable.BidsAtTime;
import communications.portable.CouplingData;
import communications.portable.MeritOrderMessage;
import communications.portable.TransmissionCapacitySeries;
import de.dlr.gitlab.fame.agent.Agent;
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
import de.dlr.gitlab.fame.data.TimeSeries;
import de.dlr.gitlab.fame.service.output.Output;
import de.dlr.gitlab.fame.time.Constants.Interval;
import de.dlr.gitlab.fame.time.TimeSpan;
import de.dlr.gitlab.fame.time.TimeStamp;

/** Provides different kind of forecasts for {@link DayAheadMarket}; issues {@link Products#ForecastRequest}s to ask for required
 * bid forecasts; uses forecasted bids to clear market ahead of time and create own forecasts
 * 
 * @author Christoph Schimeczek */
public class MarketForecaster extends Agent implements DamForecastProvider, MarketCouplingClient {
	@Input private static final Tree parameters = Make.newTree().add(Make.newInt("ForecastPeriodInHours"))
			.addAs("Clearing", MarketClearing.parameters).buildTree();

	/** Products of {@link MarketForecaster}s */
	@Product
	public static enum Products {
		/** Send this out to every (start) agent of an {@link DayAheadMarket} bidding chain (e.g. demand and power plant agents) */
		ForecastRequest
	}

	/** Output columns of {@link MarketForecaster} */
	@Output
	protected static enum OutputFields {
		/** Energy Awarded in Forecast in MWh */
		AwardedEnergyForecastInMWH,
		/** Forecasted electricity price in EUR/MWh */
		ElectricityPriceForecastInEURperMWH
	}

	/** Maximum number of hours to the future for which to provide forecasts */
	protected final int forecastPeriodInHours;
	/** The algorithm used to clear the market */
	private final MarketClearing marketClearing;
	/** All previously calculated forecasts (that still lie in the future) with their associated time */
	private final TreeMap<TimeStamp, MarketClearingResult> calculatedForecastContainer = new TreeMap<>();
	/** SupplyOrderBooks for forecasts that are not yet sent to {@link MarketCoupling} */
	private final TreeMap<TimeStamp, SupplyOrderBook> supplyOrderBooks = new TreeMap<>();
	/** DemandOrderBooks for forecasts that are not yet sent to {@link MarketCoupling} */
	private final TreeMap<TimeStamp, DemandOrderBook> demandOrderBooks = new TreeMap<>();
	/** The last time a forecast was stored */
	private TimeStamp lastStoredForecastAt = null;

	private String originMarketZone;
	/** Transmission capacities of forecasted day-ahead market to its connected neighbours */
	private HashMap<String, TimeSeries> transmissionCapacities;

	/** Creates a {@link MarketForecaster}
	 * 
	 * @param dataProvider provides input from config file
	 * @throws MissingDataException if any required data is not provided */
	public MarketForecaster(DataProvider dataProvider) throws MissingDataException {
		super(dataProvider);
		ParameterData input = parameters.join(dataProvider);
		marketClearing = new MarketClearing(input.getGroup("Clearing"));
		forecastPeriodInHours = input.getInteger("ForecastPeriodInHours");

		/** Receive transmission capacities to other markets */
		call(this::receiveTransmissionCapacities).onAndUse(DayAheadMarketMultiZone.Products.TransmissionCapacities);
		/** Send out forecast requests to make other agents prepare their bids ahead of time */
		call(this::sendForecastRequests).on(Products.ForecastRequest).use(DayAheadMarket.Products.GateClosureInfo);
		/** On incoming bid forecasts: prepare for market clearing */
		call(this::digestForecastBids).onAndUse(Trader.Products.BidsForecast);
		/** Send out transmission data and bids for (multiple) market coupling events */
		call(this::sendCouplingData).on(MarketCouplingClient.Products.TransmissionAndBidForecasts);
		/** Digest resolved market couplings and clear market */
		call(this::clearMarketCoupled).onAndUse((MarketCoupling.Products.MarketCouplingForecastResult));
		/** On outgoing merit order forecasts: provide merit order results to clients */
		call(this::sendMeritOrderForecast).on(DamForecastProvider.Products.MeritOrderForecast)
				.use(DamForecastClient.Products.MeritOrderForecastRequest);
		/** On outgoing price forecasts: provide merit order results to clients */
		call(this::sendPriceForecast).on(DamForecastProvider.Products.PriceForecast)
				.use(DamForecastClient.Products.PriceForecastRequest);
	}

	/** Receive transmission capacities to other markets from connected {@link DayAheadMarketMultiZone}
	 * 
	 * @param input single message from connected day-ahead market with transmission capacities to neighbouring zones
	 * @param __ not used */
	private void receiveTransmissionCapacities(ArrayList<Message> input, List<Contract> __) {
		var capacitySeries = CommUtils.getExactlyOneEntry(input)
				.getFirstPortableItemOfType(TransmissionCapacitySeries.class);
		transmissionCapacities = capacitySeries.getCapacities();
		originMarketZone = capacitySeries.getOriginMarketZone();
	}

	/** Requests bid forecast for all future hours within forecast period
	 * 
	 * @param input one GateClosureInfo from forecasted {@link DayAheadMarket}
	 * @param contracts with all agents that start an {@link DayAheadMarket} bidding chain */
	private void sendForecastRequests(ArrayList<Message> input, List<Contract> contracts) {
		ClearingTimes clearingTimes = CommUtils.getExactlyOneEntry(input).getDataItemOfType(ClearingTimes.class);
		List<TimeStamp> missingForecastTimes = findTimesMissing(clearingTimes.getTimes().get(0));
		fulfilForecastRequestContracts(contracts, missingForecastTimes);
		removeOutdatedForecasts();
	}

	/** @return TimeStamps based on the given referenceTime that are within the forecast horizon but not yet have a forecast */
	private List<TimeStamp> findTimesMissing(TimeStamp nextClearingTime) {
		ArrayList<TimeStamp> missingTimes = new ArrayList<>();
		for (int i = 0; i < forecastPeriodInHours; i++) {
			TimeSpan hourOffset = new TimeSpan(i, Interval.HOURS);
			TimeStamp forecastTime = nextClearingTime.laterBy(hourOffset);
			if (!calculatedForecastContainer.containsKey(forecastTime)) {
				missingTimes.add(forecastTime);
			}
		}
		return missingTimes;
	}

	/** Sends out forecast request to all receivers of given contracts */
	private void fulfilForecastRequestContracts(List<Contract> contracts, List<TimeStamp> times) {
		for (Contract contract : contracts) {
			fulfilNext(contract, new ClearingTimes(times.toArray(new TimeStamp[0])));
		}
	}

	/** Removes all out-dated market clearing results */
	private void removeOutdatedForecasts() {
		Iterator<Entry<TimeStamp, MarketClearingResult>> iterator = calculatedForecastContainer.entrySet().iterator();
		while (iterator.hasNext()) {
			Entry<TimeStamp, MarketClearingResult> entry = iterator.next();
			if (entry.getKey().isLessThan(now())) {
				iterator.remove();
			} else {
				break;
			}
		}
	}

	/** Uses received forecasted Bids to either clear the market directly (if no market coupling is used), or store order books for
	 * later interaction with {@link MarketCoupling}
	 * 
	 * @param messages bid forecasts from connected Traders - may belong to multiple TimeStamps
	 * @param __ unused */
	private void digestForecastBids(ArrayList<Message> messages, List<Contract> __) {
		TreeMap<TimeStamp, ArrayList<Message>> messagesByTimeStamp = sortMessagesByBidTimeStamp(messages);
		if (transmissionCapacities == null || transmissionCapacities.isEmpty()) {
			clearMarketUsingSentBidForecasts(messagesByTimeStamp);
		} else {
			cacheOrderBooks(messagesByTimeStamp);
		}
	}

	/** Iterates time stamp at which the bid forecast messages are valid, clears the market for each time stamp, and saves the
	 * clearing result to {@link #calculatedForecastContainer} */
	private void clearMarketUsingSentBidForecasts(TreeMap<TimeStamp, ArrayList<Message>> bidMessagesByTimeStamp) {
		for (Entry<TimeStamp, ArrayList<Message>> entry : bidMessagesByTimeStamp.entrySet()) {
			TimeStamp requestedTime = entry.getKey();
			ArrayList<Message> bidsAtRequestedTime = entry.getValue();
			String clearingId = this + " " + now();
			MarketClearingResult marketClearingResult = marketClearing.clear(bidsAtRequestedTime, clearingId);
			calculatedForecastContainer.put(requestedTime, marketClearingResult);
		}
	}

	/** Iterates time stamp at which the bid forecast messages are valid, assigns them to an order book for demand and supply for
	 * each time stamp, and caches these order books for later use */
	private void cacheOrderBooks(TreeMap<TimeStamp, ArrayList<Message>> bidMessagesByTimeStamp) {
		for (Entry<TimeStamp, ArrayList<Message>> entry : bidMessagesByTimeStamp.entrySet()) {
			TimeStamp requestedTime = entry.getKey();
			ArrayList<Message> bidsAtRequestedTime = entry.getValue();
			SupplyOrderBook supplyBook = new SupplyOrderBook();
			DemandOrderBook demandBook = new DemandOrderBook();
			MarketClearing.fillOrderBooksWithTraderBids(bidsAtRequestedTime, supplyBook, demandBook);
			demandOrderBooks.put(requestedTime, demandBook);
			supplyOrderBooks.put(requestedTime, supplyBook);
		}
	}

	/** Groups given messages by their targeted time of delivery into an ordered Map
	 * 
	 * @param messages to group by
	 * @return a Map of Messages sorted by {@link TimeStamp} of the associated delivery times */
	protected TreeMap<TimeStamp, ArrayList<Message>> sortMessagesByBidTimeStamp(ArrayList<Message> messages) {
		TreeMap<TimeStamp, ArrayList<Message>> messageByTimeStamp = new TreeMap<>();
		for (Message message : messages) {
			TimeStamp deliveryTime = message.getFirstPortableItemOfType(BidsAtTime.class).getDeliveryTime();
			messageByTimeStamp.computeIfAbsent(deliveryTime, __ -> new ArrayList<Message>()).add(message);
		}
		return messageByTimeStamp;
	}

	/** If market coupling is active: Uses previously stored order books to send them to the connected {@link MarketCoupling} agent.
	 * 
	 * @param __ unused
	 * @param contracts single contract with a {@link MarketCoupling} agent */
	private void sendCouplingData(ArrayList<Message> __, List<Contract> contracts) {
		Contract contract = CommUtils.getExactlyOneEntry(contracts);
		for (Entry<TimeStamp, SupplyOrderBook> entry : supplyOrderBooks.entrySet()) {
			TimeStamp requestedTime = entry.getKey();
			TransmissionBook transmissionBook = MarketCouplingClient.buildTransmissionBook(originMarketZone,
					transmissionCapacities, requestedTime);
			fulfilNext(contract,
					new CouplingData(requestedTime, demandOrderBooks.get(requestedTime), entry.getValue(), transmissionBook));
		}
		demandOrderBooks.clear();
		supplyOrderBooks.clear();
	}

	/** If market coupling is active: Uses order books received from {@link MarketCoupling} to clear the market and store the
	 * cleared market for later use.
	 * 
	 * @param messages incoming market coupling data
	 * @param __ not used */
	private void clearMarketCoupled(ArrayList<Message> messages, List<Contract> __) {
		for (Message message : messages) {
			CouplingData coupledData = message.getFirstPortableItemOfType(CouplingData.class);
			TimeStamp clearingTime = coupledData.getClearingTime();
			DemandOrderBook demandBook = coupledData.getDemandOrderBook();
			SupplyOrderBook supplyBook = coupledData.getSupplyOrderBook();
			String clearingId = this + " " + clearingTime;
			MarketClearingResult marketClearingResult = marketClearing.clear(supplyBook, demandBook, clearingId);
			calculatedForecastContainer.put(clearingTime, marketClearingResult);
		}
	}

	/** Sends {@link MeritOrderMessage}s to the requesting trader(s) based on incoming Forecast requests; requesting agent(s) must
	 * also have a MeritOrderForecast contract to get served
	 * 
	 * @param messages incoming forecast request message(s)
	 * @param contracts of partners that desire a MeritOrderForecast */
	private void sendMeritOrderForecast(ArrayList<Message> messages, List<Contract> contracts) {
		for (Contract contract : contracts) {
			ArrayList<Message> requests = CommUtils.extractMessagesFrom(messages, contract.getReceiverId());
			for (Message message : requests) {
				TimeStamp requestedTime = message.getDataItemOfType(PointInTime.class).validAt;
				MarketClearingResult result = getResultForRequestedTime(requestedTime);
				fulfilNext(contract, new MeritOrderMessage(result.getSupplyBook(), result.getDemandBook(), requestedTime));
			}
		}
		saveNextForecast();
	}

	/** Returns stored clearing result for the given time - or throws an Exception if no result is stored
	 * 
	 * @param requestedTime to fetch market clearing result for
	 * @return the result stored for the requested time */
	protected MarketClearingResult getResultForRequestedTime(TimeStamp requestedTime) {
		MarketClearingResult result = calculatedForecastContainer.get(requestedTime);
		if (result == null) {
			throw new RuntimeException("Forecast not available for requested time: " + requestedTime);
		}
		return result;
	}

	/** Writes out the nearest upcoming forecast after current time, if it wasn't already done */
	protected void saveNextForecast() {
		if (now() != lastStoredForecastAt) {
			MarketClearingResult marketClearingResults = calculatedForecastContainer.ceilingEntry(now()).getValue();
			store(OutputFields.ElectricityPriceForecastInEURperMWH, marketClearingResults.getMarketPriceInEURperMWH());
			store(OutputFields.AwardedEnergyForecastInMWH, marketClearingResults.getTradedEnergyInMWH());
			lastStoredForecastAt = now();
		}
	}

	/** Sends {@link AmountAtTime} from {@link MarketClearingResult} to the requesting trader(s) based on incoming Forecast
	 * requests; requesting agent(s) must also have a PriceForecast contract to get served
	 * 
	 * @param messages incoming forecast request message(s)
	 * @param contracts of partners that desire a PriceForecast */
	private void sendPriceForecast(ArrayList<Message> messages, List<Contract> contracts) {
		for (Contract contract : contracts) {
			ArrayList<Message> requests = CommUtils.extractMessagesFrom(messages, contract.getReceiverId());
			for (Message message : requests) {
				TimeStamp requestedTime = message.getDataItemOfType(PointInTime.class).validAt;
				MarketClearingResult result = getResultForRequestedTime(requestedTime);
				double forecastedPriceInEURperMWH = result.getMarketPriceInEURperMWH();
				fulfilNext(contract, new AmountAtTime(requestedTime, forecastedPriceInEURperMWH));
			}
		}
		saveNextForecast();
	}
}
