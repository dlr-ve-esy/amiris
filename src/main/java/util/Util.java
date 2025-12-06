// SPDX-FileCopyrightText: 2025 German Aerospace Center <amiris@dlr.de>
//
// SPDX-License-Identifier: Apache-2.0
package util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import communications.message.PointInTime;
import de.dlr.gitlab.fame.communication.CommUtils;
import de.dlr.gitlab.fame.communication.message.DataItem;
import de.dlr.gitlab.fame.communication.message.Message;
import de.dlr.gitlab.fame.time.TimeStamp;

/** Collection of general static methods used across packages
 * 
 * @author Christoph Schimeczek, Martin Klein, Marc Deissenroth */
public final class Util {
	static final String INVALID_RANGE = "Interpolation: minValue must be lower equal to maxValue";
	static final String INVALID_STEPS = "Interpolation steps must not be negative!";
	static final String NO_INSTANCE = "Do not instantiate class: ";
	static final String COUNT_MISMATCH = "No equal number of entries for in list of messages for types %s %s";
	static final String TIME_DUPLICATE = "More than one message of type %s found for same time %s";
	static final String TIME_UNMATCHED = "Time %s in messages of type %s could not be matched to times in messages of type %s";

	Util() {
		throw new IllegalStateException(NO_INSTANCE + getClass().getCanonicalName());
	}

	/** Returns a newly created List of doubles, interpolating between given minimum and maximum
	 * 
	 * @param minValue minimum value, if steps &gt; 1 also included in the returned list
	 * @param maxValue maximum value, if steps &gt; 1 also included in the returned list, requires maxValue &gt; minValue
	 * @param steps integer number of interpolation steps [0..]
	 * @return a list of interpolated values; in case of
	 *         <ul>
	 *         <li>steps = 0: an empty list</li>
	 *         <li>steps = 1: average of minValue and maxValue</li>
	 *         <li>steps = 2: minValue and maxValue</li>
	 *         <li>steps &gt; 2: minValue, maxValue + (steps - 2) intermediate values, splitting the interval in (steps - 1)
	 *         equidistant segments</li>
	 *         </ul>
	 * @throws IllegalArgumentException if steps are negative or if minValue &gt; maxValue */
	public static ArrayList<Double> linearInterpolation(double minValue, double maxValue, int steps) {
		ensureValidRange(minValue, maxValue);
		ArrayList<Double> interpolatedValues = new ArrayList<>();
		if (steps < 0) {
			throw new IllegalArgumentException(INVALID_STEPS);
		} else if (steps == 1) {
			double averageValue = (minValue + maxValue) / 2.;
			interpolatedValues.add(averageValue);
		} else if (steps > 1) {
			double currentValue = minValue;
			double stepWidth = (maxValue - minValue) / (steps - 1);
			for (int idx = 0; idx < steps; idx++) {
				interpolatedValues.add(currentValue);
				currentValue += stepWidth;
			}
		}
		return interpolatedValues;
	}

	/** Ensures that given minValue is smaller than or equal to given maxValue
	 * 
	 * @param minValue must be smaller than or equal to maxValue
	 * @param maxValue must be larger than or equal to minValue
	 * @throws IllegalArgumentException in case minValue &gt; maxValue */
	public static void ensureValidRange(double minValue, double maxValue) {
		if (minValue > maxValue) {
			throw new IllegalArgumentException(INVALID_RANGE);
		}
	}

	/** Calculates median of given data using {@link DescriptiveStatistics}
	 * 
	 * @param data to be analysed
	 * @return median of the given data or NaN if given data was null or empty */
	public static double calcMedian(double... data) {
		return (new DescriptiveStatistics(data)).getPercentile(50);
	}

	/** Searches given list of messages for the given type of DataItem. If at least one such message is found: Removes first message
	 * with that payload from given list and returns the payload. Further messages with that payload still reside in the message
	 * list. If no message with matching payload is found: returns null.
	 * 
	 * @param <T> type of searched and returned DataItem
	 * @param payload class type of DataItem to search for
	 * @param messages list of messages to search; message with found data item is removed
	 * @return first matching DataItem found */
	public static <T extends DataItem> T removeFirstMessageWithDataItem(Class<T> payload, List<Message> messages) {
		if (messages != null) {
			Iterator<Message> iterator = messages.iterator();
			while (iterator.hasNext()) {
				Message message = iterator.next();
				if (message.containsType(payload)) {
					iterator.remove();
					return message.getDataItemOfType(payload);
				}
			}
		}
		return null;
	}

	/** Searches given {@link Message}s for those that contain any of the two given types and extracts (removes) them from the given
	 * list. Then, matches the extracted messages to {@link MessagePair}s with same {@link TimeStamp}.
	 * 
	 * @param <X> first type of PointInTime items
	 * @param <Y> second type of PointInTime items
	 * @param messages list of messages to be searched for those containing firstType or secondType
	 * @param firstType of PointInTime items to be extracted
	 * @param secondType of PointInTime items to be extracted
	 * @return Map of {@link MessagePair}s indexed by {@link TimeStamp}
	 * @throws IllegalArgumentException if {@link TimeStamp}s are not unique among the messages of each type, if number of messages
	 *           for both types are not equal, if times for both types do not exactly match */
	public static <X extends PointInTime, Y extends PointInTime> HashMap<TimeStamp, MessagePair<X, Y>> matchMessagesByTime(
			ArrayList<Message> messages, Class<X> firstType, Class<Y> secondType) {
		List<Message> messagesOfFirstType = CommUtils.extractMessagesWith(messages, firstType);
		List<Message> messagesOfOtherType = CommUtils.extractMessagesWith(messages, secondType);
		if (messagesOfFirstType.size() != messagesOfOtherType.size()) {
			throw new IllegalArgumentException(String.format(COUNT_MISMATCH, firstType, secondType));
		}
		HashMap<TimeStamp, MessagePair<X, Y>> result = new HashMap<>(messages.size() / 2);
		for (var message : messagesOfFirstType) {
			X dataItem = message.getDataItemOfType(firstType);
			if (result.containsKey(dataItem.validAt)) {
				throw new IllegalArgumentException(String.format(TIME_DUPLICATE, firstType, dataItem.validAt));
			}
			result.put(dataItem.validAt, new MessagePair<X, Y>(dataItem));
		}
		for (var message : messagesOfOtherType) {
			Y dataItem = message.getDataItemOfType(secondType);
			if (!result.containsKey(dataItem.validAt)) {
				throw new IllegalArgumentException(String.format(TIME_UNMATCHED, dataItem.validAt, secondType, firstType));
			}
			MessagePair<X, Y> pair = result.get(dataItem.validAt);
			if (pair.getSecondItem() != null) {
				throw new IllegalArgumentException(String.format(TIME_DUPLICATE, secondType, dataItem.validAt));
			}
			result.get(dataItem.validAt).setSecondItem(dataItem);
		}
		return result;
	}

	/** A pair of messages containing a {@link PointInTime} {@link DataItem} that have the same {@link TimeStamp}.
	 * 
	 * @param <X> first type of PointInTime items
	 * @param <Y> second type of PointInTime items */
	public static class MessagePair<X extends PointInTime, Y extends PointInTime> {
		private X firstItem;
		private Y secondItem;

		private MessagePair(X firstItem) {
			this.firstItem = firstItem;
		}

		private void setSecondItem(Y secondItem) {
			this.secondItem = secondItem;
		}

		/** @return first item of type X */
		public X getFirstItem() {
			return firstItem;
		}

		/** @return second item of type Y */
		public Y getSecondItem() {
			return secondItem;
		}
	}

	/** Returns first entries of given integer array up to given new length
	 * <ul>
	 * <li>if new length matches or exceeds length of given array, the original array is returned</li>
	 * <li>if new length is Zero or negative, an array of length Zero is returned</li>
	 * <li>in any other case, a new array is returned that contains the first length entries</li>
	 * </ul>
	 * 
	 * @param array to be truncated
	 * @param newLength of the truncated array
	 * @return first entries of given array */
	public static int[] truncateIntArray(int[] array, int newLength) {
		if (newLength < array.length) {
			if (newLength <= 0) {
				return new int[0];
			} else {
				return Arrays.copyOfRange(array, 0, newLength);
			}
		}
		return array;
	}
}