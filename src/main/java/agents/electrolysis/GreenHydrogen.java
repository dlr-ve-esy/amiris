<<<<<<< Upstream, based on origin/dev
<<<<<<< Upstream, based on origin/dev
=======
>>>>>>> daf2b0e Add license information
// SPDX-FileCopyrightText: 2024 German Aerospace Center <amiris@dlr.de>
//
// SPDX-License-Identifier: Apache-2.0
package agents.electrolysis;

import agents.markets.meritOrder.sensitivities.MeritOrderSensitivity;
import de.dlr.gitlab.fame.agent.input.Make;
import de.dlr.gitlab.fame.agent.input.ParameterData;
import de.dlr.gitlab.fame.agent.input.ParameterData.MissingDataException;
import de.dlr.gitlab.fame.agent.input.Tree;
import de.dlr.gitlab.fame.time.TimePeriod;
import de.dlr.gitlab.fame.time.TimeStamp;

public class GreenHydrogen extends ElectrolyzerStrategist {
	public static enum TemporalCorrelationPeriod {
		HOURLY, MONTHLY,
	}

	static final String ERR_CORRELATION_PERIOD_NOT_IMPLEMENTED = "Correlation period is not implemented yet: ";

	/** Inputs specific to {@link GreenHydrogen} electrolyzer strategists */
	public static final Tree parameters = Make.newTree()
			.add(Make.newEnum("TemporalCorrelationPeriod", TemporalCorrelationPeriod.class).help(
					"Period in which electrolyzer production must match production of associated renewable plant"))
			.buildTree();

	private final TemporalCorrelationPeriod temporalCorrelationPeriod;
	private double maximumConsumption;

	/** Create new {@link GreenHydrogen}
	 * 
	 * @param generalInput parameter group associated with flexibility strategists in general
	 * @param specificInput parameter group associated with this strategist in specific
	 * @throws MissingDataException if any required input data is missing */
	public GreenHydrogen(ParameterData generalInput, ParameterData specificInput) throws MissingDataException {
		super(generalInput);
		temporalCorrelationPeriod = specificInput.getEnum("TemporalCorrelationPeriod", TemporalCorrelationPeriod.class);
		if (temporalCorrelationPeriod == TemporalCorrelationPeriod.MONTHLY) {
			throw new RuntimeException(ERR_CORRELATION_PERIOD_NOT_IMPLEMENTED + temporalCorrelationPeriod);
		}
	}

	@Override
	public void updateMaximumConsumption(TimeStamp time, double yieldPotential) {
		maximumConsumption = electrolyzer.calcCappedElectricDemandInMW(yieldPotential, time);
	}

	@Override
	public double getMaximumConsumption() {
		return maximumConsumption;
	}

	@Override
	protected MeritOrderSensitivity createBlankSensitivity() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	protected void updateSchedule(TimePeriod timePeriod) {
		updateScheduleArrays(actualProducedHydrogen);
	}

	/** transfer optimised dispatch to schedule arrays */
	private void updateScheduleArrays(double initialHydrogenProductionInMWH) {
		for (int hour = 0; hour < scheduleDurationPeriods; hour++) {
			demandScheduleInMWH[hour] = maximumConsumption;
			scheduledChargedHydrogenTotal[hour] = initialHydrogenProductionInMWH;
			initialHydrogenProductionInMWH += electrolyzer.calcHydrogenEnergy(demandScheduleInMWH[hour]);
		}
=======
package agents.electrolysis;

import agents.markets.meritOrder.sensitivities.MeritOrderSensitivity;
import de.dlr.gitlab.fame.agent.input.Make;
import de.dlr.gitlab.fame.agent.input.ParameterData;
import de.dlr.gitlab.fame.agent.input.ParameterData.MissingDataException;
import de.dlr.gitlab.fame.agent.input.Tree;
import de.dlr.gitlab.fame.time.TimePeriod;
import de.dlr.gitlab.fame.time.TimeStamp;

public class GreenHydrogen extends ElectrolyzerStrategist {
	public static enum TemporalCorrelationPeriod {
		HOURLY, MONTHLY,
	}

	static final String ERR_CORRELATION_PERIOD_NOT_IMPLEMENTED = "Correlation period is not implemented yet: ";

	/** Inputs specific to {@link GreenHydrogen} electrolyzer strategists */
	public static final Tree parameters = Make.newTree()
			.add(Make.newEnum("TemporalCorrelationPeriod", TemporalCorrelationPeriod.class).help(
					"Period in which electrolyzer production must match production of associated renewable plant"))
			.buildTree();

	private final TemporalCorrelationPeriod temporalCorrelationPeriod;
	private double maximumConsumption;

	/** Create new {@link GreenHydrogen}
	 * 
	 * @param generalInput parameter group associated with flexibility strategists in general
	 * @param specificInput parameter group associated with this strategist in specific
	 * @throws MissingDataException if any required input data is missing */
	public GreenHydrogen(ParameterData generalInput, ParameterData specificInput) throws MissingDataException {
		super(generalInput);
		temporalCorrelationPeriod = specificInput.getEnum("TemporalCorrelationPeriod", TemporalCorrelationPeriod.class);
		if (temporalCorrelationPeriod == TemporalCorrelationPeriod.MONTHLY) {
			throw new RuntimeException(ERR_CORRELATION_PERIOD_NOT_IMPLEMENTED + temporalCorrelationPeriod);
		}
	}

	@Override
	public void updateMaximumConsumption(TimeStamp time, double yieldPotential) {
		maximumConsumption = electrolyzer.calcCappedElectricDemandInMW(yieldPotential, time);
	}

	@Override
	public double getMaximumConsumption() {
		return maximumConsumption;
	}

	@Override
	protected MeritOrderSensitivity createBlankSensitivity() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	protected void updateSchedule(TimePeriod timePeriod) {
		updateScheduleArrays(actualProducedHydrogen);
<<<<<<< Upstream, based on origin/dev

>>>>>>> c90d9ce Create new electrolyzer strategist GreenHydrogen
=======
>>>>>>> 9740ff7 Remove PowerPlantScheduler and implement data exchange via TraderWithClients
	}

	/** transfer optimised dispatch to schedule arrays */
	private void updateScheduleArrays(double initialHydrogenProductionInMWH) {
		for (int hour = 0; hour < scheduleDurationPeriods; hour++) {
			demandScheduleInMWH[hour] = maximumConsumption;
			scheduledChargedHydrogenTotal[hour] = initialHydrogenProductionInMWH;
			initialHydrogenProductionInMWH += electrolyzer.calcHydrogenEnergy(demandScheduleInMWH[hour]);
		}
	}

}
