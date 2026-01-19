// SPDX-FileCopyrightText: 2026 German Aerospace Center <amiris@dlr.de>
//
// SPDX-License-Identifier: Apache-2.0
package agents.policy;

import java.util.EnumMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;
import agents.policy.SupportPolicy.EnergyCarrier;
import communications.message.YieldPotential;
import de.dlr.gitlab.fame.time.TimePeriod;
import de.dlr.gitlab.fame.time.TimeStamp;

/** Stores and evaluates market data
 * 
 * @author Christoph Schimeczek */
public class MarketData {
	static final String ERR_MISSING_CARRIER = "Energy carrier is not implemented: ";

	/** Stores feed-in potentials per energy carrier over time */
	private EnumMap<EnergyCarrier, TreeMap<TimeStamp, Double>> energyCarrierInfeeds = new EnumMap<>(EnergyCarrier.class);
	/** Stores electricity market prices over time */
	private TreeMap<TimeStamp, Double> powerPrices = new TreeMap<>();

	/** Saves given {@link YieldPotential} for a specific {@link EnergyCarrier}
	 * 
	 * @param potential to store */
	public void addYieldValue(YieldPotential potential) {
		EnergyCarrier carrier = potential.energyCarrier;
		TreeMap<TimeStamp, Double> yieldMap = energyCarrierInfeeds.computeIfAbsent(carrier, __ -> new TreeMap<>());
		yieldMap.compute(potential.validAt, (__, total) -> (total != null ? total : 0) + potential.amount);
	}

	/** Saves given electricity price at given time
	 * 
	 * @param time at which electricity price is valid
	 * @param price realised at electricity market */
	public void addElectricityPrice(TimeStamp time, Double price) {
		powerPrices.put(time, price);
	}

	/** Calculate market value based on energy carrier-specific RES infeed (wind and PV) or base price
	 * 
	 * @param energyCarrier to calculate for
	 * @param interval to assess
	 * @return the market value of the given energy carrier in the given time period */
	public double calcMarketValue(EnergyCarrier energyCarrier, TimePeriod interval) {
		double numerator = 0;
		double denominator = 0;
		Iterator<Entry<TimeStamp, Double>> iterator = powerPrices.entrySet().iterator();
		while (iterator.hasNext()) {
			Entry<TimeStamp, Double> entry = iterator.next();
			TimeStamp powerPriceTime = entry.getKey();
			if (powerPriceTime.isGreaterEqualTo(interval.getStartTime())
					&& powerPriceTime.isLessEqualTo(interval.getLastTime())) {
				double[] marketValQuotient = calcEnergyCarrierSpecificValue(energyCarrier, powerPriceTime, denominator,
						numerator);
				denominator = marketValQuotient[0];
				numerator = marketValQuotient[1];
			}
		}
		return numerator / denominator;
	}

	/** Calculate the market value elements (denominator and numerator) dependent on RES type:
	 * <ul>
	 * <li>For wind and PV, an average price weighted by the energy carrier feed-in potential (before curtailment) is used.</li>
	 * <li>For all other RES sources, the unweighed average price (base price) is used.</li>
	 * </ul>
	 * 
	 * @param carrier to calculate market value for
	 * @param time at which to calculate the market value element
	 * @param denominator running total of the energyCarrier-specific market value calculation
	 * @param numerator running total of the energyCarrier-specific market value calculation
	 * @return double array with the first entry being the updated denominator and the second the updated numerator */
	private double[] calcEnergyCarrierSpecificValue(EnergyCarrier carrier, TimeStamp time, double denominator,
			double numerator) {
		switch (carrier) {
			case PV:
			case WindOn:
			case WindOff:
				denominator += energyCarrierInfeeds.get(carrier).get(time);
				numerator += energyCarrierInfeeds.get(carrier).get(time) * powerPrices.get(time);
				return new double[] {denominator, numerator};
			case RunOfRiver:
			case Biogas:
			case Other:
				denominator += 1;
				numerator += powerPrices.get(time);
				return new double[] {denominator, numerator};
			default:
				throw new RuntimeException(ERR_MISSING_CARRIER + carrier);
		}
	}

	/** @return previously logged market prices */
	public TreeMap<TimeStamp, Double> getEnergyPrices() {
		return powerPrices;
	}

	/** Removes any data referring to times before the specified time
	 * 
	 * @param time before which all recorded data is to be cleared */
	public void clearBefore(TimeStamp time) {
		powerPrices.headMap(time).clear();
		for (TreeMap<TimeStamp, Double> infeed : energyCarrierInfeeds.values()) {
			infeed.headMap(time).clear();
		}
	}

	/** @return all {@link EnergyCarrier}s that had some infeed yet in this simulation */
	public Set<EnergyCarrier> getAllEnergyCarriers() {
		return new HashSet<EnergyCarrier>(energyCarrierInfeeds.keySet());
	}
}
