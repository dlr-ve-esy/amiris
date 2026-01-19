// SPDX-FileCopyrightText: 2026 German Aerospace Center <amiris@dlr.de>
//
// SPDX-License-Identifier: Apache-2.0
package agents.conventionals;

import java.util.ArrayList;
import java.util.List;
import agents.conventionals.PowerPlantPrototype.PrototypeData;
import agents.plantOperator.ConventionalPlantOperator;
import de.dlr.gitlab.fame.agent.Agent;
import de.dlr.gitlab.fame.agent.input.DataProvider;
import de.dlr.gitlab.fame.agent.input.Input;
import de.dlr.gitlab.fame.agent.input.Make;
import de.dlr.gitlab.fame.agent.input.ParameterData;
import de.dlr.gitlab.fame.agent.input.ParameterData.MissingDataException;
import de.dlr.gitlab.fame.agent.input.Tree;
import de.dlr.gitlab.fame.communication.CommUtils;
import de.dlr.gitlab.fame.communication.Contract;
import de.dlr.gitlab.fame.communication.Product;
import de.dlr.gitlab.fame.communication.message.DataItem;
import de.dlr.gitlab.fame.communication.message.Message;
import de.dlr.gitlab.fame.time.TimeSpan;
import de.dlr.gitlab.fame.time.TimeStamp;

/** Installs and tears down conventional power plants
 * 
 * @author Christoph Schimeczek */
public abstract class PlantBuildingManager extends Agent {
	@Input private static final Tree parameters = Make.newTree()
			.add(Make.newLong("PortfolioBuildingOffsetInSeconds"))
			.addAs("Prototype", PowerPlantPrototype.parameters)
			.buildTree();

	/** Products of {@link PlantBuildingManager}s */
	@Product
	public static enum Products {
		/** Collection of power plants created and managed by this {@link PlantBuildingManager} */
		PowerPlantPortfolio
	}

	/** Offset between time the portfolio created and time it becomes active */
	private final TimeSpan portfolioBuildingOffset;
	/** Prototype for all power plants in the {@link Portfolio} of this {@link PlantBuildingManager} */
	protected final PrototypeData prototypeData;
	/** The set of power plants controlled by this {@link PlantBuildingManager} */
	protected final Portfolio portfolio;

	/** Creates a {@link PlantBuildingManager}
	 * 
	 * @param dataProvider contains all input data from config
	 * @throws MissingDataException thrown if requested input is not provided */
	public PlantBuildingManager(DataProvider dataProvider) throws MissingDataException {
		super(dataProvider);
		ParameterData input = parameters.join(dataProvider);

		portfolioBuildingOffset = new TimeSpan(input.getLong("PortfolioBuildingOffsetInSeconds"));
		prototypeData = new PrototypeData(input.getGroup("Prototype"));
		portfolio = new Portfolio(prototypeData.fuelType);

		call(this::updateAndSendPortfolio).on(Products.PowerPlantPortfolio);
	}

	/** Generates a new {@link Portfolio} and sends it to a connected agent
	 * 
	 * @param input not used
	 * @param contracts one contract to receiver of generated Portfolio - typically a {@link ConventionalPlantOperator} */
	public void updateAndSendPortfolio(ArrayList<Message> input, List<Contract> contracts) {
		Contract contract = CommUtils.getExactlyOneEntry(contracts);
		TimeStamp currentDeliveryTime = contract.getNextTimeOfDeliveryAfter(now());
		TimeStamp targetTime = currentDeliveryTime.laterBy(portfolioBuildingOffset);
		TimeStamp nextDeliveryTime = contract.getNextTimeOfDeliveryAfter(targetTime);
		TimeSpan deliveryInterval = new TimeSpan(nextDeliveryTime.getStep() - currentDeliveryTime.getStep());

		portfolio.tearDownPlants(targetTime.getStep());
		updatePortfolio(targetTime, deliveryInterval);
		fulfilNext(contract, portfolio, (DataItem) null);
	}

	/** Creates new plants and tears down old ones in order to match current and future plant specifications
	 * 
	 * @param time the target time when the generated portfolio shall become active
	 * @param deliveryInterval duration for which the portfolio shall be active */
	protected abstract void updatePortfolio(TimeStamp time, TimeSpan deliveryInterval);

}