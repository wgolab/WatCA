#!/bin/bash

USR=ubuntu
rm -f servers_public
rm -f servers_public_private

echo
echo Note: configure your AWS_ACCESS_KEY and AWS_SECRET_KEY environment variables first!
echo

echo
echo Reading list of EC2 regions for ec2_regions
echo

for REGION in ` cat ec2_regions`
do
  echo Region: $REGION
  export EC2_URL=https://ec2.${REGION}.amazonaws.com
  ec2-describe-instances | grep 'running' | cut -d$'\t' -f 17 > servers_public_tmp
  echo Found `cat servers_public_tmp`
  for PUBIP in `cat servers_public_tmp`
  do
      echo $PUBIP >> servers_public
      echo "    logging in as $USR@$PUBIP"
      PRVIP=`ssh $USR@$PUBIP "ifconfig | grep 'inet addr' | grep Bcast | cut -d':' -f2 | cut -d' ' -f1 "`
      echo "    found VM with IP $PUBIP/$PRVIP"
      echo $PUBIP $PRVIP >> servers_public_private
  done
done

echo
echo Wrote public and private IP addresses to servers_public and servers_public_private:
cat servers_public_private

rm -f servers_public_tmp
