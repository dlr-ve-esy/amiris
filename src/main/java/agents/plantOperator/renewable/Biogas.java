// SPDX-FileCopyrightText: 2021-2026 German Aerospace Center <amiris@dlr.de>
//
// SPDX-License-Identifier: Apache-2.0
package agents.plantOperator.renewable;

import agents.plantOperator.Marginal;
import agents.plantOperator.RenewablePlantOperator;
import de.dlr.gitlab.fame.agent.input.DataProvider;
import de.dlr.gitlab.fame.agent.input.Input;
import de.dlr.gitlab.fame.agent.input.Make;
import de.dlr.gitlab.fame.agent.input.ParameterData;
import de.dlr.gitlab.fame.agent.input.ParameterData.MissingDataException;
import de.dlr.gitlab.fame.agent.input.Tree;
import de.dlr.gitlab.fame.data.TimeSeries;
import de.dlr.gitlab.fame.time.Constants;
import de.dlr.gitlab.fame.time.TimeOfDay;
import de.dlr.gitlab.fame.time.TimeStamp;

/** A {@link RenewablePlantOperator} for Biogas power plants
 * 
 * @author Christoph Schimeczek, Marc Deissenroth */
public class Biogas extends RenewablePlantOperator {
	/** Mode of operation of the {@link Biogas} power plant */
	public static enum OperationMode {
		/** Calculates a constant load factor based on the given {@link Biogas} plant's full load hours. */
		CONTINUOUS,
		/** Calculates two different load factor values based on the given {@link Biogas} plant's full load hours and depending on
		 * whether day or night. */
		DAY_NIGHT,
		/** Reads the load factor of the {@link Biogas} plant from the given file. */
		FROM_FILE
	}

	@Input private static final Tree parameters = Make
			.newTree().add(Make.newEnum("OperationMode", OperationMode.class),
					Make.newDouble("FullLoadHoursPerYear").optional(), Make.newSeries("DispatchTimeSeries").optional())
			.buildTree();

	private final double loadFactor;
	private final OperationMode operationMode;
	private final TimeSeries tsDispatchFromFile;

	/** Creates a {@link Biogas} plant operator
	 * 
	 * @param dataProvider provides input from config
	 * @throws MissingDataException if any required data is not provided */
	public Biogas(DataProvider dataProvider) throws MissingDataException {
		super(dataProvider);
		ParameterData input = parameters.join(dataProvider);
		operationMode = input.getEnum("OperationMode", OperationMode.class);

		switch (operationMode) {
			case CONTINUOUS:
			case DAY_NIGHT:
				loadFactor = input.getDouble("FullLoadHoursPerYear") / Constants.HOURS_PER_NORM_YEAR;
				tsDispatchFromFile = null;
				break;
			case FROM_FILE:
				tsDispatchFromFile = input.getTimeSeries("DispatchTimeSeries");
				loadFactor = Double.NaN;
				break;
			default:
				throw new RuntimeException(operationMode + " not yet implemented!");
		}
	}

	@Override
	protected Marginal calcSingleMarginal(TimeStamp time) {
		return new Marginal(calcAvailablePowerAtTime(time), getVariableOpexAtTime(time));
	}

	/** @return the available power at the specified time, considering its {@link OperationMode} */
	private double calcAvailablePowerAtTime(TimeStamp time) {
		return getInstalledPowerAtTimeInMW(time) * calcFlexibleLoadFactor(time);
	}

	/** Calculates the flexible load factor of the {@link Biogas} operator based on stored {@link OperationMode}.
	 * 
	 * @param time to calculate the load factor for
	 * @return calculated load factor */
	private double calcFlexibleLoadFactor(TimeStamp time) {
		switch (operationMode) {
			case CONTINUOUS:
				return loadFactor;
			case DAY_NIGHT:
				return calcLoadFactorDayNight(time.getStep());
			case FROM_FILE:
				return tsDispatchFromFile.getValueLinear(time);
			default:
				throw new RuntimeException(operationMode + " not yet implemented!");
		}
	}

	/** Calculates load factor in {@link OperationMode#DAY_NIGHT}. In the night, (i.e. 19h - 6h) only half of the power is used,
	 * whereas during the day, (i.e. 7h - 18h) 50% more power is used.
	 * 
	 * @param timeStep to calculate the load factor for
	 * @return load factor based on the time of the operation */
	private double calcLoadFactorDayNight(long timeStep) {
		int hourOfDay = TimeOfDay.calcHourOfDay(timeStep);
		if (hourOfDay < 7 || hourOfDay > 18) {
			return 0.5 * loadFactor;
		} else {
			return 1.5 * loadFactor;
		}
	}
}