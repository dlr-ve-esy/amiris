// SPDX-FileCopyrightText: 2026 German Aerospace Center <amiris@dlr.de>
//
// SPDX-License-Identifier: Apache-2.0
package communications.portable;

import agents.policy.PolicyItem;
import de.dlr.gitlab.fame.communication.transfer.ComponentCollector;
import de.dlr.gitlab.fame.communication.transfer.ComponentProvider;
import de.dlr.gitlab.fame.communication.transfer.Portable;

/** Information on the support policy applicable to the associated set type
 * 
 * @author Johannes Kochems, Christoph Schimeczek */
public final class SupportData implements Portable {
	static final String WRONG_TYPE = "SupportData item does not contain PolicyInfo of type: ";

	private String setType;
	/** setType-specific policy information */
	private PolicyItem policyItem;

	/** required for {@link Portable}s */
	public SupportData() {}

	/** Create a new {@link SupportData}
	 * 
	 * @param setType associated with a {@link PolicyItem}
	 * @param policyItem associated with a set type */
	public SupportData(String setType, PolicyItem policyItem) {
		this.setType = setType;
		this.policyItem = policyItem;
	}

	/** required for {@link Portable}s */
	@Override
	public void addComponentsTo(ComponentCollector collector) {
		collector.storeStrings(setType);
		collector.storeComponents(policyItem);
	}

	/** required for {@link Portable}s */
	@Override
	public void populate(ComponentProvider provider) {
		setType = provider.nextString();
		policyItem = provider.nextComponent(PolicyItem.class);
	}

	/** @return SetType associated with the also contained PolicyItem */
	public String getSetType() {
		return setType;
	}

	/** Returns PolicyItem of requested class type - if types do not match an Exception is thrown
	 * 
	 * @param <T> requested type of PolicyItem
	 * @param type class of requested PolicyItem
	 * @return object of requested type of PolicyItem type
	 * @throws RuntimeException if contained PolicyItem is of type other than requested type */
	@SuppressWarnings("unchecked")
	public <T> T getPolicyOfType(Class<T> type) {
		if (type.isInstance(policyItem)) {
			return (T) policyItem;
		} else {
			throw new RuntimeException(WRONG_TYPE + type);
		}
	}
}
