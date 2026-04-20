// SPDX-FileCopyrightText: 2025-2026 German Aerospace Center <amiris@dlr.de>
//
// SPDX-License-Identifier: Apache-2.0
package testUtils;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.List;
import nl.altindag.log.LogCaptor;

/** Handles checks for logged events from one class */
public class LogChecker {
	private final LogCaptor logCaptor;

	public LogChecker(Class<?> clas) {
		logCaptor = LogCaptor.forClass(clas);
	}

	/** Remove any logged events */
	public void clear() {
		logCaptor.clearLogs();
	}

	/** Returns true if any message was logged containing the given String at <b>any</b> logging level at the associated class.
	 * 
	 * @param string to search for
	 * @return true if message was logged in the given class */
	public boolean searchLogsFor(String string) {
		List<String> logs = logCaptor.getLogs();
		for (String loggedMessage : logs) {
			if (loggedMessage.contains(string)) {
				return true;
			}
		}
		return false;
	}

	/** Asserts that given String was logged in any message at <b>any</b> logging level at the associated class.
	 * 
	 * @param string to search for */
	public void assertLogsContain(String string) {
		assertTrue(searchLogsFor(string));
	}

	/** Asserts that given String was <b>not</b> logged in any message at <b>any</b> logging level at the associated class.
	 * 
	 * @param string to search for */
	public void assertLogsDoNotContain(String string) {
		assertFalse(searchLogsFor(string));
	}

	/** Ends log capturing */
	public void close() {
		logCaptor.close();
	}
}
