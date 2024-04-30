// SPDX-FileCopyrightText: 2024 German Aerospace Center <amiris@dlr.de>
//
// SPDX-License-Identifier: Apache-2.0
package agents.trader.renewable;

import agents.markets.DayAheadMarket;
import agents.markets.meritOrder.Bid.Type;
import communications.message.BidData;
import communications.message.Marginal;
import de.dlr.gitlab.fame.agent.input.DataProvider;
import de.dlr.gitlab.fame.agent.input.Make;
import de.dlr.gitlab.fame.agent.input.ParameterData;
import de.dlr.gitlab.fame.agent.input.ParameterData.MissingDataException;
import de.dlr.gitlab.fame.agent.input.Tree;
import de.dlr.gitlab.fame.data.TimeSeries;
import de.dlr.gitlab.fame.time.TimePeriod;
import de.dlr.gitlab.fame.time.TimeStamp;

/** Offers energy at {@link DayAheadMarket} according to given {@link TimeSeries} of renewable power plants and thereby obtaining
 * no support payments
 *
 * @author Johannes Kochems */
public class NoSupportTrader extends AggregatorTrader {
	static final Tree parameters = Make.newTree().add(Make.newDouble("ShareOfRevenues")).buildTree();

	/** Share of market revenues the NoSupportTrader keeps to himself */
	private final double shareOfRevenues;

	/** Creates a {@link NoSupportTrader}
	 * 
	 * @param dataProvider provides input from config
	 * @throws MissingDataException if any required data is not provided */
	public NoSupportTrader(DataProvider dataProvider) throws MissingDataException {
		super(dataProvider);
		ParameterData input = parameters.join(dataProvider);
		shareOfRevenues = input.getDouble("ShareOfRevenues");
	}

	@Override
	protected BidData calcBids(Marginal marginal, TimeStamp targetTime, long producerUuid, boolean hasErrors) {
		double truePowerPotential = marginal.getPowerPotentialInMW();
		double powerOffered = getPowerWithError(truePowerPotential, hasErrors);
		return new BidData(powerOffered, marginal.getMarginalCostInEURperMWH(), marginal.getMarginalCostInEURperMWH(),
				truePowerPotential, getId(), producerUuid, Type.Supply, targetTime);
	}

	/** Pass through only the market revenues since there is no support payment */
	@Override
	protected double applyPayoutStrategy(long plantOperatorId, TimePeriod accountingPeriod, double marketRevenue) {
		return marketRevenue * (1 - shareOfRevenues);
	}
}
