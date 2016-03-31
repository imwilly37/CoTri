package dnorm.types;

import java.io.File;


import cern.colt.matrix.DoubleMatrix2D;

public abstract class SynonymMatrix {

	public abstract void initalize();

	public abstract Dictionary getDictionary();

	public abstract double score(DoubleMatrix2D q, DoubleMatrix2D d);

	public abstract void trainingUpdate(DoubleMatrix2D q, DoubleMatrix2D dp, DoubleMatrix2D dn, double lambda);

	public abstract void write(File f);

}