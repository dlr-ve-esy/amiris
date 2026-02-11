// SPDX-FileCopyrightText: 2022-2026 German Aerospace Center <amiris@dlr.de>
//
// SPDX-License-Identifier: Apache-2.0
package agents.plantOperator;

/** Conveys results of dispatching conventional power plants
 * 
 * @author Christoph Schimeczek */
public class DispatchResult {
	static final String ERR_RESET = "Not allowed to reset costs of power plant dispatch";

	private double electricityGenerationInMWH = 0;
	private double variableCostInEUR = 0;
	private double fuelConsumptionInThermalMWH = 0;
	private double co2EmissionsInTons = 0;

	/** Create {@link DispatchResult} with all values initialised to 0 */
	public DispatchResult() {};

	/** Create new {@link DispatchResult} based on given values
	 * 
	 * @param electricityGenerationInMWH electric generation of the dispatch
	 * @param variableCostInEUR cost total for fuels, ramping, emissions allowances and variable OPEX
	 * @param fuelConsumptionInThermalMWH required fuel amount for the power plant
	 * @param co2EmissionsInTons CO2 emissions cause by the dispatch */
	public DispatchResult(double electricityGenerationInMWH, double variableCostInEUR, double fuelConsumptionInThermalMWH,
			double co2EmissionsInTons) {
		this.electricityGenerationInMWH = electricityGenerationInMWH;
		this.variableCostInEUR = variableCostInEUR;
		this.fuelConsumptionInThermalMWH = fuelConsumptionInThermalMWH;
		this.co2EmissionsInTons = co2EmissionsInTons;
	}

	/** @return electric generation of the dispatch */
	public double getElectricityGenerationInMWH() {
		return electricityGenerationInMWH;
	}

	/** @return cost total for fuels, ramping, emissions allowances and variable OPEX */
	public double getVariableCostsInEUR() {
		return variableCostInEUR;
	}

	/** @return required fuel amount for the power plant */
	public double getFuelConsumptionInThermalMWH() {
		return fuelConsumptionInThermalMWH;
	}

	/** @return CO2 emissions cause by the dispatch */
	public double getCo2EmissionsInTons() {
		return co2EmissionsInTons;
	}

	/** Add items from given {@link DispatchResult} to this one
	 * 
	 * @param other whose items to add to this */
	public void add(DispatchResult other) {
		this.electricityGenerationInMWH += other.electricityGenerationInMWH;
		this.variableCostInEUR += other.variableCostInEUR;
		this.fuelConsumptionInThermalMWH += other.fuelConsumptionInThermalMWH;
		this.co2EmissionsInTons += other.co2EmissionsInTons;
	}
}
