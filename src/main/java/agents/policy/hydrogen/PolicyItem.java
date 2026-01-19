// SPDX-FileCopyrightText: 2026 German Aerospace Center <amiris@dlr.de>
//
// SPDX-License-Identifier: Apache-2.0
package agents.policy.hydrogen;

import java.lang.reflect.InvocationTargetException;
import java.util.EnumMap;
import de.dlr.gitlab.fame.agent.input.Make;
import de.dlr.gitlab.fame.agent.input.ParameterBuilder;
import de.dlr.gitlab.fame.agent.input.ParameterData;
import de.dlr.gitlab.fame.agent.input.ParameterData.MissingDataException;
import de.dlr.gitlab.fame.communication.transfer.Portable;
import de.dlr.gitlab.fame.time.TimeStamp;

/** An abstract representation of hydrogen-related support policies
 * 
 * @author Johannes Kochems, Christoph Schimeczek */
public abstract class PolicyItem implements Portable {
	static final String ERR_CONSTRUCTOR = "Ensure classes has accessible default constructor: ";
	static final String ERR_CONFIG = "Config data for Policy incomplete: ";

	static final ParameterBuilder premiumParam = Make.newSeries("Premium");

	/** Available support instruments */
	public enum SupportInstrument {
		/** A fixed market premium (MPFIX) */
		MPFIX
	}

	/** Map of support instrument names to their corresponding class */
	private static final EnumMap<SupportInstrument, Class<? extends PolicyItem>> policyClasses = new EnumMap<>(
			SupportInstrument.class);
	static {
		policyClasses.put(SupportInstrument.MPFIX, Mpfix.class);
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

	/** Assigns given parameters to a given policy */
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

	/** @return true if PolicyItem returns a type of market premium */
	public abstract boolean isTypeOfMarketPremium();

	/** @return {@link SupportInstrument} this {@link PolicyItem} is covering */
	public abstract SupportInstrument getSupportInstrument();

	/** Returns support rate in EUR per thermal MWh hydrogen sold
	 * 
	 * @param validAt timestamp at which the hydrogen infeed is reported
	 * @return infeed support rate at a given time, or Zero if no infeed support is provided */
	public abstract double calcInfeedSupportRate(TimeStamp validAt);
}