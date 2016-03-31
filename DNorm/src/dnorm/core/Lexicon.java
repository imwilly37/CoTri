package dnorm.core;

import java.io.IOException;
import java.io.StringReader;
import java.util.Collections;
import java.util.Formatter;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.TermDocs;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.FuzzyQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.store.RAMDirectory;
import org.apache.lucene.util.Version;

import cern.colt.function.IntIntDoubleFunction;
import cern.colt.matrix.DoubleFactory2D;
import cern.colt.matrix.DoubleMatrix2D;
import dnorm.types.Dictionary;
import dnorm.types.Vector;


public class Lexicon {

	// TODO Refactor - this code now supports two different purposes: Lucene searching & exhaustive searching

	private Normalizer norm;
	private Dictionary dict;
	private DiseaseNameAnalyzer analyzer;
	private IndexSearcher searcher;
	private IndexWriter writer;
	private IndexReader reader;
	private RAMDirectory directory;
	private double[] tokenWeights;

	private Map<AnalyzedName, Set<String>> analyzedNamesToConceptIds;
	public Map<Vector, Set<String>> vectorsToConceptIds; // TODO Decide how to share this
	public Map<String, Set<Vector>> conceptIdToVectors; // TODO Decide how to share this
	public Set<String> conceptIds; // TODO Decide how to share this

	public Lexicon(DiseaseNameAnalyzer analyzer) {
		this(analyzer, new Dictionary());
	}

	public Lexicon(DiseaseNameAnalyzer analyzer, Dictionary dict) {
		norm = new Normalizer();
		this.dict = dict;
		directory = new RAMDirectory();
		this.analyzer = analyzer;
		System.out.println("Creating index");
		try {
			IndexWriterConfig config = new IndexWriterConfig(Version.LUCENE_36, analyzer);
			writer = new IndexWriter(directory, config);
		} catch (IOException e) {
			// TODO Improve exception handling
			throw new RuntimeException(e);
		}
		analyzedNamesToConceptIds = new HashMap<AnalyzedName, Set<String>>();
		conceptIds = new HashSet<String>();
	}

	public void addConcept(String conceptId, Set<String> names) {
		for (String name : names) {
			addConcept(conceptId, name);
		}
	}

	public void addConcept(String conceptId, String name) {
		addConcept(conceptId, name, false);
	}

	public void addConcept(String conceptId, String name, boolean isPreferred) {
		if (writer == null)
			throw new IllegalStateException("Cannot add concepts after calling prepare()");
		try {
			conceptIds.add(conceptId);
			AnalyzedName analyzedName = new AnalyzedName(analyzer.getTokens(name), isPreferred);
			Set<String> conceptIds = analyzedNamesToConceptIds.get(analyzedName);
			if (conceptIds == null) {
				conceptIds = new HashSet<String>();
				analyzedNamesToConceptIds.put(analyzedName, conceptIds);
			}
			// Name treated as a bag of words & only added once
			if (conceptIds.contains(conceptId))
				return;
			conceptIds.add(conceptId);
			for (String token : analyzedName.getName()) {
				if (dict.isFrozen()) {
					if (dict.getIndex(token) < 0) {
						System.out.println("dict.size()=" + dict.size());
						System.out.println("token=" + token);
						System.out.println("dict.getIndex(token)=" + dict.getIndex(token));
						System.out.println("ERROR: Dictionary does not contain token \"" + token + "\"");
						// throw new RuntimeException("Dictionary does not contain token \"" + token + "\"");
					}
				} else {
					dict.addToken(token);
				}
			}

			Document doc = new Document();
			doc.add(new Field("conceptId", conceptId, Field.Store.YES, Field.Index.NOT_ANALYZED));
			doc.add(new Field("name", name, Field.Store.YES, Field.Index.ANALYZED));
			writer.addDocument(doc);

		} catch (IOException e) {
			// TODO Improve exception handling
			throw new RuntimeException(e);
		}
	}

	private static class AnalyzedName {
		private List<String> name;
		private boolean isPreferred;

		public AnalyzedName(List<String> name, boolean isPreferred) {
			this.name = name;
			Collections.sort(this.name);
			this.isPreferred = isPreferred;
		}

		public List<String> getName() {
			return name;
		}

		public boolean isPreferred() {
			return isPreferred;
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + (isPreferred ? 1231 : 1237);
			result = prime * result + ((name == null) ? 0 : name.hashCode());
			return result;
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (getClass() != obj.getClass())
				return false;
			AnalyzedName other = (AnalyzedName) obj;
			if (isPreferred != other.isPreferred)
				return false;
			if (name == null) {
				if (other.name != null)
					return false;
			} else if (!name.equals(other.name))
				return false;
			return true;
		}
	}

	public void addName(String name) {
		if (writer == null)
			throw new IllegalStateException("Cannot add names after calling prepare()");
		List<String> tokens = analyzer.getTokens(name);
		for (String token : tokens) {
			if (dict.isFrozen()) {
				if (dict.getIndex(token) < 0) {
					throw new RuntimeException("Dictionary does not contain token \"" + token + "\"");
				}
			} else {
				dict.addToken(token);
			}
		}


	}

	public void prepare() {
		try {
			if (writer != null) {
				writer.close();
				writer = null;
				reader = IndexReader.open(directory);
				searcher = new IndexSearcher(reader);
				dict.freeze();
				System.out.println("Lucene done");
				tokenWeights = new double[dict.size()];
				for (int i = 0; i < tokenWeights.length; i++) {
					String token = dict.getToken(i);
					tokenWeights[i] = getTokenWeightInternal(token);
				}
				System.out.println("Token weights calculated");
				vectorsToConceptIds = new HashMap<Vector, Set<String>>();
				conceptIdToVectors = new HashMap<String, Set<Vector>>();

				// long t1 = 0;
				// long t2 = 0;
				// long t3 = 0;
				// long t4 = 0;
				// int count = 0;

				int count = 0;
				for (AnalyzedName analyzedName : analyzedNamesToConceptIds.keySet()) {
					List<String> name = analyzedName.getName();
					// t1 -= System.currentTimeMillis();
					Set<String> conceptIds = analyzedNamesToConceptIds.get(analyzedName);
					// t1 += System.currentTimeMillis();
					// t2 -= System.currentTimeMillis();
					DoubleMatrix2D matrix = convertNameToVector(name);
					// t2 += System.currentTimeMillis();
					// t3 -= System.currentTimeMillis();
					Vector vector = new Vector(matrix, analyzedName.isPreferred());
					// t3 += System.currentTimeMillis();
					// t4 -= System.currentTimeMillis();
					vectorsToConceptIds.put(vector, conceptIds);
					// t4 += System.currentTimeMillis();

					// count++;
					// if (count % 1000 == 0) {
					// System.out.println("count: " + count);
					// System.out.println("t1: " + t1);
					// System.out.println("t2: " + t2);
					// System.out.println("t3: " + t3);
					// System.out.println("t4: " + t4);
					// }
					for (String conceptId : conceptIds) {
						Set<Vector> vectors = conceptIdToVectors.get(conceptId);
						if (vectors == null) {
							vectors = new HashSet<Vector>();
							conceptIdToVectors.put(conceptId, vectors);
						}
						vectors.add(vector);
					}
					count++;
					if (count % 10000 == 0) {
						System.out.println(count);
					}
				}

				analyzedNamesToConceptIds = null;
			}
		} catch (IOException e) {
			// TODO Improve exception handling
			throw new RuntimeException(e);
		}
	}

	private double getTokenWeightInternal(String token) throws IOException {
		int frequency = 1; // Laplace smoothing
		TermDocs termDocs = reader.termDocs(new Term("name", token));
		while (termDocs.next()) {
			if (termDocs.freq() > 0)
				frequency++;
		}
		double weight = reader.numDocs();
		weight = weight / frequency;
		weight = Math.log(weight);
		assert !Double.isInfinite(weight);
		assert !Double.isNaN(weight);
		return weight;
	}

	public Dictionary getDictionary() {
		return dict;
	}

	// TODO Refactor code to eliminate this call
	public DiseaseNameAnalyzer getAnalyzer() {
		return analyzer;
	}

	public double getTokenWeight(String token) {
		int index = dict.getIndex(token);
		if (index < 0)
			return 0.0;
		return tokenWeights[index];
	}

	// TODO Refactor code to eliminate this call
	public DoubleMatrix2D convertNameToVector(List<String> tokens) {
		final DoubleMatrix2D vector = DoubleFactory2D.sparse.make(dict.size(), 1);
		for (String token : tokens) {
			int index = dict.getIndex(token);
			// Ignore unindexed words
			if (index >= 0) {
				double previous = vector.get(index, 0);
				double value = getTokenWeight(token);
				vector.set(index, 0, previous + value);
			}
		}

		// Normalize to unit length
		norm.normalize(vector);
		return vector;
	}

	public DoubleMatrix2D convertNameToVector(String name) {
		try {
			final DoubleMatrix2D vector = DoubleFactory2D.sparse.make(dict.size(), 1);

			// Create initial vector
			TokenStream tokenStream = analyzer.tokenStream("name", new StringReader(name));
			while (tokenStream.incrementToken()) {
				CharTermAttribute term = tokenStream.getAttribute(CharTermAttribute.class);
				String token = term.toString();
				int index = dict.getIndex(token);
				// Ignore unindexed words
				if (index >= 0) {
					double previous = vector.get(index, 0);
					double value = getTokenWeight(token);
					vector.set(index, 0, previous + value);
				}
			}

			norm.normalize(vector);
			return vector;
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	private static class SparseLength implements IntIntDoubleFunction {
		double sqsum;

		public SparseLength() {
			// empty
		}

		public double length(DoubleMatrix2D vector) {
			sqsum = 0.0;
			vector.forEachNonZero(this);
			return Math.sqrt(sqsum);
		}

		@Override
		public double apply(int r, int c, double v) {
			sqsum += v * v;
			return v;
		}
	}

	private static class Normalizer implements IntIntDoubleFunction {

		private SparseLength len;
		private double length;

		public Normalizer() {
			len = new SparseLength();
		}

		public void normalize(DoubleMatrix2D vector) {
			length = len.length(vector);
			vector.forEachNonZero(this);
		}

		@Override
		public double apply(int r, int c, double v) {
			return v / length;
		}
	}

	public boolean contains(String lookupText, DiseaseNameAnalyzer analyzer2) {
		Lookup contains = getExactLookup(1);
		ScoreDoc[] hits = contains.lookup(lookupText);
		if (hits.length < 1)
			return false;
		Document doc = getDocument(hits[0].doc);
		List<String> queryTerms = analyzer2.getTokens(lookupText);
		String name = doc.get("name");
		List<String> docTerms = analyzer2.getTokens(name);
		boolean retval = queryTerms.equals(docTerms);
		if (retval)
			System.out.println("lexicon returned \"" + name + "\" (" + docTerms + ") as best match for \"" + lookupText + "\" (" + queryTerms + ")");
		return retval;
	}

	public String visualizeVector(String name) {
		DoubleMatrix2D vector = convertNameToVector(name);
		return visualizeVector(vector);
	}

	public String visualizeVector(DoubleMatrix2D vector) {
		StringBuilder str = new StringBuilder();
		final Formatter f = new Formatter(str, Locale.US);
		vector.forEachNonZero(new IntIntDoubleFunction() {
			@Override
			public double apply(int row, int col, double val) {
				assert col == 0;
				f.format("%s:%f\t", dict.getToken(row), val);
				return val;
			}
		});
		// Formatter f = new Formatter(str, Locale.US);
		// for (int i = 0; i < vector.size(); i++) {
		// double value = vector.get(i, 0);
		// if (value != 0.0) {
		// f.format("%s:%f\t", dict.getToken(i), value);
		// }
		// }
		f.close();
		return str.toString().trim();
	}

	public Document getDocument(int docID) {
		try {
			return searcher.doc(docID);
		} catch (IOException e) {
			// TODO Improve exception handling
			throw new RuntimeException(e);
		}
	}

	// TODO Refactor code to eliminate this call
	public Set<String> getNamesForConcept(String conceptId) {
		try {
			TermQuery query = new TermQuery(new Term("conceptId", conceptId));
			TopDocs topDocs = searcher.search(query, 1000); // TODO Get all hits
			Set<String> names = new HashSet<String>();
			ScoreDoc[] hits = topDocs.scoreDocs;
			for (int i = 0; i < hits.length; i++) {
				Document doc = searcher.doc(hits[i].doc);
				names.add(doc.get("name"));
			}
			return names;
		} catch (IOException e) {
			// TODO Improve exception handling
			throw new RuntimeException(e);
		}
	}

	public Set<DoubleMatrix2D> getNameVectorsForConcept(String conceptId) {
		try {
			TermQuery query = new TermQuery(new Term("conceptId", conceptId));
			TopDocs topDocs = searcher.search(query, 1000); // TODO Get all hits
			Set<DoubleMatrix2D> vectors = new HashSet<DoubleMatrix2D>();
			ScoreDoc[] hits = topDocs.scoreDocs;
			for (int i = 0; i < hits.length; i++) {
				Document doc = searcher.doc(hits[i].doc);
				vectors.add(convertNameToVector(doc.get("name")));
			}
			return vectors;
		} catch (IOException e) {
			// TODO Improve exception handling
			throw new RuntimeException(e);
		}
	}

	public Lookup getExactLookup(int maxMatches) {
		if (searcher == null)
			throw new IllegalStateException("Have to call prepare() first");
		return new ExactLookup(maxMatches);
	}

	public Lookup getFuzzyLookup(int maxMatches, float minimumSimilarity, int prefixLength, int maxExpansions) {
		if (searcher == null)
			throw new IllegalStateException("Have to call prepare() first");
		return new FuzzyLookup(maxMatches, minimumSimilarity, prefixLength, maxExpansions);
	}

	private class ExactLookup implements Lookup {

		private int maxMatches;

		public ExactLookup(int maxMatches) {
			this.maxMatches = maxMatches;
		}

		@Override
		public ScoreDoc[] lookup(String lookupText) {
			try {
				BooleanQuery query = new BooleanQuery();
				TokenStream tokenStream = analyzer.tokenStream("name", new StringReader(lookupText));
				while (tokenStream.incrementToken()) {
					CharTermAttribute term = tokenStream.getAttribute(CharTermAttribute.class);
					query.add(new TermQuery(new Term("name", term.toString())), Occur.SHOULD);
				}
				TopDocs topDocs = searcher.search(query, maxMatches);
				ScoreDoc[] hits = topDocs.scoreDocs;
				return hits;
			} catch (IOException e) {
				// TODO Improve exception handling
				throw new RuntimeException(e);
			}
		}
	}

	private class FuzzyLookup implements Lookup {

		private int maxMatches;
		private float minimumSimilarity;
		private int prefixLength;
		private int maxExpansions;

		public FuzzyLookup(int maxMatches, float minimumSimilarity, int prefixLength, int maxExpansions) {
			this.maxMatches = maxMatches;
			this.minimumSimilarity = minimumSimilarity;
			this.prefixLength = prefixLength;
			this.maxExpansions = maxExpansions;
		}

		@Override
		public ScoreDoc[] lookup(String lookupText) {
			try {
				BooleanQuery query = new BooleanQuery();
				TokenStream tokenStream = analyzer.tokenStream("name", new StringReader(lookupText));
				while (tokenStream.incrementToken()) {
					CharTermAttribute term = tokenStream.getAttribute(CharTermAttribute.class);
					query.add(new FuzzyQuery(new Term("name", term.toString()), minimumSimilarity, prefixLength, maxExpansions), Occur.SHOULD);
				}
				TopDocs topDocs = searcher.search(query, maxMatches);
				ScoreDoc[] hits = topDocs.scoreDocs;
				return hits;
			} catch (IOException e) {
				// TODO Improve exception handling
				throw new RuntimeException(e);
			}
		}
	}
}
