// SPDX-FileCopyrightText: 2021-2026 German Aerospace Center <amiris@dlr.de>
//
// SPDX-License-Identifier: Apache-2.0
package agents.policy.hydrogen;

import de.dlr.gitlab.fame.agent.input.Make;
import de.dlr.gitlab.fame.agent.input.ParameterData;
import de.dlr.gitlab.fame.agent.input.Tree;
import de.dlr.gitlab.fame.agent.input.ParameterData.MissingDataException;
import de.dlr.gitlab.fame.communication.transfer.ComponentCollector;
import de.dlr.gitlab.fame.communication.transfer.ComponentProvider;
import de.dlr.gitlab.fame.data.TimeSeries;
import de.dlr.gitlab.fame.time.TimeStamp;

/** A fixed market premium for hydrogen
 * 
 * @author Johannes Kochems, Christoph Schimeczek */
public class Mpfix extends PolicyItem {
	static final Tree parameters = Make.newTree().optional().add(premiumParam).buildTree();

	/** The fixed market premium in EUR/MWh */
	private TimeSeries premium;

	@Override
	public void setDataFromConfig(ParameterData group) throws MissingDataException {
		premium = group.getTimeSeries("Premium");
	}

	@Override
	public void addComponentsTo(ComponentCollector collector) {
		collector.storeTimeSeries(premium);
	}

	@Override
	public void populate(ComponentProvider provider) {
		premium = provider.nextTimeSeries();
	}

	@Override
	public boolean isTypeOfMarketPremium() {
		return true;
	}

	@Override
	public SupportInstrument getSupportInstrument() {
		return SupportInstrument.MPFIX;
	}

	@Override
	public double calcInfeedSupportRate(TimeStamp validAt) {
		return premium.getValueEarlierEqual(validAt);
	}
}
