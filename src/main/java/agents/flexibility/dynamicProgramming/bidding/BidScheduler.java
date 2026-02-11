// SPDX-FileCopyrightText: 2025-2026 German Aerospace Center <amiris@dlr.de>
//
// SPDX-License-Identifier: Apache-2.0
package agents.flexibility.dynamicProgramming.bidding;

import agents.flexibility.BidSchedule;
import agents.flexibility.dynamicProgramming.states.StateManager.DispatchSchedule;
import de.dlr.gitlab.fame.time.TimePeriod;

/** Creates {@link BidSchedule}s
 * 
 * @author Johannes Kochems, Felix Nitsch, Christoph Schimeczek */
public interface BidScheduler {

	/** Creates bidding schedule based on given dispatch schedule
	 * 
	 * @param startingTime first time period in the schedule
	 * @param schedule dispatch schedule that serves as basis for bidding schedule
	 * @return created bidding schedule */
	BidSchedule createBidSchedule(TimePeriod startingTime, DispatchSchedule schedule);

	/** Returns duration of schedules
	 * 
	 * @return duration of schedules */
	double getScheduleHorizonInHours();
}
