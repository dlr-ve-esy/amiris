// SPDX-FileCopyrightText: 2024 German Aerospace Center <amiris@dlr.de>
//
// SPDX-License-Identifier: Apache-2.0
package agents.trader;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import agents.electrolysis.GreenHydrogenMonthly;
import agents.electrolysis.GreenHydrogenProducer;
import agents.flexibility.DispatchSchedule;
import agents.flexibility.Strategist;
import agents.markets.meritOrder.Bid;
import agents.plantOperator.PowerPlantScheduler;
import agents.plantOperator.renewable.VariableRenewableOperator;
import communications.message.AmountAtTime;
import communications.message.AwardData;
import communications.message.PointInTime;
import communications.message.PpaInformation;
import communications.portable.BidsAtTime;
import de.dlr.gitlab.fame.agent.input.DataProvider;
import de.dlr.gitlab.fame.agent.input.ParameterData.MissingDataException;
import de.dlr.gitlab.fame.communication.CommUtils;
import de.dlr.gitlab.fame.communication.Contract;
import de.dlr.gitlab.fame.communication.message.Message;
import de.dlr.gitlab.fame.time.TimePeriod;
import de.dlr.gitlab.fame.time.TimeStamp;

/** GreenHydrogenTraderMonthly is a type of ElectrolysisTrader that operates an electrolyzer unit to produce hydrogen from green
 * electricity purchased via a PPA ensuring monthly equivalence
 * 
 * @author Christoph Schimeczek, Johannes Kochems */
public class GreenHydrogenTraderMonthly extends ElectrolysisTrader implements GreenHydrogenProducer {

	private double lastUsedResElectricityInMWH = 0;

	public GreenHydrogenTraderMonthly(DataProvider dataProvider) throws MissingDataException {
		super(dataProvider);

		call(this::requestPpaForecast).on(GreenHydrogenProducer.Products.PpaInformationForecastRequest);
		call(this::updatePpaForecast).on(VariableRenewableOperator.Products.PpaInformationForecast)
				.use(VariableRenewableOperator.Products.PpaInformationForecast);
		call(this::assignDispatch).on(PowerPlantScheduler.Products.DispatchAssignment);
		call(this::payoutClient).on(PowerPlantScheduler.Products.Payout);
	}

	private void requestPpaForecast(ArrayList<Message> input, List<Contract> contracts) {
		Contract contract = CommUtils.getExactlyOneEntry(contracts);
		TimePeriod nextTime = new TimePeriod(now().laterBy(electricityForecastRequestOffset),
				Strategist.OPERATION_PERIOD);
		ArrayList<TimeStamp> missingForecastTimes = getStrategist().getTimesMissingPpaForecastTimes(nextTime);
		for (TimeStamp missingForecastTime : missingForecastTimes) {
			PointInTime pointInTime = new PointInTime(missingForecastTime);
			fulfilNext(contract, pointInTime);
		}
	}

	private void updatePpaForecast(ArrayList<Message> input, List<Contract> contracts) {
		for (Message inputMessage : input) {
			PpaInformation ppaForecast = inputMessage.getDataItemOfType(PpaInformation.class);
			TimePeriod timeSegment = new TimePeriod(ppaForecast.validAt, Strategist.OPERATION_PERIOD);
			getStrategist().storePpaForecast(timeSegment, ppaForecast);
		}
	}

	@Override
	protected void prepareBids(ArrayList<Message> input, List<Contract> contracts) {
		Contract contractToFulfil = CommUtils.getExactlyOneEntry(contracts);
		for (TimeStamp targetTime : extractTimesFromGateClosureInfoMessages(input)) {
			DispatchSchedule schedule = getStrategist().getValidSchedule(targetTime);
			Bid demandBid = prepareHourlyDemandBid(targetTime, schedule);
			Bid supplyBid = prepareHourlySupplyBid(targetTime, schedule);
			fulfilNext(contractToFulfil,
					new BidsAtTime(targetTime, getId(), Arrays.asList(supplyBid), Arrays.asList(demandBid)));
		}
	}

	private Bid prepareHourlyDemandBid(TimeStamp targetTime, DispatchSchedule schedule) {
		double demandPower = schedule.getScheduledChargingPowerInMW(targetTime);
		double price = schedule.getScheduledBidInHourInEURperMWH(targetTime);
		store(OutputColumns.RequestedEnergyInMWH, demandPower);
		return new Bid(demandPower, price, Double.NaN);
	}

	private Bid prepareHourlySupplyBid(TimeStamp targetTime, DispatchSchedule schedule) {
		double supplyPower = schedule.getScheduledDischargingPowerInMW(targetTime);
		double price = schedule.getScheduledBidInHourInEURperMWH(targetTime);
		store(OutputColumns.OfferedEnergyInMWH, supplyPower);
		return new Bid(supplyPower, price, Double.NaN);
	}

	@Override
	protected void digestAwards(ArrayList<Message> messages, List<Contract> contracts) {
		Message awardMessage = CommUtils.getExactlyOneEntry(messages);
		AwardData award = awardMessage.getDataItemOfType(AwardData.class);

		lastClearingTime = award.beginOfDeliveryInterval;
		double netAwardedEnergyInMWH = award.demandEnergyInMWH - award.supplyEnergyInMWH;
		PpaInformation ppa = getPpa(lastClearingTime);
		double availableEnergy = netAwardedEnergyInMWH + ppa.yieldPotentialInMWH;
		double electrolyzerDispatch = electrolyzer.calcCappedElectricDemandInMW(availableEnergy, lastClearingTime);
		lastUsedResElectricityInMWH = electrolyzerDispatch - netAwardedEnergyInMWH;
		lastProducedHydrogenInMWH = electrolyzer.calcProducedHydrogenOneHour(electrolyzerDispatch, lastClearingTime);
		strategist.updateProducedHydrogenTotal(lastProducedHydrogenInMWH);

		double ppaCosts = ppa.yieldPotentialInMWH * ppa.priceInEURperMWH;
		double marketCosts = award.powerPriceInEURperMWH * award.demandEnergyInMWH;
		double marketRevenue = award.powerPriceInEURperMWH * award.supplyEnergyInMWH;
		store(OutputColumns.AwardedEnergyInMWH, netAwardedEnergyInMWH);
		store(ElectrolysisTrader.Outputs.ProducedHydrogenInMWH, lastProducedHydrogenInMWH);
		store(FlexibilityTrader.Outputs.VariableCostsInEUR, ppaCosts + marketCosts);
		store(GreenHydrogenProducer.Outputs.ReceivedMoneyForElectricityInEUR, marketRevenue);
	}

	/** @param time to request PpaInformation for
	 * @return PpaInformation for given time stored in strategist */
	private PpaInformation getPpa(TimeStamp time) {
		return getStrategist().getPpaForPeriod(new TimePeriod(time, Strategist.OPERATION_PERIOD));
	}

	/** Pay client according to PPA specification
	 * 
	 * @param messages none
	 * @param contracts payment to client from PPA */
	private void payoutClient(ArrayList<Message> messages, List<Contract> contracts) {
		Contract contract = CommUtils.getExactlyOneEntry(contracts);
		PpaInformation ppa = getPpa(lastClearingTime);
		double payment = ppa.yieldPotentialInMWH * ppa.priceInEURperMWH;
		fulfilNext(contract, new AmountAtTime(ppa.validAt, payment));
	}

	private void assignDispatch(ArrayList<Message> messages, List<Contract> contracts) {
		Contract contract = CommUtils.getExactlyOneEntry(contracts);
		fulfilNext(contract, new AmountAtTime(lastClearingTime, lastUsedResElectricityInMWH));
	}

	@Override
	protected GreenHydrogenMonthly getStrategist() {
		return (GreenHydrogenMonthly) strategist;
	}
}
