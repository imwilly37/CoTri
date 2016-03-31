CP=libs/dnorm.jar
CP=${CP}:libs/colt.jar
CP=${CP}:libs/lucene-analyzers-3.6.0.jar
CP=${CP}:libs/lucene-core-3.6.0.jar
CP=${CP}:libs/trove-3.0.3.jar
CP=${CP}:libs/libs.jar
CP=${CP}:libs/commons-configuration-1.6.jar
CP=${CP}:libs/commons-collections-3.2.1.jar
CP=${CP}:libs/commons-lang-2.4.jar
CP=${CP}:libs/commons-logging-1.1.1.jar
CP=${CP}:libs/banner.jar
CP=${CP}:libs/dragontool.jar
CP=${CP}:libs/heptag.jar
CP=${CP}:libs/mallet.jar
CP=${CP}:libs/mallet-deps.jar
LEXICON_FILENAME=$1 # data/CTD_diseases.tsv
ABBREVIATIONS=$2 # data/abbreviations.tsv
TRAINING_CONFIGURATION=$3 # config/banner_NCBIDiseasePubtator_TRAIN.xml
TOP_N=$4 # 1000
LEARNING_RATE=$5 # 0.001
MAX_TRAINING_ITERATIONS=$6 # 50
HOLDOUT_CONFIGURATION=$7 # config/banner_NCBIDiseasePubtator_DEV.xml
MODEL_FILENAME=$8 # output/simmatrix.bin
java -ea -Xmx10G -Xms10G -cp ${CP} dnorm.TrainSynonymMatrix $LEXICON_FILENAME $ABBREVIATIONS $TRAINING_CONFIGURATION $TOP_N $LEARNING_RATE $MAX_TRAINING_ITERATIONS $HOLDOUT_CONFIGURATION $MODEL_FILENAME

