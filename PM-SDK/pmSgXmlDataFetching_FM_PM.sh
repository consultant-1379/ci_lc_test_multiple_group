#!/bin/bash

# Path of MASTER_siteEngineering file
MASTER_FILE="/software/autoDeploy/MASTER_siteEngineering.txt"
# Path of LITP xml files
INPUT_FILE="*.xml"

declare -A xmlMap
################## Manual entry data #########################
xmlMap["{VM_ID}"]="ms3ppsnmpfm"
xmlMap["{SVC-ID-1}"]=1
xmlMap["{SVC-ID-2}"]=2
xmlMap["{CPUS}"]=4
xmlMap["{RAM}"]="8192M"
xmlMap["{VM_IMAGE}"]="jboss-image"
xmlMap["{INTERNAL_STATUS_CHECK}"]="on"
### CUSTOM_REPO = the customization YUM repository containing the RPMs with the software to be installed in the VM
xmlMap["{CUSTOM_REPO}"]="CUSTOM/NEWSNMPNODEpmcustomization"
xmlMap["{CUSTOM_PACKAGE}"]="ERICenmsgms3ppsnmpfm_CXP1234566"
xmlMap["{NODE_LIST}"]="svc-1,svc-2"
xmlMap["{ACTIVE_VM}"]=2


# Available IP address retrieved from litp command on the server/vApp

function getIpAddressForSg {

	internalFreeIPv4Addresses=($(for a in `litp show -p /software/services/ -l`; do litp show -p $a/vm_network_interfaces/internal -o ipaddresses ; done 2>/dev/null | sed -e "s/,/\\n/g" | sort -t "." -n -k 1,1 -k 2,2 -k 3,3 -k 4,4 | tail -n 1 |  awk -F "." '{ for (i=$4+1;i<$4+5;++i) if (i<255) print $1"."$2"."$3"."i ; else print $1"."$2"."$3+1"."i-254;}'))
	echo ${internalFreeIPv4Addresses[0]}
	echo ${internalFreeIPv4Addresses[1]}
	xmlMap["{IPv4_INTERNAL_1}"]=${internalFreeIPv4Addresses[0]}
	xmlMap["{IPv4_INTERNAL_2}"]=${internalFreeIPv4Addresses[1]}
	xmlMap["<!-- first internal VLAN IPv4 Address -->"]=${internalFreeIPv4Addresses[0]}
	xmlMap["<!-- second internal VLAN IPv4 Address -->"]=${internalFreeIPv4Addresses[1]}
	xmlMap["<!-- internal VLAN IPv4 Addresses -->"]=${internalFreeIPv4Addresses[0]}","${internalFreeIPv4Addresses[1]}
	jgroupFreeIPv4Addresses=($(for a in `litp show -p /software/services/ -l`; do litp show -p $a/vm_network_interfaces/jgroups -o ipaddresses ; done 2>/dev/null | sed -e "s/,/\\n/g" | sort -t "." -n -k 1,1 -k 2,2 -k 3,3 -k 4,4 | tail -n 1 |  awk -F "." '{ for (i=$4+1;i<$4+5;++i) if (i<255) print $1"."$2"."$3"."i ; else print $1"."$2"."$3+1"."i-254;}'))
    xmlMap["<!-- jgroups VLAN IPv4 Addresses -->"]=${jgroupFreeIPv4Addresses[0]}","${jgroupFreeIPv4Addresses[1]}
	internalFreeIPv6Addresses=($(for a in `litp show -p /software/services/ -l`; do litp show -p $a/vm_network_interfaces/services -o ipv6addresses ; done 2>/dev/null | sed -e "s/,/\\n/g" | sort -t ":" -n -k 1,1 -k 2,2 -k 3,3 -k 4,4 -k 5,5 -k 6,6| tail -n 1 |  awk -F ":" '{ for (i=1;i<5;++i) {last=strtonum("0x"$6); printf ("%s:%s:%s:%s:%s:%0x\n", $1,$2,$3,$4,$5,last+i)}}'))
	xmlMap["<!-- Service VLAN IPv6 Addresses -->"]=${internalFreeIPv6Addresses[0]}","${internalFreeIPv6Addresses[1]}
}


# Data extracted from MASTER_siteEngineering.txt file

function getDataFromFile {

    xmlMap["%%ntp_1_IP%%"]=$(grep ntp_1_IP $MASTER_FILE | cut -f 2 -d=)
    xmlMap["%%ntp_2_IP%%"]=$(grep ntp_2_IP $MASTER_FILE  | cut -f 2 -d=)
    xmlMap["%%ENMservices_IPv6gateway%%"]=$(grep ENMservices_IPv6gateway $MASTER_FILE | cut -f 2 -d=)
    xmlMap["%%ENM_sfs_storage_pool_name%%"]=$(grep ENM_sfs_storage_pool_name $MASTER_FILE | cut -f 2 -d=)
    xmlMap["%%nas_vip_enm_1%%"]=$(grep nas_vip_enm_1 $MASTER_FILE | cut -f 2 -d=)
    xmlMap["%%nas_vip_enm_2%%"]=$(grep nas_vip_enm_2 $MASTER_FILE | cut -f 2 -d=)
    xmlMap["%%db_vip_jms%%"]=$(grep db_vip_jms $MASTER_FILE | cut -f 2 -d=)
    # versant
    xmlMap["%%db_vip_versant%%"]=$(grep db_vip_versant $MASTER_FILE | cut -f 2 -d=)
    # neo4j
    xmlMap["%%db_vip_neo4j_1%%"]=$(grep db_vip_neo4j_1 $MASTER_FILE | cut -f 2 -d=)
    xmlMap["%%db_vip_neo4j_2%%"]=$(grep db_vip_neo4j_2 $MASTER_FILE | cut -f 2 -d=)
    xmlMap["%%db_vip_neo4j_3%%"]=$(grep db_vip_neo4j_3 $MASTER_FILE | cut -f 2 -d=)
    xmlMap["%%db_vip_elasticsearch%%"]=$(grep db_vip_elasticsearch $MASTER_FILE | cut -f 2 -d=)
    xmlMap["%%db_vip_mysql%%"]=$(grep db_vip_mysql $MASTER_FILE | cut -f 2 -d=)
    xmlMap["%%db_vip_postgres%%"]=$(grep db_vip_postgres $MASTER_FILE | cut -f 2 -d=)
    xmlMap["%%svc_PM_vip_internal%%"]=$(grep svc_PM_vip_internal $MASTER_FILE | cut -f 2 -d=)
    xmlMap["%%LMS_IP_internal%%"]=$(grep LMS_IP_internal $MASTER_FILE | cut -f 2 -d=)
    xmlMap["%%LMS_IP_storage%%"]=$(grep LMS_IP_storage $MASTER_FILE | cut -f 2 -d=)
    xmlMap["%%vm_ssh_key%%"]=$(cat /root/.ssh/vm_private_key.pub)
}

function completeLitpXmlFiles {

    sedCommand=""

    for xmlMapKey in "${!xmlMap[@]}"
        do
            xmlMapValue=${xmlMap[$xmlMapKey]}
            sedCommand=$sedCommand's~'$xmlMapKey'~'$xmlMapValue'~g; '
        done
    sedCommand=${sedCommand%??}
    sed -i "$sedCommand" $INPUT_FILE

    if [[ ! $? -eq 0 ]]; then
	echo "ERROR in filling xml files"
    fi

}

#----------
#   MAIN
#----------

echo "Script started"

getIpAddressForSg
getDataFromFile
completeLitpXmlFiles

echo "NOTE: missing data from MASTER_siteEngineering file:"
if ! grep "%%.*%%" *.xml ; then
    echo "No data missing"
fi

echo "NOTE: missing manual entry data:"
if ! grep "{.*}" *.xml ; then
    echo "No data missing"
fi

echo "Script finished"