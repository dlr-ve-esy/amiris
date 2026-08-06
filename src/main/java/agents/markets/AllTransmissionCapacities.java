package agents.markets;

import java.util.HashMap;

/** Transmission capacities between different market zones during an unspecified market period.
 * 
 * @author Christoph Schimeczek */
public class AllTransmissionCapacities {
	static final String ERR_ORIGIN_IS_TARGET = "Transmission capacities with same origin and target are disallowed.";

	private class CapacityData {
		private double maximumForwardsInMW = 0;
		private double maximumBackwardsInM = 0;
		private double utilisedInMW = 0;

		/** Reset to Zero */
		public void clear() {
			maximumForwardsInMW = 0;
			maximumBackwardsInM = 0;
			utilisedInMW = 0;
		}
	}

	private abstract class Capacity {
		protected CapacityData data;

		public void set(CapacityData data) {
			this.data = data;
		}

		public abstract void setMaximum(double maximumUtilisationInMW);

		public abstract void addUtilisation(double addedUtilisationInMW);

		public abstract double getRemainingCapacityInMW();
	}

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

	private class BackwardsCapacity extends Capacity {
		@Override
		public void setMaximum(double maximumUtilisationInMW) {
			data.maximumBackwardsInM = maximumUtilisationInMW;
		}

		@Override
		public double getRemainingCapacityInMW() {
			return data.maximumBackwardsInM + data.utilisedInMW;
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
	public void clear() {
		for (var targets : capacities.values()) {
			for (var target : targets.values()) {
				target.clear();
			}
		}
	}

	/** Register maximum transmission capacities from origin to target */
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

	/** @return remaining transmission capacity from origin to target market */
	public double getRemainingCapacity(Long origin, Long target) {
		return get(origin, target).getRemainingCapacityInMW();
	}

	/** Reduces remaining transmission capacity from origin to target market by given additional utilisation */
	public void addTransmission(Long origin, Long target, double additionalUtilisationInMW) {
		get(origin, target).addUtilisation(additionalUtilisationInMW);
	}
}
