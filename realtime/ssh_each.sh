#!/bin/bash

USR=ubuntu

echo Make sure your servers_public and servers_public_private files are populated with IPs!

for S in `cat servers_public`
do
    echo Setting up server $S
    scp setup_ubuntu.sh $USR@$S:
    ssh $USR@$S
done
