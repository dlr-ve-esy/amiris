// SPDX-FileCopyrightText: 2023-2026 German Aerospace Center <amiris@dlr.de>
//
// SPDX-License-Identifier: Apache-2.0
package agents.trader.electrolysis;

import agents.electrolysis.Electrolyzer;
import agents.electrolysis.ElectrolyzerStrategist;
import agents.forecast.sensitivity.SensitivityForecastClient;
import agents.markets.FuelsTrader;
import agents.plantOperator.PowerPlantScheduler;
import agents.policy.hydrogen.HydrogenSupportClient;
import agents.trader.Trader;
import communications.message.HydrogenPolicyRegistration;
import communications.portable.HydrogenSupportData;
import de.dlr.gitlab.fame.agent.input.DataProvider;
import de.dlr.gitlab.fame.agent.input.Input;
import de.dlr.gitlab.fame.agent.input.Make;
import de.dlr.gitlab.fame.agent.input.ParameterData;
import de.dlr.gitlab.fame.agent.input.ParameterData.MissingDataException;
import de.dlr.gitlab.fame.agent.input.Tree;
import de.dlr.gitlab.fame.service.output.Output;
import de.dlr.gitlab.fame.time.TimeSpan;
import de.dlr.gitlab.fame.time.Constants.Interval;

/** A flexible Trader demanding electricity and producing hydrogen from it via electrolysis using sensitivity forecasts to
 * consider competition of flexibility options.
 * 
 * @author Christoph Schimeczek, Johannes Kochems */
public class ElectrolysisTraderSensitivity extends Trader
		implements SensitivityForecastClient, FuelsTrader, PowerPlantScheduler, HydrogenSupportClient {
	@Input private static final Tree parameters = Make.newTree().add(FuelsTrader.fuelTypeParameter)
			.addAs("Device", Electrolyzer.parameters)
			.addAs("Strategy", ElectrolyzerStrategist.parameters)
			.addAs("Support", HydrogenSupportClient.parameters).buildTree();

	private static final TimeSpan OPERATION_PERIOD = new TimeSpan(1, Interval.HOURS);
	
	/** Available output columns */
	@Output
	protected static enum Outputs {
		/** Price at which electricity offers are placed at the day-ahead market */
		OfferedElectricityPriceInEURperMWH,
		/** Amount of hydrogen produced */
		ProducedHydrogenInMWH,
		/** Total received money for selling hydrogen in EUR */
		ReceivedMoneyForHydrogenInEUR
	}

	/** Creates a new {@link ElectrolysisTraderSensitivity} based on given input parameters
	 * 
	 * @param data configured input
	 * @throws MissingDataException if any required input is missing */
	public ElectrolysisTraderSensitivity(DataProvider data) throws MissingDataException {
		super(data);
		ParameterData input = parameters.join(data);

	}

	@Override
	public HydrogenPolicyRegistration getRegistrationData() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void saveSupportData(HydrogenSupportData hydrogenSupportData) {
		// TODO Auto-generated method stub
		
	}

}
