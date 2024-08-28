#!/bin/bash

if [[ "$#" -eq 3 ]]; then
  TARGET_FILE_DIR=$1
  NEW_SYMLINK_DIR=$2
  MINUTES_OFFSET=$3
else
  echo Illegall number of args
  exit
fi

echo arg1 $TARGET_FILE_DIR
echo arg2 $NEW_SYMLINK_DIR

list=$(ls -p $TARGET_FILE_DIR | grep -v / )

echo $list

COUNT=1

for i in $list
do
  MINUTES=$(($(($COUNT -1)) * $MINUTES_OFFSET + 1))
  TIMESTAMP=$(date -d "-$MINUTES minute" +%Y%m%d%H%M)
  echo Creating symlink $NEW_SYMLINK_DIR/symlink$COUNT to file $TARGET_FILE_DIR/$i
  ln -s $TARGET_FILE_DIR/$i $NEW_SYMLINK_DIR/symlink$COUNT
  echo Setting timestamp of $NEW_SYMLINK_DIR/symlink$COUNT to $TIMESTAMP
  touch -mht ${TIMESTAMP} $NEW_SYMLINK_DIR/symlink$COUNT
  ((COUNT++))
done
