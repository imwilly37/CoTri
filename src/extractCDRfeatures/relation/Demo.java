/*
 * Copy from http://www.biosemantics.org/uploads/DDI.zip
 * Modified by Ming-Yu Chien
 * Classify whether these entity pairs are Chemical Disease Relation (CDR) or not
 * Important functions: evaluate, testSingleSentence
 * Example function: main
 */
/*
 * Copyright 2014 Chinh Bui.
 * Email: bqchinh@gmail.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */


package extractCDRfeatures.relation;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Random;
import java.util.TreeMap;

import libsvm.svm_model;
import libsvm.svm_parameter;
import extractCDRfeatures.corpora.TxtReader;
import extractCDRfeatures.nlp.SenData;
import extractCDRfeatures.utils.Data;
import extractCDRfeatures.utils.FeatureData;
import extractCDRfeatures.utils.SVMTrain;

/**
 * This file demos the performance of the system against three test datasets: DB2011, DB2013, and ML2013.
 * To run the main method with one of the following parameters: DB2011, DB2013,ML2013
 * @author Chinh
 */
public class Demo {
	private HashMap<String,svm_model> typeModels;
	private HashMap<String,Integer> type2Num;
	private String modelPath,type2NumPath;

	public Demo(){
		modelPath = "./model/model.ser";
		type2NumPath = "./model/type2Num.ser";
		if (typeModels == null){
			try{
			FileInputStream fileIn = new FileInputStream(modelPath);
			ObjectInputStream in = new ObjectInputStream(fileIn);
			typeModels = (HashMap<String,svm_model>) in.readObject();
			in.close();
			fileIn.close();
			}catch(Exception e){
				e.printStackTrace();
			}
		}
		if (type2Num == null){
			try{
				FileInputStream fileIn = new FileInputStream(type2NumPath);
				ObjectInputStream in = new ObjectInputStream(fileIn);
				type2Num = (HashMap<String,Integer>) in.readObject();
				in.close();
				fileIn.close();
				}catch(Exception e){
					e.printStackTrace();
				}
		}
	}
	public Demo(boolean corres){
		if (corres){
			modelPath = "./model/modelR.ser";
			type2NumPath = "./model/type2NumR.ser";
		}else{
			modelPath = "./model/model.ser";
			type2NumPath = "./model/type2Num.ser";
		}
			
		if (typeModels == null){
			try{
			FileInputStream fileIn = new FileInputStream(modelPath);
			ObjectInputStream in = new ObjectInputStream(fileIn);
			typeModels = (HashMap<String,svm_model>) in.readObject();
			in.close();
			fileIn.close();
			}catch(Exception e){
				e.printStackTrace();
			}
		}
		if (type2Num == null){
			try{
				FileInputStream fileIn = new FileInputStream(type2NumPath);
				ObjectInputStream in = new ObjectInputStream(fileIn);
				type2Num = (HashMap<String,Integer>) in.readObject();
				in.close();
				fileIn.close();
				}catch(Exception e){
					e.printStackTrace();
				}
		}
	}
    FeatureGenerator fg = new FeatureGenerator();
    /**
     * Convert DB2013 XML files into objects and parse sentences with a shallow parser
     */
    /*
    private void load_DB_2013_Data(){
      String train_source[] = {"./DDI_corpora/Train2013/DrugBank"};
      String test_source[] ={"./DDI_corpora/Test2013/DrugBank"};
        System.out.println("---> Reading xml files ...");
        // Saving training data
        converter.saveData(Data.Train_DB2013_path, train_source,true);
        // Saving testing data
        converter.saveData(Data.Test_DB2013_path, test_source,true);
        System.out.println("---> Saving ...done");
    }
    */
    
   
    /**
     * Evaluate DB2013 test dataset with the best parameters
     */
    /*
    private  void evaluateTestDB2013() {
        try {
            fg.featureGenerator(Data.Train_DB2013_path, true, false,Data.Train_DB2013Pairs);
            fg.featureGenerator(Data.Test_DB2013_path, false, false,Data.Test_DB2013Pairs);
            Map<String, FeatureData[]> all_data, test_data;
            all_data = (Map<String, FeatureData[]>) Data.read(Data.Train_DB2013Pairs);
            test_data = (Map<String, FeatureData[]>) Data.read(Data.Test_DB2013Pairs);
            double c[]={2,4,1,5,1}; //best C
            double v[] = {0.25, 0.05, 0.15, 0.15, 0.25}; // best gamma
            int true_pairs = countTruePairs(Data.Test_DB2013_path);
            evaluate(all_data, test_data, c, v, true_pairs,"DB2013");
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }*/

    /**
     * Evaluate ML test dataset using mix training data (DB2013 & ML2013)
     * @param allFeatureTest 
     * @param output 
     */
    /*
    private  void evaluateTestML2013Mix() {
        try {
            fg.featureGenerator(Data.Train_MIX2013_path, true, false,Data.Train_MIX2013Pairs);
            fg.featureGenerator(Data.Test_ML2013_path, false, false,Data.Test_ML2013Pairs);
            Map<String, FeatureData[]> all_data, test_data;
            all_data = (Map<String, FeatureData[]>) Data.read(Data.Train_MIX2013Pairs);
            test_data = (Map<String, FeatureData[]>) Data.read(Data.Test_ML2013Pairs);
            int true_pairs = countTruePairs(Data.Test_ML2013_path);
            double c[]={1,4,4,2,2}; //best C
            double v[] = {0.1, 0.05, 0.05, 0.05, 0.05}; // best gamma
            evaluate(all_data, test_data, c, v, true_pairs, "ML2013");
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }
    */
    
    /* Evaluate the result. Print precision, recall, f-socre in this function
     * Can set parameter C and Gamma of SVM in this function
     */
    public void evaluateData(Map<String,FeatureData[]> trainData,Map<String, FeatureData[]> testData, Map<String,SenData> testSen,FeatureGenerator new_fg){
    	fg = new_fg;
    	try {
			String db_name = "BioCreativeTrack3";
			
			int true_pairs = countTruePairs(testSen);
			double c[] = { 1, 4, 4, 2, 2 }; //best C
			double v[] = { 0.1, 0.05, 0.05, 0.05, 0.05 }; // best gamma
			evaluate(trainData, testData, c, v, true_pairs, db_name);
		} catch (Exception e) {
			e.printStackTrace();
		}
    }
    
	private int countTruePairs(Map<String, SenData> senMap) {
		int count = 0;
        // Count unprocess cases due to text processing errors
        try {
            SenData currSen;
            for (Map.Entry<String, SenData> entry : senMap.entrySet()) {
                currSen = entry.getValue();
                for (CIDPair pair : currSen.cidList) {
                    if(pair.cid){
                        count++;
                    }
                }
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return count;
	}

	/**
     * Count positive DDI pairs in order to calculate recall
     * @param path: path to test database
     * @return: number of true pairs 
     */
    private int countTruePairs(String path) {
        int count = 0;
        // Count unprocess cases due to text processing errors
        try {
            Map<String, SenData> senMap = (Map<String, SenData>) Data.read(path);
            SenData currSen;
            for (Map.Entry<String, SenData> entry : senMap.entrySet()) {
                currSen = entry.getValue();
                for (CIDPair pair : currSen.cidList) {
                    if(pair.cid){
                        count++;
                    }
                }
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return count;

    }
    public void evaluate(Map<String, FeatureData[]> trainset, Map<String, FeatureData[]> testset, double C_values[], 
            double G_values[], int pos_pairs,String db_name){
        try {
        	typeModels = new HashMap<String,svm_model>();
        	type2Num = new HashMap<String,Integer>();
            SVMTrain trainer = new SVMTrain();
            svm_parameter para = trainer.getPara();
            para.kernel_type = svm_parameter.RBF;
            para.nr_weight = 1;
            double ww[] = {2};
            para.weight = ww;
            int lb[] = {1};
            para.weight_label = lb;
            double c[]=C_values;
            double v[] = G_values;
            FileWriter wrt = new FileWriter("./"+db_name+"_error_output.csv");
            int i = 0;
            int tp = 0;
            int fp = 0;
            int total = pos_pairs;
            for (String type : fg.out_type) {
            	type2Num.put(type, i);
                int ltp=0,lfp=0;
                para.C = (double) c[i];
                para.gamma = v[i];
                FeatureData[] data = trainset.get(type);
                FeatureData[] test = testset.get(type);
                svm_model model = trainer.train(data, para);
                typeModels.put(type,model);
                for (FeatureData dt : test) {
                    double val = trainer.predict(dt, model);
                    if (dt.getLabel() == 1) {
                        if (val == 1) {
                            ltp++; // true positive
                        } else {
                            wrt.append(dt.id + ",false negative,"+type+"\n");
                        }
                    } else { // true negative
                        if (val == 1) {
                            lfp++; // false positive
                            wrt.append(dt.id + ",false positive,"+type+"\n");
                        }
                    }
                }
                i++;
                //System.out.println(fg.typeCounter.size());
                //System.out.println(type+"\t\tTP:\t"+ltp+"\tFP:\t"+lfp+"\tPrecision:\t"+(ltp)*1f/(ltp+lfp)+"\tRecall:\t"+(ltp*1f)/fg.typeCounter.get(type));
                tp+=ltp;
                fp+=lfp;
            }
			System.out.println("True positives:\t" + tp);
			System.out.println("False positives:\t" + fp);
			double precision = (double) tp / (double) (tp + fp);
			double recall = (double) tp / (double) total;
			double f_score = (2 * precision * recall) / (precision + recall);
			System.out.println("Precision:\t" + precision + "\tRecall:\t" + recall + "\tFscore:\t" + f_score);
			wrt.close();
			
			FileOutputStream fileOut = new FileOutputStream(modelPath);
			ObjectOutputStream out = new ObjectOutputStream(fileOut);
			out.writeObject(typeModels);
			out.close();
			fileOut.close();
			
			FileOutputStream fileOut2 = new FileOutputStream(type2NumPath);
			ObjectOutputStream out2 = new ObjectOutputStream(fileOut2);
			out2.writeObject(type2Num);
			out2.close();
			fileOut2.close();
        }catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    /**
     * DrugBank 2013 demo
     * - Load datasets (Note: path need to be modified) 
     * - Evaluate test set
     */
    /*
    public void demoDB2013(){
        long t1= System.currentTimeMillis();
        load_DB_2013_Data();
        evaluateTestDB2013(); //  path to output errors
        long t2= System.currentTimeMillis();
        System.out.println("Total time:\t"+(t2-t1)/1000);
    }
    */
    
    public void printHelp(){
        System.out.println("Run the system with one of the folling parameters: DB2011, DB2013, ML2013");
            System.out.println("DB2011 -> to evaluate the DB2011 test dataset ");
            System.out.println("DB2013 -> to evaluate the DB2013 test dataset ");
            System.out.println("ML2013 -> to evaluate the ML2013 test dataset ");
    }
    public static void main(String[] args) throws IOException {
    	/* Declare TxtReader class
		 * We can read data in Pubtator format in this class
		 */
    	TxtReader tr= new TxtReader();
    	/* Declare FeatureGenerator class
    	 * We can generate feature vectors from text data in this class
    	 */
    	FeatureGenerator fg = new FeatureGenerator();
    	/* Declare Demo class
    	 * We can classify the candidate entity pairs in this class
    	 * We can set the flag of co-reference in this class (Doing co-reference or not)
    	 */
    	Demo dm = new Demo(false);
    	
    	/* Reading training data & testing data. Notice that these data should be split into sentences in Pubtator Format(not in abstract-level)*/
    	/* If you want to split data into sentence, please check "AbstractsToSentences" class in "preprocess" package*/
    	/* Or if you still want to read entire abstract data, you can read files yourself and use "readTotalTxt" function in "textReader" class*/
    	/* The main data directory:
    	 * CDR_Data/CDR_Data/CDR.Corpus/ : for all test set
    	 */
    	String trainDataPath = "./CDR_Data/CDR_Data/CDR.Corpus/CDR_sentences.txt";	// All sentences in training set and development set
    	String testDataPath = "./CDR_Data/CDR_Data/CDR.Corpus/CDR_TestSet_Sentences.PubTator.txt"; // Sentences in testing set
    	Map<String,SenData> trainSen= tr.readTxt(trainDataPath);
    	Map<String,SenData> testSen=tr.readTxt(testDataPath);
    	
    	/* Transfer training & testing data into feature vector*/
    	Map<String,FeatureData[]> allFeatureTrain = fg.featureGenerator(trainSen, true, false, "./traindata.ser");
    	Map<String,FeatureData[]> allFeatureTest = fg.featureGenerator(testSen, false, false, "./testdata.ser");
    	
    	/*print the output of evaluation,(precision,recall and f-score)*/
    	dm.evaluateData(allFeatureTrain,allFeatureTest, testSen,fg);
    	
    	/* The following codes are added by Ming-Yu Chien for printing evaluation file (in Eval format)
    	 * Can also see PubtatorToEval.java in errorAnalysis package!
    	 */
    	String evalFilePath = "./CDR_Data/CDR_Data/CDR.Corpus/CDR_TestSet_CoTri.Eval.txt";
    	PrintWriter pw = new PrintWriter(evalFilePath);
    	HashMap<String,String> cidList = new HashMap<String,String>();
    	for (Entry<String,SenData> eTest:testSen.entrySet()){
    		Map<String,SenData> eTestMap = new TreeMap<String,SenData>();
    		eTestMap.put(eTest.getKey(), eTest.getValue());
    		Map<String,FeatureData[]> eFeatureTest = fg.featureGenerator(eTestMap, false, false, "./eTestData.ser");
    		for (Entry<String,FeatureData[]> eFT:eFeatureTest.entrySet()){
    			boolean testL;
    			for (FeatureData eFD : eFT.getValue())
    				if (eFT.getValue() != null || eFT.getValue().length>0){
	    				testL = dm.testSinglePair(eFT.getKey(),eFD);
	    				if (testL){
	    					String docID = eTest.getKey().substring(0, eTest.getKey().indexOf("_"));
	    					if (!eFD.chemical.equals("-1") && !eFD.disease.equals("-1"))
	    					cidList.put(docID+"|"+eFD.chemical+"|"+eFD.disease,docID+"\tCID\t"+eFD.chemical+"\t"+eFD.disease+"\t1.0");
	    				}
    				}
    		}
    	}
    	for (Entry<String,String> eCID:cidList.entrySet()){
    		pw.println(eCID.getValue());
    	}
    	pw.close();
    }

    /* Classify one pair whether it is a chemical disease relation or not
     * Input: type: The type of this pair(one of five types)   fd: The feature data of this type
     * Output: is a chemical disease relation (CDR) or not
     */
	public boolean testSinglePair(String type, FeatureData fd) {
		
		svm_model model = typeModels.get(type);
		 SVMTrain trainer = new SVMTrain();
		 double c[] = { 1, 4, 4, 2, 2 }; //best C
		 double v[] = { 0.1, 0.05, 0.05, 0.05, 0.05 }; // best gamma
		 svm_parameter para = trainer.getPara();
         para.kernel_type = svm_parameter.RBF;
         para.nr_weight = 1;
         double ww[] = {2};
         para.weight = ww;
         int lb[] = {1};
         para.weight_label = lb;
         para.C = (double) c[type2Num.get(type)];
         para.gamma = v[type2Num.get(type)];
         
		 return trainer.predict(fd,model)==1;
	}
}
