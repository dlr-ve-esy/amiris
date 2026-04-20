// SPDX-FileCopyrightText: 2021-2026 German Aerospace Center <amiris@dlr.de>
//
// SPDX-License-Identifier: Apache-2.0
package agents.trader;

import java.util.ArrayList;
import java.util.List;
import agents.forecast.MarketForecaster;
import agents.markets.DayAheadMarket;
import agents.markets.DayAheadMarketTrader;
import agents.markets.meritOrder.Bid;
import communications.message.AwardData;
import communications.portable.BidsAtTime;
import de.dlr.gitlab.fame.agent.input.DataProvider;
import de.dlr.gitlab.fame.agent.input.Input;
import de.dlr.gitlab.fame.agent.input.Make;
import de.dlr.gitlab.fame.agent.input.ParameterData;
import de.dlr.gitlab.fame.agent.input.ParameterData.MissingDataException;
import de.dlr.gitlab.fame.agent.input.Tree;
import de.dlr.gitlab.fame.communication.CommUtils;
import de.dlr.gitlab.fame.communication.Contract;
import de.dlr.gitlab.fame.communication.message.Message;
import de.dlr.gitlab.fame.data.TimeSeries;
import de.dlr.gitlab.fame.time.TimeStamp;

/** Purchases energy at {@link DayAheadMarket} according to given {@link TimeSeries} of energy demand
 *
 * @author Christoph Schimeczek, Ulrich Frey, Marc Deissenroth, Johannes Kochems */
public class DemandTrader extends Trader {
	@Input private static final Tree parameters = Make.newTree()
			.add(Make.newGroup("Loads").list().add(Make.newSeries("DemandSeries"), Make.newSeries("ValueOfLostLoad")))
			.buildTree();

	/** Helper class that represents one load TimeSeries with a fixed associated value of lost load */
	private class Load {
		public final TimeSeries tsEnergyDemandInMWHperTimeSegment;
		public final TimeSeries valueOfLostLoadInEURperMWH;

		public Load(TimeSeries demandSeries, TimeSeries valueOfLostLoad) {
			this.tsEnergyDemandInMWHperTimeSegment = demandSeries;
			this.valueOfLostLoadInEURperMWH = valueOfLostLoad;
		}
	}

	private ArrayList<Load> loads = new ArrayList<>();

	/** Creates a {@link DemandTrader}
	 * 
	 * @param dataProvider provides input from config file
	 * @throws MissingDataException if any required data is not provided */
	public DemandTrader(DataProvider dataProvider) throws MissingDataException {
		super(dataProvider);
		ParameterData input = parameters.join(dataProvider);
		for (ParameterData group : input.getGroupList("Loads")) {
			loads.add(new Load(group.getTimeSeries("DemandSeries"), group.getTimeSeries("ValueOfLostLoad")));
		}

		call(this::prepareForecasts).on(Trader.Products.BidsForecast).use(MarketForecaster.Products.ForecastRequest);
		call(this::prepareBids).on(DayAheadMarketTrader.Products.Bids)
				.use(DayAheadMarket.Products.GateClosureInfo);
		call(this::evaluateAwardedDemandBids).onAndUse(DayAheadMarket.Products.Awards);
	}

	/** Prepares forecasts and sends them to the {@link MarketForecaster}
	 * 
	 * @param input single message with ClearingTimes to create bid forecasts for
	 * @param contracts single contract with {@link MarketForecaster} to send the bid forecasts to */
	private void prepareForecasts(ArrayList<Message> input, List<Contract> contracts) {
		submitBidsMultipleTimes(input, contracts);
	}

	/** Calculates and submits demand bids
	 * 
	 * @param input single ClearingTimes message
	 * @param contracts one contracted partner to receive bids
	 * @return sum of all sent demand bid energies */
	private double submitBidsMultipleTimes(ArrayList<Message> input, List<Contract> contracts) {
		Contract contract = CommUtils.getExactlyOneEntry(contracts);
		double totalRequestedEnergyInMWH = 0;
		for (TimeStamp targetTime : extractTimesFromGateClosureInfoMessages(input)) {
			List<Bid> demandBids = prepareBidsFor(targetTime);
			totalRequestedEnergyInMWH += demandBids.stream().mapToDouble(bid -> bid.getEnergyAmountInMWH()).sum();
			fulfilNext(contract, new BidsAtTime(targetTime, getId(), null, demandBids));
		}
		return totalRequestedEnergyInMWH;
	}

	/** Prepares demand bids for the requested time */
	private List<Bid> prepareBidsFor(TimeStamp requestedTime) {
		List<Bid> bids = new ArrayList<>();
		for (Load load : loads) {
			double requestedEnergyInMWH = load.tsEnergyDemandInMWHperTimeSegment.getValueLinear(requestedTime);
			double voll = load.valueOfLostLoadInEURperMWH.getValueLinear(requestedTime);
			bids.add(new Bid(requestedEnergyInMWH, voll));
		}
		return bids;
	}

	/** Prepares demand bids and sends them to the {@link DayAheadMarket}
	 * 
	 * @param input single message with ClearingTimes to create bid forecasts for
	 * @param contracts single contract with {@link MarketForecaster} to send the bid forecasts to */
	private void prepareBids(ArrayList<Message> input, List<Contract> contracts) {
		double totalRequestedEnergyInMWH = submitBidsMultipleTimes(input, contracts);
		store(OutputColumns.RequestedEnergyInMWH, totalRequestedEnergyInMWH);
	}

	/** Writes out the total awarded demand */
	private void evaluateAwardedDemandBids(ArrayList<Message> input, List<Contract> contracts) {
		Message message = CommUtils.getExactlyOneEntry(input);
		double awardedPower = message.getDataItemOfType(AwardData.class).demandEnergyInMWH;
		store(OutputColumns.AwardedEnergyInMWH, awardedPower);
	}
}