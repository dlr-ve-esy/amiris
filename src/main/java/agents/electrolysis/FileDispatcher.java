// SPDX-FileCopyrightText: 2023-2026 German Aerospace Center <amiris@dlr.de>
//
// SPDX-License-Identifier: Apache-2.0
package agents.electrolysis;

import agents.markets.meritOrder.Constants;
import agents.markets.meritOrder.books.DemandOrderBook;
import agents.markets.meritOrder.books.SupplyOrderBook;
import agents.markets.meritOrder.sensitivities.MeritOrderSensitivity;
import de.dlr.gitlab.fame.agent.input.Make;
import de.dlr.gitlab.fame.agent.input.ParameterData;
import de.dlr.gitlab.fame.agent.input.ParameterData.MissingDataException;
import de.dlr.gitlab.fame.agent.input.Tree;
import de.dlr.gitlab.fame.data.TimeSeries;
import de.dlr.gitlab.fame.time.TimePeriod;
import de.dlr.gitlab.fame.time.TimeStamp;

/** Dispatches an electrolysis unit following a fixed dispatch schedule, either for hydrogen production or electric demand, and
 * either absolute or relative to available conversion power
 * 
 * @author Christoph Schimeczek */
public class FileDispatcher extends ElectrolyzerStrategist {

	/** Determines whether the given schedule represents absolute amounts or is relative to installed converter power */
	private static enum Mode {
		ABSOLUTE, RELATIVE
	};

	/** Determines whether the given schedule represents a hydrogen production or electricity consumption */
	private static enum Target {
		ELECTRICITY, HYDROGEN
	};

	/** Input parameters for the {@link FileDispatcher} */
	public static final Tree parameters = Make.newTree().optional().add(
			Make.newSeries("HourlySchedule"),
			Make.newEnum("Mode", Mode.class).help("Whether schedule is absolute or relative to installed converter power"),
			Make.newEnum("Target", Target.class)
					.help("Whether the schedule represents hydrogen production or electricity consumption"))
			.buildTree();

	private final TimeSeries schedule;
	private final Mode mode;
	private final Target target;

	/** Creates new FileDispatcher strategist based on given input
	 * 
	 * @param generalInput parameter group associated with flexibility strategists in general
	 * @param specificInput parameter group associated with this strategist in specific
	 * @throws MissingDataException if any required input data is missing */
	protected FileDispatcher(ParameterData generalInput, ParameterData specificInput) throws MissingDataException {
		super(generalInput);
		schedule = specificInput.getTimeSeries("HourlySchedule");
		mode = specificInput.getEnum("Mode", Mode.class);
		target = specificInput.getEnum("Target", Target.class);
	}

	@Override
	public double getElectricDemandForecastInMW(TimeStamp targetTime) {
		double electricConversionPowerInMW = getElectricScheduleInMW(targetTime);
		return electrolyzer.calcCappedElectricDemandInMW(electricConversionPowerInMW, targetTime);
	}

	/** @return absolute electric demand in MW from schedule at given time */
	private double getElectricScheduleInMW(TimeStamp time) {
		double demand = schedule.getValueLinear(time);
		double electricDemand = target == Target.ELECTRICITY ? demand : electrolyzer.calcElectricEnergy(demand);
		electricDemand *= (mode == Mode.ABSOLUTE ? 1 : electrolyzer.getPeakPower(time));
		return Math.max(0, Math.min(electrolyzer.getPeakPower(time), electricDemand));
	}

	@Override
	protected void updateSchedule(TimePeriod timePeriod) {
		double initialProducedHydrogen = actualProducedHydrogen;
		for (int element = 0; element < scheduleDurationPeriods; element++) {
			final TimeStamp planningTime = timePeriod.shiftByDuration(element).getStartTime();
			double demandInMW = getElectricDemandForecastInMW(planningTime);
			demandScheduleInMWH[element] = demandInMW;
			scheduledChargedHydrogenTotal[element] = initialProducedHydrogen;
			initialProducedHydrogen += electrolyzer.calcProducedHydrogenOneHour(demandInMW, planningTime);
			priceScheduleInEURperMWH[element] = demandInMW > 0 ? Constants.SCARCITY_PRICE_IN_EUR_PER_MWH : 0;
		}
	}

	/** No {@link MeritOrderSensitivity} needed for {@link FileDispatcher}, as dispatch is read from file */
	@Override
	protected MeritOrderSensitivity createBlankSensitivity() {
		return null;
	}

	/** Not needed for {@link FileDispatcher} */
	@Override
	public void storeElectricityPriceForecast(TimePeriod timePeriod, double electricityPriceForecastInEURperMWH) {
		throw new RuntimeException(ERR_USE_PRICE_FORECAST + StrategistType.DISPATCH_FILE);
	}

	/** Not needed for {@link FileDispatcher} */
	@Override
	public void storeMeritOrderForesight(TimePeriod timePeriod, SupplyOrderBook supplyForecast,
			DemandOrderBook demandForecast) {
		throw new RuntimeException(ERR_USE_MERIT_ORDER_FORECAST + StrategistType.DISPATCH_FILE);
	}
}
