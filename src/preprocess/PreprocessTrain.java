package preprocess;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.HashSet;

public class PreprocessTrain {
	private static String filePath="./CDR_Training/CDR_TrainingSet.txt";
	private static String diseasePath="./CDR_Training/CDR_TrainingSet450.txt";
	private static String filterPath="./CDR_Training/CDR_sample.txt";
	private static PrintWriter writer;
	public static void main(String args[]) throws IOException{
		FileReader fr=new FileReader(filePath);
		BufferedReader br=new BufferedReader(fr);
		FileReader frFilt=new FileReader(filterPath);
		BufferedReader brFilt=new BufferedReader(frFilt);
		HashSet<String> IDFilt=new HashSet<String>();
		writer = new PrintWriter(diseasePath, "UTF-8");
		while (brFilt.ready()){
			String line=brFilt.readLine();
			if (line.split("\\|").length==3){
				IDFilt.add(line.split("\\|")[0]);
			}
		}
		brFilt.close();
		boolean filtFlag=false;
		while(br.ready()){
			String line=br.readLine();
			if (line.split("\\|").length==3){
				if (!IDFilt.contains(line.split("\\|")[0]))
					writer.println(line);
				else
					filtFlag=true;
			}
			else{
				if (line.length() == 0){
					if (filtFlag==true)
						filtFlag=false;
					else
						writer.println();
				}
				else{
					if (!IDFilt.contains(line.split("\t")[0])){
						writer.println(line);
					}
				}
			}
		}
		br.close();
	}
}
