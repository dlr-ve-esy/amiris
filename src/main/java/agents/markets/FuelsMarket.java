// SPDX-FileCopyrightText: 2026 German Aerospace Center <amiris@dlr.de>
//
// SPDX-License-Identifier: Apache-2.0
package agents.markets;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import communications.message.AmountAtTime;
import communications.message.ClearingTimes;
import communications.message.FuelBid;
import communications.message.FuelBid.BidType;
import communications.message.FuelCost;
import communications.message.FuelData;
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
import de.dlr.gitlab.fame.logging.Logging;
import de.dlr.gitlab.fame.time.TimeStamp;

/** Determines market prices for all conventional power plant fuels
 * 
 * @author Christoph Schimeczek, Marc Deissenroth */
public class FuelsMarket extends Agent {
	static final String TIME_SERIES_MISSING = "No TimeSeries found for fuel ";
	static final String CONVERSION_FACTOR_MISSING = "No conversion factor found for fuel ";

	/** Products of the {@link FuelsMarket} */
	@Product
	public static enum Products {
		/** forecasted value of a fuel price */
		FuelPriceForecast,
		/** actual fuel price */
		FuelPrice,
		/** cost of purchased fuel amounts - negative for revenues from fuel offers */
		FuelBill,
	};

	@Input private static final Tree parameters = Make.newTree().add(Make.newGroup("FuelPrices").list()
			.add(FuelsTrader.fuelTypeParameter, Make.newSeries("Price"), Make.newDouble("ConversionFactor")))
			.buildTree();

	private final HashMap<String, TimeSeries> fuelPrices = new HashMap<>();
	private final HashMap<String, Double> conversionFactors = new HashMap<>();

	/** Creates a {@link FuelsMarket}
	 * 
	 * @param dataProvider provides input from config
	 * @throws MissingDataException if any required data is not provided */
	public FuelsMarket(DataProvider dataProvider) throws MissingDataException {
		super(dataProvider);
		ParameterData input = parameters.join(dataProvider);
		loadFuelPrices(input.getGroupList("FuelPrices"));

		call(this::sendPrices).on(Products.FuelPriceForecast).use(FuelsTrader.Products.FuelPriceForecastRequest);
		call(this::sendPrices).on(Products.FuelPrice).use(FuelsTrader.Products.FuelPriceRequest);
		call(this::sendBill).on(Products.FuelBill).use(FuelsTrader.Products.FuelBid);
	}

	/** Loads fuel prices specified as list elements, each with (FuelType, Price) items
	 * 
	 * @param groupList list of parameter data groups
	 * @throws MissingDataException if any group element misses either FuelType or Price */
	private void loadFuelPrices(List<ParameterData> groupList) throws MissingDataException {
		for (ParameterData group : groupList) {
			String fuelType = FuelsTrader.readFuelType(group);
			fuelPrices.put(fuelType, group.getTimeSeries("Price"));
			conversionFactors.put(fuelType, group.getDouble("ConversionFactor"));
		}
	}

	/** Sends prices matching requested {@link FuelType} and {@link ClearingTimes} to all {@link Contract}ed receivers
	 * 
	 * @param input FuelPriceRequests that specify {@link FuelType} and {@link ClearingTimes}
	 * @param contracts to return prices to - each receiver must issue a request first */
	private void sendPrices(ArrayList<Message> input, List<Contract> contracts) {
		for (Contract contract : contracts) {
			ArrayList<Message> connectedMessages = CommUtils.extractMessagesFrom(input, contract.getReceiverId());
			for (Message message : connectedMessages) {
				String fuelType = message.getDataItemOfType(FuelData.class).fuelType;
				List<TimeStamp> targetTimes = message.getDataItemOfType(ClearingTimes.class).getTimes();
				for (TimeStamp targetTime : targetTimes) {
					sendFuelPriceFor(targetTime, fuelType, contract);
				}
			}
		}
	}

	/** Sends price in EUR per thermal MWh for requested FuelType at requested time to given contract r
	 * 
	 * @param time of the forecast
	 * @param fuelType for which to return forecast
	 * @param contract with receiver of the FuelCost forecast */
	private void sendFuelPriceFor(TimeStamp time, String fuelType, Contract contract) {
		double fuelPriceInEURperThermalMWH = getFuelPrice(fuelType, time) * getConversionFactor(fuelType);
		FuelCost fuelPriceItem = new FuelCost(time, fuelPriceInEURperThermalMWH);
		fulfilNext(contract, fuelPriceItem);
	}

	/** Returns fuel price at specified time for specified fuel
	 * 
	 * @param fuel type of fuel to match
	 * @param time to match
	 * @return price for given FuelType at specified TimeStamp
	 * @throws RuntimeException if no TimeSeries is registered for given fuelType */
	private double getFuelPrice(String fuel, TimeStamp time) {
		TimeSeries fuelPriceOverTime = fuelPrices.get(fuel);
		if (fuelPriceOverTime == null) {
			throw Logging.logFatalException(logger, TIME_SERIES_MISSING + fuel);
		}
		return fuelPriceOverTime.getValueLinear(time);
	}

	/** Returns the conversion factor for given fuel type
	 * 
	 * @param fuel type of fuel to match
	 * @return conversion factor for given FuelType
	 * @throws RuntimeException if no conversion factor is registered for given fuelType */
	private double getConversionFactor(String fuel) {
		if (conversionFactors.containsKey(fuel)) {
			return conversionFactors.get(fuel);
		} else {
			throw Logging.logFatalException(logger, CONVERSION_FACTOR_MISSING + fuel);
		}
	}

	/** Sends bill to contracted partners for their ordered & offered fuel(s)
	 * 
	 * @param input messages that specify amount of consumed fuel(s)
	 * @param contracts to return invoice to - each receiver should issue request(s) first */
	private void sendBill(ArrayList<Message> input, List<Contract> contracts) {
		for (Contract contract : contracts) {
			ArrayList<Message> connectedMessages = CommUtils.extractMessagesFrom(input, contract.getReceiverId());
			double fuelCostTotal = 0;
			for (Message message : connectedMessages) {
				FuelBid fuelBid = message.getDataItemOfType(FuelBid.class);
				double fuelPrice = getFuelPrice(fuelBid.fuelType, fuelBid.validAt);
				double fuelValueInEUR = fuelPrice * fuelBid.amount;
				fuelCostTotal += (fuelBid.bidType == BidType.Demand ? 1 : -1) * fuelValueInEUR;
			}
			AmountAtTime bill = new AmountAtTime(now(), fuelCostTotal);
			fulfilNext(contract, bill);
		}
	}
}