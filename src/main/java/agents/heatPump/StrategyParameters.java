// SPDX-FileCopyrightText: 2026 German Aerospace Center <amiris@dlr.de>
//
// SPDX-License-Identifier: Apache-2.0
package agents.heatPump;

import agents.heatPump.strategists.HeatPumpStrategist.HeatPumpStrategistType;
import agents.heatPump.strategists.StrategistExternal;
import de.dlr.gitlab.fame.agent.input.Make;
import de.dlr.gitlab.fame.agent.input.ParameterData;
import de.dlr.gitlab.fame.agent.input.ParameterData.MissingDataException;
import de.dlr.gitlab.fame.agent.input.Tree;
import de.dlr.gitlab.fame.time.TimeStamp;

/** Strategy parameters for heat pump operation
 * 
 * @author Evelyn Sperber */
public class StrategyParameters {
	/** Input parameters stored in a {@link StrategyParameters} instance */
	public static final Tree parameters = Make.newTree()
			.add(Make.newInt("ModelledChargingSteps"),
					Make.newEnum("HeatPumpStrategistType", HeatPumpStrategistType.class),
					Make.newSeries("MinimalRoomTemperatureInC"), Make.newSeries("MaximalRoomTemperatureInC"),
					Make.newSeries("MeanRoomTemperatureInC"))
			.addAs("ApiParameters", StrategistExternal.apiParameters).buildTree();

	private int modelledChargingSteps;
	private HeatPumpStrategistType heatPumpStrategistType;
	private double minimalRoomTemperatureInC;
	private double maximalRoomTemperatureInC;
	private double meanRoomTemperatureInC;
	private ParameterData apiParameters;

	/** Creates {@link StrategyParameters}
	 * 
	 * @param data input data from config
	 * @throws MissingDataException if any required data is not provided */
	public StrategyParameters(ParameterData data) throws MissingDataException {
		heatPumpStrategistType = data.getEnum("HeatPumpStrategistType", HeatPumpStrategistType.class);
		if (heatPumpStrategistType == HeatPumpStrategistType.MIN_COST_FILE
				|| heatPumpStrategistType == HeatPumpStrategistType.MIN_COST_RC) {
			modelledChargingSteps = data.getInteger("ModelledChargingSteps");
		}
		if (heatPumpStrategistType == HeatPumpStrategistType.INFLEXIBLE_RC
				|| heatPumpStrategistType == HeatPumpStrategistType.MIN_COST_RC
				|| heatPumpStrategistType == HeatPumpStrategistType.EXTERNAL) {
			minimalRoomTemperatureInC = data.getTimeSeries("MinimalRoomTemperatureInC")
					.getValueLinear(new TimeStamp(0));
			maximalRoomTemperatureInC = data.getTimeSeries("MaximalRoomTemperatureInC")
					.getValueLinear(new TimeStamp(0));
		}
		if (heatPumpStrategistType == HeatPumpStrategistType.EXTERNAL) {
			meanRoomTemperatureInC = data.getTimeSeries("MeanRoomTemperatureInC").getValueLinear(new TimeStamp(0));
			apiParameters = data.getGroup("ApiParameters");
		}
	}

	/** @return API parameters */
	public ParameterData getApiParameters() {
		return apiParameters;
	}

	/** @return modelled room temperature steps for dynamic programming */
	public int getChargingSteps() {
		return modelledChargingSteps;
	}

	/** @return type of heat pump strategist */
	public HeatPumpStrategistType getHeatPumpStrategistType() {
		return heatPumpStrategistType;
	}

	/** @return minimal allowed room temperature */
	public double getMinimalRoomTemperatureInC() {
		return minimalRoomTemperatureInC;
	}

	/** @return maximal allowed room temperature */
	public double getMaximalRoomTemperatureInC() {
		return maximalRoomTemperatureInC;
	}

	/** @return average allowed room temperature */
	public double getMeanRoomTemperatureInC() {
		return meanRoomTemperatureInC;
	}
}
