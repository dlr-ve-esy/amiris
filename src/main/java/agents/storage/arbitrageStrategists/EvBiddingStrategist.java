// SPDX-FileCopyrightText: 2026 German Aerospace Center <amiris@dlr.de>
//
// SPDX-License-Identifier: Apache-2.0
package agents.storage.arbitrageStrategists;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import agents.predictionService.InputVariable;
import agents.predictionService.PredictionRequest;
import agents.predictionService.PredictionResponse;
import communications.message.AwardData;
import de.dlr.gitlab.fame.agent.input.Make;
import de.dlr.gitlab.fame.agent.input.ParameterData;
import de.dlr.gitlab.fame.agent.input.ParameterData.MissingDataException;
import de.dlr.gitlab.fame.agent.input.Tree;
import de.dlr.gitlab.fame.data.TimeSeries;
import de.dlr.gitlab.fame.time.TimePeriod;
import de.dlr.gitlab.fame.time.TimeStamp;
import util.SeriesManipulation;
import util.UrlModelService;

/** A bidding strategist for Electric Vehicles (EV) using machine-learning (ML) algorithms based on EV's electricity consumption
 * and storage.
 * 
 * @author A. Achraf El Ghazi, Christoph Schimeczek, Ulrich Frey */
public class EvBiddingStrategist {
	public static final Tree parameters = Make.newTree().add(
			Make.newInt("ElectricityPriceForwardWindow"), Make.newInt("ElectricityPriceBackwardWindow"),
			Make.newInt("ChargingPowerForwardWindow"), Make.newInt("ChargingPowerBackwardWindow"),
			Make.newInt("ElectricityConsumptionForwardWindow"), Make.newInt("ElectricityConsumptionBackwardWindow"),
			Make.newInt("LoadPredictionBackwardWindow")).buildTree();

	private final UrlModelService<PredictionRequest, PredictionResponse> urlService;
	private final String modelId;
	private final int forecastPeriodInHours;
	private final TimeSeries availableChargingPowerInMW;
	private final TimeSeries electricityConsumptionInMWH;
	private static final long TIME_STEPS_PER_SLICE = de.dlr.gitlab.fame.time.Constants.STEPS_PER_HOUR;

	public final TreeMap<Long, Double> priceForecastsInEURperMWH = new TreeMap<>();
	public final TreeMap<Long, Double> netLoadHistoryInMWH = new TreeMap<>();

	private final int electricityPriceForwardWindow;
	private final int electricityPriceBackwardWindow;
	private final int chargingPowerForwardWindow;
	private final int chargingPowerBackwardWindow;
	private final int electricityConsumptionForwardWindow;
	private final int electricityConsumptionBackwardWindow;
	private final int loadPredictionBackwardWindow;

	/** Create {@link EvBiddingStrategist} based on given input parameters
	 * 
	 * @param serviceURL URL of the ML prediction service
	 * @param modelId id of the ML model that should perform prediction
	 * @param forecastPeriodInHours requested forecast period
	 * @param availableChargingPowerInMW available charging power
	 * @param electricityConsumptionInMWH planed energy consumption
	 * @param predictionWindows group of prediction window parameters
	 * @throws MissingDataException if any required data is missing */
	public EvBiddingStrategist(String serviceURL, String modelId, int forecastPeriodInHours,
			TimeSeries availableChargingPowerInMW, TimeSeries electricityConsumptionInMWH, ParameterData predictionWindows)
			throws MissingDataException {
		urlService = new UrlModelService<PredictionRequest, PredictionResponse>(serviceURL) {};
		this.modelId = modelId;
		this.forecastPeriodInHours = forecastPeriodInHours;
		this.availableChargingPowerInMW = availableChargingPowerInMW;
		this.electricityConsumptionInMWH = electricityConsumptionInMWH;
		electricityPriceForwardWindow = predictionWindows.getInteger("ElectricityPriceForwardWindow");
		electricityPriceBackwardWindow = predictionWindows.getInteger("ElectricityPriceBackwardWindow");
		chargingPowerForwardWindow = predictionWindows.getInteger("ChargingPowerForwardWindow");
		chargingPowerBackwardWindow = predictionWindows.getInteger("ChargingPowerBackwardWindow");
		electricityConsumptionForwardWindow = predictionWindows.getInteger("ElectricityConsumptionForwardWindow");
		electricityConsumptionBackwardWindow = predictionWindows.getInteger("ElectricityConsumptionBackwardWindow");
		loadPredictionBackwardWindow = predictionWindows.getInteger("LoadPredictionBackwardWindow");
	}

	/** Returns the predicted net load for the requested time via the ML model behind the UrlModelService
	 * 
	 * @param requestedTime requested time
	 * @return predicted net load for the requested time via the ML model behind the UrlModelService */
	public double getNetLoadPredictionInMWH(TimeStamp requestedTime) {
		List<InputVariable> inputVars = new ArrayList<InputVariable>();

		Map<Long, Double> priceWindow = SeriesManipulation.sliceWithPadding(priceForecastsInEURperMWH, requestedTime,
				electricityPriceBackwardWindow, electricityPriceForwardWindow, TIME_STEPS_PER_SLICE);
		inputVars.add(new InputVariable("price_EUR_per_MWH", priceWindow));

		Map<Long, Double> availableChargingPowerWindow = SeriesManipulation.sliceWithPadding(availableChargingPowerInMW,
				requestedTime, chargingPowerBackwardWindow, chargingPowerForwardWindow, TIME_STEPS_PER_SLICE);
		inputVars.add(new InputVariable("aggregated_available_charging_power_cluster_MW", availableChargingPowerWindow));

		Map<Long, Double> elecConsumptionWindow = SeriesManipulation.sliceWithPadding(electricityConsumptionInMWH,
				requestedTime, electricityConsumptionBackwardWindow, electricityConsumptionForwardWindow, TIME_STEPS_PER_SLICE);
		inputVars.add(new InputVariable("aggregated_elec_consumption_cluster_MWH", elecConsumptionWindow));

		Map<Long, Double> optimisedLoadWindow = SeriesManipulation.sliceWithPadding(netLoadHistoryInMWH, requestedTime,
				loadPredictionBackwardWindow, 0, TIME_STEPS_PER_SLICE);
		inputVars.add(new InputVariable("aggregated_optimised_load_MW", optimisedLoadWindow));

		PredictionRequest request = new PredictionRequest(modelId, requestedTime.getStep(), inputVars);
		PredictionResponse response = urlService.call(request);
		return response.getNetLoadPrediction();
	}

	/** Updates the history of actual net load based on awarded demand and supply values.
	 *
	 * @param awardData awarded data */
	public void updateLoadHistory(AwardData awardData) {
		double awardedLoad = awardData.demandEnergyInMWH - awardData.supplyEnergyInMWH;
		netLoadHistoryInMWH.put(awardData.beginOfDeliveryInterval.getStep(), awardedLoad);
	}

	/** Retrieves the list of time stamps for which electricity price forecasts are missing.
	 *
	 * @param nextTime the starting time period for forecast search
	 * @return list of times for which forecasts are missing */
	public ArrayList<TimeStamp> getTimesMissingForecasts(TimePeriod nextTime) {
		ArrayList<TimeStamp> missingTimes = new ArrayList<>();
		for (int i = 0; i < forecastPeriodInHours; i++) {
			TimePeriod timeSegment = nextTime.shiftByDuration(i);
			missingTimes.add(timeSegment.getStartTime());
		}
		return missingTimes;
	}

	/** Stores the electricity price forecast for the requested time.
	 *
	 * @param timeStamp requested time
	 * @param priceForecast price forecast */
	public void storeElectricityPriceForecast(TimeStamp timeStamp, double priceForecast) {
		priceForecastsInEURperMWH.put(timeStamp.getStep(), priceForecast);
	}
}
