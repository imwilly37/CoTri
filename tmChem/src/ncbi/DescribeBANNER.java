package ncbi;

import java.io.File;
import java.io.IOException;
import java.util.Collections;

import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.HierarchicalConfiguration;
import org.apache.commons.configuration.XMLConfiguration;

import banner.eval.BANNER;
import banner.tagging.CRFTagger;
import dragon.nlp.tool.Tagger;
import dragon.nlp.tool.lemmatiser.EngLemmatiser;

public class DescribeBANNER {

	private static CRFTagger tagger;

	public static void main(String[] args) {
		HierarchicalConfiguration config;
		try {
			config = new XMLConfiguration(args[0]);
		} catch (ConfigurationException e) {
			throw new RuntimeException(e);
		}
		String cacheFilename = args[1];

		long start = System.currentTimeMillis();
		EngLemmatiser lemmatiser = BANNER.getLemmatiser(config);
		Tagger posTagger = BANNER.getPosTagger(config);

		HierarchicalConfiguration localConfig = config.configurationAt(BANNER.class.getPackage().getName());
		String modelFilename = localConfig.getString("modelFilename");

		System.out.println("Completed input: " + (System.currentTimeMillis() - start));

		try {
			tagger = CRFTagger.load(new File(modelFilename), lemmatiser, posTagger, Collections.singletonList(cacheFilename));
			tagger.describe("description.txt");

		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}
}
