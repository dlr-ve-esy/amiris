// SPDX-FileCopyrightText: 2023-2026 German Aerospace Center <amiris@dlr.de>
//
// SPDX-License-Identifier: Apache-2.0
package agents.policy;

import java.lang.reflect.InvocationTargetException;
import java.util.EnumMap;
import java.util.Set;
import java.util.TreeMap;
import communications.message.SupportRequestData;
import de.dlr.gitlab.fame.agent.input.Make;
import de.dlr.gitlab.fame.agent.input.ParameterBuilder;
import de.dlr.gitlab.fame.agent.input.ParameterData;
import de.dlr.gitlab.fame.agent.input.ParameterData.MissingDataException;
import de.dlr.gitlab.fame.communication.transfer.Portable;
import de.dlr.gitlab.fame.time.TimePeriod;
import de.dlr.gitlab.fame.time.TimeStamp;

/** Abstract base class for set-specific support policy parametrisations
 * 
 * @author Johannes Kochems, Christoph Schimeczek */
public abstract class PolicyItem implements Portable {
	static final String ERR_CONSTRUCTOR = "Ensure classes has accessible default constructor: ";
	static final String ERR_CONFIG = "Config data for Policy incomplete: ";
	static final String ERR_INVALID_NEGATIVE = "Negative values are invalid for 'MaxNumberOfNegativeHours' - leave out if not desired! Specified value: ";

	static final ParameterBuilder lcoeParam = Make.newSeries("Lcoe");
	static final ParameterBuilder premiumParam = Make.newSeries("Premium");
	static final ParameterBuilder maxNumberOfNegativeHoursParam = Make.newInt("MaxNumberOfNegativeHours").optional();
	private static final int INFINITE_NEGATIVE_HOURS = -1;

	/** Available support instruments */
	public enum SupportInstrument {
		/** A feed-in-tariff (FIT) */
		FIT,
		/** A fixed market premium (MPFIX) */
		MPFIX,
		/** A variable market premium (MPVAR); as in the German Renewable Energies Act */
		MPVAR,
		/** A contracts for differences (CFD) scheme, building on MPVAR */
		CFD,
		/** A capacity premium (CP) scheme */
		CP,
		/** A financial contract for differences (FINANCIAL_CFD) scheme */
		FINANCIAL_CFD
	}

	private static final EnumMap<SupportInstrument, Class<? extends PolicyItem>> policyClasses = new EnumMap<>(
			SupportInstrument.class);
	static {
		policyClasses.put(SupportInstrument.FIT, Fit.class);
		policyClasses.put(SupportInstrument.MPFIX, Mpfix.class);
		policyClasses.put(SupportInstrument.MPVAR, Mpvar.class);
		policyClasses.put(SupportInstrument.CFD, Cfd.class);
		policyClasses.put(SupportInstrument.CP, Cp.class);
		policyClasses.put(SupportInstrument.FINANCIAL_CFD, FinancialCfd.class);
	}

	/** Instantiates a {@link PolicyItem} from given configuration - returns null if matching configuration is not present
	 * 
	 * @param instrument type of support instrument to read from given data group
	 * @param group data from config; group should contain requested subGroup - if not: null is returned
	 * @return {@link PolicyItem} initialised with matching config data; null if group contains no parameters for requested subGroup
	 * @throws RuntimeException if config data for given type is incomplete or if default constructor is missing / inaccessible */
	public static PolicyItem buildPolicy(SupportInstrument instrument, ParameterData group) {
		PolicyItem item = null;
		ParameterData subGroup = group.getOptionalGroup(instrument.name());
		if (subGroup != null) {
			item = instantiatePolicyItem(getClass(instrument));
			initialisePolicyItem(item, subGroup);
		}
		return item;
	}

	/** Return the Subtype of {@link PolicyItem} implementing the given instrument
	 * 
	 * @param instrument to get the implementing class for
	 * @return {@link PolicyItem} implementing the given {@link SupportInstrument} */
	public static Class<? extends PolicyItem> getClass(SupportInstrument instrument) {
		return policyClasses.get(instrument);
	}

	/** @return newly instantiated {@link PolicyItem} based on required class */
	private static <T extends PolicyItem> T instantiatePolicyItem(Class<T> itemClass) {
		try {
			return itemClass.getDeclaredConstructor().newInstance();
		} catch (InstantiationException | IllegalAccessException | IllegalArgumentException | InvocationTargetException
				| NoSuchMethodException | SecurityException e) {
			throw new RuntimeException(PolicyItem.ERR_CONSTRUCTOR + itemClass);
		}
	}

	private static void initialisePolicyItem(PolicyItem policy, ParameterData parameters) {
		try {
			policy.setDataFromConfig(parameters);
		} catch (MissingDataException e) {
			throw new RuntimeException(PolicyItem.ERR_CONFIG + policy.getClass().getSimpleName());
		}
	}

	/** Initialises an empty {@link PolicyItem} with its associated config data
	 * 
	 * @param group DataProvider group matching type of {@link PolicyItem} to provide input from config
	 * @throws MissingDataException if any required data is not provided */
	abstract protected void setDataFromConfig(ParameterData group) throws MissingDataException;

	/** Read data for maximum number of negative hours from given input
	 * 
	 * @param group input group containing the requested paramter
	 * @return maxNumberOfNegativeHours if set or its default */
	protected int readMaxNumberOfNegativeHours(ParameterData group) {
		int maxNumberOfNegativeHours = group.getIntegerOrDefault("MaxNumberOfNegativeHours", INFINITE_NEGATIVE_HOURS);
		if (maxNumberOfNegativeHours < 0 && maxNumberOfNegativeHours != INFINITE_NEGATIVE_HOURS) {
			throw new RuntimeException(ERR_INVALID_NEGATIVE + maxNumberOfNegativeHours);
		}
		return maxNumberOfNegativeHours;
	}

	/** @return true if PolicyItem returns a type of market premium */
	public abstract boolean isTypeOfMarketPremium();

	/** @return {@link SupportInstrument} this {@link PolicyItem} is covering */
	public abstract SupportInstrument getSupportInstrument();

	/** Calculate the infeed eligible for support under the given instrument
	 * 
	 * @param powerPrices at the time the infeed occurred
	 * @param request specifying the infeed data
	 * @return amount eligible for support payment in MWH based on infeed in accounting period */
	public abstract double calcEligibleInfeed(TreeMap<TimeStamp, Double> powerPrices, SupportRequestData request);

	/** Calculate the capacity eligible for support under the given instrument
	 * 
	 * @param request specifying the total capacity
	 * @return amount eligible for support payment in MW based on available capacity in accounting period */
	public abstract double calcEligibleCapacity(SupportRequestData request);

	/** Calculate the support rate in EUR/MWh per eligible infeed
	 * 
	 * @param accountingPeriod for which to calculate the support rate
	 * @param marketValue of the respective energy carrier during the accounting period
	 * @return support rate in EUR/MWh per eligible infeed */
	public abstract double calcInfeedSupportRate(TimePeriod accountingPeriod, double marketValue);

	/** Calculate the support rate in EUR/MW per eligible installed capacity
	 * 
	 * @param accountingPeriod for which to calculate the support rate
	 * @return support rate in EUR/MW per installed capacity */
	public abstract double calcCapacitySupportRate(TimePeriod accountingPeriod);

	/** Calculates at which times infeed is eligible for support based on the maximum number of hours with negative prices and the
	 * actual power prices
	 * 
	 * @param maxNumberOfNegativeHours max number of hours with negative prices in a row that are still eligible for support
	 * @param times at which to check for eligibility
	 * @param powerPrices that occurred in the past
	 * @return mask of hours eligible for support: positive if number of negative hours is less than allowed maximum */
	protected TreeMap<TimeStamp, Boolean> calcEligibleHours(int maxNumberOfNegativeHours, Set<TimeStamp> times,
			TreeMap<TimeStamp, Double> powerPrices) {
		TreeMap<TimeStamp, Boolean> eligibleHours = new TreeMap<>();
		int previousNegativeHours = 0;
		for (TimeStamp time : times) {
			if (powerPrices.get(time) < 0) {
				previousNegativeHours++;
				eligibleHours.put(time, null);
			} else {
				boolean blockEligible = isEligible(maxNumberOfNegativeHours, previousNegativeHours);
				updateEndingNullsWith(blockEligible, eligibleHours);
				eligibleHours.put(time, true);
				previousNegativeHours = 0;
			}
		}
		boolean blockEligible = isEligible(maxNumberOfNegativeHours, previousNegativeHours);
		updateEndingNullsWith(blockEligible, eligibleHours);
		return eligibleHours;
	}

	/** Returns <code>true</code> if given actual number of hours with negative prices is below their maximum allowed number,
	 * otherwise returns <code>false</code>.
	 * 
	 * @param maxNumberOfNegativeHours max number of hours with negative prices in a row that are still eligible for support
	 * @param actualNegativeHours actual number of hours with negative prices in a row
	 * @return true if given actual negative hour count is smaller or equal than maximum number of negative hours (if defined) */
	protected boolean isEligible(int maxNumberOfNegativeHours, int actualNegativeHours) {
		boolean noCheckRequired = maxNumberOfNegativeHours == INFINITE_NEGATIVE_HOURS;
		return noCheckRequired || actualNegativeHours <= maxNumberOfNegativeHours;
	}

	/** Iterates through given map backwards and updates all values in given map currently bound to null until any other value is
	 * found */
	private void updateEndingNullsWith(boolean target, TreeMap<TimeStamp, Boolean> map) {
		for (TimeStamp time : map.descendingKeySet()) {
			if (map.get(time) != null) {
				return;
			}
			map.put(time, target);
		}
	}
}
