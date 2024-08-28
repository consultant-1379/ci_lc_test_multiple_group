#!/bin/bash

[ "$2" == "true" ] && SUBMIT="true" || SUBMIT="false"
curl "https://fem105-eiffel004.lmera.ericsson.se:8443/jenkins/job/pm-mediation-docker-testsuite/buildWithParameters?token=acceptance-trigger&TOPIC=$1&GERRIT_VERTICAL_SLICE_SUBMIT=$SUBMIT"
