#!/bin/bash

USR=ubuntu

echo Make sure your servers_public file is populated with IPs!

for S in `cat servers_public`
do
    echo Processing server $S
    scp update_upgrade_ubuntu.sh $USR@$S:
    ssh $USR@$S "./update_upgrade_ubuntu.sh"
done
