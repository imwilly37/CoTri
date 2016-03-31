package dnorm.util;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AbbreviationResolver {

	private Map<String, Map<String, String>> abbreviations;

	public AbbreviationResolver() {
		abbreviations = new HashMap<String, Map<String, String>>();
	}

	public void loadAbbreviations(String filename) {
		abbreviations = new HashMap<String, Map<String, String>>();
		try {
			BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(filename), "UTF8"));
			String line = reader.readLine();
			while (line != null) {
				line = line.trim();
				if (line.length() > 0) {
					String[] split = line.split("\\t");
					createAbbreviation(split[0], split[1], split[2]);
				}
				line = reader.readLine();
			}
			reader.close();
		} catch (IOException e) {
			// TODO Improve exception handling
			throw new RuntimeException(e);
		}
	}

	private void createAbbreviation(String pmid, String shortForm, String longForm) {
		Map<String, String> abbreviation = abbreviations.get(pmid);
		if (abbreviation == null) {
			abbreviation = new HashMap<String, String>();
			abbreviations.put(pmid, abbreviation);
		}
		if (abbreviation.containsKey(shortForm) && !abbreviation.get(shortForm).equals(longForm)) {
			throw new IllegalArgumentException("Multiple mappings for short form \"" + shortForm + "\": \"" + longForm + "\" and \"" + abbreviation.get(shortForm) + "\"");
		}
		abbreviation.put(shortForm, longForm);
	}

	public String expandAbbreviations(String documentId, String lookupText) {
		return expandAbbreviations(lookupText, abbreviations.get(documentId));
	}

	public static String expandAbbreviations(String lookupText, Map<String, String> abbreviationMap) {
		if (abbreviationMap == null)
			return lookupText;
		for (String abbreviation : abbreviationMap.keySet()) {
			if (lookupText.contains(abbreviation)) {
				String replacement = abbreviationMap.get(abbreviation);
				String updated = null;
				if (lookupText.contains(replacement)) {
					// Handles mentions like "von Hippel-Lindau (VHL) disease"
					updated = lookupText.replaceAll("\\(?\\b" + Pattern.quote(abbreviation) + "\\b\\)?", "");
				} else {
					updated = lookupText.replaceAll("\\(?\\b" + Pattern.quote(abbreviation) + "\\b\\)?", Matcher.quoteReplacement(replacement));
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
}
