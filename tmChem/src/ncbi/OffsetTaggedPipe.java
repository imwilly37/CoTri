package ncbi;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import banner.types.SentenceWithOffset;
import banner.types.Token;
import cc.mallet.pipe.Pipe;
import cc.mallet.types.Instance;
import cc.mallet.types.TokenSequence;

public class OffsetTaggedPipe extends Pipe {

	private static final long serialVersionUID = 3416211405197601842L;

	private Map<String, List<Tag>> documentIdToTagList;

	public OffsetTaggedPipe(String offsetTaggedFilename) {
		documentIdToTagList = new HashMap<String, List<Tag>>();
		setOffsetTaggedFilename(offsetTaggedFilename);
	}

	public void setOffsetTaggedFilename(String offsetTaggedFilename) {
		try {
			BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(offsetTaggedFilename), "UTF8"));
			int count = 0;
			try {
				String line = reader.readLine();
				while (line != null) {
					line = line.trim();
					if (line.length() > 0) {
						String[] split = line.split("\\t");
						String documentId = split[0].trim();
						int start = Integer.parseInt(split[1]);
						int end = Integer.parseInt(split[2]);
						String text = split[3];
						List<Tag> tagList = documentIdToTagList.get(documentId);
						if (tagList == null) {
							tagList = new ArrayList<Tag>();
							documentIdToTagList.put(documentId, tagList);
						}
						if (filter(text))
							tagList.add(new Tag(start, end, text));
					}
					count++;
					line = reader.readLine();
				}
			} finally {
				System.out.println("Loaded " + count + " annotations from " + documentIdToTagList.size() + " files");
				reader.close();
			}
		} catch (IOException e) {
			// TODO Improve error handling
			throw new RuntimeException(e);
		}
	}

	private boolean filter(String text) {
		if (text.toLowerCase().equals("water"))
			return false;
		if (text.toLowerCase().equals("proton"))
			return false;
		if (text.toLowerCase().equals("starch"))
			return false;
		return true;
	}

	@Override
	public Instance pipe(Instance carrier) {
		if (documentIdToTagList.size() == 0) {
			return carrier;
		}
		SentenceWithOffset sentence = (SentenceWithOffset) carrier.getSource();
		// System.out.println("OTP: " + sentence.getSentenceId() + " " + sentence.getText());
		List<Token> tokens = sentence.getTokens();
		boolean[] values = new boolean[tokens.size()]; // Initialized to all FALSE

		List<Tag> tagList = documentIdToTagList.get(sentence.getDocumentId());
		if (tagList != null) {
			for (Tag t : tagList) {
				int start = t.getStart() - sentence.getOffset();
				int end = t.getEnd() - sentence.getOffset();
				if (start >= 0 && end < sentence.getText().length()) {
					if (!sentence.getText().substring(start, end).equals(t.getText())) {
						System.out.println("ERROR: " + t.getText() + " does not match " + sentence.getText().substring(start, end));
						// throw new IllegalArgumentException();
					} else {
						for (int i = 0; i < tokens.size(); i++) {
							Token bannerToken = tokens.get(i);
							// This only requires some overlap
							if (end > bannerToken.getStart() && start < bannerToken.getEnd()) {
								// System.out.println("\t" + bannerToken.getText() + "\tT");
								values[i] = true;
								// } else {
								// System.out.println("\t" + bannerToken.getText() + "\tF");
							}
						}
					}
				}
			}
		}

		TokenSequence data = (TokenSequence) carrier.getData();
		for (int i = 0; i < tokens.size(); i++) {
			cc.mallet.types.Token malletToken = data.get(i);
			if (values[i]) {
				malletToken.setFeatureValue("OTPIPE=", 1);
			}
		}
		return carrier;
	}

	private static class Tag implements Serializable {
		private static final long serialVersionUID = -6623260447054331827L;
		int start;
		int end;
		String text;

		public Tag(int start, int end, String text) {
			this.start = start;
			this.end = end;
			this.text = text;
		}

		public int getStart() {
			return start;
		}

		public int getEnd() {
			return end;
		}

		public String getText() {
			return text;
		}

	}
}
