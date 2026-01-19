// SPDX-FileCopyrightText: 2026 German Aerospace Center <amiris@dlr.de>
//
// SPDX-License-Identifier: Apache-2.0
package agents.flexibility.dynamicProgramming.bidding;

import de.dlr.gitlab.fame.agent.input.Make;
import de.dlr.gitlab.fame.agent.input.ParameterData;
import de.dlr.gitlab.fame.agent.input.ParameterData.MissingDataException;
import de.dlr.gitlab.fame.agent.input.Tree;

/** Builds {@link BidScheduler} from provided input parameters
 * 
 * @author Christoph Schimeczek, Felix Nitsch, Johannes Kochems */
public class BidSchedulerBuilder {
	public static final Tree parameters = Make.newTree()
			.add(Make.newEnum("Type", Type.class), Make.newDouble("SchedulingHorizonInHours")).buildTree();

	/** Available {@link BidScheduler}s */
	enum Type {
		/** Ensures planned dispatch is fulfilled by bidding at technical price limits */
		ENSURE_DISPATCH,
		/** Uses estimated value changes of storage content to calculate bidding price */
		STORAGE_CONTENT_VALUE
	}

	public static final String ERR_NOT_IMPLEMENTED = "Bid Scheduler is not implemented: ";

	public static BidScheduler build(ParameterData input) throws MissingDataException {
		Type type = input.getEnum("Type", Type.class);
		switch (type) {
			case ENSURE_DISPATCH:
				return new EnsureDispatch(input.getDouble("SchedulingHorizonInHours"));
			case STORAGE_CONTENT_VALUE:
				return new StorageContentValue(input.getDouble("SchedulingHorizonInHours"));
			default:
				throw new RuntimeException(ERR_NOT_IMPLEMENTED + type);
		}
	}
}
