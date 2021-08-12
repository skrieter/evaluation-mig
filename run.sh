#! /bin/bash
JAR=evaluation-mig-1.0-SNAPSHOT-combined.jar

java -jar ${JAR} eval-clean config

java -da -Xmx12g -jar ${JAR} eval-mig-builder config
