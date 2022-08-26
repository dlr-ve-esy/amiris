// SPDX-FileCopyrightText: 2022 German Aerospace Center <amiris@dlr.de>
//
// SPDX-License-Identifier: Apache-2.0
package agents.conventionals;

import java.util.Collections;
import java.util.List;
import java.util.ListIterator;
import agents.markets.FuelsMarket.FuelType;
import de.dlr.gitlab.fame.communication.transfer.ComponentCollector;
import de.dlr.gitlab.fame.communication.transfer.ComponentProvider;
import de.dlr.gitlab.fame.communication.transfer.Portable;
import de.dlr.gitlab.fame.time.TimeStamp;
import util.SortedLinkedList;

/** Summarises a set of conventional power plants
 * 
 * @author Christoph Schimeczek */
public class Portfolio implements Portable {
	private final SortedLinkedList<PowerPlant> powerPlants = new SortedLinkedList<>();
	private FuelType fuelType;

	/** required for {@link Portable}s */
	public Portfolio() {}

	/** Creates a new Portfolio for plants with given fuelType
	 * 
	 * @param fuelType type of fuel used in this portfolio */
	public Portfolio(FuelType fuelType) {
		this.fuelType = fuelType;
	}

	/** required for {@link Portable}s */
	@Override
	public void addComponentsTo(ComponentCollector collector) {
		collector.storeInts(fuelType.ordinal());
		for (PowerPlant powerPlant : powerPlants) {
			collector.storeComponents(powerPlant);
		}
	}

	/** required for {@link Portable}s */
	@Override
	public void populate(ComponentProvider provider) {
		fuelType = FuelType.values()[provider.nextInt()];
		powerPlants.addAll(provider.nextComponentList(PowerPlant.class));
	}

	/** adds given power plant to the this portfolio
	 * 
	 * @param powerPlant to add to the portfolio */
	void addPlant(PowerPlant powerPlant) {
		powerPlants.add(powerPlant);
	}

	/** @return Type of Fuel that all contained {@link PowerPlant}s have in common */
	public FuelType getFuelType() {
		return fuelType;
	}

	/** @return {@link Collections#unmodifiableList(List) UnmodifiableList} of {@link PowerPlant}s, ordered from lowest to highest
	 *         efficiency */
	public List<PowerPlant> getPowerPlantList() {
		return Collections.unmodifiableList(powerPlants);
	}

	/** Removes any plants from the portfolio that have a tear-down time before the specified time step
	 * 
	 * @param currentTimeStep specified TimeStep */
	public void tearDownPlants(long currentTimeStep) {
		ListIterator<PowerPlant> iterator = powerPlants.listIterator();
		while (iterator.hasNext()) {
			PowerPlant powerPlant = iterator.next();
			if (powerPlant.readyToTearDownIn(currentTimeStep)) {
				iterator.remove();
			}
		}
	}

	@Override
	public String toString() {
		StringBuilder stringBuilder = new StringBuilder();
		for (PowerPlant plant : powerPlants) {
			stringBuilder.append(plant.toString());
		}
		return stringBuilder.toString();
	}

	/** Returns installed capacity at given time
	 * 
	 * @param time to evaluate capacity for
	 * @return installed capacity at given time in MW */
	public double getInstalledCapacityInMW(TimeStamp time) {
		double totalInstalledPower = 0;
		for (PowerPlant powerPlant : powerPlants) {
			totalInstalledPower += powerPlant.getInstalledPowerInMW(time);
		}
		return totalInstalledPower;
	}
}