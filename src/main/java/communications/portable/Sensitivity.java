// SPDX-FileCopyrightText: 2026 German Aerospace Center <amiris@dlr.de>
//
// SPDX-License-Identifier: Apache-2.0
package communications.portable;

import agents.forecast.sensitivity.MarketClearingAssessment;
import de.dlr.gitlab.fame.communication.transfer.ComponentCollector;
import de.dlr.gitlab.fame.communication.transfer.ComponentProvider;
import de.dlr.gitlab.fame.communication.transfer.Portable;

/** A Message that contains the sensitivity of a merit order forecast depending on additional demand or supply. The type of
 * sensitivity is unspecified here and should be known to the client.
 * 
 * @author Johannes Kochems, Christoph Schimeczek */
public class Sensitivity implements Portable {
	static final String ERR_INTERPOLATION_TYPE = "Interpolation type not implemented: ";

	/** Available types of interpolation used during value calculations */
	public enum InterpolationType {
		/** Values are calculated as a cumulative sum: last value from previous interval + linearly interpolated value within the
		 * current interval */
		CUMULATIVE,
		/** Values are linearly interpolated from the current interval only: ignoring values from previous intervals */
		DIRECT
	}

	private double multiplier;
	private double[] demandPowers;
	private double[] demandValues;
	private double[] supplyPowers;
	private double[] supplyValues;

	private int lastDemandIndex = 1;
	private int lastSupplyIndex = 1;
	private double lastDemandEnergy = 0.;
	private double lastSupplyEnergy = 0.;

	private InterpolationType interpolationType;

	/** required for {@link Portable}s */
	public Sensitivity() {}

	/** Instantiates a new Sensitivity
	 * 
	 * @param assessment to extract demand and supply change sensitivities from
	 * @param multiplier associated with the client to received this {@link Sensitivity} */
	public Sensitivity(MarketClearingAssessment assessment, double multiplier) {
		this.demandPowers = assessment.getDemandSensitivityPowers();
		this.demandValues = assessment.getDemandSensitivityValues();
		this.supplyPowers = assessment.getSupplySensitivityPowers();
		this.supplyValues = assessment.getSupplySensitivityValues();
		this.multiplier = multiplier;
	}

	/** Returns multiplier currently set in this {@link Sensitivity}
	 * 
	 * @return the current multiplier */
	public double getMultiplier() {
		return multiplier;
	}

	/** Sets a new multiplier that is used in sensitivity calculations
	 * 
	 * @param multiplier to be applied in future calls to {@link #getValue(double)} */
	public void updateMultiplier(double multiplier) {
		this.multiplier = multiplier;
		lastDemandIndex = 1;
		lastSupplyIndex = 1;
		lastDemandEnergy = 0;
		lastSupplyEnergy = 0;
	}

	/** Set the type of interpolation to be used during value calculations
	 * 
	 * @param interpolationType to be used during calculations in {@link #getValue(double)} */
	public void setInterpolationType(InterpolationType interpolationType) {
		this.interpolationType = interpolationType;
	}

	/** Returns sensitivity value for given requested energy delta
	 * 
	 * @param requestedEnergyInMWH demand &gt; 0; supply &lt; 0
	 * @return sensitivity value */
	public double getValue(double requestedEnergyInMWH) {
		double modifiedEnergy = multiplier * requestedEnergyInMWH;
		if (modifiedEnergy > 0) {
			return getValueAddedDemand(modifiedEnergy);
		} else if (modifiedEnergy < 0) {
			return getValueAddedSupply(-modifiedEnergy);
		}
		return 0;
	}

	/** @return value associated with the additional demand energy */
	private double getValueAddedDemand(double additionalDemandInMWH) {
		int firstIndex = additionalDemandInMWH >= lastDemandEnergy ? lastDemandIndex : 1;
		for (int index = firstIndex; index < demandPowers.length; index++) {
			if (demandPowers[index] >= additionalDemandInMWH) {
				lastDemandEnergy = additionalDemandInMWH;
				lastDemandIndex = index;
				return interpolateValue(demandPowers[index - 1], demandValues[index - 1], demandPowers[index],
						demandValues[index], additionalDemandInMWH);
			}
		}
		return Double.NaN;
	}

	/** @return y-value interpolated for given position x on a line determined by (x1,y1) and (x2,y2) */
	private double interpolateValue(double x1, double y1, double x2, double y2, double x) {
		switch (interpolationType) {
			case CUMULATIVE:
				return y1 + (y2 - y1) / (x2 - x1) * (x - x1);
			case DIRECT:
				return (y2 - y1) / (x2 - x1) * x;
			default:
				throw new RuntimeException(ERR_INTERPOLATION_TYPE + interpolationType);
		}
	}

	/** @return value associated with the additional supply energy */
	private double getValueAddedSupply(double additionalSupplyInMWH) {
		int firstIndex = additionalSupplyInMWH >= lastSupplyEnergy ? lastSupplyIndex : 1;
		for (int index = firstIndex; index < supplyPowers.length; index++) {
			if (supplyPowers[index] >= additionalSupplyInMWH) {
				lastSupplyEnergy = additionalSupplyInMWH;
				lastSupplyIndex = index;
				return interpolateValue(supplyPowers[index - 1], supplyValues[index - 1], supplyPowers[index],
						supplyValues[index], additionalSupplyInMWH);
			}
		}
		return Double.NaN;
	}

	@Override
	public void addComponentsTo(ComponentCollector collector) {
		collector.storeDoubles(multiplier);
		storeDoubleArray(collector, demandPowers);
		storeDoubleArray(collector, demandValues);
		storeDoubleArray(collector, supplyPowers);
		storeDoubleArray(collector, supplyValues);
	}

	/** Stores length of given array and array values to provided collector */
	private final void storeDoubleArray(ComponentCollector collector, double[] data) {
		collector.storeInts(data.length);
		collector.storeDoubles(data);
	}

	@Override
	public void populate(ComponentProvider provider) {
		multiplier = provider.nextDouble();
		demandPowers = readArray(provider, provider.nextInt());
		demandValues = readArray(provider, provider.nextInt());
		supplyPowers = readArray(provider, provider.nextInt());
		supplyValues = readArray(provider, provider.nextInt());
	}

	/** @return array with given length read from given provider */
	private final double[] readArray(ComponentProvider provider, int length) {
		double[] array = new double[length];
		for (int i = 0; i < length; i++) {
			array[i] = provider.nextDouble();
		}
		return array;
	}
}
