// SPDX-FileCopyrightText: 2026 German Aerospace Center <amiris@dlr.de>
//
// SPDX-License-Identifier: Apache-2.0
package agents.predictionService;

import java.util.Map;
import org.json.JSONObject;
import util.JSONable;

/** Creates an input variable for the the training and prediction of an machine-learning model
 * 
 * @author A. Achraf El Ghazi, Ulrich Frey */
public class InputVariable implements JSONable {
	private String name;
	private Map<Long, Double> values;

	/** Instantiate a new {@link InputVariable}
	 * 
	 * @param name of the input variable
	 * @param values to be associated with the name */
	public InputVariable(String name, Map<Long, Double> values) {
		this.name = name;
		this.values = values;
	}

	@Override
	public JSONObject toJson() {
		JSONObject json = new JSONObject();
		json.put("name", name);
		JSONObject valuesJson = new JSONObject();
		for (Map.Entry<Long, Double> entry : values.entrySet()) {
			valuesJson.put(entry.getKey().toString(), entry.getValue());
		}
		json.put("values", valuesJson);

		return json;
	}
}
