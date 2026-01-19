// SPDX-FileCopyrightText: 2026 German Aerospace Center <amiris@dlr.de>
//
// SPDX-License-Identifier: Apache-2.0
package agents.plantOperator;

import java.util.ArrayList;
import java.util.List;
import accounting.AnnualCostCalculator;
import agents.trader.Trader;
import communications.message.AmountAtTime;
import de.dlr.gitlab.fame.agent.Agent;
import de.dlr.gitlab.fame.agent.input.DataProvider;
import de.dlr.gitlab.fame.agent.input.Input;
import de.dlr.gitlab.fame.agent.input.Make;
import de.dlr.gitlab.fame.agent.input.ParameterData;
import de.dlr.gitlab.fame.agent.input.Tree;
import de.dlr.gitlab.fame.communication.CommUtils;
import de.dlr.gitlab.fame.communication.Contract;
import de.dlr.gitlab.fame.communication.Product;
import de.dlr.gitlab.fame.communication.message.Message;
import de.dlr.gitlab.fame.service.output.Output;
import de.dlr.gitlab.fame.time.TimeStamp;

/** Handles communication of power plant operators with their contracting {@link Trader}
 * 
 * @author Christoph Schimeczek */
public abstract class PowerPlantOperator extends Agent {
	/** Available products */
	@Product
	public static enum Products {
		/** Cost(s) for the production of a given amount of energy at a specific time */
		MarginalCost,
		/** Forecasted cost(s) for the production of a given amount of energy at a specific time */
		MarginalCostForecast,
		/** Report annual costs (not sent to other agents, but calculated within operator class) */
		AnnualCostReport,
	};

	@Input private static final Tree parameters = Make.newTree().addAs("Refinancing", AnnualCostCalculator.parameters)
			.buildTree();

	/** Available output columns */
	@Output
	protected static enum OutputFields {
		/** Electricity awarded for production */
		AwardedEnergyInMWH,
		/** Electricity offered for production */
		OfferedEnergyInMWH,
		/** Money received */
		ReceivedMoneyInEUR,
		/** Variable operation and maintenance costs */
		VariableCostsInEUR,
		/** Fixed operation and maintenance costs */
		FixedCostsInEUR,
		/** Annual share of investment cost */
		InvestmentAnnuityInEUR
	}

	private final AnnualCostCalculator annualCost;

	/** Creates a {@link PowerPlantOperator}
	 * 
	 * @param dataProvider provides input from config file */
	public PowerPlantOperator(DataProvider dataProvider) {
		super(dataProvider);
		ParameterData input = parameters.join(dataProvider);
		annualCost = AnnualCostCalculator.build(input, "Refinancing");

		call(this::executeDispatch).onAndUse(PowerPlantScheduler.Products.DispatchAssignment);
		call(this::digestPayment).onAndUse(PowerPlantScheduler.Products.Payout);
		call(this::reportCosts).on(Products.AnnualCostReport);
	}

	/** Runs power plant(s) according to received dispatch instructions
	 * 
	 * @param input single message declaring the power to dispatch
	 * @param contracts not used */
	public void executeDispatch(ArrayList<Message> input, List<Contract> contracts) {
		AmountAtTime award = CommUtils.getExactlyOneEntry(input).getDataItemOfType(AmountAtTime.class);
		store(OutputFields.AwardedEnergyInMWH, award.amount);
		double variableCosts = dispatchPlants(award.amount, award.validAt);
		store(OutputFields.VariableCostsInEUR, variableCosts);
	}

	/** Dispatches associated power plants to generate the specified awarded power
	 * 
	 * @param awardedPower amount of power to generate
	 * @param time at which to dispatch
	 * @return total costs for dispatch */
	protected abstract double dispatchPlants(double awardedPower, TimeStamp time);

	/** Writes the income received from an associated trader to output
	 * 
	 * @param input single message payment from an associated trader
	 * @param contracts not used */
	protected void digestPayment(ArrayList<Message> input, List<Contract> contracts) {
		Message message = CommUtils.getExactlyOneEntry(input);
		AmountAtTime payout = message.getDataItemOfType(AmountAtTime.class);
		store(OutputFields.ReceivedMoneyInEUR, payout.amount);
		digestPaymentPerPlant(payout.validAt, payout.amount);
	}

	/** Optional function to digest payments for individual plants of plant operator
	 * 
	 * @param dispatchTime time at which the payment is received
	 * @param totalPaymentInEUR total money to be paid out to plant operator */
	protected void digestPaymentPerPlant(TimeStamp dispatchTime, double totalPaymentInEUR) {}

	/** Write annual costs to output
	 * 
	 * @param input not used
	 * @param contracts not used */
	protected void reportCosts(ArrayList<Message> input, List<Contract> contracts) {
		store(OutputFields.InvestmentAnnuityInEUR, annualCost.calcInvestmentAnnuityInEUR(getInstalledCapacityInMW()));
		store(OutputFields.FixedCostsInEUR, annualCost.calcFixedCostInEUR(getInstalledCapacityInMW()));
	}

	/** @return current installed capacity */
	protected abstract double getInstalledCapacityInMW();
}
