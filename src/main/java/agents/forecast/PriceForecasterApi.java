// SPDX-FileCopyrightText: 2026 German Aerospace Center <amiris@dlr.de>
//
// SPDX-License-Identifier: Apache-2.0
package agents.forecast;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.TreeMap;
import agents.forecast.forecastApi.ForecastApiRequest;
import agents.forecast.forecastApi.ForecastApiResponse;
import agents.forecast.sensitivity.CostInsensitive;
import agents.forecast.sensitivity.SensitivityForecastClient;
import agents.forecast.sensitivity.SensitivityForecastProvider;
import agents.markets.DayAheadMarket;
import agents.markets.meritOrder.MarketClearingResult;
import communications.message.AmountAtTime;
import communications.message.AwardData;
import communications.message.ClearingTimes;
import communications.message.ForecastClientRegistration;
import communications.message.PointInTime;
import communications.portable.Sensitivity;
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
import de.dlr.gitlab.fame.time.Constants.Interval;
import de.dlr.gitlab.fame.time.TimeSpan;
import de.dlr.gitlab.fame.time.TimeStamp;
import util.UrlModelService;

/** Provides electricity price forecasts from external model via forecastApi. In order to reduce amount of calls to the external
 * forecasting model, the forecast window can be extended beyond the configured forecasting period. Specifically, the external
 * model is asked to provide a forecast for the period of {@link MarketForecaster#forecastPeriodInHours} plus
 * {@link #forecastWindowExtensionInHours} hours. The received forecasts are saved and used to fulfil the client forecast
 * requests. The "additional" hours from the {@link #forecastWindowExtensionInHours} are used in a rolling horizon way. <br>
 * However, if forecast errors exceed {@link #forecastErrorToleranceInEURperMWH}, an immediate update via the external forecasting
 * model is triggered.
 * 
 * @author Felix Nitsch, Christoph Schimeczek */
public class PriceForecasterApi extends MarketForecaster implements SensitivityForecastProvider {
	static final String ERR_TYPE_DISALLOWED = "Agent '%s' requested ForecastSensitivity of type '%s' which is not supported by PriceForecasterApi.";
	static final String WARN_PRICES_MISSING = "Could not check forecast tolerance - reference market prices missing. Ensure market sends awards to PriceForecasterApi.";
	private static Logger logger = LoggerFactory.getLogger(PriceForecasterApi.class);

	private final UrlModelService<ForecastApiRequest, ForecastApiResponse> urlService;
	private final int lookBackWindowInHours;
	private final int forecastWindowExtensionInHours;
	private final double forecastErrorToleranceInEURperMWH;
	/** Stores electricity market prices over time */
	private final TreeMap<Long, Double> marketClearingPrices = new TreeMap<>();
	private final TreeMap<Long, Double> residualLoadInMWh = new TreeMap<>();
	private final TimeSeries tsResidualLoadInMWh;
	private TreeMap<Long, Double> priceForecastMeans;
	private TreeMap<Long, Double> priceForecastVariances;
	private TimeStamp nextClearingTimeStep = now();
	private TimeStamp lastForecastedTime = new TimeStamp(Long.MIN_VALUE);

	@Input private static final Tree parameters = Make.newTree()
			.add(Make.newString("ServiceURL").help("URL to amiris-priceforecast api"),
					Make.newInt("LookBackWindowInHours")
							.help("Number of TimeSteps for look back, (default=ForecastPeriodInHours)").optional(),
					Make.newInt("ForecastWindowExtensionInHours")
							.help("Number of TimeSteps additional to forecast horizon to be requested from API, (default=0)")
							.optional(),
					Make.newDouble("ForecastErrorToleranceInEURperMWH").help(
							"Max accepted deviation between forecasted and realized electricity prices; if violated price forecasts are updated; if negative, no checks are performed(default=-1)")
							.optional(),
					Make.newSeries("ResidualLoadInMWh").optional())
			.buildTree();

	@Output
	private static enum OutputFields {
		ElectricityPriceForecastVarianceInEURperMWH
	}

	/** Creates new {@link PriceForecasterApi}
	 * 
	 * @param dataProvider provides input from config
	 * @throws MissingDataException in case mandatory input is missing */
	public PriceForecasterApi(DataProvider dataProvider) throws MissingDataException {
		super(dataProvider);
		ParameterData input = parameters.join(dataProvider);
		String serviceUrl = input.getString("ServiceURL");
		urlService = new UrlModelService<ForecastApiRequest, ForecastApiResponse>(serviceUrl) {};
		lookBackWindowInHours = input.getIntegerOrDefault("LookBackWindowInHours", forecastPeriodInHours);
		forecastWindowExtensionInHours = input.getIntegerOrDefault("ForecastWindowExtensionInHours", 0);
		forecastErrorToleranceInEURperMWH = input.getDoubleOrDefault("ForecastErrorToleranceInEURperMWH", -1.);
		tsResidualLoadInMWh = input.getTimeSeriesOrDefault("ResidualLoadInMWh", null);

		call(this::logClearingPrices).onAndUse(DayAheadMarket.Products.Awards);
		call(this::registerClearingTime).onAndUse(DayAheadMarket.Products.GateClosureInfo);
		call(this::sendPriceForecast).on(DamForecastProvider.Products.PriceForecast)
				.use(DamForecastClient.Products.PriceForecastRequest);
		call(this::checkClientRegistration).onAndUse(SensitivityForecastClient.Products.ForecastRegistration);
		call(this::doNothing).onAndUse(SensitivityForecastClient.Products.NetAward);
		call(this::sendSensitivityForecasts).on(SensitivityForecastProvider.Products.SensitivityForecast)
				.use(SensitivityForecastClient.Products.SensitivityRequest);
	}

	/** Extracts and store power prices reported from {@link DayAheadMarket}
	 * 
	 * @param input single power price message to read
	 * @param contracts not used */
	private void logClearingPrices(ArrayList<Message> input, List<Contract> contracts) {
		AwardData award = CommUtils.getExactlyOneEntry(input).getDataItemOfType(AwardData.class);
		marketClearingPrices.put(award.beginOfDeliveryInterval.getStep(), award.powerPriceInEURperMWH);
	}

	/** Notes clearing time for later determination of price forecasting times */
	private void registerClearingTime(ArrayList<Message> input, List<Contract> contracts) {
		ClearingTimes clearingTimes = CommUtils.getExactlyOneEntry(input).getDataItemOfType(ClearingTimes.class);
		nextClearingTimeStep = clearingTimes.getTimes().get(0);
	}

	/** Sends {@link AmountAtTime} from {@link MarketClearingResult} to the requesting trader */
	private void sendPriceForecast(ArrayList<Message> messages, List<Contract> contracts) {
		boolean forecastUpdateRequired = lastForecastedTime.equals(now()) ? false : prepareForecastUpdates();
		for (Contract contract : contracts) {
			ArrayList<Message> requests = CommUtils.extractMessagesFrom(messages, contract.getReceiverId());
			for (Message message : requests) {
				TimeStamp requestedTime = message.getDataItemOfType(PointInTime.class).validAt;
				for (AmountAtTime response : calcForecastResponses(requestedTime, forecastUpdateRequired)) {
					fulfilNext(contract, response);
				}
			}
		}
		if (!lastForecastedTime.equals(now())) {
			clearStoredDataAndWriteResults();
			lastForecastedTime = now();
		}
	}

	/** Prepare forecast updates, if required; returns true if updates were required, else false */
	private boolean prepareForecastUpdates() {
		if (tsResidualLoadInMWh != null) {
			chunkResidualLoad();
		}
		removeDataBefore(marketClearingPrices, now().earlierBy(new TimeSpan(lookBackWindowInHours, Interval.HOURS)));
		boolean forecastUpdateRequired = checkForecastUpdateRequired();
		if (forecastUpdateRequired) {
			updateForecasts();
		}
		return forecastUpdateRequired;
	}

	/** Chunks tsResidualLoadInMWh to residualLoadInMWh from lookBackWindowInHours to forecastPeriodInHours +
	 * forecastWindowExtensionInHours */
	private void chunkResidualLoad() {
		residualLoadInMWh.clear();
		TimeStamp startTime = nextClearingTimeStep.earlierBy(new TimeSpan(lookBackWindowInHours, Interval.HOURS));
		TimeStamp stopTime = nextClearingTimeStep.laterBy(new TimeSpan(forecastPeriodInHours, Interval.HOURS))
				.laterBy(new TimeSpan(forecastWindowExtensionInHours, Interval.HOURS));
		TimeStamp step = startTime;
		while (step.getStep() < stopTime.getStep()) {
			residualLoadInMWh.put(step.getStep(), tsResidualLoadInMWh.getValueLaterEqual(step));
			step = step.laterBy(new TimeSpan(1L, Interval.HOURS));
		}
	}

	/** Removes all values before given TimeStamp from specified container */
	private void removeDataBefore(TreeMap<Long, Double> container, TimeStamp time) {
		Iterator<Entry<Long, Double>> iterator = container.entrySet().iterator();
		while (iterator.hasNext()) {
			Entry<Long, Double> entry = iterator.next();
			if (entry.getKey() < time.getStep()) {
				iterator.remove();
			} else {
				break;
			}
		}
	}

	/** @return True if last result is missing, not within price tolerance, or required forecast price window is not fully available
	 *         in stored forecast map */
	private boolean checkForecastUpdateRequired() {
		if (priceForecastMeans == null) {
			return true;
		}
		return !resultIsInTolerance() || forecastIsMissing();
	}

	/** @return true if result is in tolerance, if no checks were requested, or if checks could not be done */
	private boolean resultIsInTolerance() {
		if (forecastErrorToleranceInEURperMWH < 0) {
			return true;
		}
		if (marketClearingPrices.isEmpty()) {
			logger.warn(WARN_PRICES_MISSING);
			return true;
		}
		var entry = marketClearingPrices.lastEntry();
		return Math.abs(entry.getValue() - priceForecastMeans.get(entry.getKey())) <= forecastErrorToleranceInEURperMWH;
	}

	/** @return true if required forecast is missing */
	private boolean forecastIsMissing() {
		var offset = new TimeSpan(forecastPeriodInHours - 1, Interval.HOURS);
		long endOfForecastWindow = nextClearingTimeStep.laterBy(offset).getStep();
		return !priceForecastMeans.containsKey(endOfForecastWindow);
	}

	/** Updates forecasts for required forecast price window considering extension */
	private void updateForecasts() {
		var request = new ForecastApiRequest(nextClearingTimeStep.getStep(),
				forecastPeriodInHours + forecastWindowExtensionInHours,
				marketClearingPrices, residualLoadInMWh);
		ForecastApiResponse response = urlService.call(request);
		priceForecastMeans = Util.averageValues(response.getForecastMeans());
		priceForecastVariances = Util.averageValues(response.getForecastVariances());
	}

	/** If forecastUpdateRequired, returns all updated forecasts until requestedTime; else, only forecast for requestedTime */
	private List<AmountAtTime> calcForecastResponses(TimeStamp requestedTime, boolean forecastUpdateRequired) {
		var messages = new ArrayList<AmountAtTime>();
		if (forecastUpdateRequired) {
			for (var entry : priceForecastMeans.headMap(requestedTime.laterByOne().getStep()).entrySet()) {
				messages.add(new AmountAtTime(new TimeStamp(entry.getKey()), entry.getValue()));
			}
		} else {
			var forecast = priceForecastMeans.get(requestedTime.getStep());
			messages.add(new AmountAtTime(requestedTime, forecast));
		}
		return messages;
	}

	/** Writes out latest price forecast results and clears them for the books */
	private void clearStoredDataAndWriteResults() {
		removeDataBefore(priceForecastMeans, now());
		removeDataBefore(priceForecastVariances, now());
		store(MarketForecaster.OutputFields.ElectricityPriceForecastInEURperMWH,
				priceForecastMeans.firstEntry().getValue());
		store(OutputFields.ElectricityPriceForecastVarianceInEURperMWH, priceForecastVariances.firstEntry().getValue());
	}

	/** Ensure that clients registered for the correct type of sensitivity forecast */
	private void checkClientRegistration(ArrayList<Message> messages, List<Contract> contracts) {
		for (Message message : messages) {
			var registration = message.getDataItemOfType(ForecastClientRegistration.class);
			if (registration.type != ForecastType.CostInsensitive) {
				throw new RuntimeException(String.format(ERR_TYPE_DISALLOWED, message.getSenderId(), registration.type));
			}
		}
	}

	/** Ignores input and just does nothing */
	private void doNothing(ArrayList<Message> messages, List<Contract> contracts) {}

	/** Sends {@link Sensitivity} to clients */
	private void sendSensitivityForecasts(ArrayList<Message> messages, List<Contract> contracts) {
		boolean forecastUpdateRequired = lastForecastedTime.equals(now()) ? false : prepareForecastUpdates();
		for (Contract contract : contracts) {
			ArrayList<Message> requests = CommUtils.extractMessagesFrom(messages, contract.getReceiverId());
			for (Message message : requests) {
				TimeStamp requestedTime = message.getDataItemOfType(PointInTime.class).validAt;
				for (AmountAtTime priceForecast : calcForecastResponses(requestedTime, forecastUpdateRequired)) {
					var assessment = new CostInsensitive();
					assessment.setPrice(priceForecast.amount);
					fulfilNext(contract, new Sensitivity(assessment, 1), new PointInTime(requestedTime));
				}
			}
		}
		if (!lastForecastedTime.equals(now())) {
			clearStoredDataAndWriteResults();
			lastForecastedTime = now();
		}
	}
}
