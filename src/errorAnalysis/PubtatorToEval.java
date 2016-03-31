/* Written by Ming-Yu Chien
 * Transfer data from Pubtator format to Eval format
 * Example function: main function
 */
package errorAnalysis;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;

import objectCDR.MentionLocation;

public class PubtatorToEval {
	String pubPath;
	String evalPath;
	public PubtatorToEval(){
		pubPath  = "./CDR_Data/CDR_Data/CDR.Corpus/CDR_TestSet_CoTri.PubTator.txt";		// Input file in Pubtator format
		evalPath = "./CDR_Data/CDR_Data/CDR.Corpus/CDR_TestSet_CoTri.Eval.txt";			// Output file path
	}
	
	private void tranform() throws IOException {
		FileReader fr = new FileReader(pubPath);
		BufferedReader br = new BufferedReader(fr);
		PrintWriter  evalWriter  = new PrintWriter(evalPath);;
		
		String id,title,ab;
		while (br.ready()) {
			title = br.readLine();
			ab = br.readLine();
			if (title == null || ab == null)
				break;
			id = title.split("\\|")[0];
			while (br.ready()) {
				String text=br.readLine();
				if (!text.contains("\t"))
					break;
				String[] textp=text.split("\t");
				if (textp[1].equals("CID")){
					evalWriter.println(id+"\tCID\t"+textp[2]+"\t"+textp[3]+"\t1.0");
				}
			}	
		}
		br.close();
		evalWriter.close();
	}
	
	public static void main(String args[]) throws IOException{
		// Declare the class
		// All parameters can be set in constructor(Pubtator file path and output file path)
		PubtatorToEval pte = new PubtatorToEval();
		// Transfer this file
		pte.tranform();
	}
}
