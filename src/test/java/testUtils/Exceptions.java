// SPDX-FileCopyrightText: 2026 German Aerospace Center <amiris@dlr.de>
//
// SPDX-License-Identifier: Apache-2.0
package testUtils;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.function.Executable;

/** Utility functions to test that exceptions are thrown with proper error messages
 *
 * @author Christoph Schimeczek */
public final class Exceptions {
	/** asserts that given function throws an Exception of the given type containing the given message in its error message */
	public static void assertThrowsMessage(Class<? extends Exception> exceptionType, String message,
			Executable runnable) {
		Exception exception = assertThrows(exceptionType, runnable);
		assertThat(exception.getMessage(), containsString(message));
	}

	/** asserts that given function throws a RuntimeException containing the given message in its error message */
	public static void assertThrowsFatalMessage(String message, Executable runnable) {
		assertThrowsMessage(RuntimeException.class, message, runnable);
	}
}
