package ncbi;

import java.io.File;
import java.util.HashSet;
import java.util.Set;

import ncbi.chemdner.CHEMDNERFeatureSet2FS;

import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.HierarchicalConfiguration;
import org.apache.commons.configuration.XMLConfiguration;

import cc.mallet.types.Alphabet;
import cc.mallet.types.FeatureVector;
import cc.mallet.types.FeatureVectorSequence;
import cc.mallet.types.Instance;
import cc.mallet.types.InstanceList;

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

public class TrainBANNER_FSGPV {

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

		int frequencyThreshold = Integer.valueOf(args[1]);
		double gaussianPriorVariance = Double.valueOf(args[2]);

		Double percentage = null;
		if (args.length > 3)
			percentage = Double.valueOf(args[3]);

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

		int index = modelFilename.lastIndexOf(".bin");
		modelFilename = new StringBuilder(modelFilename).insert(index, "_" + frequencyThreshold + "_" + gaussianPriorVariance).toString();
		System.out.println("Model filename is: " + modelFilename);

		Set<Sentence> sentences = dataset.getSentences();
		if (percentage != null)
			sentences = CollectionsRand.randomSubset(sentences, percentage);
		System.out.println("Completed input: " + (System.currentTimeMillis() - start));
		System.out.println("Entity types: " + EntityType.getTypes());

		start = System.currentTimeMillis();

		System.out.println("Training data loaded, starting training");
		FeatureSet featureSet = new CHEMDNERFeatureSet2FS(tagFormat, lemmatiser, posTagger, dictionary, null, null, "../ChemSpot_Test/output.tsv", null, mentionTypes, sameTypeOverlapOption,
				differentTypeOverlapOption);
		Set<String> selectedFeatures = getSelectedFeatures(sentences, featureSet, frequencyThreshold);
		featureSet = new CHEMDNERFeatureSet2FS(tagFormat, lemmatiser, posTagger, dictionary, null, null, "../ChemSpot_Test/output.tsv", selectedFeatures, mentionTypes, sameTypeOverlapOption,
				differentTypeOverlapOption);
		CRFTagger tagger = CRFTagger.train(sentences, crfOrder, tagFormat, featureSet, gaussianPriorVariance);
		System.out.println("Training complete, saving model");
		tagger.write(new File(modelFilename));
	}

	private static Set<String> getSelectedFeatures(Set<Sentence> sentences, FeatureSet featureSet, int frequencyThreshold) {
		System.out.println("Selecting features...");
		// Create a temporary set of instances
		InstanceList instances = new InstanceList(featureSet.getPipe());
		for (Sentence sentence : sentences) {
			Instance instance = new Instance(sentence, null, sentence.getSentenceId(), sentence);
			instances.addThruPipe(instance);
		}

		// Setup
		// Alphabet alpha2 = new Alphabet();
		// Noop pipe2 = new Noop(alpha2, instances.getTargetAlphabet());
		// InstanceList instances2 = new InstanceList(pipe2);
		Alphabet alphabet = instances.getDataAlphabet();
		int numFeatures = alphabet.size();
		double[] counts = new double[numFeatures];

		// Get counts from all sequences
		for (int ii = 0; ii < instances.size(); ii++) {
			Instance instance = instances.get(ii);
			FeatureVectorSequence fvs = (FeatureVectorSequence) instance.getData();
			for (int fvsi = 0; fvsi < fvs.size(); fvsi++) {
				FeatureVector fv = fvs.get(fvsi);
				fv.addTo(counts);
			}
		}

		// Determine included features
		// BitSet bs = new BitSet(numFeatures);
		Set<String> selectedFeatures = new HashSet<String>();
		for (int fi = 0; fi < numFeatures; fi++) {
			if (counts[fi] > frequencyThreshold) {
				String featureName = (alphabet.lookupObject(fi)).toString();
				selectedFeatures.add(featureName);
				// bs.set(fi);
			}
		}
		System.out.println("Pruning " + (numFeatures - selectedFeatures.size()) + " features out of " + numFeatures + "; leaving " + (selectedFeatures.size()) + " features.");
		return selectedFeatures;
	}
}
