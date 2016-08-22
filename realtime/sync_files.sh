#!/bin/bash

DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
cd ${DIR}

source settings.sh

arg1=$1

if [ -z $ScriptHostWatcaPath ];then
    echo ERROR ScriptHostWatcaPath empty.
    exit 1;
fi


seq=0
for host in `cat servers_ec2`
do
    echo "sync files for ${host}"
    if [ "$arg1" == "init" ];then
        mkdir -p gen_file
        mkdir -p STATE
        rsync -acvz --exclude-from=EXCLUDE_FILES $ScriptHostWatcaPath/ $RemoteUser@$host:$WatcaPath
        rsync -acvz $ScriptHostCassandraPath/ $RemoteUser@$host:$CassandraPath
    elif [ "$arg1" == "cleaninit" ];then
        mkdir -p gen_file
        mkdir -p STATE
        rsync -acvz --delete --exclude-from=EXCLUDE_FILES $ScriptHostWatcaPath/ $RemoteUser@$host:$WatcaPath
        rsync -acvz --delete $ScriptHostCassandraPath/ $RemoteUser@$host:$CassandraPath
    else
        genFilePath=$ScriptHostWatcaPath"/realtime/gen_file"
        echo $host > $genFilePath"/myIP"
        echo $seq > $genFilePath"/mySeq"
        #rsync -acvz --exclude-from=EXCLUDE_FILES $genFilePath/ $RemoteUser@$host:$genFilePath
        rsync -acz --exclude-from=EXCLUDE_FILES $genFilePath/ $RemoteUser@$host:$WatcaPath"/realtime/gen_file"
    fi

    seq=$((seq + 1))
    echo Finish sync file for this host.
done


