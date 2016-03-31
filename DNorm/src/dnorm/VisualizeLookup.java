package dnorm;
import java.io.Console;
import java.io.File;

import dnorm.core.DiseaseNameAnalyzer;
import dnorm.core.Lexicon;
import dnorm.core.MEDICLexiconLoader;
import dnorm.core.SynonymTrainer;
import dnorm.types.FullRankSynonymMatrix;
import dnorm.types.SynonymMatrix;

public class VisualizeLookup {

	public static void main(String[] args) {
		String lexiconFilename = args[0];
		String matrixFilename = args[1];

		DiseaseNameAnalyzer analyzer = DiseaseNameAnalyzer.getDiseaseNameAnalyzer(true, true, false, true);
		Lexicon lex = new Lexicon(analyzer);
		MEDICLexiconLoader loader = new MEDICLexiconLoader();
		loader.loadLexicon(lex, lexiconFilename);
		lex.prepare();
		SynonymMatrix matrix = FullRankSynonymMatrix.load(new File(matrixFilename));
		int maxRank = 10; // TODO Make configurable
		SynonymTrainer syn = new SynonymTrainer(lex, matrix, 1000);

		Console c = System.console();
		System.out.println();
		System.out.println("Enter mention string to look up, or type \"q\" to quit: ");
		String line = c.readLine();
		while (!line.equals("q")) {
			line = line.trim();
			syn.visualizeLookup(line, maxRank);
			System.out.println();
			System.out.println("Enter mention string to look up, or type \"q\" to quit: ");
			line = c.readLine();
		}
		System.out.println("Exiting.");
	}
}
