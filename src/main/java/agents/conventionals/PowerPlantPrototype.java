// SPDX-FileCopyrightText: 2021-2026 German Aerospace Center <amiris@dlr.de>
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
	static final String PARAM_EMISSION = "SpecificCo2EmissionsInTperMWH";
	static final String PARAM_OUTAGE = "OutageFactor";
	static final String PARAM_OPEX = "OpexVarInEURperMWH";
	static final String PARAM_CYCLING_COST = "CyclingCostInEURperMW";
	static final String PARAM_MUST_RUN = "MustRunFactor";

	static final Tree parameters = Make.newTree().add(
			FuelsTrader.fuelTypeParameter, Make.newDouble(PARAM_EMISSION), Make.newSeries(PARAM_OUTAGE),
			Make.newSeries(PARAM_OPEX), Make.newDouble(PARAM_CYCLING_COST), Make.newSeries(PARAM_MUST_RUN).optional())
			.buildTree();

	private String fuelType;
	private double specificCo2EmissionsInTonsPerThermalMWH;
	private double cyclingCostInEURperMW;
	private TimeSeries tsOutage;
	private TimeSeries tsVariableCosts;
	private TimeSeries tsMustRun;

	/** Technical specification template for a group conventional power plants */
	public static class PrototypeData {
		/** Type of fuel used */
		public String fuelType;
		/** Specific CO2 emissions in tons per use of 1 thermal MWh of fuel */
		public double specificCo2EmissionsInTonsPerThermalMWH;
		/** Cost for one ramping cycle */
		public double cyclingCostInEURperMW;
		/** Time-dependent outage factor */
		public TimeSeries tsOutage;
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
			specificCo2EmissionsInTonsPerThermalMWH = data.getDouble(PARAM_EMISSION);
			tsOutage = data.getTimeSeries(PARAM_OUTAGE);
			tsVariableCosts = data.getTimeSeries(PARAM_OPEX);
			cyclingCostInEURperMW = data.getDouble(PARAM_CYCLING_COST);
			tsMustRun = data.getTimeSeriesOrDefault(PARAM_MUST_RUN, null);
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
		cyclingCostInEURperMW = prototypeData.cyclingCostInEURperMW;
		tsOutage = prototypeData.tsOutage;
		tsVariableCosts = prototypeData.tsVariableCosts;
		tsMustRun = prototypeData.tsMustRun;
	}

	/** required for {@link Portable}s */
	@Override
	public void addComponentsTo(ComponentCollector collector) {
		collector.storeStrings(fuelType);
		collector.storeDoubles(specificCo2EmissionsInTonsPerThermalMWH, cyclingCostInEURperMW);
		collector.storeTimeSeries(tsOutage, tsVariableCosts);
		if (tsMustRun != null) {
			collector.storeTimeSeries(tsMustRun);
		}
	}

	/** required for {@link Portable}s */
	@Override
	public void populate(ComponentProvider provider) {
		fuelType = provider.nextString();
		specificCo2EmissionsInTonsPerThermalMWH = provider.nextDouble();
		cyclingCostInEURperMW = provider.nextDouble();
		tsOutage = provider.nextTimeSeries();
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
		return 1. - tsOutage.getValueLinear(time);
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

	/** Override {@link #cyclingCostInEURperMW} with given value
	 * 
	 * @param cyclingCostInEURperMW to replace template value */
	public void setCyclingCostInEURperMW(double cyclingCostInEURperMW) {
		this.cyclingCostInEURperMW = cyclingCostInEURperMW;
	}

	/** Override {@link #tsOutage} with given value
	 * 
	 * @param tsOutage to replace template value */
	public void setOutageFactor(TimeSeries tsOutage) {
		this.tsOutage = tsOutage;
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