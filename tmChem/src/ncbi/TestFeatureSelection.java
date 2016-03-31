package ncbi;

import java.util.BitSet;
import java.util.HashSet;
import java.util.Set;

import ncbi.chemdner.CHEMDNERFeatureSet2;

import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.HierarchicalConfiguration;
import org.apache.commons.configuration.XMLConfiguration;

import cc.mallet.pipe.Noop;
import cc.mallet.types.Alphabet;
import cc.mallet.types.FeatureSelection;
import cc.mallet.types.FeatureVector;
import cc.mallet.types.FeatureVectorSequence;
import cc.mallet.types.Instance;
import cc.mallet.types.InstanceList;

import dragon.nlp.tool.Tagger;
import dragon.nlp.tool.lemmatiser.EngLemmatiser;

import banner.eval.BANNER;
import banner.eval.dataset.Dataset;
import banner.tagging.FeatureSet;
import banner.tagging.TagFormat;
import banner.tagging.dictionary.DictionaryTagger;
import banner.types.EntityType;
import banner.types.Mention.MentionType;
import banner.types.Sentence;
import banner.types.Sentence.OverlapOption;
import banner.util.CollectionsRand;

public class TestFeatureSelection {

	public static void main(String[] args) {
		// TODO Auto-generated method stub

		HierarchicalConfiguration config;
		try {
			config = new XMLConfiguration(args[0]);
		} catch (ConfigurationException e) {
			throw new RuntimeException(e);
		}
		Double percentage = null;
		if (args.length > 1)
			percentage = Double.valueOf(args[1]);

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

		Set<Sentence> sentences = dataset.getSentences();
		if (percentage != null)
			sentences = CollectionsRand.randomSubset(sentences, percentage);
		System.out.println("Completed input: " + (System.currentTimeMillis() - start));
		System.out.println("Entity types: " + EntityType.getTypes());

		System.out.println("Training data loaded: " + (System.currentTimeMillis() - start));
		FeatureSet featureSet = new CHEMDNERFeatureSet2(tagFormat, lemmatiser, posTagger, dictionary, null, null, "../ChemSpot_Test/output.tsv", mentionTypes, sameTypeOverlapOption,
				differentTypeOverlapOption);
		System.out.println("Feature set created: " + (System.currentTimeMillis() - start));

		InstanceList instances = new InstanceList(featureSet.getPipe());
		for (Sentence sentence : sentences) {
			Instance instance = new Instance(sentence, null, sentence.getSentenceId(), sentence);
			instances.addThruPipe(instance);
		}
		System.out.println("Instances created: " + (System.currentTimeMillis() - start));
		System.out.println("Alphabet size: " + instances.getDataAlphabet().size());
		System.out.println("Features used: " + getFeaturesUsed(instances).size());

		InstanceList instances2 = pruneInstancesByCount(instances, 3); // TODO Make this command-line configurable
		System.out.println("Alphabet size: " + instances2.getDataAlphabet().size());
		System.out.println("Features used: " + getFeaturesUsed(instances2).size());

		// CRFTagger tagger = CRFTagger.train(sentences, crfOrder, tagFormat, featureSet);
		// System.out.println("Training complete, saving model");
		// tagger.write(new File(modelFilename));
	}

	private static Set<String> getFeaturesUsed(InstanceList instances) {
		Set<String> features = new HashSet<String>();
		Alphabet alphabet = instances.getDataAlphabet();
		for (int i = 0; i < instances.size(); i++) {
			Instance instance = instances.get(i);
			FeatureVectorSequence sequence = (FeatureVectorSequence) instance.getData();
			for (int j = 0; j < sequence.size(); j++) {
				FeatureVector vector = sequence.get(j);
				int[] indices = vector.getIndices();
				if (indices == null)
					System.out.println("Indices is null");
				for (int k = 0; k < indices.length; k++) {
					int index = indices[k];
					String featureName = (alphabet.lookupObject(index)).toString();
					features.add(featureName);
					// if (Math.random() < 0.001)
					// System.out.println(featureName);
				}
			}
		}
		return features;
	}

	private static InstanceList pruneInstancesByCount(InstanceList instances, int frequencyThreshold) {
		// Setup
		Alphabet alpha2 = new Alphabet();
		Noop pipe2 = new Noop(alpha2, instances.getTargetAlphabet());
		InstanceList instances2 = new InstanceList(pipe2);
		int numFeatures = instances.getDataAlphabet().size();
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
		BitSet bs = new BitSet(numFeatures);
		for (int fi = 0; fi < numFeatures; fi++) {
			if (counts[fi] > frequencyThreshold) {
				bs.set(fi);
			}
		}
		System.out.println("Pruning " + (numFeatures - bs.cardinality()) + " features out of " + numFeatures + "; leaving " + (bs.cardinality()) + " features.");

		// Create a pruned set of instances
		FeatureSelection fs = new FeatureSelection(instances.getDataAlphabet(), bs);
		for (int ii = 0; ii < instances.size(); ii++) {
			Instance instance = instances.get(ii);
			FeatureVectorSequence fvs = (FeatureVectorSequence) instance.getData();
			// Create a pruned set of feature vectors for this instance
			FeatureVector[] fva2 = new FeatureVector[fvs.size()];
			for (int fvsi = 0; fvsi < fvs.size(); fvsi++) {
				FeatureVector fv = fvs.get(fvsi);
				FeatureVector fv2 = FeatureVector.newFeatureVector(fv, alpha2, fs);
				fva2[fvsi] = fv2;
			}
			// Create and add the new instance
			FeatureVectorSequence fvs2 = new FeatureVectorSequence(fva2);
			Instance instance2 = new Instance(fvs2, instance.getTarget(), instance.getName(), instance.getSource());
			instances2.add(instance2, instances.getInstanceWeight(ii));

			// Free the old instance
			instance.unLock();
			instance.setData(null);
		}
		return instances2;
	}
}
