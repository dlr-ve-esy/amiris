// SPDX-FileCopyrightText: 2025 German Aerospace Center <amiris@dlr.de>
//
// SPDX-License-Identifier: Apache-2.0
package agents.flexibility.dynamicProgramming.states;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import de.dlr.gitlab.fame.time.TimeSpan;
import util.Util;

/** Handles discretisation of states with respect to a device's energy (State of Charge), and time out of balance (shift time).
 * 
 * @author Christoph Schimeczek, Johannes Kochems */
public class StateDiscretiser {
	static final String ERR_INVALID_ENERGY_RESOLUTION = "Energy resolution must be a positive value but was: ";
	static final String ERR_INVALID_TIME_RESOLUTION = "Time resolution must be positive value but was: ";
	static final String ERR_INVERTED_ENERGY_LIMITS = ": Minimum energy content larger than maximum energy content: ";
	static final String WARN_OUT_OF_BOUNDS = "Detected energy levels outside of feasible bounds.";
	private static final Logger logger = LoggerFactory.getLogger(StateDiscretiser.class);

	/** Used to avoid rounding errors in floating point calculations of energy levels */
	private static final double PRECISION_GUARD = 1E-5;

	private final double energyResolutionInMWH;
	private final TimeSpan timeResolution;
	private int numberOfEnergyStates;
	private int numberOfTimeStates;
	private int energyStateOffset;
	private double lowestLevelEnergyInMWH;
	private boolean considerTimeConstraint;

	/** Indices of all technically possible states ignoring impossible states (e.g. states out of balance with zero shift time) */
	private int[] allStates;
	/** Maximum amount of energy for downshift in MWh (negative) */
	private double currentDownshiftEnergyLimitInMWH;
	/** Maximum amount of energy for upshift in MWh (positive) */
	private double currentUpshiftEnergyLimitInMWH;

	/** Instantiates a new {@link StateDiscretiser}
	 * 
	 * @param energyResolutionInMWH energy delta between two neighbouring energy states
	 * @param timeResolution time delta between two neighbouring time states */
	public StateDiscretiser(double energyResolutionInMWH, TimeSpan timeResolution) {
		if (energyResolutionInMWH <= 0) {
			logger.error(ERR_INVALID_ENERGY_RESOLUTION + energyResolutionInMWH);
			throw new RuntimeException(ERR_INVALID_ENERGY_RESOLUTION + energyResolutionInMWH);
		}
		this.energyResolutionInMWH = energyResolutionInMWH;
		if (timeResolution.getSteps() < 1) {
			logger.error(ERR_INVALID_TIME_RESOLUTION + timeResolution);
			throw new RuntimeException(ERR_INVALID_TIME_RESOLUTION + timeResolution);
		}
		this.timeResolution = timeResolution;
	}

	/** Sets boundaries for energy and time
	 * 
	 * @param energyBoundariesInMWH two doubles values representing minimum and maximum energy content of the associated device
	 * @param maxShiftTime maximum allowed time span for the shift time; use 0 if only energy states shall be used */
	public void setBoundaries(double[] energyBoundariesInMWH, TimeSpan maxShiftTime) {
		assertLimitsNotInverted(energyBoundariesInMWH);
		energyStateOffset = -energyToCeilIndex(energyBoundariesInMWH[0], 0.);
		final int highestStep = energyToFloorIndex(energyBoundariesInMWH[1], 0.);
		lowestLevelEnergyInMWH = -energyStateOffset * energyResolutionInMWH;
		numberOfEnergyStates = highestStep + energyStateOffset + 1;
		final long maxShiftTimeSteps = maxShiftTime.getSteps();
		considerTimeConstraint = maxShiftTimeSteps > 0;
		numberOfTimeStates = maxShiftTimeSteps > 0 ? (int) (maxShiftTimeSteps / timeResolution.getSteps()) + 1 : 1;
		updateListOfStates();
	}

	/** @throws RuntimeException if lower limit exceeds upper limit */
	private void assertLimitsNotInverted(double[] energyBoundariesInMWH) {
		if (energyBoundariesInMWH[0] > energyBoundariesInMWH[1]) {
			logger.error(energyBoundariesInMWH[0] + ERR_INVERTED_ENERGY_LIMITS + energyBoundariesInMWH[1]);
			throw new RuntimeException(energyBoundariesInMWH[0] + ERR_INVERTED_ENERGY_LIMITS + energyBoundariesInMWH[1]);
		}
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

	/** Allocates and assign {@link #allStates} */
	private void updateListOfStates() {
		if (considerTimeConstraint) {
			allocateEnergyAndTimeStates();
		} else {
			allocateEnergyStates();
		}
	}

	/** Updates list of all states - use only, if {@link #considerTimeConstraint} applies */
	private void allocateEnergyAndTimeStates() {
		allStates = new int[(numberOfEnergyStates - 1) * (numberOfTimeStates - 1) + 1];
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

	/** Updates list of all states - use only, if time states are disregarded */
	private void allocateEnergyStates() {
		allStates = new int[numberOfEnergyStates];
		for (int energyIndex = 0; energyIndex < numberOfEnergyStates; energyIndex++) {
			allStates[energyIndex] = energyIndex;
		}
	}

	/** Returns indices of technically possible states ignoring impossible states, e.g., states out of balance with zero shift time.
	 * 
	 * @return all indices of technically possible states */
	public int[] getAllAvailableStates() {
		return allStates;
	}

	/** Returns number of discretisation steps equivalent to given energy delta in MWh (rounded down).
	 * 
	 * @param energyDeltaInMWH positive energy delta in MWh
	 * @return number of discretisation steps equivalent to given energy delta in MWh (rounded down) */
	public int discretiseEnergyDelta(double energyDeltaInMWH) {
		return (int) Math.floor(energyDeltaInMWH / energyResolutionInMWH + PRECISION_GUARD);
	}

	/** Returns energy in MWh corresponding to the given index of an <b>energy state</b>.
	 * 
	 * @param energyIndex index of the <b>energy</b> state
	 * @return energy in MWh corresponding to the given index */
	public double energyIndexToEnergyInMWH(int energyIndex) {
		return energyIndex * energyResolutionInMWH + lowestLevelEnergyInMWH;
	}

	/** Returns energy in MWh corresponding to the given index of a <b>time-and-energy state</b>.
	 * 
	 * @param stateIndex index of the <b>time-and-energy state</b>
	 * @return energy in MWh corresponding to the given index */
	public double getEnergyOfStateInMWH(int stateIndex) {
		return (stateIndex % numberOfEnergyStates - energyStateOffset) * energyResolutionInMWH;
	}

	/** Returns delta in <b>energy indices</b> for the given two <b>time-and-energy state</b> indices.
	 * 
	 * @param initialStateIndex <b>time-and-energy state</b> index of the initial state
	 * @param finalStateIndex <b>time-and-energy state</b> index of the final state
	 * @return final - initial <b>energy index</b> */
	public int getEnergyIndexDelta(int initialStateIndex, int finalStateIndex) {
		return (finalStateIndex % numberOfEnergyStates - initialStateIndex % numberOfEnergyStates);
	}

	/** Sets maximum energy delta for shifts in down and up direction that can be achieved by the associated device within a time
	 * span matching {@link #timeResolution}. Required only if <b>time constraints</b> are considered.
	 * 
	 * @param currentDownshiftEnergyLimitInMWH maximum energy delta for shifting down in MWh
	 * @param currentUpshiftEnergyLimitInMWH maximum energy delta for shifting up in MWh */
	public void setShiftEnergyDeltaLimits(double currentDownshiftEnergyLimitInMWH,
			double currentUpshiftEnergyLimitInMWH) {
		this.currentDownshiftEnergyLimitInMWH = currentDownshiftEnergyLimitInMWH;
		this.currentUpshiftEnergyLimitInMWH = currentUpshiftEnergyLimitInMWH;
	}

	/** Returns <b>time-and-energy state</b> indices of states that could follow the given initial <b>time-and-energy state</b>,
	 * considering given minimum and maximum of the follow-up energy content in MWh.
	 * 
	 * @param initialStateIndex <b>time-and-energy state</b> index of the initial state
	 * @param lowestNextEnergyInMWH minimum energy content in MWh of the final state considering the initial state's energy
	 * @param highestNextEnergyInMWH maximum energy content in MWh of the final state considering the initial state's energy
	 * @return all state indices that could follow the given initial state */
	public int[] getFollowUpStates(int initialStateIndex, double lowestNextEnergyInMWH, double highestNextEnergyInMWH) {
		final int lowestEnergyIndex = energyToCeilIndex(lowestNextEnergyInMWH, lowestLevelEnergyInMWH);
		final int highestEnergyIndex = energyToFloorIndex(highestNextEnergyInMWH, lowestLevelEnergyInMWH);
		if (considerTimeConstraint) {
			return addEnergyAndTimeFollowUps(lowestEnergyIndex, highestEnergyIndex, initialStateIndex);
		} else {
			return addEnergyFollowUps(lowestEnergyIndex, highestEnergyIndex);
		}
	}

	/** Set follow up state indices considering energy and shift time constraints */
	private int[] addEnergyAndTimeFollowUps(int lowestEnergyIndex, int highestEnergyIndex,
			int initialStateIndex) {
		final int[] followUpStates = new int[highestEnergyIndex - lowestEnergyIndex + 1];
		final int currentEnergyIndex = initialStateIndex % numberOfEnergyStates;
		final int currentShiftTime = initialStateIndex / numberOfEnergyStates;
		int arrayIndex = 0;
		for (int energyIndex = lowestEnergyIndex; energyIndex <= highestEnergyIndex; energyIndex++) {
			final int followUpShiftTime = calcNextShiftTime(currentShiftTime, currentEnergyIndex, energyIndex);
			if (followUpShiftTime >= 0) {
				followUpStates[arrayIndex] = followUpShiftTime * numberOfEnergyStates + energyIndex;
				arrayIndex++;
			}
		}
		return Util.truncateIntArray(followUpStates, arrayIndex);
	}

	/** @return next shift time for given current shift time, as well as current and next energy index; returns -1 if state cannot
	 *         be reached within shift time and prolonging constraints */
	private int calcNextShiftTime(int currentShiftTime, int currentEnergyIndex, int energyIndex) {
		if (energyIndex == energyStateOffset) {
			return 0;
		} else if (Math.signum(energyIndex - energyStateOffset) == Math.signum(currentEnergyIndex - energyStateOffset)) {
			final int followUpShiftTime = currentShiftTime + 1;
			if (followUpShiftTime >= numberOfTimeStates) {
				return isProlongableTransition(currentEnergyIndex, energyIndex) ? 1 : -1;
			} else {
				return followUpShiftTime;
			}
		} else {
			return 1;
		}
	}

	/** @return true if provided energy transition could be achieved with a prolonging action under current energy shift
	 *         constraints */
	private boolean isProlongableTransition(int currentEnergyIndex, int energyIndex) {
		final double energyToBalanceInMWH = energyIndexToEnergyInMWH(currentEnergyIndex);
		final double targetEnergyInMWH = energyIndexToEnergyInMWH(energyIndex);
		if (energyToBalanceInMWH > 0.) {
			double downshiftShareForBalance = Math.min(1., energyToBalanceInMWH / -currentDownshiftEnergyLimitInMWH);
			return targetEnergyInMWH <= (1 - downshiftShareForBalance) * currentUpshiftEnergyLimitInMWH;
		} else {
			double upshiftShareForBalance = Math.min(1., -energyToBalanceInMWH / currentUpshiftEnergyLimitInMWH);
			return targetEnergyInMWH >= (1 - upshiftShareForBalance) * currentDownshiftEnergyLimitInMWH;
		}
	}

	/** Set follow up state indices considering only energy indices due to lack of shift time constraints */
	private int[] addEnergyFollowUps(int lowestEnergyIndex, int highestEnergyIndex) {
		int[] followUpStates = new int[highestEnergyIndex - lowestEnergyIndex + 1];
		int arrayIndex = 0;
		for (int energyIndex = lowestEnergyIndex; energyIndex <= highestEnergyIndex; energyIndex++) {
			followUpStates[arrayIndex] = energyIndex;
			arrayIndex++;
		}
		return followUpStates;
	}

	/** Checks if the given final state that is reached from the given initial state is a result of prolonging
	 * 
	 * @param initialStateIndex <b>time-and-energy state</b> index of the initial state
	 * @param finalStateIndex <b>time-and-energy state</b> index of the final state
	 * @return True, if the given final state is a result of prolonging; false otherwise */
	public boolean isProlonged(int initialStateIndex, int finalStateIndex) {
		int finalTime = finalStateIndex / numberOfEnergyStates;
		int offsettedInitialEnergyState = initialStateIndex % numberOfEnergyStates - energyStateOffset;
		int offsettedFinalEnergyState = finalStateIndex % numberOfEnergyStates - energyStateOffset;
		return finalTime == 1 && (Math.signum(offsettedInitialEnergyState) == Math.signum(offsettedFinalEnergyState));
	}

	/** Returns closest <b>valid</b> <b>energy state</b> index of a given energy content in MWh.
	 * 
	 * @param energyContentInMWH for which to retrieve nearest energy index
	 * @return closest valid <b>energy state</b> index corresponding to given energy content */
	public int energyToNearestEnergyIndex(double energyContentInMWH) {
		int nearestIndex = (int) Math.round(energyContentInMWH / energyResolutionInMWH) + energyStateOffset;
		int correctedIndex = Math.max(0, Math.min(nearestIndex, numberOfEnergyStates - 1));
		if (nearestIndex != correctedIndex) {
			logger.error(WARN_OUT_OF_BOUNDS);
		}
		return correctedIndex;
	}

	/** Returns closest valid shift time index corresponding to given shift time in steps
	 * 
	 * @param shiftTimeInSteps for which to retrieve nearest shift time index
	 * @return closest valid shift time index corresponding to given shift time in steps */
	public int roundToNearestShiftTimeIndex(long shiftTimeInSteps) {
		int atLeast = (int) (shiftTimeInSteps / timeResolution.getSteps());
		int increase = (int) (((shiftTimeInSteps % timeResolution.getSteps()) * 2) / timeResolution.getSteps());
		return atLeast + increase;
	}

	/** Returns <b>time-and-energy state</b> index corresponding to given indices for the <b>energy state</b> and shift time.
	 * 
	 * @param energyLevelIndex <b>energy state</b> index
	 * @param shiftTimeIndex to evaluate
	 * @return <b>time-and-energy state</b> index derived from given indices for energy state and shift time */
	public int getStateIndex(int energyLevelIndex, int shiftTimeIndex) {
		return shiftTimeIndex * numberOfEnergyStates + energyLevelIndex;
	}

	/** Returns energy delta in MWh corresponding to a transition from an initial to a final state represented by their
	 * <b>time-and-energy</b> indices.
	 * 
	 * @param initialStateIndex <b>time-and-energy</b> index of the initial state
	 * @param finalStateIndex <b>time-and-energy</b> index of the final state
	 * @return energy delta in MWh */
	public double calcEnergyDeltaInMWH(int initialStateIndex, int finalStateIndex) {
		return getEnergyIndexDelta(initialStateIndex, finalStateIndex) * energyResolutionInMWH;
	}

	/** Returns <b>shift time</b> index derived from given <b>time-and-energy</b> state index.
	 * 
	 * @param stateIndex <b>time-and-energy</b> index from which to derive shift time
	 * @return corresponding shift time index */
	public int calcShiftTimeIndexFromStateIndex(int stateIndex) {
		return stateIndex / numberOfEnergyStates;
	}

	/** Returns <b>including</b> those that are technically not valid, i.e.
	 * <ul>
	 * <li>states with energy level equal to zero, but shift time above zero</li>
	 * <li>states with energy level not equal to zero, but shift time zero</li>
	 * </ul>
	 * 
	 * @return number of all states */
	public int getStateCount() {
		return numberOfEnergyStates * numberOfTimeStates;
	}
}
