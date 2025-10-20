// SPDX-FileCopyrightText: 2025 German Aerospace Center <amiris@dlr.de>
//
// SPDX-License-Identifier: Apache-2.0
package agents.flexibility.dynamicProgramming.states;

import java.util.Arrays;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import de.dlr.gitlab.fame.time.TimeSpan;

/** Handles state discretisation for energy, time
 * 
 * @author Christoph Schimeczek, Johannes Kochems */
public class StateDiscretiser {
	private static final String WARN_OUT_OF_BOUNDS = "Detected energy levels outside of feasible bounds.";
	private static final Logger logger = LoggerFactory.getLogger(StateDiscretiser.class);

	/** Used to avoid rounding errors in floating point calculation of transition steps */
	private static final double PRECISION_GUARD = 1E-5;

	private final double energyResolutionInMWH;
	private final TimeSpan timeResolution;
	private int numberOfEnergyStates;
	private int numberOfTimeStates;
	private int energyStateOffset;
	private double lowestLevelEnergyInMWH;

	private int[] allStates;
	/** Maximum amount of energy for downshift in MWh (negative) */
	private double currentDownshiftEnergyLimitInMWH;
	/** Maximum amount of energy for upshift in MWh (positive) */
	private double currentUpshiftEnergyLimitInMWH;

	public StateDiscretiser(double energyResolutionInMWH, TimeSpan timeResolution) {
		this.energyResolutionInMWH = energyResolutionInMWH;
		this.timeResolution = timeResolution;
	}

	public void setBoundaries(double[] energyBoundaries, long maxShiftTimeInSteps) {
		energyStateOffset = -energyToCeilIndex(energyBoundaries[0], 0.);
		final int highestStep = energyToFloorIndex(energyBoundaries[1], 0.);
		lowestLevelEnergyInMWH = -energyStateOffset * energyResolutionInMWH;
		numberOfEnergyStates = highestStep + energyStateOffset + 1;
		numberOfTimeStates = maxShiftTimeInSteps > 0 ? (int) (maxShiftTimeInSteps / timeResolution.getSteps()) : 1;
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
		allStates = new int[getNumberOfExistingStates()];
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

	private int getNumberOfExistingStates() {
		if (numberOfTimeStates == 1) {
			return numberOfEnergyStates;
		} else {
			return (numberOfEnergyStates - 1) * (numberOfTimeStates - 1) + 1;
		}
	}

	public void setShiftEnergyLimits(double currentDownshiftEnergyLimitInMWH, double currentUpshiftEnergyLimitInMWH) {
		this.currentDownshiftEnergyLimitInMWH = currentDownshiftEnergyLimitInMWH;
		this.currentUpshiftEnergyLimitInMWH = currentUpshiftEnergyLimitInMWH;
	}

	/** Returns number of discretisation steps equivalent to given energy delta in MWh (rounded down)
	 * 
	 * @param energyDeltaInMWH positive energy delta in MWh
	 * @return number of discretisation steps equivalent to given energy delta in MWh (rounded down) */
	public int discretiseEnergyDelta(double energyDeltaInMWH) {
		return (int) Math.floor(energyDeltaInMWH / energyResolutionInMWH + PRECISION_GUARD);
	}

	public double energyIndexToEnergy(int energyIndex) {
		return energyIndex * energyResolutionInMWH + lowestLevelEnergyInMWH;
	}

	public int[] getAllAvailableStates() {
		return allStates;
	}

	public double getEnergyOfStateInMWH(int stateIndex) {
		return (stateIndex % numberOfEnergyStates - energyStateOffset) * energyResolutionInMWH;
	}

	public int getEnergyIndexDelta(int initialStateIndex, int finalStateIndex) {
		return (finalStateIndex % numberOfEnergyStates - initialStateIndex % numberOfEnergyStates);
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
					double energyToBalanceInMWH = energyIndexToEnergy(currentEnergyIndex);
					if (energyToBalanceInMWH > 0.) {
						double downshiftShareForBalance = Math.min(1., energyToBalanceInMWH / -currentDownshiftEnergyLimitInMWH);
						if (energyIndexToEnergy(energyIndex) > (1 - downshiftShareForBalance) * currentUpshiftEnergyLimitInMWH) {
							continue;
						}
					} else {
						double upshiftShareForBalance = Math.min(1., -energyToBalanceInMWH / currentUpshiftEnergyLimitInMWH);
						if (energyIndexToEnergy(energyIndex) < (1 - upshiftShareForBalance) * currentDownshiftEnergyLimitInMWH) {
							continue;
						}
					}
					followUpShiftTime = 1;
				}
			} else {
				followUpShiftTime = 1;
			}
			followUpStates[arrayIndex] = followUpShiftTime * numberOfEnergyStates + energyIndex;
			arrayIndex++;
		}
		if (arrayIndex < followUpStates.length) {
			if (arrayIndex > 0) {
				return Arrays.copyOfRange(followUpStates, 0, arrayIndex - 1);
			} else {
				return new int[0];
			}
		}
		return followUpStates;
	}

	public boolean isProlonged(int initialStateIndex, int finalStateIndex) {
		int finalTime = finalStateIndex / numberOfEnergyStates;
		int offsettedInitialEnergyState = initialStateIndex % numberOfEnergyStates - energyStateOffset;
		int offsettedFinalEnergyState = finalStateIndex % numberOfEnergyStates - energyStateOffset;
		return finalTime == 1 && (Math.signum(offsettedInitialEnergyState) == Math.signum(offsettedFinalEnergyState));
	}

	/** @param energyAmountInMWH for which to retrieve nearest energy index
	 * @return closest valid energy index corresponding to given energy level */
	public int energyToNearestEnergyIndex(double energyAmountInMWH) {
		int nearestIndex = (int) Math.round(energyAmountInMWH / energyResolutionInMWH) + energyStateOffset;
		int correctedIndex = Math.max(0, Math.min(nearestIndex, numberOfEnergyStates - 1));
		if (nearestIndex != correctedIndex) {
			logger.error(WARN_OUT_OF_BOUNDS);
		}
		return correctedIndex;
	}

	/** @param shiftTimeInSteps for which to retrieve nearest shift time index
	 * @return closest valid shift time index corresponding to given shift time in steps */
	public int roundToNearestShiftTimeIndex(long shiftTimeInSteps) {
		int atLeast = (int) (shiftTimeInSteps / timeResolution.getSteps());
		int increase = (int) (((shiftTimeInSteps % timeResolution.getSteps()) * 2) / timeResolution.getSteps());
		return atLeast + increase;
	}

	/** @param energyLevelIndex to evaluate
	 * @param shiftTimeIndex to evaluate
	 * @return state index derived from given energy level and shift time indices */
	public int getStateIndex(int energyLevelIndex, int shiftTimeIndex) {
		return shiftTimeIndex * numberOfEnergyStates + energyLevelIndex;
	}

	/** @param initialStateIndex to evaluate
	 * @param finalStateIndex to evaluate
	 * @return energy delta in MWh from initial to final state */
	public double calcEnergyDeltaInMWH(int initialStateIndex, int finalStateIndex) {
		return getEnergyIndexDelta(initialStateIndex, finalStateIndex) * energyResolutionInMWH;
	}

	/** @param stateIndex from which to derive shift time
	 * @return corresponding shift time index for given state index */
	public int calcShiftTimeIndexFromStateIndex(int stateIndex) {
		return stateIndex / numberOfEnergyStates;
	}

	/** @return number of all states including those that are technically not valid, i.e.
	 *         <ul>
	 *         <li>states with energy level equal to zero, but shift time above zero</li>
	 *         <li>states with energy level not equal to zero, but shift time zero</li>
	 *         </ul>
	 */
	public int getStateCount() {
		return numberOfEnergyStates * numberOfTimeStates;
	}
}
