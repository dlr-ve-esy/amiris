// SPDX-FileCopyrightText: 2021-2026 German Aerospace Center <amiris@dlr.de>
//
// SPDX-License-Identifier: Apache-2.0
package agents.plantOperator.renewable;

import agents.plantOperator.Marginal;
import agents.plantOperator.RenewablePlantOperator;
import de.dlr.gitlab.fame.agent.input.DataProvider;
import de.dlr.gitlab.fame.agent.input.Input;
import de.dlr.gitlab.fame.agent.input.Make;
import de.dlr.gitlab.fame.agent.input.ParameterData;
import de.dlr.gitlab.fame.agent.input.ParameterData.MissingDataException;
import de.dlr.gitlab.fame.agent.input.Tree;
import de.dlr.gitlab.fame.data.TimeSeries;
import de.dlr.gitlab.fame.time.TimeStamp;

/** An operator of variable renewable energy sources plants that depend on a yield profile.
 * 
 * @author Christoph Schimeczek */
public class VariableRenewableOperator extends RenewablePlantOperator {
	static final String ERR_PPA_PRICE_MISSING = "PPA was requested, but no PPA price found in inputs of ";

	@Input private static final Tree parameters = Make.newTree().add(Make.newSeries("YieldProfile")).buildTree();

	private TimeSeries tsYieldProfile;

	/** Creates an {@link VariableRenewableOperator}
	 * 
	 * @param dataProvider provides input from config
	 * @throws MissingDataException if any required data is not provided */
	public VariableRenewableOperator(DataProvider dataProvider) throws MissingDataException {
		super(dataProvider);
		ParameterData input = parameters.join(dataProvider);
		tsYieldProfile = input.getTimeSeries("YieldProfile");
	}

	/** @return single {@link Marginal} considering variable yield */
	@Override
	protected Marginal calcSingleMarginal(TimeStamp time) {
		double availablePower = getInstalledPowerAtTimeInMW(time) * getYieldAtTime(time);
		double marginalCost = getVariableOpexAtTime(time);
		return new Marginal(availablePower, marginalCost);
	}

	/** Return relative yield profile
	 * 
	 * @param time at which to fetch the yield profile's value
	 * @return yield [0..1] relative to peak capacity at given time */
	protected double getYieldAtTime(TimeStamp time) {
		return tsYieldProfile.getValueLinear(time);
	}
}