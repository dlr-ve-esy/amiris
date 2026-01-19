// SPDX-FileCopyrightText: 2026 German Aerospace Center <amiris@dlr.de>
//
// SPDX-License-Identifier: Apache-2.0
package util;

import org.json.JSONObject;

/** This interface imposes that an Object can be translated to {@link JSONObject}.
 * 
 * @author A. Achraf El Ghazi, Christoph Schimeczek */
public interface JSONable {
	/** Return JSON representation of this object. The default implementation will rely on {@link JSONObject} constructor using this
	 * object's getters. May be overwritten to implement manual translation to JSON.
	 * 
	 * @return JSON object created from the implementing object */
	default JSONObject toJson() {
		return new JSONObject(this);
	}
}
