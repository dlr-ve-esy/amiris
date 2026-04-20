// SPDX-FileCopyrightText: 2025-2026 German Aerospace Center <amiris@dlr.de>
//
// SPDX-License-Identifier: Apache-2.0
package agents.flexibility.dynamicProgramming.states;

import java.util.Arrays;
import java.util.List;
import java.util.TreeMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import de.dlr.gitlab.fame.agent.input.Make;
import de.dlr.gitlab.fame.agent.input.ParameterData;
import de.dlr.gitlab.fame.agent.input.ParameterData.MissingDataException;
import de.dlr.gitlab.fame.agent.input.Tree;
import de.dlr.gitlab.fame.data.TimeSeries;
import de.dlr.gitlab.fame.time.TimeStamp;

/** Interpolates water values for the time and energy content of a flexibility device.
 * 
 * @author Christoph Schimeczek */
public class WaterValues {
	static final String PARAM_STORED_ENERGY = "StoredEnergyInMWH";
	static final String PARAM_WATER_VALUE = "WaterValueInEUR";
	public static final Tree parameters = Make.newTree().list().optional().add(
			Make.newDouble(PARAM_STORED_ENERGY), Make.newSeries(PARAM_WATER_VALUE)).buildTree();

	static final String WARN_NOT_USED = "Cannot interpolate water value: Choose other energy content level than zero if just one is provided.";
	private static Logger logger = LoggerFactory.getLogger(WaterValues.class);

	private final double[] energyContentsInMWH;
	private final TimeSeries[] waterValuesInEUR;

	/** Instantiates new WaterValues
	 * 
	 * @param inputs optional input to read the data from
	 * @throws MissingDataException if any required data in the inputs is missing */
	public WaterValues(List<ParameterData> inputs) throws MissingDataException {
		if (inputs == null || inputs.isEmpty()) {
			energyContentsInMWH = null;
			waterValuesInEUR = null;
		} else {
			TreeMap<Double, TimeSeries> sorted = new TreeMap<>();
			for (ParameterData input : inputs) {
				sorted.put(input.getDouble(PARAM_STORED_ENERGY), input.getTimeSeries(PARAM_WATER_VALUE));
			}
			energyContentsInMWH = new double[inputs.size()];
			waterValuesInEUR = new TimeSeries[inputs.size()];
			transformMapToArrays(sorted);
			if (energyContentsInMWH.length == 1 && energyContentsInMWH[0] == 0.) {
				logger.warn(WARN_NOT_USED);
			}
		}
	}

	/** Stores content of given map in the class's data arrays */
	private void transformMapToArrays(TreeMap<Double, TimeSeries> sorted) {
		int index = 0;
		for (double key : sorted.navigableKeySet()) {
			energyContentsInMWH[index] = key;
			waterValuesInEUR[index] = sorted.get(key);
			index++;
		}
	}

	/** Returns interpolated water value for specified time and energy content, or 0 if value is not available
	 * 
	 * @param time at which to get the water value for
	 * @param energyContentInMWH for which to get the water value for
	 * @return water value in EUR */
	public double getValueInEUR(TimeStamp time, double energyContentInMWH) {
		if (energyContentsInMWH == null) {
			return 0;
		}
		if (energyContentsInMWH.length == 1) {
			return interOrExtrapolateFromOne(time, energyContentInMWH);
		}
		return interOrExtrapolateFromMultiple(time, energyContentInMWH);
	}

	/** @return interpolated water value based on one time series */
	private double interOrExtrapolateFromOne(TimeStamp time, double energyContentInMWH) {
		if (energyContentsInMWH[0] == 0) {
			return 0;
		}
		double waterValueAtX = waterValuesInEUR[0].getValueLinear(time);
		return waterValueAtX / energyContentsInMWH[0] * energyContentInMWH;
	}

	/** @return interpolated water value based on multiple time series */
	private double interOrExtrapolateFromMultiple(TimeStamp time, double energyContentInMWH) {
		int index = Arrays.binarySearch(energyContentsInMWH, energyContentInMWH);
		if (index >= 0) {
			return waterValuesInEUR[index].getValueLinear(time);
		}
		int insertionPoint = -(index + 1);
		int lowerIndex = getLowerIndex(insertionPoint);
		int upperIndex = lowerIndex + 1;
		double y1 = waterValuesInEUR[upperIndex].getValueLinear(time);
		double y0 = waterValuesInEUR[lowerIndex].getValueLinear(time);
		double x1 = energyContentsInMWH[upperIndex];
		double x0 = energyContentsInMWH[lowerIndex];
		return (y1 - y0) / (x1 - x0) * (energyContentInMWH - x0) + y0;
	}

	/** @return lower index to be used for interpolation / extrapolation */
	private int getLowerIndex(int insertionPoint) {
		if (insertionPoint == 0) {
			return 0;
		} else if (insertionPoint == waterValuesInEUR.length) {
			return waterValuesInEUR.length - 2;
		}
		return insertionPoint - 1;
	}

	/** Returns true if any water value interpolation can be provided, false otherwise
	 * 
	 * @return true if water value interpolation is possible */
	public boolean hasData() {
		return energyContentsInMWH != null;
	}
}
