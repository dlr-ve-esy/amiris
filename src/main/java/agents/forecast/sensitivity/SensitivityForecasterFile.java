// SPDX-FileCopyrightText: 2026 German Aerospace Center <amiris@dlr.de>
//
// SPDX-License-Identifier: Apache-2.0
package agents.forecast.sensitivity;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;
import communications.message.ForecastClientRegistration;
import communications.message.PointInTime;
import communications.portable.Sensitivity;
import communications.portable.Sensitivity.InterpolationType;
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
	private final TreeMap<TimeStamp, Sensitivity> nextForecasts = new TreeMap<>();

	/** Creates new {@link SensitivityForecasterFile}
	 * 
	 * @param dataProvider holds input for this type of Forecaster
	 * @throws MissingDataException in case mandatory input is missing */
	public SensitivityForecasterFile(DataProvider dataProvider) throws MissingDataException {
		super(dataProvider);
		ParameterData input = parameters.join(dataProvider);
		priceForecasts = input.getTimeSeries("PriceForecastsInEURperMWH");

		call(this::registerClients).onAndUse(SensitivityForecastClient.Products.ForecastRegistration);
		call(this::sendSensitivityForecast).on(Products.SensitivityForecast)
				.use(SensitivityForecastClient.Products.SensitivityRequest);
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
		nextForecasts.headMap(now()).clear();
		for (Contract contract : contracts) {
			ArrayList<Message> requests = CommUtils.extractMessagesFrom(messages, contract.getReceiverId());
			for (Message message : requests) {
				TimeStamp time = message.getDataItemOfType(PointInTime.class).validAt;
				Sensitivity sensitivity = getCostInsensitiveSensitivity(time);
				fulfilNext(contract, sensitivity, new PointInTime(time));
			}
		}
		Sensitivity nextSensitivity = nextForecasts.firstEntry().getValue();
		nextSensitivity.setInterpolationType(InterpolationType.DIRECT);
		double nextPriceInEURperMWH = nextSensitivity.getValue(0);
		store(OutputFields.ElectricityPriceForecastInEURperMWH, nextPriceInEURperMWH);
	}

	/** @return {@link Sensitivity} - either stored in map or freshly calculated */
	private Sensitivity getCostInsensitiveSensitivity(TimeStamp time) {
		nextForecasts.computeIfAbsent(time, t -> buildSensitivityFor(t));
		return nextForecasts.get(time);
	}

	/** @return freshly built {@link Sensitivity} of type {@link CostInsensitive} with multiplier Zero */
	private Sensitivity buildSensitivityFor(TimeStamp time) {
		CostInsensitive costInsensitive = new CostInsensitive();
		costInsensitive.setPrice(priceForecasts.getValueLinear(time));
		return new Sensitivity(costInsensitive, 0.);
	}
}
