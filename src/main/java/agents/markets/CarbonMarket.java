// SPDX-FileCopyrightText: 2026 German Aerospace Center <amiris@dlr.de>
//
// SPDX-License-Identifier: Apache-2.0
package agents.markets;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import agents.plantOperator.ConventionalPlantOperator;
import communications.message.AmountAtTime;
import communications.message.ClearingTimes;
import communications.message.Co2Cost;
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
import de.dlr.gitlab.fame.service.output.Output;
import de.dlr.gitlab.fame.time.TimeStamp;

/** CO2 market place that sells CO2 certificates and accounts for total sold CO2 emission rights. Determines CO2 prices.
 * 
 * @author Christoph Schimeczek */
public class CarbonMarket extends Agent {
	static final String MODE_NOT_IMPLEMENTED = "OperationMode not implemented: ";

	/** Products of the {@link CarbonMarket} */
	@Product
	public static enum Products {
		/** Co2 price forecast */
		Co2PriceForecast,
		/** Co2 price */
		Co2Price,
		/** costs for ordered CO2-certificates */
		CertificateBill
	}

	/** Mode of operation of {@link CarbonMarket} */
	public static enum OperationMode {
		/** Fixed mode:: CO2 prices are read from file - actual emissions create no feedback on the CO2 price */
		FIXED,
		/** Dynamic mode:: CO2 prices are determined based on the sum of CO2 emissions and a CO2 cap read from file */
		DYNAMIC
	};

	@Output
	private static enum OutputFields {
		Co2EmissionsInTons, Co2PriceInEURperTon
	};

	/** Input parameters of {@link CarbonMarket} */
	@Input protected static Tree parameters = Make.newTree().add(Make.newSeries("Co2Prices").optional(),
			Make.newEnum("OperationMode", OperationMode.class)).buildTree();

	private OperationMode operationMode;
	private TimeSeries tsCo2Prices;
	private HashMap<Long, Double> billSubtotals = new HashMap<>();

	/** Creates a {@link CarbonMarket} agent
	 * 
	 * @param dataProvider with data from configuration
	 * @throws MissingDataException if any parameter is missing */
	public CarbonMarket(DataProvider dataProvider) throws MissingDataException {
		super(dataProvider);
		ParameterData data = parameters.join(dataProvider);
		operationMode = data.getEnum("OperationMode", OperationMode.class);
		loadOperationModeParameters(data);
		call(this::sendPrice).on(Products.Co2PriceForecast).use(ConventionalPlantOperator.Products.Co2PriceForecastRequest);
		call(this::sendPrice).on(Products.Co2Price).use(ConventionalPlantOperator.Products.Co2PriceRequest);
		call(this::registerCertificateOrders).onAndUse(ConventionalPlantOperator.Products.Co2Emissions);
		call(this::sendBill).on(Products.CertificateBill);
	}

	/** Loads {@link InputParameters parameters} according to {@link OperationMode}
	 * 
	 * @param data to contain Co2-prices from configuration
	 * @throws MissingDataException if matching data are not provided
	 * @throws RuntimeException if operationMode is not implemented */
	private void loadOperationModeParameters(ParameterData data) throws MissingDataException {
		switch (operationMode) {
			case FIXED:
				tsCo2Prices = data.getTimeSeries("Co2Prices");
				break;
			default:
				throw Logging.logFatalException(logger, MODE_NOT_IMPLEMENTED + operationMode);
		}
	}

	/** Sends Co2-certificate price matching requested {@link ClearingTimes} to contract partners
	 * 
	 * @param input {@link ClearingTimes} specifying requested times
	 * @param contracts with partners to send the certificate price(s) */
	private void sendPrice(ArrayList<Message> input, List<Contract> contracts) {
		for (Contract contract : contracts) {
			ArrayList<Message> connectedMessages = CommUtils.extractMessagesFrom(input, contract.getReceiverId());
			for (Message message : connectedMessages) {
				List<TimeStamp> targetTimes = message.getDataItemOfType(ClearingTimes.class).getTimes();
				for (TimeStamp targetTime : targetTimes) {
					sendCo2PricesFor(targetTime, contract);
				}
			}
		}
	}

	/** Returns CO2 price at given time from a given time series
	 * 
	 * @param time at which to obtain the price for
	 * @return Co2Price at the given time */
	private double getCo2Price(TimeStamp time) {
		return tsCo2Prices.getValueEarlierEqual(time);
	}

	/** Calculates the Co2 price at the specified {@link TimeStamp} and sends it to the receiver of the given {@link Contract}
	 * 
	 * @param time at which the price is valid
	 * @param contract with partner to receive the price data */
	private void sendCo2PricesFor(TimeStamp time, Contract contract) {
		Co2Cost priceDataItem = new Co2Cost(time, getCo2Price(time));
		fulfilNext(contract, priceDataItem);
	}

	/** Reads emission certificate orders and saves associated cost per reporting agent
	 * 
	 * @param input incoming certificate orders
	 * @param contracts not used */
	private void registerCertificateOrders(ArrayList<Message> input, List<Contract> contracts) {
		double emissionTotal = 0;
		for (Message message : input) {
			AmountAtTime certificateOrder = message.getDataItemOfType(AmountAtTime.class);
			emissionTotal += certificateOrder.amount;

			double subTotal = billSubtotals.getOrDefault(message.senderId, 0.);
			subTotal += certificateOrder.amount * getCo2Price(certificateOrder.validAt);
			billSubtotals.put(message.senderId, subTotal);
		}
		store(OutputFields.Co2EmissionsInTons, emissionTotal);
	}

	/** Bills contracted partners for the sum of their previously ordered Co2 certificates; resets billed partner's stored cost
	 * totals
	 * 
	 * @param input not used
	 * @param contracts with partners to send certificate costs to */
	private void sendBill(ArrayList<Message> input, List<Contract> contracts) {
		for (Contract contract : contracts) {
			long receiver = contract.getReceiverId();
			double billingValue = billSubtotals.getOrDefault(receiver, 0.);
			fulfilNext(contract, new AmountAtTime(now(), billingValue));
			billSubtotals.put(receiver, 0.);
		}
	}
}