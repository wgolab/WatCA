#!/bin/bash

for i in `cat servers_public`
do
    ssh ubuntu@$i "ps -A | grep java"
done
