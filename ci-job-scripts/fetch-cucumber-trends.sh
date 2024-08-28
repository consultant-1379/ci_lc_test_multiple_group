#! /bin/bash

# retrieve cucumber trends file from previous build
[ -z "$CUCUMBER_TRENDS_MAX_DELTA" ] && export CUCUMBER_TRENDS_MAX_DELTA=10
[ -z "$CUCUMBER_TRENDS_FILE" ] && export CUCUMBER_TRENDS_FILE="$IT_REL_PATH/cucumber-trends.json"
for BUILD_INDEX in $(seq 1 $CUCUMBER_TRENDS_MAX_DELTA); do
  BUILD_NUM=$(($BUILD_ID-$BUILD_INDEX))
  TRENDS_FILE_URL="$JOB_URL/$BUILD_NUM/artifact/$CUCUMBER_TRENDS_FILE"
  curl -f -o $CUCUMBER_TRENDS_FILE $TRENDS_FILE_URL  && break || echo "Failed to retrieve cucumber trends file: $TRENDS_FILE_URL"
done
