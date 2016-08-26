#!/bin/bash

USR=ubuntu
rm -f servers_public
rm -f servers_public_private

echo
echo Reading list of resource groups from azure_resource_groups
echo

for RESOURCE_GROUP in ` cat azure_resource_groups`
do
  echo Resource group: $RESOURCE_GROUP
  azure resource list | grep virtualMachine | tr -s ' ' | cut -d ' ' -f 3 > azure_vms_tmp
  for VM in `cat azure_vms_tmp`
  do
      PUBIP=`azure resource show $RESOURCE_GROUP ${VM}-ip Microsoft.Network/publicIPAddresses -o "2015-06-15" | grep ipAddress | tr -s ' ' | cut -d ' ' -f 4`
      echo $PUBIP >> servers_public
      echo "    logging in as $USR@$PUBIP"
      PRVIP=`ssh $USR@$PUBIP "ifconfig | grep 'inet addr' | grep Bcast | cut -d':' -f2 | cut -d' ' -f1 "`
      echo "    found VM $VM with IP $PUBIP/$PRVIP"
      echo $PUBIP $PRVIP >> servers_public_private
  done
done

echo
echo Wrote public and private IP addresses to servers_public and servers_public_private:
cat servers_public_private

rm -f azure_vms_tmp
