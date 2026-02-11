// SPDX-FileCopyrightText: 2023-2026 German Aerospace Center <amiris@dlr.de>
//
// SPDX-License-Identifier: Apache-2.0
package accounting;

import de.dlr.gitlab.fame.agent.input.Make;
import de.dlr.gitlab.fame.agent.input.ParameterData;
import de.dlr.gitlab.fame.agent.input.ParameterData.MissingDataException;
import de.dlr.gitlab.fame.agent.input.Tree;

/** Calculates annual cost for investment annuity and fixed operating expenses
 * 
 * @author Christoph Schimeczek, Johannes Kochems */
public class AnnualCostCalculator {
	/** Input parameters used by {@link AnnualCostCalculator} */
	public static final Tree parameters = Make.newTree().optional()
			.add(Make.newDouble("InvestmentExpensesInEURperMW").optional(),
					Make.newDouble("AnnuityFactor").optional(),
					Make.newDouble("AnnualFixedCostsInEURperMW").optional())
			.buildTree();

	/** Investment expenses for power plant (in nominal terms) */
	private double investmentExpensesInEURperMW = 0;
	/** Annuity factor of power plant (fleet) */
	private double annuityFactor = 0;
	/** Annual fixed costs of power plant (in nominal terms) */
	private double annualFixedCostsInEURperMW = 0;

	/** Returns {@link AnnualCostCalculator} built from group of given input and group name.
	 * <p>
	 * If group is <b>not</b> present in input, or if input is <b>null</b>: returns default object having all parameters set to
	 * Zero.
	 * </p>
	 * 
	 * @param input {@link ParameterData} containing the a group with {@link #parameters}
	 * @param groupName of the group that contains {@link #parameters}
	 * @return new {@link AnnualCostCalculator} parameterised from given input */
	public static AnnualCostCalculator build(ParameterData input, String groupName) {
		if (input != null) {
			try {
				return new AnnualCostCalculator(input.getGroup(groupName));
			} catch (MissingDataException e) {}
		}
		return new AnnualCostCalculator();
	}

	/** Constructs a new AnnualCost item from given parameter group
	 * 
	 * @param input ParameterData group matching structure of {@link #parameters} */
	AnnualCostCalculator(ParameterData input) {
		investmentExpensesInEURperMW = input.getDoubleOrDefault("InvestmentExpensesInEURperMW", 0.0);
		annuityFactor = input.getDoubleOrDefault("AnnuityFactor", 0.0);
		annualFixedCostsInEURperMW = input.getDoubleOrDefault("AnnualFixedCostsInEURperMW", 0.0);
	}

	/** Constructs a new AnnualCost item with default parameters */
	private AnnualCostCalculator() {}

	/** Return investment annuity for given installed capacity
	 *
	 * @param installedCapacityInMW total installed capacity to be considered
	 * @return calculated investment annuity total */
	public double calcInvestmentAnnuityInEUR(double installedCapacityInMW) {
		return annuityFactor * investmentExpensesInEURperMW * installedCapacityInMW;
	}

	/** Return fixed annual cost (e.g. for maintenance) for given installed capacity
	 * 
	 * @param installedCapacityInMW total installed capacity to be considered
	 * @return calculated fixed cost total */
	public double calcFixedCostInEUR(double installedCapacityInMW) {
		return annualFixedCostsInEURperMW * installedCapacityInMW;
	}

	/** @return investment expenses in EUR per MW of installed capacity */
	public double getInvestmentExpensesInEURperMW() {
		return investmentExpensesInEURperMW;
	}

	/** @return annuity factor applied to calculate annual annuity of investment */
	public double getAnnuityFactor() {
		return annuityFactor;
	}

	/** @return fixed annual operation and maintenance cost in EUR per MW of installed capacity */
	public double getFixedCostsInEURperYearMW() {
		return annualFixedCostsInEURperMW;
	}
}
