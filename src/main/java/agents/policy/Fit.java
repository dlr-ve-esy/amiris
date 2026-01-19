// SPDX-FileCopyrightText: 2026 German Aerospace Center <amiris@dlr.de>
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

/** Set-specific realisation of a feed-in tariff
 * 
 * @author Johannes Kochems, Christoph Schimeczek */
public class Fit extends PolicyItem {
	static final Tree parameters = Make.newTree().optional().add(Make.newSeries("TsFit")).buildTree();

	/** The time series containing the FIT applicable in EUR/MWh */
	private TimeSeries tsFit;

	@Override
	public void setDataFromConfig(ParameterData group) throws MissingDataException {
		tsFit = group.getTimeSeries("TsFit");
	}

	/** required for {@link Portable}s */
	@Override
	public void addComponentsTo(ComponentCollector collector) {
		collector.storeTimeSeries(tsFit);
	}

	/** required for {@link Portable}s */
	@Override
	public void populate(ComponentProvider provider) {
		tsFit = provider.nextTimeSeries();
	}

	/** @return time series of feed-in tariff in EUR/MWh */
	public TimeSeries getTsFit() {
		return tsFit;
	}

	@Override
	public SupportInstrument getSupportInstrument() {
		return SupportInstrument.FIT;
	}

	@Override
	public double calcEligibleInfeed(TreeMap<TimeStamp, Double> powerPrice, SupportRequestData request) {
		return request.infeed.values().stream().mapToDouble(v -> v).sum();
	}

	@Override
	public double calcInfeedSupportRate(TimePeriod accountingPeriod, double marketValue) {
		return tsFit.getValueEarlierEqual(accountingPeriod.getStartTime());
	}

	@Override
	public double calcEligibleCapacity(SupportRequestData request) {
		return 0;
	}

	@Override
	public double calcCapacitySupportRate(TimePeriod accountingPeriod) {
		return 0;
	}

	@Override
	public boolean isTypeOfMarketPremium() {
		return false;
	}
}
