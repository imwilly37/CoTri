package dnorm;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.HierarchicalConfiguration;
import org.apache.commons.configuration.XMLConfiguration;

import dnorm.types.FullRankSynonymMatrix;
import dnorm.types.SynonymMatrix;
import dnorm.core.Lexicon;
import dnorm.core.MEDICLexiconLoader;
import dnorm.core.DiseaseNameAnalyzer;
import dnorm.core.SynonymTrainer;
import dnorm.core.SynonymTrainer.LookupResult;
import banner.eval.BANNER;
import banner.postprocessing.PostProcessor;
import banner.tagging.CRFTagger;
import banner.tagging.dictionary.DictionaryTagger;
import banner.tokenization.Tokenizer;
import banner.types.Mention;
import banner.types.Mention.MentionType;
import banner.types.Sentence;
import banner.util.RankedList;
import banner.util.SentenceBreaker;
import banner.types.SentenceWithOffset;
import dragon.nlp.tool.Tagger;
import dragon.nlp.tool.lemmatiser.EngLemmatiser;
import dragon.util.EnvVariable;

public class RunDNorm {

	private static int UPDATE_FREQ = 100;

	private static SentenceBreaker breaker = null;
	private static BufferedReader reader = null;
	private static String currentLine = null;
	private static BufferedWriter writer = null;

	private static long[] elapsed = new long[4];

	private static DiseaseNameAnalyzer analyzer;
	private static Lexicon lex;
	private static MEDICLexiconLoader loader;

	private static HierarchicalConfiguration config;
	private static CRFTagger tagger;

	private static SynonymTrainer syn;

	public static void main(String[] args) {
		long initStart = System.currentTimeMillis();

		// Get command line args
		String configurationFilename = args[0];
		String lexiconFilename = args[1];
		String matrixFilename = args[2];
		String inputFilename = args[3];
		String outputFilename = args[4];

		// Do the setup
		analyzer = DiseaseNameAnalyzer.getDiseaseNameAnalyzer(true, true, false, true);
		loader = new MEDICLexiconLoader();
		lex = new Lexicon(analyzer);
		loader.loadLexicon(lex, lexiconFilename);
		lex.prepare();
		SynonymMatrix matrix = FullRankSynonymMatrix.load(new File(matrixFilename));
		syn = new SynonymTrainer(lex, matrix, 1000);
		try {
			prepareBANNER(configurationFilename);
		} catch (ConfigurationException e1) {
			throw new RuntimeException(e1);
		} catch (IOException e1) {
			throw new RuntimeException(e1);
		}

		// Open the input and output files
		try {
			open(inputFilename, outputFilename);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
		System.out.println("Init took " + (System.currentTimeMillis() - initStart));

		// Process each line in the input file
		try {
			int count = 0;
			while (currentLine != null) {
				elapsed[0] -= System.currentTimeMillis();
				elapsed[1] -= System.currentTimeMillis();
				List<Sentence> inputSentences = getNextSentenceList();
				elapsed[1] += System.currentTimeMillis();
				elapsed[2] -= System.currentTimeMillis();
				List<Sentence> outputSentences = processSentences_BANNER(inputSentences);
				elapsed[2] += System.currentTimeMillis();
				elapsed[3] -= System.currentTimeMillis();
				output(outputSentences);
				elapsed[3] += System.currentTimeMillis();
				elapsed[0] += System.currentTimeMillis();
				count++;
				if (count % UPDATE_FREQ == 0) {
					System.out.print("\t" + count);
					for (int i = 0; i < elapsed.length; i++) {
						System.out.print("\t" + elapsed[i]);
					}
					System.out.println();
				}
			}
			System.out.print("\t" + count);
			for (int i = 0; i < elapsed.length; i++) {
				System.out.print("\t" + elapsed[i]);
			}
			System.out.println();
		} catch (IOException e) {
			throw new RuntimeException(e);
		}

		try {
			close();
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	private static void prepareBANNER(String configurationFile) throws ConfigurationException, IOException {
		long start = System.currentTimeMillis();
		config = new XMLConfiguration(configurationFile);
		EnvVariable.setDragonHome(".");
		EnvVariable.setCharSet("US-ASCII");
		EngLemmatiser lemmatiser = BANNER.getLemmatiser(config);
		Tagger posTagger = BANNER.getPosTagger(config);
		HierarchicalConfiguration localConfig = config.configurationAt(BANNER.class.getPackage().getName());
		String modelFilename = localConfig.getString("modelFilename");
		System.out.println("Model: " + modelFilename);
		tagger = CRFTagger.load(new File(modelFilename), lemmatiser, posTagger);
		System.out.println("Completed input: " + (System.currentTimeMillis() - start));
	}

	private static void open(String inputFilename, String outputFilename) throws IOException {
		breaker = new SentenceBreaker();
		reader = new BufferedReader(new InputStreamReader(new FileInputStream(inputFilename), "UTF-8"));
		currentLine = reader.readLine();
		writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(outputFilename), "UTF-8"));
	}

	private static List<Sentence> getNextSentenceList() throws IOException {
		if (currentLine == null)
			return null;
		List<Sentence> currentSentences = new ArrayList<Sentence>();
		currentLine = currentLine.trim();
		if (currentLine.length() == 0) {
			currentLine = reader.readLine();
			return currentSentences;
		}
		int index = currentLine.indexOf("\t");
		if (index == -1) {
			System.out.println("WARNING: Incorrectly formatted input line: \"" + currentLine + "\"");
			currentLine = reader.readLine();
			return currentSentences;
		}
		String reportId = currentLine.substring(0, index);
		String reportText = currentLine.substring(index + 1);
		breaker.setText(reportText);
		int start = 0;
		List<String> sentenceTexts = breaker.getSentences();
		for (int i = 0; i < sentenceTexts.size(); i++) {
			String sentenceId = Integer.toString(i);
			if (sentenceId.length() < 2)
				sentenceId = "0" + sentenceId;
			sentenceId = reportId + sentenceId;
			String sentenceText = sentenceTexts.get(i);
			Sentence s = new SentenceWithOffset(sentenceId, reportId, sentenceText, start);
			currentSentences.add(s);
			start += sentenceText.length();
		}
		currentLine = reader.readLine();
		return currentSentences;
	}

	public static List<Sentence> processSentences_BANNER(List<Sentence> inputSentences) {
		// TODO Refactor this into separate NER and normalization methods
		List<Sentence> outputSentences = new ArrayList<Sentence>(inputSentences.size());
		for (Sentence inputSentence : inputSentences) {
			int offset = ((SentenceWithOffset) inputSentence).getOffset();
			Sentence bannerSentence = new SentenceWithOffset(inputSentence.getSentenceId(), inputSentence.getDocumentId(), inputSentence.getText(), offset);
			Tokenizer tokenizer = BANNER.getTokenizer(config);
			PostProcessor postProcessor = BANNER.getPostProcessor(config);
			Sentence outputSentence = BANNER.process(tagger, tokenizer, postProcessor, bannerSentence);

			for (Mention mention : outputSentence.getMentions(MentionType.Found)) {
				String lookupText = mention.getText();
				// Do lookup & store results
				RankedList<LookupResult> results = syn.lookup(lookupText);
				if (results.size() > 0) {
					String conceptId = results.getObject(0).getConceptId();
					mention.setConceptId(conceptId);
				}
			}
			outputSentences.add(outputSentence);
		}
		return outputSentences;
	}

	private static void output(List<Sentence> outputSentences) throws IOException {
		for (int index = 0; index < outputSentences.size(); index++) {
			Sentence outputSentence = outputSentences.get(index);
			String documentId = outputSentence.getDocumentId();

			int offset = ((SentenceWithOffset) outputSentence).getOffset();
			for (Mention mention : outputSentence.getMentions()) {
				StringBuilder outputLine = new StringBuilder();
				outputLine.append(documentId);
				outputLine.append("\t");
				outputLine.append(mention.getStartChar() + offset);
				outputLine.append("\t");
				outputLine.append(mention.getEndChar() + offset);
				outputLine.append("\t");
				outputLine.append(mention.getText());
				outputLine.append("\t");
				if (mention.getConceptId() != null)
					outputLine.append(mention.getConceptId());
				writer.write(outputLine.toString().trim());
				writer.newLine();
			}
		}
	}

	private static void close() throws IOException {
		breaker = null;
		reader.close();
		reader = null;
		currentLine = null;
		writer.close();
		writer = null;
	}
}
