// SPDX-FileCopyrightText: 2026 German Aerospace Center <amiris@dlr.de>
//
// SPDX-License-Identifier: Apache-2.0
package agents.forecast.sensitivity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import agents.forecast.MarketForecaster;
import communications.message.AmountAtTime;
import communications.message.ForecastClientRegistration;
import communications.message.PointInTime;
import communications.portable.Sensitivity;
import de.dlr.gitlab.fame.agent.input.DataProvider;
import de.dlr.gitlab.fame.agent.input.Input;
import de.dlr.gitlab.fame.agent.input.Make;
import de.dlr.gitlab.fame.agent.input.ParameterData;
import de.dlr.gitlab.fame.agent.input.ParameterData.MissingDataException;
import de.dlr.gitlab.fame.agent.input.Tree;
import de.dlr.gitlab.fame.communication.CommUtils;
import de.dlr.gitlab.fame.communication.Contract;
import de.dlr.gitlab.fame.communication.message.Message;
import de.dlr.gitlab.fame.time.TimeStamp;
import util.TimedDataMap;

/** Forecasts sensitivities of market clearing results with respect to changes in demand and supply
 * 
 * @author Christoph Schimeczek, Johannes Kochems */
public class SensitivityForecaster extends MarketForecaster implements SensitivityForecastProvider {
	static final String ERR_UNREGISTERED = "Client '%d' is not registered at SensitivityForecaster '%s'. Add `ForecastRegistration` contract before `SensitivityRequest`.";

	@Input private static final Tree parameters = Make.newTree().add(Make.newGroup("MultiplierEstimation").optional()
			.add(Make.newDouble("IgnoreAwardFactor").optional()
					.help("Awards with less energy than maximum energy divided by this factor are ignored."))
			.add(Make.newInt("InitialEstimateWeight").optional().help("Weight of the initial estimate."))
			.add(Make.newInt("DecayInterval").optional()
					.help("Interval steps after which a factor weight has reduced to exp(-1).")))
			.buildTree();

	private final FlexibilityAssessor flexibilityAssessor;
	private final HashMap<Long, ForecastType> typePerClient = new HashMap<>();
	private final TimedDataMap<ForecastType, MarketClearingAssessment> assessments = new TimedDataMap<>();

	/** Instantiate a new {@link SensitivityForecaster}
	 * 
	 * @param dataProvider input from config
	 * @throws MissingDataException if any required data is not provided */
	public SensitivityForecaster(DataProvider dataProvider) throws MissingDataException {
		super(dataProvider);
		ParameterData input = parameters.join(dataProvider);
		double cutOffFactor = input.getDoubleOrDefault("MultiplierEstimation.IgnoreAwardFactor", 1000.);
		int initialEstimateWeight = input.getIntegerOrDefault("MultiplierEstimation.InitialEstimateWeight", 24);
		int decayInterval = input.getIntegerOrDefault("MultiplierEstimation.DecayInterval", -1);
		flexibilityAssessor = new FlexibilityAssessor(cutOffFactor, initialEstimateWeight, decayInterval);

		call(this::registerClients).onAndUse(SensitivityForecastClient.Products.ForecastRegistration);
		call(this::updateForecastMultipliers).onAndUse(SensitivityForecastClient.Products.NetAward);
		call(this::sendSensitivityForecasts).on(SensitivityForecastProvider.Products.SensitivityForecast)
				.use(SensitivityForecastClient.Products.SensitivityRequest);
	}

	/** Register clients that sent a registration message */
	private void registerClients(ArrayList<Message> input, List<Contract> contracts) {
		for (Message message : input) {
			long clientId = message.getSenderId();
			var registration = message.getDataItemOfType(ForecastClientRegistration.class);
			flexibilityAssessor.registerClient(clientId, registration.amount);
			typePerClient.put(clientId, registration.type);
		}
		flexibilityAssessor.processInput();
		flexibilityAssessor.clearBefore(now());
	}

	/** Save net awards sent by clients and update their forecast multiplier history */
	private void updateForecastMultipliers(ArrayList<Message> input, List<Contract> contracts) {
		for (Message message : input) {
			AmountAtTime award = message.getDataItemOfType(AmountAtTime.class);
			flexibilityAssessor.saveAward(message.getSenderId(), award);
		}
		flexibilityAssessor.processInput();
	}

	/** Calculate new sensitivities, update multiplier averages, and send out new forecasts to clients */
	private void sendSensitivityForecasts(ArrayList<Message> messages, List<Contract> contracts) {
		for (Contract contract : contracts) {
			long clientId = contract.getReceiverId();
			double multiplier = flexibilityAssessor.getMultiplier(clientId);
			ArrayList<Message> requests = CommUtils.extractMessagesFrom(messages, clientId);
			for (Message message : requests) {
				TimeStamp time = message.getDataItemOfType(PointInTime.class).validAt;
				MarketClearingAssessment assessment = getAssessmentFor(getForecastTypeOfClient(clientId), time);
				fulfilNext(contract, new Sensitivity(assessment, multiplier), new PointInTime(time));
			}
		}
		flexibilityAssessor.clearBefore(now());
		assessments.clearBefore(now());
		saveNextForecast();
	}

	/** @return type of forecast of given client; throws a RuntimeException if client is not registered */
	private ForecastType getForecastTypeOfClient(long clientId) {
		ForecastType type = typePerClient.get(clientId);
		if (type == null) {
			throw new RuntimeException(String.format(ERR_UNREGISTERED, clientId, this));
		}
		return type;
	}

	/** @return the assessment of type associated with the given client at the specified time */
	private MarketClearingAssessment getAssessmentFor(ForecastType type, TimeStamp time) {
		assessments.computeIfAbsent(time, type, () -> buildAssessor(time, type));
		return assessments.get(time, type);
	}

	/** Create a new {@link MarketClearingAssessment} for given time and {@link ForecastType} */
	private MarketClearingAssessment buildAssessor(TimeStamp time, ForecastType type) {
		MarketClearingAssessment assessor = MarketClearingAssessment.build(type);
		assessor.assess(getResultForRequestedTime(time));
		return assessor;
	}
}
