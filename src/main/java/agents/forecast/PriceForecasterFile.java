// SPDX-FileCopyrightText: 2024 German Aerospace Center <amiris@dlr.de>
package agents.forecast;

import java.util.ArrayList;
import java.util.List;
import agents.markets.meritOrder.MarketClearingResult;
import agents.trader.Trader;
import communications.message.AmountAtTime;
import communications.message.PointInTime;
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
public class PriceForecasterFile extends Forecaster {
	@Input private static final Tree parameters = Make.newTree()
			.add(Make.newSeries("PriceForecastsInEURperMWH").help("Time series of price forecasts")).buildTree();

	@Output
	private static enum OutputFields {
		ElectricityPriceForecastInEURperMWH
	};

	protected final TimeSeries priceForecasts;

	public PriceForecasterFile(DataProvider dataProvider) throws MissingDataException {
		super(dataProvider);
		ParameterData input = parameters.join(dataProvider);
		priceForecasts = input.getTimeSeries("PriceForecastsInEURperMWH");

		call(this::sendPriceForecast).on(Forecaster.Products.PriceForecast).use(Trader.Products.PriceForecastRequest);
	}

	/** sends {@link AmountAtTime} from {@link MarketClearingResult} to the requesting trader */
	private void sendPriceForecast(ArrayList<Message> messages, List<Contract> contracts) {
		for (Contract contract : contracts) {
			ArrayList<Message> requests = CommUtils.extractMessagesFrom(messages, contract.getReceiverId());
			for (Message message : requests) {
				TimeStamp requestedTime = message.getDataItemOfType(PointInTime.class).timeStamp;
				double forecastedPriceInEURperMWH = priceForecasts.getValueLinear(requestedTime);
				AmountAtTime priceForecastMessage = new AmountAtTime(requestedTime, forecastedPriceInEURperMWH);
				fulfilNext(contract, priceForecastMessage);
			}
		}
		store(OutputFields.ElectricityPriceForecastInEURperMWH, priceForecasts.getValueLinear(now()));
	}
}
