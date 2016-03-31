package dnorm.core;

import org.apache.lucene.search.ScoreDoc;

public interface Lookup {

	public ScoreDoc[] lookup(String lookupText);

}
