// SPDX-FileCopyrightText: 2026 German Aerospace Center <amiris@dlr.de>
//
// SPDX-License-Identifier: Apache-2.0
package agents.forecast;

import java.util.List;
import java.util.TreeMap;

/** Utility functions using in the forecasting package
 * 
 * @author Felix Nitsch, Christoph Schimeczek */
public class Util {
	static final String EMPTY_FUNCTION = "Cannot average over missing / empty list.";

	/** Averages values of multiple functions with same indices
	 * 
	 * @param functions list of functions with values y at index x
	 * @return averaged values of y of all functions at each same index x */
	public static TreeMap<Long, Double> averageValues(List<TreeMap<Long, Double>> functions) {
		if (functions == null || functions.size() == 0) {
			throw new RuntimeException(EMPTY_FUNCTION);
		}
		TreeMap<Long, Double> result = new TreeMap<>();
		double count = functions.size();
		for (var index : functions.get(0).keySet()) {
			double value = 0;
			for (var function : functions) {
				value += (double) function.get(index);
			}
			result.put(index, value / count);
		}
		return result;
	}
}
