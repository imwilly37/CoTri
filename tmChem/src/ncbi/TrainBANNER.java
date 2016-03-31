package ncbi;

import java.io.File;
import java.util.Set;

import ncbi.chemdner.CHEMDNERFeatureSet2;

import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.HierarchicalConfiguration;
import org.apache.commons.configuration.XMLConfiguration;

import dragon.nlp.tool.Tagger;
import dragon.nlp.tool.lemmatiser.EngLemmatiser;
import banner.eval.BANNER;
import banner.eval.dataset.Dataset;
import banner.tagging.CRFTagger;
import banner.tagging.FeatureSet;
import banner.tagging.TagFormat;
import banner.tagging.dictionary.DictionaryTagger;
import banner.types.EntityType;
import banner.types.Mention.MentionType;
import banner.types.Sentence;
import banner.types.Sentence.OverlapOption;
import banner.util.CollectionsRand;

public class TrainBANNER {

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		// TODO Auto-generated method stub

		HierarchicalConfiguration config;
		try {
			config = new XMLConfiguration(args[0]);
		} catch (ConfigurationException e) {
			throw new RuntimeException(e);
		}
		Double percentage = null;
		String cacheFilename = args[1];
		if (args.length > 2)
			percentage = Double.valueOf(args[2]);

		long start = System.currentTimeMillis();
		Dataset dataset = BANNER.getDataset(config);
		TagFormat tagFormat = BANNER.getTagFormat(config);
		DictionaryTagger dictionary = BANNER.getDictionary(config);
		int crfOrder = BANNER.getCRFOrder(config);
		System.out.println("tagformat=" + tagFormat);
		System.out.println("crfOrder=" + crfOrder);
		EngLemmatiser lemmatiser = BANNER.getLemmatiser(config);
		Tagger posTagger = BANNER.getPosTagger(config);
		Set<MentionType> mentionTypes = BANNER.getMentionTypes(config);
		OverlapOption sameTypeOverlapOption = BANNER.getSameTypeOverlapOption(config);
		OverlapOption differentTypeOverlapOption = BANNER.getDifferentTypeOverlapOption(config);

		HierarchicalConfiguration localConfig = config.configurationAt(BANNER.class.getPackage().getName());
		String modelFilename = localConfig.getString("modelFilename");
		Set<Sentence> sentences = dataset.getSentences();
		if (percentage != null)
			sentences = CollectionsRand.randomSubset(sentences, percentage);
		System.out.println("Completed input: " + (System.currentTimeMillis() - start));
		System.out.println("Entity types: " + EntityType.getTypes());

		start = System.currentTimeMillis();

		System.out.println("Training data loaded, starting training");
		FeatureSet featureSet = new CHEMDNERFeatureSet2(tagFormat, lemmatiser, posTagger, dictionary, null, null, cacheFilename, mentionTypes, sameTypeOverlapOption, differentTypeOverlapOption);

		CRFTagger tagger = CRFTagger.train(sentences, crfOrder, tagFormat, featureSet);
		System.out.println("Training complete, saving model");
		tagger.write(new File(modelFilename));
	}
}
