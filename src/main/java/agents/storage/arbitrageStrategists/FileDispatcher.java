// SPDX-FileCopyrightText: 2026 German Aerospace Center <amiris@dlr.de>
//
// SPDX-License-Identifier: Apache-2.0
package agents.storage.arbitrageStrategists;

import agents.flexibility.BidSchedule;
import agents.markets.meritOrder.Constants;
import agents.markets.meritOrder.books.DemandOrderBook;
import agents.markets.meritOrder.books.SupplyOrderBook;
import agents.markets.meritOrder.sensitivities.MeritOrderSensitivity;
import agents.storage.Device;
import de.dlr.gitlab.fame.agent.input.Make;
import de.dlr.gitlab.fame.agent.input.ParameterData;
import de.dlr.gitlab.fame.agent.input.ParameterData.MissingDataException;
import de.dlr.gitlab.fame.agent.input.Tree;
import de.dlr.gitlab.fame.data.TimeSeries;
import de.dlr.gitlab.fame.time.TimePeriod;
import de.dlr.gitlab.fame.time.TimeStamp;

/** Creates {@link BidSchedule}s from file for a connected storage {@link Device}
 *
 * @author Christoph Schimeczek, Johannes Kochems, Ulrich Frey, Felix Nitsch */
public class FileDispatcher extends ArbitrageStrategist {
	/** Input for the {@link FileDispatcher} */
	public static final Tree parameters = Make.newTree().optional()
			.add(Make.newSeries("Schedule").optional().help(
					"Change of internal storage energy relative to available charging power. Values should be -1 <= x <= 1."),
					Make.newDouble("DispatchToleranceInMWH").optional()
							.help("Accepted tolerance for dispatch deviations in MWh."))
			.buildTree();

	static final String WARN_BELOW_TOLERANCE = ": Dispatch file may not be suitable - Storage below minimum by MWh ";
	static final String WARN_ABOVE_TOLERANCE = ": Dispatch file may not be suitable - Storage above maximum by MWh ";
	static final String ERR_CANNOT_USE_FORECAST = "Storage strategist 'FileDispatcher' cannot digest forecasts. Remove contracts.";

	private double dispatchToleranceInMWH;

	/** TimeSeries of storage charging power (< 0:discharging; >0: charging) relative to internal charging power */
	private TimeSeries tsDispatch;

	/** Creates a {@link FileDispatcher}
	 * 
	 * @param generalInput general parameters associated with strategists
	 * @param specificInput specific parameters for this strategist
	 * @param storage device to be optimised
	 * @throws MissingDataException if any required input is missing */
	public FileDispatcher(ParameterData generalInput, ParameterData specificInput, Device storage)
			throws MissingDataException {
		super(generalInput, storage);
		this.tsDispatch = specificInput.getTimeSeries("Schedule");
		this.dispatchToleranceInMWH = specificInput.getDoubleOrDefault("DispatchToleranceInMWH", 0.1);
	}

	/** No {@link MeritOrderSensitivity} needed for {@link FileDispatcher}, as dispatch is read from file */
	@Override
	protected MeritOrderSensitivity createBlankSensitivity() {
		return null;
	}

	/** Not needed for {@link FileDispatcher} */
	@Override
	protected void updateSchedule(TimePeriod t) {}

	/** @return {@link BidSchedule} for the connected {@link Device} for the specified simulation hour **/
	@Override
	public BidSchedule createSchedule(TimePeriod timeSegment) {
		double currentEnergyInStorageInMWH = storage.getCurrentEnergyInStorageInMWH();
		for (int element = 0; element < scheduleDurationPeriods; element++) {
			final TimeStamp planningTime = timeSegment.shiftByDuration(element).getStartTime();
			final double internalChargePowerInMW = calcInternalChargingPowerAt(planningTime);
			final double externalChargePowerInMW = storage.internalToExternalEnergy(internalChargePowerInMW);

			demandScheduleInMWH[element] = externalChargePowerInMW;
			scheduledInitialInternalEnergyInMWH[element] = currentEnergyInStorageInMWH;
			currentEnergyInStorageInMWH += internalChargePowerInMW;
			issueWarningIfOutsideTolerance(currentEnergyInStorageInMWH, planningTime);
			currentEnergyInStorageInMWH = ensureWithinBounds(currentEnergyInStorageInMWH);
			setBidPrice(element, externalChargePowerInMW);
		}
		return buildSchedule(timeSegment);
	}

	/** @return internal charging power in the dispatch file at the given {@link TimeStamp} */
	private double calcInternalChargingPowerAt(TimeStamp planningTime) {
		final double relativeChargePower = tsDispatch.getValueLinear(planningTime);
		return storage.getInternalPowerInMW() * relativeChargePower;
	}

	/** logs a warning message if storage is outside its constraints by more than {@link #dispatchToleranceInMWH} */
	private void issueWarningIfOutsideTolerance(double currentEnergyInStorageInMWH, TimeStamp timeStamp) {
		if (currentEnergyInStorageInMWH < -dispatchToleranceInMWH) {
			double violation = Math.abs(currentEnergyInStorageInMWH);
			logger.warn(timeStamp + WARN_BELOW_TOLERANCE + violation);
		}
		final double storageCapacityInMWH = storage.getEnergyStorageCapacityInMWH();
		if (currentEnergyInStorageInMWH > storageCapacityInMWH + dispatchToleranceInMWH) {
			double violation = currentEnergyInStorageInMWH - storageCapacityInMWH;
			logger.warn(timeStamp + WARN_ABOVE_TOLERANCE + violation);
		}
	}

	/** @return energy in storage, ensured to be within the bounds of the connected {@link Device} */
	private double ensureWithinBounds(double currentEnergyInStorageInMWH) {
		final double storageCapacityInMWH = storage.getEnergyStorageCapacityInMWH();
		return Math.max(0, Math.min(storageCapacityInMWH, currentEnergyInStorageInMWH));
	}

	/** sets bid price for a given time element and charging power; to enforce dispatch, max & min allowed prices are used */
	private void setBidPrice(int element, double externalChargePowerInMW) {
		if (externalChargePowerInMW > 0) {
			scheduledBidPricesInEURperMWH[element] = Constants.SCARCITY_PRICE_IN_EUR_PER_MWH;
		} else if (externalChargePowerInMW < 0) {
			scheduledBidPricesInEURperMWH[element] = Constants.MINIMAL_PRICE_IN_EUR_PER_MWH;
		} else {
			scheduledBidPricesInEURperMWH[element] = 0;
		}
	}

	/** @return {@link BidSchedule} for the given TimeSegment created from prepared Bid arrays */
	private BidSchedule buildSchedule(TimePeriod timeSegment) {
		final BidSchedule schedule = new BidSchedule(timeSegment, scheduleDurationPeriods);
		schedule.setBidsScheduleInEURperMWH(scheduledBidPricesInEURperMWH);
		schedule.setRequestedEnergyPerPeriod(demandScheduleInMWH);
		schedule.setExpectedInitialInternalEnergyScheduleInMWH(scheduledInitialInternalEnergyInMWH);
		return schedule;
	}

	@Override
	public double getChargingPowerForecastInMW(TimeStamp targetTime) {
		double internalEnergyInMW = calcInternalChargingPowerAt(targetTime);
		return storage.internalToExternalEnergy(internalEnergyInMW);
	}

	/** Unused method - will throw an Exception */
	@Override
	public void storeMeritOrderForesight(TimePeriod timePeriod, SupplyOrderBook supplyForecast,
			DemandOrderBook demandForecast) {
		throw new RuntimeException(ERR_CANNOT_USE_FORECAST);
	}
}