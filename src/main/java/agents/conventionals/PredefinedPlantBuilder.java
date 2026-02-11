// SPDX-FileCopyrightText: 2021-2026 German Aerospace Center <amiris@dlr.de>
//
// SPDX-License-Identifier: Apache-2.0
package agents.conventionals;

import java.util.ArrayList;
import org.apache.commons.math3.util.Precision;
import de.dlr.gitlab.fame.agent.input.DataProvider;
import de.dlr.gitlab.fame.agent.input.Input;
import de.dlr.gitlab.fame.agent.input.Make;
import de.dlr.gitlab.fame.agent.input.ParameterData;
import de.dlr.gitlab.fame.agent.input.ParameterData.MissingDataException;
import de.dlr.gitlab.fame.agent.input.Tree;
import de.dlr.gitlab.fame.data.TimeSeries;
import de.dlr.gitlab.fame.time.TimeSpan;
import de.dlr.gitlab.fame.time.TimeStamp;
import util.Util;

/** Installs and dismantles power plants according to a given predefined power TimeSeries
 *
 * @author Christoph Schimeczek */
public class PredefinedPlantBuilder extends PlantBuildingManager {
	@Input private static final Tree parameters = Make.newTree().add(
			Make.newSeries("InstalledPowerInMW"), Make.newInt("EfficiencyRoundingPrecision").optional(),
			Make.newGroup("Efficiency").add(Make.newSeries("Minimal"), Make.newSeries("Maximal")),
			Make.newDouble("BlockSizeInMW"))
			.buildTree();

	private final TimeSeries tsMinimumEfficiency;
	private final TimeSeries tsMaximumEfficiency;
	private final double blockSizeInMW;
	private final TimeSeries tsInstalledCapacityInMW;
	private final int roundingPrecision;

	/** Creates a {@link PredefinedPlantBuilder}
	 * 
	 * @param dataProvider provides input from config file
	 * @throws MissingDataException if any required data is not provided */
	public PredefinedPlantBuilder(DataProvider dataProvider) throws MissingDataException {
		super(dataProvider);
		ParameterData input = parameters.join(dataProvider);
		tsInstalledCapacityInMW = input.getTimeSeries("InstalledPowerInMW");
		roundingPrecision = input.getIntegerOrDefault("EfficiencyRoundingPrecision", 20);
		blockSizeInMW = input.getDouble("BlockSizeInMW");
		tsMinimumEfficiency = input.getTimeSeries("Efficiency.Minimal");
		tsMaximumEfficiency = input.getTimeSeries("Efficiency.Maximal");
	}

	@Override
	protected void updatePortfolio(TimeStamp targetTime, TimeSpan deliveryInterval) {
		boolean isFirstBuild = portfolio.getPowerPlantList().isEmpty();
		if (isFirstBuild) {
			addPlantsAt(targetTime, deliveryInterval);
		}
		addPlantsAt(targetTime.laterBy(deliveryInterval), deliveryInterval);
	}

	private void addPlantsAt(TimeStamp targetTime, TimeSpan deliveryInterval) {
		TimeStamp tearDownTime = targetTime.laterBy(deliveryInterval);
		setupPlants(blockSizeInMW, getPlannedPowerAt(targetTime), getMinEfficiencyAt(targetTime),
				getMaxEfficiencyAt(targetTime), targetTime.getStep(), tearDownTime.getStep(), roundingPrecision);
	}

	/** Creates new {@link PowerPlant}s which are added to the portfolio, sorted from lowest to highest efficiency
	 * 
	 * @param blockSizeInMW nominal capacity of each (but the final) created power plant block
	 * @param installedCapacityInMW total nominal capacity of all power plants to be generated
	 * @param minEfficiency the lowest efficiency in the power plant list
	 * @param maxEfficiency the highest efficiency in the power plant list
	 * @param constructionTimeStep the time at which all power plants become active
	 * @param tearDownTimeStep time step at which all power plant are deactivated
	 * @param roundingPrecision number of decimal places to round interpolated precision to
	 * @throws MissingDataException */
	void setupPlants(double blockSizeInMW, double installedCapacityInMW, double minEfficiency,
			double maxEfficiency, long constructionTimeStep, long tearDownTimeStep, int roundingPrecision) {
		int numberOfBlocks = calcBlocks(installedCapacityInMW, blockSizeInMW);
		ArrayList<Double> efficiencySet = Util.linearInterpolation(minEfficiency, maxEfficiency, numberOfBlocks);
		efficiencySet = roundEfficiencySet(efficiencySet, roundingPrecision);
		double remainingPowerInMW = installedCapacityInMW;
		for (int plantIndex = 0; plantIndex < efficiencySet.size(); plantIndex++) {
			double powerOfPlant = Math.min(remainingPowerInMW, blockSizeInMW);
			PowerPlant powerPlant = new PowerPlant(prototypeData, efficiencySet.get(plantIndex), powerOfPlant,
					"Auto_" + plantIndex);
			powerPlant.setConstructionTimeStep(constructionTimeStep);
			powerPlant.setTearDownTimeStep(tearDownTimeStep);
			portfolio.addPlant(powerPlant);
			remainingPowerInMW -= powerOfPlant;
		}
	}

	/** Calculates the number of blocks required to match the given total capacity
	 * 
	 * @param totalCapacityInMW the total nominal capacity to be installed in MW
	 * @param blockSizeInMW the nominal block size of the power plants to generate in MW
	 * @return the number of power plant blocks; the last block may not have full power */
	private int calcBlocks(double totalCapacityInMW, double blockSizeInMW) {
		return (int) Math.ceil(totalCapacityInMW / blockSizeInMW);
	}

	/** Applies rounding to given efficiencies by given precision, if appropriate
	 * 
	 * @param efficiencies the list of efficiencies to be rounded
	 * @param roundingPrecision number of decimal places to round to [1..15] - for other values no rounding is applied
	 * @return new (or old, if not rounded) list of efficiencies */
	private ArrayList<Double> roundEfficiencySet(ArrayList<Double> efficiencies, int roundingPrecision) {
		if (roundingPrecision < 16 && roundingPrecision > 0) {
			ArrayList<Double> newValues = new ArrayList<>(efficiencies.size());
			for (double originalValue : efficiencies) {
				newValues.add(Precision.round(originalValue, roundingPrecision));
			}
			return newValues;
		} else {
			return efficiencies;
		}
	}

	/** Returns the planned power at the specified time
	 * 
	 * @param time at which to calculate the installed power
	 * @return installed capacity in MW at the given time */
	private double getPlannedPowerAt(TimeStamp time) {
		return tsInstalledCapacityInMW.getValueLinear(time);
	}

	/** Calculates the minimum efficiency of portfolio at given time
	 * 
	 * @param time for which to calculate the efficiency
	 * @return the minimum efficiency of the power plant portfolio at the given time */
	private double getMinEfficiencyAt(TimeStamp time) {
		return tsMinimumEfficiency.getValueLinear(time);
	}

	/** Calculates the maximum efficiency of portfolio at given time
	 * 
	 * @param time for which to calculate the efficiency
	 * @return the maximum efficiency of the power plant portfolio at the given time */
	private double getMaxEfficiencyAt(TimeStamp time) {
		return tsMaximumEfficiency.getValueLinear(time);
	}
}