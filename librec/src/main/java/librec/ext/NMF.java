// Copyright (C) 2014 Guibing Guo
//
// This file is part of LibRec.
//
// LibRec is free software: you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation, either version 3 of the License, or
// (at your option) any later version.
//
// LibRec is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License
// along with LibRec. If not, see <http://www.gnu.org/licenses/>.
//

package librec.ext;

import happy.coding.io.Strings;
import librec.data.DenseMatrix;
import librec.data.DenseVector;
import librec.data.MatrixEntry;
import librec.data.SparseMatrix;
import librec.data.SparseVector;
import librec.intf.IterativeRecommender;

/**
 * Daniel D. Lee and H. Sebastian Seung, <strong>Algorithms for Non-negative
 * Matrix Factorization</strong>, NIPS 2001.
 * 
 * @author guoguibing
 * 
 */
public class NMF extends IterativeRecommender {

	// V = W * H
	protected DenseMatrix W, H;
	protected SparseMatrix V;

	public NMF(SparseMatrix trainMatrix, SparseMatrix testMatrix, int fold) {
		super(trainMatrix, testMatrix, fold);

		// no need to update learning rate
		lRate = -1;
	}

	@Override
	protected void initModel() throws Exception {
		W = new DenseMatrix(numUsers, numFactors);
		H = new DenseMatrix(numFactors, numItems);

		W.init(0.01);
		H.init(0.01);

		V = trainMatrix;
	}

	@Override
	protected void buildModel() throws Exception {
		for (int iter = 1; iter <= numIters; iter++) {

			// update W by fixing H
			for (int u = 0; u < W.numRows(); u++) {
				SparseVector uv = V.row(u);

				if (uv.getCount() > 0) {
					SparseVector euv = new SparseVector(V.numColumns());

					for (int j : uv.getIndex())
						euv.set(j, predict(u, j));

					for (int f = 0; f < W.numColumns(); f++) {
						DenseVector fv = H.row(f, false);
						double real = fv.inner(uv);
						double estm = fv.inner(euv) + 1e-9;

						W.set(u, f, W.get(u, f) * (real / estm));
					}
				}
			}

			// update H by fixing W
			DenseMatrix trW = W.transpose();
			for (int j = 0; j < H.numColumns(); j++) {
				SparseVector jv = V.column(j);

				if (jv.getCount() > 0) {
					SparseVector ejv = new SparseVector(V.numRows());

					for (int u : jv.getIndex())
						ejv.set(u, predict(u, j));

					for (int f = 0; f < H.numRows(); f++) {
						DenseVector fv = trW.row(f, false);
						double real = fv.inner(jv);
						double estm = fv.inner(ejv) + 1e-9;

						H.set(f, j, H.get(f, j) * (real / estm));
					}
				}
			}

			// compute errors
			loss = 0;
			errs = 0;
			for (MatrixEntry me : V) {
				int u = me.row();
				int j = me.column();
				double ruj = me.get();

				if (ruj > 0) {
					double euj = predict(u, j) - ruj;

					errs += euj * euj;
					loss += euj * euj;
				}
			}

			errs *= 0.5;
			loss *= 0.5;

			if (isConverged(iter))
				break;
		}
	}

	/**
	 * Why buildingModel2 that follows the paper instructions does not work?
	 */
	protected void buildModel2() {
		for (int iter = 1; iter <= numIters; iter++) {

			// Step 1: update W by fixing H
			DenseMatrix trH = H.transpose();

			// V * trH
			DenseMatrix V_trH = DenseMatrix.mult(V, trH);

			// W * H * trH
			DenseMatrix W_H_trH = W.mult(H.mult(trH));

			// update: W_ij = W_ij * (V_trH)_ij / (W_H_trH)_ij
			for (int i = 0; i < W.numRows(); i++)
				for (int j = 0; j < W.numColumns(); j++) {
					double denorm = W_H_trH.get(i, j) + 1e-9;
					W.set(i, j, W.get(i, j) * (V_trH.get(i, j) / denorm));
				}

			// Step 2: update H by fixing W
			DenseMatrix trW = W.transpose();

			// trW * V
			DenseMatrix trW_V = trW.mult(V);

			// trW * W * H
			DenseMatrix trW_W_H = trW.mult(W).mult(H);

			// update: H_ij = H_ij * (trW_V)_ij / (trW_W_H)_ij
			for (int i = 0; i < H.numRows(); i++)
				for (int j = 0; j < H.numColumns(); j++) {
					double denorm = trW_W_H.get(i, j) + 1e-9;
					H.set(i, j, H.get(i, j) * (trW_V.get(i, j) / denorm));
				}

			// compute errors
			loss = 0;
			errs = 0;
			for (MatrixEntry me : V) {
				int u = me.row();
				int j = me.column();
				double ruj = me.get();

				if (ruj > 0) {
					double pred = predict(u, j);
					double euj = pred - ruj;

					errs += euj * euj;
					loss += euj * euj;
				}
			}

			errs *= 0.5;
			loss *= 0.5;

			if (isConverged(iter))
				break;
		}
	}

	@Override
	protected double predict(int u, int j) {
		return DenseMatrix.product(W, u, H, j);
	}

	@Override
	public String toString() {
		return Strings.toString(new Object[] { numFactors, numIters }, ",");
	}
}
