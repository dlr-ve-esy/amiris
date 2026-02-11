// SPDX-FileCopyrightText: 2026 German Aerospace Center <amiris@dlr.de>
//
// SPDX-License-Identifier: Apache-2.0
package agents.forecast.sensitivity;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;
import agents.markets.DayAheadMarket;
import communications.message.ClearingTimes;
import communications.message.ForecastClientRegistration;
import communications.message.PointInTime;
import communications.portable.Sensitivity;
import de.dlr.gitlab.fame.agent.Agent;
import de.dlr.gitlab.fame.agent.input.DataProvider;
import de.dlr.gitlab.fame.agent.input.Input;
import de.dlr.gitlab.fame.agent.input.Make;
import de.dlr.gitlab.fame.agent.input.ParameterData;
import de.dlr.gitlab.fame.agent.input.ParameterData.MissingDataException;
import de.dlr.gitlab.fame.agent.input.Tree;
import de.dlr.gitlab.fame.communication.CommUtils;
import de.dlr.gitlab.fame.communication.Contract;
import de.dlr.gitlab.fame.communication.message.Message;
import de.dlr.gitlab.fame.data.TimeSeries;
import de.dlr.gitlab.fame.service.output.Output;
import de.dlr.gitlab.fame.time.TimeStamp;

/** Provides electricity price forecasts read from file but in the sensitivity format.
 * 
 * @author Christoph Schimeczek */
public class SensitivityForecasterFile extends Agent implements SensitivityForecastProvider {
	static final String ERR_INCOMPATIBLE = "Requested type of forecast by Client '%d' is incompatible. SensitivityForecasterFile can only send: '%s'";

	@Input private static final Tree parameters = Make.newTree()
			.add(Make.newSeries("PriceForecastsInEURperMWH").help("Time series of price forecasts")).buildTree();

	@Output
	private static enum OutputFields {
		ElectricityPriceForecastInEURperMWH
	}

	private final TimeSeries priceForecasts;
	private final TreeMap<TimeStamp, Sensitivity> cachedForecasts = new TreeMap<>();

	/** Creates new {@link SensitivityForecasterFile}
	 * 
	 * @param dataProvider holds input for this type of Forecaster
	 * @throws MissingDataException in case mandatory input is missing */
	public SensitivityForecasterFile(DataProvider dataProvider) throws MissingDataException {
		super(dataProvider);
		ParameterData input = parameters.join(dataProvider);
		priceForecasts = input.getTimeSeries("PriceForecastsInEURperMWH");

		call(this::writeForecast).onAndUse(DayAheadMarket.Products.GateClosureInfo);
		call(this::registerClients).onAndUse(SensitivityForecastClient.Products.ForecastRegistration);
		call(this::sendSensitivityForecast).on(Products.SensitivityForecast)
				.use(SensitivityForecastClient.Products.SensitivityRequest);
	}

	/** Write out electricity price forecast at next clearing time */
	private void writeForecast(ArrayList<Message> input, List<Contract> contracts) {
		ClearingTimes clearingTimes = CommUtils.getExactlyOneEntry(input).getDataItemOfType(ClearingTimes.class);
		TimeStamp nextClearingTime = clearingTimes.getTimes().get(0);
		store(OutputFields.ElectricityPriceForecastInEURperMWH, priceForecasts.getValueLinear(nextClearingTime));
	}

	/** Check registration message of clients that they required the correct type of {@link ForecastType} */
	private void registerClients(ArrayList<Message> input, List<Contract> contracts) {
		for (Message message : input) {
			var registration = message.getDataItemOfType(ForecastClientRegistration.class);
			if (registration.type != ForecastType.CostInsensitive) {
				long clientId = message.getSenderId();
				throw new RuntimeException(String.format(ERR_INCOMPATIBLE, clientId, ForecastType.CostInsensitive));
			}
		}
	}

	/** Send sensitivity forecast to clients for all times they requested */
	private void sendSensitivityForecast(ArrayList<Message> messages, List<Contract> contracts) {
		cachedForecasts.headMap(now()).clear();
		for (Contract contract : contracts) {
			ArrayList<Message> requests = CommUtils.extractMessagesFrom(messages, contract.getReceiverId());
			for (Message message : requests) {
				TimeStamp requestedTime = message.getDataItemOfType(PointInTime.class).validAt;
				Sensitivity sensitivity = getCostInsensitiveSensitivity(requestedTime);
				fulfilNext(contract, sensitivity, new PointInTime(requestedTime));
			}
		}
	}

	/** @return {@link Sensitivity} - either stored in map or freshly calculated */
	private Sensitivity getCostInsensitiveSensitivity(TimeStamp time) {
		cachedForecasts.computeIfAbsent(time, t -> buildSensitivityFor(t));
		return cachedForecasts.get(time);
	}

	/** @return freshly built {@link Sensitivity} of type {@link CostInsensitive} with multiplier 1.0 */
	private Sensitivity buildSensitivityFor(TimeStamp time) {
		CostInsensitive costInsensitive = new CostInsensitive();
		costInsensitive.setPrice(priceForecasts.getValueLinear(time));
		return new Sensitivity(costInsensitive, 1.0);
	}
}
