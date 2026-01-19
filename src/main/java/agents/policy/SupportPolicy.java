// SPDX-FileCopyrightText: 2026 German Aerospace Center <amiris@dlr.de>
//
// SPDX-License-Identifier: Apache-2.0
package agents.policy;

import java.util.ArrayList;
import java.util.List;
import agents.markets.DayAheadMarket;
import agents.plantOperator.RenewablePlantOperator;
import agents.policy.PolicyItem.SupportInstrument;
import agents.trader.renewable.AggregatorTrader;
import communications.message.AwardData;
import communications.message.SupportRequestData;
import communications.message.SupportResponseData;
import communications.message.TechnologySet;
import communications.message.YieldPotential;
import de.dlr.gitlab.fame.agent.Agent;
import de.dlr.gitlab.fame.agent.input.DataProvider;
import de.dlr.gitlab.fame.agent.input.Input;
import de.dlr.gitlab.fame.agent.input.Make;
import de.dlr.gitlab.fame.agent.input.ParameterData;
import de.dlr.gitlab.fame.agent.input.ParameterData.MissingDataException;
import de.dlr.gitlab.fame.agent.input.Tree;
import de.dlr.gitlab.fame.communication.CommUtils;
import de.dlr.gitlab.fame.communication.Contract;
import de.dlr.gitlab.fame.communication.Product;
import de.dlr.gitlab.fame.communication.message.Message;
import de.dlr.gitlab.fame.service.output.ComplexIndex;
import de.dlr.gitlab.fame.service.output.Output;
import de.dlr.gitlab.fame.time.TimePeriod;
import de.dlr.gitlab.fame.time.TimeSpan;
import de.dlr.gitlab.fame.time.TimeStamp;

/** Coordinates various different support policies - distributes according information and assigns support pay-outs.
 * 
 * @author Johannes Kochems, Christoph Schimeczek, Felix Nitsch, Farzad Sarfarazi, Kristina Nienhaus */
public class SupportPolicy extends Agent {
	/** Available energy carriers eligible for support - and "Other" */
	public enum EnergyCarrier {
		/** Photovoltaic */
		PV,
		/** onshore wind turbines */
		WindOn,
		/** offshore wind turbines */
		WindOff,
		/** run-of-river hydro-power */
		RunOfRiver,
		/** Biogas */
		Biogas,
		/** Not eligible for support */
		Other
	}

	/** Products of {@link SupportPolicy} */
	@Product
	public enum Products {
		/** Info on the support scheme to be applied to a set of plants */
		SupportInfo,
		/** Actual pay-out of the support */
		SupportPayout,
		/** Trigger for market value calculation */
		MarketValueCalculation
	}

	@Input private static final Tree parameters = Make.newTree()
			.add(Make.newGroup("SetSupportData").list().add(RenewablePlantOperator.setParameter)
					.addAs(SupportInstrument.FIT.name(), Fit.parameters)
					.addAs(SupportInstrument.MPFIX.name(), Mpfix.parameters)
					.addAs(SupportInstrument.MPVAR.name(), Mpvar.parameters)
					.addAs(SupportInstrument.CFD.name(), Cfd.parameters)
					.addAs(SupportInstrument.CP.name(), Cp.parameters)
					.addAs(SupportInstrument.FINANCIAL_CFD.name(), FinancialCfd.parameters))
			.buildTree();

	@Output
	private enum Outputs {
		MarketValueInEURperMWH
	};

	private enum OutputKey {
		EnergyCarrier
	}

	private static final ComplexIndex<OutputKey> marketValue = ComplexIndex.build(Outputs.MarketValueInEURperMWH,
			OutputKey.class);

	private SetPolicies setPolicies = new SetPolicies();
	private MarketData marketData = new MarketData();

	/** Creates a {@link SupportPolicy}
	 * 
	 * @param dataProvider provides input from config
	 * @throws MissingDataException if any required data is not provided */
	public SupportPolicy(DataProvider dataProvider) throws MissingDataException {
		super(dataProvider);
		ParameterData inputData = parameters.join(dataProvider);
		loadSetSupportData(inputData.getGroupList("SetSupportData"));

		call(this::sendSupportInfo).on(Products.SupportInfo).use(AggregatorTrader.Products.SupportInfoRequest);
		call(this::logYieldPotentials).onAndUse(AggregatorTrader.Products.YieldPotential);
		call(this::logPowerPrice).onAndUse(DayAheadMarket.Products.Awards);
		call(this::calcSupportPayout).on(Products.SupportPayout).use(AggregatorTrader.Products.SupportPayoutRequest);
		call(this::calculateAndStoreMarketValues).on(Products.MarketValueCalculation);
	}

	/** loads all set-specific support instrument configurations from given groupList */
	private void loadSetSupportData(List<ParameterData> groupList) throws MissingDataException {
		for (ParameterData group : groupList) {
			String setType = RenewablePlantOperator.readSet(group);
			for (SupportInstrument instrument : SupportInstrument.values()) {
				setPolicies.addSetPolicyItem(setType, PolicyItem.buildPolicy(instrument, group));
			}
		}
	}

	/** Send {@link TechnologySet}-specific and technology-neutral support data to contracted partner that sent request(s)
	 * 
	 * @param messages incoming request(s) from contracted partners, containing type of technology set the want to be informed of
	 * @param contracts with partners (typically {@link AggregatorTrader}s) to send set-specific support policy details to */
	private void sendSupportInfo(ArrayList<Message> messages, List<Contract> contracts) {
		for (Contract contract : contracts) {
			for (Message message : CommUtils.extractMessagesFrom(messages, contract.getReceiverId())) {
				TechnologySet technologySet = message.getDataItemOfType(TechnologySet.class);
				setPolicies.register(technologySet);
				fulfilNext(contract, setPolicies.getSupportData(technologySet));
			}
		}
	}

	/** Store {@link YieldPotential}s for RES market value calculation
	 * 
	 * @param input incoming reported yield potentials
	 * @param contracts not used */
	private void logYieldPotentials(ArrayList<Message> input, List<Contract> contracts) {
		for (Message message : input) {
			YieldPotential yieldPotential = message.getDataItemOfType(YieldPotential.class);
			marketData.addYieldValue(yieldPotential);
		}
	}

	/** Extract and store power prices and volumes reported from {@link DayAheadMarket}
	 * 
	 * @param input single power price message to read
	 * @param contracts not used */
	private void logPowerPrice(ArrayList<Message> input, List<Contract> contracts) {
		AwardData award = CommUtils.getExactlyOneEntry(input).getDataItemOfType(AwardData.class);
		marketData.addElectricityPrice(award.beginOfDeliveryInterval, award.powerPriceInEURperMWH);
	}

	/** Calculate the support pay-out and distribute it to {@link AggregatorTrader}s; Add market premium information for MPVAR,
	 * MPFIX and CFD scheme
	 * 
	 * @param messages incoming pay-out requests from contracted partners
	 * @param contracts receivers get one pay-out message per incoming pay-out request */
	private void calcSupportPayout(ArrayList<Message> messages, List<Contract> contracts) {
		for (Contract contract : contracts) {
			for (Message message : CommUtils.extractMessagesFrom(messages, contract.getReceiverId())) {
				SupportRequestData supportRequest = message.getDataItemOfType(SupportRequestData.class);
				EnergyCarrier energyCarrier = setPolicies.getEnergyCarrier(supportRequest.setType);
				double marketValue = marketData.calcMarketValue(energyCarrier, supportRequest.accountingPeriod);
				fulfilNext(contract, calcSupportPerRequest(supportRequest, marketValue));
			}
		}
	}

	/** Calculate support and respond to given request
	 * 
	 * @param request reported data to calculated support the pay-out from
	 * @param marketValue in the accounting period associated with the support data request
	 * @return response message to client that sent the given request */
	private SupportResponseData calcSupportPerRequest(SupportRequestData request, double marketValue) {
		String setType = request.setType;
		PolicyItem policyItem = setPolicies.getPolicyItem(setType, request.supportInstrument);
		double infeedInMWH = policyItem.calcEligibleInfeed(marketData.getEnergyPrices(), request);
		double infeedSupportRateInEURperMWH = policyItem.calcInfeedSupportRate(request.accountingPeriod, marketValue);
		double marketPremium = policyItem.isTypeOfMarketPremium() ? infeedSupportRateInEURperMWH : 0;
		double capacityInMW = policyItem.calcEligibleCapacity(request);
		double capacitySupportRateInEURperMW = policyItem.calcCapacitySupportRate(request.accountingPeriod);
		double supportPayoutInEUR = infeedInMWH * infeedSupportRateInEURperMWH
				+ capacityInMW * capacitySupportRateInEURperMW;
		return new SupportResponseData(request, supportPayoutInEUR, marketPremium);
	}

	/** Calculates and stores market values per energy carrier
	 * 
	 * @param messages not used
	 * @param contracts contract with self to trigger this computation */
	private void calculateAndStoreMarketValues(ArrayList<Message> messages, List<Contract> contracts) {
		Contract contract = contracts.get(0);
		TimePeriod accountingInverval = extractAccountingPeriod(now(), contract, 1796L);
		for (EnergyCarrier energyCarrier : marketData.getAllEnergyCarriers()) {
			double marketValue = marketData.calcMarketValue(energyCarrier, accountingInverval);
			store(SupportPolicy.marketValue.key(OutputKey.EnergyCarrier, energyCarrier), marketValue);
		}
		marketData.clearBefore(accountingInverval.getStartTime());
	}

	/** Extract the accounting period for support schemes from contract duration and shift it by given steps
	 * 
	 * @param time to start the search for next delivery time
	 * @param contract to create the accounting period from
	 * @param stepsToShift seconds to shift the time: positive values shift towards the future, negative ones towards the past
	 * @return a TimePeriod matching next delivery period of given contract shifted by given time steps */
	public static TimePeriod extractAccountingPeriod(TimeStamp time, Contract contract, long stepsToShift) {
		TimeStamp endTime;
		TimeStamp nextContractExecutionTime = contract.getNextTimeOfDeliveryAfter(time);
		if (stepsToShift >= 0) {
			endTime = nextContractExecutionTime.laterBy(new TimeSpan(stepsToShift));
		} else {
			endTime = nextContractExecutionTime.earlierBy(new TimeSpan(-stepsToShift));
		}
		TimeSpan duration = contract.getDeliveryInterval();
		TimeStamp startTime = endTime.earlierBy(duration);
		return new TimePeriod(startTime, duration);
	}
}
