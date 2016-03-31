package dnorm;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.HierarchicalConfiguration;
import org.apache.commons.configuration.XMLConfiguration;

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
import banner.util.RankedList;
import dnorm.core.DiseaseNameAnalyzer;
import dnorm.core.Lexicon;
import dnorm.core.MEDICLexiconLoader;
import dnorm.core.SynonymTrainer;
import dnorm.core.SynonymTrainer.LookupResult;
import dnorm.types.FullRankSynonymMatrix;
import dnorm.types.SynonymMatrix;
import dnorm.util.AbbreviationResolver;
import dragon.nlp.tool.Tagger;
import dragon.nlp.tool.lemmatiser.EngLemmatiser;
import dragon.util.EnvVariable;

public class EvalCosineSim {

	private Lexicon lex;
	private MEDICLexiconLoader loader;
	public AbbreviationResolver abbrev;
	private HierarchicalConfiguration config;
	private Dataset dataset;
	private CRFTagger tagger;

	public EvalCosineSim(DiseaseNameAnalyzer analyzer, String lexiconFilename, String abbreviationFilename) {
		lex = new Lexicon(analyzer);
		loader = new MEDICLexiconLoader(true);
		loader.loadLexicon(lex, lexiconFilename);
		abbrev = new AbbreviationResolver();
		abbrev.loadAbbreviations(abbreviationFilename);
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

	private List<Set<AbstractAnnotation>> getFoundAnnotations(Dataset dataset, banner.tagging.Tagger tagger, SynonymTrainer syn, HierarchicalConfiguration config, int maxRank) throws IOException {
		Tokenizer tokenizer = BANNER.getTokenizer(config);
		PostProcessor postProcessor = BANNER.getPostProcessor(config);

		List<Set<AbstractAnnotation>> foundAnnotations = new ArrayList<Set<AbstractAnnotation>>();
		for (int i = 0; i < maxRank; i++)
			foundAnnotations.add(new HashSet<AbstractAnnotation>());

		int numMentions = 0;
		long elapsed = 0;

		for (Sentence sentence : dataset.getSentences()) {
			Sentence sentence2 = BANNER.process(tagger, tokenizer, postProcessor, sentence);
			String documentId = sentence2.getDocumentId();
			for (Mention mention : sentence2.getMentions(MentionType.Found)) {
				String lookupText = getLookupText(mention);

				// Do lookup & store results
				numMentions++;
				elapsed -= System.currentTimeMillis();
				RankedList<LookupResult> results = syn.lookup(lookupText);
				elapsed += System.currentTimeMillis();
				for (int i = 0; i < Math.min(results.size(), maxRank); i++) {
					String conceptId = results.getObject(i).getConceptId();
					foundAnnotations.get(i).add(new AbstractAnnotation(documentId, conceptId));
				}
			}
		}
		System.out.println("Number of mentions: " + numMentions);
		System.out.println("Elapsed lookup time: " + elapsed);

		return foundAnnotations;
	}

	private String getLookupText(Mention mention) {
		Sentence sentence2 = mention.getSentence();
		String documentId = sentence2.getDocumentId();
		List<Token> tokens = sentence2.getTokens();
		int start = mention.getStart();
		int end = mention.getEnd();

		// Resolve abbreviations
		int startChar = tokens.get(start).getStart(false);
		int endChar = tokens.get(end - 1).getEnd(false);
		String lookupText = sentence2.getText(startChar, endChar);
		lookupText = abbrev.expandAbbreviations(documentId, lookupText);
		return lookupText;
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

	private void outputAnalysis(Dataset dataset, banner.tagging.Tagger tagger, SynonymTrainer syn, HierarchicalConfiguration config, int maxRank, String outputFilename,
			Set<AbstractAnnotation> correctAnnotations, Set<AbstractAnnotation> foundAnnotations) throws IOException {
		Tokenizer tokenizer = BANNER.getTokenizer(config);
		PostProcessor postProcessor = BANNER.getPostProcessor(config);
		BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(outputFilename), "UTF8"));

		List<Sentence> sentences = new ArrayList<Sentence>(dataset.getSentences());
		Collections.sort(sentences, new Comparator<Sentence>() {
			@Override
			public int compare(Sentence s1, Sentence s2) {
				return s1.getSentenceId().compareTo(s2.getSentenceId());
			}
		});
		Set<AbstractAnnotation> falseNegatives = new HashSet<AbstractAnnotation>(correctAnnotations);
		falseNegatives.removeAll(foundAnnotations);

		String currentDocument = null;
		for (Sentence sentence : sentences) {

			if (!sentence.getDocumentId().equals(currentDocument)) {
				currentDocument = sentence.getDocumentId();
				writer.newLine();
				writer.newLine();
				writer.write(currentDocument);
				writer.newLine();

				for (AbstractAnnotation a : falseNegatives) {
					if (a.getDocumentId().equals(currentDocument)) {
						writer.write("\t" + a.getConceptId());
						Set<String> names = lex.getNamesForConcept(a.getConceptId());
						writer.write("\t" + names);
						writer.newLine();
					}
				}

			}
			writer.write(sentence.getDocumentId() + "\t" + sentence.getSentenceId() + "\t" + sentence.getText());
			writer.newLine();
			Sentence sentence2 = BANNER.process(tagger, tokenizer, postProcessor, sentence);
			String documentId = sentence2.getDocumentId();
			for (Mention mention : sentence2.getMentions(MentionType.Found)) {
				String lookupText = getLookupText(mention);

				StringBuilder mentionDisplay = new StringBuilder();
				mentionDisplay.append("\t" + mention.getText() + "\n");
				mentionDisplay.append("\t" + lookupText + "\n");
				mentionDisplay.append("\t" + lex.getAnalyzer().getTokens(lookupText) + "\n");

				boolean display = true;
				// Do lookup & store results
				RankedList<LookupResult> results = syn.lookup(lookupText);
				for (int i = 0; i < Math.min(results.size(), maxRank); i++) {
					String conceptId = results.getObject(i).getConceptId();
					AbstractAnnotation annotation = new AbstractAnnotation(documentId, conceptId);
					String marker = "";
					if (correctAnnotations.contains(annotation)) {
						marker = "*";
						if (i == 0)
							display = false;
					}
					String output = String.format("\t%s\t%d\t%f", marker, i, results.getValue(i));
					output += String.format("\t%s\t%s", results.getObject(i).getConceptId(), lex.visualizeVector(results.getObject(i).getVector()));
					mentionDisplay.append(output + "\n");
				}

				if (display) {
					writer.write(mentionDisplay.toString());
				}
			}
		}
		writer.close();
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

			pSum += p;
			rSum += r;
		}

		double p = pSum / keys.size();
		double r = rSum / keys.size();
		double f = 2 * p * r / (p + r);

		return "Macro\tP\t" + p + "\tR\t" + r + "\tF\t" + f;
	}

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

	private void runSynonymSimilarity(String configurationFile, String outputFileName, SynonymMatrix matrix) throws ConfigurationException, IOException {
		int maxRank = 10;
		long start = System.currentTimeMillis();
		SynonymTrainer syn = new SynonymTrainer(lex, matrix, 1000);
		prepareBANNER(configurationFile);
		// HierarchicalConfiguration localConfig = config.configurationAt(BANNER.class.getPackage().getName());
		Set<AbstractAnnotation> correctAnnotations = prepareCorrectAnnotations(dataset);
		List<Set<AbstractAnnotation>> foundAnnotations = getFoundAnnotations(dataset, tagger, syn, config, maxRank);
		outputAnalysis(dataset, tagger, syn, config, maxRank, outputFileName, correctAnnotations, foundAnnotations.get(0));
		System.out.println(calculateMacroAveragedFMeasure(correctAnnotations, foundAnnotations.get(0)));
		System.out.println(calculateMicroAveragedFMeasure(correctAnnotations, foundAnnotations.get(0)));
		for (int i = 1; i <= maxRank; i++)
			System.out.println(calculateRecallCeiling(correctAnnotations, foundAnnotations, i));
		System.out.println("Elapsed time: " + (System.currentTimeMillis() - start));
	}

	/**
	 * @param args
	 * @throws ConfigurationException
	 * @throws IOException
	 */
	public static void main(String[] args) throws ConfigurationException, IOException {
		DiseaseNameAnalyzer analyzer = DiseaseNameAnalyzer.getDiseaseNameAnalyzer(true, true, false, true);
		EvalCosineSim dnorm = new EvalCosineSim(analyzer, "data/CTD_diseases.tsv", "data/abbreviations.tsv");
		dnorm.getLexicon().prepare();

		System.out.println(analyzer.getTokens("Down's syndrome"));
		System.out.println(analyzer.getTokens("disease type a"));

		FullRankSynonymMatrix matrix = new FullRankSynonymMatrix(dnorm.getLexicon().getDictionary());
		matrix.initalize();
		dnorm.runSynonymSimilarity(args[0], "data/output.txt", matrix);
	}
}
