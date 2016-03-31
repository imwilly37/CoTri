package dnorm.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import banner.util.RankedList;

import cern.colt.function.IntIntDoubleFunction;
import cern.colt.matrix.DoubleMatrix2D;
import dnorm.types.Dictionary;
import dnorm.types.FullRankSynonymMatrix;
import dnorm.types.SynonymMatrix;
import dnorm.types.Vector;


public class SynonymTrainer {

	private SparseEquals s;
	private int maxRank;
	private Lexicon lex;
	private SynonymMatrix matrix;

	public SynonymTrainer(Lexicon lex, SynonymMatrix matrix, int maxRank) {
		this.s = new SparseEquals();
		this.lex = lex;
		this.matrix = matrix;
		this.maxRank = maxRank;
	}

	public TrainingPoint getTrainingName(String conceptId, String name) {
		TrainingPoint point = new TrainingPoint(name, conceptId);
		return point;
	}

	public void trainArgmax(List<TrainingPoint> training, double lambda, double margin, List<TrainingPoint> holdout, int maxIterations) {
		int updates = Integer.MAX_VALUE;
		int iterations = 0;
		double previousHoldoutPerformance = Double.MAX_VALUE;
		double holdoutPerformance = 0.0;
		if (holdout != null) {
			holdoutPerformance = getHoldoutPerformance(holdout);
			System.out.println("Iteration: " + iterations + ", holdout performance=" + holdoutPerformance);
		}
		long start = System.currentTimeMillis();
		while (updates > 0 && holdoutPerformance <= previousHoldoutPerformance && iterations < maxIterations) {
			previousHoldoutPerformance = holdoutPerformance;
			visualizeLookup("excess iron deposits", 10);
			visualizeLookup("chronic meningococcemia", 10);
			visualizeLookup("autosomal recessive disease", 10);
			visualizeLookup("neurological dysfunction", 10);
			updates = 0;
			Collections.shuffle(training);
			for (int i = 0; i < training.size(); i++) {
				updates += trainConcepts(training.get(i), lambda, margin);
			}
			System.out.println("Iteration: " + iterations + ", updates=" + updates + ", elapsed=" + (System.currentTimeMillis() - start));
			iterations++;
			if (holdout != null) {
				holdoutPerformance = getHoldoutPerformance(holdout);
				System.out.println("Iteration: " + iterations + ", holdout performance=" + holdoutPerformance);
			}
		}
		visualizeLookup("excess iron deposits", 10);
		visualizeLookup("chronic meningococcemia", 10);
		visualizeLookup("autosomal recessive disease", 10);
		visualizeLookup("neurological dysfunction", 10);
		System.out.println("Training complete. Iterations: " + iterations + ", elapsed=" + (System.currentTimeMillis() - start));
	}

	public void trainArgmax(List<TrainingPoint> training, LearningRate l, double margin, List<TrainingPoint> holdout, int maxIterations) {
		int updates = Integer.MAX_VALUE;
		int iterations = 0;
		double previousHoldoutPerformance = Double.MAX_VALUE;
		double holdoutPerformance = 0.0;
		if (holdout != null) {
			holdoutPerformance = getHoldoutPerformance(holdout);
			System.out.println("Iteration: " + iterations + ", holdout performance=" + holdoutPerformance);
		}
		long start = System.currentTimeMillis();
		while (updates > 0 && holdoutPerformance <= previousHoldoutPerformance && iterations < maxIterations) {
			// while (updates > 0 && iterations < maxIterations) {
			previousHoldoutPerformance = holdoutPerformance;
			visualizeLookup("excess iron deposits", 10);
			visualizeLookup("chronic meningococcemia", 10);
			visualizeLookup("autosomal recessive disease", 10);
			visualizeLookup("neurological dysfunction", 10);
			updates = 0;
			Collections.shuffle(training);
			double lambda = l.getLearningRate(iterations);
			System.out.println("Learning rate = " + lambda + ", iterations = " + iterations);
			for (int i = 0; i < training.size(); i++) {
				System.out.println("Mention\t" + i + "\t" + training.get(i).getName() + "\t" + lex.visualizeVector(training.get(i).getVector()));
				updates += trainConcepts(training.get(i), lambda, margin);
				if ((i % 10) == 0)
					System.out.println(i + "/" + training.size() + ", updates=" + updates);
			}
			System.out.println("Iteration: " + iterations + ", updates=" + updates + ", elapsed=" + (System.currentTimeMillis() - start));
			iterations++;
			if (holdout != null) {
				holdoutPerformance = getHoldoutPerformance(holdout);
				System.out.println("Iteration: " + iterations + ", holdout performance=" + holdoutPerformance);
			}
		}
		visualizeLookup("excess iron deposits", 10);
		visualizeLookup("chronic meningococcemia", 10);
		visualizeLookup("autosomal recessive disease", 10);
		visualizeLookup("neurological dysfunction", 10);
		System.out.println("Training complete. Iterations: " + iterations + ", elapsed=" + (System.currentTimeMillis() - start));
	}

	public void trainArgmaxNEW(List<TrainingPoint> training, LearningRate l, int maxIterations, List<TrainingPoint> holdout) {
		int updates = Integer.MAX_VALUE;
		int iterations = 0;
		double holdoutPerformance = 0.0;
		if (holdout != null) {
			holdoutPerformance = getHoldoutPerformance(holdout);
			System.out.println("Iteration: " + iterations + ", holdout performance=" + holdoutPerformance);
		}
		List<Vector> vectors = new ArrayList<Vector>(lex.vectorsToConceptIds.keySet());
		FastIMultiplier fim = new FastIMultiplier();
		long start = System.currentTimeMillis();
		while (updates > 0 && iterations < maxIterations) {
			visualizeLookup("excess iron deposits", 10);
			visualizeLookup("chronic meningococcemia", 10);
			visualizeLookup("autosomal recessive disease", 10);
			visualizeLookup("neurological dysfunction", 10);
			updates = 0;
			Collections.shuffle(training);
			double lambda = l.getLearningRate(iterations);
			System.out.println("Learning rate = " + lambda + ", iterations = " + iterations);
			double alpha = 100.0 / (100.0 + iterations); // TODO Make configurable
			double beta = 1.0 - alpha;
			for (int i = 0; i < training.size(); i++) {
				DoubleMatrix2D q = training.get(i).getVector();
				String cp = training.get(i).getConceptId();
				double bestScore = -Double.MAX_VALUE;
				Vector nameP = null;
				Set<Vector> names = lex.conceptIdToVectors.get(cp);
				if (names != null) {
					for (Vector name : names) {
						DoubleMatrix2D n = name.getVector();
						double mScore = matrix.score(q, name.getVector());
						double iScore = fim.getScore(q, n);
						double score = alpha * iScore + beta * mScore;
						if (score > bestScore) {
							bestScore = score;
							nameP = name;
						}
					}
				}
				if (nameP != null) {
					Set<String> conceptIds = new HashSet<String>(lex.vectorsToConceptIds.get(nameP));
					DoubleMatrix2D dp = nameP.getVector();
					double dpScore = matrix.score(q, dp);
					System.out.println("Best name for \"" + training.get(i).getName() + "\", " + training.get(i).getConceptId() + " is [" + lex.visualizeVector(dp).replaceAll("\t", " ") + "] @ "
							+ dpScore);
					Collections.shuffle(vectors);
					// Find a vector with a higher score than the self-score && no concept overlap
					for (int j = 0; j < vectors.size(); j++) {
						Vector nameN = vectors.get(j);
						DoubleMatrix2D dn = nameN.getVector();
						double dnScore = matrix.score(q, dn);
						boolean train = dpScore - dnScore < 1.0;
						if (train)
							train = !s.equals(dp, dn);
						if (train)
							train = !containsAny(conceptIds, lex.vectorsToConceptIds.get(nameN));
						if (train) {
							System.out.println("q : " + lex.visualizeVector(q));
							System.out.println("dp: " + lex.visualizeVector(dp) + "=" + dpScore);
							System.out.println("dn: " + lex.visualizeVector(dn) + "=" + dnScore);
							System.out.println();
							matrix.trainingUpdate(q, dp, dn, lambda);
							updates++;
							dpScore = matrix.score(q, dp);
						}
					}
				}
			}
			System.out.println("Iteration: " + iterations + ", updates=" + updates + ", elapsed=" + (System.currentTimeMillis() - start));
			iterations++;
			if (holdout != null) {
				holdoutPerformance = getHoldoutPerformance(holdout);
				System.out.println("Iteration: " + iterations + ", holdout performance=" + holdoutPerformance);
			}
		}
		visualizeLookup("excess iron deposits", 10);
		visualizeLookup("chronic meningococcemia", 10);
		visualizeLookup("autosomal recessive disease", 10);
		visualizeLookup("neurological dysfunction", 10);
		System.out.println("Training complete. Iterations: " + iterations + ", elapsed=" + (System.currentTimeMillis() - start));
	}

	public void trainDictionary(LearningRate l, int maxIterations, List<TrainingPoint> holdout) {
		List<DoubleMatrix2D> vectors = new ArrayList<DoubleMatrix2D>();
		Dictionary dictionary = lex.getDictionary();
		for (int i = 0; i < dictionary.size(); i++) {
			vectors.add(lex.convertNameToVector(dictionary.getToken(i)));
		}
		List<DoubleMatrix2D> vectors2 = new ArrayList<DoubleMatrix2D>(vectors);
		int updates = Integer.MAX_VALUE;
		int iterations = 0;
		double holdoutPerformance = 0.0;
		if (holdout != null) {
			holdoutPerformance = getHoldoutPerformance(holdout);
			System.out.println("Iteration: " + iterations + ", holdout performance=" + holdoutPerformance);
		}
		FastIMultiplier fim = new FastIMultiplier();
		long start = System.currentTimeMillis();
		while (updates > 0 && iterations < maxIterations) {
			visualizeLookup("excess iron deposits", 10);
			visualizeLookup("chronic meningococcemia", 10);
			visualizeLookup("autosomal recessive disease", 10);
			visualizeLookup("neurological dysfunction", 10);
			updates = 0;
			Collections.shuffle(vectors);
			double lambda = l.getLearningRate(iterations);
			System.out.println("Learning rate = " + lambda + ", iterations = " + iterations);
			double alpha = 100.0 / (100.0 + iterations); // TODO Make configurable
			double beta = 1.0 - alpha;
			for (int i = 0; i < vectors.size(); i++) {
				DoubleMatrix2D q = vectors.get(i);
				DoubleMatrix2D dp = q;
				double dpScore = matrix.score(q, dp);
				Collections.shuffle(vectors2);
				// Find a vector with a higher score than the self-score && no concept overlap
				for (int j = 0; j < vectors2.size(); j++) {
					DoubleMatrix2D dn = vectors2.get(j);
					double dnScore = matrix.score(q, dn);
					boolean train = dpScore <= dnScore;
					if (train)
						train = !s.equals(dp, dn);
					if (train) {
						System.out.println("q : " + lex.visualizeVector(q));
						System.out.println("dp: " + lex.visualizeVector(dp) + "=" + dpScore);
						System.out.println("dn: " + lex.visualizeVector(dn) + "=" + dnScore);
						System.out.println();
						matrix.trainingUpdate(q, dp, dn, lambda);
						updates++;
						dpScore = matrix.score(q, dp);
					}
				}
			}
			System.out.println("Iteration: " + iterations + ", updates=" + updates + ", elapsed=" + (System.currentTimeMillis() - start));
			iterations++;
			if (holdout != null) {
				holdoutPerformance = getHoldoutPerformance(holdout);
				System.out.println("Iteration: " + iterations + ", holdout performance=" + holdoutPerformance);
			}
		}
		visualizeLookup("excess iron deposits", 10);
		visualizeLookup("chronic meningococcemia", 10);
		visualizeLookup("autosomal recessive disease", 10);
		visualizeLookup("neurological dysfunction", 10);
		System.out.println("Training complete. Iterations: " + iterations + ", elapsed=" + (System.currentTimeMillis() - start));
	}

	public static class FastIMultiplier implements IntIntDoubleFunction {
		private DoubleMatrix2D other;
		private double score;

		public FastIMultiplier() {
			other = null;
		}

		public double getScore(DoubleMatrix2D q, DoubleMatrix2D d) {
			this.score = 0.0;
			if (q.cardinality() < d.cardinality()) {
				other = d;
				q.forEachNonZero(this);
			} else {
				other = q;
				d.forEachNonZero(this);
			}
			return score;
		}

		@Override
		public double apply(int r, int c, double v) {
			double ov = other.get(r, c);
			if (ov != 0.0)
				score += v * ov;
			return v;
		}
	}

	public static interface LearningRate {
		public double getLearningRate(int iteration);
	}

	public static class FixedLearningRate implements LearningRate {
		private double rate;

		public FixedLearningRate(double rate) {
			this.rate = rate;
		}

		public double getLearningRate(int iteration) {
			return rate;
		}
	}

	public static class AdaptiveLearningRate implements LearningRate {
		private double initialRate;
		private double adaptation;

		public AdaptiveLearningRate(double initialRate, double adaptation) {
			this.initialRate = initialRate;
			this.adaptation = adaptation;
		}

		public double getLearningRate(int iteration) {
			return initialRate * adaptation / (adaptation + iteration);
		}
	}

	public void trainSampled(List<TrainingPoint> training, double lambda, List<TrainingPoint> holdout, int maxRounds, int iterationsPerRound) {
		int updates = Integer.MAX_VALUE;
		int rounds = 0;
		double previousHoldoutPerformance = Double.MAX_VALUE;
		double holdoutPerformance = 0.0;
		if (holdout != null) {
			holdoutPerformance = getHoldoutPerformance(holdout);
			System.out.println("Round: " + rounds + ", holdout performance=" + holdoutPerformance);
		}
		Random rnd = new Random();
		List<String> conceptIds = new ArrayList<String>(lex.conceptIds);
		Map<String, List<Vector>> conceptIdToVectors = new HashMap<String, List<Vector>>();
		for (String conceptId : conceptIds) {
			Set<Vector> names = lex.conceptIdToVectors.get(conceptId);
			conceptIdToVectors.put(conceptId, new ArrayList<Vector>(names));
		}
		long start = System.currentTimeMillis();
		while (updates > 0 && holdoutPerformance <= previousHoldoutPerformance && rounds < maxRounds) {
			updates = 0;
			previousHoldoutPerformance = holdoutPerformance;
			for (int i = 0; i < iterationsPerRound; i++) {
				// Pick training point randomly
				TrainingPoint point = training.get(rnd.nextInt(training.size()));
				// Pick randomly from names for training point concept
				List<Vector> names = conceptIdToVectors.get(point.getConceptId());
				Vector nameP = null;
				if (names == null) {
					System.out.println("Unknown conceptID:" + point.getConceptId());
				} else {
					nameP = names.get(rnd.nextInt(names.size()));
				}
				// Pick a concept randomly
				String conceptN = conceptIds.get(rnd.nextInt(conceptIds.size()));
				while (conceptN.equals(point.getConceptId()))
					conceptN = conceptIds.get(rnd.nextInt(conceptIds.size()));
				// Pick randomly from names for training point concept
				names = conceptIdToVectors.get(conceptN);
				Vector nameN = null;
				if (names == null) {
					System.out.println("Unknown conceptID:" + conceptN);
				} else {
					nameN = names.get(rnd.nextInt(names.size()));
				}
				if (nameP != null && nameN != null) {
					boolean check = true;
					DoubleMatrix2D q = point.getVector();
					DoubleMatrix2D dp = nameP.getVector();
					DoubleMatrix2D dn = nameN.getVector();
					check = check && !s.equals(q, dn);
					check = check && matrix.score(q, dp) - matrix.score(q, dn) < 1.0;
					if (check) {
						matrix.trainingUpdate(q, dp, dn, lambda);
						System.out.println("q : " + lex.visualizeVector(q));
						System.out.println("dp: " + lex.visualizeVector(dp) + "=" + matrix.score(q, dp));
						System.out.println("dn: " + lex.visualizeVector(dn) + "=" + matrix.score(q, dn));
						System.out.println();
						updates++;
					}
				}
			}
			System.out.println("Round: " + rounds + ", updates=" + updates + ", elapsed=" + (System.currentTimeMillis() - start));
			rounds++;
			if (holdout != null) {
				holdoutPerformance = getHoldoutPerformance(holdout);
				System.out.println("Round: " + rounds + ", holdout performance=" + holdoutPerformance);
			}
		}
	}

	public void trainLexicon(double lambda, List<TrainingPoint> holdout, int maxIterations) {
		int updates = Integer.MAX_VALUE;
		int iterations = 0;

		double holdoutPerformance = 0.0;
		if (holdout != null) {
			holdoutPerformance = getHoldoutPerformance(holdout);
			System.out.println("Iteration: " + iterations + ", holdout performance=" + holdoutPerformance);
		}

		long start = System.currentTimeMillis();
		List<Vector> vectors = new ArrayList<Vector>(lex.vectorsToConceptIds.keySet());
		while (updates > 0 && iterations < maxIterations) {
			visualizeLookup("excess iron deposits", 10);
			visualizeLookup("chronic meningococcemia", 10);
			visualizeLookup("autosomal recessive disease", 10);
			visualizeLookup("neurological dysfunction", 10);

			updates = 0;
			Collections.shuffle(vectors);
			int progress = 0;
			for (Vector v : vectors) {
				Set<String> conceptIds = new HashSet<String>(lex.vectorsToConceptIds.get(v));
				RankedList<LookupResult> results = lookup(v.getVector());

				// Get the minimum of the maximum score for any correct concept ID
				double minScore = Double.MAX_VALUE;
				Set<String> conceptIdsCopy = new HashSet<String>(conceptIds);
				for (int j = 0; j < results.size(); j++) {
					String resultConceptId = results.getObject(j).getConceptId();
					if (conceptIdsCopy.contains(resultConceptId)) {
						conceptIdsCopy.remove(resultConceptId);
						minScore = Math.min(minScore, results.getValue(j));
					}
				}

				// If any incorrect concept IDs with a higher score
				LookupResult resultN = null;
				for (int j = 0; j < results.size() && resultN == null; j++) {
					String resultConceptId = results.getObject(j).getConceptId();
					if (!conceptIds.contains(resultConceptId) && results.getValue(j) > minScore) {
						resultN = results.getObject(j);
					}
				}

				// Train against the one with the highest score
				if (resultN != null) {
					boolean check = true;
					DoubleMatrix2D q = v.getVector();
					DoubleMatrix2D dp = v.getVector();
					DoubleMatrix2D dn = resultN.getVector();
					check = check && !s.equals(q, dn);
					check = check && matrix.score(q, dp) - matrix.score(q, dn) < 1.0;
					if (check) {
						matrix.trainingUpdate(q, dp, dn, lambda);
						System.out.println("q : " + lex.visualizeVector(q));
						System.out.println("dp: " + lex.visualizeVector(dp) + "=" + matrix.score(q, dp));
						System.out.println("dn: " + lex.visualizeVector(dn) + "=" + matrix.score(q, dn));
						System.out.println();
						updates++;
					}
				}

				progress++;
				if (progress % 500 == 0)
					System.out.println("Progress: " + progress + " / " + vectors.size() + ", updates=" + updates + ", elapsed=" + (System.currentTimeMillis() - start));
			}
			System.out.println("Iteration: " + iterations + ", updates=" + updates + ", elapsed=" + (System.currentTimeMillis() - start));
			iterations++;
			if (holdout != null) {
				holdoutPerformance = getHoldoutPerformance(holdout);
				System.out.println("Iteration: " + iterations + ", holdout performance=" + holdoutPerformance);
			}
			visualizeLookup("excess iron deposits", 10);
			visualizeLookup("chronic meningococcemia", 10);
			visualizeLookup("autosomal recessive disease", 10);
			visualizeLookup("neurological dysfunction", 10);
			System.out.println("Training complete. Iterations: " + iterations + ", elapsed=" + (System.currentTimeMillis() - start));
		}
	}

	public void trainLexicon2(double lambda, double margin, List<TrainingPoint> holdout, int maxIterations) {
		Random r = new Random();
		int updates = Integer.MAX_VALUE;
		int iterations = 0;

		double holdoutPerformance = 0.0;
		if (holdout != null) {
			holdoutPerformance = getHoldoutPerformance(holdout);
			System.out.println("Iteration: " + iterations + ", holdout performance=" + holdoutPerformance);
		}

		long start = System.currentTimeMillis();
		List<Vector> vectors = new ArrayList<Vector>(lex.vectorsToConceptIds.keySet());
		while (updates > 0 && iterations < maxIterations) {
			visualizeLookup("excess iron deposits", 10);
			visualizeLookup("chronic meningococcemia", 10);
			visualizeLookup("autosomal recessive disease", 10);
			visualizeLookup("neurological dysfunction", 10);

			updates = 0;
			Collections.shuffle(vectors);
			int progress = 0;
			for (Vector v : vectors) {
				double threshold = matrix.score(v.getVector(), v.getVector()) - margin;
				Set<String> conceptIds = new HashSet<String>(lex.vectorsToConceptIds.get(v));

				// Find a vector with a higher score than the self-score && no concept overlap
				int index = r.nextInt(vectors.size());
				boolean cont = true;
				for (int i = 0; i < vectors.size() && cont; i++) {
					int actual = i + index;
					if (actual >= vectors.size())
						actual -= vectors.size();
					Vector v2 = vectors.get(actual);
					double score = matrix.score(v.getVector(), v2.getVector());
					boolean train = score > threshold;
					train = train && !s.equals(v.getVector(), v2.getVector());
					train = train && !containsAny(conceptIds, lex.vectorsToConceptIds.get(v2));
					if (train) {
						matrix.trainingUpdate(v.getVector(), v.getVector(), v2.getVector(), lambda);
						System.out.println("q : " + lex.visualizeVector(v.getVector()));
						System.out.println("dp: " + lex.visualizeVector(v.getVector()) + "=" + matrix.score(v.getVector(), v.getVector()));
						System.out.println("dn: " + lex.visualizeVector(v2.getVector()) + "=" + matrix.score(v.getVector(), v2.getVector()));
						System.out.println();
						updates++;
					}
				}
				progress++;
				if (progress % 1000 == 0) {
					System.out.println("Progress: " + progress + " / " + vectors.size() + ", updates=" + updates + ", elapsed=" + (System.currentTimeMillis() - start));
				}
			}
			System.out.println("Iteration: " + iterations + ", updates=" + updates + ", elapsed=" + (System.currentTimeMillis() - start));
			iterations++;
			if (holdout != null) {
				holdoutPerformance = getHoldoutPerformance(holdout);
				System.out.println("Iteration: " + iterations + ", holdout performance=" + holdoutPerformance);
			}
		}
		visualizeLookup("excess iron deposits", 10);
		visualizeLookup("chronic meningococcemia", 10);
		visualizeLookup("autosomal recessive disease", 10);
		visualizeLookup("neurological dysfunction", 10);
		System.out.println("Training complete. Iterations: " + iterations + ", elapsed=" + (System.currentTimeMillis() - start));
	}

	private static boolean containsAny(Set<? extends Object> set1, Set<? extends Object> set2) {
		for (Object o : set1)
			if (set2.contains(o))
				return true;
		return false;
	}

	public void visualizeLookupOLD(String name, int max) {
		System.out.println(name);
		System.out.println(lex.getAnalyzer().getTokens(name));
		System.out.println(lex.visualizeVector(name));
		RankedList<LookupResult> lookup = lookup(name);
		for (int i = 0; i < Math.min(lookup.size(), max); i++) {
			LookupResult result = lookup.getObject(i);
			System.out.printf("\t%f\t%s\t%s", lookup.getValue(i), result.getConceptId(), lex.visualizeVector(result.getVector()));
			System.out.println();
		}
		System.out.println();
	}

	public void visualizeLookup(String name, int max) {
		System.out.println(name);
		System.out.println(lex.getAnalyzer().getTokens(name));
		DoubleMatrix2D nameVector = lex.convertNameToVector(name);
		System.out.println(lex.visualizeVector(nameVector));
		RankedList<LookupResult> lookup = lookup(name);
		for (int i = 0; i < Math.min(lookup.size(), max); i++) {
			LookupResult result = lookup.getObject(i);
			DoubleMatrix2D resultVector = result.getVector();
			System.out.printf("\t%f\t%s\t%s", lookup.getValue(i), result.getConceptId(), lex.visualizeVector(resultVector));
			System.out.println();
			visualizeScore(nameVector, resultVector);
		}
		System.out.println();
	}

	public void visualizeScore(DoubleMatrix2D nameVector, DoubleMatrix2D resultVector) {
		if (!(matrix instanceof FullRankSynonymMatrix))
			return;
		FullRankSynonymMatrix m = (FullRankSynonymMatrix) matrix;
		int size = lex.getDictionary().size();
		for (int i = 0; i < size; i++) {
			if (nameVector.get(i, 0) != 0.0) {
				String nameToken = lex.getDictionary().getToken(i);
				for (int j = 0; j < size; j++) {
					if (resultVector.get(j, 0) != 0.0) {
						String resultToken = lex.getDictionary().getToken(j);
						System.out.printf("\t\t%s\t->\t%s\t%f", nameToken, resultToken, m.w.get(i, j));
						System.out.println();
					}
				}
			}
		}
	}

	private Vector getBestVector(DoubleMatrix2D q, String conceptId) {
		double bestScore = -Double.MAX_VALUE;
		Vector bestVector = null;
		Set<Vector> names = lex.conceptIdToVectors.get(conceptId);
		if (names == null) {
			// TODO
			System.out.println("Unknown conceptID: " + conceptId);
		} else {
			// TODO make choice under equal value arbitrary
			for (Vector name : names) {
				double score = matrix.score(q, name.getVector());
				if (score > bestScore) {
					bestScore = score;
					bestVector = name;
				}
			}
		}
		return bestVector;
	}

	private int trainConcepts(TrainingPoint point, double lambda, double margin) {
		DoubleMatrix2D q = point.getVector();
		Vector nameP = getBestVector(q, point.getConceptId());
		int updates = 0;
		if (nameP != null) {
			for (String conceptId : lex.conceptIds) {
				Vector nameN = getBestVector(q, conceptId);
				assert nameN != null;
				boolean check = true;
				DoubleMatrix2D dp = nameP.getVector();
				DoubleMatrix2D dn = nameN.getVector();
				check = check && !s.equals(q, dn);
				check = check && matrix.score(q, dp) - matrix.score(q, dn) < margin;
				if (check) {
					matrix.trainingUpdate(q, dp, dn, lambda);
					// System.out.println("q : " + lex.visualizeVector(q));
					// System.out.println("dp: " + lex.visualizeVector(dp) + "=" + matrix.score(q, dp));
					// System.out.println("dn: " + lex.visualizeVector(dn) + "=" + matrix.score(q, dn));
					// System.out.println();
					updates++;
				}
			}
		}
		return updates;
	}

	public double getHoldoutPerformance(List<TrainingPoint> holdout) {
		int rankSum = 0;
		double score1 = 0.0;
		double score2 = 0.0;
		for (TrainingPoint point : holdout) {
			int rank = getSynonymRank(point);
			rankSum += rank;
			score1 += 1.0 / (rank + 1.0);
			score2 += 1.0 / (Math.log(rank + 1.0) / Math.log(10.0) + 1.0);
		}
		score1 = score1 / holdout.size();
		System.out.println("Alternative score 1: " + score1);
		score2 = score2 / holdout.size();
		System.out.println("Alternative score 2: " + score2);
		return ((double) rankSum) / holdout.size();
	}

	private int getSynonymRank(TrainingPoint point) {
		RankedList<LookupResult> results = lookup(point.getName());
		for (int i = 0; i < results.size(); i++) {
			if (results.getObject(i).getConceptId().equals(point.getConceptId()))
				return i;
		}
		return maxRank;
	}

	public RankedList<LookupResult> lookup(String name) {
		DoubleMatrix2D nameVector = lex.convertNameToVector(name);
		return lookup(nameVector);
	}

	public RankedList<LookupResult> lookup(String name, double nonPreferredDiscount) {
		DoubleMatrix2D nameVector = lex.convertNameToVector(name);
		return lookup(nameVector, nonPreferredDiscount);
	}

	public RankedList<LookupResult> lookup(DoubleMatrix2D nameVector) {
		RankedList<LookupResult> results = new RankedList<LookupResult>(maxRank);
		for (Vector vector : lex.vectorsToConceptIds.keySet()) {
			double score = matrix.score(nameVector, vector.getVector());
			if (score > 0.0 && results.check(score)) {
				Set<String> conceptIds = lex.vectorsToConceptIds.get(vector);
				for (String conceptId : conceptIds) {
					results.add(score, new LookupResult(vector, conceptId));
				}
			}
		}
		return results;
	}

	public RankedList<LookupResult> lookup(DoubleMatrix2D nameVector, double nonPreferredDiscount) {
		RankedList<LookupResult> results = new RankedList<LookupResult>(maxRank);
		for (Vector vector : lex.vectorsToConceptIds.keySet()) {
			double score = matrix.score(nameVector, vector.getVector());
			if (!vector.isPreferred())
				score *= nonPreferredDiscount;
			if (score > 0.0 && results.check(score)) {
				Set<String> conceptIds = lex.vectorsToConceptIds.get(vector);
				for (String conceptId : conceptIds) {
					results.add(score, new LookupResult(vector, conceptId));
				}
			}
		}
		return results;
	}

	public class LookupResult {
		private Vector vector;
		private String conceptId;

		public LookupResult(Vector vector, String conceptId) {
			this.vector = vector;
			this.conceptId = conceptId;
		}

		public DoubleMatrix2D getVector() {
			return vector.getVector();
		}

		public Vector getVector2() {
			return vector;
		}

		public String getConceptId() {
			return conceptId;
		}
	}

	public class TrainingPoint {
		private String name;
		private DoubleMatrix2D vector;
		private String conceptId;

		public TrainingPoint(String name, String conceptId) {
			this.name = name;
			this.conceptId = conceptId;
		}

		public String getName() {
			return name;
		}

		public DoubleMatrix2D getVector() {
			// Delayed to allow completion of the vector space (lex.prepare())
			if (vector == null)
				vector = lex.convertNameToVector(name);
			return vector;
		}

		public String getConceptId() {
			return conceptId;
		}
	}

	public static class SparseEquals implements IntIntDoubleFunction {

		private boolean equal;
		private DoubleMatrix2D m2;

		public boolean equals(DoubleMatrix2D m1, DoubleMatrix2D m2) {
			if (m1.cardinality() != m2.cardinality())
				return false;
			equal = true;
			this.m2 = m2;
			m1.forEachNonZero(this);
			m2 = null;
			return equal;
		}

		@Override
		public double apply(int r, int c, double v) {
			equal = equal && v == m2.get(r, c);
			return v;
		}
	}
}
