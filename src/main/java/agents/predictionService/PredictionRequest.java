// SPDX-FileCopyrightText: 2026 German Aerospace Center <amiris@dlr.de>
//
// SPDX-License-Identifier: Apache-2.0
package agents.predictionService;

import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;
import util.JSONable;

/** Encapsulates a prediction request of a general prediction model. This class is designed primarily for time series prediction,
 * where each variable's value is associated with a time reference (or an ordered index).
 * 
 * @author A. Achraf El Ghazi, Felix Nitsch, Ulrich Frey */
public class PredictionRequest implements JSONable {
	private String modelId;
	private long predictionStart;
	private List<InputVariable> inputVars;

	/** Creates a {@link PredictionRequest} based on given input parameters
	 * 
	 * @param modelId ID of the ML model that should perform the prediction
	 * @param predictionStart time point of the prediction
	 * @param inputVars input data of the prediction */
	public PredictionRequest(String modelId, long predictionStart, List<InputVariable> inputVars) {
		this.modelId = modelId;
		this.predictionStart = predictionStart;
		this.inputVars = inputVars;
	}

	@Override
	public JSONObject toJson() {
		JSONObject json = new JSONObject();
		json.put("modelId", modelId);
		json.put("predictionStart", predictionStart);
		JSONArray inputVarsArray = new JSONArray();
		for (InputVariable inputVar : inputVars) {
			inputVarsArray.put(inputVar.toJson());
		}
		json.put("inputVars", inputVarsArray);

		return json;
	}
}
