#!/bin/bash
#####################################################################
# File Name    : create_docker_deployment_topology.sh
# Version      : 1.00
# Author       : Fatih ONUR | Navdeep Kalia
# Description  : Creates the docker deployment topology for rpm files
# Date Created : 2017.10.04
#####################################################################
set -o errexit # exit when a command fails.
set -o nounset # exit when this script tries to use undeclared variables
set -o pipefail # to catch pipe errors

#Global Variables
HOST=$(hostname)
TOPO_DIR="docker_deployment_topology"
TOPO_FILE="docker_deployment_topology.txt"

function createGlobalVariables {
  echo "INFO: Start Creating Global Variables"

  proj_dir=$1
  docker_compose_dirs="$(find $proj_dir -name 'docker-compose*.yml' -exec dirname {} \;)"

  echo "INFO: End Creating Global Variables"

}

# Delete existing containers for a specified docker-compose file
#
function cleanContainers {

  echo "INFO: (${FUNCNAME[0]}): Start removing containers from the vapp:$HOST"

  echo "INFO: Moving into docker compose directory as shown below:"
  for docker_compose_dir in $docker_compose_dirs
    do
      pushd $docker_compose_dir

      docker-compose down -v > /dev/null 2>&1 || echo "INFO: No containers running on vapp:$HOST"

      echo "INFO: Moving back to project directory as shown below:"
      popd

    done

  echo "INFO: (${FUNCNAME[0]}): End removing containers from the vapp:$HOST"
}


# Create containers through the compose file
#
function createContainers {

  echo "INFO: Start creating containers on vapp:$HOST, time:$(date +"%H:%M:%S")"

  for docker_compose_dir in $docker_compose_dirs
    do
      echo "INFO: Moving into docker compose directory as shown below"
      pushd $docker_compose_dir

      if docker-compose create ; then
        echo "INFO: Successfully containers are created on vapp:$HOST"
      else
        echo "ERROR: Containers are not created on vapp:$HOST"
      fi

      echo "INFO: Moving back to current working directory as shown below"
      popd

    done

  echo "INFO: End creating containers on vapp:$HOST, time:$(date +"%H:%M:%S")"
}


# Get all the created containers name for a specified docker-compose file
#
function getContainerNames {

  echo "INFO: (${FUNCNAME[0]}): Start getting name of created containers from the vapp:$HOST"

  for docker_compose_dir in $docker_compose_dirs
    do
      echo "INFO: Moving into docker compose directory as shown below"
      pushd $docker_compose_dir

      local containerNames=$(docker-compose ps | tail -n +3 | awk '{print $1}')
      if [ $? -eq 0 ]; then
        echo "INFO: Successfully captured conatainers name from the vapp:$HOST"
      else
        echo "INFO: Containers name not captured successfully from the vapp:$HOST"
      fi

      echo "INFO: Moving back to current working directory as shown below"
      popd

    done

    echo "INFO: (${FUNCNAME[0]}): End of getting name of created containers from the vapp:$HOST"

    echo $containerNames
}


# Get list of All the RPMs in base containers
#
function getBaseContainerRpmFiles() {

  echo "INFO: (${FUNCNAME[0]}): Start collecting list of names of RPMs from created containers from the vapp:$HOST, time:$(date +"%H:%M:%S")"

  echo "INFO: Removing $TOPO_DIR directory if any available"
  rm -rf $TOPO_DIR
  echo "INFO: Creating docker topology folder: ${PWD}/topology"
  mkdir $TOPO_DIR

  echo "INFO: Copying all the file names into single file from all containers"
  for containerName in `getContainerNames $proj_dir | tail -1`
  do
    echo "INFO: container: $containerName"
    rm -rf $containerName
    if $(docker cp $containerName:/backup/ ./$containerName); then
      echo "INFO: Files successfully copied from container $containerName:/backup to ${PWD}/$containerName folder on vapp:$HOST"
      find ./$containerName -name '*.txt' | egrep 'rpms/iso|rpms/nexus' | xargs cat | tee -a ${TOPO_DIR}/${containerName}.topo &&
      echo "INFO: Rpm files are captured from container:$containerName"
      # remove container folders
      rm -rf ${PWD}/$containerName
    else
      echo "ERROR: Failure to copy files from container $containerName:/basckup to ${PWD}/$containerName folder on vapp:$HOST"
    fi
  done

  echo "INFO: (${FUNCNAME[0]}): End of collectiong list of names of RPMs from created containers from the vapp:$HOST, time:$(date +"%H:%M:%S")"
}


# Get list of All the RPMs in Customer Containers
#
function getCustomContainerRpmFiles {

  echo "INFO: (${FUNCNAME[0]}): Start collecting list of names of RPMs from custom containers from the vapp:$HOST"

  for containerName in `getContainerNames $proj_dir | tail -1`
  do
    echo "INFO: Start inspecting container: $containerName"
    rpmPath=$(docker inspect $containerName | { grep :/opt/ericsson/docker/config || true; } | awk -F: '{print $1}' | cut -d '"' -f 2 );
    if [ ! -z "${rpmPath}" ]; then
      echo "INFO: Matching volume found for /opt/ericsson/docker/config. Checking host for custom rpm files"
      echo "DEBUG: rpmPath=$rpmPath"
      for rpmFile in `find $rpmPath -name '*.txt'`
      do
        echo "DEBUG: rpmFile=$rpmFile"
        local rpmFileContent=$(cat $rpmFile)
        if  [ ! -z "${rpmFileContent}" ]; then
          cat $rpmFile >> ./${TOPO_DIR}/${containerName}.topo
        fi
      done
    else
      echo "INFO: No matching volume found for /opt/ericsson/docker/config. Checking inside container $containerName:/opt/ericsson/docker/config path for customrrpm files"
      echo "${PWD}"
      echo "$containerName"
      rm -rf $containerName
      if $(docker cp $containerName:/opt/ericsson/docker/config/ ./$containerName); then
        echo "INFO: Files successfully copied from container $containerName:/opt/ericsson/docker/config to ${PWD}/$containerName folder on vapp:$HOST"
        filecount=$(ls -R $containerName | grep ".txt$" | wc -l) &&
        if [ "${filecount}" -gt 0 ]; then
          find ./$containerName -name '*.txt' | egrep 'rpms/iso|rpms/nexus' | xargs cat | tee -a ${TOPO_DIR}/${containerName}.topo &&
          echo "INFO: Rpm files are captured from container:$containerName"
        fi
        echo "INFO: NO RPM files to be captured from container:$containerName"
        rm -rf ${PWD}/$containerName
      else
        echo "ERROR: Failure to copy files from container $containerName:/opt/ericsson/docker/config to ${PWD}/$containerName folder on vapp:$HOST"
      fi
    fi
    # following code make sures that end of file has proper carriage return
    echo "" >> ./${TOPO_DIR}/${containerName}.topo
    sed -i '/^$/d' ./${TOPO_DIR}/${containerName}.topo
    echo "INFO: End inspecting container: $containerName"
  done
  echo "INFO: (${FUNCNAME[0]}): End collectiong list of names of RPMs from custom containers from the vapp:$HOST"
}


# Creates the network topology file
#
function createDockerDeploymentTopologyFile {
  echo "INFO: (${FUNCNAME[0]}): Start creating a single docker deployment topology file on the vapp:$HOST"

  echo "INFO: Removing existing docker network deployment file"
  rm -rf ${TOPO_FILE}

  for ctopoFile in `find ${TOPO_DIR} -type f -size +1c -name *.topo`
  do
      echo "--------------------$ctopoFile---------------"
      local containerName=$(echo $ctopoFile | cut -d/ -f 2 | cut -d. -f1)
      echo "INFO: Start Reading topology file: $ctopoFile for container:$containerName"
      echo "@@start containerName; $containerName" >> ${TOPO_FILE}
      perl -ne 'print if ! $x{$_}++'  $ctopoFile >> ${TOPO_FILE}
      echo "@@end containerName; $containerName" >> ${TOPO_FILE}
      echo "INFO: End reading topology file: $ctopoFile"
  done

  echo "INFO: Removing existing docker deployment topology folder"
  rm -rf ${TOPO_DIR}
  sed -i '/^#/ d' ${TOPO_FILE} &&
  sed -i -e 's/@@/##/g' ${TOPO_FILE}
  cut -d: -f2 < ${TOPO_FILE} > ${TOPO_FILE}.tmp
  /bin/mv -f ${TOPO_FILE}.tmp ${TOPO_FILE}
  echo "INFO: (${FUNCNAME[0]}): End creating a single docker deployment topology file on the vapp:$HOST"
}


## Testing dirs
#docker_compose_dir=/home/lciadm100/tools/projected/ecm-mediation
#docker_compose_dir=/home/lciadm100/tools/enm-docker/enm-docker-sample
#proj_dir=/home/lciadm100/tools/enm-docker/enm-docker-sample
#PROJ_DIR=/home/lciadm100/tools/enm-docker/enm-docker-sample
###getContainerNames $PROJ_DIR

if [ $# -eq 0 ]; then
  echo "ERROR: No arguments provided. Please provide project directory argument value like: /home/lciadm100/tools/enm-docker/enm-docker-sample "
  exit 1
elif [ "$1" = "-h" ]; then
  echo "HELP: Please provide project directory argument value like: /home/lciadm100/tools/enm-docker/enm-docker-sample "
  exit 0
else
  #ARG
  PROJ_DIR=$1
  createGlobalVariables $PROJ_DIR
  cleanContainers
  createContainers
  getBaseContainerRpmFiles
  getCustomContainerRpmFiles
  createDockerDeploymentTopologyFile
fi;

