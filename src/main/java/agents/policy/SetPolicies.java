// SPDX-FileCopyrightText: 2023-2026 German Aerospace Center <amiris@dlr.de>
//
// SPDX-License-Identifier: Apache-2.0
package agents.policy;

import java.util.EnumMap;
import java.util.HashMap;
import agents.policy.PolicyItem.SupportInstrument;
import agents.policy.SupportPolicy.EnergyCarrier;
import communications.message.TechnologySet;
import communications.portable.SupportData;

/** Controls all set-specific policy items
 * 
 * @author Christoph Schimeczek */
public class SetPolicies {
	static final String ERR_POLICY_UNCONFIGURED = ": Policy not configured for instrument ";

	/** Holds set-specific support policies
	 * 
	 * @author Johannes Kochems, Christoph Schimeczek */
	public class SetPolicyItems {
		private final EnumMap<SupportInstrument, PolicyItem> policies = new EnumMap<>(SupportInstrument.class);

		/** Stores the given {@link PolicyItem}
		 * 
		 * @param policyItem to be stored */
		public void addPolicyItem(PolicyItem policyItem) {
			if (policyItem != null) {
				policies.put(policyItem.getSupportInstrument(), policyItem);
			}
		}

		/** Gets the {@link PolicyItem} of the given {@link SupportInstrument}
		 * 
		 * @param instrument to get the {@link PolicyItem} for
		 * @return the associated {@link PolicyItem} for the requested {@link SupportInstrument} */
		public PolicyItem getPolicyFor(SupportInstrument instrument) {
			return policies.get(instrument);
		}
	}

	/** Maps each set to its support data */
	private HashMap<String, SetPolicyItems> policyItemsPerSet = new HashMap<>();
	/** Maps each set to its energy carrier */
	private HashMap<String, EnergyCarrier> energyCarrierPerSet = new HashMap<>();

	/** Associates the given {@link PolicyItem} with the specified set
	 * 
	 * @param set to be associated with the given policy
	 * @param policyItem to be associated with the given set */
	public void addSetPolicyItem(String set, PolicyItem policyItem) {
		SetPolicyItems supportData = policyItemsPerSet.computeIfAbsent(set, __ -> new SetPolicyItems());
		supportData.addPolicyItem(policyItem);
	}

	/** Registers the given {@link TechnologySet} for later evaluation
	 * 
	 * @param technologySet to be registered
	 * @throws RuntimeException if the set of the given {@link TechnologySet} has no associated {@link PolicyItem} */
	public void register(TechnologySet technologySet) {
		energyCarrierPerSet.put(technologySet.setType, technologySet.energyCarrier);
		if (getPolicyItem(technologySet.setType, technologySet.supportInstrument) == null) {
			throw new RuntimeException(technologySet.setType + ERR_POLICY_UNCONFIGURED + technologySet.supportInstrument);
		}
	}

	/** Returns the {@link EnergyCarrier} associated with a given set
	 * 
	 * @param set to find the {@link EnergyCarrier} for
	 * @return the associated {@link EnergyCarrier} */
	public EnergyCarrier getEnergyCarrier(String set) {
		return energyCarrierPerSet.get(set);
	}

	/** Create a link between the given {@link TechnologySet}'s set and its associated {@link PolicyItem}
	 * 
	 * @param technologySet to link with its associated {@link PolicyItem}
	 * @return new {@link SupportData} */
	public SupportData getSupportData(TechnologySet technologySet) {
		String set = technologySet.setType;
		return new SupportData(set, getPolicyItem(set, technologySet.supportInstrument));
	}

	/** Fetches {@link PolicyItem} for given set and {@link SupportInstrument}
	 * 
	 * @param set associated with the returned {@link PolicyItem}
	 * @param instrument used by the given set and associated with the returned {@link PolicyItem}
	 * @return PolicyItem for given set and support instrument */
	public PolicyItem getPolicyItem(String set, SupportInstrument instrument) {
		return policyItemsPerSet.get(set).getPolicyFor(instrument);
	}
}
