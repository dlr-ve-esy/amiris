// SPDX-FileCopyrightText: 2021-2026 German Aerospace Center <amiris@dlr.de>
//
// SPDX-License-Identifier: Apache-2.0
package agents.storage;

import de.dlr.gitlab.fame.agent.input.GroupBuilder;
import de.dlr.gitlab.fame.agent.input.Make;
import de.dlr.gitlab.fame.agent.input.ParameterData;
import de.dlr.gitlab.fame.agent.input.ParameterData.MissingDataException;

/** Represents a physical energy storage device (e.g. LiIon battery or pumped-hydro plant)
 *
 * @author Christoph Schimeczek */
public class Device extends AbstractDevice {
	private double currentEnergyInStorageInMWH;
	private double accountedInternalEnergyFlowsInMWH = 0;
	private double accountedFullStorageCycles = 0;

	/** Input parameters of a storage {@link Device} */
	public static final GroupBuilder parameters = Make.newTree()
			.add(Make.newDouble("EnergyToPowerRatio"), Make.newDouble("SelfDischargeRatePerHour"),
					Make.newDouble("ChargingEfficiency"), Make.newDouble("DischargingEfficiency"),
					Make.newDouble("InitialEnergyLevelInMWH"), Make.newDouble("InstalledPowerInMW"));

	/** Creates a physical {@link Device}
	 * 
	 * @param input ParameterData group to match the {@link #parameters} Tree
	 * @throws MissingDataException if any required data is not provided */
	public Device(ParameterData input) throws MissingDataException {
		super(input.getDouble("EnergyToPowerRatio"), input.getDouble("SelfDischargeRatePerHour"),
				input.getDouble("ChargingEfficiency"), input.getDouble("DischargingEfficiency"));
		setInternalPowerInMW(input.getDouble("InstalledPowerInMW"));
		setInternalEnergyInMWH(input.getDouble("InitialEnergyLevelInMWH"));
	}

	/** Set the initial energy content and thereby ensure to keep it within bounds */
	private void setInternalEnergyInMWH(double initialEnergyLevelInMWH) {
		this.currentEnergyInStorageInMWH = Math.max(0, Math.min(initialEnergyLevelInMWH, energyStorageCapacityInMWH));
	}

	/** Updates internal charging and discharging power of this {@link Device}. Since E2P ratio is fixed this will also change the
	 * storage capacity. */
	@Override
	public void setInternalPowerInMW(double internalPowerInMW) {
		super.setInternalPowerInMW(internalPowerInMW);
		currentEnergyInStorageInMWH = Math.min(currentEnergyInStorageInMWH, energyStorageCapacityInMWH);
	}

	/** Reset tracked internal energy flows and full storage cycles to 0 */
	public void resetEnergyAccounting() {
		accountedInternalEnergyFlowsInMWH = 0;
		accountedFullStorageCycles = 0;
	}

	/** (Dis-)charges this device according to given external energy delta
	 * 
	 * @param externalChargingPower
	 *          <ul>
	 *          <li>externalChargingPower &gt; 0: charging</li>
	 *          <li>externalChargingPower &lt; 0: depleting</li>
	 *          </ul>
	 * @return actual external charging power, considering power and energy capacity restrictions of this device */
	public double chargeInMW(double externalChargingPower) {
		double internalChargingPowerInMW = externalToInternalEnergy(externalChargingPower);
		internalChargingPowerInMW = considerPowerLimits(internalChargingPowerInMW);
		double internalSelfDischargeInMWH = calcInternalSelfDischargeInMWH(currentEnergyInStorageInMWH);
		double nextEnergyInStorageInMWH = currentEnergyInStorageInMWH + internalChargingPowerInMW
				- internalSelfDischargeInMWH;
		nextEnergyInStorageInMWH = considerEnergyRestrictions(nextEnergyInStorageInMWH);

		double internalEnergyDelta = nextEnergyInStorageInMWH - currentEnergyInStorageInMWH;
		currentEnergyInStorageInMWH = nextEnergyInStorageInMWH;

		accountedInternalEnergyFlowsInMWH += internalEnergyDelta;
		accountedFullStorageCycles += calcFullStorageCylces(internalEnergyDelta);
		return internalToExternalEnergy(internalEnergyDelta + internalSelfDischargeInMWH);
	}

	/** reduces given (dis-)charging power to the maximum defined by {@link #getInternalPowerInMW()} */
	private double considerPowerLimits(double internalChargingPowerInMW) {
		if (internalChargingPowerInMW > 0) {
			return Math.min(internalChargingPowerInMW, internalPowerInMW);
		} else {
			return Math.max(internalChargingPowerInMW, -internalPowerInMW);
		}
	}

	/** ensures that the storage energy content is not negative and smaller or equal to
	 * {@link AbstractDevice#energyStorageCapacityInMWH} */
	private double considerEnergyRestrictions(double nextEnergyInStorageInMWH) {
		return Math.min(Math.max(0, nextEnergyInStorageInMWH), energyStorageCapacityInMWH);
	}

	/** @return the storage cycle equivalent for an internal energy delta; a storage cycle equals one full depletion and full
	 *         re-charging of the storage */
	private double calcFullStorageCylces(double internalEnergyDeltaInMWH) {
		return internalEnergyDeltaInMWH / (2.0 * energyStorageCapacityInMWH);
	}

	/** @return total of accounted internal energy flows */
	public double getAccountedInternalEnergyFlowsInMWH() {
		return accountedInternalEnergyFlowsInMWH;
	}

	/** @return total of accounted full storage cycles */
	public double getAccountedFullStorageCycles() {
		return accountedFullStorageCycles;
	}

	/** @return current (internal) energy content in storage */
	public double getCurrentEnergyInStorageInMWH() {
		return currentEnergyInStorageInMWH;
	}
}