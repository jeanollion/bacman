/*
 * #%L
 * ImgLib2: a general-purpose, multidimensional image processing library.
 * %%
 * Copyright (C) 2009 - 2015 Tobias Pietzsch, Stephan Preibisch, Barry DeZonia,
 * Stephan Saalfeld, Curtis Rueden, Albert Cardona, Christian Dietz, Jean-Yves
 * Tinevez, Johannes Schindelin, Jonathan Hale, Lee Kamentsky, Larry Lindsey, Mark
 * Hiner, Michael Zinsmaier, Martin Horn, Grant Harris, Aivar Grislis, John
 * Bogovic, Steffen Jaensch, Stefan Helfrich, Jan Funke, Nick Perry, Mark Longair,
 * Melissa Linkert and Dimiter Prodanov.
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 2 of the 
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public 
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/gpl-2.0.html>.
 * #L%
 */

package processing.gaussianFit;

import net.imglib2.algorithm.localization.FitFunction;

/**
 * A n-dimensional, symmetric Gaussian peak function.
 * <p>
 * This fitting target function is defined over dimension <code>n</code>, by the
 * following <code>n+2</code> parameters:
 * 
 * <pre>
 * k = 0..n-1  - x₀ᵢ (with i = k)
 * k = n       - A
 * k = n+1 	   - b
 * k = n+2 C
 * </pre>
 * 
 * with
 * 
 * <pre>
 * f(x) = A × exp( - S ) + C
 * </pre>
 * 
 * and
 * 
 * <pre>
 * S = b × ∑ (xᵢ - x₀ᵢ)²
 * </pre>
 * 
 * @author jollion, from Jean-Yves Tinevez <jeanyves.tinevez@gmail.com> - 2013
 */
public class GaussianPlusConstant implements FitFunction {

	/*
	 * METHODS
	 */

	@Override
	public String toString() {
		return "Gaussian function A × exp( -b × ∑ (xᵢ - x₀ᵢ)² + C)";
	}

	@Override
	public final double val(final double[] x, final double[] a) {
		return a[a.length-3] * E(x, a) + a[a.length-1];
	}

	/**
	 * Partial derivatives indices are ordered as follow:
	 * <pre>k = 0..n-1  - x_i (with i = k)
	 *k = n       - A
	 *k = n+1     - b</pre> 
	 */
	@Override
	public final double grad(final double[] x, final double[] a, final int k) {
		final int ndims = x.length;
		if (k == a.length-3) {
			// With respect to A
			return E(x, a);

		} else if (k < ndims) {
			// With respect to xi
			int dim = k;
			return 2 * a[a.length-2] * (x[dim] - a[dim]) * a[a.length-3] * E(x, a);

		} else if (k== a.length-2 ){
			// With respect to b
			double d = 0, si;
			for (int i = 0; i < ndims; i++) {
				si = x[i] - a[i]; // (xᵢ - x₀ᵢ)
				d += si * si; 
			}
			return - d * a[a.length-3] * E(x, a);
		} else {// With respect to C
                    return 1;
                }
	}

	@Override
	public final double hessian(final double[] x, final double[] a, int rIn,
		int cIn)
	{
		int r = rIn;
		int c = cIn;
		if (c < r) {
			int tmp = c;
			c = r;
			r = tmp;
		} // Ensure c >= r, top right half the matrix

		final int ndims = x.length;

		if (c == r) {
			// diagonal

			if (c < ndims ) {
				// d²G / dxi²
				final int dim = c;
				final double di = x[dim] - a[dim];
				//         2 A b (2 b (C-x)^2-1) e^(-b ((C-x)^2+(D-y)^2))
				return 2 * a[a.length-3] * a[a.length-2] * ( 2 * a[a.length-2] * di * di - 1 ) * E(x, a);

			} else if (c == a.length-3) {
				// d²A / dxi²
				return 0;

			} else if (c==a.length-2){
				// d²G / db²
				return a[a.length-3] * E(x, a) * S(x, a) * S(x, a) / a[a.length-2] / a[a.length-2];
			} else {
                            // d²G / dC²
                            return 0;
                        }

		} else if ( c < ndims && r < ndims ) {
			// H1
			// d²G / (dxj dxi)
			final int i = c;
			final int j = r;
			final double di = x[i] - a[i];
			final double dj = x[j] - a[j];
			return 4 * a[a.length-3] * a[a.length-2] * a[a.length-2] * di * dj * E(x, a);

		} else if (c == a.length-3) {

			// d²G / (dA dxi)
			final int dim = r;
			return 2 * a[a.length-2] * (x[dim] - a[dim])  * E(x, a);

		} else if (c == a.length-2) { // c == a.length-2 -> b

			if (r == a.length-3) {
				// d²G / (dA db)
				// With respect to b
				return - S(x, a) * E(x, a) / a[a.length-2];

			} 
                        // H3
                        // d²G / (dxi db)
                        final int i = r; // xi
                        return 2 * a[a.length-3] * (x[i] - a[i]) * E(x, a) * (1 - S(x, a));
                        
		} else return 0; //d²G / (d? dC)

	}

	/*
	 * PRIVATE METHODS
	 */

	private static final double S(final double[] x, final double[] a) {
		double sum = 0;
		double di;
		for (int i = 0; i < x.length; i++) {
			di = x[i] - a[i];
			sum += di * di;
		}
		return 1/Math.pow(a[a.length-2], 2) * sum;
	}

	private static final double E(final double[] x, final double[] a) {
		return Math.exp(- S(x,a));
	}


}
