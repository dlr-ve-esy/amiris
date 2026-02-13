// SPDX-FileCopyrightText: 2025-2026 German Aerospace Center <amiris@dlr.de>
//
// SPDX-License-Identifier: Apache-2.0
package util;

import java.util.TreeMap;
import de.dlr.gitlab.fame.data.TimeSeries;
import de.dlr.gitlab.fame.time.TimeStamp;

/** Collection of methods to manipulate {@link TimeSeries}
 * 
 * @author A. Achraf El Ghazi */
public class SeriesManipulation {
	static final String ERR_NEGATIVE_SLICES = "Number of backward and forward window slices must not be negative.";
	/** Default value used if no value is available to extrapolate */
	public static final Double DEFAULT_VALUE = 0.;

	/** Slices the given series with specified backward and forward ranges around pivot and adds padding as needed. The pivot is
	 * excluded in the backward slicing, included however in forward slicing.
	 *
	 * @param series the time series to be sliced
	 * @param pivot the pivot timestamp from which backward and forward windows are calculated
	 * @param backwardSlices entries to retrieve before the pivot
	 * @param forwardSlices number of entries to retrieve, start at the pivot (backwards)
	 * @param timeStepsPerSlice number of time steps in the time span per slice (forwards)
	 * @return TreeMap containing the sliced values at their associated time steps with padding
	 * @throws IllegalArgumentException If the backward or forward value is negative */
	public static TreeMap<Long, Double> sliceWithPadding(TimeSeries series, TimeStamp pivot, int backwardSlices,
			int forwardSlices, long timeStepsPerSlice) {
		assertNotNegative(backwardSlices, forwardSlices);
		TreeMap<Long, Double> result = new TreeMap<>();
		addBackwardSlices(series, result, pivot.getStep(), backwardSlices, timeStepsPerSlice);
		addForwardSlices(series, result, pivot.getStep(), forwardSlices, timeStepsPerSlice);
		return result;
	}

	/** @throws IllegalArgumentException if any of the two given values is negative */
	private static void assertNotNegative(int backwardSlices, int forwardSlices) {
		if (backwardSlices < 0 || forwardSlices < 0) {
			throw new IllegalArgumentException(ERR_NEGATIVE_SLICES);
		}
	}

	/** Slices entries {@code backward} from the given series, starting from the first slice left of the {@code pivotStep}. Sliced
	 * values and times are put in the provided {@code result} map. Missing entries are extrapolated using nearest available value.
	 *
	 * @param series input time series as {@link TimeSeries}
	 * @param result {@link TreeMap} to store slices in
	 * @param pivotStep around which the slicing is performed
	 * @param numberOfSlices number of elements to include before the pivot step
	 * @param timeStepsPerSlice number of time steps in the time span per slice */
	private static void addBackwardSlices(TimeSeries series, TreeMap<Long, Double> result, long pivotStep,
			int numberOfSlices, long timeStepsPerSlice) {
		for (int i = 1; i <= numberOfSlices; i++) {
			long currentTimeStep = pivotStep - (i * timeStepsPerSlice);
			result.put(currentTimeStep, series.getValueEarlierEqual(new TimeStamp(currentTimeStep)));
		}
	}

	/** Slices entries {@code forward} from the given series, starting from the {@code pivotStep} that is included. Sliced values
	 * and times are put them in the provided {@code result} map. Missing entries are extrapolated using nearest available value.
	 *
	 * @param series input time series as {@link TreeMap}
	 * @param result {@link TreeMap} to store slices in
	 * @param pivotStep around which the slicing is performed
	 * @param numberOfSlices number of elements to include including the pivot step
	 * @param timeStepsPerSlice number of time steps in the time span per slice */
	private static void addForwardSlices(TimeSeries series, TreeMap<Long, Double> result, long pivotStep,
			int numberOfSlices, long timeStepsPerSlice) {
		for (int i = 0; i < numberOfSlices; i++) {
			long currentStep = pivotStep + (i * timeStepsPerSlice);
			result.put(currentStep, series.getValueLaterEqual(new TimeStamp(currentStep)));
		}
	}

	/** Slices the given data with specified backward and forward ranges around pivot and adds padding as needed. The pivot is
	 * excluded in the backward slicing, included however in forward slicing.
	 *
	 * @param data original data-series to be sliced
	 * @param pivot the pivot timestamp from which backward and forward windows are calculated
	 * @param backwardSlices number of entries to retrieve before the pivot (backwards)
	 * @param forwardSlices number of entries to retrieve, start at the pivot (forwards)
	 * @param timeStepsPerSlice number of time steps in the time span per slice
	 * @return TreeMap containing the sliced values at their associated time steps with padding
	 * @throws IllegalArgumentException If the backward or forward value is negative */
	public static TreeMap<Long, Double> sliceWithPadding(TreeMap<Long, Double> data, TimeStamp pivot,
			int backwardSlices, int forwardSlices, long timeStepsPerSlice) {
		assertNotNegative(backwardSlices, forwardSlices);
		TreeMap<Long, Double> result = new TreeMap<>();
		addBackwardSlices(data, result, pivot.getStep(), backwardSlices, timeStepsPerSlice);
		addForwardSlices(data, result, pivot.getStep(), forwardSlices, timeStepsPerSlice);
		return result;
	}

	/** Slices entries {@code backward} from the given series, starting from the first slice left of the {@code pivotStep}. Sliced
	 * values and times are put in the provided {@code result} map. Missing entries are extrapolated using nearest available value.
	 * If no values are available, the {@link #DEFAULT_VALUE} is used.
	 *
	 * @param data input time series as {@link TreeMap}
	 * @param result {@link TreeMap} to put the result in
	 * @param pivotStep around which the slicing is performed
	 * @param numberOfSlices number of elements to include before the pivot step
	 * @param timeStepsPerSlice number of time steps in the time span per slice */
	private static void addBackwardSlices(TreeMap<Long, Double> data, TreeMap<Long, Double> result,
			long pivotStep, int numberOfSlices, long timeStepsPerSlice) {
		double paddingValue = data.isEmpty() ? DEFAULT_VALUE : data.firstEntry().getValue();
		for (int i = 1; i <= numberOfSlices; i++) {
			long currentTimeStep = pivotStep - (i * timeStepsPerSlice);
			var entry = data.floorEntry(currentTimeStep);
			result.put(currentTimeStep, entry != null ? entry.getValue() : paddingValue);
		}
	}

	/** Slices entries {@code forward} from the given series, starting from the {@code pivotStep}.Sliced values and times are put in
	 * the provided {@code result} map. Missing entries are extrapolated using nearest available value. If no values are available,
	 * the {@link #DEFAULT_VALUE} is used.
	 *
	 * @param data input time series as {@link TreeMap}
	 * @param result {@link TreeMap} to put the result in
	 * @param pivotStep around which the slicing is performed
	 * @param backward number of elements to include before the pivot step.
	 * @param timeStepsPerSlice number of time steps in the time span per slice */
	private static void addForwardSlices(TreeMap<Long, Double> data, TreeMap<Long, Double> result,
			long pivotStep, int numberOfSlices, long timeStepsPerSlice) {
		double paddingValue = data.isEmpty() ? DEFAULT_VALUE : data.lastEntry().getValue();
		for (int i = 0; i < numberOfSlices; i++) {
			long currentTimeStep = pivotStep + (i * timeStepsPerSlice);
			var entry = data.ceilingEntry(currentTimeStep);
			result.put(currentTimeStep, entry != null ? entry.getValue() : paddingValue);
		}
	}
}
