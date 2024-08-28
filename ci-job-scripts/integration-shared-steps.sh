#! /bin/bash
#set -e

function loadEnvFromFile() {
  #Remove last end line
  if [ -f "$ENV_VAR_FILE" ];then
    echo "@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@"
    #cat $ENV_VAR_FILE
    while IFS='' read -r line || [[ -n "$line" ]]; do export  $(echo $line | cut  -s -d '=' -f 1)="$(echo $line |  cut -s -d '=' -f 2-)"; done < $ENV_VAR_FILE
    echo "@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@"
    env
    echo "@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@"
  fi
}

function appendEnvToFile() {
  env | grep "=" >> $ENV_VAR_FILE
  #cp $ENV_VAR_FILE env.tmp
  #head -n-1 env.tmp && tail -1 env.tmp |tr -d '\012' >> $ENV_VAR_FILE
  #cat $ENV_VAR_FILE
}

function killMonitoringJobs() {
  echo "@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@"
  jobs
  for job in $(jobs -p)
  do
    for childJob in $(pgrep -P $job)
    do
      echo -n "killing job $childJob ..."
      kill -9 $childJob || echo "warning: issue while killing $childJob"
      echo "done"
    done
    echo -n "killing job $job ..."
    kill -9 $job || echo "warning: issue while killing $job"
    echo "done"
  done
  echo "@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@"
}

function waitMonitoringJobs() {
  echo "@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@"
  jobs
  for job in $(jobs -p)
  do
    echo -n "waiting job $job ..."
    wait $job || echo "warning: $job exited with failure"
    echo "done"
  done
  echo "@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@"
}

function exitOnEmptyValues() {
  local var=$1
  if [ -z "${!var}" ]; then
    echo "############################# ERROR ##################################"
    echo "######################################################################"
    echo "$var variable is not set !!!"
    echo "######################################################################"
    echo "######################################################################"
    exit 1
  fi
}

function endCallback() {
    local EXIT_CODE=$1
    echo "Exit code: $EXIT_CODE"
    echo "INTEGRATION_EXIT_CODE=$EXIT_CODE" >> $ENV_VAR_FILE
    killMonitoringJobs
    waitMonitoringJobs
    loadEnvFromFile
    source $SCRIPTS_FOLDER/acceptance_job_review.sh $TEST_REPORTS_LOCATION || echo
    appendEnvToFile

    #invoke custom end callback script if exists
    [ ! -z "$END_CALLBACK_SCRIPT" -a -f "$END_CALLBACK_SCRIPT" ] && eval $END_CALLBACK_SCRIPT $EXIT_CODE

    echo "#############################################"
    docker images
    echo "#############################################"

    echo "#############################################"
    docker ps -a
    echo "#############################################"

    [ "$SKIP_DOCKER_TEARDOWN" != "true" ] && dockerTeardown

    syscleanup
}

function dockerTeardown() {
    docker cp ${JBOSS_DOCKER}:/ericsson/3pp/jboss/standalone/log/server.log ${WORKSPACE}/. || echo "Failed to collect sever.log"
    docker cp ${NETSIM_DOCKER}:/netsim/netsimdir/networkMap.json  $WORKSPACE/netsimNetworkMap.log || echo "Failed to collect networkMap.json"
    pushd $DOCKER_COMPOSE_DIR
    $DOCKER_COMPOSE_CMD kill || echo
    $DOCKER_COMPOSE_CMD down -v  || echo
    popd
}

function syscleanup() {

    docker ps -qa | xargs docker rm -fv >& /dev/null  || echo

    local volumes=$(docker volume ls -qf dangling=true)
    if [ ! -z "$volumes" ]; then
      echo "Removing following volumes: $volumes"
      docker volume rm ${volumes} || echo "Failed volumes cleanup"
    fi

    local imagesToBeRemoved=$(docker images --filter "dangling=true" -q)
    if [ ! -z "$imagesToBeRemoved" ]; then
      echo "Removing following images: $imagesToBeRemoved"
      docker rmi -f $imagesToBeRemoved || echo "Failed images cleanup"
    fi

    #invoke custom cleanup  script if exists
    [ ! -z "$CLEANUP_SCRIPT" -a -f "$CLEANUP_SCRIPT" ] && eval $CLEANUP_SCRIPT || echo -n
}

##########################################################################
#main
##########################################################################

#verify mandatory variables
exitOnEmptyValues PROJECT_NAME
exitOnEmptyValues PROJECT_DIR
exitOnEmptyValues IT_REL_PATH
exitOnEmptyValues IT_FOLDER
exitOnEmptyValues DOCKER_COMPOSE_DIR

#JOB CONFIGURATION
[ "$ARQUILLIAN_CUCUMBER_OPTIONS" == "NONE" ] && ARQUILLIAN_CUCUMBER_OPTIONS=""

[ ! -z "$GERRIT_TOPIC" ] && TOPIC="$GERRIT_TOPIC"
[ ! -z "$TOPIC" ] && export REVIEWS_INFO="TOPIC=$TOPIC"
[ ! -z "$GERRIT_CHANGE_NUMBERS" -a -z "$REVIEWS_INFO" ] && export REVIEWS_INFO="REVIEWS=$GERRIT_CHANGE_NUMBERS"

export START_DATE=$(date)

[ -z "$ENV_VAR_FILE" ] && ENV_VAR_FILE="$WORKSPACE/env.txt"
[ -z "$DOCKER_RUNNING_ENV" ] && export DOCKER_RUNNING_ENV="JENKINS"
[ -z "$DOCKER_COMPOSE_CMD" ] && export DOCKER_COMPOSE_CMD="docker-compose ${DOCKER_COMPOSE_OPTIONS}"
[ -z "$COMPOSE_HTTP_TIMEOUT" ] && export COMPOSE_HTTP_TIMEOUT=300
[ -z "$COMPOSE_PROJECT_NAME" ] && export COMPOSE_PROJECT_NAME=$(echo $PROJECT_NAME | sed s/-//g)
[ -z "$JBOSS_DOCKER" ] && export JBOSS_DOCKER="jboss"
[ -z "$NETSIM_DOCKER" ] && export NETSIM_DOCKER="netsim"

[ -z "$BLAST_REPORTING_PROPERTIES" ] && export BLAST_REPORTING_PROPERTIES="-Dcucumber.report.name=$JOB_NAME -Dbuild.number=$BUILD_ID -Dcucumber.report.trendFile=cucumber-trends.json"
[ -z "$RESOLVED_ADDITIONAL_SYSTSEM_PROPERTIES" ] && export RESOLVED_ADDITIONAL_SYSTSEM_PROPERTIES="$BLAST_REPORTING_PROPERTIES $ADDITIONAL_SYSTSEM_PROPERTIES"
[ -z "$RESOLVED_CUCUMBER_OPTIONS" ] && export RESOLVED_CUCUMBER_OPTIONS=${ARQUILLIAN_CUCUMBER_OPTIONS}

#inspect review config
[ -z "$TOPIC" ] && export TOPIC="$TOPIC"
[ -z "$INSPECT_REVIEW_USER_CHANGE_NUMBERS" ] && export INSPECT_REVIEW_USER_CHANGE_NUMBERS="$GERRIT_CHANGE_NUMBERS"
[ -z "$INSPECT_REVIEW_TOPIC_USERS" ] && export INSPECT_REVIEW_TOPIC_USERS="$TOPIC_USERS"
[ -z "$INSPECT_REVIEW_THREAD_POOL_SIZE" ] && export INSPECT_REVIEW_THREAD_POOL_SIZE="10"
[ -z "$INSPECT_REVIEW_ADDITIONAL_BUILD_PROPERTIES" ] && export INSPECT_REVIEW_ADDITIONAL_BUILD_PROPERTIES="-DintegrationPom"
[ -z "$SNAPSHOT_MODEL_RPMS_DIR" ] && export SNAPSHOT_MODEL_RPMS_DIR="$IT_FOLDER/testsuite/src/test/docker/dps_integration/config/init/rpms"
[ -z "$SNAPSHOT_CODE_RPMS_DIR" ] && export SNAPSHOT_CODE_RPMS_DIR="$IT_FOLDER/testsuite/src/test/docker/jboss/config/init/rpms"
[ -z "$INSPECT_REVIEW_SNAPSHOT_MODEL_RPMS_DIR" ] && export INSPECT_REVIEW_SNAPSHOT_MODEL_RPMS_DIR=$SNAPSHOT_MODEL_RPMS_DIR
[ -z "$INSPECT_REVIEW_SNAPSHOT_CODE_RPMS_DIR" ] && export INSPECT_REVIEW_SNAPSHOT_CODE_RPMS_DIR=$SNAPSHOT_CODE_RPMS_DIR
[ -z "$TEST_REPORTS_LOCATION" ] && export TEST_REPORTS_LOCATION="$IT_FOLDER/testsuite/target/failsafe-reports/*.txt"
#export GERRIT_TEST_REPORTS="true"

#multitasks script config
[ -z "$MULTITASKS_EXECUTOR_THREAD_POOL_SIZE" ] && export MULTITASKS_EXECUTOR_THREAD_POOL_SIZE="10"

#download rpms config
[ -z "$DEPLOY_PACKAGE" ] && export DEPLOY_PACKAGE="$deployPackage"

#Gerrit optional config
#export GIT_REPO="OSS/com.ericsson.oss.mediation.pm.testsuite/$PROJECT_NAME"
#export GIT_URL="$GERRIT_MIRROR/$GIT_REPO"


###################################
# main
###################################
trap "endCallback \$?" INT TERM EXIT

#Dump env config
$SCRIPTS_FOLDER/dumpEnvConfig.sh

if [ "$SYS_CLEANUP" == "true" ]; then
    syscleanup
    $SCRIPTS_FOLDER/dumpEnvConfig.sh
fi


#Fetch project from GERRIT_REFSPEC
source $SCRIPTS_FOLDER/fetch-project.sh $PROJECT_DIR

#resource watcher
if [ "$RUN_RESOURCE_WATCHER" == "true" ]; then
  [ -f "$SCRIPTS_FOLDER/watch_resources.sh" ] && $SCRIPTS_FOLDER/watch_resources.sh &
fi


echo "@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@"
#Trigger multitask executer
[ ! -z "$SETUP_TASKS_FILE" -a -f "$SETUP_TASKS_FILE" ] && groovy $SCRIPTS_FOLDER/download_rpms.groovy >> donwload-rpms.log
[ ! -z "$SETUP_TASKS_FILE" -a -f "$SETUP_TASKS_FILE" ] && $SCRIPTS_FOLDER/docker-env-setup.sh $IT_FOLDER >> docker-env-setup.log

[ ! -z "$SETUP_TASKS_FILE" -a -f "$SETUP_TASKS_FILE" ] && groovy $SCRIPTS_FOLDER/multiTasks_executor.groovy $SETUP_TASKS_FILE
#[ ! -z "$SETUP_TASKS_FILE" -a -f "$SETUP_TASKS_FILE" ] && groovy $SCRIPTS_FOLDER/inspect_review.groovy >> inspect-review.log
echo "@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@"

loadEnvFromFile

source $SCRIPTS_FOLDER/fetch-cucumber-trends.sh

#Docker compose monitor
if [ "$RUN_DOCKER_MONITOR" == "true" ]; then
  groovy $SCRIPTS_FOLDER/docker_compose_monitor.groovy $DOCKER_COMPOSE_DIR || echo &
fi
