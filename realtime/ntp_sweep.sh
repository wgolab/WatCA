#!/bin/sh

for i in `cat servers_public`; do ssh ubuntu@$i "ntpq -pn" ; done
