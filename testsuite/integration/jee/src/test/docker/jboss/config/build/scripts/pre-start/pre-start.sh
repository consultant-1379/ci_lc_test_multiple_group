#! /bin/bash

source docker-env-functions.sh

install_rpms_from_nexus
install_rpms_from_iso
cleanup_deployment
copy_jboss_config
