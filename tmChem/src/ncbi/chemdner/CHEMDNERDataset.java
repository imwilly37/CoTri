package ncbi.chemdner;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Stack;

import org.apache.commons.configuration.HierarchicalConfiguration;
import org.apache.commons.lang.NotImplementedException;

import banner.eval.dataset.Dataset;
import banner.tokenization.FineUnicodeTokenizer;
import banner.types.EntityType;
import banner.types.Mention;
import banner.types.SentenceWithOffset;
import banner.types.Token;
import banner.types.Mention.MentionType;
import banner.types.Sentence;
import banner.util.SentenceBreaker;

public class CHEMDNERDataset extends Dataset {

	private SentenceBreaker sb;

	public CHEMDNERDataset() {
		sb = new SentenceBreaker();
	}

	@Override
	public void load(HierarchicalConfiguration config) {
		HierarchicalConfiguration localConfig = config.configurationAt(this.getClass().getPackage().getName());
		String abstractsFilename = localConfig.getString("abstractsFilename");
		String annotationsFilename = localConfig.getString("annotationsFilename");
		String pmidsFilename = localConfig.getString("pmidsFilename");

		// TODO Add the type mappings (eg TRIVIAL --> CHEMICAL or whatever)
		try {
			Set<String> pmids = getPMIDs(pmidsFilename);
			Map<String, Abstract> abstracts = loadAbstracts(abstractsFilename, pmids);
			loadAnnotations(annotationsFilename, abstracts);
			processAbstracts(abstracts);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	private Map<String, Abstract> loadAbstracts(String abstractsFilename, Set<String> pmids) throws IOException {
		Map<String, Abstract> abstracts = new LinkedHashMap<String, Abstract>();
		BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(abstractsFilename), "UTF8"));
		try {
			String line = reader.readLine();
			while (line != null) {
				line = line.trim();
				if (line.length() > 0) {
					String[] split = line.split("\\t");
					String documentId = split[0].trim();
					String titleText = split[1].trim();
					String abstractText = split[2];
					if (pmids.contains(documentId)) {
						pmids.remove(documentId);
						if (abstracts.containsKey(documentId))
							throw new IllegalArgumentException("Duplicate abstract " + documentId);
						Abstract a = new Abstract();
						a.setId(documentId);
						a.setTitleText(titleText);
						a.setAbstractText(abstractText);
						abstracts.put(documentId, a);
					}
				}
				line = reader.readLine();
			}
		} finally {
			reader.close();
		}
		System.out.println("Loaded " + abstracts.size() + " abstracts");
		if (pmids.size() > 0) {
			throw new IllegalStateException("Still expecting " + pmids.size() + " document IDs");
		}
		return abstracts;
	}

	private void loadAnnotations(String annotationsFilename, Map<String, Abstract> abstracts) throws IOException {
		int count = 0;
		BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(annotationsFilename), "UTF8"));
		try {
			String line = reader.readLine();
			while (line != null) {
				line = line.trim();
				if (line.length() > 0) {
					String[] split = line.split("\\t");
					String documentId = split[0].trim();
					String textField = split[1].trim();
					if (!textField.equals("T") && !textField.equals("A")) {
						throw new IllegalArgumentException("Unknown text field: " + textField);
					}
					int mentionStart = Integer.parseInt(split[2].trim());
					int mentionEnd = Integer.parseInt(split[3].trim());
					String mentionText = split[4].trim();
					String typeText = split[5].toUpperCase().trim();

					// EntityType mentionType = EntityType.getType(typeText);
					EntityType mentionType = EntityType.getType("CHEMICAL"); // TODO Currently only allows one entity type

					// EntityType mentionType = EntityType.getType("CHEM_FLEN");
					// if (typeText.equals("FORMULA") || typeText.equals("SYSTEMATIC")) {
					// mentionType = EntityType.getType("CHEM_EXTN");
					// }

					Abstract a = abstracts.get(documentId);
					if (a != null) {
						// Add length of title to start & end if type is "A"
						if (textField.equals("A")) {
							int titleLength = a.getSentenceTexts().get(0).length();
							mentionStart += titleLength;
							mentionEnd += titleLength;
						}
						if (a.isSplitAcrossSentences(mentionStart, mentionEnd)) {
							System.out.println("WARNING: Annotation in " + documentId + " split across sentences");
						} else {
							// Verify text matches
							String mentionTextFromAbstract = a.getSubText(mentionStart, mentionEnd);
							if (!mentionText.equals(mentionTextFromAbstract)) {
								throw new IllegalArgumentException("Mention text (" + mentionText + ") does not match mention text in abstract (" + mentionTextFromAbstract + ")");
							}
							Tag t = new Tag(mentionType, mentionStart, mentionEnd);
							a.addTag(t);
							count++;
						}
					}
				}
				line = reader.readLine();
			}
			System.out.println("Loaded " + count + " annotations");
		} finally {
			reader.close();
		}
	}

	private void processAbstracts(Map<String, Abstract> abstracts) {
		int tokenCount = 0;
		for (String documentId : abstracts.keySet()) {
			Abstract a = abstracts.get(documentId);
			int start = 0;
			int end = 0;
			List<Tag> tags = a.getTags();
			List<String> sentenceTexts = a.getSentenceTexts();
			for (int i = 0; i < sentenceTexts.size(); i++) {
				String sentenceText = sentenceTexts.get(i);
				String sentenceId = Integer.toString(i);
				if (sentenceId.length() < 2)
					sentenceId = "0" + sentenceId;
				sentenceId = a.getId() + "-" + sentenceId;
				Sentence s = new SentenceWithOffset(sentenceId, a.getId(), sentenceText, start);
				// System.out.println(a.getId() + "\t" + sentenceText);
				tokenizer.tokenize(s);
				// Add mentions
				end += sentenceText.length();
				for (Tag tag : new ArrayList<Tag>(tags)) {
					if (tag.start >= start && tag.end <= end) {
						// TODO Verify that the match between the mentions and the tokens is acceptable
						int tagstart = s.getTokenIndexStart(tag.start - start);
						int tagend = s.getTokenIndexEnd(tag.end - start);
						if (tagstart < 0 || tagend < 0) {
							System.out.print("WARNING: Annotation in " + sentenceId + " ignored for tokenization error: ");
							int st = tag.start - start;
							int ws = Math.max(st - 5, 0);
							int en = tag.end - start;
							int we = Math.min(en + 5, sentenceText.length());
							System.out.print("\"");
							System.out.print(sentenceText.substring(ws, st));
							System.out.print("|");
							System.out.print(sentenceText.substring(st, en));
							System.out.print("|");
							System.out.print(sentenceText.substring(en, we));
							System.out.print("\"");
							System.out.println();
						} else {
							tagend += 1; // this side is exclusive
							EntityType type = tag.type;
							Mention mention = new Mention(s, tagstart, tagend, type, MentionType.Required);
							if (!mention.getText().equals(sentenceText.substring(tag.start - start, tag.end - start)))
								throw new IllegalArgumentException();
							// System.out.println("\t" + mention.getText() + "\t" + tagstart + "\t" + tagend + "\t" + sentenceText.substring(tag.start - start, tag.end - start));
							Set<String> ids = tag.getIds();
							if (ids.size() > 1)
								throw new IllegalArgumentException();
							for (String conceptId : ids)
								mention.setConceptId(conceptId);
							s.addMention(mention);
						}
						tags.remove(tag);
					}
				}
				tokenCount += s.getTokens().size();
				// System.out.println();
				sentences.add(s);
				start += sentenceText.length();
			}
		}
		System.out.println("Added " + sentences.size() + " sentences, with a total of " + tokenCount + " tokens.");
	}

	private Set<String> getPMIDs(String filename) throws IOException {
		Set<String> pmids = new HashSet<String>();
		BufferedReader dataFile = new BufferedReader(new FileReader(filename));
		String line = dataFile.readLine();
		while (line != null) {
			line = line.trim();
			if (line.length() > 0)
				pmids.add(line);
			line = dataFile.readLine();
		}
		dataFile.close();
		return pmids;
	}

	@Override
	public List<Dataset> split(int n) {
		throw new NotImplementedException();
	}

	private class Abstract {
		private String id;
		private List<Tag> tags;
		private List<String> sentenceTexts;

		public Abstract() {
			// Empty
			tags = new ArrayList<Dataset.Tag>();
			sentenceTexts = new ArrayList<String>();
		}

		public String getId() {
			return id;
		}

		public void setId(String id) {
			this.id = id;
		}

		public void setTitleText(String titleText) {
			sentenceTexts.add(titleText);
		}

		public void setAbstractText(String abstractText) {
			sb.setText(abstractText);
			sentenceTexts.addAll(sb.getSentences());
		}

		public List<Tag> getTags() {
			return tags;
		}

		public void addTag(Tag tag) {
			tags.add(tag);
		}

		public String getSubText(int start, int end) {
			for (int i = 0; i < sentenceTexts.size(); i++) {
				String s = sentenceTexts.get(i);
				int length = s.length();
				if (end <= length) {
					return s.substring(start, end);
				}
				start -= s.length();
				end -= s.length();
			}
			return null;
		}

		public boolean isSplitAcrossSentences(int start, int end) {
			for (int i = 0; i < sentenceTexts.size(); i++) {
				String s = sentenceTexts.get(i);
				int length = s.length();
				if (end <= length) {
					return start < 0;
				}
				start -= s.length();
				end -= s.length();
			}
			return true;
		}

		public List<String> getSentenceTexts() {
			return sentenceTexts;
		}
	}

	// public static class SentenceWithOffset extends Sentence {
	//
	// private int offset;
	//
	// public SentenceWithOffset(String sentenceId, String documentId, String text, int offset) {
	// super(sentenceId, documentId, text);
	// this.offset = offset;
	// }
	//
	// public int getOffset() {
	// return offset;
	// }
	//
	// @Override
	// public Sentence copy(boolean includeTokens, boolean includeMentions) {
	// Sentence sentence2 = new SentenceWithOffset(getSentenceId(), getDocumentId(), getText(), offset);
	// if (includeTokens) {
	// for (Token token : getTokens())
	// sentence2.addToken(new Token(sentence2, token.getStart(), token.getEnd()));
	// }
	// if (includeMentions) {
	// for (Mention mention : getMentions())
	// sentence2.addMention(mention.copy(sentence2));
	// }
	// return sentence2;
	// }
	// }

	public static boolean isBalanced(String s) {
		Stack<Character> stack = new Stack<Character>();
		for (int i = 0; i < s.length(); i++) {
			if (s.charAt(i) == '(')
				stack.push('(');
			else if (s.charAt(i) == '{')
				stack.push('{');
			else if (s.charAt(i) == '[')
				stack.push('[');
			else if (s.charAt(i) == ')') {
				if (stack.isEmpty())
					return false;
				if (stack.pop() != '(')
					return false;
			} else if (s.charAt(i) == '}') {
				if (stack.isEmpty())
					return false;
				if (stack.pop() != '{')
					return false;
			} else if (s.charAt(i) == ']') {
				if (stack.isEmpty())
					return false;
				if (stack.pop() != '[')
					return false;
			}
			// ignore all other characters
		}
		return stack.isEmpty();
	}

	public static void main(String[] args) {
		HierarchicalConfiguration config = new HierarchicalConfiguration();
		config.setProperty("ncbi.chemdner.abstractsFilename", "data/chemdner_abs.txt");
		config.setProperty("ncbi.chemdner.annotationsFilename", "data/chemdner_ann.txt");
		config.setProperty("ncbi.chemdner.pmidsFilename", "data/Train.pmids.txt");
		// config.setProperty("banner.eval.dataset.pmidsFilename", "data/Dev.pmids.txt");
		CHEMDNERDataset d = new CHEMDNERDataset();
		d.setTokenizer(new FineUnicodeTokenizer());
		d.load(config);

		Set<Sentence> sentences = d.getSentences();
		for (Sentence s : sentences) {
			List<Mention> mentions = s.getMentions();
			for (int i = 0; i < mentions.size(); i++) {
				Mention m1 = mentions.get(i);
				if (!isBalanced(m1.getText())) {
					System.out.println("WARNING Unbalanced mention in sentence " + s.getSentenceId() + ": " + m1.getText());
				}
				for (int j = i + 1; j < mentions.size(); j++) {
					Mention m2 = mentions.get(j);
					if (m1.overlaps(m2)) {
						if (m1.getEntityType().equals(m2.getEntityType())) {
							System.out.println("INFO: Overlapping mentions of same type in sentence " + s.getSentenceId());
						} else {
							System.out.println("ERROR: Overlapping mentions of different types in sentence " + s.getSentenceId());
						}
					}
				}
			}
		}
	}
}
