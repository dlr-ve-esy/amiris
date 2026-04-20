// SPDX-FileCopyrightText: 2024-2026 German Aerospace Center <amiris@dlr.de>
//
// SPDX-License-Identifier: Apache-2.0
package agents.trader.electrolysis;

import java.util.ArrayList;
import java.util.List;
import communications.message.ClearingTimes;
import communications.message.PointInTime;
import de.dlr.gitlab.fame.agent.AgentAbility;
import de.dlr.gitlab.fame.communication.CommUtils;
import de.dlr.gitlab.fame.communication.Contract;
import de.dlr.gitlab.fame.communication.Product;
import de.dlr.gitlab.fame.communication.message.Message;
import de.dlr.gitlab.fame.service.output.Output;

/** Ability to produce green hydrogen from green electricity sold by a PPA contract partner
 * 
 * @author Christoph Schimeczek, Johannes Kochems */
public interface GreenHydrogenProducer extends AgentAbility {

	/** Available output columns */
	@Output
	public static enum Outputs {
		/** Amount of electricity consumed in this period for operating the electrolysis unit */
		ConsumedElectricityInMWH,
		/** Total received money for selling electricity in EUR */
		ReceivedMoneyForElectricityInEUR,
	};

	/** Available products */
	@Product
	public static enum Products {
		/** Request for Power Purchase Agreement (PPA) contract data with electricity production unit */
		PpaInformationRequest,
		/** Request for forecasted Power Purchase Agreement (PPA) contract data with electricity production unit */
		PpaInformationForecastRequest
	};

	/** Sends {@link PointInTime}s to connected clients based on {@link ClearingTimes} message
	 * 
	 * @param input a single ClearingTimes message
	 * @param contracts connected client */
	default void requestPpaInformation(ArrayList<Message> input, List<Contract> contracts) {
		Message message = CommUtils.getExactlyOneEntry(input);
		Contract contract = CommUtils.getExactlyOneEntry(contracts);
		ClearingTimes clearingTimes = message.getDataItemOfType(ClearingTimes.class);
		for (var clearingTime : clearingTimes.getTimes()) {
			fulfilNext(contract, new PointInTime(clearingTime));
		}
	}
}
