// SPDX-FileCopyrightText: 2025-2026 German Aerospace Center <amiris@dlr.de>
//
// SPDX-License-Identifier: Apache-2.0
package agents.forecast;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static testUtils.Exceptions.assertThrowsMessage;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class UtilTest {
	TreeMap<Long, Double> a;
	TreeMap<Long, Double> b;
	TreeMap<Long, Double> c;
	List<TreeMap<Long, Double>> functions;

	@BeforeEach
	public void setUp() {
		a = new TreeMap<>();
		b = new TreeMap<>();
		c = new TreeMap<>();
		functions = new ArrayList<>();
	}

	@Test
	public void averageValues_noList_throws() {
		assertThrowsMessage(RuntimeException.class, Util.EMPTY_FUNCTION, () -> Util.averageValues(null));
	}

	@Test
	public void averageValues_emptyList_throws() {
		assertThrowsMessage(RuntimeException.class, Util.EMPTY_FUNCTION, () -> Util.averageValues(new ArrayList<>()));
	}

	@Test
	public void averageValues_emptyFunction_returnsEmptyAverage() {
		functions.add(a);
		assertTrue(Util.averageValues(functions).size() == 0);
	}

	@Test
	public void averageValues_oneFunction_returnInputEquivalent() {
		addEntries(a, 1.0, 1.0, 2.0, 3.0);
		functions.add(a);
		var result = Util.averageValues(functions);
		assertEqualFunctions(a, result);
	}

	/** sets given values for given map using an integer index */
	private void addEntries(TreeMap<Long, Double> map, double... values) {
		for (long i = 0; i < values.length; i++) {
			map.put(i, values[(int) i]);
		}
	}

	/** asserts that given two maps have equal content */
	private void assertEqualFunctions(TreeMap<Long, Double> expected, TreeMap<Long, Double> test) {
		assertEquals(expected.size(), test.size());
		for (var key : expected.keySet()) {
			assertEquals(expected.get(key), test.get(key), 1E-10);
		}
	}

	@Test
	public void averageValues_twoFunction_returnAverage() {
		addEntries(a, 1.0, 1.0, 2.0, 3.0);
		addEntries(b, 3.0, 5.0, 4.0, 7.0);
		addEntries(c, 2.0, 3.0, 3.0, 5.0);
		functions.add(a);
		functions.add(b);
		var result = Util.averageValues(functions);
		assertEqualFunctions(c, result);
	}
}
