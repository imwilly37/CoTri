package ncbi;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.Collections;
import java.util.List;

import javax.xml.stream.XMLStreamException;

import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.HierarchicalConfiguration;
import org.apache.commons.configuration.XMLConfiguration;

import dragon.nlp.tool.Tagger;
import dragon.nlp.tool.lemmatiser.EngLemmatiser;

import banner.eval.BANNER;
import banner.postprocessing.PostProcessor;
import banner.tagging.CRFTagger;
import banner.tagging.dictionary.DictionaryTagger;
import banner.tokenization.Tokenizer;
import banner.types.Mention;
import banner.types.Sentence;
import banner.util.SentenceBreaker;

import bioc.BioCAnnotation;
import bioc.BioCCollection;
import bioc.BioCDocument;
import bioc.BioCPassage;
import bioc.io.BioCDocumentWriter;
import bioc.io.BioCFactory;
import bioc.io.woodstox.ConnectorWoodstox;

public class RunBioC {

	private SentenceBreaker breaker;
	private CRFTagger tagger;
	private Tokenizer tokenizer;
	private PostProcessor postProcessor;

	public static void main(String[] args) throws IOException, XMLStreamException, ConfigurationException {
		if (args.length != 5) {
			usage();
			return;
		}

		String configFilename = args[0];
		String abbreviationFilename = args[1];
		String cacheFilename = args[2];
		RunBioC bannerBioC = new RunBioC(configFilename, cacheFilename);

		String in = args[3];
		File inFile = new File(in);
		String out = args[4];
		File outFile = new File(out);

		if (inFile.isDirectory()) {
			if (!outFile.isDirectory()) {
				usage();
				throw new IllegalArgumentException();
			}
			if (!in.endsWith("/"))
				in = in + "/";
			if (!out.endsWith("/"))
				out = in + "/";
			File[] listOfFiles = (new File(in)).listFiles();
			for (int i = 0; i < listOfFiles.length; i++) {
				if (listOfFiles[i].isFile() && listOfFiles[i].getName().endsWith(".xml")) {
					String reportFilename = in + listOfFiles[i].getName();
					System.out.println("Processing file " + reportFilename);
					String annotationFilename = out + listOfFiles[i].getName();
					bannerBioC.processFile(reportFilename, annotationFilename);
				}
			}
		} else {
			if (outFile.isDirectory()) {
				usage();
				throw new IllegalArgumentException();
			}
			bannerBioC.processFile(in, out);
		}
	}

	private static void usage() {
		System.out.println("Usage:");
		System.out.println("\tRunBioC configurationFilename abbreviationFilename cacheFilename inputFilename outputFilename");
		System.out.println("OR");
		System.out.println("\tRunBioC configurationFilename abbreviationFilename cacheFilename inputDirectory outputDirectory");
	}

	public RunBioC(String configFilename, String cacheFilename) throws IOException, ConfigurationException {
		long start = System.currentTimeMillis();
		HierarchicalConfiguration config = new XMLConfiguration(configFilename);
		tokenizer = BANNER.getTokenizer(config);
		EngLemmatiser lemmatiser = BANNER.getLemmatiser(config);
		Tagger posTagger = BANNER.getPosTagger(config);
		postProcessor = BANNER.getPostProcessor(config);
		HierarchicalConfiguration localConfig = config.configurationAt(BANNER.class.getPackage().getName());
		String modelFilename = localConfig.getString("modelFilename");
		System.out.println("Model: " + modelFilename);
		tagger = CRFTagger.load(new File(modelFilename), lemmatiser, posTagger, Collections.singletonList(cacheFilename));
		System.out.println("Loaded: " + (System.currentTimeMillis() - start));
		breaker = new SentenceBreaker();
	}

	private void processFile(String inXML, String outXML) throws IOException, XMLStreamException {
		System.out.println("inXML=" + inXML + ", outXML=" + outXML);
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

	private void processPassage(String documentId, BioCPassage passage) {
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
			Sentence sentence = new Sentence(sentenceId, documentId, sentenceText); // FIXME
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
				annotation.setInfons(Collections.singletonMap("type", entityType));
				String mentionText = mention.getText();
				annotation.setLocation(offset + mention.getStartChar(), mentionText.length());
				annotation.setText(mentionText);
				passage.addAnnotation(annotation);
			}
			offset += sentenceText.length();
		}
	}
}
