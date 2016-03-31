package dnorm;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
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

import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.HierarchicalConfiguration;
import org.apache.commons.configuration.XMLConfiguration;

import dnorm.core.DiseaseNameAnalyzer;
import dnorm.core.Lexicon;
import dnorm.core.MEDICLexiconLoader;
import dnorm.core.SynonymTrainer;
import dnorm.core.SynonymTrainer.LookupResult;
import dnorm.types.Dictionary;
import dnorm.types.FullRankSynonymMatrix;
import dnorm.util.AbbreviationIdentifier;
import dnorm.util.AbbreviationResolver;
import dnorm.util.PubtatorReader;
import dnorm.util.PubtatorReader.Abstract;
import dnorm.util.PubtatorReader.Tag;
import dragon.nlp.tool.Tagger;
import dragon.nlp.tool.lemmatiser.EngLemmatiser;
import banner.eval.BANNER;
import banner.postprocessing.PostProcessor;
import banner.tagging.CRFTagger;
import banner.tagging.dictionary.DictionaryTagger;
import banner.tokenization.Tokenizer;
import banner.types.Mention;
import banner.types.Sentence;
import banner.types.Mention.MentionType;
import banner.util.RankedList;
import dragon.util.EnvVariable;

public class ApplyDNormOnly {

	// TODO Re-integrate abbreviation resolution

	private static AbbreviationIdentifier abbrev;
	// private static CRFTagger tagger;
	// private static Tokenizer tokenizer;
	// private static PostProcessor postProcessor;
	private static SynonymTrainer syn;

	public static void main(String[] args) throws ConfigurationException, IOException {
		// String configurationFilename = args[0];
		String lexiconFilename = args[1];
		String matrixFilename = args[2];
		String abbreviationDirectory = args[3];
		String tempDirectory = args[4];
		String inputFilename = args[5];
		String outputFilename = args[6];

		FullRankSynonymMatrix matrix = FullRankSynonymMatrix.load(new File(matrixFilename));
		Dictionary dict = matrix.getDictionary();
		DiseaseNameAnalyzer analyzer = DiseaseNameAnalyzer.getDiseaseNameAnalyzer(true, true, false, true);
		Lexicon lex = new Lexicon(analyzer, dict);
		MEDICLexiconLoader loader = new MEDICLexiconLoader();
		loader.loadLexicon(lex, lexiconFilename);
		lex.prepare();

		syn = new SynonymTrainer(lex, matrix, 1000);

		System.out.println("Reading input");
		PubtatorReader reader = new PubtatorReader(inputFilename);
		System.out.println("Processing & writing output");

		abbrev = new AbbreviationIdentifier("./identify_abbr", abbreviationDirectory, tempDirectory, 1000);

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
			List<DNormResult> results = process(a);
			Collections.sort(results, new Comparator<DNormResult>() {
				@Override
				public int compare(DNormResult r1, DNormResult r2) {
					return r1.getStartChar() - r2.getStartChar();
				}
			});
			for (DNormResult r : results) {
				writer.write(a.getId() + "\t" + r.getStartChar() + "\t" + r.getEndChar() + "\t" + r.getMentionText() + "\tDisease");
				if (r.getConceptID() != null) {
					writer.write("\t" + r.getConceptID());
				}
				writer.newLine();
			}
			writer.newLine();
		}
		writer.close();
		System.out.println("Done.");
	}

	private static List<DNormResult> process(Abstract a) throws IOException {
		String text = a.getText();
		System.out.println("Text received: " + text);
		if (text == null)
			return new ArrayList<DNormResult>();
		Map<String, String> abbreviationMap = abbrev.getAbbreviations(a.getId(), text);
		List<DNormResult> found = processText(a, abbreviationMap);
		System.out.println("Mentions found:");
		for (DNormResult result : found)
			System.out.println("\t" + result.toString());
		if (abbreviationMap == null)
			return found;
		List<DNormResult> returned = extendResults(text, found, abbreviationMap);
		System.out.println("Mentions added:");
		List<DNormResult> added = new ArrayList<DNormResult>(returned);
		added.removeAll(found);
		for (DNormResult result : added)
			System.out.println("\t" + result.toString());
		System.out.println("Mentions removed:");
		List<DNormResult> removed = new ArrayList<DNormResult>(found);
		removed.removeAll(returned);
		for (DNormResult result : removed)
			System.out.println("\t" + result.toString());
		return returned;
	}

	private static List<DNormResult> processText(Abstract a, Map<String, String> abbreviationMap) {
		List<DNormResult> results = new ArrayList<DNormResult>();
		List<Tag> tags = a.getTags();
		for (Tag t : tags) {
			// Use the mentions from the file
			int start = t.start; 
			int end = t.end; 
			String lookupText = a.getSubText(start, end);
			DNormResult result = new DNormResult(start, end, lookupText);
			// Use the concept ID from DNorm lookup
			lookupText = AbbreviationResolver.expandAbbreviations(lookupText, abbreviationMap);
			RankedList<LookupResult> lookup = syn.lookup(lookupText);
			if (lookup.size() > 0) {
				result.setConceptID(lookup.getObject(0).getConceptId(), lookup.getValue(0));
			}
			results.add(result);
		}
		return results;
	}
	
	// private static List<DNormResult> processTextBANNER(Abstract a, Map<String, String> abbreviationMap) {
	// 	List<DNormResult> results = new ArrayList<DNormResult>();
	// 	int index = 0;
	// 	List<String> sentences = a.getSentenceTexts();
	// 	for (int i = 0; i < sentences.size(); i++) {
	// 		String sentence = sentences.get(i);
	// 		int length = sentence.length();
	// 		sentence = sentence.trim();
	// 		Sentence sentence1 = new Sentence(a.getId() + "-" + i, a.getId(), sentence);
	// 		Sentence sentence2 = BANNER.process(tagger, tokenizer, postProcessor, sentence1);
	// 		for (Mention mention : sentence2.getMentions(MentionType.Found)) {
	// 			int start = index + mention.getStartChar();
	// 			int end = start + mention.getText().length();
	// 			DNormResult result = new DNormResult(start, end, mention.getText());
	// 			String lookupText = result.getMentionText();
	// 			lookupText = AbbreviationResolver.expandAbbreviations(lookupText, abbreviationMap);
	// 			RankedList<LookupResult> lookup = syn.lookup(lookupText);
	// 			if (lookup.size() > 0) {
	// 				result.setConceptID(lookup.getObject(0).getConceptId(), lookup.getValue(0));
	// 			}
	// 			results.add(result);
	// 		}
	// 		index += length;
	// 	}
	// 	return results;
	// }

	private static List<DNormResult> extendResults(String text, List<DNormResult> results, Map<String, String> shortLongAbbrevMap) {
		// Get long->short map
		Map<String, String> longShortAbbrevMap = new HashMap<String, String>();
		for (String shortText : shortLongAbbrevMap.keySet()) {
			String longText = shortLongAbbrevMap.get(shortText);
			longShortAbbrevMap.put(longText, shortText);
		}

		// Create a set of strings to be set as results
		Set<DNormResult> unlocalizedResults = new HashSet<DNormResult>();
		for (DNormResult result : results) {
			if (result.getConceptID() != null) {
				unlocalizedResults.add(new DNormResult(-1, -1, result.getMentionText(), result.getConceptID(), result.getScore()));
				if (shortLongAbbrevMap.containsKey(result.getMentionText())) {
					String mentionText = shortLongAbbrevMap.get(result.getMentionText());
					// TODO Verify mentionText realistically normalizes to the concept intended, or we will drop the original result
					unlocalizedResults.add(new DNormResult(-1, -1, mentionText, result.getConceptID(), result.getScore()));
				}
				if (longShortAbbrevMap.containsKey(result.getMentionText())) {
					String mentionText = longShortAbbrevMap.get(result.getMentionText());
					unlocalizedResults.add(new DNormResult(-1, -1, mentionText, result.getConceptID(), result.getScore()));
				}
			}
		}

		return localizeResults(text, unlocalizedResults);
	}

	private static List<DNormResult> localizeResults(String text, Set<DNormResult> unlocalizedResults) {
		// Add a result for each instance of a mention found
		List<DNormResult> newResults = new ArrayList<DNormResult>();
		for (DNormResult result : unlocalizedResults) {
			String pattern = "\\b" + Pattern.quote(result.getMentionText()) + "\\b";
			Pattern mentionPattern = Pattern.compile(pattern);
			Matcher textMatcher = mentionPattern.matcher(text);
			while (textMatcher.find()) {
				newResults.add(new DNormResult(textMatcher.start(), textMatcher.end(), result.getMentionText(), result.getConceptID(), result.getScore()));
			}
		}

		// If two results overlap, remove the shorter of the two
		List<DNormResult> newResults2 = new ArrayList<DNormResult>();
		for (int i = 0; i < newResults.size(); i++) {
			DNormResult result1 = newResults.get(i);
			boolean add = true;
			for (int j = 0; j < newResults.size() && add; j++) {
				DNormResult result2 = newResults.get(j);
				if (i != j && result1.overlaps(result2) && result2.getMentionText().length() > result1.getMentionText().length())
					add = false;
			}
			if (add)
				newResults2.add(result1);
		}
		return newResults2;
	}

	private static class DNormResult {
		private int startChar;
		private int endChar;
		private String mentionText;
		private String conceptID;
		private double score;

		public DNormResult(int startChar, int endChar, String mentionText) {
			this.startChar = startChar;
			this.endChar = endChar;
			this.mentionText = mentionText;
		}

		public DNormResult(int startChar, int endChar, String mentionText, String conceptID, double score) {
			this.startChar = startChar;
			this.endChar = endChar;
			this.mentionText = mentionText;
			this.conceptID = conceptID;
			this.score = score;
		}

		public String getConceptID() {
			return conceptID;
		}

		public void setConceptID(String conceptID, double score) {
			this.conceptID = conceptID;
			this.score = score;
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

		public double getScore() {
			return score;
		}

		public boolean overlaps(DNormResult result) {
			return endChar > result.startChar && startChar < result.endChar;
		}

		@Override
		public String toString() {
			return mentionText + " (" + startChar + ", " + endChar + ") -> " + conceptID + " @ " + score;
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + ((conceptID == null) ? 0 : conceptID.hashCode());
			result = prime * result + endChar;
			result = prime * result + ((mentionText == null) ? 0 : mentionText.hashCode());
			long temp;
			temp = Double.doubleToLongBits(score);
			result = prime * result + (int) (temp ^ (temp >>> 32));
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
			DNormResult other = (DNormResult) obj;
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
			if (Double.doubleToLongBits(score) != Double.doubleToLongBits(other.score))
				return false;
			if (startChar != other.startChar)
				return false;
			return true;
		}
	}
}
