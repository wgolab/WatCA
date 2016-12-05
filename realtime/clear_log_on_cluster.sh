#!/bin/bash

source settings.sh 

while read pubip; do
  echo Clearing log files on server $pubip
  ssh -n $RemoteUser@${pubip} 'rm /tmp/not_need_log_file.log'
done <./servers_public
