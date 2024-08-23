// SPDX-FileCopyrightText: 2024 German Aerospace Center <amiris@dlr.de>
//
// SPDX-License-Identifier: Apache-2.0
package agents.electrolysis;

import java.util.ArrayList;
import java.util.List;
import communications.message.ClearingTimes;
import de.dlr.gitlab.fame.agent.AgentAbility;
import de.dlr.gitlab.fame.communication.CommUtils;
import de.dlr.gitlab.fame.communication.Contract;
import de.dlr.gitlab.fame.communication.Product;
import de.dlr.gitlab.fame.communication.message.Message;
import de.dlr.gitlab.fame.service.output.Output;

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

	/** Forwards one ClearingTimes to connected clients
	 * 
	 * @param input a single ClearingTimes message
	 * @param contracts connected client */
	public default void requestPpaInformation(ArrayList<Message> input, List<Contract> contracts) {
		Message message = CommUtils.getExactlyOneEntry(input);
		Contract contract = CommUtils.getExactlyOneEntry(contracts);
		fulfilNext(contract, message.getDataItemOfType(ClearingTimes.class));
	}

}
