#!/usr/bin/python

"""
DESCRIPTION: This script loads the pm collection mount points from global.properties file
and puses its value to the running JBoss instance via PIB's script to make them available
to the application.
"""

__version__ = "1.0.0"

__copyright__ = """
 *******************************************************************************
 * COPYRIGHT Ericsson 2015
 *
 * The copyright to the computer program(s) herein is the property of
 * Ericsson Inc. The programs may be used and/or copied only with written
 * permission from Ericsson Inc. or in accordance with the terms and
 * conditions stipulated in the agreement/contract under which the
 * program(s) have been supplied.
 *******************************************************************************
 """

import sys
import logging.handlers
import subprocess as sub
import socket
import os
import re
from os import environ

logger = logging.getLogger('ERICjbossconfig')
handler = logging.handlers.SysLogHandler(address='/dev/log')
logger.setLevel(logging.INFO)
msg_format = " %(name)s %(message)s"
formatter = logging.Formatter(msg_format, "%b %d %H:%M:%S")
handler.setFormatter(formatter)
logger.addHandler(handler)

PIB_HOME = environ["PIB_HOME"]
PIB_CONFIG_SCRIPT = PIB_HOME + "/config.py"
PIB_CMD_CREATE = PIB_CONFIG_SCRIPT + " create"
PIB_CMD_READ = PIB_CONFIG_SCRIPT + " read"
PIB_CMD_UPDATE = PIB_CONFIG_SCRIPT + " update"
PIB_PARAM_APP_SERVER_ID = socket.gethostname()
PIB_PARAM_APP_SERVER_ADDRESS = os.getenv('PIB_ADDRESS', "127.0.0.1:8080")
GLOBAL_CONFIG_FILE = environ["GLOBAL_CONFIG"]

pmic_nfs_share_list = ""


# This is the parameter name to be CREATED or UPDATED in PIB. If the name needs
# to change, it should be this value
_param_name = "pmicNfsShareList"

def parse_pmic_nfs_share_entries_from_global_config():
    """ This method parses the global.properties file and fills the global variable 'pmic_nfs_share_list'
        with the entries found in this file. In addition, formats the concatenated values
        or all these entries to issue the appropriete command to PIB
    """
    pattern_searched_for_key = "pmicNfsShareList="
    with open(GLOBAL_CONFIG_FILE, "r") as global_props:
        for current_line in global_props:
            match = re.search(pattern_searched_for_key, current_line)
            if  match:
                global pmic_nfs_share_list
                # Remove line terminators from value
                pmic_nfs_share_list = re.sub("\n", "", re.sub(pattern_searched_for_key, "", current_line))
    log_pmic_nfs_share_values()


def log_pmic_nfs_share_values():
    """ Utility method. The input file and the read values and formatted
        values are logged for supportability purposes.
    """
    logger.info("--------------------------------")
    logger.info("The following values will be used for pmicNfsShareList mount points:")
    logger.info("  Source file: %s", GLOBAL_CONFIG_FILE)
    logger.info("  pmic_nfs_share_list: %s", pmic_nfs_share_list)
    logger.info("--------------------------------")


def create_base_args_dict():
    """ Utility method. Creates a base dictionary with the common parameters needed
        according to the PIB documentation
    """
    args = {}
    args["app_server_address"] = PIB_PARAM_APP_SERVER_ADDRESS
    return args


def get_read_all_args():
    """ Utility method. Creates a dictionary with the parameters needed for a READ ALL
        command according to the PIB documentation
    """
    pib_args = create_base_args_dict()
    pib_args["name"] = "somerandomstring"
    return pib_args


def get_read_args(param_name):
    """ Utility method. Creates a dictionary with the parameters needed for a READ
        command according to the PIB documentation
    """
    pib_args = create_base_args_dict()
    pib_args["name"] = param_name
    return pib_args


def get_update_args(param_name, param_value):
    """ Utility method. Creates a dictionary with the parameters needed for an UPDATE
        command according to the PIB documentation
    """
    pib_args = create_base_args_dict()
    pib_args["name"] = param_name
    pib_args["value"] = param_value
    return pib_args


def get_create_args(param_name, param_value):
    """ Utility method. Creates a dictionary with the parameters needed for an CREATE
        command according to the PIB documentation
    """
    pib_args = create_base_args_dict()
    pib_args["type"] = "String"
    pib_args["scope"] = "GLOBAL"
    pib_args["name"] = param_name
    pib_args["value"] = param_value
    return pib_args


def format_command(cmd, args):
    """ Utility method. Formats the parameters needed for the PIB interface using the
        following format: --paramName=paramValue
    """
    for k, v in args.items():
    	if v != "" :
            cmd += " --%s=%s" % (k, v)
        else :
            cmd += " --%s" % (k)
    return cmd

def execute_pib_call(cmd, expected_val_for_sucess):
    """ Issues a PIB call using the config.py interface. The parameter 'cmd' tells the operation
        that will be executed (CRU). The parameter 'expected_val_for_sucess' works
        as a check so that, if a value different from the expected is returned after this call,
        an error is logged.
    """
    logger.info("Command: %s", cmd)
    process = sub.Popen([cmd], shell=True, stdout=sub.PIPE, stderr=sub.PIPE)
    output, errors = process.communicate()

    logger.info("PIB_CMD_STDOUT: %s", output)
    logger.info("PIB_CMD_STDERR: %s", errors)

    if expected_val_for_sucess != "" and  process.returncode != expected_val_for_sucess:
        logger.error(errors)

    return process.returncode

def execute_pib_call_without_expected_val(cmd):
    """ Issues a PIB call using the config.py interface. The parameter 'cmd' tells the operation
        that will be executed (CRU).
    """
    logger.info("Command: %s", cmd)
    process = sub.Popen([cmd], shell=True, stdout=sub.PIPE, stderr=sub.PIPE)
    output, errors = process.communicate()

    logger.info("PIB_CMD_STDOUT: %s", output)
    logger.info("PIB_CMD_STDERR: %s", errors)

    return output


def execute_create_action(__param_name, __param_value, expected_val_for_sucess):
    """ Issues a PIB CREATE call for the parameter given by '__param_name' and the value
        specified by '__param_value'. The parameter 'expected_val_for_sucess' defines what
        the expected return value for sucess is so that, if the call returns something different,
        an error is logged
    """
    create_args = get_create_args(__param_name, __param_value)
    create_cmd = format_command(PIB_CMD_CREATE, create_args)
    return execute_pib_call(create_cmd, expected_val_for_sucess)

def execute_update_action(__param_name, __param_value, expected_val_for_sucess):
    """ Issues a PIB UPDATE call for the parameter given by '__param_name' and the value
        specified by '__param_value'. The parameter 'expected_val_for_sucess' defines what
        the expected return value for sucess is so that, if the call returns something different,
        an error is logged
    """
    update_args = get_update_args(__param_name, __param_value)
    update_cmd = format_command(PIB_CMD_UPDATE, update_args)
    return execute_pib_call(update_cmd, expected_val_for_sucess)

def execute_read_action(__param_name):
    """ Issues a PIB READ call for the parameter given by '__param_name'. The parameter 'expected_val_for_sucess' defines what
        the expected return value for sucess is so that, if the call returns something different,
        an error is logged
    """
    read_args = get_read_args(__param_name)
    read_cmd = format_command(PIB_CMD_READ, read_args)
    return execute_pib_call_without_expected_val(read_cmd)

def check_pib_availability():
    """ Issues a PIB READ call to check if the PIB service is available
    """
    expected_val_for_sucess = 0;
    read_all_args = get_read_all_args()
    read_all_cmd = format_command(PIB_CMD_READ, read_all_args)

    logger.info("Checking PIB availability with command: %s", read_all_cmd);

    pib_availability_retval = execute_pib_call(read_all_cmd, expected_val_for_sucess)

    if pib_availability_retval == 2:
        logger.error("PIB service not running on %s" % (PIB_PARAM_APP_SERVER_ID))
        sys.exit(1)

def create_or_update_pmic_nfs_share_list():
    """ This method attempts to perform a PIB CREATE command to register the value for 'pmicNfsShareList'.
        If the create operation fails, it is assumed that the parameter already exists and a second
        attempt is executed as a PIB UPDATE command so that the latest values found in global.properties
        are made available to the application
    """
    expected_retval_for_sucess_on_create = 0
    expected_retval_for_sucess_on_update = 0
    create_retval = "N/A"
    update_retval = "N/A"

    create_retval = execute_create_action(_param_name, pmic_nfs_share_list, expected_retval_for_sucess_on_create)

    if create_retval == expected_retval_for_sucess_on_create:
        logger.info("Parameter created sucessfully. Param name: %s, Param value: %s", _param_name, pmic_nfs_share_list)
    else:
        logger.info("Looks like the parameter is already created. Value will only be updated...")
        # Update with latest values from global.properties as param is already registered in cache
        update_retval = execute_update_action(_param_name, pmic_nfs_share_list, expected_retval_for_sucess_on_update)
        if update_retval == expected_retval_for_sucess_on_update:
            logger.info("Parameter updated sucessfully. Param name: %s, Param value: %s", _param_name, pmic_nfs_share_list)
        else:
            logger.error("Parameter '%s' could not be updated to value '%s'. Check logs for further information")
    # Leave some traces...
    log_summary(create_retval, update_retval)


def log_summary(create_retval, update_retval):
    """ Utility method to log the results of the operations executed
    """
    logger.info("------------------")
    logger.info("Execution summary for registering parameter '%s' in PIB:", _param_name)
    # For creation...
    if update_retval != "N/A":
        logger.info("  PARAM CREATION STATUS: Atempted but failed. (Return value: %s)", create_retval)
        logger.info("  PARAM UPDATE STATUS: OK. (Return value: %s)", update_retval)
    else:
        logger.info("  PARAM CREATION STATUS: OK. (Return value: %s)", create_retval)
        logger.info("  PARAM UPDATE STATUS: Not neded. (Return value: %s)", update_retval)

    logger.info("------------------")

def verify_if_pmic_nfs_share_list_update_needed():
    """ This method verifies if update to pmicNfsShareList is needed """
    retval = execute_read_action(_param_name)
    if retval == '[]\n':
        logger.info("PIB:pmicNfsShareList is empty so update is needed from Global Properties.")
    else:
        logger.info("PIB:pmicNfsShareList is already set so no change needed!")
        sys.exit(0)


# Main execution starts here
# Check if PIB is available. This will terminate the script
# if we don't get an appropriate response
check_pib_availability()

#Verify if pmicNfsShareList update is required
verify_if_pmic_nfs_share_list_update_needed()

#parse global propery file to fetch pmicNfsShareList
parse_pmic_nfs_share_entries_from_global_config()

# Verify if parameter is set, if not then try to create, if parameters are already registered, just update
create_or_update_pmic_nfs_share_list()

# Signal normal termination
sys.exit(0)