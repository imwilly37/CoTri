package dnorm.core;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;


public class MEDICLexiconLoader {

	private Map<String, String> alternateIDMap; // Maps from alternate IDs to primary IDs

	private Map<String, Set<String>> primaryNameMap;

	private boolean checkParents;

	public MEDICLexiconLoader() {
		this(true);
	}

	public MEDICLexiconLoader(boolean checkParents) {
		this.checkParents = checkParents;
	}

	public Map<String, Set<String>> getPrimaryNameMap() {
		return primaryNameMap;
	}

	private void preload(DiseaseNameAnalyzer analyzer, String filename, Map<String, Set<String>> canonicalNames) throws IOException {
		BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(filename), "UTF8"));
		String line = reader.readLine();
		while (line != null) {
			line = line.trim();
			if (!line.startsWith("#")) {
				String[] split = line.split("\t");
				Set<String> analyzedName = new HashSet<String>(analyzer.getTokens(split[0]));
				String id = split[1]; // ID
				if (canonicalNames.containsKey(id))
					throw new IllegalArgumentException("Duplicate ID " + id);
				canonicalNames.put(id, analyzedName);
			}
			line = reader.readLine();
		}
		reader.close();
	}

	public void loadLexicon(Lexicon lexicon, String filename) {
		int numConcepts = 0;
		int numNames = 0;
		int numMeSHIDs = 0;
		int numOMIMIDs = 0;

		Map<String, Set<String>> namesToConceptIDs = new HashMap<String, Set<String>>();

		DiseaseNameAnalyzer analyzer = lexicon.getAnalyzer();
		alternateIDMap = new HashMap<String, String>();
		primaryNameMap = new HashMap<String, Set<String>>();
		try {
			Map<String, Set<String>> canonicalNames = new HashMap<String, Set<String>>();
			preload(analyzer, filename, canonicalNames);
			BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(filename), "UTF8"));
			String line = reader.readLine();
			while (line != null) {
				line = line.trim();
				if (!line.startsWith("#")) {
					String[] split = line.split("\t");
					Set<String> names = new HashSet<String>();
					names.add(split[0]); // Canonical name
					Set<String> conceptIds = primaryNameMap.get(split[0].toUpperCase());
					if (conceptIds == null) {
						conceptIds = new HashSet<String>();
						primaryNameMap.put(split[0].toUpperCase(), conceptIds);
					}
					conceptIds.add(split[1]);
					String id = split[1]; // ID
					numConcepts++;
					if (id.startsWith("OMIM:"))
						numOMIMIDs++;
					else
						numMeSHIDs++;
					if (split.length > 2) {
						String[] altIDs = split[2].split("\\|");
						for (String altID : altIDs) {
							if (altID.length() > 0) {
								if (altID.startsWith("OMIM:"))
									numOMIMIDs++;
								else
									numMeSHIDs++;
								String previous = alternateIDMap.put(altID, id);
								if (previous != null && !previous.equals(id))
									throw new IllegalArgumentException(altID + "was already associated with " + previous);
							}
						}
					}
					// System.out.println(id + "\t" + split[0]);
					// Alternate names
					if (split.length > 7) {
						Set<Set<String>> parentCanonicalNames = new HashSet<Set<String>>();
						for (String parentId : split[4].split("\\|")) {
							parentCanonicalNames.add(canonicalNames.get(parentId));
						}
						for (String name : split[7].split("\\|")) {
							Set<String> analyzedName = new HashSet<String>(analyzer.getTokens(name));
							if (checkParents && parentCanonicalNames.contains(analyzedName)) {
								System.out.println("Not adding alternate name " + name + " to concept " + id + " because it is the primary name of a parent");
							} else {
								// System.out.println("\t" + name);
								names.add(name);
							}
						}
					}
					names.remove("");
					numNames += names.size();
					for (String name : names) {
						Set<String> conceptIdsForName = namesToConceptIDs.get(name);
						if (conceptIdsForName == null) {
							conceptIdsForName = new HashSet<String>();
							namesToConceptIDs.put(name, conceptIdsForName);
						}
						conceptIdsForName.add(id);
					}

					lexicon.addConcept(id, names);
				}
				line = reader.readLine();
			}
			reader.close();
			System.out.println("Number of concepts: " + numConcepts);
			System.out.println("Number of names: " + namesToConceptIDs.size());
			System.out.println("Number of names per concept: " + ((double) numNames / numConcepts));
			System.out.println("Number of names per concept: " + numNames + " / " + numConcepts);
			int numConcepts2 = 0;
			for (String name : namesToConceptIDs.keySet()) {
				Set<String> conceptIdsForName = namesToConceptIDs.get(name);
				numConcepts2 += conceptIdsForName.size();
			}
			System.out.println("Number of concepts per name: " + ((double) numConcepts2 / namesToConceptIDs.size()));
			System.out.println("Number of concepts per name: " + numConcepts2 + " / " + namesToConceptIDs.size());
			System.out.println("Number of MeSH IDs: " + numMeSHIDs);
			System.out.println("Number of OMIM IDs: " + numOMIMIDs);
		} catch (IOException e) {
			// TODO Improve exception handling
			throw new RuntimeException(e);
		}
	}

	public Map<String, String> getAlternateIDMap() {
		return alternateIDMap;
	}

}
