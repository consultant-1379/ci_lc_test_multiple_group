#! /bin/bash

CONTAINER=$1
READY_FILE="/opt/ericsson/docker/jboss/shared/ready"

while [ 1 ]; do 
   docker cp $CONTAINER:$READY_FILE . >& /dev/null && break || echo -n;
   if [ ! -z "${WORKSPACE}" -a -d "${WORKSPACE}" ];then
     DIE_FILES=$(find ${WORKSPACE} -name "*.die")
     if [ ! -z "${DIE_FILES}" ];then
        echo "One or more dockers are died. Check the following files: ${DIE_FILES}"   
        exit 1  
     fi    
   fi
   sleep 2; 
done

