/* Written by Ming-Yu Chien
 * For doing error analysis easily,
 * you can print out false negative, false positive, true positive and true negative in this class
 * Example function: main function
 */
package errorAnalysis;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

public class FalseDataPrint {
	public String testPath;
	public String goldPath;
	public String outPath;
	public String origPath;
	public ArrayList<String> testArray;
	public ArrayList<String> goldArray;
	public ArrayList<String> goldNegative;
	
	public FalseDataPrint(){
		testPath = "./CDR_Data/CDR_Data/CDR.Corpus/CDR_TestSet_CoTri.Eval.txt";	// result dataset path(in Eval format)
		goldPath = "./CDR_Data/CDR_Data/CDR.Corpus/CDR_TestSet.Eval.txt";		// gold standard dataset path(in Eval format)
		outPath = "./CDR_Data/CDR_Data/CDR.Corpus/falseNegative.PubTator.txt";	// output file path
		origPath = "./CDR_Data/CDR_Data/CDR.Corpus/CDR_TestSet.PubTator.txt";	// original dataset path(in Pubtator format)
	}
	
	private void readGoldTest() throws IOException{
		FileReader gfr = new FileReader(goldPath);
		BufferedReader gbr = new BufferedReader(gfr);
		FileReader tfr = new FileReader(testPath);
		BufferedReader tbr = new BufferedReader(tfr);
		goldArray = new ArrayList<String>();
		testArray = new ArrayList<String>();
		goldNegative = new ArrayList<String>();
		HashMap<String,ArrayList<String>> idChe = new HashMap<String,ArrayList<String>>();
		HashMap<String,ArrayList<String>> idDis = new HashMap<String,ArrayList<String>>();
		while (gbr.ready()){
			String line = gbr.readLine();
			String id=line.split("\t")[0];
			String che = line.split("\t")[2];
			String dis = line.split("\t")[3];
			goldArray.add(id+"|"+che+"|"+dis);
			if (idChe.containsKey(id))
				idChe.get(id).add(che);
			else{
				ArrayList<String> nList = new ArrayList<>();
				nList.add(che);
				idChe.put(id,nList);
			}
			if (idDis.containsKey(id))
				idDis.get(id).add(dis);
			else{
				ArrayList<String> nList = new ArrayList<>();
				nList.add(dis);
				idDis.put(id,nList);
			}
		}
		for (Map.Entry<String,ArrayList<String>> eachEntry: idChe.entrySet()){
			String entryID = eachEntry.getKey();
			for (String che: eachEntry.getValue())
				for (String dis: idDis.get(entryID))
				goldNegative.add(eachEntry.getKey()+"|"+che+"|"+dis);
		}
		goldNegative.removeAll(goldArray);
		while (tbr.ready()){
			String line = tbr.readLine();
			String id=line.split("\t")[0];
			String che = line.split("\t")[2];
			String dis = line.split("\t")[3];
			testArray.add(id+"|"+che+"|"+dis);
		}
		gbr.close();
		tbr.close();
	}
	public ArrayList<String> getFP(){
		ArrayList<String> fpArray = new ArrayList<String>(testArray);
		fpArray.removeAll(goldArray);
		return fpArray;
	}
	public ArrayList<String> getFN(){
		ArrayList<String> fnArray = new ArrayList<String>(goldArray);
		fnArray.removeAll(testArray);
		return fnArray;
	}
	public ArrayList<String> getTP(){
		ArrayList<String> tpArray = new ArrayList<String>(goldArray);
		tpArray.retainAll(testArray);

        return tpArray;
	}
	public ArrayList<String> getTN(){
		ArrayList<String> tnArray = new ArrayList<String>(goldNegative);
		tnArray.removeAll(testArray);
		return tnArray;
	}
	public void print(ArrayList<String> dataArray) throws IOException {
		
		
		HashMap<String,HashSet<String>> dataMap = new HashMap<String,HashSet<String>>();
		for (String e:dataArray){
			String id = e.split("\\|")[0];
			String che = e.split("\\|")[1];
			String dis = e.split("\\|")[2];
			String cid = che+"|"+dis;
			if (dataMap.containsKey(id)){
				HashSet<String> s = dataMap.get(id);
				s.add(che);
				s.add(dis);
				s.add(cid);
			}
			else{
				HashSet<String> s = new HashSet<String>();
				s.add(che);
				s.add(dis);
				s.add(cid);
				dataMap.put(id,s);
			}
		}
		
		FileReader fr = new FileReader(origPath);
		BufferedReader br = new BufferedReader(fr);
		PrintWriter  outWriter  = new PrintWriter(outPath);;
		
		String id,title,ab;
		while (br.ready()) {
			title = br.readLine();
			ab = br.readLine();
			if (title == null || ab == null)
				break;
			id = title.split("\\|")[0];
			HashSet<String> sSet = null;
			if (dataMap.containsKey(id)){
				outWriter.println(title+"\n"+ab);
				sSet = dataMap.get(id);
			}
			while (br.ready()) {
				String text=br.readLine();
				if (!text.contains("\t"))
					break;
				if (!dataMap.containsKey(id))
					continue;
				String[] textp=text.split("\t");
				if (!textp[1].equals("CID")){
					String[] menID = textp[5].split("\\|");
					for (String e:menID)
						if (sSet.contains(e)){
							outWriter.println(text);
							break;
						}
				}
			}
			if (dataMap.containsKey(id)){
				for (String e:sSet)
					if (e.contains("|"))
						outWriter.println(id+"\tCID\t"+e.split("\\|")[0]+"\t"+e.split("\\|")[1]);
				outWriter.println();
			}
		}
		br.close();
		outWriter.close();
	}
	public static  void main(String args[]) throws IOException{
		// Declare the class
		// You can set all parameters in constructor(e.g. Test set file path, gold standard file path, output file path...)
		FalseDataPrint fpp = new FalseDataPrint();
		// Read the gold standard and test set
		fpp.readGoldTest();
		// Print false negative
		fpp.print(fpp.getFN());
		// You need to change output file path if you print another file
		// fpp.print(fpp.getFP());	// print false positive
		// fpp.print(fpp.getTP());	// print true positive
		// fpp.print(fpp.getTN());	// print true negative
		
	}
}
