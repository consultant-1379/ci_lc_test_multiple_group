#! /bin/bash
#
# author: ebialan
 
CONTAINER=$1
READY_FILE_DIR=$2
READY_FILE_NAME=$3

DEST_DIR="containers/$CONTAINER"
DEFAULT_READY_FILE_DIR="/opt/ericsson/docker/${CONTAINER}/shared"
DEFAULT_READY_FILE_NAME="ready"

[ -z "${READY_FILE_DIR}" ] && READY_FILE_DIR=${DEFAULT_READY_FILE_DIR}
[ -z "${READY_FILE_NAME}" ] && READY_FILE_NAME=${DEFAULT_READY_FILE_NAME}

READY_FILE="${READY_FILE_DIR}/${READY_FILE_NAME}"


mkdir -p $DEST_DIR
rm -rf $DEST_DIR/$READY_FILE_NAME

while [ 1 ]; do 
   docker cp $CONTAINER:$READY_FILE $DEST_DIR/. >& /dev/null && break || echo -n;
   if [ ! -z "${WORKSPACE}" -a -d "${WORKSPACE}" ];then
     DIE_FILES=$(find ${WORKSPACE} -name "*.die")
     if [ ! -z "${DIE_FILES}" ];then
        echo "One or more dockers are died. Check the following files: ${DIE_FILES}"   
        exit 1  
     fi    
   fi
   sleep 2; 
done

