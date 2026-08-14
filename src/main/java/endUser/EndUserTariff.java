// SPDX-FileCopyrightText: 2024-2026 German Aerospace Center <amiris@dlr.de>
//
// SPDX-License-Identifier: Apache-2.0
package endUser;

import java.util.EnumMap;
import de.dlr.gitlab.fame.agent.input.GroupBuilder;
import de.dlr.gitlab.fame.agent.input.Make;
import de.dlr.gitlab.fame.agent.input.ParameterData;
import de.dlr.gitlab.fame.agent.input.ParameterData.MissingDataException;
import de.dlr.gitlab.fame.agent.input.Tree;
import de.dlr.gitlab.fame.data.TimeSeries;
import de.dlr.gitlab.fame.time.TimeStamp;

/** Determines end-user tariffs for consumption or feed-in
 * 
 * @author Farzad Sarfarazi, Johannes Kochems, Christoph Schimeczek */
public class EndUserTariff {
	private enum FeedInTariffScheme {
		FIXED, TIME_VARYING, NONE
	}

	private enum ComponentType {
		POWER_PRICE, EEG_SURCHARGE, VOLUMETRIC_NETWORK_CHARGE, OTHER_COMPONENTS
	}

	private FeedInTariffScheme feedInTariffScheme;
	private EnumMap<ComponentType, DynamicTariffComponent> dynamicTariffComponents = new EnumMap<>(ComponentType.class);

	private TimeSeries eegSurchargeInEURperMWH;
	private TimeSeries volumetricNetworkChargeInEURperMWH;
	private TimeSeries electricityTaxInEURperMWH;
	private TimeSeries otherSurchargesInEURperMWH;
	private TimeSeries capacityBasedNetworkChargeInEURperMW;
	private TimeSeries fixedNetworkChargesInEURperYear;
	private TimeSeries averageMarketPriceInEURperMWH;
	private double vat;
	private double fit;
	private double timeVaryingFitMultiplier;
	private double profitMarginInEURperMWH;

	/** Holds configuration for one dynamic tariff component */
	private class DynamicTariffComponent {
		public final TimeSeries multiplier;
		public final double lowerBound;
		public final double upperBound;

		public DynamicTariffComponent(TimeSeries multiplier, double lowerBound, double upperBound) {
			this.multiplier = multiplier;
			this.lowerBound = lowerBound;
			this.upperBound = upperBound;
		}
	}

	static final String PARAM_EEG_SURCHARGE = "EEGSurchargeInEURperMWH";
	static final String PARAM_VOLUMETRIC_CHARGE = "VolumetricNetworkChargeInEURperMWH";
	static final String PARAM_TAX = "ElectricityTaxInEURperMWH";
	static final String PARAM_OTHER_SURCHARGE = "OtherSurchargesInEURperMWH";
	static final String GROUP_DYNAMIC = "DynamicTariffComponents";
	static final String PARAM_COMPONENT = "ComponentName";
	static final String PARAM_MULTIPLIER = "Multiplier";
	static final String PARAM_LOWER = "LowerBound";
	static final String PARAM_UPPER = "UpperBound";
	static final String PARAM_VAT = "VAT";
	static final String PARAM_CAPACITY_CHARGE = "CapacityBasedNetworkChargesInEURperMW";
	static final String PARAM_FIXED_CHARGE = "FixedNetworkChargesInEURperYear";
	static final String PARAM_FIT = "FitInEURperMWH";
	static final String PARAM_VARYING_MULTIPLIER = "TimeVaryingFiTMultiplier";
	static final String PARAM_SCHEME = "FeedInTariffScheme";

	/** Policy-related input parameters to construct an {@link EndUserTariff} */
	public static final GroupBuilder policyParameters = Make.newTree()
			.add(Make.newSeries(PARAM_EEG_SURCHARGE), Make.newSeries(PARAM_VOLUMETRIC_CHARGE),
					Make.newSeries(PARAM_TAX), Make.newSeries(PARAM_OTHER_SURCHARGE),
					Make.newGroup(GROUP_DYNAMIC).list().add(
							Make.newEnum(PARAM_COMPONENT, ComponentType.class).optional(),
							Make.newSeries(PARAM_MULTIPLIER).optional(),
							Make.newDouble(PARAM_LOWER).optional(),
							Make.newDouble(PARAM_UPPER).optional()),
					Make.newDouble(PARAM_VAT), Make.newSeries(PARAM_CAPACITY_CHARGE),
					Make.newSeries(PARAM_FIXED_CHARGE),
					Make.newDouble(PARAM_FIT).optional(), Make.newDouble(PARAM_VARYING_MULTIPLIER).optional(),
					Make.newEnum(PARAM_SCHEME, FeedInTariffScheme.class).optional());

	static final String PARAM_MARGIN = "ProfitMarginInEURperMWH";
	static final String PARAM_AVERAGE_PRICE = "AverageMarketPriceInEURperMWH";

	/** Business-model related input parameters to construct an {@link EndUserTariff} */
	public static final Tree businessModelParameters = Make.newTree().optional()
			.add(Make.newDouble(PARAM_MARGIN), Make.newSeries(PARAM_AVERAGE_PRICE)).buildTree();

	/** Creates an {@link EndUserTariff}
	 * 
	 * @param policy containing all policy-based tariff components
	 * @param businessModel containing all business-model related tariff components
	 * @throws MissingDataException if any required data is not provided */
	public EndUserTariff(ParameterData policy, ParameterData businessModel) throws MissingDataException {
		eegSurchargeInEURperMWH = policy.getTimeSeries(PARAM_EEG_SURCHARGE);
		volumetricNetworkChargeInEURperMWH = policy.getTimeSeries(PARAM_VOLUMETRIC_CHARGE);
		electricityTaxInEURperMWH = policy.getTimeSeries(PARAM_TAX);
		otherSurchargesInEURperMWH = policy.getTimeSeries(PARAM_OTHER_SURCHARGE);
		for (ParameterData group : policy.getGroupList(GROUP_DYNAMIC)) {
			var type = group.getEnum(PARAM_COMPONENT, ComponentType.class);
			var component = new DynamicTariffComponent(group.getTimeSeries(PARAM_MULTIPLIER),
					group.getDoubleOrDefault(PARAM_LOWER, 0.0), group.getDoubleOrDefault(PARAM_UPPER, 200.0));
			dynamicTariffComponents.put(type, component);
		}
		vat = policy.getDouble(PARAM_VAT);
		capacityBasedNetworkChargeInEURperMW = policy.getTimeSeries(PARAM_CAPACITY_CHARGE);
		fixedNetworkChargesInEURperYear = policy.getTimeSeries(PARAM_FIXED_CHARGE);
		fit = policy.getDoubleOrDefault(PARAM_FIT, -Double.MAX_VALUE);
		timeVaryingFitMultiplier = policy.getDoubleOrDefault(PARAM_VARYING_MULTIPLIER, -Double.MAX_VALUE);
		feedInTariffScheme = policy.getEnumOrDefault(PARAM_SCHEME, FeedInTariffScheme.class, FeedInTariffScheme.NONE);
		profitMarginInEURperMWH = businessModel.getDouble(PARAM_MARGIN);
		averageMarketPriceInEURperMWH = businessModel.getTimeSeries(PARAM_AVERAGE_PRICE);
	}

	/** Calculate and return the price at which a retailer energy power to customers
	 * 
	 * @param forecastedMarketPriceInEURPerMWH expected electricity price at the day-ahead market
	 * @param targetTime for which to calculate the electricity retail price
	 * @return calculated sales price */
	public double calcSalePriceInEURperMWH(double forecastedMarketPriceInEURPerMWH, TimeStamp targetTime) {
		double salePrice = ((calcAndReturnTariffComponent(forecastedMarketPriceInEURPerMWH, ComponentType.POWER_PRICE,
				averageMarketPriceInEURperMWH.getValueEarlierEqual(targetTime), targetTime)
				+ calcAndReturnTariffComponent(forecastedMarketPriceInEURPerMWH, ComponentType.EEG_SURCHARGE,
						eegSurchargeInEURperMWH.getValueEarlierEqual(targetTime), targetTime)
				+ calcAndReturnTariffComponent(forecastedMarketPriceInEURPerMWH, ComponentType.VOLUMETRIC_NETWORK_CHARGE,
						volumetricNetworkChargeInEURperMWH.getValueEarlierEqual(targetTime), targetTime)
				+ calcAndReturnTariffComponent(forecastedMarketPriceInEURPerMWH, ComponentType.OTHER_COMPONENTS,
						otherSurchargesInEURperMWH.getValueEarlierEqual(targetTime)
								+ electricityTaxInEURperMWH.getValueEarlierEqual(targetTime),
						targetTime)
				+ profitMarginInEURperMWH) * vat);
		return salePrice;
	}

	/** Calculate and return the price for peak capacity of a customer
	 * 
	 * @param targetTime to calculate at
	 * @return capacity price at given time */
	public double calcCapacityRelatedPriceInEURPerMW(TimeStamp targetTime) {
		return capacityBasedNetworkChargeInEURperMW.getValueEarlierEqual(targetTime);
	}

	/** Calculate and return the fixed price for, e.g., network charges
	 * 
	 * @param targetTime at which to calculate
	 * @return fixed price */
	public double calcFixedPriceInEURPerYear(TimeStamp targetTime) {
		return fixedNetworkChargesInEURperYear.getValueEarlierEqual(targetTime);
	}

	/** Calculate and return the price at which a retailer provides power to customers excluding the actual wholesale day-ahead
	 * power price
	 * 
	 * @param forecastedMarketPriceInEURPerMWH expected wholesale market price
	 * @param targetTime at which to calculate
	 * @return retail price without wholesale price component */
	public double calcSalePriceExcludingPowerPriceInEURPerMWH(double forecastedMarketPriceInEURPerMWH,
			TimeStamp targetTime) {
		return calcSalePriceInEURperMWH(forecastedMarketPriceInEURPerMWH, targetTime)
				- (calcAndReturnTariffComponent(forecastedMarketPriceInEURPerMWH, ComponentType.POWER_PRICE,
						averageMarketPriceInEURperMWH.getValueEarlierEqual(targetTime), targetTime));
	}

	/** Return true if tariff is static
	 * 
	 * @return whether or not power price is static */
	public boolean isStaticPowerPrice() {
		return !dynamicTariffComponents.containsKey(ComponentType.POWER_PRICE);
	}

	/** Gets the static average market price
	 * 
	 * @param targetTime time for which static power price is evaluated
	 * @return static power price */
	public double getStaticPowerPrice(TimeStamp targetTime) {
		return averageMarketPriceInEURperMWH.getValueEarlierEqual(targetTime);
	}

	/** Calculate purchase price based on feed in tariff
	 * 
	 * @param forecastedMarketPriceInEURPerMWH forecasted market price
	 * @return feed in tariff at given forecasted market price */
	public double calcPurchasePriceInEURPerMWH(double forecastedMarketPriceInEURPerMWH) {
		return getFeedInTariff(forecastedMarketPriceInEURPerMWH);
	}

	/** Get the feed-in tariff, which may be either static or dependent on given market price forecast
	 * 
	 * @param forecastedMarketPriceInEURPerMWH forecasted market price
	 * @return feed-in tariff */
	private double getFeedInTariff(double forecastedMarketPriceInEURPerMWH) {
		switch (feedInTariffScheme) {
			case FIXED:
				return fit;
			case TIME_VARYING:
				double timeVaryingFiT = forecastedMarketPriceInEURPerMWH * timeVaryingFitMultiplier;
				return timeVaryingFiT > fit * 2 ? fit * 2 : timeVaryingFiT < 0 ? 0 : timeVaryingFiT;
			default:
				throw new RuntimeException("FIT scheme not implemented.");
		}
	}

	/** Calculate and individual tariff component and return either its static or a calculated dynamic value
	 * 
	 * @param forecastedMarketPriceInEURPerMWH forecasted market price
	 * @param componentName name of tariff component
	 * @return tariff component */
	private double calcAndReturnTariffComponent(double forecastedMarketPriceInEURPerMWH, ComponentType componentName,
			double staticValueInEURPerMWH, TimeStamp targetTime) {
		if (dynamicTariffComponents.containsKey(componentName)) {
			return calcDynamicTariffComponent(forecastedMarketPriceInEURPerMWH, componentName, targetTime);
		} else {
			return staticValueInEURPerMWH;
		}
	}

	/** Calculate the value of a dynamic tariff component for a given power price taking into account upper and lower bounds
	 * 
	 * @param forecastedMarketPriceInEURPerMWH forecasted market price
	 * @param componentName name of tariff component
	 * @param targetTime time at which tariff is calculated
	 * @return dynamic tariff component */
	private double calcDynamicTariffComponent(double forecastedMarketPriceInEURPerMWH, ComponentType componentName,
			TimeStamp targetTime) {
		double dynamicTariffComponentInEURPerMWH = forecastedMarketPriceInEURPerMWH
				* dynamicTariffComponents.get(componentName).multiplier.getValueEarlierEqual(targetTime);
		DynamicTariffComponent component = dynamicTariffComponents.get(componentName);
		return Math.max(component.lowerBound, Math.min(component.upperBound, dynamicTariffComponentInEURPerMWH));
	}
}
