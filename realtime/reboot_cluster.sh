#!/bin/bash

USR=ubuntu

echo Make sure your servers_public and servers_public_private files are populated with IPs!

for S in `cat servers_public`
do
    echo Rebooting server $S
    scp reboot.sh $USR@$S:
    ssh $USR@$S "./reboot.sh"
done
