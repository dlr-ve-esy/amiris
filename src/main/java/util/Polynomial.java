// SPDX-FileCopyrightText: 2026 German Aerospace Center <amiris@dlr.de>
//
// SPDX-License-Identifier: Apache-2.0
package util;

import java.util.List;

/** Represents a polynomial of any degree
 * 
 * @author Christoph Schimeczek */
public class Polynomial {
	private final double[] prefactors;

	/** Creates a polynomial of any degree, e.g. a + b*x + c*x**2 + d*x**3, from a List of doubles
	 * 
	 * @param prefactorsInAscendingDegree list of i.e. (a,b,c,d) in the above example */
	public Polynomial(List<Double> prefactorsInAscendingDegree) {
		double[] prefactors = new double[prefactorsInAscendingDegree.size()];
		for (int i = 0; i < prefactors.length; i++) {
			prefactors[i] = prefactorsInAscendingDegree.get(i);
		}
		this.prefactors = prefactors;
	}

	/** Creates a polynomial of any degree, e.g. a + b*x + c*x**2 + d*x**3, from an array of doubles
	 * 
	 * @param prefactorsInAscendingDegree array of i.e. [a,b,c,d] in the above example */
	public Polynomial(double[] prefactorsInAscendingDegree) {
		this.prefactors = prefactorsInAscendingDegree;
	}

	/** Evaluates polynomial function value at given position
	 * 
	 * @param x position to evaluate polynomial
	 * @return value of the polynomial at x */
	public double evaluateAt(double x) {
		double functionValue = 0;
		double powerOfX = 1;
		for (int power = 0; power < prefactors.length; power++) {
			functionValue += prefactors[power] * powerOfX;
			powerOfX *= x;
		}
		return functionValue;
	}
}