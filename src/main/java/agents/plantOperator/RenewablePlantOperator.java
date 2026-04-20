// SPDX-FileCopyrightText: 2021-2026 German Aerospace Center <amiris@dlr.de>
//
// SPDX-License-Identifier: Apache-2.0
package agents.plantOperator;

import java.util.ArrayList;
import java.util.List;
import agents.policy.PolicyItem.SupportInstrument;
import agents.policy.SupportPolicy.EnergyCarrier;
import agents.trader.TraderWithClients;
import agents.trader.renewable.AggregatorTrader;
import communications.message.AmountAtTime;
import communications.message.ClearingTimes;
import communications.message.TechnologySet;
import communications.portable.MarginalsAtTime;
import de.dlr.gitlab.fame.agent.input.DataProvider;
import de.dlr.gitlab.fame.agent.input.Input;
import de.dlr.gitlab.fame.agent.input.Make;
import de.dlr.gitlab.fame.agent.input.ParameterBuilder;
import de.dlr.gitlab.fame.agent.input.ParameterData;
import de.dlr.gitlab.fame.agent.input.ParameterData.MissingDataException;
import de.dlr.gitlab.fame.agent.input.Tree;
import de.dlr.gitlab.fame.communication.CommUtils;
import de.dlr.gitlab.fame.communication.Contract;
import de.dlr.gitlab.fame.communication.Product;
import de.dlr.gitlab.fame.communication.message.Message;
import de.dlr.gitlab.fame.data.TimeSeries;
import de.dlr.gitlab.fame.time.TimeStamp;

/** A {@link PowerPlantOperator} for renewable plants
 * 
 * @author Christoph Schimeczek, Johannes Kochems */
public abstract class RenewablePlantOperator extends PowerPlantOperator {
	/** Name for set type input parameter harmonised across agents related to policy sets */
	public static ParameterBuilder setParameter = Make.newStringSet("PolicySet");

	/** Returns PolicySet type read from given input group or specified default if data is missing
	 * 
	 * @param input group that (might) hold the PolicySet input parameter
	 * @param defaultValue is used in case PolicySet is not explicitly specified in this group
	 * @return PolicySet read from input or given default */
	public static String readSet(ParameterData input, String defaultValue) {
		return input.getStringOrDefault("PolicySet", defaultValue);
	}

	/** Returns PolicySet read from given input group.
	 * 
	 * @param input group that holds the PolicySet input parameter
	 * @return PolicySet read from input
	 * @throws MissingDataException if PolicySet is not present in given input group */
	public static String readSet(ParameterData input) throws MissingDataException {
		return input.getString("PolicySet");
	}

	@Input private static final Tree parameters = Make.newTree()
			.add(setParameter.optional(), Make.newEnum("EnergyCarrier", EnergyCarrier.class),
					Make.newEnum("SupportInstrument", SupportInstrument.class).optional(), Make.newSeries("InstalledPowerInMW"),
					Make.newSeries("OpexVarInEURperMWH"))
			.buildTree();

	/** Products of {@link RenewablePlantOperator}s */
	@Product
	public static enum Products {
		/** A registration message to the marketing agent */
		SetRegistration
	};

	/** A group of plants of the same energy carrier and support type attributed to a dedicated PowerPlantOperator */
	private final TechnologySet technologySet;
	/** TimeSeries of total peak production capacity */
	private final TimeSeries tsInstalledPowerInMW;
	/** TimeSeries of variable cost of operation */
	private final TimeSeries tsOpexVarInEURperMWH;

	/** Creates a {@link RenewablePlantOperator}
	 * 
	 * @param dataProvider provides input from config
	 * @throws MissingDataException if any required data is not provided */
	public RenewablePlantOperator(DataProvider dataProvider) throws MissingDataException {
		super(dataProvider);
		ParameterData input = parameters.join(dataProvider);
		tsInstalledPowerInMW = input.getTimeSeries("InstalledPowerInMW");
		tsOpexVarInEURperMWH = input.getTimeSeries("OpexVarInEURperMWH");

		String technologySetType = readSet(input, null);
		EnergyCarrier energyCarrier = input.getEnum("EnergyCarrier", EnergyCarrier.class);
		SupportInstrument supportInstrument = input.getEnumOrDefault("SupportInstrument", SupportInstrument.class, null);
		technologySet = new TechnologySet(technologySetType, energyCarrier, supportInstrument);

		call(this::registerSet).on(Products.SetRegistration);
		call(this::sendSupplyMarginalForecasts).on(PowerPlantOperator.Products.MarginalCostForecast)
				.use(TraderWithClients.Products.ForecastRequestForward);
		call(this::sendSupplyMarginals).on(PowerPlantOperator.Products.MarginalCost)
				.use(TraderWithClients.Products.GateClosureForward);
	}

	/** Registers the {@link TechnologySet} to be marketed by a single associated {@link AggregatorTrader} */
	private void registerSet(ArrayList<Message> input, List<Contract> contracts) {
		Contract contract = CommUtils.getExactlyOneEntry(contracts);
		fulfilNext(contract, technologySet, new AmountAtTime(now(), getInstalledCapacityInMW()));
	}

	/** Prepares supply {@link MarginalForecast}s and sends them to contracted trader
	 * 
	 * @param input single ClearingTimes message that define the time(s) of the forecast to be created
	 * @param contracts one contract with a single trader */
	private void sendSupplyMarginalForecasts(ArrayList<Message> input, List<Contract> contracts) {
		sendSupplyMarginalsMultipleTimes(input, contracts);
	}

	/** Prepares supply {@link Marginal}s at requested times and sends them to single contracted trader
	 * 
	 * @param input single ClearingTimes message that define the time(s) of the marginal costs to be sent
	 * @param contracts one contract with a single trader
	 * @return sum of all power potentials for the requested time(s) */
	private double sendSupplyMarginalsMultipleTimes(ArrayList<Message> input, List<Contract> contracts) {
		Contract contract = CommUtils.getExactlyOneEntry(contracts);
		Message message = CommUtils.getExactlyOneEntry(input);
		ClearingTimes clearingTimes = message.getDataItemOfType(ClearingTimes.class);
		List<TimeStamp> clearingTimeList = clearingTimes.getTimes();
		double totalPowerPotential = 0;
		for (TimeStamp clearingTime : clearingTimeList) {
			Marginal marginal = fulfilSupplyContract(clearingTime, contract);
			totalPowerPotential += marginal.getPowerPotentialInMW();
		}
		return totalPowerPotential;
	}

	/** Prepares supply {@link Marginal}s and sends them to single contracted trader & stores their output
	 * 
	 * @param input single ClearingTimes message that define the time(s) of the marginal costs to be sent
	 * @param contracts single contracted trader to receive calculated marginals */
	private void sendSupplyMarginals(ArrayList<Message> input, List<Contract> contracts) {
		double totalPowerPotential = sendSupplyMarginalsMultipleTimes(input, contracts);
		store(PowerPlantOperator.OutputFields.OfferedEnergyInMWH, totalPowerPotential);
	}

	/** Calculates supply marginals for the given TimeStamp to fulfil the given contract */
	private Marginal fulfilSupplyContract(TimeStamp targetTime, Contract contract) {
		Marginal marginal = calcSingleMarginal(targetTime);
		fulfilNext(contract, new MarginalsAtTime(getId(), targetTime, marginal));
		return marginal;
	}

	/** @param time to calculate costs for
	 * @return single Marginal item calculated for the given time */
	abstract protected Marginal calcSingleMarginal(TimeStamp time);

	/** Returns the installed power at the specified time
	 * 
	 * @param time to get the installed power for
	 * @return installed peak power in MW at the specified time */
	protected double getInstalledPowerAtTimeInMW(TimeStamp time) {
		return tsInstalledPowerInMW.getValueLinear(time);
	}

	/** Returns variable operational expenses at the specified time
	 * 
	 * @param time to calculate the costs for
	 * @return variable operational expenses in EUR per MWh at the specified time */
	protected double getVariableOpexAtTime(TimeStamp time) {
		return tsOpexVarInEURperMWH.getValueEarlierEqual(time);
	}

	@Override
	protected double dispatchPlants(double awardedPower, TimeStamp time) {
		return getVariableOpexAtTime(time) * awardedPower;
	}

	@Override
	protected double getInstalledCapacityInMW() {
		return getInstalledPowerAtTimeInMW(now());
	}
}
