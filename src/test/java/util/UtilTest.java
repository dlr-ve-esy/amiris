// SPDX-FileCopyrightText: 2026 German Aerospace Center <amiris@dlr.de>
//
// SPDX-License-Identifier: Apache-2.0
package util;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static testUtils.Exceptions.assertThrowsMessage;
import java.util.ArrayList;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import communications.message.PointInTime;
import de.dlr.gitlab.fame.communication.message.DataItem;
import de.dlr.gitlab.fame.communication.message.Message;
import de.dlr.gitlab.fame.protobuf.Agent.ProtoDataItem;
import de.dlr.gitlab.fame.protobuf.Agent.ProtoDataItem.Builder;
import de.dlr.gitlab.fame.time.TimeStamp;

public class UtilTest {

	@Test
	public void constructor_throws() {
		assertThrowsMessage(IllegalStateException.class, Util.NO_INSTANCE, () -> new Util());
	}

	@ParameterizedTest
	@ValueSource(ints = {0, -1, 1})
	public void ensureValidRange_equals(int number) {
		Util.ensureValidRange(number, number);
	}

	@ParameterizedTest
	@CsvSource({"-2, -1", "-1, 0", "0, 1", "1, 2"})
	public void ensureValidRange_aLessThanB(int left, int right) {
		Util.ensureValidRange(left, right);
	}

	@ParameterizedTest
	@CsvSource({"-1, -2", "0, -0.0001", "0.0001, 0", "1, 0", "2, 1"})
	public void ensureValidRange_throwsIf_bLessThanA(double left, double right) {
		assertThrowsMessage(IllegalArgumentException.class, Util.INVALID_RANGE, () -> Util.ensureValidRange(left, right));
	}

	@Test
	public void linearInterpolation_throwsIf_invalidRange() {
		assertThrowsMessage(IllegalArgumentException.class, Util.INVALID_RANGE, () -> Util.linearInterpolation(-1, -2, 2));
	}

	@Test
	public void linearInterpolation_throwsIf_negativeSteps() {
		assertThrowsMessage(IllegalArgumentException.class, Util.INVALID_STEPS, () -> Util.linearInterpolation(1, 2, -2));
	}

	@Test
	public void linearInterpolation_noSteps_empty() {
		assert Util.linearInterpolation(1, 2, 0).size() == 0;
	}

	@ParameterizedTest
	@CsvSource({"-1, 1, 0", "0, 1, 0.5", "1, 10, 5.5"})
	public void linearInterpolation_oneStep_average(double left, double right, double expected) {
		assertEquals(expected, Util.linearInterpolation(left, right, 1).get(0), 1E-12);
	}

	@ParameterizedTest
	@CsvSource({"-1, 10", "1, 10"})
	public void linearInterpolation_twoSteps_minMax(double left, double right) {
		assertThat(Util.linearInterpolation(left, right, 2), contains(left, right));
	}

	@Test
	public void linearInterpolation_moreSteps() {
		assertThat(Util.linearInterpolation(0, 1, 3), contains(0., 0.5, 1.));
		assertThat(Util.linearInterpolation(-1, 3, 5), contains(-1., 0., 1., 2., 3.));
		assertThat(Util.linearInterpolation(0, 2, 5), contains(0., 0.5, 1., 1.5, 2.));
	}

	@Test
	public void calcMedian_doesNotChangeInput() {
		double[] input = new double[] {5.4, 2.3, 99.9};
		double[] copy = Arrays.copyOf(input, 3);
		Util.calcMedian(input);
		assertTrue(Arrays.equals(input, copy));
	}

	@Test
	public void calcMedian_null_returnsNan() {
		assertTrue(Double.isNaN(Util.calcMedian(null)));
	}

	@Test
	public void calcMedian_empty_returnsNan() {
		assertTrue(Double.isNaN(Util.calcMedian(new double[] {})));
	}

	@Test
	public void calcMedian_one_equals() {
		assertEquals(21., Util.calcMedian(21.), 1E-12);
	}

	@Test
	public void calcMedian_two_average() {
		assertEquals(21., Util.calcMedian(0., 42.), 1E-12);
	}

	@Test
	public void calcMedian_three_middle() {
		assertEquals(1., Util.calcMedian(0., 1., 42.), 1E-12);
	}

	@Test
	public void removeFirstMessageWithDataItem_null_returnsNull() {
		assertNull(Util.removeFirstMessageWithDataItem(DataItem.class, null));
	}

	@Test
	public void removeFirstMessageWithDataItem_emptyList_returnsNull() {
		assertNull(Util.removeFirstMessageWithDataItem(DataItem.class, new ArrayList<Message>()));
	}

	@SuppressWarnings("unchecked")
	@Test
	public void removeFirstMessageWithDataItem_contained_isRemoved() {
		ArrayList<Message> messages = mockMessages(DataItem.class);
		DataItem result = Util.removeFirstMessageWithDataItem(DataItem.class, messages);
		assertEquals(0, messages.size());
		assertTrue(result != null);
	}

	/** Creates List of mocked messages given DataItems */
	@SuppressWarnings("unchecked")
	private ArrayList<Message> mockMessages(Class<? extends DataItem>... itemClasses) {
		ArrayList<Message> result = new ArrayList<Message>();
		for (Class<? extends DataItem> itemClass : itemClasses) {
			Message mockMessage = mock(Message.class);
			when(mockMessage.containsType(itemClass)).thenReturn(true);
			when(mockMessage.getDataItemOfType(any())).thenReturn(mock(itemClass));
			result.add(mockMessage);
		}
		return result;
	}

	@SuppressWarnings("unchecked")
	@Test
	public void removeFirstMessageWithDataItem_notContained_notRemoved() {
		ArrayList<Message> messages = mockMessages(DataItem.class);
		DataItem result = Util.removeFirstMessageWithDataItem(DummyDataItemA.class, messages);
		assertEquals(1, messages.size());
		assertNull(result);
	}

	private class DummyDataItemA extends DataItem {
		@Override
		protected void fillDataFields(Builder builder) {}
	}

	@SuppressWarnings("unchecked")
	@Test
	public void removeFirstMessageWithDataItem_multipleContained_removeFirst() {
		ArrayList<Message> messages = mockMessages(DummyDataItemA.class, DataItem.class, DummyDataItemA.class);
		DataItem result = Util.removeFirstMessageWithDataItem(DummyDataItemA.class, messages);
		assertTrue(result != null);
		assertEquals(2, messages.size());
		assertTrue(messages.get(0).containsType(DataItem.class));
		assertTrue(messages.get(1).containsType(DummyDataItemA.class));
	}

	private class DummyA extends PointInTime {
		public DummyA(TimeStamp validAt) {
			super(validAt);
		}

		public DummyA(ProtoDataItem proto) {
			super(proto);
		}
	}

	private class DummyB extends PointInTime {
		public DummyB(TimeStamp validAt) {
			super(validAt);
		}

		public DummyB(ProtoDataItem proto) {
			super(proto);
		}
	}

	@Test
	public void matchMessageByTime_emptyLists_returnsEmptyList() {
		var result = Util.matchMessagesByTime(new ArrayList<Message>(), DummyA.class, DummyB.class);
		assertEquals(0, result.size());
	}

	@Test
	public void matchMessageByTime_messageCountMismatch_throws() {
		String expectedError = String.format(Util.COUNT_MISMATCH, DummyA.class, DummyB.class);

		var messages = mockPointInTimeMessages(new long[] {1, 2}, new long[] {1});
		assertThrowsMessage(IllegalArgumentException.class, expectedError,
				() -> Util.matchMessagesByTime(messages, DummyA.class, DummyB.class));

		var messages2 = mockPointInTimeMessages(new long[] {}, new long[] {1});
		assertThrowsMessage(IllegalArgumentException.class, expectedError,
				() -> Util.matchMessagesByTime(messages2, DummyA.class, DummyB.class));
	}

	/** @return a list of {@link Message}s containing mocked Messages with timesA.size() mocked {@link DummyA} items validAt timesA,
	 *         and timesB.size() {@link DummyB} items valid at times B **/
	private ArrayList<Message> mockPointInTimeMessages(long[] timesA, long[] timesB) {
		ArrayList<Message> messages = new ArrayList<>();
		for (long time : timesA) {
			Message mockMessage = mock(Message.class);
			when(mockMessage.containsType(DummyA.class)).thenReturn(true);
			when(mockMessage.getDataItemOfType(DummyA.class)).thenReturn(new DummyA(new TimeStamp(time)));
			messages.add(mockMessage);
		}
		for (long time : timesB) {
			Message mockMessage = mock(Message.class);
			when(mockMessage.containsType(DummyB.class)).thenReturn(true);
			when(mockMessage.getDataItemOfType(DummyB.class)).thenReturn(new DummyB(new TimeStamp(time)));
			messages.add(mockMessage);
		}
		return messages;
	}

	@Test
	public void matchMessageByTime_duplicateTimesFirstType_throws() {
		String expectedError = String.format(Util.TIME_DUPLICATE, DummyA.class, new TimeStamp(1));
		var messages = mockPointInTimeMessages(new long[] {1, 1}, new long[] {1, 2});
		assertThrowsMessage(IllegalArgumentException.class, expectedError,
				() -> Util.matchMessagesByTime(messages, DummyA.class, DummyB.class));
	}

	@Test
	public void matchMessageByTime_duplicateTimesSecondType_throws() {
		String expectedError = String.format(Util.TIME_DUPLICATE, DummyB.class, new TimeStamp(1));
		var messages = mockPointInTimeMessages(new long[] {1, 2}, new long[] {1, 1});
		assertThrowsMessage(IllegalArgumentException.class, expectedError,
				() -> Util.matchMessagesByTime(messages, DummyA.class, DummyB.class));
	}

	@Test
	public void matchMessageByTime_timesDoNotMatch_throws() {
		String expectedError = String.format(Util.TIME_UNMATCHED, new TimeStamp(3), DummyB.class, DummyA.class);
		var messages = mockPointInTimeMessages(new long[] {1, 2}, new long[] {1, 3});
		assertThrowsMessage(IllegalArgumentException.class, expectedError,
				() -> Util.matchMessagesByTime(messages, DummyA.class, DummyB.class));
	}

	@Test
	public void matchMessageByTime_twoMessages_mappedToSamePair() {
		var messages = mockPointInTimeMessages(new long[] {1}, new long[] {1});
		var result = Util.matchMessagesByTime(messages, DummyA.class, DummyB.class);
		assertEquals(1, result.size());
		var pair = result.get(new TimeStamp(1));
		assertEquals(1L, pair.getFirstItem().validAt.getStep());
		assertEquals(1L, pair.getSecondItem().validAt.getStep());
	}

	@Test
	public void matchMessageByTime_fourMessages_mappedToSamePairs() {
		var messages = mockPointInTimeMessages(new long[] {2, 1}, new long[] {1, 2});
		var result = Util.matchMessagesByTime(messages, DummyA.class, DummyB.class);
		assertEquals(2, result.size());
		for (long time : new long[] {1, 2}) {
			var pair = result.get(new TimeStamp(time));
			assertEquals(time, pair.getFirstItem().validAt.getStep());
			assertEquals(time, pair.getSecondItem().validAt.getStep());
		}
	}

	@ParameterizedTest
	@ValueSource(ints = {-1, 0, 2, 10})
	public void truncateIntArray_arrayEmpty_returnEmptyArray(int newLength) {
		assertEquals(0, Util.truncateIntArray(new int[] {}, newLength).length);
	}

	@ParameterizedTest
	@ValueSource(ints = {0, 2, 10})
	public void truncateIntArray_newLengthZero_returnEmptyArray(int originalLength) {
		assertEquals(0, Util.truncateIntArray(new int[originalLength], 0).length);
	}

	@Test
	public void truncateIntArray_newLengthSmaller_returnReducedSize() {
		int[] original = new int[] {0, 1, 2, 3, 4, 5};
		assertArrayEquals(new int[] {0}, Util.truncateIntArray(original, 1));
		assertArrayEquals(new int[] {0, 1, 2}, Util.truncateIntArray(original, 3));
		assertArrayEquals(new int[] {0, 1, 2, 3, 4}, Util.truncateIntArray(original, 5));
	}

	@ParameterizedTest
	@ValueSource(ints = {6, 8, 10, 100})
	public void truncateIntArray_newLengthEqualOrLarger_returnOriginal(int newLength) {
		int[] original = new int[] {0, 1, 2, 3, 4, 5};
		assertEquals(original, Util.truncateIntArray(original, newLength));
	}
}
