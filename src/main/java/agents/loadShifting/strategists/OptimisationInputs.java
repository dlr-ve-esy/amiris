// SPDX-FileCopyrightText: 2026 German Aerospace Center <amiris@dlr.de>
//
// SPDX-License-Identifier: Apache-2.0
package agents.loadShifting.strategists;

import util.JSONable;

/** Inputs sent to external load shifting optimisation model
 *
 * @author Johannes Kochems, Christoph Schimeczek */
public class OptimisationInputs implements JSONable {
	enum Solver {
		gurobi, cplex, cbc, glpk
	}

	private double peak_load_price;
	private int max_shifting_time;
	private int interference_time;
	private double peak_demand_before;
	private double max_capacity_down;
	private double max_capacity_up;
	private double efficiency;
	private boolean activate_annual_limits;
	private int max_activations;
	private double initial_energy_level;
	private Solver solver;

	private double[] normalized_baseline_load;
	private double[] energy_price;
	private double[] availability_up;
	private double[] availability_down;
	private double[] variable_costs_down;
	private double[] variable_costs_up;
	private double[] price_sensitivity;

	public double getPeak_load_price() {
		return peak_load_price;
	}

	public void setPeak_load_price(double peak_load_price) {
		this.peak_load_price = peak_load_price;
	}

	public double[] getVariable_costs_down() {
		return variable_costs_down;
	}

	public void setVariable_costs_down(double[] variable_costs_down) {
		this.variable_costs_down = variable_costs_down;
	}

	public double[] getVariable_costs_up() {
		return variable_costs_up;
	}

	public void setVariable_costs_up(double[] variable_costs_up) {
		this.variable_costs_up = variable_costs_up;
	}

	public int getMax_shifting_time() {
		return max_shifting_time;
	}

	public void setMax_shifting_time(int max_shifting_time) {
		this.max_shifting_time = max_shifting_time;
	}

	public int getInterference_time() {
		return interference_time;
	}

	public void setInterference_time(int interference_time) {
		this.interference_time = interference_time;
	}

	public double getPeak_demand_before() {
		return peak_demand_before;
	}

	public void setPeak_demand_before(double peak_demand_before) {
		this.peak_demand_before = peak_demand_before;
	}

	public double getMax_capacity_down() {
		return max_capacity_down;
	}

	public void setMax_capacity_down(double max_capacity_down) {
		this.max_capacity_down = max_capacity_down;
	}

	public double getMax_capacity_up() {
		return max_capacity_up;
	}

	public void setMax_capacity_up(double max_capacity_up) {
		this.max_capacity_up = max_capacity_up;
	}

	public double getEfficiency() {
		return efficiency;
	}

	public void setEfficiency(double efficiency) {
		this.efficiency = efficiency;
	}

	public boolean isActivate_annual_limits() {
		return activate_annual_limits;
	}

	public void setActivate_annual_limits(boolean activate_annual_limits) {
		this.activate_annual_limits = activate_annual_limits;
	}

	public Solver getSolver() {
		return solver;
	}

	public void setSolver(Solver solver) {
		this.solver = solver;
	}

	/** @return normalised baseline load */
	public double[] getNormalized_baseline_load() {
		return normalized_baseline_load;
	}

	public void setNormalized_baseline_load(double[] normalized_baseline_load) {
		this.normalized_baseline_load = normalized_baseline_load;
	}

	public double[] getEnergy_price() {
		return energy_price;
	}

	public void setEnergy_price(double[] energy_price) {
		this.energy_price = energy_price;
	}

	public double[] getAvailability_up() {
		return availability_up;
	}

	public void setAvailability_up(double[] availability_up) {
		this.availability_up = availability_up;
	}

	public double[] getAvailability_down() {
		return availability_down;
	}

	public void setAvailability_down(double[] availability_down) {
		this.availability_down = availability_down;
	}

	public int getMax_activations() {
		return max_activations;
	}

	public void setMax_activations(int max_activations) {
		this.max_activations = max_activations;
	}

	public double getInitial_energy_level() {
		return initial_energy_level;
	}

	public void setInitial_energy_level(double initial_energy_level) {
		this.initial_energy_level = initial_energy_level;
	}

	public double[] getPrice_sensitivity() {
		return price_sensitivity;
	}

	public void setPrice_sensitivity(double[] price_sensitivity) {
		this.price_sensitivity = price_sensitivity;
	}
}
