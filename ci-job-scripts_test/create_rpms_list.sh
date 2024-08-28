#!/bin/bash

RPMS_FOLDER=$1
RPMS_FILE_NAME=$2
RPMS_INFO=$3
RPMS_FILE="$RPMS_FOLDER/$RPMS_FILE_NAME"

RPMS_LIST=$(echo $RPMS_INFO | sed  's/@@/ /g') 

rm -rf $RPMS_FILE

for RPM in $RPMS_LIST; do 
  if [ "$(echo $RPM | grep -o "http")" == "http" ];then
    echo "Handling RPM: $RPM ..."
    RPM_URL=$(echo $RPM | awk -F '::' '{print $2}')
    RPM_NAME=$(echo $RPM_URL |  awk -F '/' '{print $NF}')
    echo "Downloading $RPM_NAME from $RPM_URL ..."
    curl -o $RPMS_FOLDER/$RPM_NAME "$RPM_URL"
  else
    echo $RPM | sed  's/::/:/g' | sed  's/:Latest//g' >> $RPMS_FILE
  fi
done
