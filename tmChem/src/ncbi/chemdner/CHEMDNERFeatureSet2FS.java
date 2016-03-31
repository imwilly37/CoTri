package ncbi.chemdner;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import ncbi.OffsetTaggedPipe;

import banner.tagging.FeatureSet;
import banner.tagging.TagFormat;
import banner.tagging.pipe.LChar;
import banner.tagging.pipe.LemmaPOS;
import banner.tagging.pipe.LowerCaseTokenText;
import banner.tagging.pipe.Pretagger;
import banner.tagging.pipe.RChar;
import banner.tagging.pipe.Sentence2TokenSequence;
import banner.tagging.pipe.SimFind;
import banner.tagging.pipe.TaggedCachePipe;
import banner.tagging.pipe.TokenNumberClass;
import banner.tagging.pipe.TokenWordClass;
import banner.types.Mention.MentionType;
import banner.types.Sentence.OverlapOption;
import cc.mallet.pipe.Noop;
import cc.mallet.pipe.Pipe;
import cc.mallet.pipe.SerialPipes;
import cc.mallet.pipe.TokenSequence2FeatureVectorSequence;
import cc.mallet.pipe.tsf.OffsetConjunctions;
import cc.mallet.pipe.tsf.RegexMatches;
import cc.mallet.pipe.tsf.TokenTextCharNGrams;
import cc.mallet.pipe.tsf.TokenTextCharPrefix;
import cc.mallet.pipe.tsf.TokenTextCharSuffix;
import dragon.nlp.tool.Lemmatiser;

public class CHEMDNERFeatureSet2FS extends FeatureSet {

	// TODO Can / should this be expanded into a general configuration class?

	private static final long serialVersionUID = 6262180482022406614L;

	protected static String GREEK = "(alpha|beta|gamma|delta|epsilon|zeta|eta|theta|iota|kappa|lambda|mu|nu|xi|omicron|pi|rho|sigma|tau|upsilon|phi|chi|psi|omega)";

	protected static String ElementAbbrs = "(H|He|Li|Be|B|C|N|O|F|Ne|Na|Mg|Al|Si|P|S|Cl|Ar|K|Ca|Sc|Ti|V|Cr|Mn|Fe|Co|Ni|Cu|Zn|Ga|Ge|As|Se|Br|Kr|Rb|Sr|Y|Zr|Nb|Mo|Tc|Ru|Rh|Pd|Ag|Cd|In|Sn|SbTe|I|Xe|Cs|Ba|La|Ce|Pr|Nd|Pm|Sm|Eu|Gd|Tb|Dy|Ho|Er|Tm|Yb|Lu|Hf|Ta|W|Re|Os|Ir|Pt|Au|Hg|Tl|Pb|Bi|Po|At|Rn|Fr|Ra|Ac|Th|Pa|U|Np|Pu|Am|Cm|Bk|Cf)";
	protected static String Elements = "(Hydrogen|Helium|Lithium|Beryllium|Boron|Carbon|Nitrogen|Oxygen|Fluorine|Neon|Sodium|Magnesium|Aluminum|Aluminium|Silicon|Phosphorus|Sulfur|Chlorine|Argon|Potassium|Calcium|Scandium|Titanium|Vanadium|Chromium|Manganese|Iron|Cobalt|Nickel|Copper|Zinc|Gallium|Germanium|Arsenic|Selenium|Bromine|Krypton|Rubidium|Strontium|Yttrium|Zirconium|Niobium|Molybdenum|Technetium|Ruthenium|Rhodium|Palladium|Silver|Cadmium|Indium|Tin|Antimony|Tellurium|Iodine|Xenon|Cesium|Barium|Lanthanum|Cerium|Praseodymium|Neodymium|Promethium|Samarium|Europium|Gadolinium|Terbium|Dysprosium|Holmium|Erbium|Thulium|Ytterbium|Lutetium|Hafnium|Tantalum|Tungsten|Rhenium|Osmium|Iridium|Platinum|Gold|Mercury|Thallium|Lead|Bismuth|Polonium|Astatine|Radon|Francium|Radium|Actinium|Thorium|Protactinium|Uranium|Neptunium|Plutonium|Americium|Curium|Berkelium|Californium)";
	protected static String AminoAcidLong = "(Alanine|Arginine|Asparagine|Aspartic|Cysteine|Glutamine|Glutamic|Glycine|Histidine|Isoleucine|Leucine|Lysine|Methionine|Phenylalanine|Proline|Serine|Threonine|Tryptophan|Tyrosine|Valine)";
	protected static String AminoAcidMed = "(Ala|Arg|Asn|Asp|Cys|Gln|Glu|Gly|His|Ile|Leu|Lys|Met|Phe|Pro|Ser|Thr|Trp|Tyr|Val|Asx|Glx)";
	protected static String AminoAcidShort = "(A|R|N|D|C|Q|E|G|H|I|L|K|M|F|P|S|T|W|Y|V|B|Z)";

	private SerialPipes pipe;

	protected LemmaPOS pipeLemmaPOS;
	protected Pretagger pipePreTagger;
	protected TaggedCachePipe pipeTaggedCache;
	protected OffsetTaggedPipe pipeOffsetTagged;

	public CHEMDNERFeatureSet2FS(TagFormat format, Lemmatiser lemmatiser, dragon.nlp.tool.Tagger posTagger, banner.tagging.Tagger preTagger, String simFindFilename, List<String> cacheFilenames,
			String offsetTaggedFilename, Set<String> featureWhitelist, Set<MentionType> mentionTypes, OverlapOption sameType, OverlapOption differentType) {
		super(format, lemmatiser, posTagger, preTagger, null, null, mentionTypes, sameType, differentType);
		pipe = createPipe(format, lemmatiser, posTagger, preTagger, simFindFilename, cacheFilenames, offsetTaggedFilename, featureWhitelist, mentionTypes, sameType, differentType);
	}

	public void setLemmatiser(Lemmatiser lemmatiser) {
		if (pipeLemmaPOS != null)
			pipeLemmaPOS.setLemmatiser(lemmatiser);
	}

	public void setPosTagger(dragon.nlp.tool.Tagger posTagger) {
		if (pipeLemmaPOS != null)
			pipeLemmaPOS.setPosTagger(posTagger);
	}

	public void setPreTagger(banner.tagging.Tagger preTagger) {
		if (pipePreTagger != null)
			pipePreTagger.setPreTagger(preTagger);
	}

	public void setCacheFilenames(List<String> cacheFilenames) {
		if (pipeTaggedCache != null)
			pipeTaggedCache.loadCaches(cacheFilenames);
	}

	public void setOffsetTaggedPipe(String offsetTaggedFilename) {
		pipeOffsetTagged.setOffsetTaggedFilename(offsetTaggedFilename);
	}

	public Pipe getPipe() {
		return pipe;
	}

	protected SerialPipes createPipe(TagFormat format, Lemmatiser lemmatiser, dragon.nlp.tool.Tagger posTagger, banner.tagging.Tagger preTagger, String simFindFilename, List<String> cacheFilenames,
			String offsetTaggedFilename, Set<String> featureWhitelist, Set<MentionType> mentionTypes, OverlapOption sameType, OverlapOption differentType) {
		ArrayList<Pipe> pipes = new ArrayList<Pipe>();
		// TODO Test feature variations
		// TODO Make configurable which features to use
		// TODO Try dropping redundant features
		pipes.add(new Sentence2TokenSequence(format, mentionTypes, sameType, differentType));
		if (lemmatiser != null || posTagger != null) {
			pipeLemmaPOS = new LemmaPOS(lemmatiser, posTagger);
			pipes.add(pipeLemmaPOS);
		}
		if (preTagger != null) {
			pipePreTagger = new Pretagger("PRETAG=", preTagger);
			pipes.add(pipePreTagger);
		}
		if (cacheFilenames != null) {
			pipeTaggedCache = new TaggedCachePipe("TAGGED_CACHE", cacheFilenames);
			pipes.add(pipeTaggedCache);
		}
		if (offsetTaggedFilename != null) {
			pipeOffsetTagged = new OffsetTaggedPipe(offsetTaggedFilename);
			pipes.add(pipeOffsetTagged);
		}

		pipes.add(new LChar("LCHAR="));
		pipes.add(new RChar("RCHAR="));
		// pipes.add(new TokenText("TT="));
		pipes.add(new LowerCaseTokenText("W="));
		// pipes.add(new TokenLength("WLEN="));
		pipes.add(new TokenNumberClass("NC=", false));
		pipes.add(new TokenNumberClass("BNC=", true));
		pipes.add(new TokenWordClass("WC=", false));
		pipes.add(new TokenWordClass("BWC=", true));
		pipes.add(new RegexMatches("ALPHA", Pattern.compile("[A-Za-z]+")));
		pipes.add(new RegexMatches("INITCAPS", Pattern.compile("[A-Z].*")));
		pipes.add(new RegexMatches("UPPER-LOWER", Pattern.compile("[A-Z][a-z].*")));
		pipes.add(new RegexMatches("LOWER-UPPER", Pattern.compile("[a-z]+[A-Z]+.*")));
		pipes.add(new RegexMatches("ALLCAPS", Pattern.compile("[A-Z]+")));
		pipes.add(new RegexMatches("MIXEDCAPS", Pattern.compile("[A-Z][a-z]+[A-Z][A-Za-z]*")));
		pipes.add(new RegexMatches("SINGLECHAR", Pattern.compile("[A-Za-z]")));
		pipes.add(new RegexMatches("SINGLEDIGIT", Pattern.compile("[0-9]")));
		pipes.add(new RegexMatches("DOUBLEDIGIT", Pattern.compile("[0-9][0-9]")));
		pipes.add(new RegexMatches("NUMBER", Pattern.compile("[0-9,]+")));
		pipes.add(new RegexMatches("HASDIGIT", Pattern.compile(".*[0-9].*")));
		pipes.add(new RegexMatches("ALPHANUMERIC", Pattern.compile(".*[0-9].*[A-Za-z].*")));
		pipes.add(new RegexMatches("ALPHANUMERIC", Pattern.compile(".*[A-Za-z].*[0-9].*")));
		pipes.add(new RegexMatches("NUMBERS_LETTERS", Pattern.compile("[0-9]+[A-Za-z]+")));
		pipes.add(new RegexMatches("LETTERS_NUMBERS", Pattern.compile("[A-Za-z]+[0-9]+")));

		// TODO Change these to multi-token features
		pipes.add(new RegexMatches("HAS_DASH", Pattern.compile(".*-.*")));
		pipes.add(new RegexMatches("HAS_QUOTE", Pattern.compile(".*'.*")));
		pipes.add(new RegexMatches("HAS_SLASH", Pattern.compile(".*/.*")));
		pipes.add(new RegexMatches("REALNUMBER", Pattern.compile("(-|\\+)?[0-9,]+(\\.[0-9]*)?%?")));
		pipes.add(new RegexMatches("REALNUMBER", Pattern.compile("(-|\\+)?[0-9,]*(\\.[0-9]+)?%?")));
		pipes.add(new RegexMatches("START_MINUS", Pattern.compile("-.*")));
		pipes.add(new RegexMatches("START_PLUS", Pattern.compile("\\+.*")));
		pipes.add(new RegexMatches("END_PERCENT", Pattern.compile(".*%")));

		pipes.add(new TokenTextCharPrefix("2PREFIX=", 2));
		pipes.add(new TokenTextCharPrefix("3PREFIX=", 3));
		pipes.add(new TokenTextCharPrefix("4PREFIX=", 4));
		pipes.add(new TokenTextCharSuffix("2SUFFIX=", 2));
		pipes.add(new TokenTextCharSuffix("3SUFFIX=", 3));
		pipes.add(new TokenTextCharSuffix("4SUFFIX=", 4));
		pipes.add(new TokenTextCharNGrams("CHARNGRAM=", new int[] { 2, 3, 4 }, true));
		pipes.add(new RegexMatches("ROMAN", Pattern.compile("[IVXDLCM]+", Pattern.CASE_INSENSITIVE)));
		pipes.add(new RegexMatches("GREEK", Pattern.compile(GREEK, Pattern.CASE_INSENSITIVE)));
		// TODO try breaking this into several sets (brackets, sentence marks, etc.)
		pipes.add(new RegexMatches("ISPUNCT", Pattern.compile("[`~!@#$%^&*()-=_+\\[\\]\\\\{}|;\':\\\",./<>?]+")));

		// Case sensitivity has been considered in the below
		pipes.add(new RegexMatches("ELEMENT_ABBR", Pattern.compile(ElementAbbrs)));
		pipes.add(new RegexMatches("MOLECULE_PART", Pattern.compile(ElementAbbrs + "+"))); // Full formulas are multi-token with the fine tokenization
		pipes.add(new RegexMatches("ELEMENT_NAME", Pattern.compile(Elements)));
		// TODO Add full amino acid names
		// Do not need strings of amino acids?
		pipes.add(new RegexMatches("AMINO_MED", Pattern.compile(AminoAcidMed, Pattern.CASE_INSENSITIVE)));
		pipes.add(new RegexMatches("AMINO_SHORT", Pattern.compile(AminoAcidShort)));

		pipes.add(new MultiTokenRegexMatcher("AMINO_STRING", Pattern.compile("\\b" + AminoAcidMed + "-(" + AminoAcidMed + "-)*" + AminoAcidMed + "\\b")));

		// siddhartha added these;
		pipes.add(simFindFilename == null ? new Noop() : new SimFind(simFindFilename));
		pipes.add(new OffsetConjunctions(new int[][] { { -2 }, { -1 }, { 1 }, { 2 } }));
		if (featureWhitelist != null)
			pipes.add(new FeatureSuppressor(true, featureWhitelist));
		pipes.add(new TokenSequence2FeatureVectorSequence(false, true));
		return new SerialPipes(pipes);
	}
}
