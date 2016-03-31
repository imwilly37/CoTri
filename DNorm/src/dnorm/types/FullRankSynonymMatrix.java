package dnorm.types;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import cern.colt.function.IntIntDoubleFunction;
import cern.colt.matrix.DoubleFactory2D;
import cern.colt.matrix.DoubleMatrix2D;
import cern.jet.math.Functions;

public class FullRankSynonymMatrix extends SynonymMatrix {

	public DoubleMatrix2D w;
	private Dictionary dict;
	private int size;
	private Scorer scorer;
	private FastTrainingUpdate ftu;

	public FullRankSynonymMatrix(Dictionary dict) {
		this.dict = dict;
		this.size = Integer.MIN_VALUE;
		this.scorer = new Scorer();
		this.ftu = new FastTrainingUpdate();
	}

	public FullRankSynonymMatrix(Dictionary dict, DoubleMatrix2D w) {
		this.dict = dict;
		size = dict.size();
		if (w.columns() != w.rows())
			throw new IllegalArgumentException("Matrix is not symmetric");
		if (w.columns() != size)
			throw new IllegalArgumentException("Matrix and dictionary sizes do not match");
		this.w = w;
		this.scorer = new Scorer();
		this.ftu = new FastTrainingUpdate();
	}

	public void initalize() {
		if (!dict.isFrozen())
			throw new IllegalStateException("Dictionary must be frozen");
		size = dict.size();
		System.out.println("w[" + size + "," + size + "]");
		w = DoubleFactory2D.dense.make(size, size);
		for (int i = 0; i < size; i++)
			w.set(i, i, 1.0);
	}

	public Dictionary getDictionary() {
		return dict;
	}

	static double getLength(DoubleMatrix2D v) {
		return Math.sqrt(v.aggregate(Functions.plus, Functions.square));
	}

	double scoreSLOW(DoubleMatrix2D q, DoubleMatrix2D d) {
		DoubleMatrix2D t = DoubleFactory2D.dense.make(1, size);
		q.zMult(w, t, 1, 0, true, false);
		DoubleMatrix2D s = DoubleFactory2D.dense.make(1, 1);
		t.zMult(d, s, 1, 0, false, false);
		return s.get(0, 0);
	}

	public double score(DoubleMatrix2D q, DoubleMatrix2D d) {
		return scorer.getScore(q, d);
	}

	private class Scorer implements IntIntDoubleFunction {
		DMultiplier d;

		public Scorer() {
			d = new DMultiplier();
		}

		public double apply(int row, int ignored1, double qValue) {
			d.set(qValue, row);
			return qValue;
		}

		public double getScore(DoubleMatrix2D q, DoubleMatrix2D d) {
			this.d.init(d);
			q.forEachNonZero(this);
			return this.d.getScore();
		}

	}

	private class DMultiplier implements IntIntDoubleFunction {
		private DoubleMatrix2D d;
		private double qValue;
		private int row;
		private double score;

		public void init(DoubleMatrix2D d) {
			this.d = d;
			score = 0.0;
		}

		public void set(double qValue, int row) {
			this.qValue = qValue;
			this.row = row;
			d.forEachNonZero(this);
		}

		@Override
		public double apply(int column, int ignored2, double dValue) {
			score += qValue * dValue * w.get(row, column);
			return dValue;
		}

		public double getScore() {
			return score;
		}
	}

	@Override
	public void trainingUpdate(DoubleMatrix2D q, DoubleMatrix2D dp, DoubleMatrix2D dn, double lambda) {
		// FIXME Why is SLOW training more accurate?
		// trainingUpdateFAST(q, dp, dn, lambda);
		trainingUpdateSLOW(q, dp, dn, lambda);
	}

	public void trainingUpdateSLOW(DoubleMatrix2D q, DoubleMatrix2D dp, DoubleMatrix2D dn, double lambda) {
		q.zMult(dp, w, lambda, 1.0, false, true);
		q.zMult(dn, w, -lambda, 1.0, false, true);
	}

	public void trainingUpdateFAST(DoubleMatrix2D q, DoubleMatrix2D dp, DoubleMatrix2D dn, double lambda) {
		ftu.update(w, q, dp, lambda);
		ftu.update(w, q, dn, -lambda);
	}

	private class FastTrainingUpdate implements IntIntDoubleFunction {
		private InnerTrainingUpdate inner;
		private double value;

		public FastTrainingUpdate() {
			inner = new InnerTrainingUpdate();
		}

		public void update(DoubleMatrix2D matrix, DoubleMatrix2D vector1, DoubleMatrix2D vector2, double value) {
			this.value = value;
			inner.init(matrix, vector2);
			vector1.forEachNonZero(this);
		}

		@Override
		public double apply(int v1i, int ignore, double v1v) {
			inner.update(v1i, value * v1v);
			return v1v;
		}
	}

	private class InnerTrainingUpdate implements IntIntDoubleFunction {
		private DoubleMatrix2D inputMatrix;
		private DoubleMatrix2D vector2;
		private int v1i;
		private double v1v;

		public InnerTrainingUpdate() {
			inputMatrix = null;
		}

		public void init(DoubleMatrix2D inputMatrix, DoubleMatrix2D vector2) {
			this.inputMatrix = inputMatrix;
			this.vector2 = vector2;
		}

		public void update(int v1i, double v1v) {
			this.v1i = v1i;
			this.v1v = v1v;
			vector2.forEachNonZero(this);
		}

		@Override
		public double apply(int v2i, int ignore, double v2v) {
			int inputMatrixRow = v1i;
			int inputMatrixColumn = v2i;
			double v = v1v * v2v + inputMatrix.get(inputMatrixRow, inputMatrixColumn);
			inputMatrix.set(inputMatrixRow, inputMatrixColumn, v);
			return v2v;
		}
	}

	public void output() {
		for (int i = 0; i < size; i++) {
			for (int j = i + 1; j < size; j++) {
				double v1 = w.get(i, j);
				double v2 = w.get(j, i);
				if (v1 != 0.0 || v2 != 0.0)
					System.out.println(v1 + "\t" + v2);
			}
		}
	}

	public int size() {
		assert w.rows() == w.columns();
		return w.rows();
	}

	public static FullRankSynonymMatrix load(File f) {
		try {
			ObjectInputStream ois = new ObjectInputStream(new GZIPInputStream(new FileInputStream(f)));
			Dictionary dict = (Dictionary) ois.readObject();
			DoubleMatrix2D w = (DoubleMatrix2D) ois.readObject();
			ois.close();
			return new FullRankSynonymMatrix(dict, w);
		} catch (ClassNotFoundException e) {
			throw new RuntimeException(e);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	public void write(File f) {
		try {
			ObjectOutputStream oos = new ObjectOutputStream(new GZIPOutputStream(new FileOutputStream(f)));
			oos.writeObject(dict);
			oos.writeObject(w);
			oos.close();
		} catch (IOException e) {
			System.err.println("Exception writing file " + f + ": " + e);
		}
	}
}
