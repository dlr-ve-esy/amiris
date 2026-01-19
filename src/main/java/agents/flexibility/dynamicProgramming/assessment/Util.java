// SPDX-FileCopyrightText: 2026 German Aerospace Center <amiris@dlr.de>
//
// SPDX-License-Identifier: Apache-2.0
package agents.flexibility.dynamicProgramming.assessment;

import java.util.ArrayList;
import java.util.TreeMap;

/** Utility methods used by {@link AssessmentFunction}s
 * 
 * @author Christoph Schimeczek */
public final class Util {

	/** Removes elements from given map associated with elements before given element
	 * 
	 * @param map to remove elements from
	 * @param element before which elements are removed from given map */
	static final <T> void clearMapBefore(TreeMap<T, ?> map, T element) {
		map.headMap(element).clear();
	}

	/** Returns required keys that are not yet present in given map
	 * 
	 * @param map to search for the required keys
	 * @param requiredKeys that are to be searched in given map
	 * @return list of all keys that are required but not yet contained in given map */
	static final <T> ArrayList<T> findMissingKeys(TreeMap<T, ?> map,
			ArrayList<T> requiredKeys) {
		ArrayList<T> missingElements = new ArrayList<>();
		for (T element : requiredKeys) {
			if (!map.containsKey(element)) {
				missingElements.add(element);
			}
		}
		return missingElements;
	}
}
