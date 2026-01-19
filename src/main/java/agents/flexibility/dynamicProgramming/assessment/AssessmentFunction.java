// SPDX-FileCopyrightText: 2026 German Aerospace Center <amiris@dlr.de>
//
// SPDX-License-Identifier: Apache-2.0
package agents.flexibility.dynamicProgramming.assessment;

import java.util.ArrayList;
import agents.flexibility.dynamicProgramming.Optimiser.Target;
import agents.flexibility.dynamicProgramming.states.StateManager;
import agents.forecast.sensitivity.SensitivityForecastProvider.ForecastType;
import de.dlr.gitlab.fame.communication.message.Message;
import de.dlr.gitlab.fame.time.TimeStamp;

/** A function to assess transitions between discretised states of a {@link StateManager}
 * 
 * @author Christoph Schimeczek, Felix Nitsch, Johannes Kochems */
public interface AssessmentFunction {
	/** Prepare {@link AssessmentFunction} for follow-up transition assessments at given time
	 * 
	 * @param time for which transitions will be assessed */
	void prepareFor(TimeStamp time);

	/** Return estimated value or costs of the transition
	 * 
	 * @param externalEnergyDeltaInMWH of the transition to be assessed; positive values correspond to "charging"
	 * @return the value or costs of the transition at the time the {@link AssessmentFunction} was {@link #prepareFor(TimeStamp)} */
	double assessTransition(double externalEnergyDeltaInMWH);

	/** Clear entries of electricity price forecasts before given time
	 * 
	 * @param time before which elements are cleared */
	void clearBefore(TimeStamp time);

	/** Return list of time stamps at which additional information is required, based on given list of time stamps required for
	 * dispatch planning
	 * 
	 * @param planningTimes times in the upcoming planning horizon
	 * @return list of time stamp which require additional information */
	ArrayList<TimeStamp> getMissingForecastTimes(ArrayList<TimeStamp> planningTimes);

	/** Store forecasts for dispatch assessment
	 * 
	 * @param messages to be scraped for forecast data */
	void storeForecast(ArrayList<Message> messages);

	/** Get type of target this {@link AssessmentFunction} is connected with
	 * 
	 * @return type of assessment target */
	Target getTargetType();

	/** Get type of SensitivityForecast used by this {@link AssessmentFunction}
	 * 
	 * @return type of sensitivity */
	ForecastType getSensitivityType();

	/** Get the multiplier currently applied at dispatch planning
	 * 
	 * @return dispatch multiplication factor */
	default double getMultiplier() {
		return 1.0;
	}
}
