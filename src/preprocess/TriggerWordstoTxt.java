package preprocess;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

public class TriggerWordstoTxt {
	private String TriggerCSV;
	private String TriggerTXT;
	TriggerWordstoTxt(){
		TriggerCSV = "./triggerWords/triggerWords.csv";
		TriggerTXT = "./triggerWords/triggerWords.txt";
	}
	public void CSVtoTXT() throws IOException{
		FileReader fr = new FileReader(TriggerCSV);
		BufferedReader br = new BufferedReader(fr);
		FileWriter wr= new FileWriter(TriggerTXT);
		
		if (br.ready())
			br.readLine();
		while (br.ready()){
			String line = br.readLine();
			wr.write("3\t"+line.substring(line.indexOf(",")+1).toLowerCase()+"\n");
		}
		wr.close();
		br.close();
	}
	static public void main(String args[]) throws IOException{
		TriggerWordstoTxt twt = new TriggerWordstoTxt();
		twt.CSVtoTXT();
	}
}
