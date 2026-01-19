// SPDX-FileCopyrightText: 2026 German Aerospace Center <amiris@dlr.de>
//
// SPDX-License-Identifier: Apache-2.0
package agents.trader;

import java.security.InvalidParameterException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map.Entry;
import java.util.TreeMap;
import agents.forecast.MarketForecaster;
import agents.markets.DayAheadMarket;
import agents.markets.DayAheadMarketTrader;
import agents.markets.meritOrder.Bid;
import agents.markets.meritOrder.Constants;
import agents.plantOperator.ConventionalPlantOperator;
import agents.plantOperator.Marginal;
import agents.plantOperator.PowerPlantOperator;
import agents.plantOperator.PowerPlantScheduler;
import communications.message.AmountAtTime;
import communications.message.AwardData;
import communications.portable.BidsAtTime;
import communications.portable.MarginalsAtTime;
import de.dlr.gitlab.fame.agent.input.DataProvider;
import de.dlr.gitlab.fame.agent.input.Input;
import de.dlr.gitlab.fame.agent.input.Make;
import de.dlr.gitlab.fame.agent.input.ParameterData;
import de.dlr.gitlab.fame.agent.input.ParameterData.MissingDataException;
import de.dlr.gitlab.fame.agent.input.Tree;
import de.dlr.gitlab.fame.communication.CommUtils;
import de.dlr.gitlab.fame.communication.Contract;
import de.dlr.gitlab.fame.communication.message.Message;
import de.dlr.gitlab.fame.logging.Logging;
import de.dlr.gitlab.fame.time.TimeStamp;
import util.Util;

/** Sells energy of one conventional PowerPlantOperator at the {@link DayAheadMarket}
 *
 * @author Christoph Schimeczek, Marc Deissenroth, Ulrich Frey */
public class ConventionalTrader extends TraderWithClients implements PowerPlantScheduler {
	@Input private static final Tree parameters = Make.newTree()
			.add(Make.newDouble("minMarkup").optional(), Make.newDouble("maxMarkup").optional()).buildTree();

	private double minMarkup;
	private double maxMarkup;

	/** Creates a {@link ConventionalTrader}
	 * 
	 * @param dataProvider provides input from config
	 * @throws MissingDataException if any required data is not */
	public ConventionalTrader(DataProvider dataProvider) throws MissingDataException {
		super(dataProvider);
		ParameterData input = parameters.join(dataProvider);
		minMarkup = input.getDoubleOrDefault("minMarkup", 0.);
		maxMarkup = input.getDoubleOrDefault("maxMarkup", 0.);
		ensureValidMarkups();

		call(this::sendForecastBids).on(Trader.Products.BidsForecast)
				.use(PowerPlantOperator.Products.MarginalCostForecast);
		call(this::sendBids).on(DayAheadMarketTrader.Products.Bids).use(PowerPlantOperator.Products.MarginalCost);
		call(this::assignDispatch).on(PowerPlantScheduler.Products.DispatchAssignment).use(DayAheadMarket.Products.Awards);
		call(this::payout).on(PowerPlantScheduler.Products.Payout).use(DayAheadMarket.Products.Awards);
	}

	/** @throws RuntimeException if {@link #minMarkup} > {@link #maxMarkup} */
	private void ensureValidMarkups() {
		try {
			Util.ensureValidRange(minMarkup, maxMarkup);
		} catch (InvalidParameterException e) {
			throw Logging.logFatalException(logger, "Markups of " + this + ":: " + e.getMessage());
		}
	}

	/** Prepares forecast bids grouped by time stamps
	 * 
	 * @param messages marginal cost forecasts from associated PowerPlantOperators
	 * @param contractsToFulfill single contract, typically with a {@link MarketForecaster} */
	private void sendForecastBids(ArrayList<Message> messages, List<Contract> contracts) {
		Contract contractToFulfil = CommUtils.getExactlyOneEntry(contracts);
		TreeMap<TimeStamp, ArrayList<MarginalsAtTime>> marginalsByTimeStamp = sortMarginalsByTimeStamp(messages);
		for (Entry<TimeStamp, ArrayList<MarginalsAtTime>> entry : marginalsByTimeStamp.entrySet()) {
			List<Bid> supplyBids = prepareBids(entry.getValue());
			fulfilNext(contractToFulfil, new BidsAtTime(entry.getKey(), getId(), supplyBids, null));
		}
	}

	/** Create {@link BidData bids} from given marginals: add markups to regular bids, place must-run bids at minimal market price
	 * 
	 * @param marginals Marginal costs items from power plant operators - must all be valid for the same time
	 * @return Bids created from the marginals */
	private List<Bid> prepareBids(ArrayList<MarginalsAtTime> marginals) {
		ArrayList<Marginal> sortedMarginals = getSortedMarginalList(marginals);
		ArrayList<Double> markups = Util.linearInterpolation(minMarkup, maxMarkup, sortedMarginals.size() - 1);
		List<Bid> bids = new ArrayList<>(sortedMarginals.size());
		int markUpIndex = 0;
		for (Marginal marginal : sortedMarginals) {
			double offeredPriceInEURperMWH;
			double marginalCostInEURperMWH;
			if (marginal.getMarginalCostInEURperMWH() == ConventionalPlantOperator.MUST_RUN_COST) {
				offeredPriceInEURperMWH = Constants.MINIMAL_PRICE_IN_EUR_PER_MWH;
				marginalCostInEURperMWH = 0;
			} else {
				offeredPriceInEURperMWH = marginal.getMarginalCostInEURperMWH() + markups.get(markUpIndex);
				marginalCostInEURperMWH = marginal.getMarginalCostInEURperMWH();
				markUpIndex++;
			}
			bids.add(new Bid(marginal.getPowerPotentialInMW(), offeredPriceInEURperMWH, marginalCostInEURperMWH));
		}
		return bids;
	}

	/** Sends supply {@link Bid}s to {@link DayAheadMarket} and stores offered power
	 * 
	 * @param messages marginal cost data from client
	 * @param contracts single {@link DayAheadMarket} to send bids to */
	private void sendBids(ArrayList<Message> messages, List<Contract> contracts) {
		Contract contractToFulfil = CommUtils.getExactlyOneEntry(contracts);
		ArrayList<MarginalsAtTime> marginals = extractMarginalsAtTime(messages);
		if (marginals.size() > 0) {
			List<Bid> supplyBids = prepareBids(marginals);
			TimeStamp deliveryTime = marginals.get(0).getDeliveryTime();
			fulfilNext(contractToFulfil, new BidsAtTime(deliveryTime, getId(), supplyBids, null));
			double totalOfferedPowerInMW = supplyBids.stream().mapToDouble(bid -> bid.getEnergyAmountInMWH()).sum();
			store(OutputColumns.OfferedEnergyInMWH, totalOfferedPowerInMW);
		}
	}

	/** Assigns dispatch from {@link DayAheadMarket} to power plant operators and writes information to output
	 * 
	 * @param messages one single message containing award information (price, awarded power)
	 * @param contracts single contract with associated PowerPlantOperator to order the energy to deliver */
	private void assignDispatch(ArrayList<Message> messages, List<Contract> contracts) {
		Message message = CommUtils.getExactlyOneEntry(messages);
		Contract contract = CommUtils.getExactlyOneEntry(contracts);

		AwardData award = message.getDataItemOfType(AwardData.class);
		fulfilNext(contract, new AmountAtTime(award.beginOfDeliveryInterval, award.supplyEnergyInMWH));
		store(OutputColumns.AwardedEnergyInMWH, award.supplyEnergyInMWH);
	}

	/** Sends pay-out to {@link ConventionalPlantOperator}
	 * 
	 * @param messages one single message containing award information (price, awarded power)
	 * @param contracts single contract with associated PowerPlantOperator to send its pay-out */
	private void payout(ArrayList<Message> messages, List<Contract> contracts) {
		Message message = CommUtils.getExactlyOneEntry(messages);
		Contract contract = CommUtils.getExactlyOneEntry(contracts);
		AwardData award = message.getDataItemOfType(AwardData.class);
		double payoutInEUR = award.supplyEnergyInMWH * award.powerPriceInEURperMWH;
		fulfilNext(contract, new AmountAtTime(award.beginOfDeliveryInterval, payoutInEUR));
	}
}