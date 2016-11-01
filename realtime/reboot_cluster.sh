#!/bin/bash

USR=ubuntu

echo Make sure your servers_public file is populated with IPs!

for S in `cat servers_public`
do
    echo Rebooting server $S
    scp reboot_server.sh $USR@$S:
    ssh $USR@$S "./reboot.sh"
done
