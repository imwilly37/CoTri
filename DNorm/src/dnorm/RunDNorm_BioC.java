package dnorm;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.List;

import javax.xml.stream.XMLStreamException;

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
import banner.types.Sentence;
import banner.util.RankedList;
import banner.util.SentenceBreaker;
import bioc.BioCAnnotation;
import bioc.BioCCollection;
import bioc.BioCDocument;
import bioc.BioCPassage;
import bioc.io.BioCDocumentWriter;
import bioc.io.BioCFactory;
import bioc.io.woodstox.ConnectorWoodstox;
import dragon.nlp.tool.Tagger;
import dragon.nlp.tool.lemmatiser.EngLemmatiser;
import dragon.util.EnvVariable;

public class RunDNorm_BioC {

	private static SentenceBreaker breaker;
	private static DiseaseNameAnalyzer analyzer;
	private static Lexicon lex;
	private static MEDICLexiconLoader loader;
	private static CRFTagger tagger;
	private static Tokenizer tokenizer;
	private static PostProcessor postProcessor;
	private static SynonymTrainer syn;

	public static void main(String[] args) throws IOException, XMLStreamException, ConfigurationException {

		// Get command line args
		String configurationFilename = args[0];
		String lexiconFilename = args[1];
		String matrixFilename = args[2];
		String input = args[3];
		String output = args[4];

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

		// Process file(s)
		File inFile = new File(input);
		File outFile = new File(output);

		if (inFile.isDirectory()) {
			if (!outFile.isDirectory()) {
				usage();
				throw new IllegalArgumentException();
			}
			if (!input.endsWith("/"))
				input = input + "/";
			if (!output.endsWith("/"))
				output = output + "/";
			File[] listOfFiles = (new File(input)).listFiles();
			for (int i = 0; i < listOfFiles.length; i++) {
				if (listOfFiles[i].isFile() && listOfFiles[i].getName().endsWith(".xml")) {
					String reportFilename = input + listOfFiles[i].getName();
					System.out.println("Processing file " + reportFilename);
					String annotationFilename = output + listOfFiles[i].getName();
					processFile(reportFilename, annotationFilename);
				}
			}
		} else {
			if (outFile.isDirectory()) {
				usage();
				throw new IllegalArgumentException();
			}
			processFile(input, output);
		}
	}

	private static void usage() {
		System.out.println("Usage:");
		System.out.println("\tBANNER_BioC configurationFilename lexiconFilename matrixFilename inputFilename outputFilename");
		System.out.println("OR");
		System.out.println("\tBANNER_BioC configurationFilename lexiconFilename matrixFilename inputDirectory outputDirectory");
	}

	private static void prepareBANNER(String configurationFile) throws ConfigurationException, IOException {
		long start = System.currentTimeMillis();
		HierarchicalConfiguration config = new XMLConfiguration(configurationFile);
		EnvVariable.setDragonHome(".");
		EnvVariable.setCharSet("US-ASCII");
		tokenizer = BANNER.getTokenizer(config);
		EngLemmatiser lemmatiser = BANNER.getLemmatiser(config);
		Tagger posTagger = BANNER.getPosTagger(config);
		postProcessor = BANNER.getPostProcessor(config);
		HierarchicalConfiguration localConfig = config.configurationAt(BANNER.class.getPackage().getName());
		String modelFilename = localConfig.getString("modelFilename");
		System.out.println("Model: " + modelFilename);
		tagger = CRFTagger.load(new File(modelFilename), lemmatiser, posTagger);
		System.out.println("Completed input: " + (System.currentTimeMillis() - start));
		breaker = new SentenceBreaker();
	}

	private static void processFile(String inXML, String outXML) throws IOException, XMLStreamException {
		ConnectorWoodstox connector = new ConnectorWoodstox();
		BioCCollection collection = connector.startRead(new InputStreamReader(new FileInputStream(inXML), "UTF-8"));
		String parser = BioCFactory.WOODSTOX;
		BioCFactory factory = BioCFactory.newFactory(parser);
		BioCDocumentWriter writer = factory.createBioCDocumentWriter(new OutputStreamWriter(new FileOutputStream(outXML), "UTF-8"));
		writer.writeCollectionInfo(collection);
		while (connector.hasNext()) {
			BioCDocument document = connector.next();
			String documentId = document.getID();
			System.out.println("ID=" + documentId);
			for (BioCPassage passage : document.getPassages()) {
				processPassage(documentId, passage);
			}
			writer.writeDocument(document);
			System.out.println();
		}
		writer.close();
	}

	private static void processPassage(String documentId, BioCPassage passage) {
		// Figure out the correct next annotation ID to use
		int nextId = 0;
		for (BioCAnnotation annotation : passage.getAnnotations()) {
			String annotationIdString = annotation.getID();
			if (annotationIdString.matches("[0-9]+")) {
				int annotationId = Integer.parseInt(annotationIdString);
				if (annotationId > nextId)
					nextId = annotationId;
			}
		}

		// Process the passage text
		System.out.println("Text=" + passage.getText());
		breaker.setText(passage.getText());
		int offset = passage.getOffset();
		List<String> sentences = breaker.getSentences();
		for (int i = 0; i < sentences.size(); i++) {
			String sentenceText = sentences.get(i);
			String sentenceId = Integer.toString(i);
			if (sentenceId.length() < 2)
				sentenceId = "0" + sentenceId;
			sentenceId = documentId + "-" + sentenceId;
			Sentence sentence = new Sentence(sentenceId, documentId, sentenceText);
			sentence = BANNER.process(tagger, tokenizer, postProcessor, sentence);
			for (Mention mention : sentence.getMentions()) {
				BioCAnnotation annotation = new BioCAnnotation();
				nextId++;
				annotation.setID(Integer.toString(nextId));
				String entityType = mention.getEntityType().getText();
				if (entityType.matches("[A-Z]+")) {
					entityType = entityType.toLowerCase();
					String first = entityType.substring(0, 1);
					entityType = entityType.replaceFirst(first, first.toUpperCase());
				}
				annotation.putInfon("type", entityType);
				String mentionText = mention.getText();
				annotation.setLocation(offset + mention.getStartChar(), mentionText.length());
				annotation.setText(mentionText);
				RankedList<LookupResult> results = syn.lookup(mentionText);
				if (results.size() > 0) {
					String conceptId = results.getObject(0).getConceptId();
					// Cut off "MESH:" and "OMIM:"
					String id = "id";
					int index = conceptId.indexOf(":");
					if (index != -1) {
						id = conceptId.substring(0, index);
						conceptId = conceptId.substring(index + 1);
					}
					annotation.putInfon(id, conceptId);
				}
				passage.addAnnotation(annotation);
			}
			offset += sentenceText.length();
		}
	}
}
