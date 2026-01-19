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

/** Set-specific realisation of a fixed market premium
 * 
 * @author Johannes Kochems, Christoph Schimeczek */
public class Mpfix extends PolicyItem {
	static final String ERR_NEGATIVE_PREMIUM = "Fixed market premium was negative at: ";

	static final Tree parameters = Make.newTree().optional().add(premiumParam, maxNumberOfNegativeHoursParam).buildTree();

	/** The fixed market premium in EUR/MWh */
	private TimeSeries premium;
	/** The maximum number of consecutive hours with negative prices tolerated until suspending support payment */
	private int maxNumberOfNegativeHours;

	@Override
	public void setDataFromConfig(ParameterData group) throws MissingDataException {
		premium = group.getTimeSeries("Premium");
		maxNumberOfNegativeHours = readMaxNumberOfNegativeHours(group);
	}

	/** required for {@link Portable}s */
	@Override
	public void addComponentsTo(ComponentCollector collector) {
		collector.storeTimeSeries(premium);
		collector.storeInts(maxNumberOfNegativeHours);
	}

	/** required for {@link Portable}s */
	@Override
	public void populate(ComponentProvider provider) {
		premium = provider.nextTimeSeries();
		maxNumberOfNegativeHours = provider.nextInt();
	}

	/** @param time at which to evaluate
	 * @return fixed market premium in EUR/MWh for a given time */
	public double getPremium(TimeStamp time) {
		double value = premium.getValueEarlierEqual(time);
		if (value < 0) {
			throw new RuntimeException(ERR_NEGATIVE_PREMIUM + time);
		}
		return value;
	}

	/** @return maximum number of consecutive hours with negative prices tolerated until suspending support payment */
	public int getMaxNumberOfNegativeHours() {
		return maxNumberOfNegativeHours;
	}

	@Override
	public SupportInstrument getSupportInstrument() {
		return SupportInstrument.MPFIX;
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
		return premium.getValueEarlierEqual(accountingPeriod.getStartTime());
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
