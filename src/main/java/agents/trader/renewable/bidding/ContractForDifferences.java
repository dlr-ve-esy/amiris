// SPDX-FileCopyrightText: 2023-2026 German Aerospace Center <amiris@dlr.de>
//
// SPDX-License-Identifier: Apache-2.0
package agents.trader.renewable.bidding;

import agents.policy.Cfd;
import agents.policy.PolicyItem.SupportInstrument;
import agents.trader.ClientData;
import de.dlr.gitlab.fame.agent.input.ParameterData;
import de.dlr.gitlab.fame.agent.input.ParameterData.MissingDataException;
import de.dlr.gitlab.fame.time.TimeStamp;

/** Bidding strategy for two-sided contracts for differences
 * 
 * @author Johannes Kochems, Christoph Schimeczek */
public class ContractForDifferences extends PremiumBased implements BiddingStrategy {

	/** Creates new {@link ContractForDifferences} bidding strategy
	 * 
	 * @param input from config
	 * @throws MissingDataException if any required input is missing */
	public ContractForDifferences(ParameterData input)
			throws MissingDataException {
		super(input);
	}

	@Override
	public double calcBiddingPrice(double marginalCostInEURperMWH, TimeStamp time, ClientData clientData) {
		Cfd cfd = clientData.getSupportData().getPolicyOfType(Cfd.class);
		assertMaxNumberOfNegativeHoursFeasible(cfd.getMaxNumberOfNegativeHours());
		double expectedMarketPremium = calcExpectedMarketPremium(clientData, time, SupportInstrument.CFD);
		int negativeHourRisk = calcPreviousNegativeHours(time) + 1;
		if (cfd.isEligible(negativeHourRisk)) {
			return marginalCostInEURperMWH - expectedMarketPremium;
		} else {
			return Math.max(0, marginalCostInEURperMWH - expectedMarketPremium);
		}
	}

	@Override
	protected double calcMarketPremium(ClientData clientData, double marketValue, TimeStamp time) {
		Cfd cfd = clientData.getSupportData().getPolicyOfType(Cfd.class);
		return cfd.calcCfD(marketValue, time);
	}
}
