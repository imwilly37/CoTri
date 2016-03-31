package dnorm.types;

import cern.colt.matrix.DoubleMatrix2D;

public class Vector {

	private DoubleMatrix2D vector;
	private int hashCode;
	private boolean isPreferred;

	public Vector(DoubleMatrix2D vector, boolean isPreferred) {
		this.vector = vector;
		this.isPreferred = isPreferred;
		hashCodeInternal2();
	}

	public DoubleMatrix2D getVector() {
		return vector;
	}

	public boolean isPreferred() {
		return isPreferred;
	}

	private void hashCodeInternal2() {
		StringBuilder h = new StringBuilder();
		for (int j = 0; j < vector.columns(); j++) {
			for (int i = 0; i < vector.rows(); i++) {
				double v = vector.get(i, j);
				if (v != 0.0) {
					h.append("(" + i + "," + j + ")" + v);
				}
			}
		}
		hashCode = 31 * h.toString().hashCode() + (isPreferred ? 1231 : 1237);
	}

	@Override
	public int hashCode() {
		return hashCode;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		Vector other = (Vector) obj;
		if (hashCode != other.hashCode)
			return false;
		if (isPreferred != other.isPreferred)
			return false;
		if (vector == null) {
			if (other.vector != null)
				return false;
		} else if (!vector.equals(other.vector))
			return false;
		return true;
	}
}
