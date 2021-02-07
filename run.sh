#! /bin/bash
JAR=target/evaluation-mig-1.0-SNAPSHOT-jar-with-dependencies.jar
MAINCLASS=test.MIGEvaluator

# Clean eval jar
mvn clean

# Build eval jar
mvn package

# Pause
read -p "Press [Enter] key to continue..."

java -jar ${JAR} de.ovgu.featureide.fm.benchmark.OutputCleaner config

java -da -Xmx12g -jar ${JAR} ${MAINCLASS} config
