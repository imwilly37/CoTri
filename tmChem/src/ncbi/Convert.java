package ncbi;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Convert {

	public static void main(String[] args) {

		String abstractFilename = args[0];
		String annotationsFilename = args[1];
		String pubtatorFilename = args[2];

		try {
			System.out.println("Reading documents");
			BufferedReader abstractReader = null;
			List<Document> documents = new ArrayList<Document>();
			try {
				abstractReader = new BufferedReader(new InputStreamReader(new FileInputStream(abstractFilename), "UTF-8"));
				String line = abstractReader.readLine();
				while (line != null) {
					line = line.trim();
					if (line.length() > 0) {
						String[] split = line.split("\t");
						String documentId = split[0];
						String titleText = split[1];
						String abstractText = split[2];
						for (int i = 3; i < split.length; i++) {
							abstractText += " " + split[3];
						}
						documents.add(new Document(documentId, titleText, abstractText));
					}
					line = abstractReader.readLine();
				}
			} finally {
				if (abstractReader != null)
					abstractReader.close();
			}
			abstractReader = null;
			Collections.sort(documents);
			System.out.println("Done.");

			System.out.println("Reading annotations");
			BufferedReader annotationsReader = null;
			List<Annotation> annotations = new ArrayList<Annotation>();
			try {
				annotationsReader = new BufferedReader(new InputStreamReader(new FileInputStream(annotationsFilename), "UTF-8"));
				String line = annotationsReader.readLine();
				while (line != null) {
					line = line.trim();
					if (line.length() > 0) {
						System.out.println(line);
						String[] split = line.split("\t");
						String documentId = split[0];
						String field = split[1];
						int start = Integer.parseInt(split[2]);
						int end = Integer.parseInt(split[3]);
						String text = split[4];
						annotations.add(new Annotation(documentId, field, start, end, text));
					}
					line = annotationsReader.readLine();
				}

			} finally {
				if (annotationsReader != null)
					annotationsReader.close();
			}
			annotationsReader = null;
			Collections.sort(annotations);
			System.out.println("Done.");

			System.out.println("Writing output");
			BufferedWriter writer = null;
			try {
				writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(pubtatorFilename), "UTF-8"));

				for (int i = 0; i < documents.size(); i++) {

					Document document = documents.get(i);
					writer.write(document.getDocumentId());
					writer.write("|t|");
					writer.write(document.getTitleText());
					writer.newLine();
					writer.write(document.getDocumentId());
					writer.write("|a|");
					writer.write(document.getAbstractText());
					writer.newLine();

					String verificationText = document.getTitleText() + "\t" + document.getAbstractText();

					for (int j = 0; j < annotations.size(); j++) {
						Annotation annotation = annotations.get(j);
						if (document.getDocumentId().equals(annotation.getDocumentId())) {
							writer.write(document.getDocumentId());
							writer.write("\t");
							String field = annotation.getField();
							int start = annotation.getStart();
							int end = annotation.getEnd();
							if (field.equals("A")) {
								start += document.getTitleText().length() + 1;
								end += document.getTitleText().length() + 1;
							}
							if (!verificationText.substring(start, end).equals(annotation.getText())) {
								throw new IllegalArgumentException();
							}
							writer.write(Integer.toString(start));
							writer.write("\t");
							writer.write(Integer.toString(end));
							writer.write("\t");
							writer.write(annotation.getText());
							writer.write("\t");
							writer.write("Chemical");
							writer.newLine();
						}
					}

					writer.newLine();
				}

			} finally {
				if (writer != null)
					writer.close();
				if (abstractReader != null)
					abstractReader.close();
			}
			writer = null;
			System.out.println("Done.");

		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	private static class Document implements Comparable<Document> {
		private String documentId;
		private String titleText;
		private String abstractText;

		public Document(String documentId, String titleText, String abstractText) {
			this.documentId = documentId;
			this.titleText = titleText;
			this.abstractText = abstractText;
		}

		public String getDocumentId() {
			return documentId;
		}

		public String getTitleText() {
			return titleText;
		}

		public String getAbstractText() {
			return abstractText;
		}

		@Override
		public int compareTo(Document document) {
			return documentId.compareTo(document.documentId);
		}
	}

	private static class Annotation implements Comparable<Annotation> {
		private String documentId;
		private String field;
		private int start;
		private int end;
		private String text;

		public Annotation(String documentId, String field, int start, int end, String text) {
			this.documentId = documentId;
			this.field = field;
			this.start = start;
			this.end = end;
			this.text = text;
		}

		public String getDocumentId() {
			return documentId;
		}

		public String getField() {
			return field;
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

		@Override
		public int compareTo(Annotation annotation) {
			int compare = documentId.compareTo(annotation.documentId);
			if (compare == 0)
				compare = start - annotation.start;
			return compare;
		}

	}
}
