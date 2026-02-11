// SPDX-FileCopyrightText: 2021-2026 German Aerospace Center <amiris@dlr.de>
//
// SPDX-License-Identifier: Apache-2.0
package agents.storage.arbitrageStrategists;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import agents.flexibility.Strategist;
import agents.markets.meritOrder.sensitivities.MeritOrderSensitivity;
import agents.storage.Device;
import agents.trader.StorageTrader;
import de.dlr.gitlab.fame.agent.input.Make;
import de.dlr.gitlab.fame.agent.input.ParameterBuilder;
import de.dlr.gitlab.fame.agent.input.ParameterData;
import de.dlr.gitlab.fame.agent.input.ParameterData.MissingDataException;
import de.dlr.gitlab.fame.agent.input.Tree;
import de.dlr.gitlab.fame.time.TimePeriod;
import de.dlr.gitlab.fame.time.TimeStamp;

/** Creates arbitrage strategies for storage devices based on forecasts for merit-order or electricity prices
 * 
 * @author Christoph Schimeczek */
public abstract class ArbitrageStrategist extends Strategist {
	/** Types of Strategists for storage operators */
	public static enum StrategistType {
		/** Creates a the schedule according to a given TimeSeries. */
		DISPATCH_FILE,
		/** Uses the storage {@link Device} in order to minimise the total system costs. */
		SINGLE_AGENT_MIN_SYSTEM_COST,
		/** Optimises the {@link Device} dispatch in order to maximise the profits of the {@link StorageTrader}. A perfect forecast of
		 * upcoming prices (and their changes due to charging) is used for the optimisation. */
		SINGLE_AGENT_MAX_PROFIT,
		/** Calculates the {@link Device} dispatch in order to maximise the profits of the {@link StorageTrader}. A median of the
		 * forecasted prices is used to estimate a good dispatch strategy in an environment with more than one flexible agent. */
		MULTI_AGENT_MEDIAN,
		/** Creates dispatch schedules aiming to optimize own profits without considering own price impact. */
		MAX_PROFIT_PRICE_TAKER,
	}

	static final String WARN_ROUND_UP = "`EnergyToPowerRatio * ModelledChargingSteps` no integer: storage capacity increased by ";
	static final String WARN_ROUND_DOWN = "`EnergyToPowerRatio * ModelledChargingSteps` no integer: storage capacity decreased by ";

	/** Specific input parameters for storage strategists */
	public static final Tree parameters = Make.newTree()
			.add(Strategist.forecastPeriodParam, Strategist.scheduleDurationParam, Strategist.bidToleranceParam,
					Strategist.forecastUpdateTypeParam,
					Make.newEnum("StrategistType", StrategistType.class))
			.addAs("SingleAgent", DynamicProgrammingStrategist.parameters).addAs("FixedDispatch", FileDispatcher.parameters)
			.addAs("MultiAgent", MultiAgentMedian.parameters)
			.buildTree();

	/** General input parameter of storage strategists to determine its type */
	public static final ParameterBuilder StrategistTypeParam = Make.newEnum("StrategistType", StrategistType.class);
	static final double ENERGY_STATE_ROUNDING_TOLERANCE = 1.E-2;
	/** Logs errors for this class and its subclasses */
	protected static Logger logger = LoggerFactory.getLogger(ArbitrageStrategist.class);

	/** Expected initial energy levels of the associated storage for each operation period */
	protected double[] scheduledInitialInternalEnergyInMWH;
	/** The associated storage device this strategists plans for */
	protected Device storage;

	/** Create {@link ArbitrageStrategist}
	 * 
	 * @param input parameters associated with strategists
	 * @param storage Device for which schedules are to be created
	 * @throws MissingDataException if any required input is missing */
	protected ArbitrageStrategist(ParameterData input, Device storage) throws MissingDataException {
		super(input);
		this.storage = storage;
		scheduledInitialInternalEnergyInMWH = new double[scheduleDurationPeriods];
	}

	/** Create an {@link ArbitrageStrategist} - its actual type is determined based on the given
	 * 
	 * @param input all parameters associated with strategists
	 * @param storage Device for which schedules are to be created
	 * @return newly instantiated {@link ArbitrageStrategist} based on the given input
	 * @throws MissingDataException if any required input is missing */
	public static ArbitrageStrategist createStrategist(ParameterData input, Device storage) throws MissingDataException {
		StrategistType strategistType = input.getEnum("StrategistType", StrategistType.class);
		switch (strategistType) {
			case SINGLE_AGENT_MIN_SYSTEM_COST:
				return new SystemCostMinimiser(input, input.getGroup("SingleAgent"), storage);
			case DISPATCH_FILE:
				return new FileDispatcher(input, input.getGroup("FixedDispatch"), storage);
			case SINGLE_AGENT_MAX_PROFIT:
				return new ProfitMaximiser(input, input.getGroup("SingleAgent"), storage);
			case MULTI_AGENT_MEDIAN:
				return new MultiAgentMedian(input, input.getGroup("MultiAgent"), storage);
			case MAX_PROFIT_PRICE_TAKER:
				return new ProfitMaximiserPriceTaker(input, input.getGroup("SingleAgent"), storage);
			default:
				throw new RuntimeException("Storage Strategist not implemented: " + strategistType);
		}
	}

	@Override
	protected void callOnSensitivity(MeritOrderSensitivity sensitivity, TimePeriod __) {
		sensitivity.updatePowers(storage.getExternalChargingPowerInMW(), storage.getExternalDischargingPowerInMW());
	}

	@Override
	protected double[] getInternalEnergySchedule() {
		return scheduledInitialInternalEnergyInMWH;
	}

	/** Returns forecasted external charging power, if possible - otherwise throws a RuntimeEception
	 * 
	 * @param targetTime for which to provide the forecast
	 * @return forecasted external charging power in MW
	 * @throws RuntimeException if this strategist cannot provide forecasts */
	public abstract double getChargingPowerForecastInMW(TimeStamp targetTime);

	/** Calculates number of energy states based on the storage capacity, its energy to power ratio and the number of discretisation
	 * steps used for internal energy changes. Logs warning if the discretised total capacity of the storage significantly deviates
	 * from its parameterised value.
	 * 
	 * @param numberOfTransitionStates number of states the (dis-)charging power is discretised with
	 * @return numberOfTransitionStates (rounded to closest integer) */
	protected int calcNumberOfEnergyStates(int numberOfTransitionStates) {
		double numberOfEnergyStates = numberOfTransitionStates * storage.getEnergyToPowerRatio() + 1;
		int roundedNumberOfEnergyStates = (int) Math.round(numberOfEnergyStates);
		double stateDelta = roundedNumberOfEnergyStates - numberOfEnergyStates;
		double capacityDeltaInMWH = stateDelta * storage.getInternalPowerInMW() / numberOfTransitionStates;
		if (stateDelta > ENERGY_STATE_ROUNDING_TOLERANCE) {
			logger.warn(WARN_ROUND_UP + capacityDeltaInMWH + " MWh");
		} else if (stateDelta < -ENERGY_STATE_ROUNDING_TOLERANCE) {
			logger.warn(WARN_ROUND_DOWN + capacityDeltaInMWH + " MWh");
		}
		return roundedNumberOfEnergyStates;
	}

	/** Corrects given internal energy value if it is below Zero or above maximum capacity.
	 * 
	 * @param internalEnergyInMWH to be corrected (if necessary)
	 * @return internal energy value that is secured to lie within storage bounds */
	protected double ensureWithinEnergyBounds(double internalEnergyInMWH) {
		return Math.max(0, Math.min(storage.getEnergyStorageCapacityInMWH(), internalEnergyInMWH));
	}
}