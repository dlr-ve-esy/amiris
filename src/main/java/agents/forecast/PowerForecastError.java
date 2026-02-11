// SPDX-FileCopyrightText: 2021-2026 German Aerospace Center <amiris@dlr.de>
//
// SPDX-License-Identifier: Apache-2.0
package agents.forecast;

import java.util.Random;
import de.dlr.gitlab.fame.agent.input.Make;
import de.dlr.gitlab.fame.agent.input.ParameterData;
import de.dlr.gitlab.fame.agent.input.ParameterData.MissingDataException;
import de.dlr.gitlab.fame.agent.input.Tree;

/** Calculates power values with errors following a normal distribution for power forecasting
 * 
 * @author Johannes Kochems, Christoph Schimeczek */
public class PowerForecastError {

	/** Specific inputs to parameterise {@link PowerForecastError} modelling */
	public static final Tree parameters = Make.newTree().optional()
			.add(Make.newDouble("Mean").optional().help("Relative offset of the power forecasts"),
					Make.newDouble("StandardDeviation").optional()
							.help("Standard deviation of the relative power forecasting errors"))
			.buildTree();

	private double mean;
	private double standardDeviation;
	private Random rng;

	/** Creates a {@link PowerForecastError}
	 * 
	 * @param input parameter group according to {@link #parameters}
	 * @param random random number generator - use FAME's RNG creation function to ensure reproducibility on identical seeds
	 * @throws MissingDataException if any required data is not provided */
	public PowerForecastError(ParameterData input, Random random) throws MissingDataException {
		mean = input.getDouble("Mean");
		standardDeviation = input.getDouble("StandardDeviation");
		rng = random;
	}

	/** Calculates a power forecast with errors following a normal distribution
	 * 
	 * @param powerWithoutError perfect foresight power forecast
	 * @return given power multiplied with a randomly generated forecast error factor */
	public double calcPowerWithError(double powerWithoutError) {
		double factor = Math.max(0, 1 + getNextNormallyDistributedNumber());
		return powerWithoutError * factor;
	}

	/** @return a random error from a normal distribution */
	private double getNextNormallyDistributedNumber() {
		return rng.nextGaussian() * standardDeviation + mean;
	}
}