// SPDX-FileCopyrightText: 2025-2026 German Aerospace Center <amiris@dlr.de>
//
// SPDX-License-Identifier: Apache-2.0
package agents.flexibility.dynamicProgramming.assessment;

import java.util.ArrayList;
import java.util.TreeMap;
import agents.flexibility.GenericDevice;
import communications.message.PointInTime;
import communications.portable.Sensitivity;
import communications.portable.Sensitivity.InterpolationType;
import de.dlr.gitlab.fame.communication.message.Message;
import de.dlr.gitlab.fame.time.TimeStamp;

/** {@link AssessmentFunction} that is based on {@link Sensitivity}
 * 
 * @author Christoph Schimeczek */
public abstract class SensitivityBasedAssessment implements AssessmentFunction {
	private final TreeMap<TimeStamp, Sensitivity> sensitivityForecasts = new TreeMap<>();
	protected Sensitivity currentSensitivity;
	protected GenericDevice device;
	protected double currentVariableCostInEURperMWH;

	public SensitivityBasedAssessment(GenericDevice device) {
		this.device = device;
	}

	@Override
	public void prepareFor(TimeStamp time) {
		currentSensitivity = sensitivityForecasts.get(time);
		currentVariableCostInEURperMWH = device.getVariableCostInEURperMWH(time);
	}

	@Override
	public abstract double assessTransition(double externalEnergyDeltaInMWH);

	@Override
	public void clearBefore(TimeStamp time) {
		Util.clearMapBefore(sensitivityForecasts, time);
	}

	@Override
	public ArrayList<TimeStamp> getMissingForecastTimes(ArrayList<TimeStamp> planningTimes) {
		return Util.findMissingKeys(sensitivityForecasts, planningTimes);
	}

	@Override
	public void storeForecast(ArrayList<Message> messages) {
		double multiplier = 0;
		for (Message inputMessage : messages) {
			Sensitivity sensitivity = inputMessage.getAllPortableItemsOfType(Sensitivity.class).get(0);
			sensitivity.setInterpolationType(getInterpolationType());
			multiplier = sensitivity.getMultiplier();
			TimeStamp time = inputMessage.getDataItemOfType(PointInTime.class).validAt;
			sensitivityForecasts.put(time, sensitivity);
		}
		for (var entry : sensitivityForecasts.entrySet()) {
			entry.getValue().updateMultiplier(multiplier);
		}
	}

	/** Returns the type of interpolation used by this kind of {@link AssessmentFunction}
	 * 
	 * @return the type of value interpolation used */
	protected abstract InterpolationType getInterpolationType();

	@Override
	public final double getMultiplier() {
		return currentSensitivity.getMultiplier();
	}

	@Override
	public double getElectricityPriceAt(TimeStamp time, double externalEnergyDeltaInMWH) {
		return sensitivityForecasts.get(time).getPriceInEURperMWH(externalEnergyDeltaInMWH);
	}
}
