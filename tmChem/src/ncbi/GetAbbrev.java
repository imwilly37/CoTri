package ncbi;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.Map;

import ncbi.chemdner.AbbreviationIdentifier;

public class GetAbbrev {

	public static void main(String[] args) throws IOException {
		AbbreviationIdentifier abbrev = new AbbreviationIdentifier("./identify_abbr", "/home/leamanjr/software/Ab3P-v1.5/", "/home/leamanjr/temp", 1000);
		BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(args[0]), "UTF8"));
		BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(args[1]), "UTF8"));
		try {
			String line = reader.readLine();
			while (line != null) {
				line = line.trim();
				if (line.length() > 0) {
					String[] split = line.split("\\t");
					String documentId = split[0].trim();
					String titleText = split[1].trim(); // Do we need to run this too?
					String abstractText = split[2];

					System.out.println(line);
					Map<String, String> abbreviations = abbrev.getAbbreviations(documentId, titleText + " " + abstractText);
					if (abbreviations == null) {
						System.out.println("WARNING: result was null for " + documentId);
					} else {
						for (String shortStr : abbreviations.keySet()) {
							writer.append(documentId);
							writer.append("\t");
							writer.append(shortStr);
							writer.append("\t");
							writer.append(abbreviations.get(shortStr));
							writer.newLine();
						}
					}
				}
				line = reader.readLine();
			}
		} finally {
			reader.close();
			writer.close();
		}
	}
	
}
