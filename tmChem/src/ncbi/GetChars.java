package ncbi;

import java.io.BufferedWriter;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.HashMap;
import java.util.Map;

public class GetChars {

	public static void main(String[] args) throws IOException {
		String inputFilename = args[0];
		String outputFilename = args[1];

		InputStreamReader reader = new InputStreamReader(new FileInputStream(inputFilename), "UTF8");
		int codePointInt = reader.read();
		Map<Integer, Integer> codePointCount = new HashMap<Integer, Integer>();
		while (codePointInt != -1) {
			Integer count = codePointCount.get(new Integer(codePointInt));
			if (count == null) {
				codePointCount.put(new Integer(codePointInt), new Integer(1));
			} else {
				codePointCount.put(new Integer(codePointInt), new Integer(count.intValue() + 1));
			}
			codePointInt = reader.read();
		}
		reader.close();

		BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(outputFilename), "UTF8"));
		for (Integer codePoint : codePointCount.keySet()) {
			StringBuffer output = new StringBuffer();
			codePointInt = codePoint.intValue();
			output.appendCodePoint(codePointInt);
			output.append("\t");
			// output.append(Character.isValidCodePoint(codePointInt));
			// output.append("\t");
			// output.append(Character.charCount(codePointInt));
			// output.append("\t");
			output.append(Character.isLetter(codePointInt));
			output.append("\t");
			output.append(Character.UnicodeBlock.of(codePointInt));
			output.append("\t");
			int count = codePointCount.get(codePoint).intValue();
			output.append(count);
			writer.write(output.toString());
			writer.newLine();
		}
		writer.close();
	}

}
