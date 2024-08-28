#! /bin/bash
#
# zkalvai
#
#

source docker-env-functions.sh
#docker_print "Installing additional packages"
yum install -y iproute 2>/dev/null

mkdir -p /ericsson/tor/data

cp -Rf ${DOCKER_INIT_DIR}/data/global.properties /ericsson/tor/data/.

cp -Rf ${DOCKER_INIT_DIR}/updates/* /

docker_print "Creating model deployment directories"
mkdir -p /var/opt/ericsson/ERICmodeldeployment/data

docker_print "Installing RPMs"
install_rpms_from_nexus
install_rpms_from_iso


