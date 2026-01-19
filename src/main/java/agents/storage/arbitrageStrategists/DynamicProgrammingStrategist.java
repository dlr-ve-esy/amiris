// SPDX-FileCopyrightText: 2026 German Aerospace Center <amiris@dlr.de>
//
// SPDX-License-Identifier: Apache-2.0
package agents.storage.arbitrageStrategists;

import agents.storage.Device;
import de.dlr.gitlab.fame.agent.input.Make;
import de.dlr.gitlab.fame.agent.input.ParameterData;
import de.dlr.gitlab.fame.agent.input.ParameterData.MissingDataException;
import de.dlr.gitlab.fame.agent.input.Tree;
import de.dlr.gitlab.fame.time.TimePeriod;
import de.dlr.gitlab.fame.time.TimeStamp;

/** Provides basic functions for dynamic programming-based optimisation of dispatch strategies. The states storage states
 * considered are the discretised (internal) storage energy levels. The full merit-order forecast information is required to
 * implement a perfect-foresight optimisation. Optimisation is done by finding the best sequence of states, also considering
 * (dis-)charging efficiencies. Self discharge is not considered during optimisation, but tracked during schedule building. I.e.,
 * self discharge will not trigger rescheduling events, but the found schedule is not guaranteed to be optimal.
 * 
 * @author Evelyn Sperber, Christoph Schimeczek */
public abstract class DynamicProgrammingStrategist extends ArbitrageStrategist {
	/** Specific input parameters for storage strategists using dynamic programming */
	public static final Tree parameters = Make.newTree().optional()
			.add(Make.newInt("ModelledChargingSteps").optional()
					.help("Resolution of discretisation, total levels = ModelledChargingSteps * Device.EnergyToPowerRatio + 1"))
			.buildTree();

	/** number of discrete states to model an energy state transition */
	protected final int numberOfTransitionStates;
	/** total number of discrete states representing the storage state of charge */
	protected final int numberOfEnergyStates;
	/** delta of internal energy in MWH associated with an increase of one discrete charging state */
	protected final double internalEnergyPerState;

	/** bestNextState[t][i]: best next internal state identified when current state is i in period t */
	protected final int[][] bestNextState;

	/** Creates a {@link DynamicProgrammingStrategist}
	 * 
	 * @param generalInput general parameters associated with strategists
	 * @param specificInput specific parameters for this type of strategist
	 * @param storage device to be optimised
	 * @throws MissingDataException if any required input is missing */
	public DynamicProgrammingStrategist(ParameterData generalInput, ParameterData specificInput, Device storage)
			throws MissingDataException {
		super(generalInput, storage);
		this.numberOfTransitionStates = specificInput.getInteger("ModelledChargingSteps");
		this.numberOfEnergyStates = calcNumberOfEnergyStates(numberOfTransitionStates);
		this.internalEnergyPerState = storage.getInternalPowerInMW() / numberOfTransitionStates;

		bestNextState = new int[forecastSteps][numberOfEnergyStates];
	}

	@Override
	public void updateSchedule(TimePeriod startTimePeriod) {
		clearPlanningArrays();
		optimiseDispatch(startTimePeriod);
		updateScheduleArrays(startTimePeriod);
	}

	/** replaces all entries in the planning arrays with 0 or Integer.MIN_VALUE */
	protected abstract void clearPlanningArrays();

	/** optimise the dispatch based on the specific optimisation target
	 * 
	 * @param startPeriod first period of the schedule to be created */
	protected abstract void optimiseDispatch(TimePeriod startPeriod);

	/** For scheduling period: updates arrays for expected initial energy levels, (dis-)charging power and bidding prices
	 * 
	 * @param firstPeriod of the new schedule */
	protected final void updateScheduleArrays(TimePeriod firstPeriod) {
		double initialEnergyInStorageInMWh = storage.getCurrentEnergyInStorageInMWH();
		int initialState = findNearestState(initialEnergyInStorageInMWh);
		double totalEnergySurplus = determineEnergyDeviation(initialEnergyInStorageInMWh, initialState);
		for (int period = 0; period < scheduleDurationPeriods; period++) {
			double initialInternalEnergyInMWH = internalEnergyPerState * initialState + totalEnergySurplus;
			scheduledInitialInternalEnergyInMWH[period] = ensureWithinEnergyBounds(initialInternalEnergyInMWH);
			int nextState = bestNextState[period][initialState];

			double internalSelfDischarge = storage
					.calcInternalSelfDischargeInMWH(scheduledInitialInternalEnergyInMWH[period]);
			double nextExactInternalEnergy = nextState * internalEnergyPerState + totalEnergySurplus - internalSelfDischarge;
			nextExactInternalEnergy = ensureWithinEnergyBounds(nextExactInternalEnergy);
			double internalEnergyDelta = nextExactInternalEnergy - scheduledInitialInternalEnergyInMWH[period];
			double externalEnergyDelta = storage.internalToExternalEnergy(internalEnergyDelta + internalSelfDischarge);

			nextState = findNearestState(nextExactInternalEnergy);
			totalEnergySurplus = determineEnergyDeviation(nextExactInternalEnergy, nextState);

			demandScheduleInMWH[period] = externalEnergyDelta;
			priceScheduleInEURperMWH[period] = calcBidPrice(firstPeriod.shiftByDuration(period), externalEnergyDelta);
			initialState = nextState;
		}
	}

	/** @return the nearest energy state */
	private int findNearestState(double currentEnergyInStorageInMWh) {
		return Math.max(0,
				Math.min(numberOfEnergyStates, (int) Math.round(currentEnergyInStorageInMWh / internalEnergyPerState)));
	}

	/** @return the energy deviation caused between the exact energy and its associated state */
	private double determineEnergyDeviation(double exactEnergyInMWH, int associatedState) {
		return exactEnergyInMWH - associatedState * internalEnergyPerState;
	}

	/** Calculates bidding price for given time period and planned external energy delta
	 * 
	 * @param timePeriod at which the (dis-)charging action happens
	 * @param externalEnergyDelta planned to take place; &gt; 0: charging &lt; 0: discharging
	 * @return bidding price depending on time and type of action */
	protected abstract double calcBidPrice(TimePeriod timePeriod, double externalEnergyDelta);

	/** Calculates lowest final state reachable from given initial state
	 * 
	 * @param initialState internal energy state at the beginning of the transition
	 * @return lower bound (inclusive) of discrete states reachable from specified initialState */
	protected final int calcFinalStateLowerBound(int initialState) {
		return Math.max(0, initialState - numberOfTransitionStates);
	}

	/** Calculates highest final state reachable from given initial state
	 * 
	 * @param initialState internal energy state at the beginning of the transition
	 * @return upper bound (inclusive) of discrete states reachable from specified initialState */
	protected final int calcFinalStateUpperBound(int initialState) {
		return Math.min(numberOfEnergyStates - 1, initialState + numberOfTransitionStates);
	}

	@Override
	public double getChargingPowerForecastInMW(TimeStamp targetTime) {
		throw new RuntimeException(ERR_PROVIDE_FORECAST + getClass().getSimpleName());
	}
}
