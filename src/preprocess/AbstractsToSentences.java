/* Written by Ming-Yu Chien
 * Transfer data in Pubtator format from abstract-level to sentence level
 * Example function: main function
 */
package preprocess;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.text.BreakIterator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;

import edu.stanford.nlp.dcoref.CorefChain;
import edu.stanford.nlp.dcoref.CorefCoreAnnotations;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.StanfordCoreNLP;
import edu.stanford.nlp.util.CoreMap;
import objectCDR.MentionLocation;

public class AbstractsToSentences {
	private String abstractPath;
	private String outPath;
	private PrintWriter  writer;
	private int trueSen;
	private int falseSen;
	private int totalCID;
	private int checkCID;
	private HashMap<String,Boolean> cidCheck;
	private boolean resolution;
	private StanfordCoreNLP pipeline;
	
	public AbstractsToSentences() throws IOException{

		abstractPath = "./CDR_Data/CDR_Data/CDR.Corpus/CDR_TestSet.PubTator.txt";		// File with data in abstract-level
		outPath ="./CDR_Data/CDR_Data/CDR.Corpus/CDR_TestSet_Sentences.PubTator.txt";	// Output file
		
		/*statistical variables*/
		trueSen=0;
		falseSen=0;
		totalCID = 0;
		checkCID = 0;
		/*-----------*/
		
		// Do co-reference resolution or not
		// There is no threshold in this function
		resolution = true;
		
		pipeline = new StanfordCoreNLP();
	}
	
	public void transFile() throws IOException{

		String title,ab,id;
		FileReader fr = new FileReader(abstractPath);
		BufferedReader br = new BufferedReader(fr);
		
		HashSet<String> totalFile = new HashSet<String>();

		writer = new PrintWriter(outPath, "UTF-8");

		while (br.ready()) {
			ArrayList<MentionLocation> diseases=new ArrayList<MentionLocation>();
			ArrayList<MentionLocation> chemicals=new ArrayList<MentionLocation>();
			ArrayList<String> cids=new ArrayList<String>();
			 cidCheck=new HashMap<String,Boolean>();
			title = br.readLine();
			ab = br.readLine();
			if (title == null || ab == null)
				break;
			id = title.split("\\|")[0];
			
			title = title.split("\\|")[2];
			ab = ab.split("\\|")[2];
			while (br.ready()) {
				String text=br.readLine();
				if (!text.contains("\t"))
					break;
				String[] textp=text.split("\t");
				if (!textp[1].equals("CID")){
					 if (textp[4].equals("Disease")){
						 int start = Integer.parseInt(textp[1]);
						 int end = Integer.parseInt(textp[2]);
						 String menText = textp[3];
						 String[] menID = textp[5].split("\\|");
						 MentionLocation men = new MentionLocation(start,end,"Disease",menText,menID,id);
						 diseases.add(men);
					 }
					 else{
						 int start = Integer.parseInt(textp[1]);
						 int end = Integer.parseInt(textp[2]);
						 String menText = textp[3];
						 String[] menID = textp[5].split("\\|");
						 MentionLocation men = new MentionLocation(start,end,"Chemical",menText,menID,id);
						 chemicals.add(men);
					 }
				}
				else{
					cids.add(textp[2]+"|"+textp[3]);
					cidCheck.put(textp[2]+"|"+textp[3],false);
				}
			}
			
			if (totalFile.contains(id))
				continue;
			else
				totalFile.add(id);
			if (resolution == true){
				corRes(title+"\n"+ab,diseases,chemicals);
				matchCID(title+"\n"+ab,diseases,chemicals,cids,id,"s");
				
			}
			else
				matchCID(title+"\n"+ab,diseases,chemicals,cids,id,"s");
			
			totalCID += cidCheck.size();
			for (Entry<String,Boolean> entry:cidCheck.entrySet())
				if (entry.getValue())
					checkCID ++;
		}
		
		System.out.println(trueSen);
		System.out.println(falseSen);
		System.out.println(totalCID);
		System.out.println(checkCID);
		br.close();
		writer.close();
	}

	private void corRes(String text, ArrayList<MentionLocation> diseases,
			ArrayList<MentionLocation> chemicals) {
		HashMap<String,MentionLocation> disMap = new HashMap<String,MentionLocation>();
		HashMap<String,MentionLocation> cheMap = new HashMap<String,MentionLocation>();
		for (MentionLocation eMen:diseases)
			disMap.put(eMen.start+","+eMen.end, eMen);
		for (MentionLocation eMen:chemicals)
			cheMap.put(eMen.start+","+eMen.end, eMen);
		
		Annotation annotation = new Annotation(text);
		pipeline.annotate(annotation);
		List<CoreMap> sentences = annotation
				.get(CoreAnnotations.SentencesAnnotation.class);
		if (sentences != null && !sentences.isEmpty()) {
			Map<Integer, CorefChain> corefChains = annotation.get(CorefCoreAnnotations.CorefChainAnnotation.class);
			if (corefChains == null) {
				return;
			}
			
			for (Map.Entry<Integer, CorefChain> entry : corefChains.entrySet()) {
				List<CorefChain.CorefMention> mList = entry.getValue().getMentionsInTextualOrder();
				if (mList.size() < 2)
					continue;
				
				CorefChain.CorefMention oM = mList.get(0);
				List<CoreLabel> tokens = sentences.get(oM.sentNum - 1).get(
						CoreAnnotations.TokensAnnotation.class);
				int begin = tokens.get(oM.startIndex - 1).beginPosition();
				int end = tokens.get(oM.endIndex - 2).endPosition();
				String regionStr = begin+","+end;
				boolean disFlag;
				MentionLocation oMen;
				if (cheMap.containsKey(regionStr)){
					disFlag = false;
					oMen = cheMap.get(regionStr);
				}
				else if (disMap.containsKey(regionStr)){
					disFlag = true;
					oMen = disMap.get(regionStr);
				}
				else
					continue;
				for (int i=1;i<mList.size();i++) {
					CorefChain.CorefMention m = mList.get(i);
					// We need to subtract one since the indices count from 1
					// but the Lists start from 0
					tokens = sentences.get(m.sentNum - 1).get(
							CoreAnnotations.TokensAnnotation.class);
					// We subtract two for end: one for 0-based indexing, and
					// one because we want last token of mention not one
					// following.
					begin = tokens.get(m.startIndex - 1).beginPosition();
					end = tokens.get(m.endIndex - 2).endPosition();
					regionStr = begin+","+end;
					MentionLocation nMen = new MentionLocation(oMen);
					if (nMen.end-nMen.start <= end-begin)
						continue;
					nMen.start = begin;
					nMen.end = end;
					nMen.mentionStr = text.substring(begin, end);
					if (disFlag){
						if (!disMap.containsKey(regionStr)){
							diseases.add(nMen);
							System.out.println("Add one disease after resolution");
							System.out.println(text);
							System.out.println(nMen.mentionStr);
						}
					}
					else{
						if (!cheMap.containsKey(regionStr)){
							chemicals.add(nMen);
							System.out.println("Add one chemical after resolution");
						}
					}
				}
			}
		}
	}
	
	private void matchCID(String text, ArrayList<MentionLocation> diseases,
			ArrayList<MentionLocation> chemicals,ArrayList<String> cids, String id,String prefix) {
		BreakIterator border = BreakIterator.getSentenceInstance(Locale.US);
		border.setText(text);
		int start = border.first();
		int senCount = 0;
		for (int end = border.next(); end != BreakIterator.DONE; start = end, end = border.next()) {
			String outid = id+"_"+senCount++;		// add senCount suffix
			
			String sentenceStr=text.substring(start,end).trim();
			if (sentenceStr.contains("\n"))
				sentenceStr = sentenceStr.replace("\n", "");
			ArrayList<MentionLocation> mDiseases = new ArrayList<MentionLocation>();
			ArrayList<MentionLocation> mChemicals = new ArrayList<MentionLocation>();
			for (int i=0;i<diseases.size();i++){
				if (diseases.get(i).start >= start && diseases.get(i).end <= end)
					mDiseases.add(diseases.get(i));
			}
			for (int i=0;i<chemicals.size();i++){
				if (chemicals.get(i).start >= start && chemicals.get(i).end <= end)
					mChemicals.add(chemicals.get(i));
			}
						
			if (mDiseases.size()>0 && mChemicals.size()>0){
				writer.println(outid+"|"+prefix+"|"+sentenceStr);
				for (MentionLocation eMDis:mDiseases){
					String dID="";
					for (String e:eMDis.mentionID){
						if (dID.length() == 0)
							dID += e;
						else
							dID += "|"+e;
					}
					writer.println(outid+"\t"+(eMDis.start-start)+"\t"+(eMDis.end-start)+"\t"+eMDis.mentionStr+"\tDisease\t"+dID);
				}
				for (MentionLocation eMChe:mChemicals){
					String cID="";
					for (String e:eMChe.mentionID){
						if (cID.length() == 0)
							cID += e;
						else
							cID += "|"+e;
					}
					writer.println(outid+"\t"+(eMChe.start-start)+"\t"+(eMChe.end-start)+"\t"+eMChe.mentionStr+"\tChemical\t"+cID);
				}
				
				HashSet<String> mDisID = new HashSet<String>();
				HashSet<String> mCheID = new HashSet<String>();
				for (MentionLocation eMDis:mDiseases){
					for (String e:eMDis.mentionID)
						mDisID.add(e);
				}
				for (MentionLocation eMChe:mChemicals){
					for (String e:eMChe.mentionID)
						mCheID.add(e);
				}
				boolean hasCID = false;
				for (String e:cids){
					String cID = e.split("\\|")[0];
					String dID = e.split("\\|")[1];
					
					if (mCheID.contains(cID) && mDisID.contains(dID)){
						writer.println(outid+"\tCID\t"+cID+"\t"+dID);
						cidCheck.put(cID+"|"+dID,true);
						hasCID = true;
					}
				}
				
								
				if (hasCID)
					trueSen++;
				else
					falseSen++;
				writer.println();
			}
		}
		
	}
	static public void main(String args[]) throws IOException{

		// Declare this class
		// All parameters can be set in constructor (e.g. original data file path, output data file path, do co-reference or not...)
		AbstractsToSentences ats = new AbstractsToSentences();
		//transfer this file
		ats.transFile();
	}
}
