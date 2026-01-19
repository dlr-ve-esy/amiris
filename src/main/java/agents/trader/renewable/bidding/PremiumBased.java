// SPDX-FileCopyrightText: 2026 German Aerospace Center <amiris@dlr.de>
//
// SPDX-License-Identifier: Apache-2.0
package agents.trader.renewable.bidding;

import java.util.HashMap;
import java.util.List;
import java.util.TreeMap;
import agents.policy.PolicyItem.SupportInstrument;
import agents.policy.SupportPolicy.EnergyCarrier;
import agents.trader.ClientData;
import communications.message.TechnologySet;
import de.dlr.gitlab.fame.agent.input.GroupBuilder;
import de.dlr.gitlab.fame.agent.input.Make;
import de.dlr.gitlab.fame.agent.input.ParameterBuilder;
import de.dlr.gitlab.fame.agent.input.ParameterData;
import de.dlr.gitlab.fame.agent.input.ParameterData.MissingDataException;
import de.dlr.gitlab.fame.data.TimeSeries;
import de.dlr.gitlab.fame.time.TimePeriod;
import de.dlr.gitlab.fame.time.TimeStamp;

/** Common functionality used by bidding strategies of infeed-based market premia
 * 
 * @author Johannes Kochems, Christoph Schimeczek */
public abstract class PremiumBased {
	static final String ERR_INVALID_NEGATIVE_HOURS = "Positive values for `MaxNumberOfNegativeHours` not implemented. Specified value: ";
	static final String ERR_MARKET_VALUE_FORECAST = " market value forecast method not implemented: ";
	static final String ERR_FORECAST_MISSING = " market value forecast not specified for energy carrier: ";
	static final String ERR_FORECAST_METHOD_MISSING = " `MarketValueForecastMethod` required for support instrument: ";

	private enum MarketValueForecastMethod {
		/** Forecast from previous month */
		PREVIOUS_MONTH,
		/** Forecast from file */
		FROM_FILE
	};

	/** Inputs for market value forecasting */
	public static final ParameterBuilder marketValueForecastParam = Make
			.newEnum("MarketValueForecastMethod", MarketValueForecastMethod.class).optional();
	/** Specific inputs for file-based market value forecasts */
	public static final GroupBuilder fileForecastParams = Make.newGroup("MarketValueForecasts").list().optional().add(
			Make.newEnum("EnergyCarrier", EnergyCarrier.class),
			Make.newSeries("Forecast"));

	private final MarketValueForecastMethod marketValueForecastMethod;
	private final HashMap<EnergyCarrier, TimeSeries> marketValueForecasts = new HashMap<>();

	/** Creates a new {@link PremiumBased} bidding strategy
	 * 
	 * @param input for this bidding strategy type
	 * @throws MissingDataException in case any mandatory input is missing */
	protected PremiumBased(ParameterData input) throws MissingDataException {
		marketValueForecastMethod = input.getEnumOrDefault("MarketValueForecastMethod", MarketValueForecastMethod.class,
				MarketValueForecastMethod.PREVIOUS_MONTH);
		if (marketValueForecastMethod == MarketValueForecastMethod.FROM_FILE) {
			fillPremiumForecastsMap(input.getGroupList("MarketValueForecasts"));
		}
	}

	/** Fills the HashMap for storing market premium forecasts */
	private void fillPremiumForecastsMap(List<ParameterData> groups) throws MissingDataException {
		for (ParameterData group : groups) {
			EnergyCarrier energyCarrier = group.getEnum("EnergyCarrier", EnergyCarrier.class);
			TimeSeries forecast = group.getTimeSeries("Forecast");
			marketValueForecasts.put(energyCarrier, forecast);
		}
	}

	/** Asserts that given max number of hours with negative prices is met with a corresponding bidding strategy. Positive values
	 * for maximum number of negative hours are not supported, yet.
	 * 
	 * @param numberOfNegativeHours to be tested
	 * @throws RuntimeException if no corresponding bidding strategy is available */
	protected void assertMaxNumberOfNegativeHoursFeasible(int numberOfNegativeHours) {
		if (numberOfNegativeHours > 0) {
			throw new RuntimeException(ERR_INVALID_NEGATIVE_HOURS + numberOfNegativeHours);
		}
	}

	/** Find the number of hours directly preceding the given time that have negative prices in a row - either from forecast or
	 * price history
	 * 
	 * @param time for which to calculate
	 * @return number of consecutive hours with negative prices directly before given time */
	protected int calcPreviousNegativeHours(TimeStamp time) {
		return 0;
	}

	/** Return the expected market premium depending on the market premium forecast method
	 * 
	 * @param clientData data specific to the associated client
	 * @param time of marketing of the related electricity
	 * @param instrument {@link SupportInstrument} used by the client
	 * @return expected market premium */
	protected double calcExpectedMarketPremium(ClientData clientData, TimeStamp time, SupportInstrument instrument) {
		if (marketValueForecastMethod == null) {
			throw new RuntimeException(this + ERR_FORECAST_METHOD_MISSING + instrument);
		}
		switch (marketValueForecastMethod) {
			case PREVIOUS_MONTH:
				return calcMarketPremiumPreviousInterval(clientData, time);
			case FROM_FILE:
				return calcMarketPremiumFromFileBasedMarketValue(clientData, time);
			default:
				throw new RuntimeException(this + ERR_MARKET_VALUE_FORECAST + marketValueForecastMethod);
		}
	}

	/** @return the expected market premium of the previous time interval; 0: if no premium was yet logged */
	private double calcMarketPremiumPreviousInterval(ClientData clientData, TimeStamp targetTime) {
		TreeMap<TimePeriod, Double> marketPremium = clientData.getMarketPremiaInEURperMWH();
		if (!marketPremium.isEmpty()) {
			TimePeriod timePeriod = new TimePeriod(targetTime, marketPremium.firstKey().getDuration());
			return marketPremium.get(marketPremium.floorKey(timePeriod));
		} else {
			return 0;
		}
	}

	/** Returns the expected market premium based on the technology's LCOE and a market value forecast read from file from a given
	 * input file; uses lower value if time not present */
	private double calcMarketPremiumFromFileBasedMarketValue(ClientData clientData, TimeStamp targetTime) {
		TechnologySet technologySet = clientData.getTechnologySet();
		double marketValueForecast = getMarketValueForecastAt(technologySet.energyCarrier, targetTime);
		return calcMarketPremium(clientData, marketValueForecast, targetTime);
	}

	/** @return market value forecast read from file for given energy carrier and time */
	private double getMarketValueForecastAt(EnergyCarrier energyCarrier, TimeStamp targetTime) {
		TimeSeries marketValueForecast = marketValueForecasts.get(energyCarrier);
		if (marketValueForecast == null) {
			throw new RuntimeException(this + ERR_FORECAST_MISSING + energyCarrier);
		}
		return marketValueForecasts.get(energyCarrier).getValueEarlierEqual(targetTime);
	}

	/** Calculates the market premium for a given time, client and market value
	 * 
	 * @param clientData data of the client registered for a type of market premium
	 * @param marketValue actual or expected for the given time
	 * @param time for which to calculate the premium
	 * @return market premium for a given time, client and market value */
	protected abstract double calcMarketPremium(ClientData clientData, double marketValue, TimeStamp time);
}
