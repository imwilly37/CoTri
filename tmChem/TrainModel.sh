CP=libs/CHEMDNER.jar
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
PERCENTAGE=$2
java -Xmx10G -Xms10G -cp ${CP} ncbi.TrainBANNER $CONFIG data/empty.tsv $PERCENTAGE
