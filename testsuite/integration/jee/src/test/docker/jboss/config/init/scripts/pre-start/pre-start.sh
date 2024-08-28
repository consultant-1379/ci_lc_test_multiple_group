#! /bin/bash

source docker-env-functions.sh

docker_print "UI_TEST=$UI_TEST"

docker_print "Running environment: $RUNNING_ENV"


#Increase resources on Jenkins to be faster
if [ "$RUNNING_ENV" = "JENKINS" ]; then
  cat $DOCKER_DEFAULT_JVM_PROPERTIES_SCRIPT | grep -v -e ms512m -e mx2g -e PermSize > jvm.tmp
  mv jvm.tmp $DOCKER_DEFAULT_JVM_PROPERTIES_SCRIPT
fi

if [ "$UI_TEST" = "true" ];then
   echo "Preparing for UI test..."
   cp $DOCKER_RPMS_DIR/iso/additional_rpms_for_ui_test.ui $DOCKER_RPMS_DIR/iso/additional_rpms_for_ui_test.txt
   cp $DOCKER_JBOSS_SCRIPTS_DIR/config_modcluster.ui $DOCKER_JBOSS_SCRIPTS_DIR/config_modcluster.cli
   
   #Install additional RPMs and configure jboss
   startup.sh -NSJ

   #cleanup
   rm -rf $DOCKER_RPMS_DIR/iso/additional_rpms_for_ui_test.txt
   rm -rf $DOCKER_JBOSS_SCRIPTS_DIR/config_modcluster.cli
fi

install_rpms_from_nexus
install_rpms_from_iso
cleanup_deployment
copy_jboss_config

cp /opt/ericsson/ERICdpsneo4j_CXP9032728/dps-neo4j-ear-*.staging $JBOSS_HOME/standalone/deployments/dps-neo4j-ear.ear
cp /opt/ericsson/ERICneo4jjca_CXP9032726/neo4j-jca-rar*.staging $JBOSS_HOME/standalone/deployments/neo4j-jca-rar.rar

wait_postgres
docker_print "Waiting for Model Deployment Service"
wait_model_deployment
docker_print "Waiting for NEO4J"
wait_neo4j
docker_print "Deploying NEO4J"
 #deploy_neo4j
JBOSS_DEPLOYMENTS_DIR="${JBOSS_HOME}/standalone/deployments"
# Find neo4j ear in installation directory and copy to deployments folder
ls /opt/ericsson/ERICdpsneo4j_CXP9032728
ls /opt/ericsson/ERICneo4jjca_CXP9032726
NEO_EAR_STAGING_FILE=$(find '/opt/ericsson/ERICdpsneo4j_CXP9032728' -type f -regex ".*dps-neo4j-ear-.*\.ear")
NEO_EAR_FILE=$(basename $(echo ${NEO_EAR_STAGING_FILE} | sed 's/staging/ear/g'))
docker_print "Deploying ${NEO_EAR_FILE}"
cp -f "${NEO_EAR_STAGING_FILE}" "${JBOSS_DEPLOYMENTS_DIR}/${NEO_EAR_FILE}"
# Find neo4j jca rar in installation directory and copy to deployments folder
NEO_RAR_STAGING_FILE=$(find '/opt/ericsson/ERICneo4jjca_CXP9032726' -type f -regex ".*neo4j-jca-rar-.*\.rar")
NEO_RAR_FILE=$(basename $(echo ${NEO_RAR_STAGING_FILE} | sed 's/staging/rar/g'))
docker_print "Deploying ${NEO_RAR_FILE}"
cp -f "${NEO_RAR_STAGING_FILE}" "${JBOSS_DEPLOYMENTS_DIR}/${NEO_RAR_FILE}"

