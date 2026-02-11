// SPDX-FileCopyrightText: 2021-2026 German Aerospace Center <amiris@dlr.de>
//
// SPDX-License-Identifier: Apache-2.0
package agents.policy;

import java.util.TreeMap;
import communications.message.SupportRequestData;
import de.dlr.gitlab.fame.agent.input.Make;
import de.dlr.gitlab.fame.agent.input.ParameterData;
import de.dlr.gitlab.fame.agent.input.ParameterData.MissingDataException;
import de.dlr.gitlab.fame.agent.input.Tree;
import de.dlr.gitlab.fame.communication.transfer.ComponentCollector;
import de.dlr.gitlab.fame.communication.transfer.ComponentProvider;
import de.dlr.gitlab.fame.communication.transfer.Portable;
import de.dlr.gitlab.fame.data.TimeSeries;
import de.dlr.gitlab.fame.time.TimePeriod;
import de.dlr.gitlab.fame.time.TimeStamp;

/** Set-specific realisation of a capacity premium
 * 
 * @author Johannes Kochems, Christoph Schimeczek */
public class Cp extends PolicyItem {
	static final Tree parameters = Make.newTree().optional().add(premiumParam).buildTree();

	/** The capacity premium in EUR/MW installed */
	private TimeSeries premium;

	@Override
	public void setDataFromConfig(ParameterData group) throws MissingDataException {
		premium = group.getTimeSeries("Premium");
	}

	/** required for {@link Portable}s */
	@Override
	public void addComponentsTo(ComponentCollector collector) {
		collector.storeTimeSeries(premium);
	}

	/** required for {@link Portable}s */
	@Override
	public void populate(ComponentProvider provider) {
		premium = provider.nextTimeSeries();
	}

	/** @return time series capacity premium in EUR/MW */
	public TimeSeries getPremium() {
		return premium;
	}

	@Override
	public SupportInstrument getSupportInstrument() {
		return SupportInstrument.CP;
	}

	@Override
	public double calcEligibleInfeed(TreeMap<TimeStamp, Double> powerPrices, SupportRequestData request) {
		return 0;
	}

	@Override
	public double calcInfeedSupportRate(TimePeriod accountingPeriod, double marketValue) {
		return 0;
	}

	@Override
	public double calcEligibleCapacity(SupportRequestData request) {
		return request.installedCapacityInMW;
	}

	@Override
	public double calcCapacitySupportRate(TimePeriod accountingPeriod) {
		return premium.getValueEarlierEqual(accountingPeriod.getStartTime());
	}

	@Override
	public boolean isTypeOfMarketPremium() {
		return false;
	}
}
