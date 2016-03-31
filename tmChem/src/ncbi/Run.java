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

import javax.xml.stream.XMLStreamException;

import ncbi.PubtatorReader.Abstract;
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
import bioc.BioCAnnotation;
import bioc.BioCCollection;
import bioc.BioCDocument;
import bioc.BioCPassage;
import bioc.io.BioCDocumentWriter;
import bioc.io.BioCFactory;
import bioc.io.woodstox.ConnectorWoodstox;
import dragon.nlp.tool.Tagger;
import dragon.nlp.tool.lemmatiser.EngLemmatiser;

public class Run {

	public static AbbreviationIdentifier abbrev;
	public static Tokenizer tokenizer;
	public static PostProcessor postProcessor;
	public static CRFTagger tagger;
	public static SentenceBreaker breaker;
	public static Map<String, String> dict;

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
		tokenizer = BANNER.getTokenizer(config);
		postProcessor = new ParenthesisBalancingPostProcessor();

		HierarchicalConfiguration localConfig = config.configurationAt(BANNER.class.getPackage().getName());
		String modelFilename = localConfig.getString("modelFilename");
		System.out.println("modelFilename=" + modelFilename);

		tagger = CRFTagger.load(new File(modelFilename), lemmatiser, posTagger, null);

		abbrev = new AbbreviationIdentifier("./identify_abbr", abbreviationDirectory, tempDirectory, 1000);
		breaker = new SentenceBreaker();
		dict = loadDictionary(dictionaryFilename);

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

			List<String> reportFilenames = getUnlockedFiles(input);
			for (String filename : reportFilenames) {
				String reportFilename = input + filename;
				String annotationFilename = output + filename;
				if (filename.endsWith(".xml")) {
					processFile_BioC(reportFilename, annotationFilename);
				} else {
					processFile_PubTator(reportFilename, annotationFilename);
				}
			}
		} else {
			if (outFile.isDirectory()) {
				usage();
				throw new IllegalArgumentException();
			}
			if (input.endsWith(".xml")) {
				processFile_BioC(input, output);
			} else {
				processFile_PubTator(input, output);
			}
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
		System.out.println("\tRun configurationFilename dictionaryFilename Ab3P_Directory tempDirectory input output");
	}

	public static void processFile_PubTator(String inputFilename, String outputFilename) throws IOException {
		System.out.println("Reading input");
		PubtatorReader reader = new PubtatorReader(inputFilename);
		System.out.println("Processing & writing output");

		BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(outputFilename), "UTF-8"));
		for (Abstract a : reader.getAbstracts()) {
			writer.write(a.getId() + "|t|");
			if (a.getTitleText() != null)
				writer.write(a.getTitleText());
			writer.newLine();
			writer.write(a.getId() + "|a|");
			if (a.getAbstractText() != null)
				writer.write(a.getAbstractText());
			writer.newLine();
			List<TmChemResult> results = process(a);
			Collections.sort(results, new Comparator<TmChemResult>() {
				@Override
				public int compare(TmChemResult r1, TmChemResult r2) {
					return r1.getStartChar() - r2.getStartChar();
				}
			});
			for (TmChemResult r : results) {
				writer.write(a.getId() + "\t" + r.getStartChar() + "\t" + r.getEndChar() + "\t" + r.getMentionText() + "\tChemical");
				if (r.getConceptID() != null) {
					writer.write("\t" + r.getConceptID());
				}
				writer.newLine();
			}
			writer.newLine();
		}
		writer.close();
	}

	private static List<TmChemResult> process(Abstract a) throws IOException {
		String text = a.getText();
		System.out.println("Text received: " + text);
		if (text == null)
			return new ArrayList<TmChemResult>();
		Map<String, String> abbreviationMap = abbrev.getAbbreviations(a.getId(), text);
		List<TmChemResult> found = processText(a, abbreviationMap);
		System.out.println("Mentions found:");
		for (TmChemResult result : found)
			System.out.println("\t" + result.toString());
		if (abbreviationMap == null)
			return found;
		// FIXME Consistency
		// FIXME Abbreviation
		return found;
	}

	private static List<TmChemResult> processText(Abstract a, Map<String, String> abbreviationMap) {
		List<TmChemResult> results = new ArrayList<TmChemResult>();
		int index = 0;
		List<String> sentences = a.getSentenceTexts();
		for (int i = 0; i < sentences.size(); i++) {
			String sentence = sentences.get(i);
			int length = sentence.length();
			sentence = sentence.trim();
			Sentence sentence1 = new Sentence(a.getId() + "-" + i, a.getId(), sentence);
			Sentence sentence2 = BANNER.process(tagger, tokenizer, postProcessor, sentence1);
			for (Mention mention : sentence2.getMentions(MentionType.Found)) {
				int start = index + mention.getStartChar();
				int end = start + mention.getText().length();
				TmChemResult result = new TmChemResult(start, end, mention.getText());
				String lookupText = result.getMentionText();
				lookupText = expandAbbreviations(lookupText, abbreviationMap);
				String conceptId = normalize(lookupText);
				result.setConceptID(conceptId);
				results.add(result);
			}
			index += length;
		}
		return results;
	}

	private static String expandAbbreviations(String lookupText, Map<String, String> abbreviationMap) {
		if (abbreviationMap == null)
			return lookupText;
		for (String abbreviation : abbreviationMap.keySet()) {
			if (lookupText.contains(abbreviation)) {
				String replacement = abbreviationMap.get(abbreviation);
				String updated = null;
				if (lookupText.contains(replacement)) {
					// Handles mentions like "von Hippel-Lindau (VHL) disease"
					updated = lookupText.replaceAll("\\(?\\b" + abbreviation + "\\b\\)?", "");
				} else {
					updated = lookupText.replaceAll("\\(?\\b" + abbreviation + "\\b\\)?", replacement);
				}
				if (!updated.equals(lookupText)) {
					// System.out.println("Before:\t" + lookupText);
					// System.out.println("After :\t" + updated);
					// System.out.println();
					lookupText = updated;
				}
			}
		}
		return lookupText;
	}

	private static void processAbstract(HierarchicalConfiguration config, String abbreviationFilename, String outputFilename, String outputDirname) {
		Dataset dataset = BANNER.getDataset(config);
		List<Sentence> sentences = new ArrayList<Sentence>(dataset.getSentences());

		changeType(sentences);
		Collections.sort(sentences, new Comparator<Sentence>() {
			@Override
			public int compare(Sentence s1, Sentence s2) {
				return s1.getSentenceId().compareTo(s2.getSentenceId());
			}
		});

		try {

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

	private static void normalize(List<Sentence> sentences) {
		for (Sentence s : sentences) {
			for (Mention m : s.getMentions()) {
				String mentionText = m.getText();
				String conceptId = normalize(mentionText);
				m.setConceptId(conceptId);
			}
		}
	}

	private static String normalize(String mentionText) {
		String processedText = mentionText.replaceAll("[^A-Za-z0-9]", "").toLowerCase();
		String conceptId = dict.get(processedText);
		if (conceptId == null) {
			conceptId = "-1";
		}
		return conceptId;
	}

	public static Map<String, String> loadDictionary(String filename) throws IOException {
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

	public static void processFile_BioC(String inXML, String outXML) throws IOException, XMLStreamException {
		ConnectorWoodstox connector = new ConnectorWoodstox();
		BioCCollection collection = connector.startRead(new InputStreamReader(new FileInputStream(inXML), "UTF-8"));
		String parser = BioCFactory.WOODSTOX;
		BioCFactory factory = BioCFactory.newFactory(parser);
		BioCDocumentWriter writer = factory.createBioCDocumentWriter(new OutputStreamWriter(new FileOutputStream(outXML), "UTF-8"));
		writer.writeCollectionInfo(collection);
		while (connector.hasNext()) {
			BioCDocument document = connector.next();
			String documentId = document.getID();
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
					String lookupText = expandAbbreviations(mentionText, abbreviationMap);
					String conceptId = normalize(lookupText);
					String id = "id";
					int index = conceptId.indexOf(":");
					if (index != -1) {
						id = conceptId.substring(0, index);
						conceptId = conceptId.substring(index + 1);
					}
					annotation.putInfon(id, conceptId);
					passage.addAnnotation(annotation);
					// FIXME Consistency
					// FIXME Abbreviation
				}
			}
			offset += sentenceText.length();
		}
	}

	private static class TmChemResult {
		private int startChar;
		private int endChar;
		private String mentionText;
		private String conceptID;

		public TmChemResult(int startChar, int endChar, String mentionText) {
			this.startChar = startChar;
			this.endChar = endChar;
			this.mentionText = mentionText;
		}

		public TmChemResult(int startChar, int endChar, String mentionText, String conceptID) {
			this.startChar = startChar;
			this.endChar = endChar;
			this.mentionText = mentionText;
			this.conceptID = conceptID;
		}

		public String getConceptID() {
			return conceptID;
		}

		public void setConceptID(String conceptID) {
			this.conceptID = conceptID;
		}

		public int getStartChar() {
			return startChar;
		}

		public int getEndChar() {
			return endChar;
		}

		public String getMentionText() {
			return mentionText;
		}

		public boolean overlaps(TmChemResult result) {
			return endChar > result.startChar && startChar < result.endChar;
		}

		@Override
		public String toString() {
			return mentionText + " (" + startChar + ", " + endChar + ") -> " + conceptID;
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + ((conceptID == null) ? 0 : conceptID.hashCode());
			result = prime * result + endChar;
			result = prime * result + ((mentionText == null) ? 0 : mentionText.hashCode());
			result = prime * result + startChar;
			return result;
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (getClass() != obj.getClass())
				return false;
			TmChemResult other = (TmChemResult) obj;
			if (conceptID == null) {
				if (other.conceptID != null)
					return false;
			} else if (!conceptID.equals(other.conceptID))
				return false;
			if (endChar != other.endChar)
				return false;
			if (mentionText == null) {
				if (other.mentionText != null)
					return false;
			} else if (!mentionText.equals(other.mentionText))
				return false;
			if (startChar != other.startChar)
				return false;
			return true;
		}
	}
}
