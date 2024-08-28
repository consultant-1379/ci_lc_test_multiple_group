#! /bin/bash
#
# ebialan

DEFAULT_DOCKER_COMPOSE_CMD="docker-compose"
[ -z "$DOCKER_COMPOSE_CMD" ] && DOCKER_COMPOSE_CMD="$DEFAULT_DOCKER_COMPOSE_CMD"

export DOCKER_COMPOSE_CMD
export COMPOSE_HTTP_TIMEOUT=500

pushd $1
$DOCKER_COMPOSE_CMD down -v
$DOCKER_COMPOSE_CMD kill 
docker ps -qa | xargs docker rm -fv >& /dev/null

$DOCKER_COMPOSE_CMD pull
$DOCKER_COMPOSE_CMD build --pull
$DOCKER_COMPOSE_CMD up -d
$DOCKER_COMPOSE_CMD kill

find . -regex ".*downloads/.*.rpm" -exec  rm -rf {} \;
popd

