package ncbi;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.commons.configuration.ConfigurationException;

import banner.types.EntityType;
import banner.util.SentenceBreaker;

public class PubtatorReader {

	// TODO Replace this with BANNER's PubtatorDataset
	
	private BufferedReader dataFile;
	private String currentLine;
	private List<Abstract> abstracts;

	public PubtatorReader(String filename) {
		abstracts = new ArrayList<Abstract>();
		try {
			dataFile = new BufferedReader(new InputStreamReader(new FileInputStream(filename), "UTF8"));
			nextLine();
			Abstract a = getAbstract();
			while (a != null) {
				abstracts.add(a);
				a = getAbstract();
			}
			dataFile.close();
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	public PubtatorReader(String filename, String pmidsFilename) {
		abstracts = new ArrayList<Abstract>();
		try {
			Set<String> pmids = getPMIDs(pmidsFilename);
			dataFile = new BufferedReader(new InputStreamReader(new FileInputStream(filename), "UTF8"));
			nextLine();
			Abstract a = getAbstract();
			while (a != null) {
				if (pmids.contains(a.getId()))
					abstracts.add(a);
				a = getAbstract();
			}
			dataFile.close();
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	public static class Abstract {
		private static final SentenceBreaker sb = new SentenceBreaker();

		private String id;
		private String titleText;
		private String abstractText;
		private List<Tag> tags;
		private List<String> sentenceTexts;

		public Abstract() {
			tags = new ArrayList<Tag>();
			sentenceTexts = new ArrayList<String>();
		}

		public String getId() {
			return id;
		}

		public void setId(String id) {
			this.id = id;
		}

		public String getTitleText() {
			return titleText;
		}

		public String getAbstractText() {
			return abstractText;
		}

		public String getText() {
			StringBuilder text = new StringBuilder();
			if (titleText != null)
				text.append(titleText);
			text.append(" ");
			if (abstractText != null)
				text.append(abstractText);
			if (text.length() <= 1)
				return null;
			return text.toString();
		}

		public void setTitleText(String titleText) {
			if (titleText.length() > 0) {
				this.titleText = titleText;
				sentenceTexts.add(titleText + " ");
			}
		}

		public void setAbstractText(String abstractText) {
			if (abstractText.length() > 0) {
				this.abstractText = abstractText;
				sb.setText(abstractText);
				sentenceTexts.addAll(sb.getSentences());
			}
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

		public List<String> getSentenceTexts() {
			return sentenceTexts;
		}
	}

	public List<Abstract> getAbstracts() {
		return abstracts;
	}

	private Abstract getAbstract() throws IOException {
		if (currentLine() == null)
			return null;
		Abstract a = new Abstract();
		getTitleText(a);
		getAbstractText(a);
		Tag t = getTag(a);
		while (t != null) {
			a.addTag(t);
			t = getTag(a);
		}
		return a;
	}

	private void getTitleText(Abstract a) throws IOException {
		String[] split = currentLine().split("\\|");
		if (split.length != 3)
			throw new IllegalArgumentException("Invalid title text=\"" + currentLine() + "\"");
		a.setId(split[0]);
		if (!split[1].equals("t"))
			throw new IllegalArgumentException("Invalid title text=\"" + currentLine() + "\"");
		a.setTitleText(split[2]);
		nextLine();
	}

	private void getAbstractText(Abstract a) throws IOException {
		String[] split = currentLine().split("\\|");
		if (split.length != 3)
			throw new IllegalArgumentException("Invalid abstract text=\"" + currentLine() + "\"");
		if (!split[0].equals(a.getId()))
			throw new IllegalArgumentException("Invalid abstract text=\"" + currentLine() + "\"");
		if (!split[1].equals("a"))
			throw new IllegalArgumentException("Invalid abstract text=\"" + currentLine() + "\"");
		a.setAbstractText(split[2]);
		nextLine();
	}

	private Tag getTag(Abstract a) throws IOException {
		String line = currentLine();
		if (line == null)
			return null;
		String[] split = line.split("\t");
		if (split.length != 5 && split.length != 6)
			return null;
		if (!split[0].equals(a.getId()))
			throw new IllegalArgumentException("1");
		int start = Integer.parseInt(split[1]);
		int end = Integer.parseInt(split[2]);
		String text = a.getSubText(start, end);
		if (!split[3].equals(removePunctuation(text)))
			throw new IllegalArgumentException("Text from mention definition (\"" + split[3] + "\") does not match text specified by mention boundaries (\"" + removePunctuation(text) + "\")");
		if (!text.equals(text.trim()))
			throw new IllegalArgumentException("Mention text cannot begin or end with whitespace (\"" + text + "\")");
		String typeText = split[4];
		EntityType type = EntityType.getType(typeText);
		Tag t = new Tag(type, start, end);
		String conceptId = null;
		if (split.length > 5) {
			conceptId = split[5].trim().replaceAll("\\*", "");
			if (conceptId.length() > 0) {
				t.setId(conceptId);
			} else {
				System.out.println("WARNING: " + a.getId() + " lists no concept for annotation \"" + text + "\"");
			}
		} else {
			System.out.println("WARNING: " + a.getId() + " lists no concept for annotation \"" + text + "\"");
		}
		// TODO How to handle + && | ids
		nextLine();
		return t;
	}

	private String removePunctuation(String text) {
		String remove = "\"";
		StringBuffer sb = new StringBuffer();
		for (int i = 0; i < text.length(); i++) {
			char c = text.charAt(i);
			if (remove.indexOf(c) == -1) {
				sb = sb.append(c);
			} else {
				sb.append(" ");
			}
		}
		return sb.toString();
	}

	private String currentLine() throws IOException {
		return currentLine;
	}

	private String nextLine() throws IOException {
		do {
			currentLine = dataFile.readLine();
			if (currentLine != null)
				currentLine = currentLine.trim();
		} while (currentLine != null && currentLine.length() == 0);
		return currentLine;
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

	public static class Tag {
		public EntityType type;
		public int start;
		public int end;
		public String id;

		public Tag(EntityType type, int start, int end) {
			this.type = type;
			this.start = start;
			this.end = end;
		}

		public String getId() {
			return id;
		}

		public void setId(String id) {
			this.id = id;
		}

		public boolean overlaps(Tag tag) {
			return start <= tag.end && tag.start <= end;
		}

		public boolean contains(Tag tag) {
			return start <= tag.start && end >= tag.end;
		}

		public String toString() {
			return type.toString() + ":" + start + "-" + end;
		}
	}

	public static void main(String[] args) throws ConfigurationException {
		new PubtatorReader("data/Corpus.txt", "data/NCBI_corpus_test_PMIDs.txt");
		System.out.println("Done.");
	}
}