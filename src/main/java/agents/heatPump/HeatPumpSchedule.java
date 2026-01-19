// SPDX-FileCopyrightText: 2026 German Aerospace Center <amiris@dlr.de>
//
// SPDX-License-Identifier: Apache-2.0
package agents.heatPump;

import agents.flexibility.BidSchedule;
import de.dlr.gitlab.fame.time.TimePeriod;
import de.dlr.gitlab.fame.time.TimeStamp;

/** Represents a dispatch schedule for flexible heat pumps
 * 
 * @author Christoph Schimeczek, Evelyn Sperber */
public class HeatPumpSchedule extends BidSchedule {
	private final double planningResultionInC;

	/** Creates a {@link HeatPumpSchedule}
	 * 
	 * @param timePeriod defines first time at which this schedule is valid and length of each period
	 * @param durationInPeriods number of time periods covered by this schedule, i.e. multiples of timeSegment durations
	 * @param planningResultionInC discrete resolution of room temperature for dynamic programming */
	public HeatPumpSchedule(TimePeriod timePeriod, int durationInPeriods, double planningResultionInC) {
		super(timePeriod, durationInPeriods);
		this.planningResultionInC = planningResultionInC;
	}

	@Override
	protected boolean energyLevelWithinTolerance(TimeStamp time, double temperatureInC) {
		double plannedTemperatureInC = expectedInitialInternalEnergyPerPeriodInMWH[calcElementInSchedule(time)];
		double absoluteTemperatureDeviationFromSchedule = Math.abs(plannedTemperatureInC - temperatureInC);
		return !(absoluteTemperatureDeviationFromSchedule > planningResultionInC / 2.);
	}
}
