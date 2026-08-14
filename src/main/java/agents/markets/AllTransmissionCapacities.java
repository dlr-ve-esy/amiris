// SPDX-FileCopyrightText: 2026 German Aerospace Center <amiris@dlr.de>
//
// SPDX-License-Identifier: Apache-2.0
package agents.markets;

import java.util.HashMap;

/** Transmission capacities between different market zones during an unspecified market period.
 * 
 * @author Christoph Schimeczek */
public class AllTransmissionCapacities {
	static final String ERR_ORIGIN_IS_TARGET = "Transmission capacities with same origin and target are disallowed.";

	/** Holds maximum transmission capacities in forwards (lower ID -> higher ID) and backwards (higherID -> lower ID) directions;
	 * utilised capacity is positive in forwards direction and negative in backwards direction. */
	private class CapacityData {
		private double maximumForwardsInMW = 0;
		private double maximumBackwardsInMW = 0;
		private double utilisedInMW = 0;

		/** Reset both maximums and the utilisation to Zero */
		public void clear() {
			maximumForwardsInMW = 0;
			maximumBackwardsInMW = 0;
			utilisedInMW = 0;
		}
	}

	/** Its child classes simplify interpretation of a linked {@link CapacityData} object */
	private abstract class Capacity {
		protected CapacityData data;

		/** Updates the {@link CapacityData} object that is to be interpreted
		 * 
		 * @param data to be linked to this type of {@link Capacity} */
		public void set(CapacityData data) {
			this.data = data;
		}

		/** Sets the maximum capacity, either in forwards or backwards direction, depending on the child class
		 * 
		 * @param maximumUtilisationInMW */
		public abstract void setMaximum(double maximumUtilisationInMW);

		/** Adds the given amount to the current transfer capacity utilisation
		 * 
		 * @param addedUtilisationInMW the amount to be added */
		public abstract void addUtilisation(double addedUtilisationInMW);

		/** Return the remaining amount of utilisation in the direction of the child class
		 * 
		 * @return can exceed the maximum capacity in that direction if utilisation towards the other direction is present */
		public abstract double getRemainingCapacityInMW();
	}

	/** Interprets the linked {@link CapacityData} in forwards direction */
	private class ForwardsCapacity extends Capacity {
		@Override
		public void setMaximum(double maximumUtilisationInMW) {
			data.maximumForwardsInMW = maximumUtilisationInMW;
		}

		@Override
		public double getRemainingCapacityInMW() {
			return data.maximumForwardsInMW - data.utilisedInMW;
		}

		@Override
		public void addUtilisation(double addedUtilisationInMW) {
			data.utilisedInMW += addedUtilisationInMW;
		}
	}

	/** Interprets the linked {@link CapacityData} in backwards direction */
	private class BackwardsCapacity extends Capacity {
		@Override
		public void setMaximum(double maximumUtilisationInMW) {
			data.maximumBackwardsInMW = maximumUtilisationInMW;
		}

		@Override
		public double getRemainingCapacityInMW() {
			return data.maximumBackwardsInMW + data.utilisedInMW;
		}

		@Override
		public void addUtilisation(double addedUtilisationInMW) {
			data.utilisedInMW -= addedUtilisationInMW;
		}
	}

	/** All data about maximum and used transmission capacities from origins to targets (forwards direction) */
	private final HashMap<Long, HashMap<Long, CapacityData>> capacities = new HashMap<>();
	/** Single instance of Capacity representing forwards direction */
	private final ForwardsCapacity forwardsCapacity = new ForwardsCapacity();
	private final BackwardsCapacity backwardsCapacity = new BackwardsCapacity();

	/** Reset all transmission capacities to Zero */
	public void reset() {
		for (var targets : capacities.values()) {
			for (var target : targets.values()) {
				target.clear();
			}
		}
	}

	/** Register maximum transmission capacities from origin to target
	 * 
	 * @param origin ID of market that sends electricity
	 * @param target ID of market that receives electricity
	 * @param transmissionCapacityInMW electric transmission capacity from origin to target */
	public void register(Long origin, Long target, double transmissionCapacityInMW) {
		Capacity capacity = get(origin, target);
		capacity.setMaximum(transmissionCapacityInMW);
	}

	/** @return {@link CapacityData} between two markets given by ID, creates market connection if necessary */
	private Capacity get(Long origin, Long target) {
		if (origin < target) {
			var capacityMap = capacities.computeIfAbsent(origin, __ -> new HashMap<Long, CapacityData>());
			var data = capacityMap.computeIfAbsent(target, __ -> new CapacityData());
			forwardsCapacity.set(data);
			return forwardsCapacity;
		} else if (origin > target) {
			var capacityMap = capacities.computeIfAbsent(target, __ -> new HashMap<Long, CapacityData>());
			var data = capacityMap.computeIfAbsent(origin, __ -> new CapacityData());
			backwardsCapacity.set(data);
			return backwardsCapacity;
		} else {
			throw new RuntimeException(ERR_ORIGIN_IS_TARGET);
		}
	}

	/** Returns still available transmission capacity from origin market to sender market; Can exceed the maximum capacity from
	 * origin to target market if currently electricity is transferred from target to origin market
	 * 
	 * @param origin ID of market that sends electricity
	 * @param target ID of market that receives electricity
	 * @return remaining transmission capacity from origin to target market */
	public double getRemainingCapacity(Long origin, Long target) {
		return get(origin, target).getRemainingCapacityInMW();
	}

	/** Reduces remaining transmission capacity from origin to target market by given additional utilisation. No checks are
	 * performed if the line capacity is actually sufficient. Thus, use {@link #getRemainingCapacity(Long, Long)} first to determine
	 * the maximum transmission that can be added.
	 * 
	 * @param origin ID of market that sends electricity
	 * @param target ID of market that receives electricity
	 * @param additionalUtilisationInMW amount of additional transmission utilisation from origin to target market */
	public void addTransmission(Long origin, Long target, double additionalUtilisationInMW) {
		get(origin, target).addUtilisation(additionalUtilisationInMW);
	}
}
