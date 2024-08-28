#! /bin/bash

#Download CI Scritps
export SCRIPTS_FOLDER="$WORKSPACE/ci-job-scripts"
export CI_JOB_SCRIPT_GROUP_ID="blast"
export CI_JOB_SCRIPT_ARTIFACT_ID="ci-job-scripts"
export CI_JOB_SCRIPT_VERSION="$CI_SCRIPTS_VERSION"
export CI_JOB_SCRIPT_ZIP="$CI_JOB_SCRIPT_ARTIFACT_ID-$CI_JOB_SCRIPT_VERSION.zip"
export NEXUS_DATASTORE="https://arm1s11-eiffel004.eiffel.gic.ericsson.se:8443/nexus/service/local/repositories/releases/content"
export CI_JOB_SCRIPT_LINK="$NEXUS_DATASTORE/$CI_JOB_SCRIPT_GROUP_ID/$CI_JOB_SCRIPT_ARTIFACT_ID/$CI_JOB_SCRIPT_VERSION/$CI_JOB_SCRIPT_ZIP"
curl -o ci-job-scripts.zip $CI_JOB_SCRIPT_LINK
unzip ci-job-scripts.zip

