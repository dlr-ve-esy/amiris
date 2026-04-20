// SPDX-FileCopyrightText: 2021-2026 German Aerospace Center <amiris@dlr.de>
//
// SPDX-License-Identifier: Apache-2.0
package agents.plantOperator.renewable;

import java.util.ArrayList;
import java.util.List;
import agents.trader.electrolysis.GreenHydrogenProducer;
import communications.message.PointInTime;
import communications.message.PpaInformation;
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
import de.dlr.gitlab.fame.data.TimeSeries;
import de.dlr.gitlab.fame.time.TimeStamp;

/** A power plant operating on variable renewable energy sources and fulfilling a single private purchase agreement (PPA) to
 * market its energy.
 * 
 * @author Christoph Schimeczek, Johannes Kochems */
public class VariableRenewableOperatorPpa extends VariableRenewableOperator {
	/** Products of {@link VariableRenewableOperatorPpa} */
	@Product
	public static enum Products {
		/** (Perfect) Forecast of Price set in PPA and the future yield potential */
		PpaInformationForecast,
		/** Price set in PPA and the current yield potential */
		PpaInformation,
		/** Logs own PPA potential */
		PotentialLogging,
	};

	@Input private static final Tree parameters = Make.newTree().add(Make.newSeries("PpaPriceInEURperMWH")).buildTree();

	private TimeSeries ppaPriceInEURperMWH;

	/** Creates an {@link VariableRenewableOperatorPpa}
	 * 
	 * @param dataProvider provides input from config
	 * @throws MissingDataException if any required data is not provided */
	public VariableRenewableOperatorPpa(DataProvider dataProvider) throws MissingDataException {
		super(dataProvider);
		ParameterData input = parameters.join(dataProvider);
		ppaPriceInEURperMWH = input.getTimeSeriesOrDefault("PpaPriceInEURperMWH", null);

		call(this::sendPpaMultipleTimes).on(Products.PpaInformationForecast)
				.use(GreenHydrogenProducer.Products.PpaInformationForecastRequest);
		call(this::sendPpaMultipleTimes).on(Products.PpaInformation)
				.use(GreenHydrogenProducer.Products.PpaInformationRequest);
		call(this::logProductionPotential).on(Products.PotentialLogging);
	}

	/** Send {@link PpaInformation} responding to any number of requested times
	 * 
	 * @param input one or multiple messages telling the time
	 * @param contracts one contracted partner */
	private void sendPpaMultipleTimes(ArrayList<Message> input, List<Contract> contracts) {
		Contract contract = CommUtils.getExactlyOneEntry(contracts);
		for (var message : input) {
			var time = message.getDataItemOfType(PointInTime.class).validAt;
			double ppaPrice = ppaPriceInEURperMWH.getValueEarlierEqual(time);
			double availablePower = getInstalledPowerAtTimeInMW(time) * getYieldAtTime(time);
			fulfilNext(contract, new PpaInformation(time, ppaPrice, availablePower, getVariableOpexAtTime(time)));
		}
	}

	/** Logs available power production potential
	 * 
	 * @param input not used
	 * @param contracts one contract with itself */
	private void logProductionPotential(ArrayList<Message> input, List<Contract> contracts) {
		Contract contract = CommUtils.getExactlyOneEntry(contracts);
		TimeStamp time = contract.getNextTimeOfDeliveryAfter(now());
		double availablePower = getInstalledPowerAtTimeInMW(time) * getYieldAtTime(time);
		store(OutputFields.OfferedEnergyInMWH, availablePower);
	}
}
