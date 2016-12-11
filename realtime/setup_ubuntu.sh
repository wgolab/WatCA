#!/bin/bash

# Cassandra version.
CVER=2.2.7
YCSBVER=0.10.0


sudo apt-get -y update
sudo apt-get -y install build-essential

# install java 8
#sudo add-apt-repository ppa:webupd8team/java
#sudo apt-get -y update
#sudo apt-get -y install oracle-java8-installer
#sudo apt-get -y install oracle-java8-set-default
sudo apt-get install openjdk-8-jdk
# test
java -version
sudo apt-get -y install ntp


# install python and cassandra driver
sudo apt-get -y install python
sudo apt-get install -y python-pip
sudo pip install cqlsh


# install cassandra
mkdir -p ~/cassandra
cd  ~/cassandra
wget http://archive.apache.org/dist/cassandra/$CVER/apache-cassandra-$CVER-bin.tar.gz
tar -xzvf apache-cassandra-$CVER-bin.tar.gz
sudo mkdir /var/lib/cassandra
sudo mkdir /var/log/cassandra
sudo chown -R $USER:$GROUP /var/lib/cassandra
sudo chown -R $USER:$GROUP /var/log/cassandra
cd ~/cassandra
unlink current-version
ln -s apache-cassandra-$CVER current-version

export CASSANDRA_HOME=~/cassandra/current-version
export PATH=$PATH:$CASSANDRA_HOME/bin
# test
sh current-version/bin/cassandra
sh current-version/bin/cqlsh

# install YCSB
mkdir -p ~/ycsb
cd  ~/ycsb
curl -O --location https://github.com/brianfrankcooper/YCSB/releases/download/$YCSBVER/ycsb-$YCSBVER.tar.gz
tar xfvz ycsb-$YCSBVER.tar.gz
unlink current-version
ln -s ycsb-$YCSBVER current-version
