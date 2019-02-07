#!/bin/bash

DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
cd ${DIR}

source settings.sh

cd $DIR/..
mvn install

cd $DIR
# this line is only needed in the first time of running to send files.
echo "Make sure the file servers_public have changed with the host ip addresses"
rm STATE/*
rm gen_file/*
bash sync_files.sh init


# This file grows without bound!
rm scores.txt
export CLASSPATH=../target/classes/
# TODO Hua args "localhost 12347 javaControl" is meaning less, for historical reason, will be cleaned up
rm watca*.log
java ca.uwaterloo.watca.RealtimeMain $ServerLogPort $WebPort localhost 12357 javaControl saveLogs
