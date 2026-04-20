// SPDX-FileCopyrightText: 2021-2026 German Aerospace Center <amiris@dlr.de>
//
// SPDX-License-Identifier: Apache-2.0
package agents.storage;

/** Represents a storage device of any kind (e.g. LiIon, pumped-hydro) in its abstract form, i.e. without a specific state of
 * charge
 * 
 * @author Christoph Schimeczek */
public abstract class AbstractDevice {
	/** internal maximum charging / discharging power in MW */
	protected double internalPowerInMW;
	/** internal energy storage capacity in MWh */
	protected double energyStorageCapacityInMWH;
	private double energyToPowerRatio;
	private double selfDischargeRatePerHour;
	private double dischargingEfficiency;
	private double chargingEfficiency;

	/** Creates an {@link AbstractDevice}
	 * 
	 * @param energyToPowerRatio ratio of energy capacity to maximum internal charging and dis-charging power (assumed symmetric)
	 * @param selfDischargeRatePerHour relative share of energy lost per hour proportional to state of charge [0..1]
	 * @param chargingEfficiency efficiency during charging [0..1]; affects maximum external charging power
	 * @param dischargingEfficiency efficiency during discharging [0..1]; affects maximum external discharging power */
	protected AbstractDevice(double energyToPowerRatio, double selfDischargeRatePerHour, double chargingEfficiency,
			double dischargingEfficiency) {
		this.energyToPowerRatio = energyToPowerRatio;
		this.selfDischargeRatePerHour = selfDischargeRatePerHour;
		this.chargingEfficiency = chargingEfficiency;
		this.dischargingEfficiency = dischargingEfficiency;
	}

	/** @return internal charging / discharging power maximum in MW */
	public double getInternalPowerInMW() {
		return internalPowerInMW;
	}

	/** Set internal charging / discharging power maximum in MW; energy storage capacity is adapted accordingly
	 * 
	 * @param internalPowerInMW new internal power */
	public void setInternalPowerInMW(double internalPowerInMW) {
		this.internalPowerInMW = internalPowerInMW;
		energyStorageCapacityInMWH = getEnergyToPowerRatio() * internalPowerInMW;
	}

	/** @return internal energy storage capacity in MWh */
	public double getEnergyStorageCapacityInMWH() {
		return energyStorageCapacityInMWH;
	}

	/** @return the energy-to-power ratio of this storage; equals the time needed to fully charge / discharge the storage */
	public double getEnergyToPowerRatio() {
		return energyToPowerRatio;
	}

	/** @return self discharge <b>rate</b> per hour; values between 0 and 1 */
	public double getSelfDischargeRatePerHour() {
		return selfDischargeRatePerHour;
	}

	/** Returns energy losses due to self discharge based on given energy in storage
	 * 
	 * @param energyInStorageInMWH to be considered
	 * @return energy loss in one hour due to self discharge based on given energy in storage */
	public double calcInternalSelfDischargeInMWH(double energyInStorageInMWH) {
		return energyInStorageInMWH * selfDischargeRatePerHour;
	}

	/** @return relative charging efficiency [0..1] */
	public double getChargingEfficiency() {
		return chargingEfficiency;
	}

	/** @return relative discharging efficiency [0..1] */
	public double getDischargingEfficiency() {
		return dischargingEfficiency;
	}

	/** @return net charging loss factor, to be multiplied to the net energy difference during charging (i.e. the effective internal
	 *         energy amount charged) */
	public double getNetChargingLossFactor() {
		return netLossFactor(chargingEfficiency);
	}

	/** @return gross depletion loss factor, to be multiplied to the gross energy difference during depletion (i.e. the internal
	 *         energy amount depleted) */
	public double getGrossDepletionLossFactor() {
		return grossLossFactor(dischargingEfficiency);
	}

	/** Base equation: E<sub>gross</sub> * &eta; = E<sub>net</sub> <br />
	 * Transforms to: E<sub>gross</sub> - E<sub>net</sub> = E<sub>gross</sub> * ( 1 - &eta; )
	 * 
	 * @param efficiency &eta;
	 * @return net loss factor to be multiplied to net energy in order to yield energy losses */
	private double grossLossFactor(double efficiency) {
		return (1 - efficiency);
	}

	/** Base equation: E<sub>gross</sub> * &eta; = E<sub>net</sub> <br />
	 * Transforms to: E<sub>gross</sub> - E<sub>net</sub> = E<sub>net</sub> * ( 1/&eta; - 1 )
	 * 
	 * @param efficiency &eta;
	 * @return net loss factor to be multiplied to net energy in order to yield energy losses */
	private double netLossFactor(double efficiency) {
		return ((1 / efficiency) - 1);
	}

	/** Return internal energy delta equivalent of given external energy delta
	 * 
	 * @param externalEnergyDelta &gt; 0: charging; &lt; 0: depleting
	 * @return corresponding internal energy delta equivalent */
	public double externalToInternalEnergy(double externalEnergyDelta) {
		if (externalEnergyDelta > 0) {
			return externalEnergyDelta * chargingEfficiency;
		} else {
			return externalEnergyDelta / dischargingEfficiency;
		}
	}

	/** Return external energy delta equivalent of given internal energy delta
	 * 
	 * @param internalEnergyDelta &gt; 0: charging; &lt; 0: depleting
	 * @return external energy delta equivalent */
	public double internalToExternalEnergy(double internalEnergyDelta) {
		if (internalEnergyDelta > 0) {
			return internalEnergyDelta / chargingEfficiency;
		} else {
			return internalEnergyDelta * dischargingEfficiency;
		}
	}

	/** @return maximum external charging power */
	public double getExternalChargingPowerInMW() {
		return internalPowerInMW / chargingEfficiency;
	}

	/** @return maximum external discharging power */
	public double getExternalDischargingPowerInMW() {
		return internalPowerInMW * dischargingEfficiency;
	}
}