[Directory]

A. Versions
B. Contacts
C. Prerequisites
D. Preparing Ab3P
E. Test DNorm setup
F. Running DNorm
G. Running the polling version of DNorm
H. Preparing UMLS
I. Instructions for retraining BANNER
J. Instructions for downloading and modifying BANNER
K. Instructions for testing BANNER
L. Instructions for retraining DNorm
M. Known issues
N. References

#======================================================================================#

A. [Versions]

	0.0.6 Upgraded Woodstox to 4.2.0 and improved documentation
	0.0.5 Fixed bug in abbreviation expansion code
	0.0.4 Enabled abbreviation identification with Ab3P in ApplyDNorm

B. [Contacts]

	If you have any questions or problems, please e-mail robert.leaman@nih.gov

C. [Prerequisites]

	All scripts are prepared and tested for the Linux command line. Windows equivalents should be relatively straightforward (with the possible exception of Ab3P), but are not provided.

	Running DNorm requires the Java runtime environment.

	Compiling DNorm also requires the Java SE Development Kit and the Apache build tool ant.

	Scripts are configured to request 10Gb main memory. It is possible to run with less (possibly as low as 6Gb) by modifying the scripts, though this increases the runtime.
	
	Highest performance from DNorm requires Ab3P to resolve abbreviations and also, if you retrain BANNER, the UMLS Metathesaurus to provide lexical hints. This README will assume you intend the highest performance, so the next sections describe how to prepare these.

D. [Preparing Ab3P]
	
	Download the Ab3P abbreviation resolution tool, version 1.5 from ftp://ftp.ncbi.nlm.nih.gov/pub/wilbur. Extract it into a folder accessible from the command line. Call this folder AB3P_DIR.
	
	Ab3P must be compiled after download. The command is "make", but it is recommended for you to open the readme file for Ab3P and follow the full instructions there.

	DNorm uses the file system to communicate with Ab3P. We therefore need a folder that can be used to store temporary files. Any folder should be usable, though this has been best tested with a dedicated temporary folder that is otherwise unused. Call this folder TEMP.

E. [Test DNorm setup]	

	To verify that the DNorm setup is correct, run the EvalDNorm script:

	./EvalDNorm.sh data/CTD_diseases-2015-06-04.tsv data/BC5CDR/abbreviations.tsv config/banner_BC5CDR_UMLS2013AA_SAMPLE.xml output/simmatrix_BC5CDR_e4_TRAINDEV.bin output/analysis.txt

	The parameters are as follows:
	1) The lexicon file, a tab separated values file, containing the lexicon to use. The default is the CTD diseases file, which is provided.
	2) A list of the abbreviations found by Ab3P in the data; an example for the NCBI Disease corpus is provided.
	3) A BANNER configuration file specifying which dataset to use for the test, which model file and which BANNER configuration.
	4) The name of the DNorm model file to load.
	5) The name of the file to use for detailed analysis output.

	You will get the following output:

	Macro   P       0.7914285714285715      R       0.7885685425685426      F       0.771662411408407
	Micro   P       0.8097560975609757      R       0.7685185185185185      F       0.7885985748218528
	Ceiling @ 1     Max     10      R       0.7685185185185185
	Ceiling @ 2     Max     10      R       0.8101851851851852
	Ceiling @ 3     Max     10      R       0.8240740740740741
	Ceiling @ 4     Max     10      R       0.8333333333333334
	Ceiling @ 5     Max     10      R       0.8425925925925926
	Ceiling @ 6     Max     10      R       0.8425925925925926
	Ceiling @ 7     Max     10      R       0.8425925925925926
	Ceiling @ 8     Max     10      R       0.8425925925925926
	Ceiling @ 9     Max     10      R       0.8425925925925926
	Ceiling @ 10    Max     10      R       0.8425925925925926

F. [Running DNorm]

	To use DNorm to process a file in PubTator format, use the ApplyDNorm script:
	
	./ApplyDNorm.sh config/banner_BC5CDR_UMLS2013AA_SAMPLE.xml data/CTD_diseases-2015-06-04.tsv output/simmatrix_BC5CDR_e4_TRAINDEV.bin AB3P_DIR TEMP INPUT OUTPUT

	The parameters are as follows:
	1) A BANNER configuration file specifying which model file and which BANNER configuration to use; any dataset specified in this file is ignored
	2) The lexicon file, a tab separated values file, containing the lexicon to use. The default is the CTD diseases file, which is provided.
	3) The name of the DNorm model file to load.
	4) The folder containing Ab3P; see Section E
	5) The temp folder; see Section E
	6) The input file or folder. If this is a file, it will be processed and then output written to the specified file name. If this is a folder, then each file in the folder will be processed and output written to output folder using the same filename.
	7) The output file or folder. The input and output should both be files or both be folders.

	The INPUT and OUTPUT parameters can either be single files or directories. There is also a version of this script that supports the BioC format (ApplyDNorm_BioC.sh).
	
	The BioC format is supported via the ApplyDNorm_BioC.sh script, whose parameters are the same:
	
	./ApplyDNorm_BioC.sh config/banner_BC5CDR_UMLS2013AA_SAMPLE.xml data/CTD_diseases-2015-06-04.tsv output/simmatrix_BC5CDR_e4_TRAINDEV.bin AB3P_DIR TEMP INPUT OUTPUT

	When running on real test data, you will want to use the models whose training data included the sample data. To do this, you need to use a different BANNER configuration and a different DNorm model:

	./ApplyDNorm.sh config/banner_BC5CDR_UMLS2013AA.xml data/CTD_diseases-2015-06-04.tsv output/simmatrix_BC5CDR_e4.bin AB3P_DIR TEMP INPUT OUTPUT

	DNorm requires some time to load, approximately 30 - 60 seconds, depending on the exact environment. Once loaded, it processes approximately 3 abstracts per second. 
	
G. [Running the polling version of DNorm]

	Since DNorm requires some time to load it is not convenient to run DNorm as part of a webserver as an external process: each request received by the webserver would be delayed by the loading time. We have therefore provided a version of DNorm that continuously checks an input folder for input files, processes each file found, and places the result in an output folder.
	
	To run the polling version of DNorm, use the PollDNorm script:
	
	./PollDNorm.sh config/banner_BC5CDR_UMLS2013AA_SAMPLE.xml data/CTD_diseases-2015-06-04.tsv output/simmatrix_BC5CDR_e4_TRAINDEV.bin AB3P_DIR TEMP INPUT OUTPUT

	The command line parameters are the same as the ApplyDNorm script described in [F], except that INPUT and OUTPUT must be folders. Input files may be either in PubTator or BioC format: the output format will be the same as the input format. Files whose name ends with ".xml" will be processed as BioC, all others as PubTator. To reduce excessive use of processor time, DNorm waits 500 milliseconds between checks of the INPUT folder.
	
	Since DNorm is potentially reading input at the same time it is being written, there is a race condition where DNorm reads the data before it is ready. There is also a race condition with the output, where it could be read by the downstream process before DNorm has completed processing. DNorm therefore uses a protocol involving lock files: a file that is being written (e.g. "file.txt") will also have a lock file (".file.txt.lck") in the same folder until the file is complete. A process that wishes to use DNorm to process a file would therefore use the following procedure:
	a.	The process creates ".file.txt.lck" in the INPUT folder
	b.	The process writes "file.txt" in the INPUT folder
	c.	The process deletes ".file.txt.lck" from the INPUT folder
	d.	DNorm sees "file.txt" and no ".file.txt.lck" in the INPUT folder
	e.	DNorm creates ".file.txt.lck" in the OUTPUT folder
	f.	DNorm reads "file.txt" in the INPUT folder, writing output to "file.txt" in the OUTPUT folder
	g.	DNorm deletes ".file.txt.lck" from the OUTPUT folder
	h.	The process sees "file.txt" and no ".file.txt.lck" in in the OUTPUT folder
	i.	The process reads "file.txt" in the OUTPUT folder
	
H. [Preparing UMLS]
	
	If you intend to retrain BANNER, you will need to download a copy of the UMLS Metathesaurus. If you do not retrain BANNER, it is not needed and this step may be skipped.
	
	The software is known to support the format of the 2013 AA version, which can be obtained from:
	http://download.nlm.nih.gov/umls/kss/2013AA/active/2013aa-1-meta.nlm
	http://download.nlm.nih.gov/umls/kss/2013AA/active/2013aa-2-meta.nlm
	Change the "nlm"ù file extension to "zip"ù, unzip the files and place the MRCONSO.* and MRSTY.* files in folder accessible from your Linux command line (call this UMLS). You may delete all other files from this download.

	In the DNorm/config folder, open the file banner_BC5CDR_UMLS2013AA_SAMPLE.xml and change the "***PATH***" to the full path of the UMLS folder:
			  <dirWithMRSTY>***PATH***</dirWithMRSTY>
			  <dirWithMRCONSO>***PATH***</dirWithMRCONSO>
	
	The other configuration files will need the same modification, most notably the file config/banner_BC5CDR_UMLS2013AA_TRAINDEV.xml

I. [Instructions for retraining BANNER]
	
	The pretrained BANNER models can be recreated using the banner.sh script and the appropriate configuration file. For example, to recreate the model trained on the training and development sets:

	./banner.sh train config/banner_BC5CDR_UMLS2013AA_TRAINDEV.xml
	
	The parameters are as follows:
	1) The BANNER command; in this case "train"
	2) The BANNER configuration file to use. 
	
	The configuration files under the config folder specify many parameters, but the most important is which dataset and model name to use.
	
	The config files that end in "TRAIN" load the training set (minus the sample set) and the "TRAIN" model; these are intended for training the "TRAIN" model. The "SAMPLE" configurations load the sample set and the "TRAIN" model: this is so you can evaluate the "TRAIN" model on the sample set. 

	To retrain the BANNER model that includes the sample data:
	
	./banner.sh train config/banner_BC5CDR_UMLS2013AA.xml

J. [Instructions for downloading and modifying BANNER]
	
	DNorm uses BANNER to perform NER. You will need the source code if you wish to make modifications to BANNER, which can be downloaded from the Subversion site on Sourceforge. From a Linux command line, execute: 

	svn co https://svn.code.sf.net/p/banner/code/trunk banner

	When the modifications are complete, copy the banner/lib/banner.jar to the DNorm/libs folder.
	
K. [Instructions for testing BANNER]

	If you are modifying BANNER or the trained BANNER models, you will likely want to specifically evaluate the BANNER model, which can be performed with the banner.sh script.

	./banner.sh test config/banner_BC5CDR_UMLS2013AA_SAMPLE.xml
	
	The parameters are as follows:
	1) The BANNER command; in this case "test"
	2) The BANNER configuration file to use. The example uses the model trained on the BC5CDR training data (minus the sample set) and loads the BC5CDR sample data.

	This output will display the performance:
		OVERALL: 
		TP: 330
		FP: 68
		FN: 99
		precision: 0.8291457286432161
		   recall: 0.7692307692307693
		f-measure: 0.7980652962515116

L. [Instructions for retraining DNorm]	

	Unlike BANNER, the configuration for DNorm is specified on the command line. Since DNorm uses BANNER to load the dataset, however, the BANNER configuration files are still needed.

	The following line will train DNorm on the BC5CDR training set (minus the sample set), using the sample set as a holdout set:

	./TrainSynonymMatrix.sh data/CTD_diseases-2015-06-04.tsv data/BC5CDR/abbreviations.tsv config/banner_BC5CDR_UMLS2013AA_TRAINDEV.xml 1000 0.001 50 config/banner_BC5CDR_UMLS2013AA_SAMPLE.xml output/simmatrix_BC5CDR_e4_TRAINDEV.bin

	The parameters are as follows:
	1) The lexicon file, a tab separated values file, containing the lexicon to use. The default is the CTD diseases file, which is provided.
	2) A list of the abbreviations found by Ab3P in the data; this is a simple tab-delimited format, but is not the same as the output of Ab3p. An example for the NCBI Disease corpus is provided.
	3) A BANNER configuration file specifying which dataset to use for training.
	4) The maximum rank to consider before the similarity is said to be 0. 1000 works well, and there is no need to modify this.
	5) The learning rate (lambda). Values in the range of 0.001 to 0.0001 seem to work well, but you will need to determine this empirically.
	6) The maximum number of training iterations before stopping. DNorm stops training if the performance drops on the holdout set, but will stop early if it reaches this number.
	7) A BANNER configuration file specifying which dataset to use as a holdout set. DNorm stops training if performance drops on this set.
	8) The name of the model file to output

	Please note that some variation is expected in the quality of the DNorm model.

	To train the DNorm model that includes the sample data, use:
	./TrainSynonymMatrix.sh data/CTD_diseases-2015-06-04.tsv data/BC5CDR/abbreviations.tsv config/banner_BC5CDR_UMLS2013AA.xml 1000 0.001 50 config/banner_BC5CDR_UMLS2013AA_SAMPLE.xml output/simmatrix_BC5CDR_e4.bin

M. [Known issues]
	
	If the scripts are downloaded and unpacked using Windows software, such as WinZip, then it is likely that the newlines were converted to DOS format. This causes errors during execution, typically "Error: Could not find or load main class"
	
	This can be corrected with the dos2unix command. Executing the following line from the base DNorm folder will convert all scripts back to UNIX format:
	
	find . -name "*.sh" | xargs dos2unix
	
N. [References]

	Robert Leaman, Rezarta Islamaj Dogan and Zhiyong Lu, DNorm: Disease Name Normalization with Pairwise Learning to Rank. Bioinformatics (2013) 29 (22): 2909-2917, doi:10.1093/bioinformatics/btt474
	Robert Leaman, Ritu Khare and Zhiyong Lu, NCBI at 2013 ShARe/CLEF eHealth Share Task: Disorder Normalization in Clinical Notes with DNorm. Working Notes of the Conference and Labs of the Evaluation Forum (2013)
	Robert Leaman and Zhiyong Lu, Automated Disease Normalization with Low Rank Approximations. Proceedings of BioNLP 2014: pp 24-28
	Robert Leaman and Graciela Gonzalez, BANNER: an executable survey of advances in biomedical named entity recognition. Pac. Symp. Biocomput. 2008;13:652-663.
