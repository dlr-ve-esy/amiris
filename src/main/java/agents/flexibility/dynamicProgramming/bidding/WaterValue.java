// SPDX-FileCopyrightText: 2025 German Aerospace Center <amiris@dlr.de>
//
// SPDX-License-Identifier: Apache-2.0
package agents.flexibility.dynamicProgramming.bidding;

import agents.flexibility.BidSchedule;
import agents.flexibility.dynamicProgramming.Optimiser;
import agents.flexibility.dynamicProgramming.states.StateManager.DispatchSchedule;
import de.dlr.gitlab.fame.time.TimePeriod;

/** Uses specific value of transitions derived from water values to calculate bids
 * 
 * @author Christoph Schimeczek */
public class WaterValue implements BidScheduler {
	private final double schedulingHorizonInHours;

	public WaterValue(double schedulingHorizonInHours) {
		this.schedulingHorizonInHours = schedulingHorizonInHours;
	}

	@Override
	public BidSchedule createBidSchedule(TimePeriod startingTime, DispatchSchedule schedule) {
		int numberOfScheduleSteps = Optimiser.calcHorizonInPeriodSteps(startingTime, schedulingHorizonInHours);
		BidSchedule bidSchedule = new BidSchedule(startingTime, numberOfScheduleSteps);

		double[] biddingPricePerPeriodInEURperMWH = new double[numberOfScheduleSteps];
		for (int i = 0; i < numberOfScheduleSteps; i++) {

			biddingPricePerPeriodInEURperMWH[i] = getBid(schedule.externalEnergyDeltasInMWH[i],
					schedule.specificValuesInEURperMWH[i]);
		}
		bidSchedule.setRequestedEnergyPerPeriod(schedule.externalEnergyDeltasInMWH);
		bidSchedule.setBidsScheduleInEURperMWH(biddingPricePerPeriodInEURperMWH);
		bidSchedule.setExpectedInitialInternalEnergyScheduleInMWH(schedule.initialInternalEnergiesInMWH);
		return bidSchedule;
	}

	/** @return bid price: recover value losses when selling energy by placing the as minimum bid price */
	private double getBid(double energyDeltaInMWH, double specificValueDeltaInEURperMWH) {
		return Math.signum(energyDeltaInMWH) * specificValueDeltaInEURperMWH;
	}

	@Override
	public double getScheduleHorizonInHours() {
		return schedulingHorizonInHours;
	}
}
