// SPDX-FileCopyrightText: 2025-2026 German Aerospace Center <amiris@dlr.de>
//
// SPDX-License-Identifier: Apache-2.0
package communications.message;

import agents.forecast.sensitivity.SensitivityForecastProvider.ForecastType;
import de.dlr.gitlab.fame.communication.message.DataItem;
import de.dlr.gitlab.fame.protobuf.Agent.ProtoDataItem;
import de.dlr.gitlab.fame.protobuf.Agent.ProtoDataItem.Builder;

/** Registration message of a forecast client at a forecast provider
 * 
 * @author Christoph Schimeczek */
public class ForecastClientRegistration extends DataItem {
	public final double amount;
	public final ForecastType type;

	/** Create {@link ForecastClientRegistration} messagef
	 * 
	 * @param type of forecast to be delivered to the client by the forecaster
	 * @param maximumEnergyPerIntervalInMWH that can be charged or discharged by the client during a market interval */
	public ForecastClientRegistration(ForecastType type, double maximumEnergyPerIntervalInMWH) {
		this.type = type;
		this.amount = maximumEnergyPerIntervalInMWH;
	}

	/** Mandatory for deserialisation of {@link DataItem}s
	 * 
	 * @param proto protobuf representation */
	public ForecastClientRegistration(ProtoDataItem proto) {
		type = ForecastType.values()[proto.getIntValues(0)];
		this.amount = proto.getDoubleValues(0);
	}

	@Override
	protected void fillDataFields(Builder builder) {
		builder.addIntValues(type.ordinal());
		builder.addDoubleValues(amount);
	}
}
