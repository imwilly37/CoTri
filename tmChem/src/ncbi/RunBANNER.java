package ncbi;

import gnu.trove.map.TObjectIntMap;
import gnu.trove.map.hash.TObjectIntHashMap;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import ncbi.chemdner.AbbreviationIdentifier;
import ncbi.chemdner.ParenthesisBalancingPostProcessor;

import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.HierarchicalConfiguration;
import org.apache.commons.configuration.XMLConfiguration;

import banner.eval.BANNER;
import banner.eval.BANNER.MatchCriteria;
import banner.eval.BANNER.Performance;
import banner.eval.dataset.Dataset;
import banner.postprocessing.PostProcessor;
import banner.tagging.CRFTagger;
import banner.tokenization.Tokenizer;
import banner.types.EntityType;
import banner.types.Mention;
import banner.types.Sentence;
import banner.types.Mention.MentionType;
import banner.types.SentenceWithOffset;
import banner.util.SentenceBreaker;
import dragon.nlp.tool.Tagger;
import dragon.nlp.tool.lemmatiser.EngLemmatiser;

public class RunBANNER {

	private static Tokenizer tokenizer;
	private static PostProcessor postProcessor;
	private static CRFTagger tagger;
	private static SentenceBreaker breaker;
	private static AbbreviationIdentifier abbrev;

	public static void main(String[] args) {
		HierarchicalConfiguration config;
		try {
			config = new XMLConfiguration(args[0]);
		} catch (ConfigurationException e) {
			throw new RuntimeException(e);
		}
		String abbreviationFilename = args[1];
		String cacheFilename = args[2];
		String outputFilename = args[3];
		String outputDirname = args[4];
		if (!outputDirname.endsWith("/"))
			outputDirname = outputDirname + "/";

		long start = System.currentTimeMillis();
		EngLemmatiser lemmatiser = BANNER.getLemmatiser(config);
		Tagger posTagger = BANNER.getPosTagger(config);
		tokenizer = BANNER.getTokenizer(config);
		postProcessor = new ParenthesisBalancingPostProcessor();

		HierarchicalConfiguration localConfig = config.configurationAt(BANNER.class.getPackage().getName());
		String modelFilename = localConfig.getString("modelFilename");
		System.out.println("modelFilename=" + modelFilename);

		Dataset dataset = BANNER.getDataset(config);
		List<Sentence> sentences = new ArrayList<Sentence>(dataset.getSentences());

		changeType(sentences);
		Collections.sort(sentences, new Comparator<Sentence>() {
			@Override
			public int compare(Sentence s1, Sentence s2) {
				return s1.getSentenceId().compareTo(s2.getSentenceId());
			}
		});

		System.out.println("Completed input: " + (System.currentTimeMillis() - start));

		try {
			tagger = CRFTagger.load(new File(modelFilename), lemmatiser, posTagger, Collections.singletonList(cacheFilename));

			List<Sentence> processedSentences = process(sentences);
			changeType(processedSentences);
			System.out.println("===============");
			System.out.println("Performance with BANNER:");
			System.out.println("===============");
			checkPerformance(sentences, processedSentences);

			processedSentences = consistency(processedSentences, 2);
			System.out.println("===============");
			System.out.println("Performance with Consistency:");
			System.out.println("===============");
			checkPerformance(sentences, processedSentences);

			processedSentences = resolveAbbreviations(abbreviationFilename, processedSentences);
			System.out.println("===============");
			System.out.println("Performance after resolving abbreviations:");
			System.out.println("===============");
			checkPerformance(sentences, processedSentences);
			normalize(processedSentences);
			output(processedSentences, outputFilename, outputDirname);

		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	private static void changeType(List<Sentence> sentences) {
		for (Sentence s : sentences) {
			for (Mention m : s.getMentions()) {
				m.setEntityType(EntityType.getType("CHEMICAL"));
			}
		}
	}

	private static void checkPerformance(List<Sentence> annotatedSentences, List<Sentence> processedSentences) {
		Performance performance = new Performance(MatchCriteria.Strict);
		for (int i = 0; i < annotatedSentences.size(); i++) {
			Sentence annotatedSentence = annotatedSentences.get(i);
			Sentence processedSentence = processedSentences.get(i);
			performance.update(annotatedSentence, processedSentence);
		}
		performance.print();
	}

	private static List<Sentence> process(List<Sentence> sentences) {
		int count = 0;
		List<Sentence> sentences2 = new ArrayList<Sentence>();
		for (Sentence sentence : sentences) {
			if (count % 1000 == 0)
				System.out.println(count);
			Sentence sentence2 = sentence.copy(false, false);
			tokenizer.tokenize(sentence2);
			tagger.tag(sentence2);
			postProcessor.postProcess(sentence2);
			sentences2.add(sentence2);
			count++;
		}
		return sentences2;
	}

	private static List<Sentence> resolveAbbreviations(String filename, List<Sentence> sentences) throws IOException {

		// Get abbreviations
		Map<String, Map<String, String>> shortLongMap = new HashMap<String, Map<String, String>>();
		BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(filename), "UTF8"));
		try {
			String line = reader.readLine();
			while (line != null) {
				line = line.trim();
				if (line.length() > 0) {
					String[] split = line.split("\\t");
					String documentId = split[0];
					String shortForm = split[1];
					String longForm = split[2];
					Map<String, String> shortLong = shortLongMap.get(documentId);
					if (shortLong == null) {
						shortLong = new HashMap<String, String>();
						shortLongMap.put(documentId, shortLong);
					}
					if (shortLong.containsKey(shortForm) && !shortLong.get(shortForm).equals(longForm)) {
						throw new IllegalArgumentException("short =" + shortForm + ", long =" + shortForm + ", previous=" + shortLong.get(shortForm));
					}
					shortLong.put(shortForm, longForm);
				}
				line = reader.readLine();
			}
		} finally {
			reader.close();
		}

		// Get mentions
		Map<String, Set<String>> mentionMap = new HashMap<String, Set<String>>();
		for (Sentence sentence : sentences) {
			Set<String> mentions = mentionMap.get(sentence.getDocumentId());
			if (mentions == null) {
				mentions = new HashSet<String>();
				mentionMap.put(sentence.getDocumentId(), mentions);
			}
			for (Mention mention : sentence.getMentions()) {
				mentions.add(mention.getText());
			}
		}

		// Remove all mentions that match a short form
		// for (Sentence sentence : sentences) {
		// Map<String, String> shortLongMapTemp = shortLongMap.get(sentence.getDocumentId());
		// if (shortLongMapTemp != null) {
		// List<Mention> mentions = new ArrayList<Mention>(sentence.getMentions());
		// for (Mention m : mentions) {
		// if (shortLongMapTemp.containsKey(m.getText())) {
		// sentence.removeMention(m);
		// }
		// }
		// }
		// }

		// Add mentions for all short forms where the long form is marked as a mention
		for (Sentence sentence : sentences) {
			Set<String> mentions = mentionMap.get(sentence.getDocumentId());
			Map<String, String> shortLongMapTemp = shortLongMap.get(sentence.getDocumentId());
			if (shortLongMapTemp != null) {
				for (String shortForm : shortLongMapTemp.keySet()) {
					String longForm = shortLongMapTemp.get(shortForm);
					if (mentions != null && mentions.contains(longForm)) {
						String pattern = "\\b" + Pattern.quote(shortForm) + "\\b";
						Pattern mentionPattern = Pattern.compile(pattern);
						Matcher textMatcher = mentionPattern.matcher(sentence.getText());
						while (textMatcher.find()) {
							// TODO Add the mention found
							System.out.println("\tABBREV FOUND: " + sentence.getDocumentId() + "|" + textMatcher.start() + "|" + textMatcher.end() + "|" + shortForm + "|" + longForm);
							int tagstart = sentence.getTokenIndexStart(textMatcher.start());
							int tagend = sentence.getTokenIndexEnd(textMatcher.end());
							if (tagstart < 0 || tagend < 0) {
								System.out.println("WARNING: Abbreviation ignored");
							} else {
								Mention mention = new Mention(sentence, tagstart, tagend + 1, EntityType.getType("CHEMICAL"), MentionType.Found);
								if (!sentence.getMentions().contains(mention))
									sentence.addMention(mention);
							}
						}
					}
				}
			}
		}
		return sentences;
	}

	private static void output(List<Sentence> sentences, String outputFilename, String outputDirname) throws IOException {
		// Store the offsets for each of the titles
		int count = 0;
		BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(outputFilename), "UTF8"));
		for (Sentence sentence : sentences) {
			if (count % 1000 == 0)
				System.out.println(count);
			int offset = ((SentenceWithOffset) sentence).getOffset();

			for (Mention mention : sentence.getMentions()) {
				int startChar = offset + mention.getStartChar();
				int endChar = offset + mention.getEndChar();
				writer.write(sentence.getDocumentId() + "\t" + startChar + "\t" + endChar + "\t" + mention.getText() + "\tChemical\tUnknown");
				writer.newLine();
			}
			count++;
		}
		writer.close();
	}

	// private static void output(List<Sentence> sentences, String outputFilename, String outputDirname) throws IOException {
	// // Store the offsets for each of the titles
	// Map<String, Integer> titleOffset = new HashMap<String, Integer>();
	// for (Sentence sentence : sentences) {
	// int offset = ((SentenceWithOffset) sentence).getOffset();
	// if (sentence.getSentenceId().endsWith("-01")) {
	// titleOffset.put(sentence.getDocumentId(), offset);
	// }
	// }
	// Map<String, List<String>> output = new HashMap<String, List<String>>();
	// int count = 0;
	// BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(outputFilename), "UTF8"));
	// for (Sentence sentence : sentences) {
	// if (count % 1000 == 0)
	// System.out.println(count);
	// int offset = ((SentenceWithOffset) sentence).getOffset();
	//
	// boolean isTitle = sentence.getSentenceId().endsWith("-00");
	// String filename = sentence.getDocumentId();
	// if (isTitle) {
	// filename += "_T";
	// } else {
	// filename += "_A";
	// }
	// List<String> outputLines = output.get(filename);
	// if (outputLines == null) {
	// outputLines = new ArrayList<String>();
	// output.put(filename, outputLines);
	// }
	// for (Mention mention : sentence.getMentions()) {
	// int startChar = offset + mention.getStartChar();
	// int endChar = offset + mention.getEndChar();
	// if (!isTitle) {
	// int firstAbstractOffset = titleOffset.get(sentence.getDocumentId());
	// startChar -= firstAbstractOffset;
	// endChar -= firstAbstractOffset;
	// }
	// writer.write(sentence.getDocumentId() + "\t" + (isTitle ? "T\t" : "A\t") + startChar + "\t" + endChar + "\t" + mention.getText());
	// writer.newLine();
	// outputLines.add("\t" + startChar + "\t" + endChar + "\t" + mention.getText());
	// }
	// count++;
	// }
	// writer.close();
	//
	// for (String filename : output.keySet()) {
	// writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(outputDirname + filename + ".txt.chem"), "UTF8"));
	//
	// List<String> outputLines = output.get(filename);
	// for (String line : outputLines) {
	// writer.write(line);
	// writer.newLine();
	// }
	// writer.close();
	// }
	// }

	private static List<Sentence> consistency(List<Sentence> sentences, int countThreshold) {
		// Get counts
		Map<String, TObjectIntMap<String>> documentIdMentionCount = new HashMap<String, TObjectIntMap<String>>();
		for (Sentence sentence : sentences) {
			String documentId = sentence.getDocumentId();
			TObjectIntMap<String> mentionCount = documentIdMentionCount.get(documentId);
			if (mentionCount == null) {
				mentionCount = new TObjectIntHashMap<String>();
				documentIdMentionCount.put(documentId, mentionCount);
			}
			for (Mention mention : sentence.getMentions()) {
				mentionCount.adjustOrPutValue(mention.getText(), 1, 1);
			}
		}

		// For each sentence add mentions not already present for counts > count
		for (Sentence sentence : sentences) {
			String documentId = sentence.getDocumentId();
			TObjectIntMap<String> mentionCount = documentIdMentionCount.get(documentId);
			if (mentionCount != null) {
				for (String mentionText : mentionCount.keySet()) {
					if (mentionCount.get(mentionText) >= countThreshold) {
						String pattern = "\\b" + Pattern.quote(mentionText) + "\\b";
						Pattern mentionPattern = Pattern.compile(pattern);
						Matcher textMatcher = mentionPattern.matcher(sentence.getText());
						while (textMatcher.find()) {
							// TODO Add the mention found
							System.out.println("\tCONSIST ADDED: " + sentence.getDocumentId() + "|" + textMatcher.start() + "|" + textMatcher.end() + "|" + mentionText);
							int tagstart = sentence.getTokenIndexStart(textMatcher.start());
							int tagend = sentence.getTokenIndexEnd(textMatcher.end());
							if (tagstart < 0 || tagend < 0) {
								System.out.println("WARNING: Mention ignored");
							} else {
								Mention mention = new Mention(sentence, tagstart, tagend + 1, EntityType.getType("CHEMICAL"), MentionType.Found);
								// if (!sentence.getMentions().contains(mention))
								if (!overlaps(mention, sentence.getMentions()))
									sentence.addMention(mention);
							}
						}
					}
				}
			}
		}
		return sentences;
	}

	private static boolean overlaps(Mention mention, List<Mention> mentions) {
		for (Mention mention2 : mentions) {
			if (mention2.overlaps(mention)) {
				return true;
			}
		}
		return false;
	}

	private static void normalize(List<Sentence> sentences) throws IOException {
		Map<String, String> dict = loadDictionary("data/dict.txt"); // FIXME Make configurable
		for (Sentence s : sentences) {
			SentenceWithOffset so = (SentenceWithOffset) s;
			for (Mention m : s.getMentions()) {
				String mentionText = m.getText();
				String processedText = mentionText.replaceAll("[^A-Za-z0-9]", "");
				String conceptId = dict.get(processedText);
				if (conceptId == null) {
					conceptId = "-1";
				}
				m.setConceptId(conceptId);
			}
		}
	}

	private static Map<String, String> loadDictionary(String filename) throws IOException {
		Map<String, String> dict = new HashMap<String, String>();
		BufferedReader reader = null;
		try {
			reader = new BufferedReader(new FileReader(filename));
			String line = reader.readLine();
			while (line != null) {
				String[] fields = line.split("\t");
				String text = fields[0];
				String conceptId = fields[1];
				dict.put(text, conceptId);
				line = reader.readLine();
			}
		} finally {
			if (reader != null) {
				reader.close();
			}
		}
		return dict;
	}
}
