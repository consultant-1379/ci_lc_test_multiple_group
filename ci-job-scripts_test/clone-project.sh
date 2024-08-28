#! /bin/bash

echo "Cloning $GIT_URL"

git clone $GIT_URL

cd $PROJECT_NAME

git checkout master

if [ ! -z "$GERRIT_REFSPEC" ]; then
  echo "Fetching $GERRIT_REFSPEC ..."
  git fetch $GIT_URL $GERRIT_REFSPEC
  git checkout FETCH_HEAD
fi  

git status
