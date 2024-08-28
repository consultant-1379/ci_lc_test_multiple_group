#! /bin/bash
set -e

#JOB CONFIGURATION
export PROJECT_NAME="pm-service"
export PROJECT_DIR="$WORKSPACE/$PROJECT_NAME"
export START_DATE=$(date)
export IT_FOLDER="$PROJECT_DIR/testsuite/integration/jee/"
export IT_REL_PATH="$IT_FOLDER"
export CUCUMBER_IT_FOLDER="$PROJECT_DIR/vertical-slice/vertical-slice-testsuite"
export DOCKER_COMPOSE_DIR="$IT_FOLDER"
export CUCUMBER_TRENDS_FILE="$PROJECT_NAME/vertical-slice/vertical-slice-testsuite/cucumber-trends.json"
export SCRIPTS_FOLDER="$WORKSPACE/ci-job-scripts"
export COMPOSE_PROJECT_NAME=$(echo $PROJECT_NAME | sed s/-//g)
export JBOSS_DOCKER="${COMPOSE_PROJECT_NAME}_pm_service_jboss_1"

export RESOLVED_ADDITIONAL_SYSTSEM_PROPERTIES=$ADDITIONAL_SYSTSEM_PROPERTIES
export RESOLVED_CUCUMBER_OPTIONS=${ARQUILLIAN_CUCUMBER_OPTIONS}
export NEO4J_SERVER_HOSTNAME="neo4j1"
export NEO4j_SERVERTRANSPORT="bolt"

#inspect review config
export INSPECT_REVIEW_IGNORE_TEST_REPO_RPMS="true"
export INSPECT_REVIEW_IGNORE_RPMS=""
export INSPECT_REVIEW_ADDITIONAL_BUILD_PROPERTIES="-Darquillian.container=docker -DskipTests"
#export INSPECT_REVIEW_BUILD_PROPERTIES="-U -B clean install -DintegrationPom -DskipTests -Dmaven.test.skip=true -Dcheckstyle.skip=true -Dcobertura.skip=true -Dmaven.artifact.threads=9"
export SNAPSHOT_MODEL_RPMS_DIR="$IT_FOLDER/src/test/docker/models/config/init/rpms"
export SNAPSHOT_CODE_RPMS_DIR="$IT_FOLDER/src/test/docker/jboss/config/init/rpms"
export INSPECT_REVIEW_SNAPSHOT_MODEL_RPMS_DIR=$SNAPSHOT_MODEL_RPMS_DIR
export INSPECT_REVIEW_SNAPSHOT_CODE_RPMS_DIR=$SNAPSHOT_CODE_RPMS_DIR
export TEST_REPORTS_LOCATION="$IT_FOLDER/target/surefire-reports/*.xml"

echo "#!/bin/bash" > $WORKSPACE/endCallback.sh
echo "source $SCRIPTS_FOLDER/acceptance_job_review.sh $CUCUMBER_IT_FOLDER/target/failsafe-reports/*.txt" >> $WORKSPACE/endCallback.sh
chmod 755 $WORKSPACE/endCallback.sh
export END_CALLBACK_SCRIPT="$WORKSPACE/endCallback.sh"
#export GERRIT_TEST_REPORTS="true"

#multitasks script config
export MULTITASKS_EXECUTOR_THREAD_POOL_SIZE="10"

#download rpms config
export DEPLOY_PACKAGE="$deployPackage"

#Gerrit optional config
#export GIT_REPO="OSS/com.ericsson.oss.pmic/pm-service"
#export GIT_URL="$GERRIT_MIRROR/$GIT_REPO"

env | grep "=" > env.txt

export RUN_DOCKER_MONITOR="true"
export RUN_RESOURCE_WATCHER="false"
export SKIP_DOCKER_TEARDOWN="false"

export SETUP_TASKS_FILE="$WORKSPACE/tasksConfig.txt"

#Download CI Scritps
export CI_SCRIPTS_VERSION="1.0.741"
export CI_JOB_SCRIPT_GROUP_ID="blast"
export CI_JOB_SCRIPT_ARTIFACT_ID="ci-job-scripts"
export CI_JOB_SCRIPT_VERSION="$CI_SCRIPTS_VERSION"
export CI_JOB_SCRIPT_ZIP="$CI_JOB_SCRIPT_ARTIFACT_ID-$CI_JOB_SCRIPT_VERSION.zip"
export NEXUS_DATASTORE="https://arm1s11-eiffel004.eiffel.gic.ericsson.se:8443/nexus/service/local/repositories/releases/content"
export CI_JOB_SCRIPT_LINK="$NEXUS_DATASTORE/$CI_JOB_SCRIPT_GROUP_ID/$CI_JOB_SCRIPT_ARTIFACT_ID/$CI_JOB_SCRIPT_VERSION/$CI_JOB_SCRIPT_ZIP"
# curl -f -o ci-job-scripts.zip $CI_JOB_SCRIPT_LINK
# unzip ci-job-scripts.zip


#Config parallel tasks
# echo "donwload-rpms;$WORKSPACE; groovy $SCRIPTS_FOLDER/download_rpms.groovy" >> $SETUP_TASKS_FILE
# echo "docker-env-setup;$IT_FOLDER; $SCRIPTS_FOLDER/docker-env-setup.sh $IT_FOLDER" >> $SETUP_TASKS_FILE
echo "inspect-review;$WORKSPACE; groovy $SCRIPTS_FOLDER/inspect_review.groovy" >> $SETUP_TASKS_FILE

source $SCRIPTS_FOLDER/integration-shared-steps.sh


cd $IT_FOLDER

docker-compose up --no-recreate -d
docker-compose logs -f pm_service_jboss >& ${WORKSPACE}/pm_service_jboss.log &
docker-compose logs -f pm_service_db >& ${WORKSPACE}/pm_service_db.log &
docker-compose logs -f pm_service_model-deployment >& ${WORKSPACE}/pm_service_model-deployment.log &
docker-compose logs -f pm_service_postgres >& ${WORKSPACE}/pm_service_postgres.log &
docker-compose logs -f pm_service_opendj >& ${WORKSPACE}/pm_service_opendj.log &

$SCRIPTS_FOLDER/wait-jboss.sh ${JBOSS_DOCKER}