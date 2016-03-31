package ncbi.chemdner;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.StringReader;
import java.util.HashMap;
import java.util.Map;

public class AbbreviationIdentifier {

	private String command;
	private String commandDir;
	private File tempDir;
	private long timeout;

	// TODO Add a minimum score threshold

	public AbbreviationIdentifier(String command, String commandDir, String tempDir, long timeout) {
		this.command = command; // "./identify_abbr"
		this.commandDir = commandDir; // "/home/leamanjr/software/Ab3P-v1.5/"
		this.tempDir = new File(tempDir);
		this.timeout = timeout;
	}

	public Map<String, String> getAbbreviations(String id, String text) throws IOException {
		// Write text to a temp file
		String filenamePrefix = id;
		while (filenamePrefix.length() < 3)
			filenamePrefix = "0" + filenamePrefix;
		File f = File.createTempFile(filenamePrefix, ".txt", tempDir);
		BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(f), "UTF-8"));
		writer.write(text);
		writer.close();

		// Get abbreviations
		ProcessRunner pw = new ProcessRunner(command + " " + f.getAbsolutePath(), commandDir);
		pw.await(timeout);
		String result = pw.getResult();
		String error = pw.getError();
		System.out.println("Abbreviation result is: " + result);
		System.out.println("Abbreviation error is: " + error);

		// Delete temp file
		f.delete();

		// Return abbreviations found
		if (result == null || error != null)
			return null;
		Map<String, String> abbreviations = new HashMap<String, String>();
		BufferedReader reader = new BufferedReader(new StringReader(result));
		String line = reader.readLine();
		while (line != null) {
			if (line.startsWith("  ")) {
				String[] split = line.trim().split("\\|");
				abbreviations.put(split[0], split[1]);
				System.out.println("Found abbreviation pair: " + split[0] + "->" + split[1]);
			}
			line = reader.readLine();
		}
		reader.close();

		return abbreviations;
	}
}
