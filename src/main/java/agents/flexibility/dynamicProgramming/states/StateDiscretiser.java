// SPDX-FileCopyrightText: 2025 German Aerospace Center <amiris@dlr.de>
//
// SPDX-License-Identifier: Apache-2.0
package agents.flexibility.dynamicProgramming.states;

import de.dlr.gitlab.fame.time.TimeSpan;

/** Handles state discretisation for energy, time
 * 
 * @author Christoph Schimeczek, Johannes Kochems */
public class StateDiscretiser {
	/** Used to avoid rounding errors in floating point calculation of transition steps */
	private static final double PRECISION_GUARD = 1E-5;

	private final double energyResolutionInMWH;
	private final TimeSpan timeResolution;
	private int numberOfEnergyStates;
	private int numberOfTimeStates;
	private int energyStateOffset;
	private double lowestLevelEnergyInMWH;

	private int[] allStates;

	public StateDiscretiser(double energyResolutionInMWH, TimeSpan timeResolution) {
		this.energyResolutionInMWH = energyResolutionInMWH;
		this.timeResolution = timeResolution;
	}

	public void setBoundaries(double[] energyBoundaries, long maxShiftTimeInSteps) {
		energyStateOffset = energyToCeilIndex(energyBoundaries[0], 0.);
		final int highestStep = energyToFloorIndex(energyBoundaries[1], 0.);
		lowestLevelEnergyInMWH = energyStateOffset * energyResolutionInMWH;
		numberOfEnergyStates = highestStep - energyStateOffset + 1;
		numberOfTimeStates = maxShiftTimeInSteps > 0 ? (int) (maxShiftTimeInSteps / timeResolution.getSteps()) + 1 : 1;
		allocateStates();
	}

	/** @return next lower index corresponding to given energy level */
	private int energyToFloorIndex(double energyAmountInMWH, double lowestLevelEnergyInMWH) {
		double energyLevel = Math.floor(energyAmountInMWH / energyResolutionInMWH + PRECISION_GUARD)
				* energyResolutionInMWH;
		return (int) Math.round((energyLevel - lowestLevelEnergyInMWH) / energyResolutionInMWH);
	}

	/** @return next higher index corresponding to given energy level */
	private int energyToCeilIndex(double energyAmountInMWH, double lowestLevelEnergyInMWH) {
		double energyLevel = Math.ceil(energyAmountInMWH / energyResolutionInMWH - PRECISION_GUARD) * energyResolutionInMWH;
		return (int) Math.round((energyLevel - lowestLevelEnergyInMWH) / energyResolutionInMWH);
	}

	private void allocateStates() {
		allStates = new int[getNumberOfStates()];
		allStates[0] = energyStateOffset;
		int arrayIndex = 1;
		for (int time = 1; time < numberOfTimeStates; time++) {
			for (int energy = 0; energy < numberOfEnergyStates; energy++) {
				if (energy == energyStateOffset) {
					continue;
				}
				allStates[arrayIndex] = time * numberOfEnergyStates + energy;
				arrayIndex++;
			}
		}

	}

	public int getNumberOfStates() {
		if (numberOfTimeStates == 1) {
			return numberOfEnergyStates;
		} else {
			return (numberOfEnergyStates - 1) * (numberOfTimeStates - 1) + 1;
		}
	}

	/** Returns number of discretisation steps equivalent to given energy delta in MWh (rounded down)
	 * 
	 * @param energyDeltaInMWH positive energy delta in MWh
	 * @return number of discretisation steps equivalent to given energy delta in MWh (rounded down) */
	public int discretiseEnergyDelta(double energyDeltaInMWH) {
		return (int) Math.floor(energyDeltaInMWH / energyResolutionInMWH + PRECISION_GUARD);
	}

	public double energyIndexToEnergy(int energyIndex) {
		return energyIndex * energyResolutionInMWH - lowestLevelEnergyInMWH;
	}

	public int[] getAllAvailableStates() {
		return allStates;
	}

	public double getEnergyOfStateInMWH(int stateIndex) {
		return (stateIndex % numberOfEnergyStates - energyStateOffset) * energyResolutionInMWH;
	}

	public int[] getFollowUpStates(int initialStateIndex, double lowestEnergyContentInMWH,
			double highestEnergyContentInMWH) {
		int lowestEnergyIndex = energyToCeilIndex(lowestEnergyContentInMWH, lowestLevelEnergyInMWH);
		int highestEnergyIndex = energyToFloorIndex(highestEnergyContentInMWH, lowestLevelEnergyInMWH);
		int currentEnergyIndex = initialStateIndex % numberOfEnergyStates;
		int currentShiftTime = initialStateIndex / numberOfEnergyStates;
		int[] followUpStates = new int[highestEnergyIndex - lowestEnergyIndex + 1];
		int arrayIndex = 0;
		int followUpShiftTime;
		for (int energyIndex = lowestEnergyIndex; energyIndex <= highestEnergyIndex; energyIndex++) {
			if (energyIndex == energyStateOffset) {
				followUpShiftTime = 0;
			} else if (Math.signum(energyIndex - energyStateOffset) == Math.signum(currentEnergyIndex - energyStateOffset)) {
				followUpShiftTime = currentShiftTime + 1;
				if (followUpShiftTime >= numberOfTimeStates) {
					followUpShiftTime = 1;
				}
			} else {
				followUpShiftTime = 1;
			}
			followUpStates[arrayIndex] = followUpShiftTime * numberOfEnergyStates + energyIndex;
			arrayIndex++;
		}
		return followUpStates;
	}

	public boolean isProlonged(int initialStateIndex, int finalStateIndex) {
		int finalTime = finalStateIndex / numberOfEnergyStates;
		int offsettedInitialEnergyState = initialStateIndex % numberOfEnergyStates - energyStateOffset;
		int offsettedFinalEnergyState = finalStateIndex % numberOfEnergyStates - energyStateOffset;
		return finalTime == 1 && (Math.signum(offsettedInitialEnergyState) == Math.signum(offsettedFinalEnergyState));
	}
}
