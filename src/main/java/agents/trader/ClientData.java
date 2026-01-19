// SPDX-FileCopyrightText: 2026 German Aerospace Center <amiris@dlr.de>
//
// SPDX-License-Identifier: Apache-2.0
package agents.trader;

import java.util.Iterator;
import java.util.Map.Entry;
import java.util.TreeMap;
import agents.plantOperator.RenewablePlantOperator;
import communications.message.TechnologySet;
import communications.portable.SupportData;
import de.dlr.gitlab.fame.time.TimePeriod;
import de.dlr.gitlab.fame.time.TimeStamp;

/** Holds data on the client of an AggregatorTrader (i.e. a {@link RenewablePlantOperator})
 * 
 * @author Johannes Kochems, Christoph Schimeczek */
public class ClientData {
	private TechnologySet technologySet;
	private double installedCapacityInMW;
	private final TreeMap<TimeStamp, Double> yieldPotentials = new TreeMap<>();
	private final TreeMap<TimeStamp, Double> dispatches = new TreeMap<>();
	private final TreeMap<TimeStamp, Double> marketRevenues = new TreeMap<>();
	/** Total amount of support payed in accounting interval in € */
	private final TreeMap<TimePeriod, Double> supportRevenueInEUR = new TreeMap<>();
	/** The market premium for the accounting interval in €/MWh */
	private final TreeMap<TimePeriod, Double> marketPremiaInEURperMWH = new TreeMap<>();
	private SupportData supportData;

	/** Create a client data object and initialise it with the given technology set
	 * 
	 * @param technologySet to be assigned
	 * @param installedCapacityInMW of the associated client */
	public ClientData(TechnologySet technologySet, double installedCapacityInMW) {
		this.technologySet = technologySet;
		this.installedCapacityInMW = installedCapacityInMW;
	}

	/** Removes any internal data with TimeStamp before given TimeStamp; other arrays are cleared as well
	 * 
	 * @param timeStamp any stored data associated with earlier times are removed */
	public void clearBefore(TimeStamp timeStamp) {
		yieldPotentials.headMap(timeStamp).clear();
		dispatches.headMap(timeStamp).clear();
		marketRevenues.headMap(timeStamp).clear();
		clearBefore(timeStamp, supportRevenueInEUR);
		clearBefore(timeStamp, marketPremiaInEURperMWH);
	}

	/** Removes any element of a TreeMap with TimeSegment key if last time before given TimeStamp */
	private void clearBefore(TimeStamp timeStamp, TreeMap<TimePeriod, Double> map) {
		Iterator<Entry<TimePeriod, Double>> iterator = map.entrySet().iterator();
		while (iterator.hasNext()) {
			Entry<TimePeriod, Double> entry = iterator.next();
			TimePeriod timeSegment = entry.getKey();
			if (timeSegment.getLastTime().isLessThan(timeStamp)) {
				iterator.remove();
			} else if (timeSegment.getStartTime().isGreaterEqualTo(timeStamp)) {
				break;
			}
		}
	}

	/** Save the given yield potential at the given time to this {@link ClientData}
	 * 
	 * @param time at which the yield potential is valid
	 * @param yieldPotentialInMW true yield potential of the client at the given time */
	public void appendYieldPotential(TimeStamp time, double yieldPotentialInMW) {
		yieldPotentials.put(time, yieldPotentialInMW);
	}

	/** Append dispatch and revenue for given time stamps to maps for tracking it
	 * 
	 * @param time at which to save dispatch and revenue
	 * @param dispatch at given time to save
	 * @param revenue at given time to save */
	public void appendStepDispatchAndRevenue(TimeStamp time, double dispatch, double revenue) {
		dispatches.put(time, dispatch);
		marketRevenues.put(time, revenue);
	}

	/** Save received support revenues to the client it is associated with
	 * 
	 * @param accountingPeriod for which the support is received
	 * @param amountPaymentInEUR of the associated client */
	public void appendSupportRevenue(TimePeriod accountingPeriod, double amountPaymentInEUR) {
		supportRevenueInEUR.put(accountingPeriod, amountPaymentInEUR);
	}

	/** Save market premium to the client it is associated with
	 * 
	 * @param accountingPeriod for which the market premium applies
	 * @param marketPremiumInEURperMWH the associated client receives for the specified accounting period */
	public void appendMarketPremium(TimePeriod accountingPeriod, double marketPremiumInEURperMWH) {
		marketPremiaInEURperMWH.put(accountingPeriod, marketPremiumInEURperMWH);
	}

	/** @return the {@link TechnologySet} of the associated client */
	public TechnologySet getTechnologySet() {
		return technologySet;
	}

	/** @return the installed capacity of the associated client in MW */
	public double getInstalledCapacity() {
		return installedCapacityInMW;
	}

	/** @return the actual dispatch previously assigned to the client */
	public TreeMap<TimeStamp, Double> getDispatch() {
		return dispatches;
	}

	/** @return the market revenues created with the clients dispatch in previous times */
	public TreeMap<TimeStamp, Double> getMarketRevenue() {
		return marketRevenues;
	}

	/** @return the actual (perfect foresight) yield potentials as previously reported by the client */
	public TreeMap<TimeStamp, Double> getYieldPotential() {
		return yieldPotentials;
	}

	/** @return the support revenues previously assigned to the client */
	public TreeMap<TimePeriod, Double> getSupportRevenueInEUR() {
		return supportRevenueInEUR;
	}

	/** @return the previous market premia of the client */
	public TreeMap<TimePeriod, Double> getMarketPremiaInEURperMWH() {
		return marketPremiaInEURperMWH;
	}

	/** Saves the given {@link SupportData} applicable for this client
	 * 
	 * @param supportData associated with this client */
	public void setSupportData(SupportData supportData) {
		this.supportData = supportData;
	}

	/** @return data on the associated support policy */
	public SupportData getSupportData() {
		return supportData;
	}
}
