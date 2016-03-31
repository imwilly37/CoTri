/* Copy from http://www.biosemantics.org/uploads/DDI.zip
 * Modified by Ming-Yu Chien
 * Read data in Pubtator format，and transfer it into SenData format (a data format to record all dat in one sentence)
 * Important functions: readTotalTxt, readTxt
 * Example function: main function in "Demo" class (in "extractCDRfeatures.relation" package)
 */
package extractCDRfeatures.corpora;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringReader;
import java.text.BreakIterator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Random;
import java.util.TreeMap;
import java.util.regex.Pattern;

import edu.stanford.nlp.dcoref.CorefChain;
import edu.stanford.nlp.dcoref.CorefCoreAnnotations;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.StanfordCoreNLP;
import edu.stanford.nlp.process.CoreLabelTokenFactory;
import edu.stanford.nlp.process.PTBTokenizer;
import edu.stanford.nlp.util.CoreMap;
import libsvm.svm_node;
import objectCDR.MentionLocation;
import extractCDRfeatures.corpora.Pair;
import extractCDRfeatures.nlp.ShallowParser;
import uk.ac.man.entitytagger.Mention;
import uk.ac.man.entitytagger.matching.Matcher;
import extractCDRfeatures.utils.FeatureData;
import extractCDRfeatures.utils.NER;
import extractCDRfeatures.nlp.SenData;
import extractCDRfeatures.nlp.Word;
import extractCDRfeatures.relation.CIDPair;
import extractCDRfeatures.relation.Demo;
import extractCDRfeatures.relation.FeatureGenerator;
import extractCDRfeatures.utils.Data;
import extractCDRfeatures.corpora.Entity;
import extractCDRfeatures.corpora.Sentence;

public class TxtReader {
	private Map<String, ArrayList<Word>> entityMap;
	private Pattern pDis;
	private Pattern pChe;
	private ShallowParser parser;
	private Matcher matcher;
	private HashMap<String,String> typeMap= new HashMap<>();
	private StanfordCoreNLP pipeline;
	private int wordThreshold;
	private int senThreshold;
	public TxtReader(){
		entityMap = new HashMap<>();
		pDis = Pattern.compile("DISEASE\\d{1,3}");
		pChe = Pattern.compile("CHEMICAL\\d{1,3}");
		parser = new ShallowParser();
		matcher = NER.createDic(Data.Trigger_path);
		typeMap.put("Disease","2");
		typeMap.put("Chemical", "3");
		typeMap.put("3", "Chemical");
		typeMap.put("2", "Disease");
		pipeline = new StanfordCoreNLP();
		
		//do correference resolution (只用在readTotalTxt)
		wordThreshold = 70;
		//幾個句子以內作correference resolution (只用在readTotalTxt)
		senThreshold = 15;
	}
	private List<Word> createDiseaseChemicalList(List<Entity> list) {
        List<Word> ls = new ArrayList<>();
        entityMap.clear();
        for (Entity en : list) {
            String offset = en.getCharOffset();
            String values[] = offset.split("-|;");
            int locs[] = new int[2];
            locs[0] = Integer.parseInt(values[0]);
            locs[1] = Integer.parseInt(values[1]);
            Word dr = new Word(en.getId(), en.getText(), locs);
            dr.pos = -1;
            
            dr.type = typeMap.get(en.getType()); // to Integer
            ls.add(dr);
            if (entityMap.containsKey(dr.id)){
            	ArrayList<Word> drs = entityMap.get(dr.id);
            	drs.add(dr);
            }
            else{
            	ArrayList<Word> drs = new ArrayList<Word>();
            	drs.add(dr);
            	entityMap.put(dr.id, drs);
            }
            	
            if (dr.id.contains("|"))
            	for (String eID:dr.id.split("\\|"))
            		if (entityMap.containsKey(eID)){
                    	ArrayList<Word> drs = entityMap.get(eID);
                    	drs.add(dr);
                    }
                    else{
                    	ArrayList<Word> drs = new ArrayList<Word>();
                    	drs.add(dr);
                    	entityMap.put(eID, drs);
                    }
        }
        Collections.sort(ls);
        return ls;
    }

    public SenData preparedData(Sentence sens, boolean train) {
        List<Word> diseaseChemicalList = createDiseaseChemicalList(sens.getEntity());
        List<Word> connectList = new ArrayList<>();
        List<Word> negList = new ArrayList<>();
        List<Word> prepList = new ArrayList<>();
        List<Word> ccList = new ArrayList<>();
        List<Word> commaList = new ArrayList<>();
        String text = senSimplify(sens.getText(), diseaseChemicalList).trim();
        SenData sen = new SenData(text, 0, sens.getId());
        sen.long_text = sens.getText();
        sen.relList = detectRelWord(sen.text);
        sen.diseaseChemicalList = diseaseChemicalList;
        parser.initSen(sen);
        int idx_dr = 0;
        int r_idx = 0;
        List<Word> rList = sen.relList;
        for (int i = 0; i < sen.tokens.length; i++) {
            List<String> drList = getArgList(sen.tokens[i]);
            if (drList.size() > 0) {
                for (String name : drList) {
                    diseaseChemicalList.get(idx_dr).pos = i;
                    idx_dr++;
                }
            }
            if (r_idx < rList.size()) {
                if (sen.tokens[i].equals(rList.get(r_idx).word)) {
                    Word trg = rList.get(r_idx);
                    trg.pos = i;
                    trg.posTag = sen.POS[i];
                    r_idx++;
                } else if (sen.tokens[i].contains("-")) {
                    if (sen.tokens[i].endsWith("-" + rList.get(r_idx).word)) {
                        Word trg = rList.get(r_idx);
                        trg.pos = i;
                        trg.posTag = sen.POS[i];
                        r_idx++;
                    }
                    if (r_idx < rList.size() && sen.tokens[i].startsWith(rList.get(r_idx).word + "-")) {
                        Word trg = rList.get(r_idx);
                        trg.pos = i;
                        trg.posTag = sen.POS[i];
                        r_idx++;
                    }
                }
            }
            if (Data.connnectSet.contains(sen.tokens[i].toLowerCase())) {
                Word cond_word = new Word(sen.tokens[i].toLowerCase(), i, null);
                connectList.add(cond_word);
            }
            if (Data.negSet.contains(sen.tokens[i])) {
                Word cond_word = new Word(sen.tokens[i], i, null);
                negList.add(cond_word);
            }
            if (Data.prepSet.contains(sen.tokens[i])) {
                Word cond_word = new Word(sen.tokens[i], i, null);
                prepList.add(cond_word);
            }
            if (Data.ccSet.contains(sen.tokens[i])) {
                Word cond_word = new Word(sen.tokens[i], i, null);
                ccList.add(cond_word);
            }
            if (sen.tokens[i].equals(",")) {
                Word com_word = new Word(sen.tokens[i], i, null);
                commaList.add(com_word);
            }
        }
        sen.connector = connectList;
        sen.relList = rList;
        sen.prepList = prepList;
        sen.negList = negList;
        sen.chunks = parser.parse(sen);
        sen.cidList = createCIDPair(sens, train);
        sen.ccList = ccList;
        sen.commaList = commaList;
        Collections.sort(sen.cidList);
        sen.init = true;
        return sen;
    }
    List<String> getDisArgList(String txt) {
        java.util.regex.Matcher m = pDis.matcher(txt);
        List<String> list = new ArrayList<>();
        while (m.find()) {
            list.add(m.group()); // creating a list of proteins from text ;
        }
        return list;
    }
    List<String> getCheArgList(String txt) {
        java.util.regex.Matcher m = pChe.matcher(txt);
        List<String> list = new ArrayList<>();
        while (m.find()) {
            list.add(m.group()); // creating a list of proteins from text ;
        }
        return list;
    }
    List<String> getArgList(String txt) {
        List<String> list = new ArrayList<>();
        list.addAll(getCheArgList(txt));
        list.addAll(getDisArgList(txt));
        return list;
    }
    public List<Word> detectRelWord(String txt) {
        List<Word> list = new ArrayList<>();
        List<Mention> ls = matcher.match(txt);
        String w;
        int locs[];
        for (Mention m : ls) {
            locs = new int[2];
            w = m.getText();
            locs[0] = m.getStart();
            locs[1] = m.getEnd();
            Word tg = new Word("", w, locs);
            tg.type = m.getIds()[0];
            list.add(tg);
        }
        return list;
    }

    private String senSimplify(String txt, List<Word> ls) {
        StringBuilder sb = new StringBuilder(txt);
        Word w = null;
        int idx = ls.size();
        try {
            for (int i = ls.size() - 1; i >= 0; i--) {
                w = ls.get(i);
                w.name = typeMap.get(w.type).toUpperCase() + idx;	//typeMap to String
                if (sb.substring(w.locs[0], w.locs[1]).length() == w.word.length()) { // equal
                    sb.replace(w.locs[0], w.locs[1], w.name);
                } else if (sb.length() >= w.locs[1] + 1 && sb.substring(w.locs[0], w.locs[1] + 1).length() == w.word.length()) {//+1
                    sb.replace(w.locs[0], w.locs[1] + 1, w.name);
                } else if (sb.length() >= w.locs[1] + 2 && sb.substring(w.locs[0], w.locs[1] + 2).length() == w.word.length()) { //+2
                    sb.replace(w.locs[0], w.locs[1] + 2, w.name);
                } else if (w.locs[0] >= 0 && w.locs[1] > w.locs[0] && sb.length() >= w.locs[1] && sb.substring(w.locs[0], w.locs[1] - 1).length() == w.word.length()) {//-1
                    sb.replace(w.locs[0], w.locs[1] - 1, w.name);
                } else {
                    sb.replace(w.locs[0], w.locs[1] + 1, w.name); // trainning data
                }
                idx--;
            }
        } catch (Exception ex) {
            System.out.println("Unknown cases ---> :\t" + txt);
            System.out.println(sb.toString());
            if (w != null) {
                System.out.println(w.word + "\tLoc:\t" + w.locs[0] + "\t" + w.locs[1]);
            }
        }
        while (sb.length() > 0 && (sb.charAt(sb.length() - 1) == '\n' || sb.charAt(sb.length() - 1) == ' ')) {
            sb = sb.deleteCharAt(sb.length() - 1);
        }
        return sb.toString();
    }
    private List<CIDPair> createCIDPair(Sentence sens, boolean train) {
		List<CIDPair> ddiList = new ArrayList<>();
		Word temp;
		for (Pair pr : sens.getPair()) {
			for (Word d1 : entityMap.get(pr.getE1()))
				for (Word d2 : entityMap.get(pr.getE2())) {
					if (d1.locs[0] > d2.locs[0]) { // d1 > d2 , swap
						temp = d1;
						d1 = d2;
						d2 = temp;
					}
					boolean cid = false;
					if (train) {
						String s_cid = pr.getCid();
						if (s_cid != null) {
							cid = s_cid.equals("true");
						} else {
							s_cid = pr.getInteraction();
							if (s_cid != null) {
								cid = s_cid.equals("true");
							}
						}
					}
					String type = cid ? pr.getType() : "";
					CIDPair pair = new CIDPair(pr.getId(), d1, d2, type, cid);
					pair.chemical = pr.getE1();
					pair.disease = pr.getE2();
					ddiList.add(pair);
				}
		}
		return ddiList;
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
							//System.out.println("Add one disease after resolution");
						}
					}
					else{
						if (!cheMap.containsKey(regionStr)){
							chemicals.add(nMen);
							//System.out.println("Add one chemical after resolution");
						}
					}
				}
			}
		}
	}
    public void matchCIDtoData(String text, ArrayList<MentionLocation> diseases,
			ArrayList<MentionLocation> chemicals,ArrayList<String> cids, String id,Map<String, SenData> allSenData) {
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
				if (diseases.get(i).start >= start && diseases.get(i).end <= end){
					MentionLocation nd = new MentionLocation(diseases.get(i));
					nd.start = nd.start -start;
					nd.end = nd.end -start;
					mDiseases.add(nd);
				}
			}
			for (int i=0;i<chemicals.size();i++){
				if (chemicals.get(i).start >= start && chemicals.get(i).end <= end){
					MentionLocation nc = new MentionLocation(chemicals.get(i));
					nc.start = nc.start -start;
					nc.end = nc.end -start;
					mChemicals.add(nc);
				}
			}
						
			if (mDiseases.size()>0 && mChemicals.size()>0){
				
				Sentence sen = stringToSentence(sentenceStr,outid,mDiseases,mChemicals,cids);
				
				allSenData.put(outid, preparedData(sen,true));
			}
		}
	}
    /* 將文字轉換成SenData
     * 輸入 text: 一段文字(可以是title+abstract)		id: 文章id
     *		diseases: 該段文字包含的diseases			chemicals: 該段文字包含的chemical
     * 輸出每個句子的資料
     */
    public Map<String,SenData> readTotalTxt(String text,String id,ArrayList<MentionLocation> diseases,ArrayList<MentionLocation> chemicals){
    	Map<String,SenData> allSenData = new HashMap<String,SenData>();
    	ArrayList<String> cids = new ArrayList<String>();
    	if (checkCorRes(text))
    		corRes(text,diseases,chemicals);
		matchCIDtoData(text,diseases,chemicals,cids,id,allSenData);
		return allSenData;
    }
    /* 判斷是否要做correference resolution
     * 
     */
    boolean checkCorRes(String text){
  	  BreakIterator border = BreakIterator.getSentenceInstance(Locale.US);
  	  border.setText(text);
  	  
  		int start = border.first();
  		int senCount =0;
  		for (int end = border.next(); end != BreakIterator.DONE; start = end, end = border.next()) {
  			senCount++;
  			StringReader sr = new StringReader(text.substring(start,end));
  			int wordCount =0;
  			PTBTokenizer<CoreLabel> ptbt = new PTBTokenizer<CoreLabel>(sr, new CoreLabelTokenFactory(), "");
  		      while (ptbt.hasNext()) {
  		        ptbt.next();
  		        wordCount++;
  		      }
  		      if (wordCount> wordThreshold)
  		    	  return false;
  		}
  		if (senCount> senThreshold)
  			return false;
  		return true;
    }
    /* Transfer data in sentence level to SenData
     * Input: path: file path(split into sentences and should be in Pubtator format)
     * Output: data of all sentences in SenData format
     */
    public Map<String,SenData> readTxt(String path) throws IOException{
    	Map<String,SenData> allSenData = new HashMap<String,SenData>();
    	
    	FileReader fr = new FileReader(path);
		BufferedReader br = new BufferedReader(fr);
		String senTxt,id;
		while (br.ready()) {
			ArrayList<MentionLocation> diseases=new ArrayList<MentionLocation>();
			ArrayList<MentionLocation> chemicals=new ArrayList<MentionLocation>();
			ArrayList<String> cids=new ArrayList<String>();
			senTxt = br.readLine();
			if (senTxt == null)
				break;
			id = senTxt.split("\\|")[0];
			senTxt = senTxt.split("\\|")[2];
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
				}
			}
			if (containTrigger(senTxt)){
				Sentence sen = stringToSentence(senTxt,id,diseases,chemicals,cids);
			
				allSenData.put(id, preparedData(sen,true));
			}
		}
		
		br.close();
		return allSenData;
    }
    private boolean containTrigger(String sen){
    	// Modified by Ming-Yu Chien
    	// Read all data no matter they contains trigger words or not
    	// We'll check trigger words in "FeatureGenerator" class in "extractCDRfeatures.relation" package
    	/*if (matcher.match(sen).isEmpty())
    		return false;
    	else*/
    		return true;
    }
    /* transfer single sentence into SenData format
     * Input:	senTxt: a sentence text		id: article id
     *			diseases: disease entities in this sentence		chemicals: chemical entities in this sentence
     * Output:	Sentence data of this sentence
     */
    private Sentence stringToSentence(String senTxt,String senID, ArrayList<MentionLocation> diseases, ArrayList<MentionLocation> chemicals, ArrayList<String> cids) {
		Sentence sen = new Sentence();
		sen.entity = new ArrayList<Entity>();
		for (MentionLocation e:diseases)
			sen.entity.add(new Entity(e));
		for (MentionLocation e:chemicals)
			sen.entity.add(new Entity(e));
		sen.id= senID;
		sen.pair = new ArrayList<Pair>();
		int pairID = 0;
		for (String e:cids){
			String chemicalID = e.split("\\|")[0];
			String diseaseID = e.split("\\|")[1];
			
			sen.pair.add(new Pair(senID+"_"+pairID, chemicalID, diseaseID, "true","CID","CID"));
			pairID++;
		}
		HashSet<String> falsePair = new HashSet<String>();
		for (MentionLocation eChe:chemicals)
			for (String eCheID:eChe.mentionID)
				for (MentionLocation eDis:diseases)
					for (String eDisID:eDis.mentionID){
						if (!cids.contains(eCheID+"|"+eDisID) && !falsePair.contains(eCheID+"|"+eDisID)){
							sen.pair.add(new Pair(senID+"_"+pairID,eCheID,eDisID,"false","NULL","NULL"));
							falsePair.add(eCheID+"|"+eDisID);
							pairID++;
						}
					}
		sen.text = senTxt;
		
		return sen;
    }
	private void outCSV(Map<String, FeatureData[]> allFeature) throws IOException{
		PrintWriter  detailWriter;
		for (Entry<String,FeatureData[]> entry:allFeature.entrySet()){
			detailWriter = new PrintWriter("./"+entry.getKey()+".csv");
			for (int i=0;i<entry.getValue().length;i++){
				for (svm_node eData:entry.getValue()[i].getData())
					detailWriter.print(eData.index+",");
				detailWriter.println(entry.getValue()[i].getLabel());
			}
			detailWriter.close();
		}
		
	}
	static public void main(String args[]) throws IOException{
		/* do nothing */
    }
	private void separateTrainTest(Map<String, SenData> allSen,
			Map<String, SenData> trainSen, Map<String, SenData> testSen, int fold) {
		Random rand = new Random();
		for (Entry<String,SenData> eSen:allSen.entrySet())
			if (rand.nextInt(fold) == 0)
				testSen.put(eSen.getKey(), eSen.getValue());
			else
				trainSen.put(eSen.getKey(), eSen.getValue());
	}
	public int getWordThreshold() {
		return wordThreshold;
	}
	public void setWordThreshold(int wordThreshold) {
		this.wordThreshold = wordThreshold;
	}
	public int getSenThreshold() {
		return senThreshold;
	}
	public void setSenThreshold(int senThreshold) {
		this.senThreshold = senThreshold;
	}
	
}
