// SPDX-FileCopyrightText: 2026 German Aerospace Center <amiris@dlr.de>
//
// SPDX-License-Identifier: Apache-2.0
package agents.markets;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import agents.markets.meritOrder.MarketClearingResult;
import agents.markets.meritOrder.books.DemandOrderBook;
import agents.markets.meritOrder.books.SupplyOrderBook;
import agents.markets.meritOrder.books.TransferOrderBook;
import agents.markets.meritOrder.books.TransmissionBook;
import communications.message.AwardData;
import communications.message.TransmissionCapacity;
import communications.portable.CouplingData;
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
import de.dlr.gitlab.fame.logging.Logging;
import de.dlr.gitlab.fame.service.output.Output;
import de.dlr.gitlab.fame.time.TimeStamp;

/** Energy exchange performs local market clearing for day-ahead energy market.
 * 
 * @author Christoph Schimeczek, A. Achraf El Ghazi, Felix Nitsch, Johannes Kochems */
public class DayAheadMarketMultiZone extends DayAheadMarket {
	static final String MARKET_ZONE_MISSING = "Each Transmission requires a connected market zone.";
	static final String TIME_SERIES_MISSING = "No transmission capacity specified for market zone: ";
	static final String ERR_CLEARING_FAILED = ": Market clearing failed due to: ";

	/** Products of {@link DayAheadMarketMultiZone}s */
	@Product
	public static enum Products {
		/** Transmission capacities and bids from local exchange */
		TransmissionAndBids,
	};

	@Output
	private static enum OutputFields {
		/** Electricity price that would occur without any market coupling in EUR/MWh */
		PreCouplingElectricityPriceInEURperMWH,
		/** Awarded power without any market coupling in MW */
		PreCouplingTotalAwardedPowerInMW,
		/** System cost without any market coupling in EUR */
		PreCouplingDispatchSystemCostInEUR,
		/** Net energy awarded to exports */
		AwardedNetEnergyToExportInMWH,
		/** Net energy awarded from imports */
		AwardedNetEnergyFromImportInMWH
	};

	@Input private static final Tree parameters = Make.newTree()
			.add(
					Make.newStringSet("MarketZone").optional()
							.help("Identifier specifying the market zone this DayAheadMarket is representing"),
					Make.newGroup("Transmission").list().optional()
							.add(Make.newStringSet("MarketZone"),
									Make.newSeries("CapacityInMW")
											.help("Net transfer capacity of supply from own to connected market zone.")))
			.buildTree();

	/** Market region of this energy exchange instance */
	private final String ownMarketZone;
	private DemandOrderBook demandBook = new DemandOrderBook();
	private SupplyOrderBook supplyBook = new SupplyOrderBook();
	private final HashMap<String, TimeSeries> transmissionCapacities = new HashMap<>();
	private TransferOrderBook importBook = new TransferOrderBook();
	private TransferOrderBook exportBook = new TransferOrderBook();

	/** Holds net export / import to other regions */
	private class NetEnergyTransfer {
		public double netImportsInMWH;
		public double netExportsInMWH;

		public NetEnergyTransfer(double netImportsInMWH, double netExportsInMWH) {
			this.netImportsInMWH = netImportsInMWH;
			this.netExportsInMWH = netExportsInMWH;
		}
	}

	/** Creates an {@link DayAheadMarketMultiZone}
	 * 
	 * @param dataProvider provides input from config
	 * @throws MissingDataException if any required data is not provided */
	public DayAheadMarketMultiZone(DataProvider dataProvider) throws MissingDataException {
		super(dataProvider);
		ParameterData input = parameters.join(dataProvider);
		ownMarketZone = input.getStringOrDefault("MarketZone", null);
		if (ownMarketZone != null) {
			loadTransmissionCapacities(input.getGroupList("Transmission"));
		}

		call(this::digestBids).onAndUse(DayAheadMarketTrader.Products.Bids);
		call(this::provideTransmissionAndBids).on(Products.TransmissionAndBids);
		call(this::clearMarket).on(DayAheadMarket.Products.Awards).use(MarketCoupling.Products.MarketCouplingResult);
	}

	/** Loads all transmission capacity time-series and stores them with the corresponding target market zones as key
	 * 
	 * @param transmissions list of all available transmission time-series with the current market as origin of supply */
	private void loadTransmissionCapacities(List<ParameterData> transmissions) {
		for (ParameterData dataItem : transmissions) {
			String targetRegion = null;
			try {
				targetRegion = dataItem.getString("MarketZone");
			} catch (MissingDataException e) {
				logger.error(MARKET_ZONE_MISSING);
			}
			try {
				transmissionCapacities.put(targetRegion, dataItem.getTimeSeries("CapacityInMW"));
			} catch (MissingDataException e) {
				logger.error(TIME_SERIES_MISSING + targetRegion);
			}
		}
	}

	/** Collects the received trader bids in {@link EnergyExchange #demandBook} and {@link EnergyExchange #supplyBook}, according to
	 * their type.
	 * 
	 * @param input messages specifying the trader bids
	 * @param contracts with the Trader Agent's */
	private void digestBids(ArrayList<Message> input, List<Contract> contracts) {
		marketClearing.fillOrderBooksWithTraderBids(input, supplyBook, demandBook);
	}

	/** Builds a CouplingRequest and sends it to the contracted MarketCoupling Agent. The CouplingRequest contains: the local
	 * DemandOrderBook, the local SupplyOrderBook, and the TransmissionCapacity's from the Region of this EnergyExchange to all
	 * EnergyExchange's that are coupled with it.
	 * 
	 * @param input not-used, not-expected
	 * @param contracts with the MarketCoupling Agent */
	private void provideTransmissionAndBids(ArrayList<Message> input, List<Contract> contracts) {
		Contract contract = CommUtils.getExactlyOneEntry(contracts);

		TransmissionBook transmissionBook = new TransmissionBook(ownMarketZone);
		for (String targetRegion : transmissionCapacities.keySet()) {
			transmissionBook.add(getTransmissionCapacity(targetRegion, now()));
		}
		MarketClearingResult result = marketClearing.clear(supplyBook.clone(), demandBook.clone(), getClearingEventId());
		store(OutputFields.PreCouplingElectricityPriceInEURperMWH, result.getMarketPriceInEURperMWH());
		store(OutputFields.PreCouplingTotalAwardedPowerInMW, result.getTradedEnergyInMWH());
		store(OutputFields.PreCouplingDispatchSystemCostInEUR, result.getSystemCostTotalInEUR());

		fulfilNext(contract, new CouplingData(demandBook, supplyBook, transmissionBook));
	}

	/** Returns the TransmissionCapacity for a given target Region and a given TimeStamp
	 * 
	 * @param targetRegion given target Region
	 * @param time given TimeStamp
	 * @return TransmissionCapacity for a given target Region and TimeStamp */
	private TransmissionCapacity getTransmissionCapacity(String targetRegion, TimeStamp time) {
		double amount = getTransmissionCapacityAmount(targetRegion, time);
		TransmissionCapacity transmissionCapacity = new TransmissionCapacity(targetRegion, amount);
		return transmissionCapacity;
	}

	/** Returns the TransmissionCapacityAmount for a given target Region and a given TimeStamp
	 * 
	 * @param targetRegion given target Region
	 * @param time given TimeStamp
	 * @return transmission capacity amount for a given target Region and TimeStamp */
	private double getTransmissionCapacityAmount(String targetRegion, TimeStamp time) {
		TimeSeries transmissionCapacityOverTime = transmissionCapacities.get(targetRegion);
		if (transmissionCapacityOverTime == null) {
			throw Logging.logFatalException(logger, TIME_SERIES_MISSING + targetRegion);
		}
		return transmissionCapacityOverTime.getValueEarlierEqual(time);
	}

	/** Clears the local market and sends the Awards to the contracted Trader Agents. Depending on whether this EnergyExchange is
	 * involved in a coupled super-market (via contract to a MarketCoupling Agent) or not, it used the local or the coupled
	 * DemandOrderBook and SupplyOrderBook.
	 * 
	 * @param input one message from the MarketCoupling Agent containing the result of the market coupling
	 * @param contracts with the Trader Agents */
	private void clearMarket(ArrayList<Message> input, List<Contract> contracts) {
		if (input.size() > 0) {
			Message message = CommUtils.getExactlyOneEntry(input);
			CouplingData coupledData = message.getFirstPortableItemOfType(CouplingData.class);
			demandBook = coupledData.getDemandOrderBook();
			supplyBook = coupledData.getSupplyOrderBook();
			importBook = coupledData.getImportOrderBook();
			exportBook = coupledData.getExportOrderBook();
		} else {
			importBook = new TransferOrderBook();
			exportBook = new TransferOrderBook();
		}
		MarketClearingResult marketClearingResult = marketClearing.clear(supplyBook, demandBook, getClearingEventId());
		NetEnergyTransfer energyTransfer = computeNetEngergyTransfer(importBook, exportBook);

		store(DayAheadMarket.OutputFields.ElectricityPriceInEURperMWH, marketClearingResult.getMarketPriceInEURperMWH());
		store(DayAheadMarket.OutputFields.AwardedEnergyInMWH, marketClearingResult.getTradedEnergyInMWH());
		store(DayAheadMarket.OutputFields.DispatchSystemCostInEUR, marketClearingResult.getSystemCostTotalInEUR());

		store(OutputFields.AwardedNetEnergyFromImportInMWH, energyTransfer.netImportsInMWH);
		store(OutputFields.AwardedNetEnergyToExportInMWH, energyTransfer.netExportsInMWH);

		sendAwardsToTraders(contracts, marketClearingResult, importBook, exportBook);
	}

	/** Computes the net accumulated export and import of energy according to the given import and export TransferOrderBook. Thereby
	 * the accumulated export and import are offset together. So only one of their net results is different that zero.
	 * 
	 * @param importBook import TransferOrderBook
	 * @param exportBook export TransferOrderBook
	 * @return a EnergyTransfer of the result */
	private NetEnergyTransfer computeNetEngergyTransfer(TransferOrderBook importBook, TransferOrderBook exportBook) {
		double netImportPower = importBook.getAccumulatedEnergyInMWH();
		double netExportPower = exportBook.getAccumulatedEnergyInMWH();
		if (netImportPower > netExportPower) {
			return new NetEnergyTransfer(netImportPower - netExportPower, 0.0);
		}
		return new NetEnergyTransfer(0.0, netExportPower - netImportPower);
	}

	/** Computes and sends the Awards for each contracted Trader Agent.
	 *
	 * @param contracts with the contracted Trader Agents
	 * @param result the result of the market clearing process
	 * @param importBook imports from other markets
	 * @param exportBook exports to other markets */
	private void sendAwardsToTraders(List<Contract> contracts, MarketClearingResult result, TransferOrderBook importBook,
			TransferOrderBook exportBook) {
		double powerPrice = result.getMarketPriceInEURperMWH();
		for (Contract contract : contracts) {
			long receiverId = contract.getReceiverId();

			double energyFromImports = importBook.getEnergySumForTrader(receiverId);
			double supplyPower = result.getSupplyBook().getTradersSumOfPower(receiverId);
			double demandPower = result.getDemandBook().getTradersSumOfPower(receiverId) + energyFromImports;

			List<TimeStamp> clearingTimeList = clearingTimes.getTimes();
			if (clearingTimeList.size() > 1) {
				throw new RuntimeException(LONE_LIST + clearingTimeList);
			}
			for (TimeStamp clearingTime : clearingTimeList) {
				AwardData awardData = new AwardData(supplyPower, demandPower, powerPrice, clearingTime);
				fulfilNext(contract, awardData);
			}
		}
	}
}
