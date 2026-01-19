// SPDX-FileCopyrightText: 2026 German Aerospace Center <amiris@dlr.de>
//
// SPDX-License-Identifier: Apache-2.0
package agents.forecast;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;
import agents.markets.meritOrder.MarketClearingResult;
import communications.message.AmountAtTime;
import communications.message.PointInTime;
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

/** Provides static electricity price forecasts read from file
 * 
 * @author Christoph Schimeczek */
public class PriceForecasterFile extends Agent implements DamForecastProvider {
	@Input private static final Tree parameters = Make.newTree()
			.add(Make.newSeries("PriceForecastsInEURperMWH").help("Time series of price forecasts")).buildTree();

	@Output
	private static enum OutputFields {
		ElectricityPriceForecastInEURperMWH
	}

	private final TimeSeries priceForecasts;
	private final TreeMap<TimeStamp, Double> nextForecasts = new TreeMap<>();

	/** Creates new {@link PriceForecasterFile}
	 * 
	 * @param dataProvider holding input for this type of Forecaster
	 * @throws MissingDataException in case mandatory input is missing */
	public PriceForecasterFile(DataProvider dataProvider) throws MissingDataException {
		super(dataProvider);
		ParameterData input = parameters.join(dataProvider);
		priceForecasts = input.getTimeSeries("PriceForecastsInEURperMWH");

		call(this::sendPriceForecast).on(DamForecastProvider.Products.PriceForecast)
				.use(DamForecastClient.Products.PriceForecastRequest);
	}

	/** sends {@link AmountAtTime} from {@link MarketClearingResult} to the requesting trader */
	private void sendPriceForecast(ArrayList<Message> messages, List<Contract> contracts) {
		nextForecasts.headMap(now()).clear();
		for (Contract contract : contracts) {
			ArrayList<Message> requests = CommUtils.extractMessagesFrom(messages, contract.getReceiverId());
			for (Message message : requests) {
				TimeStamp requestedTime = message.getDataItemOfType(PointInTime.class).validAt;
				nextForecasts.computeIfAbsent(requestedTime, __ -> priceForecasts.getValueLinear(requestedTime));
				AmountAtTime priceForecastMessage = new AmountAtTime(requestedTime, nextForecasts.get(requestedTime));
				fulfilNext(contract, priceForecastMessage);
			}
		}
		store(OutputFields.ElectricityPriceForecastInEURperMWH, nextForecasts.firstEntry().getValue());
	}
}
