CP=libs/banner.jar
CP=${CP}:libs/dragontool.jar
CP=${CP}:libs/heptag.jar
CP=${CP}:libs/commons-collections-3.2.1.jar
CP=${CP}:libs/commons-configuration-1.6.jar
CP=${CP}:libs/commons-lang-2.4.jar
CP=${CP}:libs/mallet-deps.jar
CP=${CP}:libs/mallet.jar
CP=${CP}:libs/commons-logging-1.1.1.jar
FUNCTION=$1
CONFIG=$2
PERCENTAGE=$3
java -cp ${CP} banner.eval.BANNER $FUNCTION $CONFIG $PERCENTAGE

