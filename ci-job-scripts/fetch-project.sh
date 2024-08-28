#! /bin/bash

WORK_DIR=$1

[ -z "$WORK_DIR" ] && WORK_DIR=$PROJECT_NAME

pushd $WORK_DIR

if [ ! -z "$GERRIT_REFSPEC" ]; then
  echo "Fetching $GERRIT_REFSPEC ..."
  git fetch $GIT_URL $GERRIT_REFSPEC
  git checkout FETCH_HEAD
fi  

git status

popd
