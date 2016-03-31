package ncbi.chemdner;

import java.util.Set;

import cc.mallet.pipe.Pipe;
import cc.mallet.types.Instance;
import cc.mallet.types.Token;
import cc.mallet.types.TokenSequence;
import cc.mallet.util.PropertyList;

public class FeatureSuppressor extends Pipe {

	private static final long serialVersionUID = 1L;
	private boolean isWhiteList;
	private Set<String> suppress;

	public FeatureSuppressor(boolean isWhiteList, Set<String> suppress) {
		this.isWhiteList = isWhiteList;
		this.suppress = suppress;
	}

	@Override
	public Instance pipe(Instance carrier) {
		TokenSequence ts = (TokenSequence) carrier.getData();
		for (int i = 0; i < ts.size(); i++) {
			Token t = ts.get(i);
			PropertyList newFeatures = null;
			PropertyList.Iterator iterator = t.getFeatures().iterator();
			while (iterator.hasNext()) {
				String key = iterator.getKey();
				if (suppress.contains(key) == isWhiteList) {
					double value = iterator.getNumericValue();
					newFeatures = PropertyList.add(key, value, newFeatures);
				}
				iterator.next();
			}
			t.setFeatures(newFeatures);
		}
		return carrier;
	}
}
