[Directory]

A. [Versions]
B. [Contacts]
C. [Prerequisites]
D. [Preparing Ab3P]
E. [Test tmChem.M1 setup]
F. [Running tmChem.M1]
G. [Running the polling version of tmChem.M1]
H. [Instructions for retraining tmChem.M1]
I. [Instructions for downloading and modifying BANNER]
J. [Known issues]
K. [References]

#======================================================================================#

A. [Versions]

	0.0.2 Upgraded documentation

B. [Contacts]

	If you have any questions or problems, please e-mail robert.leaman@nih.gov

C. [Prerequisites]

	All scripts are prepared and tested for the Linux command line. Windows equivalents should be relatively straightforward (with the possible exception of Ab3P), but are not provided.

	Running tmChem.M1 requires the Java runtime environment.

	Compiling tmChem.M1 also requires the Java SE Development Kit and the Apache build tool ant.

	Scripts are configured to request 10Gb main memory. It is likely possible to run with less.
	
	Highest performance from tmChem.M1 requires Ab3P to resolve abbreviations. This README will assume you intend the highest performance, so the next sections describe how to prepare these.

D. [Preparing Ab3P]
	
	Download the Ab3P abbreviation resolution tool, version 1.5 from ftp://ftp.ncbi.nlm.nih.gov/pub/wilbur. Extract it into a folder accessible from the command line. Call this folder AB3P_DIR.
	
	Ab3P must be compiled after download. The command is "make", but it is recommended for you to open the readme file for Ab3P and follow the full instructions there.

	tmChem.M1 uses the file system to communicate with Ab3P. We therefore need a folder that can be used to store temporary files. Any folder should be usable, though this has been best tested with a dedicated temporary folder that is otherwise unused. Call this folder TEMP.

E. [Test tmChem.M1 setup]	

	To verify that the tmChem.M1 setup is correct, run the Eval script:

	./Eval.sh config/banner_JOINT_SAMPLE.xml

	The parameters are as follows:
	1) A BANNER configuration file specifying which dataset to use for the test, which model file and which BANNER configuration.
	
	The code will complain rather verbosely that many annotations in the file are missing concept IDs. This is expected.

	You will get the following output:

	TP: 451
	FP: 20
	FN: 59
	precision: 0.9575371549893843
	   recall: 0.884313725490196
	f-measure: 0.9194699286442406

	This measurement only considers the boundaries of each mention (exact), and ignores the identifier.
	
F. [Running tmChem.M1]

	To use tmChem.M1 to process a file in PubTator format, use the Run script:
	
	./Run.sh config/banner_JOINT_SAMPLE.xml data/dict.txt AB3P_DIR TEMP INPUT OUTPUT

	The parameters are as follows:
	1) A BANNER configuration file specifying which model file and which BANNER configuration to use; any dataset specified in this file is ignored
	2) The dictionary file, a tab separated values file, containing the lexicon to use. The dictionary provided is a mix of MeSH, ChEBI and some manual entries.
	4) The folder containing Ab3P; see Section E
	5) The temp folder; see Section E
	6) The input file or folder. If this is a file, it will be processed and then output written to the specified file name. If this is a folder, then each file in the folder will be processed and output written to output folder using the same filename.
	7) The output file or folder. The input and output should both be files or both be folders.

	The INPUT and OUTPUT parameters can either be single files or directories. Input files may be either in PubTator or BioC format: the output format will be the same as the input format.
	
	When running on real test data, you will want to use the models whose training data included the sample data. To do this, you need to use a different BANNER configuration file:

	./Run.sh config/banner_JOINT.xml data/dict.txt AB3P_DIR TEMP INPUT OUTPUT

	tmChem.M1 loads in only a few seconds. Once loaded, it processes approximately 10 abstracts per second. 
	
G. [Running the polling version of tmChem.M1]

	While tmChem.M1 does not require significant time to load we have nevertheless provided a version of tmChem.M1 that continuously checks an input folder for input files, processes each file found, and places the result in an output folder.
	
	To run the polling version of tmChem.M1, use the Poll script:
	
	./Poll.sh config/banner_JOINT.xml data/dict.txt AB3P_DIR TEMP INPUT OUTPUT

	The command line parameters are the same as the Run script described in [F], except that INPUT and OUTPUT must be folders. Input files may be either in PubTator or BioC format: the output format will be the same as the input format. Files whose name ends with ".xml" will be processed as BioC, all others as PubTator. To reduce excessive use of processor time, tmChem.M1 waits 500 milliseconds between checks of the INPUT folder.
	
	Since tmChem.M1 is potentially reading input at the same time it is being written, there is a race condition where tmChem.M1 reads the data before it is ready. There is also a race condition with the output, where it could be read by the downstream process before tmChem.M1 has completed processing. tmChem.M1 therefore uses a protocol involving lock files: a file that is being written (e.g. "file.txt") will also have a lock file (".file.txt.lck") in the same folder until the file is complete. A process that wishes to use tmChem.M1 to process a file would therefore use the following procedure:
	a.	The process creates ".file.txt.lck" in the INPUT folder
	b.	The process writes "file.txt" in the INPUT folder
	c.	The process deletes ".file.txt.lck" from the INPUT folder
	d.	tmChem.M1 sees "file.txt" and no ".file.txt.lck" in the INPUT folder
	e.	tmChem.M1 creates ".file.txt.lck" in the OUTPUT folder
	f.	tmChem.M1 reads "file.txt" in the INPUT folder, writing output to "file.txt" in the OUTPUT folder
	g.	tmChem.M1 deletes ".file.txt.lck" from the OUTPUT folder
	h.	The process sees "file.txt" and no ".file.txt.lck" in in the OUTPUT folder
	i.	The process reads "file.txt" in the OUTPUT folder
	
	When running on real test data, you will want to use the models whose training data included the sample data. To do this, you need to use a different BANNER configuration file:

	./Poll.sh config/banner_JOINT.xml data/dict.txt AB3P_DIR TEMP INPUT OUTPUT
	
H. [Instructions for retraining tmChem.M1]
	
	The pretrained tmChem.M1 models can be recreated using the TrainModel.sh script and the appropriate configuration file. For example, to recreate the model trained on the training and development sets:

	./TrainModel.sh config/banner_JOINT_TRAINDEV.xml
	
	The parameters are as follows:
	1) The BANNER configuration file to use. 
	
	The configuration files under the config folder specify many parameters, but the most important is which dataset and model name to use.
	
	There are two corpors with a total of five subsets available: the CHEMDNER corpus, with TRAIN and TEST subsets and the BC5CDR corpus, with TRAIN, DEV and SAMPLE subsets. For the purposes of this readme, the SAMPLE articles have been removed from the BC5CDR TRAIN set. The CHEMDNER_TRAINDEV config loads the CHEMDNER.TRAIN subset and CHEMDNER.TEST config loads the CHEMDNER.TEST subset. The JOINT_TRAINDEV config loads CHEMDNER.TRAIN, CHEMDNER.TEST, BC5CDR.TRAIN, and BC5CDR.DEV. The JOINT_SAMPLE config loads the BC5CDR.SAMPLE. The JOINT_TRAINDEV config loads CHEMDNER.TRAIN, CHEMDNER.TEST, BC5CDR.TRAIN, BC5CDR.DEV and BC5CDR.SAMPLE. 
	
	To retrain the BANNER model that includes the sample data:
	
	./TrainModel.sh config/banner_JOINT.xml

I. [Instructions for downloading and modifying BANNER]
	
	tmChem.M1 uses BANNER. You will need the source code if you wish to make modifications to BANNER, which can be downloaded from the Subversion site on Sourceforge. From a Linux command line, execute: 

	svn co https://svn.code.sf.net/p/banner/code/trunk banner

	When the modifications are complete, copy the banner/lib/banner.jar to the tmChem.M1/libs folder.
	
J. [Known issues]
	
	If the scripts are downloaded and unpacked using Windows software, such as WinZip, then it is likely that the newlines were converted to DOS format. This causes errors during execution, typically "Error: Could not find or load main class"
	
	This can be corrected with the dos2unix command. Executing the following line from the base tmChem.M1 folder will convert all scripts back to UNIX format:
	
	find . -name "*.sh" | xargs dos2unix
	
K. [References]

	Robert Leaman, Chih-Hsuan Wei, Zhiyong Lu, tmChem: a high performance tool for chemical named entity recognition and normalization, Journal of Cheminformatics, 2015;7(Suppl 1):S3.
	Robert Leaman and Graciela Gonzalez, BANNER: an executable survey of advances in biomedical named entity recognition. Pac. Symp. Biocomput. 2008;13:652-663.
