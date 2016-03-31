/*
 * Copy from http://www.biosemantics.org/uploads/DDI.zip
 * Modified by Ming-Yu Chien
 * Generate the feature vector，only a few difference after I modified
 * Modified part 1: Delete the unnecessary part (Could find by "delete by Ming-Yu Chien")
 * Modified part 2: Fix some errors in my case(Could find by "edit by Ming-Yu Chien")
 * Modified part 3: Add a new feature(Could find by "add new feature by Ming-Yu Chien")
 * Example function: main function in "Demo" class (in "extractCDRfeatures.relation" package)
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
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import libsvm.svm_model;
import libsvm.svm_parameter;
import extractCDRfeatures.nlp.Chunk;
import extractCDRfeatures.nlp.SenData;
import extractCDRfeatures.nlp.Word;
import extractCDRfeatures.utils.Data;
import extractCDRfeatures.utils.SVMTrain;
import static extractCDRfeatures.utils.Data.appoSet;
import static extractCDRfeatures.utils.Data.beSet;
import static extractCDRfeatures.utils.Data.ccSet;
import static extractCDRfeatures.utils.Data.subclauseSet;
import extractCDRfeatures.utils.FeatureIndex;
import extractCDRfeatures.utils.FeatureData;
import extractCDRfeatures.utils.Lemmatizer;
import negex.CallKit;
import negex.GenNegEx;

/**
 *
 * @author Chinh
 */
public class FeatureGenerator {

    List<Chunk> chunkList = null;
    SenData currSen = null; // current senData
    String senID;
    Set<String> vocSet = null; // vocabulary
    Map<String, FeatureData[]> fdataMap = new HashMap<>(); // vectors
    String f_names[] = {"sub_feature", "obj_feature", "np", "verb", "auxiliary", "syntactic", "lexical_feature"};
    public String[] out_type = {"subject", "object", "clause", "clause2", "np"};
    String[] sub_vector = {f_names[0], f_names[3], f_names[4], f_names[6], f_names[6], f_names[5], f_names[5]};
    String[] obj_vector = {f_names[1], f_names[3], f_names[4], f_names[6], f_names[6], f_names[5], f_names[5]};
    String[] clause_vector = {f_names[3], f_names[5], f_names[5], f_names[4], f_names[6], f_names[6]};
    String[] clause2_vector = {f_names[5], f_names[3], f_names[5], f_names[3], f_names[4], f_names[6], f_names[6]};
    String[] np_vector = {f_names[2], f_names[6], f_names[6], f_names[4], f_names[5], f_names[5]};
    Map<String, String[]> nameMaps = new HashMap<>();
    private Word left_con, right_con;
    boolean ddi_type = true;
    Map<String, FeatureIndex> featureMap = new HashMap<>(); // store feature index by name
    Map<String, List<CIDPair>> outputMap = new HashMap<>(); // output of extracted feature
    Lemmatizer lemma = new Lemmatizer(Data.Dictionary_path);
    private String featureMapPath;
    
    //private PrintWriter  detailWriter;
    //delete by Ming-Yu Chien
    //static Connection conn = null;       

    /**
     * Define number of feature vector for each
     */
    /**
     * Define number of feature vector for each
     */
    public FeatureGenerator() {
        // Initialize indexes
        nameMaps.put(out_type[0], sub_vector);
        nameMaps.put(out_type[1], obj_vector);
        nameMaps.put(out_type[2], clause_vector);
        nameMaps.put(out_type[3], clause2_vector);
        nameMaps.put(out_type[4], np_vector);
        loadTriggers(Data.Trigger_path);
        featureMapPath = Data.FeatureMaps;
        		
        /*try{
        	detailWriter = new PrintWriter("./detail.txt");
        }catch (Exception e){
        	e.printStackTrace();
        }*/
        
	// set up a connection to the DB of known PDDIs
        // delete by Ming-Yu Chien
        /*
	try {
	    conn = DriverManager.getConnection("jdbc:mysql://localhost/merged_DDIs?user=mergedPddi&password=pddi");
	} catch (SQLException ex) {
	    // handle any errors
	    System.out.println("SQLException: " + ex.getMessage());
	    System.out.println("SQLState: " + ex.getSQLState());
	    System.out.println("VendorError: " + ex.getErrorCode());
	    System.exit(1);
	}*/
    }

    private boolean between(int begin, int end, int pos) {
        return pos >= begin && pos <= end;
    }

    /**
     * Check whether a trigger has a negative word
     *
     * @param pos
     * @param negs
     * @return
     */
    private boolean hasNegation(int pos, List<Word> negs) {
        int trg_pos = WPos2Chunk(pos);
        for (Word w : negs) {
            int c_pos = WPos2Chunk(w.pos);
            if (c_pos == trg_pos && pos > w.pos) { // same chunk, neg_pos < trg_pos
                return true;
            }
        }
        return false;
    }

    private FeatureData getPhraseFeatures(SenData sen, int pos1, int pos2, boolean train, String f_name, CIDPair pair) {
        FeatureData vector = new FeatureData();
        Set<String> features = new HashSet<>();
        int begin = sen.chunks.get(pos1).begin;
        int end = sen.chunks.get(pos2).end;

        FeatureIndex fdx = featureMap.get(f_name);
        if (fdx == null && train) {
            fdx = new FeatureIndex();
            featureMap.put(f_name, fdx);
        }
        List<Word> trgs = getTriggers(sen, begin, end);
        if (trgs.isEmpty()) {
            return null;
        }
        if (fdx == null) {
        	
            System.out.println("BUG ----> ");
            
            return null;
        }
        int cpos1 = WPos2Chunk(pair.arg1.pos);
        int cpos2 = WPos2Chunk(pair.arg2.pos);
        if (cpos1 == cpos2) { // same chunk
            features.add("same_chunk");
        } else {
            features.add("not_same_chunk");
        }
        String[] tk = sen.tokens; //
        if (!trgs.isEmpty()) {
            Set<String> flist = formNPData(pair, trgs);
            features.addAll(flist);
        }
        for (int i = pair.arg1.pos + 1; i <= pair.arg2.pos; i++) { // bag_of_words features between arg1 and arg2
            String txt = tk[i].toLowerCase();
            if (!txt.startsWith("chemical") && !txt.startsWith("disease") && (vocSet.contains(txt))) {
                features.add(txt);
            }
            if (txt.equals(":") && !tk[i - 1].equals("following")) {
                features.add("has_separator");
            } else if (tk[i].equals(":") && tk[i - 1].equals("following")) {
                features.add("following:");
            }
        }
        // adding feature surrounding drug1
        if (pair.arg1.pos - 1 >= begin) { // before
            String val = tk[pair.arg1.pos - 1].toLowerCase();
            if (val.equals("(") || val.equals(",")) {
                features.add(val + "_entity1");
            }
        }

        if (tk[pair.arg1.pos + 1].equals(",") || tk[pair.arg1.pos + 1].equals(")")) { // after
            features.add("entity1_" + tk[pair.arg1.pos + 1]);
        }
        //
        if (pair.arg1.pos + 2 == pair.arg2.pos) {
            features.add("entity1_" + tk[pair.arg1.pos + 1] + "_entity2");
        }
        // "," or ")"
        if (pair.arg2.pos - 1 >= begin) {
            String val = tk[pair.arg2.pos - 1].toLowerCase();
            if (!val.startsWith("chemical") && !val.startsWith("disease")) { // skip drug name
                features.add(val + "_entity2");
            }
        }
        if (pair.arg2.pos + 1 <= end) {
            String val = tk[pair.arg2.pos + 1].toLowerCase();
            if (!val.startsWith("chemical")  && !val.startsWith("disease")) {
                features.add("entity2_" + val);
            }
        }

        List<Word> wlist;
        //preps list
        wlist = getWords(sen.prepList, pair.arg1.pos, pair.arg2.pos);
        if (!wlist.isEmpty()) {
            Word prev = null;
            for (Word w : wlist) {
                features.add(w.word);
                if (prev != null) {
                    features.add(prev.word + "_" + w.word);
                }
                prev = w;
            }

        } else {
            features.add("no_preposition_between");
        }

        // Negation
        wlist = getWords(sen.negList, begin, pair.arg2.pos);
        if (!wlist.isEmpty()) {
            for (Word w : wlist) {
                //features.add(w.word);
                features.add(w.word + "_" + tk[w.pos + 1]);
            }
        } else {
            features.add("no_negation");
        }
        // rel list

        wlist = getWords(sen.relList, begin, pair.arg2.pos);
        if (!wlist.isEmpty()) {
            for (Word w : wlist) {
                if (hasNegation(w.pos, sen.negList)) {
                    features.add("no_" + w.word.toLowerCase());
                } else {
                    features.add(w.word.toLowerCase());
                }
            }
        } else {
            features.add("no_trigger_word");
        }
        /*
        for (String s:features){
        	detailWriter.println("Phrase feature: "+s);
        }*/

        if (train) { // training phase: add to feature vector
            for (String s : features) {
                int idx = fdx.add(s);
                vector.add(idx, 1);
            }
        } else { // testing phase: retrieve from feature vector
            for (String s : features) {
                int idx = fdx.getIndex(s);
                if (idx >= 0) {
                    vector.add(idx, 1);
                }
            }
        }
        // Generate bag of words
        return vector;
    }

    boolean hasCommaBetween(int pos1, int pos2) {
        Chunk c;
        for (int i = pos1 + 1; i < pos2; i++) {
            c = chunkList.get(i);
            if (c.txt.equals(",")) {
                return true;
            }
        }
        return false;
    }

    private Set<String> formNPData(CIDPair r, List<Word> trgs) {
        Set<String> flist = new HashSet<>();
        Word trg;
        int pos1 = WPos2Chunk(r.arg1.pos);
        int pos2 = WPos2Chunk(r.arg2.pos);
        int type;
        for (int i = 0; i < trgs.size(); i++) {
            trg = trgs.get(i);
            type = Integer.parseInt(trg.type);
            int tg_pos = WPos2Chunk(trg.pos);
            if (hasNegation(trg.pos, currSen.negList)) {
                flist.add("neg_" + trg.word);
                //continue;
            }
            if (type >= 5 || type < 2) {
                continue;
            }
            String trg_word = trg.word.toLowerCase();
            if (trg.posTag.startsWith("NN")) {
                if (tg_pos < pos1) { // REL - PREP - DRUG - PREP - DRUG
                    String prep1, prep2;
                    int count = countPreps(tg_pos, pos1);
                    if (count == 0 || count >= 2) {
                        continue;
                    }
                    count = countPreps(pos1, pos2);
                    if (count == 0 || count > 2) {
                        continue;
                    }
                    prep2 = findPrep(pos1, pos2);
                    prep1 = findPrep(tg_pos, pos1);
                    flist.add(trg_word + "_on_left");
                    if (prep1 != null) { // Finding prep2, can also be 'AND' for some cases.
                        flist.add(trg_word + "_" + prep1 + "_entity1");
                    } else {
                        flist.add(trg_word + "_no_prep_entity1");
                    }
                    if (prep2 != null) {
                        if (prep1 != null) {
                            flist.add(trg_word + "_" + prep1 + "_" + prep2);
                        }
                        flist.add(trg_word + "_" + prep1 + "_entity1_" + prep2 + "_entity2");
                    }
                } else if (pos1 < tg_pos && tg_pos < pos2) {
                    String prep1, prep2;
                    prep1 = findPrep(pos1, tg_pos);
                    prep2 = findPrep(tg_pos, pos2);
                    if (prep1 != null) { // Finding prep2, can also be 'AND' for some cases.
                        flist.add("entity1_" + prep1 + "_" + trg_word);
                    } else {
                        flist.add("entity1_no_prep_" + trg_word);
                    }
                    if (prep1 != null && prep2 != null) {
                        flist.add("entity1_" + prep1 + "_" + trg.word + "_" + prep2 + "_entity1");
                    } else {
                        flist.add("between_entity_" + trg_word);
                    }
                    if (prep2 != null) {
                        flist.add(trg.word + "_" + prep2 + "_entity1");
                    }
                }
            } else if (trg.posTag.startsWith("VB")) { // check use event with trg having VB tag (not main verb)
                // the search limit to subject or object
                if (pos1 < tg_pos && pos2 > tg_pos) { // DRUG1 - TRG - DRUG2
                    String prep = findPrep(pos1, tg_pos);
                    if (prep != null) {
                        flist.add("entity1_" + prep + "_" + trg_word);
                    } else {
                        flist.add("entity1_no_prep_" + trg_word);
                    }
                    prep = findPrep(tg_pos, pos2);
                    if (prep != null) {
                        flist.add(trg_word + "_" + prep + "_entity2");
                    } else {
                        flist.add(trg_word + "_no_prep_entity2");
                    }
                    flist.add("between_pos_" + trg_word);
                } else if (tg_pos <= pos1 && tg_pos < pos2) { // TG - DRUG1- DRUG 2
                    flist.add(trg_word + "_on_left");
                    String prep = findPrep(tg_pos, pos1);
                    if (prep != null) {
                        flist.add(trg_word + "_" + prep + "_entity1");
                    } else {
                        flist.add(trg_word + "_no_prep_entity1");
                    }
                    prep = findPrep(pos1, pos2);
                    if (prep != null) {
                        flist.add("entity1_" + prep + "_entity2");
                    } else {
                        flist.add("entity1_no_prep_entity2");
                    }
                } else if (tg_pos > pos1 && tg_pos > pos2) { // DRUG1 - DRUG2 - TG
                    flist.add(trg_word + "_on_right");
                    String prep = findPrep(pos2, tg_pos);
                    if (prep != null) {
                        flist.add("entity2_" + prep + "_" + trg_word);
                    } else {
                        flist.add("entity2_no_prep_" + trg_word);
                    }
                    prep = findPrep(pos1, pos2);
                    if (prep != null) {
                        flist.add("entity1_" + prep + "_entity2");
                    } else {
                        flist.add("entity1_no_prep_entity2");
                    }
                }
            }
        }
        return flist;
    }

    private int countPreps(int pos1, int pos2) {
        if ((pos1 == pos2)) {
            Chunk c = chunkList.get(pos1);
            if (c.txt.contains(" and ")) {
                return 1;
            }
        } else {
            int count = 0;
            for (int i = pos1 + 1; i < pos2; i++) {
                Chunk c = chunkList.get(i);
                if (c.type.equals("PP") && Data.prepSet.contains(c.txt)) {
                    count++;
                }
            }
            return count;
        }
        return 0;
    }

    private FeatureData getLexicalFeatures(SenData sen, int begin, int end, int pos, String f_name, boolean train, int len, boolean subject) {
        FeatureData vector = new FeatureData();
        Set<String> features = new HashSet<>();
        FeatureIndex fdx = featureMap.get(f_name);
        int pos1 = chunkList.get(begin).begin;
        int pos2 = chunkList.get(end).end;
        if (fdx == null && train) {
            fdx = new FeatureIndex();
            featureMap.put(f_name, fdx);
        }
        if (fdx == null) {
            return null;
        }
        if (begin > end) {
            return null;
        }
        String tk[] = sen.tokens;
        int start, stop;
        if (subject) {
            start = Math.max(pos1, pos - 3);
            for (int i = start; i < pos; i++) {
                String txt = tk[i].toLowerCase();
                if (!txt.startsWith("chemical") && !txt.startsWith("disease")) {
                    features.add(tk[i].toLowerCase() + "_left");
                }
            }
            stop = Math.min(pos2, pos + 3);
            for (int i = pos + 1; i < stop; i++) {
                String txt = tk[i].toLowerCase();
                if (!txt.startsWith("chemical") && !txt.startsWith("disease")) {
                    features.add(tk[i].toLowerCase() + "_right");
                } else {
                    // features.add("arg_right");
                }
            }
        } else {
            String POS[] = sen.POS;
            int c_pos = WPos2Chunk(pos);
            start = Math.max(begin, c_pos - 3);
            pos1 = chunkList.get(start).begin;
            for (int i = pos1; i < pos; i++) {
                String txt = lemma.lemmatize(tk[i], POS[i]);
                features.add(txt + "_left");
            }
            stop = Math.min(end, c_pos + len);
            pos2 = chunkList.get(stop).end;
            for (int i = pos + 1; i < pos2; i++) {
                String txt = lemma.lemmatize(tk[i], POS[i]);
                features.add(txt + "_right");
            }
        }
        /*for (String s:features)
        	detailWriter.println("Lexical feature: "+s);
        	*/
        if (train) {
            for (String s : features) {
                int idx = fdx.add(s);
                vector.add(idx, 1);
            }
        } else { // test
            for (String s : features) {
                int idx = fdx.getIndex(s);
                if (idx >= 0) {
                    vector.add(idx, 1);
                }
            }
        }
        return vector;
    }

    private List<String> getPrepFeatures(String pre, int pos1, int pos2, List<Chunk> ls) {
        List<String> list = new ArrayList<>();
        String txt2 = pre + "_";
        int idx = 0, np = 0;
        Chunk c;
        if (pos1 == pos2) {
            txt2 += "no_prep_";
            list.add(txt2);
            return list;
        }
        for (int i = pos1; i < pos2; i++) {
            c = ls.get(i);
            if (Data.prepSet.contains(c.txt)) {
                txt2 = txt2 + c.txt + "_" + idx;
                idx++;
            }
        }
        c = ls.get(pos2);
        if (Data.prepSet.contains(c.txt)) {
            txt2 = txt2 + c.txt;
        }
        list.add(txt2);
        return list;
    }

    private FeatureData getSyntaticFeatures(SenData sen, int begin, int end, int pos, String f_name, boolean train) {
        FeatureData vector = new FeatureData();
        Set<String> features = new HashSet<>();
        FeatureIndex fdx = featureMap.get(f_name);

        int pos1 = chunkList.get(begin).begin;
        int pos2 = chunkList.get(end).end;
        int arg_pos = WPos2Chunk(pos);
        if (fdx == null && train) {
            fdx = new FeatureIndex();
            featureMap.put(f_name, fdx);
        }
        if (fdx == null) {
            return null;
        }
        // count number of prep from begin to arg_pos
        int prep_count = 0, noun_count = 0;
        for (int i = begin; i <= arg_pos; i++) {
            Chunk c = chunkList.get(i);
            if (c.type.equals("PP")) {
                features.add(c.txt + "_" + prep_count);
                prep_count++;
            } else if (c.type.equals("NP")) {
                features.add(c.type + noun_count);
                noun_count++;
            }
        }
        features.add("Prep_count_" + prep_count);
        features.add("Noun_count_" + noun_count);
        List<Word> ls = getWords(sen.relList, pos1, pos2);
        if (ls != null && !ls.isEmpty()) {
            for (Word w : ls) {
                int tg_pos = WPos2Chunk(w.pos);
                int type = Integer.parseInt(w.type);
                String pre = w.pos < pos ? "l_" : "r_";
                String tg_word = pre + w.word.toLowerCase();
                if (hasNegation(w.pos, sen.negList)) {
                    tg_word = "neg_" + w.word.toLowerCase();
                }
                if (type >= 2 && type < 5 && tg_pos>=0) {
                    List<String> ft = getPrepFeatures(tg_word, tg_pos, arg_pos, chunkList);
                    features.addAll(ft);
                } else {
                    features.add(tg_word);
                }
            }

        } else {
            features.add("no_rel_word");
        }
        // find drug immediately followed this drug
        List<Word> drLs = getWords(sen.diseaseChemicalList, pos, pos2);
        if (drLs.isEmpty()) {
            features.add("_no_other_argument");
        } else {
            features.add("_has_other_arguments");
            int j = 0;
            int arg_count = 1;
            while (j < drLs.size()) {
                Word dr = drLs.get(j);
                int dr_pos = WPos2Chunk(dr.pos);
                if (dr_pos == arg_pos) { // same chunk, check 'and'
                    String tk[] = sen.tokens;
                    for (int i = pos + 1; i < dr.pos; i++) {
                        if (tk[i].equals("and") || tk[i].equals(",")) {
                            features.add(tk[i] + "_arg");
                            break;
                        }
                    }
                } else { // different chunk
                    List<Word> preps = getPrep(pos, dr.pos, sen.prepList);
                    if (!preps.isEmpty()) {
                        String v = "";
                        for (Word p : preps) {
                            int p_pos = WPos2Chunk(p.pos);
                            if (p_pos + 1 == dr_pos) { // prep_arg
                                features.add(p.word + "_arg");
                            } else {
                                features.add("_no_link_with_arg");
                            }
                            break;
                        }
                        features.add(v + "_arg");
                        break;
                    } else {
                        features.add("arg_" + arg_count);
                        arg_count++;
                    }
                }
                j++;
            }
        }
        /*for (String s:features)
        	detailWriter.println("Syntatic feature: "+s);*/
        if (train) {
            for (String s : features) {
                int idx = fdx.add(s);
                vector.add(idx, 1);
            }
        } else { // test
            for (String s : features) {
                int idx = fdx.getIndex(s);
                if (idx >= 0) {
                    vector.add(idx, 1);
                }
            }
        }
        return vector;
    }

    private FeatureData getVBPhraseFeatures(SenData sen, Chunk vb_chunk, String f_name, boolean train) {
        FeatureData vector = new FeatureData();
        FeatureIndex fdx = featureMap.get(f_name);
        if (fdx == null && train) {
            fdx = new FeatureIndex();
            featureMap.put(f_name, fdx);
        }
        if (fdx == null) {
            return null;
        }
        Set<String> features = new HashSet<>();
        int pos1 = vb_chunk.begin;
        int pos2 = vb_chunk.end;
        String tk[] = sen.tokens;
        List<Word> ls = getWords(sen.relList, pos1, pos2);
        if (ls.isEmpty()) {
            features.add("no_trigger_word");
            //return null;
        } else {
            features.add("has_trigger_word");
            for (Word w : ls) {
                if (hasNegation(w.pos, sen.negList)) {
                    features.add("neg_" + w.word.toLowerCase());
                } else {
                    features.add(w.word.toLowerCase() + "_" + w.posTag);
                }
            }
        }
        ls = getWords(sen.negList, pos1, pos2);
        if (ls != null && !ls.isEmpty()) {
            for (Word w : ls) {
                features.add(w.word.toLowerCase());
            }
        } else {
            features.add("no_negation_word");
        }
        String txt = null;
        for (int i = pos1; i <= pos2; i++) {
            String token = tk[i].toLowerCase();
            features.add(token);
            if (txt != null) {
                features.add(txt + "_" + token);
            }
            txt = tk[i].toLowerCase();
        }
        int c_pos = sen.chunks.indexOf(vb_chunk);
        if (c_pos < sen.chunks.size() - 1) {
            Chunk c = sen.chunks.get(c_pos + 1);
            if (c.type.equals("ADVP")) {
                features.add(c.txt + "_right_pos");
            }
        }
        if (pos1 > 0 && Data.connnectSet.contains(tk[pos1 - 1].toLowerCase())) {
            features.add(tk[pos1 - 1] + "_left_pos");
        }
        if (pos1 == pos2 && Data.beSet.contains(tk[pos1])) { // is_a
            if (c_pos < sen.chunks.size() - 1) {
                Chunk c = sen.chunks.get(c_pos + 1);
                if (c.type.equals("NP") || c.type.equals("ADJP")) {
                    features.add(c.txt + "_right_pos");
                }
            }
        }
        
        /*for (String s:features)
        	detailWriter.println("VBPhrase feature: "+s);*/
        
        if (train) {
            for (String s : features) {
                int idx = fdx.add(s);
                vector.add(idx, 1);
            }
        } else {
            for (String s : features) {
                int idx = fdx.getIndex(s);
                if (idx >= 0) {
                    vector.add(idx, 1);
                }
            }
        }
        return vector;
    }

    /**
     * Generating semantic feature vector for a given pair.
     *
     * @param sen
     * @param pair
     * @param pos1: position of arg 1 (by chunk)
     * @param pos2: position of arg 2 (by chunk)
     * @param f_name
     * @param train
     * @return: feature vector
     */
    public FeatureData getAuxiliaryFeatures(SenData sen, CIDPair pair, int pos1, int pos2, String f_name, boolean train) {
        FeatureData vector = new FeatureData();
        Set<String> features = new HashSet<>();
        FeatureIndex fdx = featureMap.get(f_name);
        int begin = sen.chunks.get(pos1).begin;
        int end = sen.chunks.get(pos2).end;
        if (fdx == null && train) {
            fdx = new FeatureIndex();
            featureMap.put(f_name, fdx);
        }
        if (fdx == null) {
            return null;
        }
        int cpos1 = WPos2Chunk(pair.arg1.pos);
        int cpos2 = WPos2Chunk(pair.arg2.pos);
        if (cpos1 == cpos2) { // same chunk
            features.add("same_chunk");
        } else {
            features.add("not_same_chunk");
        }

        if (pair.arg1.word.toLowerCase().startsWith("chemical") || pair.arg2.word.toLowerCase().startsWith("disease")) {
            features.add("arg_has_entity_word");
        }
        if (pair.arg1.word.toLowerCase().startsWith("disease") || pair.arg2.word.toLowerCase().startsWith("chemical")) {
            features.add("arg_has_entity_word");
        }
        String[] tk = sen.tokens; //
        for (int i = pair.arg1.pos + 1; i <= pair.arg2.pos; i++) { // bag_of_words features between arg1 and arg2
            String txt = tk[i].toLowerCase();
            if (txt.equals(":") && !tk[i - 1].equals("following")) {
                features.add("has_separator");
            } else if (tk[i].equals(":") && tk[i - 1].equals("following")) {
                features.add("following:");
            }
            if (Data.connnectSet.contains(txt)) {
                features.add("betw_con_" + txt);
            }
        }
        if (left_con != null) {
            int con_pos = WPos2Chunk(left_con.pos);
            if (con_pos >= pos1 && con_pos < pos2) {
                String txt = left_con.word.toLowerCase();
                if (Data.connnectSet.contains(txt)) {
                    features.add("left_con_" + txt);
                }
            }
        }
        // adding feature surrounding drug1
        if (pair.arg1.pos - 1 >= begin) { // before
            String val = tk[pair.arg1.pos - 1].toLowerCase();
            features.add(val + "_entity1");
        }

        features.add("entity1_" + tk[pair.arg1.pos + 1]);
        //
        if (pair.arg1.pos + 2 == pair.arg2.pos) {
            features.add("entity1_" + tk[pair.arg1.pos + 1] + "_entity2");
        }
        // "," or ")"
        if (pair.arg2.pos - 1 >= begin) {
            String val = tk[pair.arg2.pos - 1].toLowerCase();
            features.add(val + "_entity2");
        }
        if (pair.arg2.pos + 1 <= end) {
            String val = tk[pair.arg2.pos + 1].toLowerCase();
            features.add("entity1_" + val);
        }
        features.add(pair.arg1.type+"_"+pair.arg2.type);
        
           // 增加一個negex feature
        // add new feature by Ming-Yu Chien
        if (extractNegation(sen.text))
        	features.add("sen_affirmed");
        else
        	features.add("sen_negated");
        
        /*for (String s:features)
        	detailWriter.println("Auxiliary feature: "+s);*/
        
        if (train) {
            for (String s : features) {
                int idx = fdx.add(s);
                vector.add(idx, 1);
            }
        } else {
            for (String s : features) {
                int idx = fdx.getIndex(s);
                if (idx >= 0) {
                    vector.add(idx, 1);
                }
            }
        }
        return vector;
    }

    private boolean extractNegation(String senText){
		GenNegEx g = new GenNegEx(false);
		String sentence = CallKit.cleans(senText);
	   String scope = g.negScope(sentence);	   
	   if (scope.equals("-1")) 
			return false;
	   else
	   	return true;
	}
    private List<Word> getTriggers(SenData sen, int begin, int end) {
        List<Word> ls = new ArrayList<>();
        for (Word w : sen.relList) {
            if (w.pos >= begin && w.pos <= end) {
                int vb_type = Integer.parseInt(w.type);
                if (vb_type >= 2 && vb_type < 5) {
                    ls.add(w);
                }
            }
        }
        return ls;
    }

    /**
     * Generating feature vector for a given pair when it belongs to object
     *
     * @param sen
     * @param pos1: start of object (chunk)
     * @param pos2: end of object (chunk)
     * @param f_name: feature idx name
     * @param pair: interaction pair (PPI/DDI)
     * @return: feature vector
     */
    private boolean hasTrigger(List<Word> ls) {
        for (Word w : ls) {
            int value = Integer.parseInt(w.type);
            if (value > 1 && value < 5) {
                return true;
            }
        }
        return false;
    }

    int skip_true_pairs = 0;

    /**
     * Feature generator. This method generate features for DDI pairs from a
     * given dataset
     *
     * @param db_path: path to (object) data converted from XML files
     * @param train: where this is a training file, if yes, then feature indexes
     * are generated.
     * @param useToken: for lexical feature; use chunk or token
     */
    public Map<String,FeatureData[]> featureGenerator(Map<String,SenData> senMap, boolean train, boolean useToken, String out_path) {
	boolean checkForKnownPDDIs = false;

        outputMap.clear();
        fdataMap.clear();
        for (String s : out_type) {
            outputMap.put(s, new ArrayList<CIDPair>()); // list of DDI pairs where each pair belongs to an output type
        }
        int chunk_len = 4;
        if (useToken) {
            chunk_len = 3;
        }
        int total = 0;
        int all = 0;
        int skip_count = 0;
        int unknown = 0;
        int unknown_true = 0;
        int false_ls_all = 0, false_ls_true = 0;
        int sen_count = 0, sen_skip_count = 0;
        skip_true_pairs = 0;
        Set<CIDPair> check = new HashSet<>();
	
	// set up a connection to the database of known PDDIs
        //delete by Ming-Yu Chien
	/*try {
	    conn = DriverManager.getConnection("jdbc:mysql://localhost/merged_DDIs?user=mergedPddi&password=pddi");
	} catch (SQLException ex) {
	    // handle any errors
	    System.out.println("SQLException: " + ex.getMessage());
	    System.out.println("SQLState: " + ex.getSQLState());
	    System.out.println("VendorError: " + ex.getErrorCode());
	    System.exit(1);
	}*/
        if (!train && featureMap.isEmpty()) { // load feature map from database
            try {
                System.out.println("");
                System.out.println("Loading feature indexes .....");
                featureMap = (Map<String, FeatureIndex>) Data.read(featureMapPath);
            } catch (Exception ex) {
                ex.printStackTrace();
            }
            if (featureMap == null) {
                System.out.println("Failed to read feature index data");
                System.exit(1);
            }
        }

        for (Map.Entry<String, SenData> entry : senMap.entrySet()) {
            senID = entry.getKey();
            currSen = entry.getValue();
            
            /*detailWriter.println("Sentence: "+currSen.long_text);
            detailWriter.println("Sentence: "+currSen.text);*/
            
            chunkList = currSen.chunks;
            sen_count++;
            int count = countTruePairs(currSen.cidList);
            if (currSen.cidList != null) {
                all += currSen.cidList.size();
            }
            total += count;
            if (!hasTrigger(currSen.relList)) {
		if (!checkForKnownPDDIs){
		    //System.out.println("Skipping sentence because no trigger found:\n\t" + currSen.long_text);
		    skip_true_pairs += count;
		    skip_count += currSen.cidList.size();
		    sen_skip_count++;
		    check.addAll(currSen.cidList);
		    continue;
		} else {
			System.err.println("ERROR at check for Known PDDIs");
		    /*if (!testForKnownPDDI(currSen.ddiList)){
			//System.out.println("Skipping sentence because no trigger found:\n\t" + currSen.text);
			System.out.println("Skipping sentence because no trigger found and no pair not present in a PDDI within the merged database:\n\t" + currSen.long_text);
			skip_true_pairs += count;
			skip_count += currSen.ddiList.size();
			sen_skip_count++;
			check.addAll(currSen.ddiList);
			continue;
		    } else {
			System.out.println("Processing sentences - No trigger found but data for at least one drug pair mentioned is present in the merged PDDI dataset:\n\t" + currSen.long_text);
		    }*/
		}
            }
            List<Chunk> verbs = findVerbChunk(currSen);
            //loop over list of 'main' verb
            FeatureData[] vectors;
            for (CIDPair pair : currSen.cidList) {
            	/*detailWriter.println("CID: "+pair.arg1.word+"|"+pair.arg2.word);*/
                if (pair.arg1.word.toLowerCase().equals(pair.arg2.word.toLowerCase())) {
                    if (pair.cid) {
                        skip_true_pairs++;
                    }
                    skip_count++;
                    check.add(pair);
                    continue;
                }
                int pos1 = WPos2Chunk(pair.arg1.pos);
                int pos2 = WPos2Chunk(pair.arg2.pos);
                int start = 0;
                boolean has_break = false;
                share_subject = false;
                if (verbs.size() > 0) { // has clause ; otherwise check NP
                    int idx = 0;
                    int vb_pos;
                    int last_stop = chunkList.size() - 1;
                    int next_stop;
                    int vb_pos2 = 0;
                    while (idx < verbs.size()) {
                        //setup values
                        left_con = checkConnector(start, -1); // first connection.
                        Chunk vb = verbs.get(idx);
                        vb_pos = chunkList.indexOf(vb);
                        if (idx < verbs.size() - 1) { // more verb,
                            Chunk vb2 = verbs.get(idx + 1);
                            vb_pos2 = chunkList.indexOf(vb2);
                            right_con = checkConnector(vb_pos, vb_pos2); // connection between two verbs
                            next_stop = findBreak(vb_pos, vb_pos2, chunkList);
                            has_break = false;
                            share_subject = false;
                            if (next_stop != -1) {
                                has_break = true;
                            } else {
                                next_stop = findStop(vb_pos, vb_pos2);
                            }
                        } else {
                            next_stop = last_stop;
                        }
                        if (between(start, next_stop, pos1) && between(start, next_stop, pos2)) { // pair within single clause
                            if (between(start, vb_pos, pos1) && between(start, vb_pos, pos2)) {
                            	/*detailWriter.println("type: subject");*/
                                vectors = new FeatureData[sub_vector.length];
                                vectors[0] = getPhraseFeatures(currSen, start, vb_pos, train, sub_vector[0], pair);// sub feature
                                vectors[1] = getVBPhraseFeatures(currSen, vb, sub_vector[1], train); // verb
                                vectors[2] = getAuxiliaryFeatures(currSen, pair, pos1, pos2, sub_vector[2], train);
                                vectors[3] = getLexicalFeatures(currSen, start, vb_pos - 1, pair.arg1.pos, sub_vector[3], train, 3, useToken);
                                vectors[4] = getLexicalFeatures(currSen, start, vb_pos - 1, pair.arg2.pos, sub_vector[4], train, 3, useToken);
                                vectors[5] = getSyntaticFeatures(currSen, start, vb_pos - 1, pair.arg1.pos, sub_vector[5], train);
                                vectors[6] = getSyntaticFeatures(currSen, start, vb_pos - 1, pair.arg2.pos, sub_vector[6], train);
                                if (vectors[0] != null) {
                                	while (fdataMap.containsKey(pair.id)){
                                		pair.id += "#";
                                	}
                                    fdataMap.put(pair.id, vectors);
                                    List<CIDPair> ids = outputMap.get("subject");
                                    ids.add(pair);
                                } else {
                                    skip_count++;
                                    if (pair.cid) {
                                        skip_true_pairs++;
                                    }
                                }
                                check.add(pair);
                                break;
                            } else if (between(vb_pos, next_stop, pos1) && between(vb_pos, next_stop, pos2)) {
                            	/*detailWriter.println("type: object");*/
                                vectors = new FeatureData[obj_vector.length];
                                vectors[0] = getPhraseFeatures(currSen, vb_pos + 1, next_stop, train, obj_vector[0], pair);// obj feature
                                vectors[1] = getVBPhraseFeatures(currSen, vb, obj_vector[1], train); // verb
                                vectors[2] = getAuxiliaryFeatures(currSen, pair, pos1, pos2, obj_vector[2], train);
                                vectors[3] = getLexicalFeatures(currSen, vb_pos, next_stop, pair.arg1.pos, obj_vector[3], train, 2, false);
                                vectors[4] = getLexicalFeatures(currSen, vb_pos, next_stop, pair.arg2.pos, obj_vector[4], train, 2, false);
                                                               
                                if (vectors[0] != null) {
                                	while (fdataMap.containsKey(pair.id)){
                                		pair.id += "#";
                                	}
                                    fdataMap.put(pair.id, vectors);
                                    List<CIDPair> ids = outputMap.get("object");
                                    ids.add(pair);
                                } else {
                                    skip_count++;
                                    if (pair.cid) {
                                        skip_true_pairs++;
                                    }
                                }
                                check.add(pair);
                                break;
                            } else {
                            	/*detailWriter.println("type: clause");*/
                                vectors = new FeatureData[clause_vector.length];
                                vectors[0] = getVBPhraseFeatures(currSen, vb, clause_vector[0], train); // verb
                                vectors[1] = getLexicalFeatures(currSen, start, vb_pos - 1, pair.arg1.pos, clause_vector[4], train, chunk_len, false); // np - subject
                                vectors[2] = getLexicalFeatures(currSen, vb_pos + 1, next_stop, pair.arg2.pos, clause_vector[5], train, chunk_len, false); // np - object
                                vectors[3] = getAuxiliaryFeatures(currSen, pair, pos1, pos2, clause_vector[3], train);
                                vectors[4] = getSyntaticFeatures(currSen, start, vb_pos - 1, pair.arg1.pos, clause_vector[1], train);
                                vectors[5] = getSyntaticFeatures(currSen, vb_pos, next_stop, pair.arg2.pos, clause_vector[2], train);
                                while (fdataMap.containsKey(pair.id)){
                            		pair.id += "#";
                            	}
                                fdataMap.put(pair.id, vectors);
                                List<CIDPair> ids = outputMap.get("clause");
                                if (vectors[0] != null) {
                                    ids.add(pair);
                                } else {
                                    skip_count++;
                                    if (pair.cid) {
                                        skip_true_pairs++;
                                    }
                                }
                                check.add(pair);
                                break;
                            }
                        } else if (pos1 >= start && pos1 <next_stop && pos2 > next_stop) { // Drug1 belongs clause1, drug2 might belong to clause2
                            if (has_break) {
                                if (pair.cid) {
                                    skip_true_pairs++;
                                }
                                skip_count++;
                                check.add(pair);
                                break;
                            }
                            int next2stop;
                            if (idx + 2 == verbs.size()) {
                                next2stop = last_stop;
                            } else if (idx + 2 < verbs.size()) {
                                Chunk next2vb = verbs.get(idx + 2);
                                int next2vb_pos = chunkList.indexOf(next2vb);
                                right_con = checkConnector(vb_pos2, next2vb_pos);
                                next2stop = findStop(vb_pos2, next2vb_pos);
                            } else {
                                next2stop = -1;
                                System.out.println("---------> cannot be here !!!!!!!");
                            }
                            if (pos2 > next_stop && pos2 < next2stop) { // Drug2 belongs to clause2
                                Chunk vb2 = verbs.get(idx + 1);
                                if (share_subject) {
                                    if (pos1 < vb_pos && pos2 > vb_pos2) { // single verb feature
                                    	/*detailWriter.println("type: clause");*/
                                        vectors = new FeatureData[clause_vector.length];
                                        vectors[0] = getVBPhraseFeatures(currSen, vb2, clause_vector[0], train); // verb
                                        vectors[1] = getLexicalFeatures(currSen, start, vb_pos - 1, pair.arg1.pos, clause_vector[4], train, chunk_len, false); // np - subject
                                        vectors[2] = getLexicalFeatures(currSen, vb_pos2 + 1, next_stop, pair.arg2.pos, clause_vector[5], train, chunk_len, false); // np - object
                                        vectors[3] = getAuxiliaryFeatures(currSen, pair, pos1, pos2, clause_vector[3], train);
                                        vectors[4] = getSyntaticFeatures(currSen, start, vb_pos - 1, pair.arg1.pos, clause_vector[1], train);
                                        vectors[5] = getSyntaticFeatures(currSen, vb_pos2, next2stop, pair.arg2.pos, clause_vector[2], train);
                                        while (fdataMap.containsKey(pair.id)){
                                    		pair.id += "#";
                                    	}
                                        fdataMap.put(pair.id, vectors);
                                        List<CIDPair> ids = outputMap.get("clause");
                                        if (vectors[0] != null) {
                                            ids.add(pair);
                                        } else {
                                            skip_count++;
                                            if (pair.cid) {
                                                skip_true_pairs++;
                                            }
                                        }
                                        check.add(pair);
                                        break;
                                    }
                                }
                                /*detailWriter.println("type: clause2");*/
                                vectors = new FeatureData[clause2_vector.length];
                                if (pos1 < vb_pos) {
                                    vectors[0] = getLexicalFeatures(currSen, start, vb_pos - 1, pair.arg1.pos, clause2_vector[5], train, 3, false); // between arg1 and arg2
                                    vectors[5] = getSyntaticFeatures(currSen, start, vb_pos - 1, pair.arg1.pos, clause2_vector[0], train);
                                } else {
                                    vectors[0] = getLexicalFeatures(currSen, vb_pos + 1, next_stop - 1, pair.arg1.pos, clause2_vector[5], train, 3, false); // between arg1 and arg2
                                    vectors[5] = getSyntaticFeatures(currSen, vb_pos + 1, next_stop - 1, pair.arg1.pos, clause2_vector[0], train);
                                }
                                vectors[1] = getVBPhraseFeatures(currSen, vb, clause2_vector[1], train); // verb
                                if (pos2 < vb_pos2) {
                                    vectors[2] = getLexicalFeatures(currSen, next_stop + 1, vb_pos2 - 1, pair.arg2.pos, clause2_vector[6], train, 3, false); //np -sub
                                    vectors[6] = getSyntaticFeatures(currSen, next_stop + 1, vb_pos2 - 1, pair.arg2.pos, clause2_vector[2], train);
                                } else {
                                    vectors[2] = getLexicalFeatures(currSen, vb_pos2 + 1, next2stop, pair.arg2.pos, clause2_vector[6], train, 3, false);  // np- object
                                    vectors[6] = getSyntaticFeatures(currSen, vb_pos2 + 1, next2stop, pair.arg2.pos, clause2_vector[2], train);
                                }

                                vectors[3] = getVBPhraseFeatures(currSen, vb2, clause2_vector[3], train); // verb 2
                                vectors[4] = getAuxiliaryFeatures(currSen, pair, start, next2stop, clause2_vector[4], train); // semantic type
                                while (fdataMap.containsKey(pair.id)){
                            		pair.id += "#";
                            	}
                                fdataMap.put(pair.id, vectors);
                                List<CIDPair> ids = outputMap.get("clause2");
                                ids.add(pair);
                                check.add(pair);
                                break;
                            } else { // unknown case
                                unknown++;
                                if (pair.cid) {
                                    unknown_true++;
                                }
                                check.add(pair);
                            }
                        }
                        idx++;
                        if (has_break) {
                            start = next_stop + 1;
                        } else {
                            start = next_stop + 1;
                        }
                    }

                } else { // phrase cases
                    int end = chunkList.size() - 1;
                    if (pos1 >= start && pos1 <= end && pos2 >= start && pos2 <= end) {
                        vectors = new FeatureData[np_vector.length];
                        vectors[0] = getPhraseFeatures(currSen, 0, end, train, np_vector[0], pair);// np_sub
                        vectors[1] = getLexicalFeatures(currSen, 0, end, pair.arg1.pos, np_vector[1], train, 3, false);
                        vectors[2] = getLexicalFeatures(currSen, 0, end, pair.arg2.pos, np_vector[2], train, 3, false);
                        vectors[3] = getAuxiliaryFeatures(currSen, pair, 0, end, np_vector[3], train);
                        if (vectors[0] != null) {
                        	while (fdataMap.containsKey(pair.id)){
                        		pair.id += "#";
                        	}
                            fdataMap.put(pair.id, vectors);
                            List<CIDPair> ids = outputMap.get("np");
                            ids.add(pair);
                        } else {
                            skip_count++;
                        }
                        check.add(pair);
                    }

                }
            }
            /*detailWriter.println();*/
        }
        
        // Count unprocess cases due to text processing errors
        for (Map.Entry<String, SenData> entry : senMap.entrySet()) {
            currSen = entry.getValue();
            for (CIDPair pair : currSen.cidList) {
                if (!check.contains(pair)) {
                    unknown++;
                    if(pair.cid){
                        unknown_true++;
                    }
                }
            }
        }
        /*System.out.println("\n\n");
        System.out.println("All pairs:\t" + all);
        System.out.println("True pairs:\t" + total);
        System.out.println("Negative pairs:\t" + (all - total));
        System.out.println("Skip true pairs:\t" + skip_true_pairs);
        System.out.println("Skip (all) count:\t" + skip_count + " Skip neg:" + (skip_count - skip_true_pairs));
        System.out.println("Unknown cases:\t" + unknown + "\ttrue pairs:\t" + unknown_true + "\t" + "true negatives:\t" + (unknown - unknown_true));
        System.out.println("Sen_count:\t" + sen_count);
        System.out.println("Sen_skip:\t" + sen_skip_count);
        System.out.println("\n");*/
        
        // merging vectors and save feature vectors into databases
        Map<String, FeatureData[]> out_map = new HashMap<>();
        int true_count;
        for (String s : out_type) { // group of DDI type (subject,object...)
            true_count = 0;
            List<CIDPair> data = outputMap.get(s);

            FeatureData[] ls = new FeatureData[data.size()];
            out_map.put(s, ls);// type -> list of vector
            // Prepare size for merged vector
            String flist[] = nameMaps.get(s);
          //edit by Ming-Yu Chien
            int size[] = new int[7];	
            for (int i = 0; i < flist.length; i++) {
                FeatureIndex fdx = featureMap.get(flist[i]);
                if (fdx != null) {
                    size[i] = fdx.getSize(); // size of each feature vector
                } else {
                    size[i] = 0;
                }
            }
            if (flist.length ==6)
            	size[6]=0;
            // end edit
            int idx = 0;
            for (CIDPair pair : data) { // loop over list of IDs for each group;
                if (pair.cid) {
                    true_count++;
                }
                FeatureData[] fdt = fdataMap.get(pair.id);
                FeatureData v = FeatureData.mergeVector(fdt, size);
                v.setLabel(pair.cid ? 1 : 0);
                v.id = pair.id;
                v.chemical = pair.chemical;
                v.disease = pair.disease;
                ls[idx] = v;
                idx++;
            }
            if (!train) {
                typeCounter.put(s, true_count);
            }
            //System.out.println("Type:\t" + s + "\t\ttrue pairs:\t" + true_count + "\tNeg count:\t" + (ls.length - true_count));
        }
        //System.out.println("");
        if (train) {
            // Save to databases
            try {
                System.out.println("Storing training data");
                Data.write(out_path, out_map); // pairs
                // save feature vector:
                System.out.println("Storing feature vectors...");
                System.out.println("");
                for (String s1 : featureMap.keySet()) {
                    System.out.println("Feature type:\t" + s1 + "\t -> number of featues:\t" + featureMap.get(s1).getSize());
                }
                Data.write(Data.FeatureMaps, featureMap); // feature maps
                System.out.println("");
            } catch (Exception ex) {
                ex.printStackTrace();
            }

        } else { // test data, store into test database
            try {
                Data.write(out_path, out_map); // pairs
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }
        
        //detailWriter.close();
        return out_map;
    }

    public Map<String, Integer> typeCounter = new HashMap<String, Integer>(); // for calculate recall

    public static void main(String[] args) {
        FeatureGenerator fg = new FeatureGenerator();
        
    }

    public Map<String, SenData> readData(String path) {

        try {
            Map<String, SenData> senMap = (Map<String, SenData>) Data.read(path);
            return senMap;
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return null;
    }

    private void loadTriggers(String path) {
        vocSet = new HashSet<>();
        try {
            FileReader fr = new FileReader(path);
            BufferedReader reader = new BufferedReader(fr);
            String txt;
            String st[];
            while ((txt = reader.readLine()) != null) {
                st = txt.split("\t");
                vocSet.add(st[1].toLowerCase());
            }
            reader.close();
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    /**
     * Check connector (when, if, since...) of two relative clauses.
     *
     * @param begin: of the clause
     * @param end: end=-1 ; check connector on the left, end > 0 ; check
     * connector between begin/end
     * @return: true if found a connector, false otherwise.
     */
    private Word checkConnector(int begin, int end) {
        if (end == -1) { // check connector on the left
            if (!currSen.connector.isEmpty()) {
                for (Word w : currSen.connector) {
                    int pos = WPos2Chunk(w.pos);
                    if (pos == begin || pos - 1 == begin) {
                        return w;
                    }
                }
            }
        } else {// check connector between begin and end
            for (Word w : currSen.connector) {
                int pos = WPos2Chunk(w.pos);
                if (between(begin + 1, end, pos)) {
                    return w;
                }
            }
        }
        return null;
    }

    private int countTruePairs(List<CIDPair> ls) {
        int count = 0;
        for (CIDPair pair : ls) {
            if (pair.cid) {
                count++;
            }
        }
        return count;
    }

    /**
     * Get triggers from a given position
     *
     * @param trigger: list of triggers
     * @param pos1: start (token)
     * @param pos2: end (tokens)
     * @return: triggers belong to the given position
     */
    private List<Word> getWords(List<Word> trigger, int pos1, int pos2) {
        List<Word> list = new ArrayList<>();
        for (int i = 0; i < trigger.size(); i++) {
            Word tg = trigger.get(i);
            if (tg.pos >= pos1 && tg.pos <= pos2) {
                list.add(tg);
            }
        }
        return list;
    }

    /**
     * Get list of prep as string between pos1 and pos2
     *
     * @param pos1
     * @param pos2
     * @return : string of prep if exist
     */
    private String findPrep(int pos1, int pos2) {
        String txt = "";
        if ((pos1 == pos2)) {
            Chunk c = chunkList.get(pos1);
            if (c.txt.contains(" and ")) {
                return "and";
            }
        } else {
            for (int i = pos1 + 1; i < pos2; i++) {
                Chunk c = chunkList.get(i);
                if (Data.prepSet.contains(c.txt)) {
                    if (txt.isEmpty()) {
                        txt = c.txt;
                    } else {
                        txt = txt + "_" + c.txt;
                    }
                }
            }
        }
        if (txt.isEmpty()) {
            return null;
        } else {
            return txt;
        }
    }

    private boolean hasNounChunk(int start, List<Chunk> ls, int stop) {
        Chunk c;
        for (int i = start; i <= stop; i++) {
            c = ls.get(i);
            if (c.type.equals("NP") && !subclauseSet.contains(c.txt)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Determine verb chunk type: passive/active/gerund
     *
     * @param vb
     * @return:0 -> active; 1 -> passive ;2-> VBN ; 3 -> VBing ; 4 -> to VB
     */
    private int getVerbChunkType(Chunk vb, SenData sen) {
        boolean has_be = false;
        boolean VBN = false;
        String tokens[] = sen.tokens;
        String POS[] = sen.POS;
        if (vb.txt.toLowerCase().startsWith("to ") && vb.end > vb.begin) {
            return 4;
        }
        for (int i = vb.begin; i <= vb.end; i++) {
            if (beSet.contains(tokens[i])) {
                has_be = true;
            }
            if (POS[i].equals("VBN")) {
                VBN = true;
            }
        }
        if (POS[vb.end].equals("VBG") && !has_be) {
            return 3;
        }
        if ((vb.end > vb.begin && has_be) && VBN) {
            return 1;
        }
        if (VBN) {
            return 2;
        }
        return 0;
    }
    /**
     * First step: remove
     *
     * @param ls
     */
    boolean skip_phrase = false; // do not skip PP phrase

    /**
     * print chunks contain marker word
     *
     * @param chunk
     */
    public void printChunk(List<Chunk> chunk) {
        for (Chunk c : chunk) {
            System.out.print("[" + c.type + " " + c.txt + "]");
        }
        System.out.println("");
    }

    public void printChunk(int start, List<Chunk> chunk, int stop) {
        for (int t = start; t <= stop; t++) {
            System.out.print("[" + chunk.get(t).type + " " + chunk.get(t).txt + "] ");
        }
        System.out.println("");
    }

    
    /**
     * Word position to Chunk position
     *
     * @param pos
     * @return
     */
    /**
     * Get list of verb
     *
     * @param chunk
     * @return
     */
    private List<Chunk> getConjList(int start, int end, List<Chunk> ls) {
        List<Chunk> comma = new ArrayList<>();
        Chunk c;
        for (int i = start + 1; i <= end; i++) {
            c = ls.get(i);
            if (c.type.equals("O") && ccSet.contains(c.txt)) {
                comma.add(c);
            }
        }
        return comma;
    }

    public List<Chunk> findVerbChunk(SenData sen) {
        List<Chunk> ls = new ArrayList<>();
        int i = 0;
        Chunk c, prev = null;
        while (i < sen.chunks.size()) {
            c = sen.chunks.get(i);
            if (c.type.equals("VP") && !appoSet.contains(c.txt)
                    && !(prev != null && subclauseSet.contains(prev.txt) && prev.type.equals("NP"))) {
                if (getVerbChunkType(c, sen) < 2 && !c.txt.startsWith("as ")) { // skip VB+ing and To VB
                    ls.add(c);
                }
            }
            prev = c;
            i++;
        }
        return ls;
    }

    public boolean print_chunk = false;
    public int count_chunk = 0;
    /**
     * Determine splitter between two verb chunks
     *
     * @param start
     * @param ls
     * @param end
     * @return: Object
     */
    private boolean share_subject = false;

    private int findStop(int start, int end) {
        // end: end of sentence or next verb chunk
        // Patterns: VB NP.....,NP VB;
        // next verb is reduced clause
        // First try to use relative word as the splitter
        List<Chunk> ls = chunkList;
        if (end - start > 2) {
            Chunk pp = ls.get(start + 1);
            if ((pp.type.equals("SBAR") || pp.type.equals("PP")) && subclauseSet.contains(pp.txt)) {
                return start + 1;
            }
            pp = ls.get(start + 2); // 04.01.2014:23:44
            if ((pp.type.equals("SBAR") || pp.type.equals("PP")) && subclauseSet.contains(pp.txt)) {
                return start + 2;
            }
        }
        if (end - start > 2) {
            Chunk c1 = chunkList.get(end - 1);
            Chunk c2 = chunkList.get(end - 2);
            if (c1.type.equals("O") && (c1.txt.equals("and") || c1.txt.equals("or"))) { // replace "," by "or"  04.01.2014:9:34 //|| c1.txt.equals("or")||c1.txt.equals(",")
                share_subject = true;
                if (c2.txt.equals(",")) {
                    return end - 3;
                } else {
                    return end - 2;
                }
            }
        }
        if (right_con != null) {
            return WPos2Chunk(right_con.pos);
        }
        List<Chunk> comma = getCommaList(start, end, ls);
        List<Chunk> conj = getConjList(start, end, ls);
        if (comma.size() == 1) { // this might be the separator between object/subject
            if (conj.size() > 0) {// has conjunction
                Chunk and = conj.get(conj.size() - 1);
                int conj_pos = ls.indexOf(and);
                if (hasNounChunk(conj_pos, ls, end)) { // share subject
                    // has NP after conjunction [and/or][NP] .... [VB]
                    // now determine 'comma' is in front or behind 'conj'
                    Chunk com = comma.get(0);
                    int com_pos = ls.indexOf(com);
                    if (com.end < and.end && conj_pos - com_pos == 1) { // set end point for object
                        end = com_pos - 1;
                        // skip [, and]
                    } else {//
                        if (!hasNounChunk(com_pos, ls, end)) {
                        } else { //use comma as separator
                            end = com_pos - 1; // nerver happend
                        }
                    }
                }
            } else { // use "," to separate two clauses
                Chunk com = comma.get(0);
                int com_pos = ls.indexOf(com);
                if (!hasNounChunk(com_pos, ls, end)) { // share subject
                    // take all
                } else {
                    end = com_pos - 1;
                }
            }
        } else if (comma.isEmpty()) { // find conjunction: and/but/or
            if (conj.size() > 0) {
                Chunk and = conj.get(conj.size() - 1);
                int conj_pos = ls.indexOf(and);
                end = conj_pos - 1;
            }
        } else { // more than one comma, find the last comma
            // take last conj and comma
            Chunk com = comma.get(0);
            int com_pos = ls.indexOf(com);
            int pos = find_coord(com_pos, ls, end);
            if (pos == com_pos) {
                end = pos--;
            } else {
                if (hasNounChunk(pos + 1, ls, end)) {
                    end = pos;
                }
            }
        }
        end = Math.min(end, ls.size() - 1); // make sure that the last position is not longer than the list size.
        return end;
    }

    /**
     * Find sentence break
     *
     * @param start
     * @param end
     * @param ls
     * @return
     */
    private int findBreak(int start, int end, List<Chunk> ls) {
        for (int i = start + 1; i < end; i++) {
            Chunk c = ls.get(i);
            if (c.type.equals("O") && c.txt.equals(".")) {
                if (i < end - 1) {
                    Chunk c1 = ls.get(i + 1);
                    if (Character.isUpperCase(c1.txt.charAt(0))) {
                        return i - 1;
                    }
                }
            } else if (c.type.equals("NP") && c.txt.startsWith(".")) {
                if (c.txt.length() >= 4 && (Character.isUpperCase(c.txt.charAt(2)) || Character.isUpperCase(c.txt.charAt(1)))) {
                    return i - 1;
                }
            }
        }
        return -1;
    }

    public boolean is_passive(Chunk verb, SenData sen) {
        boolean has_be = false;
        int count = 0;
        String tokens[] = sen.tokens;
        String POS[] = sen.POS;
        for (int i = verb.begin; i <= verb.end; i++) {
            if (beSet.contains(tokens[i])) {
                has_be = true;
            }
            if (POS[i].equals("VBN")) {
                count++;
            }
        }
        if (has_be && count >= 1) {
            return true;
        }
        return false;
    }

    /**
     * Find commas in a list of chunk
     *
     * @param start
     * @param chunk
     * @return
     */
    private List<Chunk> getCommaList(int start, int end, List<Chunk> ls) {
        List<Chunk> comma = new ArrayList<>();
        Chunk c;
        for (int i = start + 1; i <= end; i++) {
            c = ls.get(i);
            if (c.type.equals("O") && c.txt.equals(",")) {
                comma.add(c);
            }
        }
        return comma;
    }

    /**
     * Get list of preposition between pos1 and pos2
     *
     * @param pos1: start position
     * @param pos2: end position
     * @return: list of preposition if available
     */
    private List<Word> getPrep(int pos1, int pos2, List<Word> prepList) {
        List<Word> list = new ArrayList<>();
        for (Word w : prepList) {
            if (w.pos >= pos1 && w.pos <= pos2) {
                list.add(w);
            }
        }
        return list;
    }

    /**
     * Word position to chunk position containing word
     *
     * @param pos: word index
     * @return: chunk (position) contains word
     */
    public int WPos2Chunk(int pos) {
        Chunk c;
        for (int i = 0; i < chunkList.size(); i++) {
            c = chunkList.get(i);
            if (pos >= c.begin && pos <= c.end) {
                return i;
            }
        }
        return -1; // not in chunk
    }

    /**
     * Find a list of drug in enumeration or apposition form
     *
     * @param start
     * @param end
     * @return
     */
    private int find_coord(int start, List<Chunk> ls, int end) {
        int count = 0;
        int conj = 0;
        int list = 0;
        int last_pos = 0;
        Chunk c, prev = null;
        if (start > 0) {
            Chunk np = ls.get(start - 1);
            if (np.type.equals("NP")) {
                for (int i = start + 1; i <= end; i++) {
                    c = ls.get(i);
                    if (c.txt.equals(",")) {
                        continue;
                    } else if (c.type.equals("NP")) {
                        count++;
                        if (conj > 0 && count > 0) {
                            return i;
                        }
                        if (list > 0) {
                            last_pos = i;
                        }
                    } else if (ccSet.contains(c.txt) || c.type.equals("CONJ")) {
                        conj++;
                    } else if (c.type.equals("PP") && appoSet.contains(c.txt)) {
                        list++;
                    } else {
                        break;
                    }
                    prev = c;
                }
            }
        }
        if (list > 0) {
            return last_pos;
        }
        return start;
    }

    public void toLibSVM(FeatureData[] data, String sub_type, String path) {
        try {
            FileWriter wr = new FileWriter(path + "/" + sub_type + ".libsvm");
            BufferedWriter writer = new BufferedWriter(wr);
            for (FeatureData dt : data) {
                writer.append(dt.toString());
                //writer.newLine();
            }
            writer.flush();
            writer.close();
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    /**
     * Do n-fold cross-validation
     *
     * @param data
     * @param n_fold
     * @param output
     */
    public void crossValidation(FeatureData[] data, int n_fold, String output) {
        // Slit data based on document level
        Map<String, List<FeatureData>> docMap = new HashMap<>();// DocID -> DDI pairs
        List<String> idList = new ArrayList<>(); // list of document ID
        for (FeatureData dt : data) {
            String docid = getDocID(dt.id);
            List<FeatureData> ldt = docMap.get(docid);
            if (ldt == null) {// new doc ID
                ldt = new ArrayList<>();
                docMap.put(docid, ldt);
                idList.add(docid);
            }
            ldt.add(dt);
        }
        int size = 0;
        for (String s : idList) {
            size += docMap.get(s).size();
        }
        int begin = 0;
        size = idList.size() / n_fold;
        int end = size;
        List<FeatureData>[] split = new List[n_fold];
        for (int i = 0; i < n_fold; i++) {
            split[i] = new ArrayList<>();
            for (int j = begin; j < end; j++) {
                String id = idList.get(j);
                List<FeatureData> ls = docMap.get(id);
                split[i].addAll(ls);
            }
            begin += size;
            if (i == n_fold - 2) {
                end = idList.size();
            } else {
                end += size;
            }
        }
        size = 0;
        for (List<FeatureData> split1 : split) {
            size += split1.size();
        }
        System.out.println("Check sum, split n_fold:\t" + size + "\t input:\t" + data.length);
        if (size != data.length) {
            System.out.println("Problem with splitting, exit now");
            System.exit(1);
        }
        // trainning
        SVMTrain trainer = new SVMTrain();
        svm_parameter para = trainer.getPara();
        para.kernel_type = svm_parameter.RBF;
        para.nr_weight = 1;
        double ww[] = {2};
        para.weight = ww;
        int lb[] = {1};
        para.weight_label = lb;
        double v[] = {0.05, 0.1, 0.15, 0.2, 0.25, 0.3};
        try {
            FileWriter writer = new FileWriter(output);
            writer.append("C,G,TP,FP,Pre,Recall,Fscore");
            writer.append("\n");
            for (int i = 1; i <= 6; i++) { // loop over C
                for (int j = 0; j < v.length; j++) {
                    para.C = i;
                    para.gamma = v[j];
                    int tp = 0;
                    int fp = 0;
                    int total = 0;
                    for (int t = 0; t < n_fold; t++) { // n_fold cross validation
                        List<FeatureData> trainData = new ArrayList<>();
                        List<FeatureData> testData = split[t];
                        for (int k = 0; k < n_fold; k++) {
                            if (k != t) {
                                trainData.addAll(split[k]);
                            }
                        }
                        svm_model model = trainer.train(trainData, para);
                        for (FeatureData dt : testData) {
                            double val = trainer.predict(dt, model);
                            if (dt.getLabel() == 1) {
                                total++;
                                if (val == 1) {
                                    tp++; // true positive
                                }
                            } else { // true negative
                                if (val == 1) {
                                    fp++; // false positive
                                }
                            }

                        }
                    }
                    double precision = (double) tp / (double) (tp + fp);
                    double recall = (double) tp / (double) total;
                    double f_score = (2 * precision * recall) / (precision + recall);
                    writer.append(i + "," + v[j] + "," + tp + "," + fp + "," + precision + "," + recall + "," + f_score);
                    writer.append("\n");
                }
                writer.flush();
            }
            writer.close();
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    private String getDocID(String id) {
        return id.substring(0, id.indexOf(".s"));
    }

    //delete by Ming-Yu Chien
    /*
    private boolean testForKnownPDDI(List<CIDPair> ddiList){
	boolean ddiFound = false;
	boolean excludeDDICorpi = true;
	for (CIDPair ddiPair : ddiList) {
	    if (ddiPair.arg1.word.toLowerCase() == ddiPair.arg2.word.toLowerCase()){
		System.out.println("Not querying - drug 1 and drug 2 are the same: " + ddiPair.arg1.word.toLowerCase());
		continue;
	    }
	    String query = "";
	    if (excludeDDICorpi){ // excludes drugbank and the two SemEval corpora (DDI-Corpus-2011 and DDI-Corpus-2013)
		query = "SELECT object, precipitant FROM DDI_CONS WHERE source IN ('DIKB', 'NDF-RT', 'NLM-Corpus', 'PK-Corpus', 'ONC-HighPriority', 'ONC-NonInteruptive', 'OSCAR', 'CredibleMeds') AND (LOWER(object) = \'" + ddiPair.arg1.word.toLowerCase() + "\' AND LOWER(precipitant) = \'" + ddiPair.arg2.word.toLowerCase() + "\') OR (LOWER(object) = \'" + ddiPair.arg2.word.toLowerCase() + "\' AND LOWER(precipitant) = \'" + ddiPair.arg1.word.toLowerCase() + "\');";
	    } else {
		query = "SELECT object, precipitant FROM DDI_CONS WHERE source IN ('DIKB', 'Drugbank', 'NDF-RT', 'DDI-Corpus-2011', 'DDI-Corpus-2013', 'NLM-Corpus', 'PK-Corpus', 'ONC-HighPriority', 'ONC-NonInteruptive', 'OSCAR', 'CredibleMeds') AND (LOWER(object) = \'" + ddiPair.arg1.word.toLowerCase() + "\' AND LOWER(precipitant) = \'" + ddiPair.arg2.word.toLowerCase() + "\') OR (LOWER(object) = \'" + ddiPair.arg2.word.toLowerCase() + "\' AND LOWER(precipitant) = \'" + ddiPair.arg1.word.toLowerCase() + "\');";
	    }
	    System.out.println(query);

	    try {						    				
		Statement st = conn.createStatement();
		ResultSet rs = null;
		if (st.execute(query)){
		    rs = st.getResultSet();
		} else {
		    System.exit(1);
		}

		if (rs.first()){
		    System.out.println("QUERY RETURNED RESULT");
		    String object = rs.getString("object");
		    String precipitant = rs.getString("precipitant");
		    System.out.format("%s, %s\n", object, precipitant);			      
		    while (rs.next()){
			object = rs.getString("object");
			precipitant = rs.getString("precipitant");
			System.out.format("%s, %s\n", object, precipitant);
		    }
		    System.out.println("SETTING ddiFound = true");
		    ddiFound =  true;
		    break;
		} else {
		    System.out.println("NO RESULT");
		    ddiFound = false;
		}
				
		if (rs != null) {
		    try {
			rs.close();
		    } catch (SQLException sqlEx) { } // ignore
		    rs = null;
		}
		if (st != null) {
		    try {
			st.close();
		    } catch (SQLException sqlEx) { } // ignore
		    st = null;
		}
	    } catch (SQLException ex) {
		// handle any errors
		System.out.println("SQLException: " + ex.getMessage());
		System.out.println("SQLState: " + ex.getSQLState());
		System.out.println("VendorError: " + ex.getErrorCode());
		System.exit(1);
	    }
	}
	return ddiFound;
    }
	*/

    public void printGroupData(String fpath) {
        try {
            FileReader rd = new FileReader(fpath);
            BufferedReader br = new BufferedReader(rd);
            Map<String, String> typemap = new HashMap<String, String>();
            Map<String, String> groupmap = new HashMap<String, String>();
            String line;
            while ((line = br.readLine()) != null) { // prepare map : pairID -> error type ; group type
                String st[] = line.split(",");
                typemap.put(st[0], st[1]);
                groupmap.put(st[0], st[2]);
            }
            // read test dataset
            Map<String, SenData> senMap = readData(Data.Test_DB2013_path); // load sentence data
            for (Map.Entry<String, SenData> entry : senMap.entrySet()) {
                senID = entry.getKey();
                currSen = entry.getValue();
                boolean print = false;
                for (CIDPair pair : currSen.cidList) {
                    if (typemap.containsKey(pair.id)) {
                        if (!print) {
                            System.out.print(currSen.senID + "\t");
                            printChunk(currSen.chunks);
                            print = true;
                        }
                        System.out.println(pair + "\t" + typemap.get(pair.id) + "\t" + groupmap.get(pair.id));
                    }
                }
                if (print) {
                    System.out.println("");
                }
            }
        } catch (Exception ex) {

        }
    }

    public void printGroupDataPK(String fpath) {
        try {
            FileReader rd = new FileReader(fpath);
            BufferedReader br = new BufferedReader(rd);
            Map<String, String> typemap = new HashMap<String, String>();
            Map<String, String> groupmap = new HashMap<String, String>();
            String line;
            while ((line = br.readLine()) != null) { // prepare map : pairID -> error type ; group type
                String st[] = line.split(",");
                typemap.put(st[0], st[1]);
                groupmap.put(st[0], st[2]);
            }
            // read test dataset
            Map<String, SenData> senMap = readData("data/test.ser"); // load sentence data
            for (Map.Entry<String, SenData> entry : senMap.entrySet()) {
                senID = entry.getKey();
                currSen = entry.getValue();
                boolean print = false;
                for (CIDPair pair : currSen.cidList) {
                    if (typemap.containsKey(pair.id)) {
                        if (!print) {
                            System.out.print(currSen.senID + "\t");
                            printChunk(currSen.chunks);
                            print = true;
                        }
                        System.out.println(pair + "\t" + typemap.get(pair.id) + "\t" + groupmap.get(pair.id));
                    }
                }
                if (print) {
                    System.out.println("");
                }
            }
        } catch (Exception ex) {

        }
    }
    public void setFMPath(String s){
    	featureMapPath = s;
    	try {
			featureMap = (Map<String, FeatureIndex>) Data.read(featureMapPath);
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
    }
}
