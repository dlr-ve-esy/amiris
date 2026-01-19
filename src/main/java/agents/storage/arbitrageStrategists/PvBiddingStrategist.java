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
import agents.storage.Device;
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

/** Creates a bidding strategist for PV ML based agents with own load, own electricity production, and own storage.
 * 
 * @author A. Achraf El Ghazi, Christoph Schimeczek, Ulrich Frey */
public class PvBiddingStrategist {
	public static final Tree parameters = Make.newTree().add(
			Make.newInt("EnergyGenerationForwardWindow"), Make.newInt("EnergyGenerationBackwardWindow"),
			Make.newInt("StoredEnergyForwardWindow"), Make.newInt("StoredEnergyBackwardWindow"),
			Make.newInt("ElectricityConsumptionForwardWindow"), Make.newInt("ElectricityConsumptionBackwardWindow"),
			Make.newInt("ElectricityPriceForwardWindow"), Make.newInt("ElectricityPriceBackwardWindow"),
			Make.newInt("GridInteractionBackwardWindow")).buildTree();

	private final UrlModelService<PredictionRequest, PredictionResponse> urlService;
	private final String modelId;
	private final double installedGenerationPowerInMW;
	private final TimeSeries tsLoadInMW;
	private final TimeSeries tsGenerationProfile;
	private final int forecastPeriodInHours;
	private final Device storage;
	private final int energyGenerationForwardWindow;
	private final int energyGenerationBackwardWindow;
	private final int storedEnergyForwardWindow;
	private final int storedEnergyBackwardWindow;
	private final int electricityConsumptionForwardWindow;
	private final int electricityConsumptionBackwardWindow;
	private final int electricityPriceForwardWindow;
	private final int electricityPriceBackwardWindow;
	private final int gridInteractionBackwardWindow;
	private static final long TIME_STEPS_PER_SLICE = de.dlr.gitlab.fame.time.Constants.STEPS_PER_HOUR;

	private final TreeMap<Long, Double> endUserPriceForecastsInEURperMWH = new TreeMap<>();
	private final TreeMap<Long, Double> netLoadHistoryInMWH = new TreeMap<>();
	private final TreeMap<Long, Double> storedEnergyHistoryInMWH = new TreeMap<>();

	/** Create {@link PvBiddingStrategist} based on given input parameters
	 * 
	 * @param serviceURL URL of the ML prediction service
	 * @param modelId id of the ML prediction model
	 * @param installedGenerationInMW installed PV capacity
	 * @param tsLoadInMW time series of the self load
	 * @param tsGenerationProfile time series of the PV generation profile
	 * @param storage installed battery capacity
	 * @param forecastPeriodInHours requested forecast period
	 * @param predictionWindows group of prediction window parameters
	 * @throws MissingDataException if any required data is missing */
	public PvBiddingStrategist(String serviceURL, String modelId, double installedGenerationInMW,
			TimeSeries tsLoadInMW, TimeSeries tsGenerationProfile, Device storage, int forecastPeriodInHours,
			ParameterData predictionWindows) throws MissingDataException {
		urlService = new UrlModelService<PredictionRequest, PredictionResponse>(serviceURL) {};
		this.modelId = modelId;
		this.installedGenerationPowerInMW = installedGenerationInMW;
		this.tsLoadInMW = tsLoadInMW;
		this.tsGenerationProfile = tsGenerationProfile;
		this.storage = storage;
		this.forecastPeriodInHours = forecastPeriodInHours;
		storedEnergyHistoryInMWH.put(0L, storage.getCurrentEnergyInStorageInMWH());
		energyGenerationForwardWindow = predictionWindows.getInteger("EnergyGenerationForwardWindow");
		energyGenerationBackwardWindow = predictionWindows.getInteger("EnergyGenerationBackwardWindow");
		storedEnergyForwardWindow = predictionWindows.getInteger("StoredEnergyForwardWindow");
		storedEnergyBackwardWindow = predictionWindows.getInteger("StoredEnergyBackwardWindow");
		electricityConsumptionForwardWindow = predictionWindows.getInteger("ElectricityConsumptionForwardWindow");
		electricityConsumptionBackwardWindow = predictionWindows.getInteger("ElectricityConsumptionBackwardWindow");
		electricityPriceForwardWindow = predictionWindows.getInteger("ElectricityPriceForwardWindow");
		electricityPriceBackwardWindow = predictionWindows.getInteger("ElectricityPriceBackwardWindow");
		gridInteractionBackwardWindow = predictionWindows.getInteger("GridInteractionBackwardWindow");
	}

	/** Returns the predicted net load for the requested time via the ML model behind the UrlModelService
	 * 
	 * @param requestedTime requested time
	 * @return predicted net load for the requested time via the ML model behind the UrlModelService */
	public double getNetLoadPredictionInMWH(TimeStamp requestedTime) {
		List<InputVariable> inputVars = new ArrayList<InputVariable>();

		Map<Long, Double> EnergyGenerationPerMW = SeriesManipulation.sliceWithPadding(tsGenerationProfile,
				requestedTime, energyGenerationBackwardWindow, energyGenerationForwardWindow, TIME_STEPS_PER_SLICE);
		inputVars.add(new InputVariable("energyGenerationPerMW", EnergyGenerationPerMW));

		Map<Long, Double> StoredMWh = SeriesManipulation.sliceWithPadding(storedEnergyHistoryInMWH, requestedTime,
				storedEnergyBackwardWindow, storedEnergyForwardWindow, TIME_STEPS_PER_SLICE);
		inputVars.add(new InputVariable("storedMWh", StoredMWh));

		Map<Long, Double> ProsumersLoadInMW = SeriesManipulation.sliceWithPadding(tsLoadInMW, requestedTime,
				electricityConsumptionBackwardWindow, electricityConsumptionForwardWindow, TIME_STEPS_PER_SLICE);
		inputVars.add(new InputVariable("prosumersLoadInMW", ProsumersLoadInMW));

		Map<Long, Double> AggregatorSalesPriceInEURperMWH = SeriesManipulation.sliceWithPadding(
				endUserPriceForecastsInEURperMWH, requestedTime, electricityPriceBackwardWindow, electricityPriceForwardWindow,
				TIME_STEPS_PER_SLICE);
		inputVars.add(new InputVariable("aggregatorSalesPriceInEURperMWH", AggregatorSalesPriceInEURperMWH));

		Map<Long, Double> ProsumersGridInteraction = SeriesManipulation.sliceWithPadding(netLoadHistoryInMWH, requestedTime,
				gridInteractionBackwardWindow, gridInteractionBackwardWindow, TIME_STEPS_PER_SLICE);
		inputVars.add(new InputVariable("prosumersGridInteraction", ProsumersGridInteraction));

		PredictionRequest request = new PredictionRequest(modelId, requestedTime.getStep(), inputVars);
		PredictionResponse response = urlService.call(request);
		double correctedNetLoadPredictionInMWH = correctStorageOperation(response.getNetLoadPrediction(), requestedTime);

		return correctedNetLoadPredictionInMWH;
	}

	/** Corrects the predicted net load based on the current battery charge level, load, and generation. That is: predictedNetLoad =
	 * selfLoad - batteryDischarge + batteryCharge - PVProduction
	 * 
	 * @param netLoadPrediction predicted net load
	 * @param requestedTime requested time
	 * @return corrected net load prediction for the requested time */
	private double correctStorageOperation(double netLoadPrediction, TimeStamp requestedTime) {
		double externalEnergyDelta = netLoadPrediction - tsLoadInMW.getValueLinear(requestedTime)
				+ tsGenerationProfile.getValueLinear(requestedTime) * installedGenerationPowerInMW;
		double predictedStorageLevel = storage.getCurrentEnergyInStorageInMWH()
				+ storage.externalToInternalEnergy(externalEnergyDelta);
		double storage_below_min = Math.min(0, predictedStorageLevel);
		double storage_above_max = Math.max(0,
				predictedStorageLevel - storage.getEnergyToPowerRatio() * storage.getInternalPowerInMW());

		return netLoadPrediction - storage_below_min - storage_above_max;
	}

	/** Updates the battery storage based on awarded demand and supply values.
	 *
	 * @param awardData awarded data */
	public void updateStorage(AwardData awardData) {
		TimeStamp requestedTime = awardData.beginOfDeliveryInterval;
		double awardedDemandedPower = awardData.demandEnergyInMWH;
		double awardedSuppliedPower = awardData.supplyEnergyInMWH;
		double netPowerExchange = awardedDemandedPower - awardedSuppliedPower;
		double externalChargingPower = netPowerExchange - tsLoadInMW.getValueLinear(requestedTime)
				+ tsGenerationProfile.getValueLinear(requestedTime) * installedGenerationPowerInMW;

		storedEnergyHistoryInMWH.put(requestedTime.getStep(), storage.getCurrentEnergyInStorageInMWH());
		storage.chargeInMW(externalChargingPower);
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
		endUserPriceForecastsInEURperMWH.put(timeStamp.getStep(), priceForecast);
	}
}
