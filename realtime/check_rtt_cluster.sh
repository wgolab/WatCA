#!/bin/bash

for i in `cat servers_public`
do
    for j in `cat servers_public`
    do
	if [ "$i" != "$j" ]
        then
	    ssh ubuntu@$i "ping -c 5 $j | grep rtt | cut -d' ' -f 4 | cut -d'/' -f 2"
	fi
    done
done
