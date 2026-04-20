// SPDX-FileCopyrightText: 2024-2026 German Aerospace Center <amiris@dlr.de>
//
// SPDX-License-Identifier: Apache-2.0
package agents.heatPump.strategists;

import java.util.ArrayList;
import agents.heatPump.HeatPump;
import agents.heatPump.HeatingInputData;
import agents.heatPump.StrategyParameters;
import agents.markets.meritOrder.sensitivities.MeritOrderSensitivity;
import agents.markets.meritOrder.sensitivities.PriceNoSensitivity;
import agents.storage.Device;
import de.dlr.gitlab.fame.agent.input.Make;
import de.dlr.gitlab.fame.agent.input.ParameterData;
import de.dlr.gitlab.fame.agent.input.ParameterData.MissingDataException;
import de.dlr.gitlab.fame.agent.input.Tree;
import de.dlr.gitlab.fame.data.TimeSeries;
import de.dlr.gitlab.fame.time.TimePeriod;
import endUser.EndUserTariff;
import util.UrlModelService;

/** Creates a cost-optimal HeatPumpSchedule according to real-time prices, which is endogenously calculated by a heat pump
 * dispatch model.
 * 
 * @author Evelyn Sperber, Christoph Schimeczek */
public class StrategistExternal extends HeatPumpStrategist {
	/** Input parameters required for connecting to an external API-based model */
	public static final Tree apiParameters = Make.newTree().optional()
			.add(Make.newString("ServiceUrl"), Make.newString("StaticParameterFolder"))
			.buildTree();
	private final UrlModelService<OptimisationInputs, OptimisationOutputs> optimiserApi;
	private final String staticParameterFolder;
	private boolean isFirstOptimisation = true;
	private final EndUserTariff tariffStrategist;
	private final double fixedRoomTemperaturInC;

	/** Creates a {@link StrategistExternal}
	 * 
	 * @param basicStrategy basic input data related to the strategist
	 * @param heatPump specifies the heat pump to be dispatched
	 * @param device the storage used for heat pump dispatch optimisation
	 * @param heatingData input regarding heat-related input time series
	 * @param installedUnits number of installed heat pump units
	 * @param strategyParams strategy parameters for heat pump operation
	 * @param tariffStrategist specifies the real time price design
	 * @param fixedRoomTemperaturInC initial room temperature when instantiated
	 * @throws MissingDataException if any required data is not provided */
	public StrategistExternal(ParameterData basicStrategy, HeatPump heatPump, Device device,
			HeatingInputData heatingData, TimeSeries installedUnits, StrategyParameters strategyParams,
			EndUserTariff tariffStrategist, double fixedRoomTemperaturInC) throws MissingDataException {
		super(basicStrategy, heatPump, heatingData, device, installedUnits, strategyParams);
		this.tariffStrategist = tariffStrategist;
		ParameterData apiParameters = strategyParams.getApiParameters();
		optimiserApi = new UrlModelService<OptimisationInputs, OptimisationOutputs>(
				apiParameters.getString("ServiceUrl")) {};
		staticParameterFolder = apiParameters.getString("StaticParameterFolder");
		this.fixedRoomTemperaturInC = fixedRoomTemperaturInC;
	}

	@Override
	protected MeritOrderSensitivity createBlankSensitivity() {
		return new PriceNoSensitivity();
	}

	@Override
	protected void updateSchedule(TimePeriod timePeriod) {
		OptimisationInputs inputs = prepareInputs(timePeriod);
		OptimisationOutputs outputs = optimiserApi.call(inputs);
		updateScheduleArrays(timePeriod, outputs);
	}

	/** Prepares the input data used in the external heat pump dispatch optimization model
	 * 
	 * @param timeSegment current time in simulation
	 * @return all inputs for external model */
	private OptimisationInputs prepareInputs(TimePeriod timeSegment) {
		OptimisationInputs inputs = new OptimisationInputs();
		inputs.setInitialize_optimization_model(isFirstOptimisation);
		isFirstOptimisation = false;
		inputs.setElectricity_prices(calcHouseholdElectricityPrices(timeSegment));
		inputs.setStatic_parameter_folder(staticParameterFolder);
		inputs.setSchedule_duration(scheduleDurationPeriods);
		inputs.setForecast_period(forecastSteps);
		return inputs;
	}

	/** Calculates real-time prices for households over the forecast period
	 * 
	 * @param timeSegment start time of the forecast period
	 * @return real-time prices for households over the forecast period */
	private ArrayList<Double> calcHouseholdElectricityPrices(TimePeriod startTime) {
		ArrayList<Double> consumerPrices = new ArrayList<>(forecastSteps);
		for (int period = 0; period < forecastSteps; period++) {
			TimePeriod timePeriod = startTime.shiftByDuration(period);
			PriceNoSensitivity priceObject = ((PriceNoSensitivity) getSensitivityForPeriod(timePeriod));
			double exchangePriceForecast = priceObject != null ? priceObject.getPriceForecast() : 0;
			consumerPrices
					.add(tariffStrategist.calcSalePriceInEURperMWH(exchangePriceForecast, startTime.getStartTime()));
		}
		return consumerPrices;
	}

	/** Updates schedule arrays starting at the given TimePeriod with optimization outputs regarding electricity demand and the
	 * given initial room temperature
	 * 
	 * @param timePeriod first period of the schedule to be created */
	private void updateScheduleArrays(TimePeriod startTime, OptimisationOutputs outputs) {
		for (int period = 0; period < scheduleDurationPeriods; period++) {
			demandScheduleInMWH[period] = outputs.getElectricity_demand().get(period);
			hourlyInitialTemperatureInC[period] = fixedRoomTemperaturInC;
		}
	}

	@Override
	public double getHeatLoad(TimePeriod currentTimeSegment) {
		return 0; // not needed for this Strategist type
	}
}
