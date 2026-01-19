// SPDX-FileCopyrightText: 2026 German Aerospace Center <amiris@dlr.de>
//
// SPDX-License-Identifier: Apache-2.0
package agents.flexibility.dynamicProgramming.bidding;

import agents.flexibility.BidSchedule;
import agents.flexibility.dynamicProgramming.Optimiser;
import agents.flexibility.dynamicProgramming.states.StateManager.DispatchSchedule;
import de.dlr.gitlab.fame.time.TimePeriod;

/** Uses specific value of transitions derived from storage content values to calculate bids
 * 
 * @author Christoph Schimeczek */
public class StorageContentValue implements BidScheduler {
	private final double schedulingHorizonInHours;

	public StorageContentValue(double schedulingHorizonInHours) {
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

	/** @return bid price: consider opportunity costs when buying/selling energy to derive the minimum/maximum bid price */
	private double getBid(double energyDeltaInMWH, double specificValueDeltaInEURperMWH) {
		return Math.signum(energyDeltaInMWH) * specificValueDeltaInEURperMWH;
	}

	@Override
	public double getScheduleHorizonInHours() {
		return schedulingHorizonInHours;
	}
}
