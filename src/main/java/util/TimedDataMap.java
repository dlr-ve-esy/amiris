// SPDX-FileCopyrightText: 2026 German Aerospace Center <amiris@dlr.de>
//
// SPDX-License-Identifier: Apache-2.0
package util;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.TreeMap;
import java.util.function.Supplier;
import de.dlr.gitlab.fame.time.TimeStamp;

/** Container for data that is associated with a {@link TimeStamp} but also mapped to another key, e.g., a client ID.
 * 
 * @author Christoph Schimeczek */
public class TimedDataMap<U, V> {
	private final TreeMap<TimeStamp, HashMap<U, V>> dataPerTime = new TreeMap<>();

	/** Set a value associated with the given time and key
	 * 
	 * @param time that the value is associated with
	 * @param key that the value is associated with
	 * @param value that is to be set */
	public void set(TimeStamp time, U key, V value) {
		ensurePresent(time);
		dataPerTime.get(time).put(key, value);
	}

	/** Ensure that at the given time an inner map is initialised */
	private void ensurePresent(TimeStamp time) {
		if (!dataPerTime.containsKey(time)) {
			dataPerTime.put(time, new HashMap<>());
		}
	}

	/** Remove all entries that are strictly before the given time
	 * 
	 * @param time before which the data is to be removed */
	public void clearBefore(TimeStamp time) {
		dataPerTime.headMap(time).clear();
	}

	/** Return the value that is associated with the given time and key
	 * 
	 * @param time for which to return the value
	 * @param key for which to return the value
	 * @return the value, or null if no value is associated with the specified time and key */
	public V get(TimeStamp time, U key) {
		if (!dataPerTime.containsKey(time)) {
			return null;
		}
		return dataPerTime.get(time).get(key);
	}

	/** If no value is associated with the given time and key, use the provided function to calculate and set the value
	 * 
	 * @param time for which to search / set the value
	 * @param key for which to search / set the value
	 * @param function that will be called if no value is set yet, and whose return value will be used to define the value */
	public void computeIfAbsent(TimeStamp time, U key, Supplier<? extends V> function) {
		ensurePresent(time);
		var data = dataPerTime.get(time);
		if (!data.containsKey(key)) {
			data.put(key, function.get());
		}
	}

	/** Return all values that are associated with the given key across all times in ascending order
	 * 
	 * @param key for which to search for values
	 * @return all values that are associated with the given key */
	public ArrayList<V> getValuesOf(U key) {
		ArrayList<V> values = new ArrayList<V>();
		for (var data : dataPerTime.values()) {
			V value = data.get(key);
			if (value != null) {
				values.add(value);
			}
		}
		return values;
	}

	/** Return the inner data map that is associated with the specified time
	 * 
	 * @param time for which to return the data
	 * @return data associated with the specified time, or null if no data is associated with it */
	public HashMap<U, V> getDataAt(TimeStamp time) {
		ensurePresent(time);
		return dataPerTime.get(time);
	}

	/** Return all values that are associated with the given key across any times strictly less than the specified time
	 * 
	 * @param time before which the data is to be searched
	 * @param key for which to search for values
	 * @return all values that are associated with the given key and with times before the specified time */
	public ArrayList<V> getValuesBefore(TimeStamp time, U key) {
		ArrayList<V> values = new ArrayList<V>();
		for (var data : dataPerTime.headMap(time).values()) {
			values.add(data.get(key));
		}
		return values;
	}

	/** Return all keys associated with data at the given time
	 * 
	 * @param time at which the data is to be searched
	 * @return any keys that are associated with data at the specified time */
	public HashSet<U> getKeysBefore(TimeStamp time) {
		HashSet<U> keys = new HashSet<>();
		for (var data : dataPerTime.headMap(time).values()) {
			keys.addAll(data.keySet());
		}
		return keys;
	}
}
