#!/bsn/bash

DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
cd ${DIR}

source settings.sh
source gen_file/config.update

fun_init_schema() {
    echo "Waiting for Cassandra servers to show up/normal status"

    sleep 5
    upNum=`cd ${CassandraPath}/current-version && bin/nodetool status | grep UN | wc -l`
    totalNum=`cat servers_ec2 | wc -l`
    while [[ $upNum != $totalNum ]];do
        echo "started "${upNum}" servers out of "${totalNum}
        sleep 3
        upNum=`cd ${CassandraPath}/current-version && bin/nodetool status | grep UN | wc -l`
    done
    echo "started "${upNum}" servers out of "${totalNum}

    public_ip=`cat gen_file/myIP`
    ip=`cat servers_ec2_public_private | grep $public_ip | awk '{print $2}'`
    #ip=`cat gen_file/myIP`
    type=$storage_type
    sed -e s/Replication_Factor_Placeholder/${replication_factor}/ \
        cql.template.${type} > gen_file/cassandra.cql.script1
    result=0

    while [[ $result != 1 ]];do
        echo "Begin to init schema of cassandra on seed"
        if [ "$type" == "Cassandra2_0" ];then
            ${CassandraPath}/current-version/bin/cassandra-cli -h $ip -f gen_file/cassandra.cql.script1
            result=`${CassandraPath}/current-version/bin/cqlsh $ip -e "SELECT * FROM system.schema_keyspaces" | grep usertable | wc -l`
        elif [ "$type" == "Cassandra2_2" ];then
            ${CassandraPath}/current-version/bin/cqlsh $ip -f gen_file/cassandra.cql.script1
            result=`${CassandraPath}/current-version/bin/cqlsh $ip -e "SELECT * FROM system.schema_keyspaces" | grep ycsb | wc -l`
        else
            echo "Error DB type: "$type
            return
        fi
        sleep 3
    done

    echo "Schema updated, waiting for servers to synchronize schema"
    for host in `cat servers_ec2`
    do
        result=0
        while [[ $result != 1 ]];do
            echo "waiting for host: "${host}
            if [ "$type" == "Cassandra2_0" ];then
                result=`${CassandraPath}/current-version/bin/cqlsh $host -e "SELECT * FROM system.schema_keyspaces" | grep usertable | wc -l`
            elif [ "$type" == "Cassandra2_2" ];then
                result=`${CassandraPath}/current-version/bin/cqlsh $host -e "SELECT * FROM system.schema_keyspaces" | grep ycsb | wc -l`
            else
                echo "Error DB type: "$type
                return
            fi
            sleep 2
        done
    done
    echo "Done waiting"
}

fun_kill_DB() {
    for pid in `ps auwx | grep cassandra | awk {'print $2'}`
    do
        kill $pid
    done
}

fun_start_DB() {

    # delete old data
    # cassandra 2.2
    rm -rf ${CassandraPath}/current-version/data/*
    # cassandra 2.0
    rm -rf ${CassandraDataPath}/data/*
    rm -rf ${CassandraDataPath}/commitlog/*
    rm -rf ${CassandraDataPath}/saved_caches/*

    # create yaml file
    # TODO Hua when geo_replication private ip and ip will be different at
    # listen_address
    seed=`head -n 1 servers_ec2`
    # cassandra does not allows concurrent join, we set all server as seed
    for host in `tail servers_ec2 -n +2`
    do
        seed="$seed, $host"
    done

    #myip=`cat gen_file/myIP`
    public_ip=`cat gen_file/myIP`
    myip=`cat servers_ec2_public_private | grep $public_ip | awk '{print $2}'`
    type=$storage_type
    if [ "$type" == "Cassandra2_0" ];then
        sed -e s/Seed_Ip_Address_Placeholder/"${seed}"/ \
            -e s/Listen_Address_Placeholder/""/ \
            -e s/Broadcast_Address_Placeholder/${public_ip}/ \
            -e s/Rpc_Address_Placeholder/""/ \
            cassandra.v2_0_17.yaml > cassandra.yaml
    elif [ "$type" == "Cassandra2_2" ];then
        sed -e s/Seed_Ip_Address_Placeholder/"${seed}"/ \
            -e s/Listen_Address_Placeholder/""/ \
            -e s/Broadcast_Address_Placeholder/${public_ip}/ \
            -e s/Rpc_Address_Placeholder/""/ \
            cassandra.v2_2_5.yaml > cassandra.yaml
    else
        echo "Error DB type: "$type
        return
    fi
    mv cassandra.yaml ${CassandraPath}/current-version/conf/

    # start server
    cd ${CassandraPath}/current-version/
    bin/cassandra &> /var/log/cassandra/output.log
}

fun_load_ycsb() {
    #myip=`cat gen_file/myIP`
    public_ip=`cat gen_file/myIP`
    myip=`cat servers_ec2_public_private | grep $public_ip | awk '{print $2}'`
    echo "load phase of ycsb:"$myip
    type=$storage_type
    if [ "$type" == "Cassandra2_0" ];then
        java -cp "ycsb_wrapper/:${YCSBPath}/current-version/lib/*:${YCSBPath}/current-version/cassandra-binding/lib/*" \
            -Danalysis.ConnectorClass=com.yahoo.ycsb.db.CassandraClient10 \
            -Danalysis.LogFile=/tmp/not_need_log_file.log \
            -Danalysis.LogHost=localhost  -Danalysis.LogPort=${ServerLogPort} \
            com.yahoo.ycsb.Client -load -db ca.uwaterloo.watca.YCSBConnectorWrapper \
            -p maxexecutiontime=0 -P gen_file/workload".${myip}" \
            -threads $YCSB_threads_for_load \
            -s -p hosts=$myip
    elif [ "$type" == "Cassandra2_2" ];then
        java -cp "ycsb_wrapper/:${YCSBPath}/current-version/lib/*:${YCSBPath}/current-version/cassandra2-binding/lib/*" \
            -Danalysis.ConnectorClass=com.yahoo.ycsb.db.CassandraCQLClient \
            -Danalysis.LogFile=/tmp/not_need_log_file.log \
            -Danalysis.LogHost=localhost  -Danalysis.LogPort=${ServerLogPort} \
            com.yahoo.ycsb.Client -load -db ca.uwaterloo.watca.YCSBConnectorWrapper \
            -p maxexecutiontime=0 -P gen_file/workload".${myip}" \
            -threads $YCSB_threads_for_load \
            -s -p hosts=$myip
    else
        echo "Error DB type: "$type
    fi
}

fun_work_ycsb() {
    #myip=`cat gen_file/myIP`
    public_ip=`cat gen_file/myIP`
    myip=`cat servers_ec2_public_private | grep $public_ip | awk '{print $2}'`
    echo "work phase of ycsb:"$myip
    type=$storage_type
    if [ "$type" == "Cassandra2_0" ];then
        java -cp "ycsb_wrapper/:${YCSBPath}/current-version/lib/*:${YCSBPath}/current-version/cassandra-binding/lib/*" \
            -Danalysis.ConnectorClass=com.yahoo.ycsb.db.CassandraClient10 \
            -Danalysis.LogFile=/tmp/not_need_log_file.log \
            -Danalysis.LogHost=localhost  -Danalysis.LogPort=${ServerLogPort} \
            com.yahoo.ycsb.Client -t -db ca.uwaterloo.watca.YCSBConnectorWrapper \
            -p maxexecutiontime=${num_seconds_to_run} -P gen_file/workload".${myip}" \
            -threads $YCSB_threads -target $target_thr_per_host \
            -p cassandra.readconsistencylevel=$read_consistency \
            -p cassandra.writeconsistencylevel=$write_consistency \
            -p readdelay=$read_delay -p writedelay=$write_delay \
            -p readconprob=$con_prob -p writeconprob=$con_prob \
            -s -p hosts=$myip
    elif [ "$type" == "Cassandra2_2" ];then
        java -cp "ycsb_wrapper/:${YCSBPath}/current-version/lib/*:${YCSBPath}/current-version/cassandra2-binding/lib/*" \
            -Danalysis.ConnectorClass=com.yahoo.ycsb.db.CassandraCQLClient \
            -Danalysis.LogFile=/tmp/not_need_log_file.log \
            -Danalysis.LogHost=localhost  -Danalysis.LogPort=${ServerLogPort} \
            com.yahoo.ycsb.Client -t -db ca.uwaterloo.watca.YCSBConnectorWrapper \
            -p maxexecutiontime=${num_seconds_to_run} -P gen_file/workload".${myip}" \
            -threads $YCSB_threads -target $target_thr_per_host \
            -p cassandra.readconsistencylevel=$read_consistency \
            -p cassandra.writeconsistencylevel=$write_consistency \
            -p readdelay=$read_delay -p writedelay=$write_delay \
            -p readconprob=$con_prob -p writeconprob=$con_prob \
            -s -p hosts=$myip
    else
        echo "Error DB type: "$type
    fi
}

case $1 in
    kill)
        fun_kill_DB
        echo "====kill cassandra done!.===="
        ;;
    start)
        fun_start_DB
        ;;
    init_schema)
        fun_init_schema
        ;;
    load)
        fun_load_ycsb
        ;;
    workYcsb)
        fun_work_ycsb
        ;;
    *)
        echo "Control stub cmd not found:"$1
esac

exit 0
