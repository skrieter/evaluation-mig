#! /bin/bash

# Clean eval jar
mvn clean

# Build eval jar
mvn package

# Copy eval jar
rm mig-evaluation.jar
cp target/evaluation-mig-1.0-SNAPSHOT-jar-with-dependencies.jar mig-evaluation.jar
