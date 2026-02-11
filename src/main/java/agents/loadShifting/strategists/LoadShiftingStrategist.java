// SPDX-FileCopyrightText: 2024-2026 German Aerospace Center <amiris@dlr.de>
//
// SPDX-License-Identifier: Apache-2.0
package agents.loadShifting.strategists;

import agents.flexibility.BidSchedule;
import agents.flexibility.Strategist;
import agents.loadShifting.LoadShiftingPortfolio;
import agents.markets.meritOrder.sensitivities.MeritOrderSensitivity;
import de.dlr.gitlab.fame.agent.input.Make;
import de.dlr.gitlab.fame.agent.input.ParameterData;
import de.dlr.gitlab.fame.agent.input.ParameterData.MissingDataException;
import de.dlr.gitlab.fame.agent.input.Tree;
import de.dlr.gitlab.fame.time.TimePeriod;
import endUser.EndUserTariff;

/** Creates arbitrage strategies for {@link LoadShiftingPortfolio}s
 * 
 * @author Johannes Kochems, Christoph Schimeczek */
public abstract class LoadShiftingStrategist extends Strategist {
	/** Type of dispatch strategy to be applied for load shifting scheduling */
	public static enum StrategistType {
		/** Minimise the cost from load shifting */
		SINGLE_AGENT_MIN_SYSTEM_COST,
		/** Creates a DispatchSchedule following a given TimeSeries for an energy pattern and one for a shift time pattern, both read
		 * from input file. It can provide forecasts regarding its behaviour. */
		DISPATCH_FILE,
		/** Maximise profits from load shifting */
		SINGLE_AGENT_MAX_PROFIT,
		/** Maximise profits from load shifting also considering end user tariffs in addition to day-ahead electricity prices */
		SINGLE_AGENT_MAX_PROFIT_TARIFFS,
		/** Minimise the total costs of energy consumption from load shifting also considering end user tariffs in addition to
		 * day-ahead electricity prices; Calls an external micro-model for load shifting scheduling. */
		SINGLE_AGENT_MIN_CONSUMER_COST_EXTERNAL
	}

	/** Specific input parameters for load shifting strategists */
	public static final Tree parameters = Make.newTree()
			.add(Strategist.forecastPeriodParam, Strategist.scheduleDurationParam, Strategist.bidToleranceParam,
					Make.newEnum("StrategistType", StrategistType.class))
			.add(Make.newGroup("SingleAgent").add(Make.newDouble("PurchaseLeviesAndTaxesInEURperMWH").optional()))
			.addAs("FixedDispatch", ShiftFileDispatcher.parameters)
			.addAs("Api", ShiftConsumerCostMinimiserExternal.apiParameters)
			.buildTree();

	/** Expected initial energy levels of the associated load shifting portfolio for each operation period */
	protected double[] scheduledInitialEnergyInMWH;
	/** The associated load shifting portfolio this strategists plans for */
	protected LoadShiftingPortfolio portfolio;

	/** Create {@link LoadShiftingStrategist}
	 * 
	 * @param generalInput parameters associated with strategists
	 * @param loadShiftingPortfolio for which schedules are to be created
	 * @param __ not used here; specific input for child classes
	 * @throws MissingDataException if any required input is missing */
	public LoadShiftingStrategist(ParameterData generalInput, ParameterData __,
			LoadShiftingPortfolio loadShiftingPortfolio) throws MissingDataException {
		super(generalInput);
		portfolio = loadShiftingPortfolio;
		scheduledInitialEnergyInMWH = new double[scheduleDurationPeriods];
	}

	/** Create an {@link LoadShiftingStrategist} - its actual type is determined from the given input
	 * 
	 * @param input all parameters associated with strategists
	 * @param endUserTariff {@link EndUserTariff} applicable for loads
	 * @param loadShiftingPortfolio for which schedules are to be created
	 * @return newly instantiated {@link LoadShiftingStrategist} based on the given input
	 * @throws MissingDataException if any required input is missing */
	public static LoadShiftingStrategist createStrategist(ParameterData input, EndUserTariff endUserTariff,
			LoadShiftingPortfolio loadShiftingPortfolio) throws MissingDataException {
		StrategistType strategistType = input.getEnum("StrategistType", StrategistType.class);
		switch (strategistType) {
			case SINGLE_AGENT_MIN_SYSTEM_COST:
				return new ShiftSystemCostMinimiser(input, input.getGroup("SingleAgent"), loadShiftingPortfolio);
			case DISPATCH_FILE:
				return new ShiftFileDispatcher(input, input.getGroup("FixedDispatch"), loadShiftingPortfolio);
			case SINGLE_AGENT_MAX_PROFIT:
				return new ShiftProfitMaximiser(input, input.getGroup("SingleAgent"), loadShiftingPortfolio);
			case SINGLE_AGENT_MAX_PROFIT_TARIFFS:
				return new ShiftProfitMaximiserTariffs(input, input.getGroup("SingleAgent"), endUserTariff,
						loadShiftingPortfolio);
			case SINGLE_AGENT_MIN_CONSUMER_COST_EXTERNAL:
				return new ShiftConsumerCostMinimiserExternal(input, input.getGroup("Api"), endUserTariff,
						loadShiftingPortfolio);
			default:
				throw new RuntimeException("LoadShifting Strategist not implemented: " + strategistType);
		}
	}

	@Override
	protected void callOnSensitivity(MeritOrderSensitivity sensitivity, TimePeriod __) {
		sensitivity.updatePowers(portfolio.getPowerInMW(), portfolio.getPowerInMW());
	}

	@Override
	protected double[] getInternalEnergySchedule() {
		return scheduledInitialEnergyInMWH;
	}

	/** Creates a {@link BidSchedule Schedule} for the connected {@link LoadShiftingPortfolio}
	 * 
	 * @param timePeriod first TimePeriod element of the schedule to be created
	 * @param currentEnergyShiftStorageLevelInMWH fictitious storage energy level the @link{LoadShiftingPortfolio} is at
	 * @param currentShiftTime time which the @link{LoadShiftingPortfolio} has already been shifted for
	 * @return {@link BidSchedule Schedule} for the specified {@link TimePeriod} */
	public BidSchedule createSchedule(TimePeriod timePeriod, double currentEnergyShiftStorageLevelInMWH,
			int currentShiftTime) {
		updateSchedule(timePeriod, currentEnergyShiftStorageLevelInMWH, currentShiftTime);
		updateBidSchedule();
		BidSchedule schedule = new BidSchedule(timePeriod, scheduleDurationPeriods);
		schedule.setBidsScheduleInEURperMWH(scheduledBidPricesInEURperMWH);
		schedule.setRequestedEnergyPerPeriod(demandScheduleInMWH);
		schedule.setExpectedInitialInternalEnergyScheduleInMWH(scheduledInitialEnergyInMWH);
		return schedule;
	}

	/** updates schedule arrays, from which afterwards a new schedule can be created
	 * 
	 * @param timePeriod at the beginning of the schedule
	 * @param currentEnergyShiftStorageLevelInMWH energy state of the portfolio at the beginning of the schedule
	 * @param currentShiftTime shifting state of the portfolio at the beginning of the schedule */
	protected abstract void updateSchedule(TimePeriod timePeriod, double currentEnergyShiftStorageLevelInMWH,
			int currentShiftTime);

	/** @return variable shifting costs from external scheduling - defaults to zero */
	public double getVariableShiftingCostsFromOptimiser() {
		return 0.0;
	}

	@Override
	public BidSchedule createSchedule(TimePeriod timePeriod) {
		throw new RuntimeException("This shall not be used!");
	}

	protected final void updateSchedule(TimePeriod timePeriod) {
		throw new RuntimeException("This shall not be used!");
	};
}