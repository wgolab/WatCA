#!/bin/bash

SRV_PRIV=$(head -n 1 servers_public_private | cut -d' ' -f2)
SRV_PUB=$(head -n 1 servers_public)
echo Configuring sync with server $SRV_PUB / $SRV_PRIV

cat ntp.conf.template2 > ntp.conf

echo Setting up NTP clients
for pubip in `cat servers_public`
do
    echo Server $pubip
    scp ntp.conf ubuntu@${pubip}:/tmp
    ssh ubuntu@${pubip} "sudo cp /tmp/ntp.conf /etc/ntp.conf"
done

echo Restarting NTP
for pubip in `cat servers_public`
do
    echo Server $pubip
    ssh ubuntu@${pubip} "sudo service ntp restart"
done

./ntp_sweep.sh

