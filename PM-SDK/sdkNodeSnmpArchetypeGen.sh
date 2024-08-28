#!/bin/bash

# Initialize globals
################## CXP NUMBERS ###################
CXP_NUMBER_NODE_MODEL=CXP1234560
CXP_NUMBER_PM_NODE_MODEL=CXP1234561
CXP_NUMBER_PM_MED_CONF_MODEL=CXP1234562
CXP_NUMBER_SNMP_MED_MODEL=CXP1234563
CXP_NUMBER_MED_ROUTER_POL_MODEL=CXP1234564
CXP_NUMBER_SNMP_SG=CXP1234565
CXP_NUMBER_FM_PM_SG=CXP1234566
CXP_NUMBER_FM_MED_CONF_MODEL=CXP1234567
CXP_NUMBER_FM_TARGET_DEST=CXP1234568
CXP_NUMBER_ADD_FM_MED_SER_TYPE=CXP1234569
CXP_NUMBER_FM_TRANSFORM=CXP1234570
################## END CXP NUMBERS ###################

################## SDK ARCHETYPES VERSION ###################
SDK_ARCHETYPES_VERSION=RELEASE
################## END SDK ARCHETYPES VERSION ###################

################## releaseVersion ###################
releaseVersion=$1-1.0
################## END releaseVersion ###################

################## repoVersions ###################
NODE_MODEL_VERSION=1.0.0
PM_NODE_MODEL_VERSION=1.0.0
PM_MED_CONF_MODEL_VERSION=1.0.0
SNMP_MED_MODEL_VERSION=1.0.0
MED_ROUTER_POL_MODEL_VERSION=1.0.0
SNMP_SG_VERSION=1.0.0
FMPM_SG_VERSION=1.0.0
FM_MED_CONF_MODEL_VERSION=1.0.0
FM_TARGET_DEST_VERSION=1.0.0
ADD_FM_MED_SER_TYPE_VERSION=1.0.0
FM_TRANSFORM_VERSION=1.0.0
################## END repoVersions ###################

################## SERVICE GROUP NAME ###################
SG_NAME=ms3pppm
FM_PM_SG_NAME=ms3ppsnmpfm
################## END SERVICE GROUP NAME ###################

################## POLICY CAPABILITY NAME ###################
CAPABILITY=generic3pp
################## END SERVICE GROUP NAME ###################

################## SERVICE GROUP JBOSS MEMORY MAX ###################
JBOSSMemoryMax=3072
JBOSSMemoryMaxFM_PM=5120
################## END SERVICE GROUP JBOSS MEMORY MAX ###################

################## EMPTY MODELS COMPILE OPTION ###################
#Please Change this to EMPTY_MODELS_OPTION="-Premove-models"
#in case you want to generate empty model RPMs
EMPTY_MODELS_OPTION=""
################## END EMPTY MODELS COMPILE OPTION ###################

################## POLICY SUPPORTED PROTOCOL TYPE ###################
supportedMSProtocolType=PM
################## END POLICY SUPPORTED PROTOCOL TYPE ###################

#################### FUNCTIONS ####################
print() {
   echo "[INFO] $@"
}

printSep() {
   print $separator
   print $@
   print $separator
}

printError() {
   echo "Usage: ./sdkNodeSnmpArchetypeGen.sh [newnodetype] [scenario]"
   echo "Type ./sdkNodeSnmpArchetypeGen.sh -h for details"
}

printScenario() {
   echo "Input Parameters:"
   echo "[newnodetype]        = node type of the customized node"
   echo "[scenario]           = what archetype(s) to run. Possible values:"
   echo "          all        = creates all repos for customization"
   echo "          pm         = creates all repos for customization for an already present node model"
   echo "          newpm      = creates all repos for customization for a new node type with PM already deployed"
   echo "          newversion = creates all repos for customization for a new model version of the node"
   echo "          fmpm       = creates all repos for FM-PM customization"
}

####################    CHECK VAR      ####################
function CheckCxpFormat {
   regexCxp=CXP[0-9][0-9][0-9][0-9][0-9][0-9][0-9]
   if [[ $1 != $regexCxp ]] ; then
      echo ""$2" wrong format"
      exit 1
   fi
}

####################    COMPILE      ####################
function Compile {
   command="mvn clean install -U "$1""
   print "--- compile archetype"
   echo $command
   eval $command
   if [ $? -ne 0 ]; then
      exit 1
   fi
}

####################    SETUP and CLEAN      ####################
function SetupCleanEnv {
   printSep "SETUP and CLEAN"
   echo "--- Clean building directory: "$1""
   rm -rf $1
   mkdir -p $1
   pushd $1
}

####################    NODE MODEL COMMON      ####################
function NodeModelCommonCreate {
   printSep "NODE MODEL COMMON"

   CheckCxpFormat $1 "CXP_NUMBER_NODE_MODEL"

   archetypeArtifactId=node-model-common-archetype
   archetypeGroupId=com.ericsson.oss.mediation.sdk
   artifactId=${prefix}-node-model-common
   packageName=ERIC${prefix}nodemodelcommon

   command="mvn archetype:generate -DarchetypeRepository=https://arm1s11-eiffel004.eiffel.gic.ericsson.se:8443/nexus/content/repositories/releases -DinteractiveMode=false -DarchetypeGroupId=$archetypeGroupId -DarchetypeArtifactId=$archetypeArtifactId -DarchetypeVersion=$version -DartifactId=$artifactId -DcxpNumber=$1 -DpackageName=${packageName} -DnodeType=$nodeType -Dversion=$2"

   print "--- Run node model archetype generate"
   echo $command
   eval $command

   if [ $? -ne 0 ]; then
      echo "NodeModelCommonCreate failed"
      exit 1
   fi

   pushd ${artifactId}

   Compile $3

   popd
}

####################    PM NODE MODEL COMMON      ####################
function PMNodeModelCommonCreate {
   printSep "PM NODE MODEL COMMON"

   CheckCxpFormat $1 "CXP_NUMBER_PM_NODE_MODEL"

   archetypeArtifactId=pm-node-model-common-archetype
   archetypeGroupId=com.ericsson.oss.mediation.pm
   artifactId=${prefix}-pm-node-model-common
   packageName=ERIC${prefix}pmnodemodelcommon
   networkTransformerPhase=process-sources
   snmpNode=true

   command="mvn archetype:generate -DarchetypeRepository=https://arm1s11-eiffel004.eiffel.gic.ericsson.se:8443/nexus/content/repositories/releases -DinteractiveMode=false -DarchetypeGroupId=$archetypeGroupId -DarchetypeArtifactId=$archetypeArtifactId -DreleaseVersion=$releaseVersion -DnetworkTransformerPhase=$networkTransformerPhase -DarchetypeVersion=$version -DartifactId=$artifactId -DcxpNumber=$1 -DpackageName=$packageName -DnodeType=$nodeType -Dversion=$2 -DsnmpNode=$snmpNode"

   print "--- Run pm node model archetype generate"
   echo $command
   eval $command

   if [ $? -ne 0 ]; then
      echo "PMNodeModelCommonCreate failed"
      exit 1
   fi

   mibFileDir=$(pwd)/$artifactId/$artifactId-$releaseVersion-jar/src/main/resources/input/
   configFileDir=$(pwd)/$artifactId/$artifactId-$releaseVersion-jar/src/main/resources/config/

   ans_yn=""
   while [[ "$ans_yn" != done ]] ; do
      read -p "Add/Change MIB file(s) and/or pm_configuration.properties file for counters, if you have to, and then type done to continue
               mib file dir : ["$mibFileDir"]
               pm_configuration.properties dir : [$configFileDir]
               :" ans_yn
   done

   pushd ${artifactId}

   Compile $3

   popd
}

####################  PM SNMP NODE MEDIATION CONFIGURATION    ####################
function PMNodeMediationConfigurationCreate {
   printSep "PM SNMP NODE MEDIATION CONFIGURATION"

   CheckCxpFormat $1 "CXP_NUMBER_PM_MED_CONF_MODEL"

   archetypeArtifactId=pm-mediation-snmp-configuration-archetype
   archetypeGroupId=com.ericsson.oss.mediation.pm
   artifactId=${prefix}-pm-mediation-snmp-configuration
   packageName=ERIC${prefix}pmmediationsnmpconfiguration

   command="mvn archetype:generate -DarchetypeRepository=https://arm1s11-eiffel004.eiffel.gic.ericsson.se:8443/nexus/content/repositories/releases -DinteractiveMode=false -DarchetypeGroupId=$archetypeGroupId -DarchetypeArtifactId=$archetypeArtifactId -DarchetypeVersion=$version -DartifactId=$artifactId -DcxpNumber=$1 -DpackageName=$packageName -DnodeType=$nodeType -Dversion=$2"

   print "--- Run snmp node mediation configuration archetype generate"
   echo $command
   eval $command

   if [ $? -ne 0 ]; then
      echo "PMNodeMediationConfigurationCreate failed"
      exit 1
   fi

   pushd ${artifactId}

   Compile $3

   popd
}

####################  PM SNMP NODE MEDIATION FLOW    ####################
function PMNodeMediationFlowCreate {
   printSep "PM SNMP NODE MEDIATION FLOW"

   CheckCxpFormat $1 "CXP_NUMBER_SNMP_MED_MODEL"

   archetypeArtifactId=pm-mediation-snmp-archetype
   archetypeGroupId=com.ericsson.oss.mediation.pm
   artifactId=${prefix}-pm-mediation-snmp-model
   symbolicLinkBaseDir=generic3ppsnmp
   packageName=ERIC${prefix}pmmediationsnmp

   command="mvn archetype:generate -DarchetypeRepository=https://arm1s11-eiffel004.eiffel.gic.ericsson.se:8443/nexus/content/repositories/releases -DinteractiveMode=false -DarchetypeGroupId=$archetypeGroupId -DarchetypeArtifactId=$archetypeArtifactId -DarchetypeVersion=$version -DartifactId=$artifactId -DcxpNumber=$1 -DpackageName=$packageName -DnodeType=$nodeType -Dversion=$2 -DsgCapability=$CAPABILITY"

   echo $command
   eval $command

   if [ $? -ne 0 ]; then
      echo "PMNodeMediationFlowCreate failed"
      exit 1
   fi

   pushd ${artifactId}

   Compile $3

   popd
}

####################  PM MEDIATION ROUTER POLICY    ####################
function PMMediationRouterPolicyCreate {
   printSep "PM MEDIATION ROUTER POLICY"

   CheckCxpFormat $1 "CXP_NUMBER_MED_ROUTER_POL_MODEL"

   archetypeArtifactId=pm-mediation-router-policy-archetype
   archetypeGroupId=com.ericsson.oss.mediation.pm
   artifactId=generic3pppmmediationrouterpolicy
   rpmRules=ERIC${artifactId}rules_${CXP_NUMBER_MED_ROUTER_POL_MODEL}
   rpmJgroups=ERIC${artifactId}jgroups_${CXP_NUMBER_MED_ROUTER_POL_MODEL}

   command="mvn archetype:generate -DarchetypeRepository=https://arm1s11-eiffel004.eiffel.gic.ericsson.se:8443/nexus/content/repositories/releases -DinteractiveMode=false -DarchetypeGroupId=$archetypeGroupId -DarchetypeArtifactId=$archetypeArtifactId -DarchetypeVersion=$version -DcxpNumber=$1 -Dversion=$2 -DsupportedMSProtocolType=$supportedMSProtocolType -Dcapability=$CAPABILITY"

   echo $command
   eval $command

   if [ $? -ne 0 ]; then
      echo "PMMediationRouterPolicyCreate failed"
      exit 1
   fi

   pushd ${artifactId}

   Compile $3

   popd
}

####################  PM GENERIC 3PP SNMP MEDIATION SERVICE GROUP    ####################
function PMServiceGroupCreate {
   printSep "PM GENERIC 3PP SNMP MEDIATION SERVICE GROUP"

   CheckCxpFormat $1 "CXP_NUMBER_SNMP_SG"

   archetypeArtifactId=servicegroupcontainers-pm-archetype
   archetypeGroupId=com.ericsson.oss.mediation.pm
   # service group name
   deliverable=$3

   command="mvn archetype:generate -DarchetypeRepository=https://arm1s11-eiffel004.eiffel.gic.ericsson.se:8443/nexus/content/repositories/releases -DinteractiveMode=false -DarchetypeGroupId=$archetypeGroupId -DarchetypeArtifactId=$archetypeArtifactId -DarchetypeVersion=$version -DCXP=$1 -DDeliverable=$deliverable -Dversion=$2 -DJBOSSMemoryMax=$JBOSSMemoryMax -DsgCapability=$CAPABILITY"

   echo $command
   eval $command

   if [ $? -ne 0 ]; then
      echo "PMServiceGroupCreate failed"
      exit 1
   fi

   pushd ${deliverable}/ERICenmsg${deliverable}_${CXP_NUMBER_SNMP_SG}

   # command="ex -c \"set autoindent\" -c \"/<\!-- Insert new PM Router Policy for PM-SDK \-\->\" -c \"normal! o<require>$rpmRules</require>\" -c  \"/<\!-- Insert new Policy JGROUPS config for PM-SDK \-\->/\" -c \"normal! o<require>$rpmJgroups</require>\" -c \"wq!\" pom.xml </dev/null"
   command="sed -i -e '/<!-- Insert new PM Router Policy for PM-SDK -->/a\\\t\t\ \ \ \ <require>$rpmRules</require>' -e '/<!-- Insert new Policy JGROUPS config for PM-SDK -->/a\\\t\t\ \ \ \ <require>$rpmJgroups</require>' pom.xml"

   echo $command
   eval $command

   if [ $? -ne 0 ]; then
      echo "RPM files put in service group pom failed"
      exit 1
   fi

   popd
   pushd ${deliverable}

   Compile

   popd
}

####################  FM NODE MEDIATION CONFIGURATION    ####################
function FMNodeMediationConfigurationCreate {
   printSep "FM NODE MEDIATION CONFIGURATION"

   CheckCxpFormat $1 "CXP_NUMBER_FM_MED_CONF_MODEL"

   archetypeArtifactId=node-mediation-configuration-archetype
   archetypeGroupId=com.ericsson.oss.mediation.sdk
   artifactId=${prefix}-mediation-configuration
   packageName=ERIC${prefix}mediationconfiguration
   flowNamespace=GENERIC-3PP-NODE

   command="mvn archetype:generate -DarchetypeRepository=https://arm1s11-eiffel004.eiffel.gic.ericsson.se:8443/nexus/content/repositories/releases -DinteractiveMode=false -DarchetypeGroupId=$archetypeGroupId -DarchetypeArtifactId=$archetypeArtifactId -DarchetypeVersion=$version -DartifactId=$artifactId -DcxpNumber=$1 -DpackageName=$packageName -DnodeType=$nodeType -Dversion=$2 -DflowNamespace=$flowNamespace"

   print "--- Run node mediation configuration archetype generate"
   echo $command
   eval $command

   if [ $? -ne 0 ]; then
      echo "FMNodeMediationConfigurationCreate failed"
      exit 1
   fi

   pushd ${artifactId}

   Compile $3

   popd
}

####################  FM TARGET DESTINATION  ####################
function FMTargetDestinationCreate {
   printSep "FM TARGET DESTINATION"

   CheckCxpFormat $1 "CXP_NUMBER_FM_TARGET_DEST"

   archetypeArtifactId=fm-target-destination-archetype
   archetypeGroupId=com.ericsson.oss.mediation.sdk
   artifactId=${prefix}-fm-target-destination
   nmSpace=GENERIC-3PP-NODE
   rpmFMTargetDest=ERIC${prefix}fmtargetdesthandler_${CXP_NUMBER_FM_TARGET_DEST}

   command="mvn archetype:generate -DarchetypeRepository=https://arm1s11-eiffel004.eiffel.gic.ericsson.se:8443/nexus/content/repositories/releases -DinteractiveMode=false -DarchetypeGroupId=$archetypeGroupId -DbasePackage=$artifactId -DarchetypeArtifactId=$archetypeArtifactId -DneTypeLow=${prefix} -Dtargetdestflow=${prefix}fmtargetdestflow -Dtargetdesthandler=${prefix}fmtargetdesthandler -DarchetypeVersion=$version -DartifactId=$artifactId -DcxpNumberFlow=$CXP_NUMBER_FM_TARGET_DEST -DcxpNumberHandler=$CXP_NUMBER_FM_TARGET_DEST -Dversion=$2 -DnmSpace=$nmSpace -DneTypeLow=${prefix} -DnodeType=${prefix} -DsgCapability=$CAPABILITY"

   print "--- Run fm target destination archetype generate"
   echo $command
   eval $command

   if [ $? -ne 0 ]; then
      echo "FMTargetDestinationCreate failed"
      exit 1
   fi

   pushd ${artifactId}

   Compile $3

   popd
}

####################  ADDITIONAL FM MEDIATION SERVICE TYPE ####################
function ADDFMMedServTypeCreate {
   printSep "ADDITIONAL FM MEDIATION SERVICE TYPE"

   CheckCxpFormat $1 "CXP_NUMBER_ADD_FM_MED_SER_TYPE"

   archetypeArtifactId=additionalfmmediationservicetype-archetype
   archetypeGroupId=com.ericsson.oss.mediation.sdk
   artifactId=${prefix}-additional-fm-mediation-service-type
   packageName=${prefix}additionalfmmediationservicetype

   command="mvn archetype:generate -DarchetypeRepository=https://arm1s11-eiffel004.eiffel.gic.ericsson.se:8443/nexus/content/repositories/releases -DinteractiveMode=false -DarchetypeGroupId=$archetypeGroupId -DarchetypeArtifactId=$archetypeArtifactId -DarchetypeVersion=$version -DartifactId=$artifactId -DCXP=$1 -DpackageName=$packageName -Dversion=$2 -DSGList=FM_PM_SG_NAME"

   print "--- Run additional fm mediation service type archetype generate"
   echo $command
   eval $command

   if [ $? -ne 0 ]; then
      echo "ADDFMMedServTypeCreate failed"
      exit 1
   fi

   pushd ${artifactId}

   Compile $3

   popd
}

####################  FM TRANSFORMATION  ####################
function FMTransformationCreate {
   printSep "FM TRANSFORMATION"

   CheckCxpFormat $1 "CXP_NUMBER_FM_TRANSFORM"

   archetypeArtifactId=fm-transformation-archetype
   archetypeGroupId=com.ericsson.oss.mediation.sdk
   artifactId=${prefix}-fm-transformation
   snmpTrapPort=162
   heartbeatMode=PULL
   rpmFMTransformation=ERIC${prefix}fmtransformation_${CXP_NUMBER_FM_TRANSFORM}

   command="mvn archetype:generate -DarchetypeRepository=https://arm1s11-eiffel004.eiffel.gic.ericsson.se:8443/nexus/content/repositories/releases -DinteractiveMode=false -DarchetypeGroupId=$archetypeGroupId -DarchetypeArtifactId=$archetypeArtifactId -DarchetypeVersion=$version -DartifactId=$artifactId -DcxpNumber=$1 -Dversion=$2 -DsnmpTrapPort=$snmpTrapPort -DHeartbeatMode=$heartbeatMode -DNEVersion=$releaseVersion"

   print "--- Run fm mediation tranformation archetype generate"
   echo $command
   eval $command

   if [ $? -ne 0 ]; then
      echo "FMTransformationCreate failed"
      exit 1
   fi

   pushd ${artifactId}

   Compile $3

   popd
}

####################  FM-PM GENERIC 3PP SNMP MEDIATION SERVICE GROUP  ####################
function FMPMServiceGroupCreate {
   printSep "FM-PM GENERIC 3PP SNMP MEDIATION SERVICE GROUP"

   CheckCxpFormat $1 "CXP_NUMBER_FM_PM_SG"

   archetypeArtifactId=servicegroupcontainers-fm-pm-archetype
   archetypeGroupId=com.ericsson.oss.mediation.sdk
   # service group name
   deliverable=$3

   command="mvn archetype:generate -DarchetypeRepository=https://arm1s11-eiffel004.eiffel.gic.ericsson.se:8443/nexus/content/repositories/releases -DinteractiveMode=false -DarchetypeGroupId=$archetypeGroupId -DarchetypeArtifactId=$archetypeArtifactId -DarchetypeVersion=$version -DCXP=$1 -DDeliverable=$deliverable -Dversion=$2 -DJBOSSMemoryMax=$JBOSSMemoryMaxFM_PM -DsgCapability=$CAPABILITY"

   echo $command
   eval $command

   if [ $? -ne 0 ]; then
      echo "FMPMServiceGroupCreate failed"
      exit 1
   fi

   pushd ${deliverable}/ERICenmsg${deliverable}_${CXP_NUMBER_FM_PM_SG}

   # command="ex -c \"set autoindent\" -c \"/<\!-- Insert new PM Router Policy for PM-SDK \-\->\" -c \"normal! o<require>$rpmRules</require>\" -c  \"/<\!-- Insert new Policy JGROUPS config for PM-SDK \-\->\" -c \"normal! o<require>$rpmJgroups</require>\" -c \"/<\!-- Start custom packages for BUGS (handlers and transformations) \-\->/\" -c \"normal! o<require>$rpmFMTransformation</require>\" -c \"/<\!-- Start custom packages for BUGS (handlers and transformations) \-\->/\" -c \"normal! o<require>$rpmFMTargetDest</require>\" -c \"wq!\" pom.xml </dev/null"
   command="sed -i -e '/<!-- Insert new PM Router Policy for PM-SDK -->/a\\\t\t\ \ \ \ <require>$rpmRules</require>' -e '/<!-- Insert new Policy JGROUPS config for PM-SDK -->/a\\\t\t\ \ \ \ <require>$rpmJgroups</require>' -e '/<!-- Start custom packages for BUGS (handlers and transformations) -->/a\\\t\t\ \ \ \ <require>$rpmFMTransformation</require>' -e '/<!-- Start custom packages for BUGS (handlers and transformations) -->/a\\\t\t\ \ \ \ <require>$rpmFMTargetDest</require>' pom.xml"

   echo $command
   eval $command

   if [ $? -ne 0 ]; then
      echo "RPM files put in service group pom failed"
      exit 1
   fi

   popd
   pushd ${deliverable}

   Compile

   popd
}
################## END FUNCTIONS ##################

################## BEGIN SCRIPT ###################
#---------------------------------------------------------
# MAIN
#---------------------------------------------------------

if [ "$#" -eq 0 ]; then
   printError
   exit 1
elif [ "$#" -eq 1 ]; then
   if [ "$1" == "-h" ]; then
      printScenario
      exit 1
   else
      nodeType=$1
      scenarioArchetype=all
   fi
elif [ "$#" -eq 2 ]; then
   nodeType=$1
   scenarioArchetype=$2
   if [ "$2" == "fmpm" ]; then
      supportedMSProtocolType=FM
   fi
else
   printError
   exit 1
fi


################## VARIABLES ###################
localDir=$(pwd)
archetypeDir=${localDir}/archetypes
version=${SDK_ARCHETYPES_VERSION}
initPrefix=$(echo "${nodeType,,}")
prefix=${initPrefix//[^[:alnum:]]/}
separator="========================================================================"
################## END VARIABLES ###################

SetupCleanEnv $archetypeDir

case "$scenarioArchetype" in
   "all")
      NodeModelCommonCreate $CXP_NUMBER_NODE_MODEL $NODE_MODEL_VERSION $EMPTY_MODELS_OPTION
      ;&
   "pm")
      PMNodeModelCommonCreate $CXP_NUMBER_PM_NODE_MODEL $PM_NODE_MODEL_VERSION $EMPTY_MODELS_OPTION
      PMNodeMediationConfigurationCreate $CXP_NUMBER_PM_MED_CONF_MODEL $PM_MED_CONF_MODEL_VERSION $EMPTY_MODELS_OPTION
      PMNodeMediationFlowCreate $CXP_NUMBER_SNMP_MED_MODEL $SNMP_MED_MODEL_VERSION $EMPTY_MODELS_OPTION
      PMMediationRouterPolicyCreate $CXP_NUMBER_MED_ROUTER_POL_MODEL $MED_ROUTER_POL_MODEL_VERSION $EMPTY_MODELS_OPTION
      PMServiceGroupCreate $CXP_NUMBER_SNMP_SG $SNMP_SG_VERSION $SG_NAME
      ;;
   "newpm")
      NodeModelCommonCreate $CXP_NUMBER_NODE_MODEL $NODE_MODEL_VERSION $EMPTY_MODELS_OPTION
      PMNodeModelCommonCreate $CXP_NUMBER_PM_NODE_MODEL $PM_NODE_MODEL_VERSION $EMPTY_MODELS_OPTION
      PMNodeMediationConfigurationCreate $CXP_NUMBER_PM_MED_CONF_MODEL $PM_MED_CONF_MODEL_VERSION $EMPTY_MODELS_OPTION
      PMNodeMediationFlowCreate $CXP_NUMBER_SNMP_MED_MODEL $SNMP_MED_MODEL_VERSION $EMPTY_MODELS_OPTION
      ;;
   "newversion")
      PMNodeModelCommonCreate $CXP_NUMBER_PM_NODE_MODEL $PM_NODE_MODEL_VERSION $EMPTY_MODELS_OPTION
      ;;
   "fmpm")
      NodeModelCommonCreate $CXP_NUMBER_NODE_MODEL $NODE_MODEL_VERSION $EMPTY_MODELS_OPTION
      PMNodeModelCommonCreate $CXP_NUMBER_PM_NODE_MODEL $PM_NODE_MODEL_VERSION $EMPTY_MODELS_OPTION
      PMNodeMediationConfigurationCreate $CXP_NUMBER_PM_MED_CONF_MODEL $PM_MED_CONF_MODEL_VERSION $EMPTY_MODELS_OPTION
      PMNodeMediationFlowCreate $CXP_NUMBER_SNMP_MED_MODEL $SNMP_MED_MODEL_VERSION $EMPTY_MODELS_OPTION
      PMMediationRouterPolicyCreate $CXP_NUMBER_MED_ROUTER_POL_MODEL $MED_ROUTER_POL_MODEL_VERSION $EMPTY_MODELS_OPTION
      ADDFMMedServTypeCreate $CXP_NUMBER_ADD_FM_MED_SER_TYPE $ADD_FM_MED_SER_TYPE_VERSION $EMPTY_MODELS_OPTION
      FMTransformationCreate $CXP_NUMBER_FM_TRANSFORM $FM_TRANSFORM_VERSION $EMPTY_MODELS_OPTION
      FMNodeMediationConfigurationCreate $CXP_NUMBER_FM_MED_CONF_MODEL $FM_MED_CONF_MODEL_VERSION $EMPTY_MODELS_OPTION
      FMTargetDestinationCreate $CXP_NUMBER_FM_TARGET_DEST $FM_TARGET_DEST_VERSION $EMPTY_MODELS_OPTION
      FMPMServiceGroupCreate $CXP_NUMBER_FM_PM_SG $FMPM_SG_VERSION $FM_PM_SG_NAME
      ;;
   *)
      echo "Invalid Choice "$scenarioArchetype""
      exit 1
      ;;
esac

command="find . -name \"*.rpm\" -exec cp {} . \;"
eval $command

echo -e "RPMs ready."
################## END SCRIPT ###################