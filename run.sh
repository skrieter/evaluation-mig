#! /bin/bash
JAR=mig-evaluation.jar
MAINCLASS=org.spldev.evaluation.mig.MIGEvaluator

java -jar ${JAR} org.spldev.evaluation.OutputCleaner config

java -da -Xmx12g -jar ${JAR} ${MAINCLASS} config
