/* Written by Ming-Yu Chien
 * Some test programs with bad results
 * (not the major class, the major class is "Demo" class in  "extractCDRfeatures.relation" package)
 * Some minor methods for chemical disease relations extraction
 * E.g. co-occurence, SRL ...
 */
package extractionCDRapi;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.BreakIterator;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;

import objectCDR.SRLObject;

public class CDRExtraction {
	private int threshold;
	private boolean checkTriggerSen;
	private boolean checkTriggerBetween;
	private boolean checkTriggerRel;
	private HashMap<String,Integer> triggerWords;
	private String triggerWordsFile;
	public CDRExtraction() throws IOException{
		threshold=1;
		this.checkTriggerSen=false;
		this.checkTriggerBetween=false;
		this.checkTriggerRel=false;
		this.triggerWordsFile="./triggerWords/triggerWords.csv";
		triggerWords = new HashMap<String,Integer>();
		readTriggerWords();
	}
	private void readTriggerWords() throws IOException{
		FileReader fr = new FileReader(triggerWordsFile);
		BufferedReader br = new BufferedReader(fr);
		if (br.ready())
			br.readLine();
		while (br.ready()) {
			String line = br.readLine();
			try {
				int freq = Integer.parseInt(line.split(",")[0]);
				String triggerWord = line.split(",")[1].trim().toLowerCase();
				if (triggerWords.containsKey(triggerWord))
					triggerWords.put(triggerWord, freq + triggerWords.get(triggerWords));
				else
					triggerWords.put(triggerWord, freq);
			} catch (NumberFormatException e) {
				e.printStackTrace();
			}
		}
		br.close();

	}
	public HashMap<String,Integer> cdreFromTextSRL(String text,HashMap<String,String>diseases,HashMap<String,String>chemicals) throws IOException{
		BreakIterator border = BreakIterator.getSentenceInstance(Locale.US);
		border.setText(text);
		int start = border.first();
		HashMap<String,Integer> tempR=new HashMap<String,Integer>();
		HashMap<String,Integer> eSenR=new HashMap<String,Integer>();
		for (int end = border.next(); end != BreakIterator.DONE; start = end, end = border.next()) {
			String sentenceStr=text.substring(start,end).trim();
			if (sentenceStr.contains("\n")){
				int tmpStart=start;
				for (String eSen:sentenceStr.split("\\n")){
					eSenR=cdreFromSentenceSRL(eSen,tmpStart,diseases,chemicals);
					tmpStart+=eSen.length();
				}
			}
			else
				eSenR=cdreFromSentenceSRL(sentenceStr,start,diseases,chemicals);
			for (Entry<String,Integer> entry:eSenR.entrySet()){
				if (tempR.containsKey(entry.getKey())){
					tempR.put(entry.getKey(),tempR.get(entry.getKey())+entry.getValue());
				}
				else
					tempR.put(entry.getKey(),entry.getValue());
			}
		}
		for(Iterator<Entry<String, Integer>> it = tempR.entrySet().iterator(); it.hasNext(); ) {
		      Map.Entry<String, Integer> entry = it.next();
		      if(entry.getValue()<threshold) {
		        it.remove();
		      }
		    }
		
		return tempR;
	}
	public HashMap<String,Integer> cdreFromSentenceSRL(String senStr,int senStart,HashMap<String,String>diseases,HashMap<String,String>chemicals) throws IOException{
		HashMap<String,Integer> pCIDs=new HashMap<String,Integer>();
		
		if (checkTriggerSen){
			if (!checkTriggerWords(senStr,1))
				return pCIDs;
		}
		
		HashSet<String> pDiseases = new HashSet<String>();
		for (Entry<String,String> entry:diseases.entrySet()){
			String diseaseTxt=entry.getKey();
			if (senStr.contains(diseaseTxt))
				pDiseases.add(diseaseTxt);
		}
		if (pDiseases.isEmpty())
			return new HashMap<String,Integer> ();
		
		HashSet<String> pChemicals = new HashSet<String>();
		for (Entry<String,String> entry:chemicals.entrySet()){
			String chemicalTxt=entry.getKey();
			if (senStr.contains(chemicalTxt))
				pChemicals.add(chemicalTxt);
		}
		if (pChemicals.isEmpty())
			return new HashMap<String,Integer> ();
		
		SRL srl=new SRL();
		SRLObject[] srlResults=srl.srl(senStr);
		if (srlResults!=null){
			for (int i=0;i<srlResults.length;i++){
				if (checkTriggerRel && !checkRelMatchTrigger(srlResults[i].rel,100))
					continue;
				HashSet<String> beforeDiseases=new HashSet<String>();
				HashSet<String> beforeChemicals=new HashSet<String>();
				for (String eachArg:srlResults[i].args){
					for (String eachpDisease:pDiseases){
						if (eachArg.contains(eachpDisease)){
							if (!beforeChemicals.isEmpty()){
								String dID=diseases.get(eachpDisease);
								for (String eachbChemical : beforeChemicals) {
									String cID = chemicals.get(eachbChemical);
									if (checkTriggerBetween
											&& !checkTriggerBetweenEntities(senStr, eachpDisease,eachbChemical,1))
										continue;

									if (pCIDs.containsKey(cID + "|" + dID))
										pCIDs.put(cID + "|" + dID,
												pCIDs.get(cID + "|" + dID) + 1);
									else
										pCIDs.put(cID + "|" + dID, 1);
								}
							}
							beforeDiseases.add(eachpDisease);
						}
					}
					for (String eachpChemical:pChemicals){
						if (eachArg.contains(eachpChemical)){
							if (!beforeDiseases.isEmpty()){
								String cID=chemicals.get(eachpChemical);
								for (String eachbDisease:beforeDiseases){
									String dID=diseases.get(eachbDisease);
									
									if (checkTriggerBetween
											&& !checkTriggerBetweenEntities(senStr, eachbDisease,eachpChemical,1))
										continue;
									
									if (pCIDs.containsKey(cID+"|"+dID))
										pCIDs.put(cID+"|"+dID, pCIDs.get(cID+"|"+dID)+1);
									else
										pCIDs.put(cID+"|"+dID,1);
								}
							}
							beforeChemicals.add(eachpChemical);
						}
					}
				}
				
			}
		}
		 return pCIDs;
	}
	private boolean checkRelMatchTrigger(String rel,int relThreshold){
		if (triggerWords.containsKey(rel) && triggerWords.get(rel)>= relThreshold)
			return true;
		else
			return false;
	}
	private boolean checkTriggerWords(String senText,int freqThreshold){
		senText = senText.toLowerCase();
		for (Entry<String,Integer> entry:triggerWords.entrySet()){
			if (senText.contains(entry.getKey()) && entry.getValue() >= freqThreshold)
				return true;
		}
		return false;
	}
	private boolean checkTriggerBetweenEntities(String sen,String entity1,String entity2,int Threshold){
		System.out.println(entity1);
		System.out.println(entity2);
		int index1 = sen.indexOf(entity1);
		int index2 = sen.indexOf(entity2);
		if (index1 > index2){
			int temp = index1;
			index1 = index2;
			index2 = temp;
		}
		String betweenStr = sen.substring(index1,index2 );
		return checkTriggerWords(betweenStr,Threshold);
	}
	public HashMap<String,Integer> cooFromText(String text,HashMap<String,String>diseases,HashMap<String,String>chemicals){
		BreakIterator border = BreakIterator.getSentenceInstance(Locale.US);
		border.setText(text);
		int start = border.first();
		HashMap<String,Integer> tempR=new HashMap<String,Integer>();
		HashMap<String,Integer> eSenR=new HashMap<String,Integer>();
		for (int end = border.next(); end != BreakIterator.DONE; start = end, end = border.next()) {
			String sentenceStr=text.substring(start,end).trim();
			if (sentenceStr.contains("\n")){
				for (String eSen:sentenceStr.split("\\n")){
					eSenR=cooFromSentence(eSen,diseases,chemicals);
				}
			}
			else
				eSenR=cooFromSentence(sentenceStr,diseases,chemicals);
			for (Entry<String,Integer> entry:eSenR.entrySet()){
				if (tempR.containsKey(entry.getKey())){
					tempR.put(entry.getKey(),tempR.get(entry.getKey())+entry.getValue());
				}
				else
					tempR.put(entry.getKey(),entry.getValue());
			}
		}
		
		return tempR;
	}
	public HashMap<String,Integer> cooFromSentence(String sen,HashMap<String,String>diseases,HashMap<String,String>chemicals){
		HashMap<String,Integer> cdrs= new HashMap<String,Integer>();
		HashSet<String> cDiseases= new HashSet<String>();
		for (Entry<String,String> eDis:diseases.entrySet()){
			if (sen.contains(eDis.getKey()))
				cDiseases.add(eDis.getValue());
		}
		for (Entry<String,String> eChe:chemicals.entrySet()){
			if (sen.contains(eChe.getKey())){
				for (String ecDis:cDiseases){
					cdrs.put(eChe.getValue()+"|"+ecDis, 1);
				}
			}
		}
		return cdrs;
	}
	static public void main(String args[]) throws IOException{
		String title, ab, total;
		String filePath = "./CDR_Training/CDR_sample.txt";
		String logPath="./log.txt";
		
		FileReader fr = new FileReader(filePath);
		BufferedReader br = new BufferedReader(fr);
		
		PrintWriter  writer = new PrintWriter(logPath, "UTF-8");
		
		
		int tp=0,fp=0,fn=0;
		
		while (br.ready()) {
			HashMap<String,String> diseases=new HashMap<String,String>();
			HashMap<String,String> chemicals=new HashMap<String,String>();
			ArrayList<String> cids=new ArrayList<String>();
			title = br.readLine();
			ab = br.readLine();
			writer.println("---------"+title.split("\\|")[0]+"---------");
			if (title == null || ab == null)
				break;
			while (br.ready()) {
				String text=br.readLine();
				if (!text.contains("\t"))
					break;
				String[] textp=text.split("\t");
				if (!textp[1].equals("CID")){
					 if (textp[4].equals("Disease")){
						 diseases.put(textp[3],textp[5]);
					 }
					 else{
						 chemicals.put(textp[3],textp[5]);
					 }
				}
				else{
					cids.add(textp[2]+"|"+textp[3]);
				}
			}
			
			System.out.println(title.split("\\|")[0]);
			total = title.split("\\|")[2] + "\n" + ab.split("\\|")[2];
			int tmptp=0;
			CDRExtraction cdre=new CDRExtraction();
			HashMap<String,Integer> CDRs=cdre.cdreFromTextSRL(total,diseases,chemicals);
			for (Entry<String,Integer> entry:CDRs.entrySet()){
				if (cids.contains(entry.getKey())){
					tp++;
					tmptp++;
				}
				else{
					fp++;
				}
			}
			fn+=cids.size()-tmptp;
			
			writer.println("---------------------------");	
		}
		double precision=(double)tp/(tp+fp);
		double recall=(double)tp/(tp+fn);
		System.out.println("true positive: "+tp);
		System.out.println("false positive: "+fp);
		System.out.println("false negative: "+fn);
		System.out.println("precision: "+precision);
		System.out.println("recall: "+recall);
		System.out.println("f-measure: "+2*precision*recall/(precision+recall));
		writer.close();
		br.close();
	}
}
