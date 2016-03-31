package dnorm;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.List;
import java.util.Map;

import javax.xml.stream.XMLStreamException;

import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.HierarchicalConfiguration;
import org.apache.commons.configuration.XMLConfiguration;

import dnorm.core.DiseaseNameAnalyzer;
import dnorm.core.Lexicon;
import dnorm.core.MEDICLexiconLoader;
import dnorm.core.SynonymTrainer;
import dnorm.core.SynonymTrainer.LookupResult;
import dnorm.types.FullRankSynonymMatrix;
import dnorm.util.AbbreviationIdentifier;
import dnorm.util.AbbreviationResolver;
import dragon.nlp.tool.Tagger;
import dragon.nlp.tool.lemmatiser.EngLemmatiser;
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
import dragon.util.EnvVariable;

public class ApplyDNorm_BioC {

	private static SentenceBreaker breaker;
	private static AbbreviationIdentifier abbrev;
	private static CRFTagger tagger;
	private static Tokenizer tokenizer;
	private static PostProcessor postProcessor;
	private static SynonymTrainer syn;

	public static void main(String[] args) throws IOException, XMLStreamException, ConfigurationException {
		String configurationFilename = args[0];
		String lexiconFilename = args[1];
		String matrixFilename = args[2];
		String abbreviationDirectory = args[3];
		String tempDirectory = args[4];
		String input = args[5];
		String output = args[6];

		DiseaseNameAnalyzer analyzer = DiseaseNameAnalyzer.getDiseaseNameAnalyzer(true, true, false, true);
		Lexicon lex = new Lexicon(analyzer);
		MEDICLexiconLoader loader = new MEDICLexiconLoader();
		loader.loadLexicon(lex, lexiconFilename);
		lex.prepare();

		FullRankSynonymMatrix matrix = FullRankSynonymMatrix.load(new File(matrixFilename));
		syn = new SynonymTrainer(lex, matrix, 1000);

		HierarchicalConfiguration config = new XMLConfiguration(configurationFilename);
		EnvVariable.setDragonHome(".");
		EnvVariable.setCharSet("US-ASCII");
		EngLemmatiser lemmatiser = BANNER.getLemmatiser(config);
		Tagger posTagger = BANNER.getPosTagger(config);
		HierarchicalConfiguration localConfig = config.configurationAt(BANNER.class.getPackage().getName());
		String modelFilename = localConfig.getString("modelFilename");
		tagger = CRFTagger.load(new File(modelFilename), lemmatiser, posTagger);
		tokenizer = BANNER.getTokenizer(config);
		postProcessor = BANNER.getPostProcessor(config);

		abbrev = new AbbreviationIdentifier("./identify_abbr", abbreviationDirectory, tempDirectory, 1000);
		breaker = new SentenceBreaker();

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
		System.out.println("Done.");
	}

	private static void usage() {
		System.out.println("Usage:");
		// TODO
		System.out.println("\tBANNER_BioC configurationFilename lexiconFilename matrixFilename inputFilename outputFilename");
		System.out.println("OR");
		System.out.println("\tBANNER_BioC configurationFilename lexiconFilename matrixFilename inputDirectory outputDirectory");
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
				Map<String, String> abbreviationMap = abbrev.getAbbreviations(documentId, passage.getText());
				processPassage(documentId, passage, abbreviationMap);
			}
			writer.writeDocument(document);
			System.out.println();
		}
		writer.close();
	}

	private static void processPassage(String documentId, BioCPassage passage, Map<String, String> abbreviationMap) {
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
		breaker.setText(passage.getText());
		int offset = passage.getOffset();
		List<String> sentences = breaker.getSentences();
		for (int i = 0; i < sentences.size(); i++) {
			String sentenceText = sentences.get(i);
			String sentenceTextTrim = sentenceText.trim();
			int trimOffset = sentenceText.indexOf(sentenceTextTrim);
			if (sentenceTextTrim.length() > 0) {
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
					annotation.setLocation(offset + trimOffset + mention.getStartChar(), mentionText.length());
					annotation.setText(mentionText);
					String lookupText = AbbreviationResolver.expandAbbreviations(mentionText, abbreviationMap);
					RankedList<LookupResult> results = syn.lookup(lookupText);
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
			}
			offset += sentenceText.length();
		}
	}

	// TODO The original version of ApplyDNorm performed some post-processing
}
