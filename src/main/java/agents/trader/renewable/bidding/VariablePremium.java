// SPDX-FileCopyrightText: 2026 German Aerospace Center <amiris@dlr.de>
//
// SPDX-License-Identifier: Apache-2.0
package agents.trader.renewable.bidding;

import agents.policy.Mpvar;
import agents.policy.PolicyItem.SupportInstrument;
import agents.trader.ClientData;
import de.dlr.gitlab.fame.agent.input.ParameterData;
import de.dlr.gitlab.fame.agent.input.ParameterData.MissingDataException;
import de.dlr.gitlab.fame.time.TimeStamp;

/** Bidding strategy for variable market premium
 * 
 * @author Johannes Kochems, Christoph Schimeczek */
public class VariablePremium extends PremiumBased implements BiddingStrategy {

	/** Creates new {@link VariablePremium} bidding strategy
	 * 
	 * @param input from config
	 * @throws MissingDataException if any required input is missing */
	public VariablePremium(ParameterData input) throws MissingDataException {
		super(input);
	}

	@Override
	public double calcBiddingPrice(double marginalCostInEURperMWH, TimeStamp time, ClientData clientData) {
		Mpvar mpvar = clientData.getSupportData().getPolicyOfType(Mpvar.class);
		assertMaxNumberOfNegativeHoursFeasible(mpvar.getMaxNumberOfNegativeHours());
		double expectedMarketPremium = Math.max(0, calcExpectedMarketPremium(clientData, time, SupportInstrument.MPVAR));
		int negativeHourRisk = calcPreviousNegativeHours(time) + 1;
		if (mpvar.isEligible(negativeHourRisk)) {
			return marginalCostInEURperMWH - expectedMarketPremium;
		} else {
			return Math.max(0, marginalCostInEURperMWH - expectedMarketPremium);
		}
	}

	@Override
	protected double calcMarketPremium(ClientData clientData, double marketValue, TimeStamp time) {
		Mpvar mpvar = clientData.getSupportData().getPolicyOfType(Mpvar.class);
		return mpvar.calcMpVar(marketValue, time);
	}
}
