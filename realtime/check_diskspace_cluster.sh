#!/bin/bash

for i in `cat servers_public`
do
    ssh ubuntu@$i "df -h"
done
