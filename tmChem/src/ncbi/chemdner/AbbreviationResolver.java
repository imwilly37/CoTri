package ncbi.chemdner;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;

public class AbbreviationResolver {

	private Map<String, Map<String, String>> abbreviations;

	public AbbreviationResolver() {
		abbreviations = new HashMap<String, Map<String, String>>();
	}

	public void loadFromFile(String filename) throws IOException {
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
	}

	public void createAbbreviation(String pmid, String shortForm, String longForm) {
		Map<String, String> abbreviation = abbreviations.get(pmid);
		if (abbreviation == null) {
			abbreviation = new HashMap<String, String>();
			abbreviations.put(pmid, abbreviation);
		}
		if (abbreviation.containsKey(shortForm) && !abbreviation.get(shortForm).equals(longForm))
			throw new IllegalArgumentException();
		abbreviation.put(shortForm, longForm);
	}

	public String expandAbbreviations(String documentId, String lookupText) {
		Map<String, String> abbreviationMap = abbreviations.get(documentId);
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
}
