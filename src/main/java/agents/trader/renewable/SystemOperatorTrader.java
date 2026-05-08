// SPDX-FileCopyrightText: 2021-2026 German Aerospace Center <amiris@dlr.de>
//
// SPDX-License-Identifier: Apache-2.0
package agents.trader.renewable;

import agents.markets.DayAheadMarket;
import agents.markets.meritOrder.Bid;
import agents.markets.meritOrder.Constants;
import agents.plantOperator.Marginal;
import de.dlr.gitlab.fame.agent.input.DataProvider;
import de.dlr.gitlab.fame.time.TimePeriod;
import de.dlr.gitlab.fame.time.TimeStamp;

/** Offers energy at {@link DayAheadMarket} of renewable power plants using a feed-in tariff support scheme
 *
 * @author Christoph Schimeczek, Ulrich Frey, Marc Deissenroth */
public class SystemOperatorTrader extends AggregatorTrader {

	/** Create new {@link SystemOperatorTrader}
	 * 
	 * @param dataProvider no specific data required here */
	public SystemOperatorTrader(DataProvider dataProvider) {
		super(dataProvider);
	}

	@Override
	protected Bid calcBids(Marginal marginal, TimeStamp targetTime, long producerUuid) {
		double truePowerPotential = marginal.getPowerPotentialInMW();
		double powerOffered = getPowerWithError(truePowerPotential);
		return new Bid(powerOffered, Constants.MINIMAL_PRICE_IN_EUR_PER_MWH, marginal.getMarginalCostInEURperMWH());
	}

	/** Pass through only the support pay-out in a FIT scheme */
	@Override
	protected double applyPayoutStrategy(long plantOperatorId, TimePeriod accountingPeriod, double marketRevenue) {
		return clientMap.get(plantOperatorId).getSupportRevenueInEUR().getOrDefault(accountingPeriod, 0.0);
	}
}
