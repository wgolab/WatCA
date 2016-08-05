#!/bin/bash

export CLASSPATH=".:build:lib/*"

echo Cleaning up
rm -fr build/*

echo Building
javac -d build ca/uwaterloo/watca/*.java
if [ $? -ne 0 ]; then
    exit
fi

echo Running
java ca.uwaterloo.watca.LinearizabilityTest execution.log scores.txt

echo Done
#echo Top of scores.txt file:
#head scores.txt
#echo Set of distinct scores observed in scores.txt:
#cat scores.txt | rev | cut -d' ' -f1 | sort -u
echo Score 0: `cat scores.txt | grep 'Score = 0' | wc -l` occurrences
echo Score 1: `cat scores.txt | grep 'Score = 1' | wc -l` occurrences
echo Score 2: `cat scores.txt | grep 'Score = 2' | wc -l` occurrences
echo "Explanation: "
echo "0 means OK"
echo "1 means linearizability violation (bug!)"
echo "2 means a get operation returned a value that was never assigned by a put operation (bug!)"
