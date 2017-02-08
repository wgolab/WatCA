#!/bin/bash

export CLASSPATH=".:build:lib/*"

INPUT_DIR=lintest_input
OUTPUT_DIR=lintest_output
mkdir $INPUT_DIR &> /dev/null
mkdir $OUTPUT_DIR &> /dev/null
rm -f $INPUT_DIR/*
rm -f $OUTPUT_DIR/*
cp execution.log $INPUT_DIR/

echo Running
java -cp ".:target/WatCA-1.0-SNAPSHOT.jar" ca.uwaterloo.watca.LinearizabilityTest $INPUT_DIR $OUTPUT_DIR
cp $OUTPUT_DIR/scores.* scores.txt
echo Done
echo Score 0: `cat scores.txt | grep 'Score = 0' | wc -l` occurrences
echo Score 1: `cat scores.txt | grep 'Score = 1' | wc -l` occurrences
echo Score 2: `cat scores.txt | grep 'Score = 2' | wc -l` occurrences
echo "Explanation: "
echo "0 means OK"
echo "1 means linearizability violation (bug!)"
echo "2 means a get operation returned a value that was never assigned by a put operation (bug!)"
