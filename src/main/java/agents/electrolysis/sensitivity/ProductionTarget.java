package agents.electrolysis.sensitivity;

import java.util.ArrayList;
import agents.electrolysis.Electrolyzer;
import agents.flexibility.dynamicProgramming.states.StateManager;
import de.dlr.gitlab.fame.agent.input.Make;
import de.dlr.gitlab.fame.agent.input.ParameterData;
import de.dlr.gitlab.fame.agent.input.ParameterData.MissingDataException;
import de.dlr.gitlab.fame.agent.input.Tree;
import de.dlr.gitlab.fame.data.TimeSeries;
import de.dlr.gitlab.fame.time.Constants;
import de.dlr.gitlab.fame.time.TimePeriod;
import de.dlr.gitlab.fame.time.TimeSpan;
import de.dlr.gitlab.fame.time.TimeStamp;

public class ProductionTarget extends ElectrolyzerStrategist {
	static final String GROUP_PRODUCTION = "ProductionTarget";
	static final String PARAM_TARGET = "HydrogenProductionTargetInMWH";
	static final String PARAM_INTERVAL = "ProductionTargetIntervalInHours";
	static final String PARAM_STEP = "EnergyResolutionInMWH";

	static final Tree parameters = Make.newTree().optional().add(
			Make.newSeries(PARAM_TARGET), Make.newInt(PARAM_INTERVAL), Make.newDouble(PARAM_STEP)).buildTree();

	private final TimeSeries productionTargets;
	private final int productionInterval;
	private final double powerStepInMW;
	private TimePeriod productionPeriod;

	protected ProductionTarget(ParameterData input, Electrolyzer electrolyzer, TimeSpan operationSpan)
			throws MissingDataException {
		super(input, electrolyzer, operationSpan);
		ParameterData production = input.getGroup(GROUP_PRODUCTION);
		productionTargets = production.getTimeSeries(PARAM_TARGET);
		productionInterval = production.getInteger(PARAM_INTERVAL);
		powerStepInMW = production.getDouble(PARAM_STEP);
	}

	@Override
	public ArrayList<TimeStamp> getPlanningTimes(TimePeriod startingPeriod) {
		return StateManager.createPlanningTimes(startingPeriod, planningHorizonInHours);
	}

	@Override
	protected void updateSchedule(TimePeriod timePeriod) {
		updateIntervalStart(timePeriod);

	}

	/** Updates {@link #productionPeriod} and hydrogen production (deficit or surplus) at the beginning of an interval */
	private void updateIntervalStart(TimePeriod timePeriod) {
		if (productionPeriod == null) {
			TimeStamp startTime = timePeriod.getStartTime();
			TimeSpan duration = new TimeSpan(productionInterval * Constants.STEPS_PER_HOUR);
			productionPeriod = new TimePeriod(startTime, duration);
		}
		if (productionPeriod.getLastTime().isLessEqualTo(timePeriod.getStartTime())) {
			double previousPeriodProductionTarget = productionTargets
					.getValueEarlierEqual(timePeriod.getStartTime().earlierByOne());
			double missingProductionLastInterval = previousPeriodProductionTarget - actualProducedHydrogen;
			actualProducedHydrogen = -missingProductionLastInterval;
			productionPeriod = productionPeriod.shiftByDuration(1);
		}
	}

	@Override
	protected double[] getInternalEnergySchedule() {
		// TODO Auto-generated method stub
		return null;
	}

}
