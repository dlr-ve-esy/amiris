// SPDX-FileCopyrightText: 2026 German Aerospace Center <amiris@dlr.de>
//
// SPDX-License-Identifier: Apache-2.0
package agents.heatPump.strategists;

import java.util.ArrayList;
import util.JSONable;

/** Encapsulates all input data from AMIRIS for the external model that is called by heat pump strategist
 * {@link StrategistExternal}
 * 
 * @author Evelyn Sperber, Christoph Schimeczek */
public class OptimisationInputs implements JSONable {

	private boolean initialize_optimization_model;
	private int schedule_duration;
	private int forecast_period;
	private ArrayList<Double> electricity_prices;
	private String static_parameter_folder;

	/** @return if optimisation model needs to be initialised, typically at the start of the simulation */
	public boolean isInitialize_optimization_model() {
		return initialize_optimization_model;
	}

	/** set if optimisation model needs to be initialised, typically at the start of the simulation
	 * 
	 * @param initialize_optimization_model model is initialised if true */
	public void setInitialize_optimization_model(boolean initialize_optimization_model) {
		this.initialize_optimization_model = initialize_optimization_model;
	}

	/** @return real-time electricity prices under which heat pumps are operated */
	public ArrayList<Double> getElectricity_prices() {
		return electricity_prices;
	}

	/** set the real-time electricity prices under which heat pumps are operated
	 * 
	 * @param electricity_prices to be assumed by the optimisation model */
	public void setElectricity_prices(ArrayList<Double> electricity_prices) {
		this.electricity_prices = electricity_prices;
	}

	/** @return link to directory of all input parameters for external heat pump dispatch model */
	public String getStatic_parameter_folder() {
		return static_parameter_folder;
	}

	/** set the link to directory of all input parameters for external heat pump dispatch model
	 * 
	 * @param static_parameter_folder path to folder with static parameters */
	public void setStatic_parameter_folder(String static_parameter_folder) {
		this.static_parameter_folder = static_parameter_folder;
	}

	/** @return the schedule duration */
	public int getSchedule_duration() {
		return schedule_duration;
	}

	/** set the schedule duration
	 * 
	 * @param schedule_duration length of the schedule */
	public void setSchedule_duration(int schedule_duration) {
		this.schedule_duration = schedule_duration;
	}

	/** @return the forecast period */
	public int getForecast_period() {
		return forecast_period;
	}

	/** set the forecast period
	 * 
	 * @param forecast_period number of available forecasts */
	public void setForecast_period(int forecast_period) {
		this.forecast_period = forecast_period;
	}
}
