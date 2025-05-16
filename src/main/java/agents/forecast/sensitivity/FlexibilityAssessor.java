// SPDX-FileCopyrightText: 2025 German Aerospace Center <amiris@dlr.de>
//
// SPDX-License-Identifier: Apache-2.0
package agents.forecast.sensitivity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import communications.message.AmountAtTime;
import de.dlr.gitlab.fame.time.TimeStamp;
import util.TimedDataMap;

/** Assesses flexibility dispatch history to derive bid multipliers. These multipliers determine how the dispatch of an individual
 * flexibility option behaves compared to the total dispatch of power plants.
 * 
 * @author Christoph Schimeczek, Johannes Kochems */
public class FlexibilityAssessor {
	static final String WARN_MISSING_REGISTRATION = "Agent with ID %s was not registered with SensitivityForecaster; it is recommended that all clients register, or none.";
	static final String WARN_INFEASIBLE_WEIGHT = "Weight of initial estimate must not be smaller than 1 but was: ";
	private static Logger logger = LoggerFactory.getLogger(FlexibilityAssessor.class);

	private static final double NUMERICAL_TOLERANCE = 1E-12;

	private final double cutOffFactor;
	private final int initialEstimateWeight;

	private final HashMap<Long, Double> maxEnergyDeltaPerClient = new HashMap<>();
	private final TimedDataMap<Long, Double> multiplierHistory = new TimedDataMap<>();
	private final TimedDataMap<Long, Double> awardHistory = new TimedDataMap<>();
	private final HashSet<TimeStamp> updatesRequiredAt = new HashSet<>();
	private final HashMap<Long, Double> sumOfMultipliers = new HashMap<>();
	private final HashMap<Long, Integer> numberOfPreviousSummands = new HashMap<>();

	/** Instantiate a new {@link FlexibilityAssessor}
	 * 
	 * @param cutOffFactor ignore awards that have lower energy than this factor * largest previous award */
	public FlexibilityAssessor(double cutOffFactor, int initialEstimateWeight) {
		this.cutOffFactor = cutOffFactor;
		this.initialEstimateWeight = getFeasibleWeight(initialEstimateWeight);
	}

	/** Ensure given weight is at least one, else log a warning */
	private int getFeasibleWeight(int weight) {
		if (weight < 1) {
			logger.warn(WARN_INFEASIBLE_WEIGHT, weight);
		}
		return Math.max(1, weight);
	}

	/** Update a client's maximum energy; On first registration: use this value to mimic awards before simulation time
	 * 
	 * @param clientId id of agent whose installed power is to be registered
	 * @param maxEnergyDeltaInMWH of the client valid at the beginning of the simulation, must be greater than zero */
	public void registerClient(long clientId, double maxEnergyDeltaInMWH) {
		boolean isFirstRegistrationOfThisClient = !maxEnergyDeltaPerClient.containsKey(clientId);
		maxEnergyDeltaPerClient.put(clientId, maxEnergyDeltaInMWH);
		if (isFirstRegistrationOfThisClient) {
			for (int i = 0; i < initialEstimateWeight; i++) {
				TimeStamp time = new TimeStamp(Long.MIN_VALUE + i);
				awardHistory.set(time, clientId, maxEnergyDeltaInMWH);
				updatesRequiredAt.add(time);
			}
		}
	}

	/** Store a client's net awarded energy at a given time
	 * 
	 * @param clientId id of agent to store the net awarded energy for
	 * @param award telling the net awarded energy for a specific clearing time */
	public void saveAward(long clientId, AmountAtTime award) {
		awardHistory.set(award.validAt, clientId, award.amount);
		updatesRequiredAt.add(award.validAt);
	}

	/** Process all previously stored awards and registrations and update each client's multiplier history */
	public void processInput() {
		for (TimeStamp time : updatesRequiredAt) {
			HashMap<Long, Double> awards = awardHistory.getDataAt(time);
			double sum = sumValuesInMap(awards);
			for (var entry : awards.entrySet()) {
				double factor = calcFactor(entry.getKey(), entry.getValue(), sum);
				multiplierHistory.set(time, entry.getKey(), factor);
			}
		}
		updatesRequiredAt.clear();
	}

	/** @return sum of double values in given map */
	private double sumValuesInMap(HashMap<?, Double> map) {
		double sum = 0;
		for (double value : map.values()) {
			sum += value;
		}
		return sum;
	}

	/** @return factor for given client based on the client's award and the sum of all awards */
	private double calcFactor(long clientId, double award, double sum) {
		double installedPower = maxEnergyDeltaPerClient.getOrDefault(clientId, 0.);
		if (installedPower < NUMERICAL_TOLERANCE || Math.abs(award) * cutOffFactor < installedPower) {
			return Double.NaN;
		}
		return sum / award;
	}

	/** Return a client's average multiplier derived from award history or installed power
	 * 
	 * @param clientId id of agent to obtain the multiplier for
	 * @return an estimate of the client's bid multiplier */
	public double getMultiplier(long clientId) {
		double[] result = calcMultiplierComponents(clientId, multiplierHistory.getValuesOf(clientId));
		return result[0] / result[1];
	}

	/** The sum of a client's multiplier history and the number of elements in that history; the number of elements is greater or
	 * equal that one
	 * 
	 * @return sum of multipliers, number of summands */
	private double[] calcMultiplierComponents(long clientId, ArrayList<Double> multipliers) {
		double sum = sumOfMultipliers.getOrDefault(clientId, 0.);
		int numberOfValidMultipliers = numberOfPreviousSummands.getOrDefault(clientId, 0);
		for (double multiplier : multipliers) {
			if (!Double.isNaN(multiplier)) {
				sum += multiplier;
				numberOfValidMultipliers++;
			}
		}
		if (numberOfValidMultipliers == 0) {
			if (!maxEnergyDeltaPerClient.isEmpty()) {
				logger.warn(WARN_MISSING_REGISTRATION, clientId);
			}
			return new double[] {1.0, 1.0};
		}
		return new double[] {sum, numberOfValidMultipliers};
	}

	/** Remove any stored award data from before the given time and compress the bid history
	 * 
	 * @param time elements associated with previous times are removed */
	public void clearBefore(TimeStamp time) {
		awardHistory.clearBefore(time);
		HashSet<Long> uniqueClients = multiplierHistory.getKeysBefore(time);
		for (long clientId : uniqueClients) {
			double[] result = calcMultiplierComponents(clientId, multiplierHistory.getValuesBefore(time, clientId));
			sumOfMultipliers.put(clientId, result[0]);
			numberOfPreviousSummands.put(clientId, (int) Math.round(result[1]));
		}
		multiplierHistory.clearBefore(time);
	}
}
