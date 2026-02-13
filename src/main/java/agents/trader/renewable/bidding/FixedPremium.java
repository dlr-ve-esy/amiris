// SPDX-FileCopyrightText: 2023-2026 German Aerospace Center <amiris@dlr.de>
//
// SPDX-License-Identifier: Apache-2.0
package agents.trader.renewable.bidding;

import agents.policy.Mpfix;
import agents.trader.ClientData;
import de.dlr.gitlab.fame.agent.input.ParameterData;
import de.dlr.gitlab.fame.agent.input.ParameterData.MissingDataException;
import de.dlr.gitlab.fame.time.TimeStamp;

/** Bidding strategy for fixed market premium
 * 
 * @author Johannes Kochems, Christoph Schimeczek */
public class FixedPremium extends PremiumBased implements BiddingStrategy {

	/** Creates new {@link FixedPremium} bidding strategy
	 * 
	 * @param input from config
	 * @throws MissingDataException if any required input is missing */
	public FixedPremium(ParameterData input) throws MissingDataException {
		super(input);
	}

	@Override
	public double calcBiddingPrice(double marginalCostInEURperMWH, TimeStamp time, ClientData clientData) {
		Mpfix mpfix = clientData.getSupportData().getPolicyOfType(Mpfix.class);
		assertMaxNumberOfNegativeHoursFeasible(mpfix.getMaxNumberOfNegativeHours());
		double marketPremium = mpfix.getPremium(time);
		int negativeHourRisk = calcPreviousNegativeHours(time) + 1;
		if (mpfix.isEligible(negativeHourRisk)) {
			return marginalCostInEURperMWH - marketPremium;
		} else {
			return Math.max(0, marginalCostInEURperMWH - marketPremium);
		}
	}

	@Override
	protected double calcMarketPremium(ClientData clientData, double __, TimeStamp time) {
		Mpfix mpfix = clientData.getSupportData().getPolicyOfType(Mpfix.class);
		return mpfix.getPremium(time);
	}
}
