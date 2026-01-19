// SPDX-FileCopyrightText: 2026 German Aerospace Center <amiris@dlr.de>
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
 * @author Farzad Sarfarazi, Johannes Kochems */
public class EndUserTariff {
	private enum FeedInTariffScheme {
		FIXED, TIME_VARYING, NONE
	}

	private enum ComponentType {
		POWER_PRICE, EEG_SURCHARGE, VOLUMETRIC_NETWORK_CHARGE, OTHER_COMPONENTS
	}

	private FeedInTariffScheme feedInTariffScheme;
	private EnumMap<ComponentType, DynamicTariffComponent> dynamicTariffComponents = new EnumMap<>(ComponentType.class);

	private TimeSeries eegSurchargeInEURPerMWH;
	private TimeSeries volumetricNetworkChargeInEURPerMWH;
	private TimeSeries electricityTaxInEURPerMWH;
	private TimeSeries otherSurchargesInEURPerMWH;
	private TimeSeries capacityBasedNetworkChargeInEURPerMW;
	private TimeSeries fixedNetworkChargesInEURPerYear;
	private TimeSeries averageMarketPriceInEURPerMWH;
	private double vat;
	private double fit;
	private double timeVaryingFitMultiplier;
	private double profitMarginInEURPerMWH;

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

	/** Policy-related input parameters to construct an {@link EndUserTariff} */
	public static final GroupBuilder policyParameters = Make.newTree()
			.add(Make.newSeries("EEGSurchargeInEURPerMWH"), Make.newSeries("VolumetricNetworkChargeInEURPerMWH"),
					Make.newSeries("ElectricityTaxInEURPerMWH"), Make.newSeries("OtherSurchargesInEURPerMWH"),
					Make.newGroup("DynamicTariffComponents").list().add(
							Make.newEnum("ComponentName", ComponentType.class).optional(), Make.newSeries("Multiplier").optional(),
							Make.newDouble("LowerBound").optional(), Make.newDouble("UpperBound").optional()),
					Make.newDouble("VAT"), Make.newSeries("CapacityBasedNetworkChargesInEURPerMW"),
					Make.newSeries("FixedNetworkChargesInEURPerYear"),
					Make.newDouble("FitInEURPerMWH").optional(), Make.newDouble("TimeVaryingFiTMultiplier").optional(),
					Make.newEnum("FeedInTariffScheme", FeedInTariffScheme.class).optional());

	/** Business-model related input parameters to construct an {@link EndUserTariff} */
	public static final Tree businessModelParameters = Make.newTree().optional()
			.add(Make.newDouble("ProfitMarginInEURPerMWH"), Make.newSeries("AverageMarketPriceInEURPerMWH")).buildTree();

	/** Creates an {@link EndUserTariff}
	 * 
	 * @param policy containing all policy-based tariff components
	 * @param businessModel containing all business-model related tariff components
	 * @throws MissingDataException if any required data is not provided */
	public EndUserTariff(ParameterData policy, ParameterData businessModel) throws MissingDataException {
		eegSurchargeInEURPerMWH = policy.getTimeSeries("EEGSurchargeInEURPerMWH");
		volumetricNetworkChargeInEURPerMWH = policy.getTimeSeries("VolumetricNetworkChargeInEURPerMWH");
		electricityTaxInEURPerMWH = policy.getTimeSeries("ElectricityTaxInEURPerMWH");
		otherSurchargesInEURPerMWH = policy.getTimeSeries("OtherSurchargesInEURPerMWH");
		for (ParameterData group : policy.getGroupList("DynamicTariffComponents")) {
			dynamicTariffComponents.put(group.getEnum("ComponentName", ComponentType.class),
					new DynamicTariffComponent(group.getTimeSeries("Multiplier"), group.getDoubleOrDefault("LowerBound", 0.0),
							group.getDoubleOrDefault("UpperBound", 200.0)));
		}
		vat = policy.getDouble("VAT");
		capacityBasedNetworkChargeInEURPerMW = policy.getTimeSeries("CapacityBasedNetworkChargesInEURPerMW");
		fixedNetworkChargesInEURPerYear = policy.getTimeSeries("FixedNetworkChargesInEURPerYear");
		feedInTariffScheme = policy.getEnumOrDefault("FeedInTariffScheme", FeedInTariffScheme.class,
				FeedInTariffScheme.NONE);
		fit = policy.getDoubleOrDefault("FitInEURPerMWH", -Double.MAX_VALUE);
		timeVaryingFitMultiplier = policy.getDoubleOrDefault("TimeVaryingFiTMultiplier", -Double.MAX_VALUE);
		profitMarginInEURPerMWH = businessModel.getDouble("ProfitMarginInEURPerMWH");
		averageMarketPriceInEURPerMWH = businessModel.getTimeSeries("AverageMarketPriceInEURPerMWH");
	}

	/** Calculate and return the price at which a retailer energy power to customers
	 * 
	 * @param forecastedMarketPriceInEURPerMWH expected electricity price at the day-ahead market
	 * @param targetTime for which to calculate the electricity retail price
	 * @return calculated sales price */
	public double calcSalePriceInEURperMWH(double forecastedMarketPriceInEURPerMWH, TimeStamp targetTime) {
		double salePrice = ((calcAndReturnTariffComponent(forecastedMarketPriceInEURPerMWH, ComponentType.POWER_PRICE,
				averageMarketPriceInEURPerMWH.getValueEarlierEqual(targetTime), targetTime)
				+ calcAndReturnTariffComponent(forecastedMarketPriceInEURPerMWH, ComponentType.EEG_SURCHARGE,
						eegSurchargeInEURPerMWH.getValueEarlierEqual(targetTime), targetTime)
				+ calcAndReturnTariffComponent(forecastedMarketPriceInEURPerMWH, ComponentType.VOLUMETRIC_NETWORK_CHARGE,
						volumetricNetworkChargeInEURPerMWH.getValueEarlierEqual(targetTime), targetTime)
				+ calcAndReturnTariffComponent(forecastedMarketPriceInEURPerMWH, ComponentType.OTHER_COMPONENTS,
						otherSurchargesInEURPerMWH.getValueEarlierEqual(targetTime)
								+ electricityTaxInEURPerMWH.getValueEarlierEqual(targetTime),
						targetTime)
				+ profitMarginInEURPerMWH) * vat);
		return salePrice;
	}

	/** Calculate and return the price for peak capacity of a customer
	 * 
	 * @param targetTime to calculate at
	 * @return capacity price at given time */
	public double calcCapacityRelatedPriceInEURPerMW(TimeStamp targetTime) {
		return capacityBasedNetworkChargeInEURPerMW.getValueEarlierEqual(targetTime);
	}

	/** Calculate and return the fixed price for, e.g., network charges
	 * 
	 * @param targetTime at which to calculate
	 * @return fixed price */
	public double calcFixedPriceInEURPerYear(TimeStamp targetTime) {
		return fixedNetworkChargesInEURPerYear.getValueEarlierEqual(targetTime);
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
						averageMarketPriceInEURPerMWH.getValueEarlierEqual(targetTime), targetTime));
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
		return averageMarketPriceInEURPerMWH.getValueEarlierEqual(targetTime);
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
