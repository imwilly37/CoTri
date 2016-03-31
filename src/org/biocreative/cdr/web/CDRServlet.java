/* Written by Ming-Yu Chien
 * For BioCreativeV track 3, receive data form RESTful api
 * In this class, the input(abstract or file) will be processed by named entity recognition(NER) and chemical-disease relation extraction(CDRE)
 * Before running the function in this class, you should turn on the poll version of NER tools(DNorm and tmChem)
 * You can see more details about NER tools in "doc" directory or in the directory of each tool 
 * Example function: main function
 */

package org.biocreative.cdr.web;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import extractCDRfeatures.corpora.Sentence;
import extractCDRfeatures.corpora.TxtReader;
import extractCDRfeatures.nlp.SenData;
import extractCDRfeatures.relation.Demo;
import extractCDRfeatures.relation.FeatureGenerator;
import extractCDRfeatures.utils.FeatureData;
import objectCDR.MentionLocation;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Map.Entry;

public class CDRServlet extends HttpServlet {
	private TxtReader tr;
	private Demo dm;
	private FeatureGenerator fg;
	
	public CDRServlet(){
		tr = new TxtReader();
		tr.setWordThreshold(0);
		tr.setSenThreshold(0);
		dm = new Demo();
		fg = new FeatureGenerator();
	}

  @Override
  public void doGet(HttpServletRequest request, HttpServletResponse response)
      throws ServletException, IOException {
    response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED, "All services require POST.");
  }

  @Override
  public void doPost(HttpServletRequest request, HttpServletResponse response)
      throws ServletException, IOException {

    // test parameters
    String format = request.getParameter("format");
    if (format == null) {
      response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Missing parameter format ");
      return;
    }

    int run = 1;
    String setString = request.getParameter("run");
    if (setString != null) {
      try {
        run = Integer.parseInt(setString);
      } catch (NumberFormatException e) {
        response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Cannot parse parameter set: " +
            setString);
        return;
      }
    }
    if (!(1 <= run && run <= 3)) {
      response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Run has to be one of 1, 2, 3: " +
          run);
      return;
    }

    // read data
    Optional<String> optional = readData(request);
    if (!optional.isPresent()) {
      response.sendError(HttpServletResponse.SC_BAD_REQUEST, "No data is found in the POST.");
      return;
    }

    // test format
    String data = optional.get();
    if (!checkFormat(data, format)) {
      response.sendError(HttpServletResponse.SC_BAD_REQUEST,
          String.format("The input text is not in %s format", format));
      return;
    }

    try {
      String result = annotate(data, run);
      PrintWriter out = response.getWriter();
      out.print(result);
      out.flush();
      out.close();
      response.setStatus(HttpServletResponse.SC_OK);
    } catch (Exception e) {
      response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
          "Internal server error");
    }
  }

  /**
   * Read data from the http post
   *
   * @param request the http post
   * @return data from the http post
   */
  private Optional<String> readData(HttpServletRequest request) {
    try {
      StringBuilder sb = new StringBuilder();
      String line = null;
      BufferedReader reader = request.getReader();
      while ((line = reader.readLine()) != null) {
        sb.append(line).append('\n');
      }
      if (sb.length() == 0) {
        return Optional.empty();
      } else {
        return Optional.of(sb.toString());
      }
    } catch (IOException e) {
      return Optional.empty();
    }
  }

  private boolean checkFormat(String data, String format) {
    if (format.equals("pubtator")) {
      // test pubtator format
      return true;
    } else if (format.equals("bioc")) {
      // test bioc format
      return true;
    } else {
      return false;
    }
  }

  private String annotate(String data, int run) throws IOException {
	  defaultSetting(run);
	  
	  String id = data.split("\n")[0].split("\\|")[0];
		String title  = data.split("\n")[0].split("\\|")[2];
		String ab  = data.split("\n")[1].split("\\|")[2];
	  
		File tmf = File.createTempFile("0"+run+"_", ".txt", new File("./tmChem/PollInput/"));
		String tmPath = tmf.getAbsolutePath();
		BufferedWriter bw = new BufferedWriter(new FileWriter(tmf));
		bw.write(data);
		bw.close();
		File dnf = File.createTempFile("0"+run+"_", ".txt", new File("./DNorm/PollInput/"));
		String dnormPath = dnf.getAbsolutePath();
		bw = new BufferedWriter(new FileWriter(dnf));
		bw.write(data);
		bw.close();
		ArrayList<MentionLocation> chemicals = getEntities(tmPath,tmPath.replace("Input", "Output"));
		ArrayList<MentionLocation> diseases = getEntities(dnormPath,dnormPath.replace("Input", "Output"));
		
		File deletef = new File(tmPath.replace("Input", "Output"));
		deletef.delete();
		deletef = new File(dnormPath.replace("Input", "Output"));
		deletef.delete();
		
		
		
		Map<String,SenData> allSenData = tr.readTotalTxt(title+"\n"+ab, id, diseases, chemicals);
		fg = new FeatureGenerator();
		
		Map<String,FeatureData[]> eFeatureTest = fg.featureGenerator(allSenData, false, false, "./eTestData.ser");
		
		HashMap<String,String> cidList = new HashMap<String,String>();
		HashSet<String> chemicalList = new HashSet<String>();
		HashSet<String> diseaseList = new HashSet<String>();
		for (Entry<String,FeatureData[]> eFT:eFeatureTest.entrySet()){
			boolean testL;
			for (FeatureData eFD : eFT.getValue())
				if (eFT.getValue() != null || eFT.getValue().length>0){
    				testL = dm.testSinglePair(eFT.getKey(),eFD);
    				if (testL){
    					if (!eFD.chemical.equals("-1") && !eFD.disease.equals("-1")){
    						cidList.put(eFD.chemical+"|"+eFD.disease,id+"\tCID\t"+eFD.chemical.replace("MESH:", "")+"\t"+eFD.disease.replace("MESH:", "")+"\t1.0");
    						chemicalList.add(eFD.chemical);
    						diseaseList.add(eFD.disease);
    					}
    				}
				}
		}
		
		for (MentionLocation e:diseases){
			for (String eID:e.mentionID)
				if (diseaseList.contains(eID)) {
					String dID = "";
					for (String eIDstr:e.mentionID)
						if (dID.length() == 0)
							dID += eIDstr.replace("MESH:", "");
						else
							dID += "|" + eIDstr.replace("MESH:", "");
					data+= id+"\t"+ e.start +"\t"+ e.end +"\t"+e.mentionStr+"\tDisease\t"+dID+"\t1.0\n";
					break;
				}
		}
		
		for (MentionLocation e:chemicals){
			for (String eID:e.mentionID)
				if (chemicalList.contains(eID)) {
					String cID = "";
					for (String eIDstr:e.mentionID)
						if (cID.length() == 0)
							cID += eIDstr.replace("MESH:", "");
						else
							cID += "|" + eIDstr.replace("MESH:", "");
					data+= id+"\t"+ e.start +"\t"+ e.end +"\t"+e.mentionStr+"\tChemical\t"+cID+"\t1.0\n";
					break;
				}
		}
		
		for (Entry<String,String> entry:cidList.entrySet()){
			data+= entry.getValue()+"\n";
		}
		return data;
  }
  private ArrayList<MentionLocation> getEntities(String inPath, String outPath) throws NumberFormatException, IOException{
		File fout = new File(outPath);
		File fin = new File(inPath);
		for (int i = 0; i < 10000; i++) {
			if (fout.exists() && !fin.exists())
				break;
			try {
				Thread.sleep(500);
			} catch (InterruptedException ex) {
				Thread.currentThread().interrupt();
			}
			if (i == 9999) {
				System.err.println("No File: " + outPath);
				System.exit(0);
			}
		}

		FileReader fr = new FileReader(outPath);
		BufferedReader br = new BufferedReader(fr);		
		String line, id;
		ArrayList<MentionLocation> entities = new ArrayList<MentionLocation>();
		line = br.readLine();
		line = br.readLine();
		id = line.split("\\|")[0];
		while (br.ready()) {
			String text = br.readLine();
			if (!text.contains("\t"))
				break;
			String[] textp = text.split("\t");
			
			int start = Integer.parseInt(textp[1]);
			int end = Integer.parseInt(textp[2]);
			String menText = textp[3];
			String[] menID = textp[5].split("\\|");
			MentionLocation men = new MentionLocation(start, end, textp[4], menText, menID, id);
			entities.add(men);

		}
		br.close();
		return entities;
  }
  public void defaultSetting(int run){
		if (run == 1) {
			tr.setWordThreshold(0);
			tr.setSenThreshold(0);
			fg.setFMPath("./data/featureMaps.ser");
			dm = new Demo(false);
		} else if (run == 2) {
			tr.setWordThreshold(45);
			tr.setSenThreshold(15);
			fg.setFMPath("./data/featureMaps.ser");
			dm = new Demo(true);
		} else {
			tr.setWordThreshold(999);
			tr.setSenThreshold(99);
			fg.setFMPath("./data/featureMaps.ser");
			dm = new Demo(true);
		}
  }
  public void runDataset (String filePath,String outPath,int run) throws IOException{
	  String titleWithID,abWithID,id;
	  FileReader fr = new FileReader(filePath);
	  BufferedReader br = new BufferedReader(fr);
	  
	  HashSet<String> totalFile = new HashSet<String>();
	  PrintWriter writer = new PrintWriter(outPath, "UTF-8");
	  
	  while (br.ready()) {
			titleWithID = br.readLine();
			abWithID = br.readLine();
			if (titleWithID == null || abWithID == null)
				break;
			id = titleWithID.split("\\|")[0];
			
			while (br.ready()) {	// if there are entities or cid information
				String text=br.readLine();
				if (!text.contains("\t"))
					break;
			}
			
			if (totalFile.contains(id))
				continue;
			else
				totalFile.add(id);
			String data = annotate(titleWithID+"\n"+abWithID+"\n",run);
			writer.println(data);
		}
	  br.close();
	  writer.close();
  }
  public static void main(String args[]) throws IOException{
	  CDRServlet cdrs = new CDRServlet();
	  // Use annotate function to do chemical-disease relation extraction
	  // An example for annotating an abstract
	  String medline="2385256|t|Myasthenia gravis presenting as weakness after magnesium administration.\n2385256|a|We studied a patient with no prior history of neuromuscular disease who became virtually quadriplegic after parenteral magnesium administration for preeclampsia. The serum magnesium concentration was 3.0 mEq/L, which is usually well tolerated. The magnesium was stopped and she recovered over a few days. While she was weak, 2-Hz repetitive stimulation revealed a decrement without significant facilitation at rapid rates or after exercise, suggesting postsynaptic neuromuscular blockade. After her strength returned, repetitive stimulation was normal, but single fiber EMG revealed increased jitter and blocking. Her acetylcholine receptor antibody level was markedly elevated. Although paralysis after magnesium administration has been described in patients with known myasthenia gravis, it has not previously been reported to be the initial or only manifestation of the disease. Patients who are unusually sensitive to the neuromuscular effects of magnesium should be suspected of having an underlying disorder of neuromuscular transmission.";
	  System.out.println(cdrs.annotate(medline, 1));
	  // Another example for annotating an abstract
	  medline ="354896|t|Lidocaine-induced cardiac asystole.\n354896|a|Intravenous administration of a single 50-mg bolus of lidocaine in a 67-year-old man resulted in profound depression of the activity of the sinoatrial and atrioventricular nodal pacemakers. The patient had no apparent associated conditions which might have predisposed him to the development of bradyarrhythmias; and, thus, this probably represented a true idiosyncrasy to lidocaine.";
	  System.out.println(cdrs.annotate(medline, 1));
	  
	  // If you want to annotate entire dataset, use "runDataset" function
	  // You can set data path that you want to annotate, output file path and the run number
	  // You can see more details of run number in "Usage.txt" documents
	  String annotateDataPath = "./CDR_Data/CDR_Data/CDR.Corpus/CDR_TestSet.PubTator.txt";
	  String outPath = "./CDR_Data/CDR_Data/CDR.Corpus/CDR_TestSet_CoTri.PubTator.txt";
	  int run = 3;
	  cdrs.runDataset(annotateDataPath,outPath,3);
  }
}
