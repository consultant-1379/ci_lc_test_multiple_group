#!/bin/bash

dump() {
   local CMD=$1
   local FILE=$2
   echo "@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@" >> $FILE
   date >> $FILE
   echo "@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@" >> $FILE
   eval $CMD >> $FILE
}

while [ 1 ]; do
   dump "ps aux --sort -rss | head -20" process_rss_sorted.log
   dump "ps aux --sort -%cpu | head -20" process_cpu_sorted.log
   dump free memory.log 
   sleep 30
done
