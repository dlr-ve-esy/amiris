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

/** Set-specific realisation of a two-sided contract for difference
 * 
 * @author Johannes Kochems, Christoph Schimeczek */
public class Cfd extends PolicyItem {
	static final Tree parameters = Make.newTree().optional().add(lcoeParam, maxNumberOfNegativeHoursParam).buildTree();

	/** The levelised cost of electricity (value applied) */
	private TimeSeries lcoe;
	/** The maximum number of consecutive hours with negative prices tolerated until suspending support payment */
	private int maxNumberOfNegativeHours;

	@Override
	public void setDataFromConfig(ParameterData group) throws MissingDataException {
		lcoe = group.getTimeSeries("Lcoe");
		maxNumberOfNegativeHours = readMaxNumberOfNegativeHours(group);
	}

	/** required for {@link Portable}s */
	@Override
	public void addComponentsTo(ComponentCollector collector) {
		collector.storeTimeSeries(lcoe);
		collector.storeInts(maxNumberOfNegativeHours);
	}

	/** required for {@link Portable}s */
	@Override
	public void populate(ComponentProvider provider) {
		lcoe = provider.nextTimeSeries();
		maxNumberOfNegativeHours = provider.nextInt();
	}

	@Override
	public SupportInstrument getSupportInstrument() {
		return SupportInstrument.CFD;
	}

	/** @return maximum number of consecutive hours with negative prices tolerated until suspending support payment */
	public int getMaxNumberOfNegativeHours() {
		return maxNumberOfNegativeHours;
	}

	@Override
	public double calcEligibleInfeed(TreeMap<TimeStamp, Double> powerPrices, SupportRequestData request) {
		TreeMap<TimeStamp, Boolean> eligibleHours = calcEligibleHours(maxNumberOfNegativeHours, request.infeed.keySet(),
				powerPrices);
		return request.infeed.entrySet().stream()
				.mapToDouble(entry -> eligibleHours.get(entry.getKey()) ? entry.getValue() : 0)
				.sum();
	}

	@Override
	public double calcInfeedSupportRate(TimePeriod accountingPeriod, double marketValue) {
		return calcCfD(marketValue, accountingPeriod.getStartTime());
	}

	/** Calculate specific contracts for differences premium in EUR/MWh at given time using LCOE and market value (or market value
	 * estimate)
	 * 
	 * @param marketValue the market value or a market value estimate
	 * @param time at which to evaluate
	 * @return specific contracts for differences premium in â‚¬/MWh */
	public double calcCfD(double marketValue, TimeStamp time) {
		double valueApplied = lcoe.getValueEarlierEqual(time);
		return valueApplied - marketValue;
	}

	/** Returns true if given number of hours with negative prices is below or equal their maximum allowed value
	 * 
	 * @param actualNegativeHours actual number of hours with negative prices in a row
	 * @return true if given actual negative hour count is smaller or equal than maximum number of negative hours (if defined) */
	public boolean isEligible(int actualNegativeHours) {
		return isEligible(maxNumberOfNegativeHours, actualNegativeHours);
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
		return true;
	}
}
