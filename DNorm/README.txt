[Directory]

A. Versions
B. Contacts
C. Prerequisites
D. Instructions for running DNorm
E. Running DNorm with UMLS and Ab3P
F. Instructions for running or retraining BANNER
G. Instructions for retraining DNorm
H. Instructions for evaluating DNorm
I. Known issues
J. References

#======================================================================================#

A. [Versions]

	0.0.6 Upgraded Woodstox to 4.2.0 and improved documentation
	0.0.5 Fixed bug in abbreviation expansion code
	0.0.4 Enabled abbreviation identification with Ab3P in ApplyDNorm

B. [Contacts]

	If you have any questions or problems, please e-mail robert.leaman@nih.gov

C. [Prerequisites]

	All scripts are prepared and tested for the Linux command line. Windows equivalents should be relatively straightforward, but are not provided.

	Running DNorm requires the Java runtime environment.

	Compiling DNorm also requires the Java SE Development Kit and ant.

	Scripts are configured to request 10Gb main memory. It is possible to run with less (possibly as low as 6Gb) by modifying the scripts, though this increases the runtime.

D. [Instructions for running DNorm]
	
	The RunDNorm.sh script is the simplest way to RunDNorm. The script takes 5 parameters:
	•	CONFIG is the BANNER configuration file; for running DNorm it is banner_NCBIDisease_TEST.xml.
	•	LEXICON is the copy of the MEDIC disease vocabulary from CTD, data/CTD_diseases.tsv.
	•	MATRIX is the DNorm model, output/simmatrix_NCBIDisease_e4.bin, which is provided in the download.
	•	INPUT is the input file, a sample is provided at sample.txt, this is a simplified tab-delimited format.
	•	OUTPUT is the name of the output file, and sample-out2.txt is the output file, this is a simplified tab-delimited format.

	Using the following command on a Linux command line will run DNorm on the sample.txt file and place the output in sample-out2.txt:
	./RunDNorm.sh config/banner_NCBIDisease_TEST.xml data/CTD_diseases.tsv output/simmatrix_NCBIDisease_e4.bin sample.txt sample-out2.txt

	The file format used is a simple tab-delimited format. The sample.txt and sample-out.txt files contain examples.

	The tmBioC format is also supported through the RunDNorm_BioC script, which has similar parameters. In this case INPUT and OUTPUT can either be filenames or folders. If folders are used, DNorm will process each file in the INPUT directory and place the output in the output folder using a file with the same name.

	While the RunDNorm scripts are simple to use, for highest performance, you will also need to incorporate the UMLS features into BANNER and also use the Ab3P abbreviation resolution tool, please see the next section.

E. [Running DNorm with UMLS and Ab3P]
	
	Highest performance from DNorm requires the UMLS Metathesaurus to provide lexical hints to BANNER and also Ab3P to resolve abbreviations. This section describes how to set this up.

	First, get a copy of the 2013 AA version of the UMLS Metathesaurus. This version is necessary because this is what BANNER was trained against.
	http://download.nlm.nih.gov/umls/kss/2013AA/active/2013aa-1-meta.nlm
	http://download.nlm.nih.gov/umls/kss/2013AA/active/2013aa-2-meta.nlm
	Change the “nlm” file extension to “zip”, unzip the files and place the MRCONSO.* and MRSTY.* files in folder accessible from your linux command line (call this UMLS). You may delete all other files from this download.

	In the DNorm folder, open the config/banner_NCBIDisease_UMLS2013AA_TEST.xml file and change the “***PATH***” to the full path of the UMLS folder:
			  <dirWithMRSTY>***PATH***</dirWithMRSTY>
			  <dirWithMRCONSO>***PATH***</dirWithMRCONSO>

	Next, get a copy of the Ab3P abbreviation resolution tool (ftp://ftp.ncbi.nlm.nih.gov/pub/wilbur) and extract it into a folder accessible from the linux command line (call this AB3P_DIR).

	Next, decide on a file folder that can be used to store temporary files (call this TEMP). Any folder will do; this is used to communicate with Ab3P.

	Since this version is the one intended for use (rather than demonstration), it uses the format specified by the BANNER configuration, rather than the simple tab-delimited format used in the previous section. This is typically the PubTator format.

	Now you can finally run DNorm:

	./ApplyDNorm.sh config/banner_NCBIDisease_UMLS2013AA_TEST.xml data/CTD_diseases.tsv output/simmatrix_NCBIDisease_e4.bin AB3P_DIR TEMP INPUT OUTPUT

	The INPUT and OUTPUT parameters can either be single files or directories. There is also a version of this script that supports the BioC format (ApplyDNorm_BioC.sh).

F. [Instructions for running or retraining BANNER]
	
	DNorm uses BANNER to perform NER. Therefore you may need to retrain BANNER if you wish to apply it to new datasets. 

	The configuration files under the config folder specify many parameters, but the most important is which dataset and model name to use. The config files that end in "TRAIN" load the training set and the "TRAIN" model. The "DEV" configurations load the development set and the "TRAIN" model: this is so you can evaluate the model on the development set. Similarly, the "TRAINDEV" configuration loads both the training and development set and the "TRAINDEV" model. The "TEST" configuration loads the test set and the "TRAINDEV" model. 

	The following line will train BANNER using the configuration at config/banner_NCBIDisease_TRAIN.xml 

	./banner.sh train config/banner_NCBIDisease_TRAIN.xml

	If you care to evaluate a BANNER model, this is also straightforward:

	./banner.sh test config/banner_NCBIDisease_DEV.xml

	Please note that the highest performance is only achieved when BANNER is using the UMLS Metathesaurus. Please see the section "Running DNorm with UMLS and Ab3P" for how to set this up.

	Modifications to BANNER require the source code, which can be downloaded from the Subversion site on Sourceforge. From a Linux command line, execute: 

	svn co https://svn.code.sf.net/p/banner/code/trunk banner

	When the modifications are complete, copy the banner/lib/banner.jar to the DNorm/libs folder.

G. [Instructions for retraining DNorm]	

	Unlike BANNER, the configuration for DNorm is specified on the command line. Since DNorm uses BANNER to load the dataset, however, the BANNER configuration files are still needed.

	The parameters are as follows:
	1) The lexicon file, a tab separated values file, containing the lexicon to use. The default is the CTD diseases file, which is provided.
	2) A list of the abbreviations found by Ab3P in the data; this is a simple tab-delimited format, but is not the same as the output of Ab3p. An example for the NCBI Disease corpus is provided.
	3) A BANNER configuration file specifying which dataset to use for training.
	4) The maximum rank to consider before the similarity is said to be 0. 1000 works well, and there is no need to modify this.
	5) The learning rate (lambda). Values in the range of 0.001 to 0.0001 seem to work well, but you will need to determine this empirically.
	6) The maximum number of training iterations before stopping. DNorm stops training if the performance drops on the holdout set, but will stop early if it reaches this number.
	7) A BANNER configuration file specifying which dataset to use as a holdout set. DNorm stops training if performance drops on this set.
	8) The name of the model file to output

	The following line will train DNorm on the NCBI Disease training set, using the development set as a holdout set:

	./TrainSynonymMatrix.sh data/CTD_diseases.tsv data/NCBI_disease/abbreviations.tsv config/banner_NCBIDisease_TRAIN.xml 1000 0.001 50 config/banner_NCBIDisease_DEV.xml output/simmatrix_NCBIDisease_e4.bin 

	Please note that some variation is expected in the quality of the DNorm model. 

H. [Instructions for evaluating DNorm]	

	If you have a BANNER-readable file (such as a PubTator file) with annotated test data, you can evaluate DNorm using the EvalDNorm.sh script.

	The parameters are as follows:
	1) The lexicon file, a tab separated values file, containing the lexicon to use. The default is the CTD diseases file, which is provided.
	2) A list of the abbreviations found by Ab3P in the data; an example for the NCBI Disease corpus is provided.
	3) A BANNER configuration file specifying which dataset to use for the test, which model file and which BANNER configuration.
	4) The name of the DNorm model file to load.
	5) The name of the file to use for detailed analysis output.

	./EvalDNorm.sh data/CTD_diseases.tsv data/NCBI_disease/abbreviations.tsv config/banner_NCBIDisease_TEST.xml output/simmatrix_NCBIDisease_e4.bin output/analysis.txt

J. [Known issues]
	
	If the scripts are downloaded and unpacked using Windows software, such as WinZip, then it is likely that the newlines were converted to DOS format. This causes errors during execution, typically "Error: Could not find or load main class"
	
	This can be corrected with the dos2unix command. Executing the following line will convert all scripts back to UNIX format:
	
	find . -name "*.sh" | xargs dos2unix
	
K. [References]

	Robert Leaman, Rezarta Islamaj Doǧan and Zhiyong Lu, DNorm: Disease Name Normalization with Pairwise Learning to Rank. Bioinformatics (2013) 29 (22): 2909-2917, doi:10.1093/bioinformatics/btt474
	Robert Leaman, Ritu Khare and Zhiyong Lu, NCBI at 2013 ShARe/CLEF eHealth Share Task: Disorder Normalization in Clinical Notes with DNorm. Working Notes of the Conference and Labs of the Evaluation Forum (2013)
	Robert Leaman and Zhiyong Lu, Automated Disease Normalization with Low Rank Approximations. Proceedings of BioNLP 2014: pp 24-28
