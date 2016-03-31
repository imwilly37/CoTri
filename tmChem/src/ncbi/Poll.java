package ncbi;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.xml.stream.XMLStreamException;

import ncbi.chemdner.AbbreviationIdentifier;
import ncbi.chemdner.ParenthesisBalancingPostProcessor;

import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.HierarchicalConfiguration;
import org.apache.commons.configuration.XMLConfiguration;

import banner.eval.BANNER;
import banner.tagging.CRFTagger;
import banner.util.SentenceBreaker;
import dragon.nlp.tool.Tagger;
import dragon.nlp.tool.lemmatiser.EngLemmatiser;

public class Poll {

	public static void main(String[] args) throws IOException, XMLStreamException {
		HierarchicalConfiguration config;
		try {
			config = new XMLConfiguration(args[0]);
		} catch (ConfigurationException e) {
			throw new RuntimeException(e);
		}
		String dictionaryFilename = args[1];
		String abbreviationDirectory = args[2];
		String tempDirectory = args[3];
		String input = args[4];
		String output = args[5];

		EngLemmatiser lemmatiser = BANNER.getLemmatiser(config);
		Tagger posTagger = BANNER.getPosTagger(config);
		Run.tokenizer = BANNER.getTokenizer(config);
		Run.postProcessor = new ParenthesisBalancingPostProcessor();

		HierarchicalConfiguration localConfig = config.configurationAt(BANNER.class.getPackage().getName());
		String modelFilename = localConfig.getString("modelFilename");
		System.out.println("modelFilename=" + modelFilename);

		Run.tagger = CRFTagger.load(new File(modelFilename), lemmatiser, posTagger, null);

		Run.abbrev = new AbbreviationIdentifier("./identify_abbr", abbreviationDirectory, tempDirectory, 1000);
		Run.breaker = new SentenceBreaker();
		Run.dict = Run.loadDictionary(dictionaryFilename);

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

			boolean error = false;
			System.out.println("Waiting for input");
			while (!error) {
				// TODO Move the locking to the 'poll' version
				List<String> reportFilenames = getUnlockedFiles(input);
				for (String filename : reportFilenames) {
					String reportFilename = input + filename;
					String annotationFilename = output + filename;
					String lockFilename = output + "." + filename + ".lck";
					(new OutputStreamWriter(new FileOutputStream(lockFilename), "UTF-8")).close();
					if (filename.endsWith(".xml")) {
						Run.processFile_BioC(reportFilename, annotationFilename);
					} else {
						Run.processFile_PubTator(reportFilename, annotationFilename);
					}
					(new File(lockFilename)).delete();
					(new File(reportFilename)).delete();
					System.out.println("Waiting for input");
				}
				try {
					Thread.sleep(500);
				} catch (InterruptedException e) {
					System.err.println("Interrupted while polling:");
					e.printStackTrace();
					error = true;
				}
			}
		} else {
			usage();
			throw new IllegalArgumentException();
		}
		System.out.println("Done.");
	}

	private static List<String> getUnlockedFiles(String input) {
		List<String> reportFilenames = new ArrayList<String>();
		Set<String> lockFilenames = new HashSet<String>();
		File[] listOfFiles = (new File(input)).listFiles();
		for (int i = 0; i < listOfFiles.length; i++) {
			if (listOfFiles[i].isFile()) {
				String filename = listOfFiles[i].getName();
				if (filename.endsWith(".lck")) {
					lockFilenames.add(filename);
				} else {
					reportFilenames.add(filename);
				}
			}
		}
		List<String> unlockedReportFilenames = new ArrayList<String>();
		for (String filename : reportFilenames) {
			String lockFilename = "." + filename + ".lck";
			if (!lockFilenames.contains(lockFilename)) {
				unlockedReportFilenames.add(filename);
			}
		}
		return unlockedReportFilenames;
	}

	private static void usage() {
		System.out.println("Usage:");
		System.out.println("\tPoll configurationFilename dictionaryFilename Ab3P_Directory tempDirectory input output");
	}
}
