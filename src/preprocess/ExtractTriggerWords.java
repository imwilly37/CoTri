package preprocess;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ExtractTriggerWords {
	private String trainSet;
	private String outFile;
	private String stopWordFile;
	private HashMap<String,Integer> triggerCount;
	private HashSet<String> stopWords;
	private Pattern pattern;
	private List<Map.Entry<String, Integer>> list_Data;
	private PorterStemmer stemmer;
	private boolean stemFlag;
	
	public ExtractTriggerWords(boolean stem) throws IOException{
		trainSet = "./CDR_Training/CDR_sentences.txt";
		outFile = "./triggerTrain.tsv";
		stopWordFile = "./stopWords.txt";
		stemFlag = stem;
		triggerCount = new HashMap<String,Integer>();
		pattern = Pattern.compile("\\p{Alpha}\\w+");
		stemmer = new PorterStemmer();
		loadStopWords();
	}
	public void loadStopWords() throws IOException{
		stopWords = new HashSet<String>();
		FileReader fr = new FileReader(stopWordFile);
		BufferedReader br = new BufferedReader(fr);
		while (br.ready()){
			String line = br.readLine().toLowerCase();
			String[] stopWordList = line.split(",");
			for (String e:stopWordList){
				if (stemFlag){
					for (int i=0;i<e.length();i++)
						stemmer.add(e.charAt(i));
					stemmer.stem();
					stopWords.add(stemmer.toString());
				}
				else
					stopWords.add(e);
			}
		}
		br.close();
	}
	public void readFile() throws IOException{
		FileReader fr = new FileReader(trainSet);
		BufferedReader br = new BufferedReader(fr);
		String senTxt;
		while (br.ready()) {
			boolean hasCid=false;
			senTxt = br.readLine();
			if (senTxt == null)
				break;
			senTxt = senTxt.split("\\|")[2];
			while (br.ready()) {
				String text=br.readLine();
				if (!text.contains("\t"))
					break;
				String[] textp=text.split("\t");
				if (textp[1].equals("CID")){
					hasCid = true;
				}
			}
			if (hasCid){
				countEachWord(senTxt);
			}
		}
		br.close();
	}
	public void countEachWord(String senTxt){
		Matcher matcher = pattern.matcher(senTxt);
		while (matcher.find()){
			String s = matcher.group().toLowerCase();
			if (stemFlag) {
				for (int i = 0; i < s.length(); i++)
					stemmer.add(s.charAt(i));
				stemmer.stem();
				s = stemmer.toString();
			}
			if (stopWords.contains(s))
				break;
			if (triggerCount.containsKey(s))
				triggerCount.put(s, triggerCount.get(s)+1);
			else
				triggerCount.put(s, 1);
		}
		list_Data = new ArrayList<Map.Entry<String, Integer>>(triggerCount.entrySet());
		Collections.sort(list_Data, new Comparator<Map.Entry<String, Integer>>() {
			public int compare(Map.Entry<String, Integer> entry1, Map.Entry<String, Integer> entry2) {
				return (entry2.getValue() - entry1.getValue());
			}
		});
	}
	public void outputFile() throws IOException{
		PrintWriter pw = new PrintWriter(outFile);
		for (Map.Entry<String, Integer> entry : list_Data) {
			if (entry.getValue()>=3)
				pw.println(entry.getValue()+"\t"+entry.getKey());
		}
		pw.close();
	}
	static public void main(String args[]) throws IOException{
		ExtractTriggerWords et = new ExtractTriggerWords(false);
		et.readFile();
		et.outputFile();
	}
}
