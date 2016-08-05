#!/bin/bash

export CLASSPATH=".:build:lib/*"

echo Cleaning up
rm -fr build/*

echo Building
javac -d build ca/uwaterloo/watca/*.java
if [ $? -ne 0 ]; then
    exit
fi

echo Done
