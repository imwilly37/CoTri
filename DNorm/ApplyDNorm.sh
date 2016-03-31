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
CONFIG=$1
LEXICON=$2
MATRIX=$3
AB3P_DIR=$4
TEMP_DIR=$5
INPUT=$6
OUTPUT=$7
java -Xmx10G -Xms10G -cp ${CP} dnorm.ApplyDNorm $CONFIG $LEXICON $MATRIX $AB3P_DIR $TEMP_DIR $INPUT $OUTPUT

