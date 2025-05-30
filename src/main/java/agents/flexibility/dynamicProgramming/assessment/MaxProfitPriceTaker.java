// SPDX-FileCopyrightText: 2025 German Aerospace Center <amiris@dlr.de>
//
// SPDX-License-Identifier: Apache-2.0
package agents.flexibility.dynamicProgramming.assessment;

import java.util.ArrayList;
import java.util.TreeMap;
import agents.flexibility.Strategist;
import agents.flexibility.dynamicProgramming.Optimiser.Target;
import communications.message.AmountAtTime;
import de.dlr.gitlab.fame.communication.message.Message;
import de.dlr.gitlab.fame.time.TimePeriod;
import de.dlr.gitlab.fame.time.TimeStamp;

/** Maximise profit of transitions using an electricity price forecast neglecting any price impact of bids
 * 
 * @author Christoph Schimeczek, Felix Nitsch, Johannes Kochems */
public class MaxProfitPriceTaker implements AssessmentFunction {
	private double currentElectricityPriceInEURperMWH;
	private TreeMap<TimeStamp, Double> electricityPriceForecastsInEURperMWH = new TreeMap<>();

	@Override
	public void prepareFor(TimeStamp time) {
		currentElectricityPriceInEURperMWH = electricityPriceForecastsInEURperMWH.getOrDefault(time, 0.);
	}

	@Override
	public double assessTransition(double externalEnergyDeltaInMWH) {
		return -externalEnergyDeltaInMWH * currentElectricityPriceInEURperMWH;
	}

	@Override
	public void clearBefore(TimeStamp time) {
		Util.clearMapBefore(electricityPriceForecastsInEURperMWH, time);
	}

	@Override
	public ArrayList<TimeStamp> getMissingForecastTimes(ArrayList<TimeStamp> requiredTimes) {
		return Util.findMissingKeys(electricityPriceForecastsInEURperMWH, requiredTimes);
	}

	@Override
	public void storeForecast(ArrayList<Message> messages) {
		for (Message inputMessage : messages) {
			AmountAtTime priceForecastMessage = inputMessage.getDataItemOfType(AmountAtTime.class);
			double priceForecast = priceForecastMessage.amount;
			TimePeriod timePeriod = new TimePeriod(priceForecastMessage.validAt, Strategist.OPERATION_PERIOD);
			priceForecast = Double.isNaN(priceForecast) ? 0. : priceForecast;
			electricityPriceForecastsInEURperMWH.put(timePeriod.getStartTime(), priceForecast);
		}
	}

	@Override
	public Target getTargetType() {
		return Target.MAXIMISE;
	}
}
