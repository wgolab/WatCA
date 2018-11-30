#!/bin/bash

USR=ubuntu

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
  ec2-describe-instances | grep 'INSTANCE' | grep 'running' | cut -d$'\t' -f 2 > instances_tmp
  echo Found `cat instances_tmp`
  for IID in `cat instances_tmp`
  do      
      ec2-stop-instances $IID
  done
done

rm -f instances_tmp
