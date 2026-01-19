// SPDX-FileCopyrightText: 2026 German Aerospace Center <amiris@dlr.de>
//
// SPDX-License-Identifier: Apache-2.0
package agents.conventionals;

import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import de.dlr.gitlab.fame.agent.input.DataProvider;
import de.dlr.gitlab.fame.agent.input.Input;
import de.dlr.gitlab.fame.agent.input.Make;
import de.dlr.gitlab.fame.agent.input.ParameterData;
import de.dlr.gitlab.fame.agent.input.ParameterData.MissingDataException;
import de.dlr.gitlab.fame.agent.input.Tree;
import de.dlr.gitlab.fame.data.TimeSeries;
import de.dlr.gitlab.fame.time.TimeSpan;
import de.dlr.gitlab.fame.time.TimeStamp;

/** Installs and dismantles power plants according to a list of individual plants
 * 
 * @author Christoph Schimeczek */
public class IndividualPlantBuilder extends PlantBuildingManager {
	static final String GROUP_PLANTS = "Plants";
	static final String GROUP_OVERRIDE = "Override";
	static final String PARAM_EFFICIENCY = "Efficiency";
	static final String PARAM_CAPACITY = "NetCapacityInMW";
	static final String PARAM_ACTIVATION = "ActivationTime";
	static final String PARAM_DEACTIVATION = "DeactivationTime";
	static final String PARAM_ID = "Id";
	static final String AUTO_NAME = "Auto_";

	@Input private static final Tree parameters = Make.newTree().add(
			Make.newGroup(GROUP_PLANTS).list().add(
					Make.newDouble(PARAM_EFFICIENCY),
					Make.newDouble(PARAM_CAPACITY),
					Make.newTimeStamp(PARAM_ACTIVATION).optional(),
					Make.newTimeStamp(PARAM_DEACTIVATION).optional(),
					Make.newString(PARAM_ID).optional(),
					Make.newGroup(GROUP_OVERRIDE).optional().add(
							Make.newSeries(PowerPlantPrototype.PARAM_OUTAGE).optional(),
							Make.newSeries(PowerPlantPrototype.PARAM_OPEX).optional(),
							Make.newDouble(PowerPlantPrototype.PARAM_CYCLING_COST).optional(),
							Make.newSeries(PowerPlantPrototype.PARAM_MUST_RUN).optional())))
			.buildTree();

	private final List<PowerPlant> powerPlants;

	/** Creates an {@link IndividualPlantBuilder}
	 * 
	 * @param dataProvider provides input from configuration file
	 * @throws MissingDataException if any required data is not provided */
	public IndividualPlantBuilder(DataProvider dataProvider) throws MissingDataException {
		super(dataProvider);
		ParameterData input = parameters.join(dataProvider);
		powerPlants = readPowerPlants(input.getGroupList(GROUP_PLANTS));
	}

	/** @return list of all power plants created from the corresponding input group list */
	private List<PowerPlant> readPowerPlants(List<ParameterData> plantsData) throws MissingDataException {
		LinkedList<PowerPlant> plants = new LinkedList<>();
		int plantCount = 0;
		for (ParameterData data : plantsData) {
			plantCount++;
			PowerPlant plant = new PowerPlant(prototypeData, data.getDouble(PARAM_EFFICIENCY),
					data.getDouble(PARAM_CAPACITY), data.getStringOrDefault(PARAM_ID, AUTO_NAME + plantCount));
			TimeStamp activationTime = data.getTimeStampOrDefault(PARAM_ACTIVATION, null);
			TimeStamp deactivationTime = data.getTimeStampOrDefault(PARAM_DEACTIVATION, null);
			if (activationTime != null) {
				plant.setConstructionTimeStep(activationTime.getStep());
			}
			if (deactivationTime != null) {
				plant.setTearDownTimeStep(deactivationTime.getStep());
			}
			try {
				ParameterData override = data.getGroup(GROUP_OVERRIDE);
				TimeSeries outageFactor = override.getTimeSeriesOrDefault(PowerPlantPrototype.PARAM_OUTAGE, null);
				TimeSeries opexVarInEURperMWH = override.getTimeSeriesOrDefault(PowerPlantPrototype.PARAM_OPEX, null);
				Double cyclingCostInEURperMW = override.getDoubleOrDefault(PowerPlantPrototype.PARAM_CYCLING_COST, null);
				TimeSeries mustRunFactor = override.getTimeSeriesOrDefault(PowerPlantPrototype.PARAM_MUST_RUN, null);
				if (outageFactor != null) {
					plant.setOutageFactor(outageFactor);
				}
				if (opexVarInEURperMWH != null) {
					plant.setTsVariableCosts(opexVarInEURperMWH);
				}
				if (cyclingCostInEURperMW != null) {
					plant.setCyclingCostInEURperMW(cyclingCostInEURperMW);
				}
				if (mustRunFactor != null) {
					plant.setMustRunFactor(mustRunFactor);
				}
			} catch (MissingDataException e) {}
			plants.add(plant);
		}
		return plants;
	}

	@Override
	protected void updatePortfolio(TimeStamp targetTime, TimeSpan deliveryInterval) {
		TimeStamp followUpTime = targetTime.laterBy(deliveryInterval);
		TimeStamp followFollowUpTime = followUpTime.laterBy(deliveryInterval);

		ListIterator<PowerPlant> iterator = powerPlants.listIterator();
		while (iterator.hasNext()) {
			PowerPlant plant = iterator.next();
			if (plant.isActiveAnyTimeBetween(targetTime.getStep(), followFollowUpTime.getStep())) {
				iterator.remove();
				portfolio.addPlant(plant);
			}
		}
	}
}
