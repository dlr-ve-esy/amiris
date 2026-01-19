// SPDX-FileCopyrightText: 2026 German Aerospace Center <amiris@dlr.de>
//
// SPDX-License-Identifier: Apache-2.0
package agents.heatPump;

import de.dlr.gitlab.fame.agent.input.Make;
import de.dlr.gitlab.fame.agent.input.ParameterData;
import de.dlr.gitlab.fame.agent.input.ParameterData.MissingDataException;
import de.dlr.gitlab.fame.agent.input.Tree;
import de.dlr.gitlab.fame.data.TimeSeries;

/** Input time series to derive heating demands
 * 
 * @author Evelyn Sperber */
public class HeatingInputData {

	/** Input parameters for constructing new {@link HeatingInputData} */
	public static final Tree parameters = Make.newTree().add(Make.newSeries("TemperatureProfile"),
			Make.newSeries("SolarRadiation"), Make.newSeries("PvProfile"), Make.newSeries("HeatDemandProfile"))
			.buildTree();

	private TimeSeries ambientTemperature;
	private TimeSeries solarRadiation;
	private TimeSeries pvProfile;
	private TimeSeries heatDemandProfile;

	/** Creates {@link HeatingInputData}
	 * 
	 * @param data input data from config
	 * @throws MissingDataException if any required data is not provided */
	public HeatingInputData(ParameterData data) throws MissingDataException {
		ambientTemperature = data.getTimeSeries("TemperatureProfile");
		solarRadiation = data.getTimeSeries("SolarRadiation");
		pvProfile = data.getTimeSeries("PvProfile");
		heatDemandProfile = data.getTimeSeries("HeatDemandProfile");
	}

	/** @return the time series of the ambient temperature */
	public TimeSeries getTemperaturProfile() {
		return ambientTemperature;
	}

	/** @return the time series of the solar radiation on a vertical surface facing south */
	public TimeSeries getSolarRadiation() {
		return solarRadiation;
	}

	/** @return the time series of the (aggregated) heating demand */
	public TimeSeries getHeatDemandProfile() {
		return heatDemandProfile;
	}

	/** @return the time series of the local pv yield for self-consumption */
	public TimeSeries getPvProfile() {
		return pvProfile;
	}
}
