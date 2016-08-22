#!/bin/bash

USR=ubuntu
SFILE=servers_ec2_public_private

echo Make sure your servers_ec2 file is populated with public IPs!

rm $SFILE

for S in `cat servers_ec2`
do
    PRIVIP=`ssh $USR@$S "ifconfig | grep 'inet addr' | grep Bcast | cut -d':' -f2 | cut -d' ' -f1 "`
    echo Found server $S with private IP $PRIVIP
    echo $S $PRIVIP >> $SFILE
done

for S in `cat servers_ec2`
do
    echo Setting up server $S
    scp setup_ubuntu.sh $USR@$S:
    ssh $USR@$S "bash setup_ubuntu.sh"
done
