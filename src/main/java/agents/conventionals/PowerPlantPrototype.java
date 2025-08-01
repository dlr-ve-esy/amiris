// SPDX-FileCopyrightText: 2025 German Aerospace Center <amiris@dlr.de>
//
// SPDX-License-Identifier: Apache-2.0
package agents.conventionals;

import java.util.NoSuchElementException;
import agents.markets.FuelsTrader;
import de.dlr.gitlab.fame.agent.input.Make;
import de.dlr.gitlab.fame.agent.input.ParameterData;
import de.dlr.gitlab.fame.agent.input.ParameterData.MissingDataException;
import de.dlr.gitlab.fame.agent.input.Tree;
import de.dlr.gitlab.fame.communication.transfer.ComponentCollector;
import de.dlr.gitlab.fame.communication.transfer.ComponentProvider;
import de.dlr.gitlab.fame.communication.transfer.Portable;
import de.dlr.gitlab.fame.data.TimeSeries;
import de.dlr.gitlab.fame.time.TimeStamp;

/** Stores common data of one type of conventional power plants
 * 
 * @author Christoph Schimeczek */
public abstract class PowerPlantPrototype implements Portable {
	static final Tree parameters = Make.newTree().add(
			FuelsTrader.fuelTypeParameter, Make.newDouble("SpecificCo2EmissionsInTperMWH"),
			Make.newSeries("PlannedAvailability"), Make.newDouble("UnplannedAvailabilityFactor"),
			Make.newSeries("OpexVarInEURperMWH"), Make.newDouble("CyclingCostInEURperMW"),
			Make.newSeries("MustRunFactor").optional())
			.buildTree();

	private String fuelType;
	private double specificCo2EmissionsInTonsPerThermalMWH;
	private double unplannedAvailabilityFactor;
	private double cyclingCostInEURperMW;
	private TimeSeries tsAvailability;
	private TimeSeries tsVariableCosts;
	private TimeSeries tsMustRun;

	/** Technical specification template for a group conventional power plants */
	public static class PrototypeData {
		/** Type of fuel used */
		public String fuelType;
		/** Specific CO2 emissions in tons per use of 1 thermal MWh of fuel */
		public double specificCo2EmissionsInTonsPerThermalMWH;
		/** Permanently applied average availability reduction factor */
		public double unplannedAvailabilityFactor;
		/** Cost for one ramping cycle */
		public double cyclingCostInEURperMW;
		/** Time-dependent availability factor */
		public TimeSeries tsAvailability;
		/** Time-dependent variable costs per MWh of produced electricity */
		public TimeSeries tsVariableCosts;
		/** Time-dependend factor of the installed capacity that must run and may not be shut down */
		public TimeSeries tsMustRun;

		/** Creates a new {@link PrototypeData}
		 * 
		 * @param data input parameters of group {@link PowerPlantPrototype#parameters}
		 * @throws MissingDataException if any required parameter is not specified */
		public PrototypeData(ParameterData data) throws MissingDataException {
			fuelType = FuelsTrader.readFuelType(data);
			specificCo2EmissionsInTonsPerThermalMWH = data.getDouble("SpecificCo2EmissionsInTperMWH");
			unplannedAvailabilityFactor = data.getDouble("UnplannedAvailabilityFactor");
			tsAvailability = data.getTimeSeries("PlannedAvailability");
			cyclingCostInEURperMW = data.getDouble("CyclingCostInEURperMW");
			tsVariableCosts = data.getTimeSeries("OpexVarInEURperMWH");
			tsMustRun = data.getTimeSeriesOrDefault("MustRunFactor", null);
		}
	}

	/** required for {@link Portable}s */
	public PowerPlantPrototype() {}

	/** Creates {@link PowerPlantPrototype} based on
	 * 
	 * @param prototypeData template to initialise most technical plant parameters */
	public PowerPlantPrototype(PrototypeData prototypeData) {
		fuelType = prototypeData.fuelType;
		specificCo2EmissionsInTonsPerThermalMWH = prototypeData.specificCo2EmissionsInTonsPerThermalMWH;
		unplannedAvailabilityFactor = prototypeData.unplannedAvailabilityFactor;
		cyclingCostInEURperMW = prototypeData.cyclingCostInEURperMW;
		tsAvailability = prototypeData.tsAvailability;
		tsVariableCosts = prototypeData.tsVariableCosts;
		tsMustRun = prototypeData.tsMustRun;
	}

	/** required for {@link Portable}s */
	@Override
	public void addComponentsTo(ComponentCollector collector) {
		collector.storeStrings(fuelType);
		collector.storeDoubles(specificCo2EmissionsInTonsPerThermalMWH, unplannedAvailabilityFactor, cyclingCostInEURperMW);
		collector.storeTimeSeries(tsAvailability, tsVariableCosts);
		if (tsMustRun != null) {
			collector.storeTimeSeries(tsMustRun);
		}
	}

	/** required for {@link Portable}s */
	@Override
	public void populate(ComponentProvider provider) {
		fuelType = provider.nextString();
		specificCo2EmissionsInTonsPerThermalMWH = provider.nextDouble();
		unplannedAvailabilityFactor = provider.nextDouble();
		cyclingCostInEURperMW = provider.nextDouble();
		tsAvailability = provider.nextTimeSeries();
		tsVariableCosts = provider.nextTimeSeries();
		try {
			tsMustRun = provider.nextTimeSeries();
		} catch (NoSuchElementException e) {
			tsMustRun = null;
		}
	}

	/** Returns availability of power plants at given time
	 * 
	 * @param time at which to return availability
	 * @return availability ratio between effective and nominal available net electricity generation considering planned and
	 *         unplanned availabilities */
	protected double getAvailability(TimeStamp time) {
		return tsAvailability.getValueLinear(time) * unplannedAvailabilityFactor;
	}

	/** Returns the variable costs at a specified time.
	 * 
	 * @param time to return costs for
	 * @return variable costs in EUR per (electric) MWh */
	protected double getVariableCostInEURperMWH(TimeStamp time) {
		return tsVariableCosts.getValueLinear(time);
	}

	/** Calculates CO2 emissions for given used thermal energy
	 * 
	 * @param thermalEnergyInMWH for which to calculate emissions for
	 * @return emitted co2 for the specified thermal energy used */
	public double calcCo2EmissionInTons(double thermalEnergyInMWH) {
		return thermalEnergyInMWH * specificCo2EmissionsInTonsPerThermalMWH;
	}

	/** Returns the cycling costs
	 * 
	 * @return cycling costs in Euro per MW */
	public double getCyclingCostInEURperMW() {
		return cyclingCostInEURperMW;
	}

	/** Returns the fuel type
	 * 
	 * @return fuel type */
	protected String getFuelType() {
		return fuelType;
	}

	/** Returns specific CO2 emissions
	 * 
	 * @return specific CO2 emissions in tons per thermal MWH */
	public double getSpecificCo2EmissionsInTonsPerThermalMWH() {
		return specificCo2EmissionsInTonsPerThermalMWH;
	}

	/** Returns the must-run factor at a specified time, or Zero if it is not defined
	 * 
	 * @param time to return the must-run factor for
	 * @return must-run factor at the requested time */
	public double getMustRunFactor(TimeStamp time) {
		return tsMustRun != null ? tsMustRun.getValueLinear(time) : 0.;
	}

	/** Override {@link #unplannedAvailabilityFactor} with given value
	 * 
	 * @param unplannedAvailabilityFactor to replace template value */
	public void setUnplannedAvailabilityFactor(double unplannedAvailabilityFactor) {
		this.unplannedAvailabilityFactor = unplannedAvailabilityFactor;
	}

	/** Override {@link #cyclingCostInEURperMW} with given value
	 * 
	 * @param cyclingCostInEURperMW to replace template value */
	public void setCyclingCostInEURperMW(double cyclingCostInEURperMW) {
		this.cyclingCostInEURperMW = cyclingCostInEURperMW;
	}

	/** Override {@link #tsAvailability} with given value
	 * 
	 * @param tsAvailability to replace template value */
	public void setPlannedAvailability(TimeSeries tsAvailability) {
		this.tsAvailability = tsAvailability;
	}

	/** Override {@link #tsVariableCosts} with given value
	 * 
	 * @param tsVariableCosts to replace template value */
	public void setTsVariableCosts(TimeSeries tsVariableCosts) {
		this.tsVariableCosts = tsVariableCosts;
	}

	/** Override {@link #tsMustRun} with given value
	 * 
	 * @param tsMustRunFactor to replace the template value */
	public void setMustRunFactor(TimeSeries tsMustRunFactor) {
		this.tsMustRun = tsMustRunFactor;
	}
}