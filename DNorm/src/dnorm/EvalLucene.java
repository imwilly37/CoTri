package dnorm;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.HierarchicalConfiguration;
import org.apache.commons.configuration.XMLConfiguration;
import org.apache.lucene.analysis.StopAnalyzer;
import org.apache.lucene.analysis.synonym.SynonymMap;
import org.apache.lucene.document.Document;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.util.CharsRef;

import banner.eval.BANNER;
import banner.eval.dataset.Dataset;
import banner.postprocessing.PostProcessor;
import banner.tagging.CRFTagger;
import banner.tagging.dictionary.DictionaryTagger;
import banner.tokenization.Tokenizer;
import banner.types.Mention;
import banner.types.Mention.MentionType;
import banner.types.Sentence;
import banner.types.Token;
import dnorm.core.DiseaseNameAnalyzer;
import dnorm.core.Lexicon;
import dnorm.core.MEDICLexiconLoader;
import dnorm.core.Lookup;
import dnorm.util.AbbreviationResolver;
import dragon.nlp.tool.Tagger;
import dragon.nlp.tool.lemmatiser.EngLemmatiser;
import dragon.util.EnvVariable;

public class EvalLucene {

	private DiseaseNameAnalyzer analyzer;
	private Lexicon lex;
	public MEDICLexiconLoader loader; // FIXME
	public AbbreviationResolver abbrev;
	private HierarchicalConfiguration config;
	private Dataset dataset;
	private CRFTagger tagger;

	public EvalLucene(DiseaseNameAnalyzer analyzer, String lexiconFilename, String abbreviationFilename) {
		this.analyzer = analyzer;
		lex = new Lexicon(analyzer);
		loader = new MEDICLexiconLoader();
		loader.loadLexicon(lex, lexiconFilename);
		abbrev = new AbbreviationResolver();
		abbrev.loadAbbreviations(abbreviationFilename);

	}

	public static DiseaseNameAnalyzer getDiseaseAnalyzer(boolean stopWords, boolean numericSynonyms, boolean nearSynonyms, boolean stem) {
		try {
			Set<String> stopwords = null;
			if (stopWords) {
				stopwords = new HashSet<String>();
				stopwords.addAll((Collection<? extends String>) StopAnalyzer.ENGLISH_STOP_WORDS_SET);
			}
			SynonymMap.Builder synonyms = new SynonymMap.Builder(true);
			if (numericSynonyms) {
				synonyms.add(new CharsRef("first"), new CharsRef("1"), false);
				synonyms.add(new CharsRef("second"), new CharsRef("2"), false);
				synonyms.add(new CharsRef("third"), new CharsRef("3"), false);
				synonyms.add(new CharsRef("fourth"), new CharsRef("4"), false);
				synonyms.add(new CharsRef("fifth"), new CharsRef("5"), false);
				synonyms.add(new CharsRef("sixth"), new CharsRef("6"), false);
				synonyms.add(new CharsRef("seventh"), new CharsRef("7"), false);
				synonyms.add(new CharsRef("eighth"), new CharsRef("8"), false);
				synonyms.add(new CharsRef("ninth"), new CharsRef("9"), false);
				synonyms.add(new CharsRef("I"), new CharsRef("1"), false);
				synonyms.add(new CharsRef("II"), new CharsRef("2"), false);
				synonyms.add(new CharsRef("III"), new CharsRef("3"), false);
				synonyms.add(new CharsRef("IV"), new CharsRef("4"), false);
				synonyms.add(new CharsRef("V"), new CharsRef("5"), false);
				synonyms.add(new CharsRef("VI"), new CharsRef("6"), false);
				synonyms.add(new CharsRef("VII"), new CharsRef("7"), false);
				synonyms.add(new CharsRef("VIII"), new CharsRef("8"), false);
				synonyms.add(new CharsRef("IX"), new CharsRef("9"), false);
			}
			if (nearSynonyms) {
				synonyms.add(new CharsRef("dominant"), new CharsRef("genetic"), false);
				synonyms.add(new CharsRef("recessive"), new CharsRef("genetic"), false);
				synonyms.add(new CharsRef("inherited"), new CharsRef("genetic"), false);
				synonyms.add(new CharsRef("hereditary"), new CharsRef("genetic"), false);
				synonyms.add(new CharsRef("disorder"), new CharsRef("disease"), false);
				synonyms.add(new CharsRef("abnormality"), new CharsRef("disease"), false);
				synonyms.add(new CharsRef("absence"), new CharsRef("deficiency"), false);
				synonyms.add(new CharsRef("handicap"), new CharsRef("deficiency"), false);
			}
			SynonymMap map = null;
			if (numericSynonyms || nearSynonyms) {
				map = synonyms.build();
			}
			return new DiseaseNameAnalyzer(stem, stopwords, map);
		} catch (IOException e) {
			// TODO Improve exception handling
			throw new RuntimeException(e);
		}
	}

	public Lexicon getLexicon() {
		return lex;
	}

	private Set<AbstractAnnotation> prepareCorrectAnnotations(Dataset dataset) {
		Set<AbstractAnnotation> correctAnnotations = new HashSet<AbstractAnnotation>();
		Map<String, String> alternateIDMap = loader.getAlternateIDMap();
		for (Sentence sentence : dataset.getSentences()) {
			String documentId = sentence.getDocumentId();
			for (Mention mention : sentence.getMentions()) {
				// TODO handle "+" IDs
				String[] conceptIds = mention.getConceptId().split("\\|");
				for (String conceptId : conceptIds) {
					if (alternateIDMap.containsKey(conceptId)) {
						conceptId = alternateIDMap.get(conceptId);
					}
					if (conceptId.indexOf(":") == -1)
						conceptId = "MESH:" + conceptId;
					correctAnnotations.add(new AbstractAnnotation(documentId, conceptId));
				}
			}
		}
		return correctAnnotations;
	}

	private static class AbstractAnnotation {
		private String documentId;
		private String conceptId;

		public AbstractAnnotation(String documentId, String conceptId) {
			this.documentId = documentId;
			this.conceptId = conceptId;
		}

		public String getDocumentId() {
			return documentId;
		}

		public String getConceptId() {
			return conceptId;
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + ((conceptId == null) ? 0 : conceptId.hashCode());
			result = prime * result + ((documentId == null) ? 0 : documentId.hashCode());
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
			AbstractAnnotation other = (AbstractAnnotation) obj;
			if (conceptId == null) {
				if (other.conceptId != null)
					return false;
			} else if (!conceptId.equals(other.conceptId))
				return false;
			if (documentId == null) {
				if (other.documentId != null)
					return false;
			} else if (!documentId.equals(other.documentId))
				return false;
			return true;
		}
	}

	public String getLookupText(Mention mention) {
		Sentence sentence2 = mention.getSentence();
		String documentId = sentence2.getDocumentId();
		List<Token> tokens = sentence2.getTokens();
		int start = mention.getStart();
		int end = mention.getEnd();

		// TODO TEST Maximize the match against nearby tokens
		// while (lexiconContains(sentence2, start, end, analyzer2) && lexiconContains(sentence2, start - 1, end, analyzer2)) {
		// int startChar1 = tokens.get(start).getStart(false);
		// int endChar1 = tokens.get(end - 1).getEnd(false);
		// String lookupText1 = sentence2.getText(startChar1, endChar1);
		// start--;
		// int startChar2 = tokens.get(start).getStart(false);
		// int endChar2 = tokens.get(end - 1).getEnd(false);
		// String lookupText2 = sentence2.getText(startChar2, endChar2);
		// System.out.println("Expanded left from \"" + lookupText1 + "\" to \"" + lookupText2 + "\"");
		// }
		// while (lexiconContains(sentence2, start, end, analyzer2) && lexiconContains(sentence2, start, end + 1, analyzer2)) {
		// int startChar1 = tokens.get(start).getStart(false);
		// int endChar1 = tokens.get(end - 1).getEnd(false);
		// String lookupText1 = sentence2.getText(startChar1, endChar1);
		// end++;
		// int startChar2 = tokens.get(start).getStart(false);
		// int endChar2 = tokens.get(end - 1).getEnd(false);
		// String lookupText2 = sentence2.getText(startChar2, endChar2);
		// System.out.println("Expanded right from \"" + lookupText1 + "\" to \"" + lookupText2 + "\"");
		// }

		// Resolve abbreviations
		int startChar = tokens.get(start).getStart(false);
		int endChar = tokens.get(end - 1).getEnd(false);
		String lookupText = sentence2.getText(startChar, endChar);
		lookupText = abbrev.expandAbbreviations(documentId, lookupText);
		return lookupText;
	}

	// private boolean lexiconContains(Sentence sentence, int start, int end, ConceptNameAnalyzer analyzer2) {
	// if (start < 0)
	// return false;
	// List<Token> tokens = sentence.getTokens();
	// if (end > tokens.size())
	// return false;
	// int startChar = tokens.get(start).getStart(false);
	// int endChar = tokens.get(end - 1).getEnd(false);
	// String expanded = sentence.getText(startChar, endChar);
	// return lex.contains(expanded, analyzer2);
	// }

	private List<Set<AbstractAnnotation>> getFoundAnnotations(Dataset dataset, banner.tagging.Tagger tagger, Lookup lookup, HierarchicalConfiguration config, int maxRank) throws IOException {
		Tokenizer tokenizer = BANNER.getTokenizer(config);
		PostProcessor postProcessor = BANNER.getPostProcessor(config);

		List<Set<AbstractAnnotation>> foundAnnotations = new ArrayList<Set<AbstractAnnotation>>();
		for (int i = 0; i < maxRank; i++)
			foundAnnotations.add(new HashSet<AbstractAnnotation>());

		for (Sentence sentence : dataset.getSentences()) {
			Sentence sentence2 = BANNER.process(tagger, tokenizer, postProcessor, sentence);
			String documentId = sentence2.getDocumentId();
			for (Mention mention : sentence2.getMentions(MentionType.Found)) {
				String lookupText = getLookupText(mention);

				// Do lookup & store results
				ScoreDoc[] hits = lookup.lookup(lookupText);
				for (int i = 0; i < Math.min(hits.length, maxRank); i++) {
					Document doc = lex.getDocument(hits[i].doc);
					String conceptId = doc.get("conceptId");
					foundAnnotations.get(i).add(new AbstractAnnotation(documentId, conceptId));
				}
			}
		}
		return foundAnnotations;
	}

	private void visualize(Lookup lookup, String lookupString) {
		System.out.println(lookupString);
		System.out.println(analyzer.getTokens(lookupString));
		System.out.println(lex.visualizeVector(lookupString));
		ScoreDoc[] hits = lookup.lookup(lookupString);
		for (int i = 0; i < hits.length; i++) {
			Document doc = lex.getDocument(hits[i].doc);
			System.out.printf("\t%f\t%s\t%s", hits[i].score, doc.get("conceptId"), lex.visualizeVector(doc.get("name")));
			System.out.println();
		}
		System.out.println();
	}

	private void prepareBANNER(String configurationFile) throws ConfigurationException, IOException {
		long start = System.currentTimeMillis();
		config = new XMLConfiguration(configurationFile);
		EnvVariable.setDragonHome(".");
		EnvVariable.setCharSet("US-ASCII");
		dataset = BANNER.getDataset(config);
		EngLemmatiser lemmatiser = BANNER.getLemmatiser(config);
		Tagger posTagger = BANNER.getPosTagger(config);
		HierarchicalConfiguration localConfig = config.configurationAt(BANNER.class.getPackage().getName());
		String modelFilename = localConfig.getString("modelFilename");
		System.out.println("Model: " + modelFilename);
		BANNER.logInput(dataset.getSentences(), config);
		tagger = CRFTagger.load(new File(modelFilename), lemmatiser, posTagger);
		System.out.println("Completed input: " + (System.currentTimeMillis() - start));
	}

	private String calculateMicroAveragedFMeasure(Set<AbstractAnnotation> correctAnnotations, Set<AbstractAnnotation> foundAnnotations) {
		Set<AbstractAnnotation> tpSet = new HashSet<AbstractAnnotation>(correctAnnotations);
		tpSet.retainAll(foundAnnotations);
		int tp = tpSet.size();
		int fp = foundAnnotations.size() - tp;
		int fn = correctAnnotations.size() - tp;
		double p = ((double) tp) / (tp + fp);
		double r = ((double) tp) / (tp + fn);
		double f = 2 * p * r / (p + r);
		return "Micro\tP\t" + p + "\tR\t" + r + "\tF\t" + f;
	}

	private String calculateMacroAveragedFMeasure(Set<AbstractAnnotation> correctAnnotations, Set<AbstractAnnotation> foundAnnotations) {
		Map<String, Set<AbstractAnnotation>> correctAnnotationsMap = new HashMap<String, Set<AbstractAnnotation>>();
		for (AbstractAnnotation a : correctAnnotations) {
			String documentId = a.getDocumentId();
			Set<AbstractAnnotation> annotations = correctAnnotationsMap.get(documentId);
			if (annotations == null) {
				annotations = new HashSet<AbstractAnnotation>();
				correctAnnotationsMap.put(documentId, annotations);
			}
			annotations.add(a);
		}

		Map<String, Set<AbstractAnnotation>> foundAnnotationsMap = new HashMap<String, Set<AbstractAnnotation>>();
		for (AbstractAnnotation a : foundAnnotations) {
			String documentId = a.getDocumentId();
			Set<AbstractAnnotation> annotations = foundAnnotationsMap.get(documentId);
			if (annotations == null) {
				annotations = new HashSet<AbstractAnnotation>();
				foundAnnotationsMap.put(documentId, annotations);
			}
			annotations.add(a);
		}

		Set<String> keys = new HashSet<String>();
		keys.addAll(correctAnnotationsMap.keySet());
		keys.addAll(foundAnnotationsMap.keySet());

		double pSum = 0.0;
		double rSum = 0.0;
		double fSum = 0.0;

		for (String documentId : keys) {
			Set<AbstractAnnotation> ca = correctAnnotationsMap.get(documentId);
			if (ca == null)
				ca = Collections.EMPTY_SET;
			Set<AbstractAnnotation> fa = foundAnnotationsMap.get(documentId);
			if (fa == null)
				fa = Collections.EMPTY_SET;
			Set<AbstractAnnotation> tpSet = new HashSet<AbstractAnnotation>(ca);
			tpSet.retainAll(fa);

			int tp = tpSet.size();
			int fp = fa.size() - tp;
			int fn = ca.size() - tp;
			double p = 1.0;
			if (tp + fp != 0)
				p = ((double) tp) / (tp + fp);
			double r = 1.0;
			if (tp + fn != 0)
				r = ((double) tp) / (tp + fn);
			double f = 0.0;
			if (p + r > 0)
				f = 2 * p * r / (p + r);

			pSum += p;
			rSum += r;
			fSum += f;
		}

		double p = pSum / keys.size();
		double r = rSum / keys.size();
		double f = fSum / keys.size();
		System.out.println("Macro-averaged f-measure used to be calculated as " + (2 * p * r / (p + r)));

		return "Macro\tP\t" + p + "\tR\t" + r + "\tF\t" + f;
	}

	// private String calculateRecallCeiling(Set<AbstractAnnotation> correctAnnotations, List<Set<AbstractAnnotation>> foundAnnotations) {
	// Set<AbstractAnnotation> ceilingAnnotations = new HashSet<AbstractAnnotation>();
	// for (int i = 0; i < foundAnnotations.size(); i++) {
	// ceilingAnnotations.addAll(foundAnnotations.get(i));
	// }
	// ceilingAnnotations.retainAll(correctAnnotations);
	// int tp = ceilingAnnotations.size();
	// int fn = correctAnnotations.size() - tp;
	// double r = ((double) tp) / (tp + fn);
	// return "Ceiling\tMax\t" + foundAnnotations.size() + "\tR\t" + r;
	// }

	private String calculateRecallCeiling(Set<AbstractAnnotation> correctAnnotations, List<Set<AbstractAnnotation>> foundAnnotations, int maxRank) {
		Set<AbstractAnnotation> ceilingAnnotations = new HashSet<AbstractAnnotation>();
		for (int i = 0; i < maxRank; i++) {
			ceilingAnnotations.addAll(foundAnnotations.get(i));
		}
		ceilingAnnotations.retainAll(correctAnnotations);
		int tp = ceilingAnnotations.size();
		int fn = correctAnnotations.size() - tp;
		double r = ((double) tp) / (tp + fn);
		return "Ceiling @ " + maxRank + "\tMax\t" + foundAnnotations.size() + "\tR\t" + r;
	}

	private void runCosineSimilarity(String configurationFile) throws ConfigurationException, IOException {
		int maxRank = 10;
		long start = System.currentTimeMillis();
		Lookup baseLookup = lex.getExactLookup(1000);
		Lookup lookup = new LimitToBest(baseLookup, maxRank);
		prepareBANNER(configurationFile);
		// HierarchicalConfiguration localConfig = config.configurationAt(BANNER.class.getPackage().getName());
		Set<AbstractAnnotation> correctAnnotations = prepareCorrectAnnotations(dataset);
		List<Set<AbstractAnnotation>> foundAnnotations = getFoundAnnotations(dataset, tagger, lookup, config, maxRank);
		System.out.println(calculateMacroAveragedFMeasure(correctAnnotations, foundAnnotations.get(0)));
		System.out.println(calculateMicroAveragedFMeasure(correctAnnotations, foundAnnotations.get(0)));
		for (int i = 1; i <= maxRank; i++)
			System.out.println(calculateRecallCeiling(correctAnnotations, foundAnnotations, i));
		System.out.println("Elapsed time: " + (System.currentTimeMillis() - start));
	}

	public class LimitToBest implements Lookup {

		private Lookup parent;
		private int limit;

		public LimitToBest(Lookup parent, int limit) {
			this.parent = parent;
			this.limit = limit;
		}

		@Override
		public ScoreDoc[] lookup(String lookupText) {
			ScoreDoc[] hits = parent.lookup(lookupText);
			int length = hits.length;
			if (limit < length)
				length = limit;
			return Arrays.copyOf(hits, length);
		}

	}

	/**
	 * @param args
	 * @throws ConfigurationException
	 * @throws IOException
	 */
	public static void main(String[] args) throws ConfigurationException, IOException {
		DiseaseNameAnalyzer analyzer = getDiseaseAnalyzer(true, true, false, true);
		EvalLucene dnorm = new EvalLucene(analyzer, "data/CTD_diseases.tsv", "data/abbreviations.tsv");
		dnorm.getLexicon().prepare();

		dnorm.runCosineSimilarity(args[0]);
	}
}
