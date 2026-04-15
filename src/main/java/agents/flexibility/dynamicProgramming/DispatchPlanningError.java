// SPDX-FileCopyrightText: 2026 German Aerospace Center <amiris@dlr.de>
//
// SPDX-License-Identifier: Apache-2.0
package agents.flexibility.dynamicProgramming;

/** An error that occurred during dispatch planning
 * 
 * @author Christoph Schimeczek */
@SuppressWarnings("serial")
public class DispatchPlanningError extends Exception {
	/** Instantiate a {@link DispatchPlanningError}
	 * 
	 * @param message describing the error */
	public DispatchPlanningError(String message) {
		super(message);
	}

	/** Instantiate a {@link DispatchPlanningError}
	 * 
	 * @param message describing the error
	 * @param cause that lead to this error */
	public DispatchPlanningError(String message, Throwable cause) {
		super(message, cause);
	}
}
