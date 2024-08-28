/*------------------------------------------------------------------------------
 *******************************************************************************
 * COPYRIGHT Ericsson 2012
 *
 * The copyright to the computer program(s) herein is the property of
 * Ericsson Inc. The programs may be used and/or copied only with written
 * permission from Ericsson Inc. or in accordance with the terms and
 * conditions stipulated in the agreement/contract under which the
 * program(s) have been supplied.
 *******************************************************************************
 *----------------------------------------------------------------------------*/

package com.ericsson.oss.services.pm.test.requests;

public enum PmServiceRequestsTypes {
    CREATE,
    CREATE_UE,
    DELETE,
    DELETE_UE,
    LIST,
    LIST_ACTIVE,
    RETURNED,
    UPDATE_SUB_PERSISTENCE_SERVICE,
    DELETE_SUB_PERSISTENCE_SERVICE,
    ACTIVATE,
    DEACTIVATE,
    ADD_NODE_TO_SUBSCRIPTION,
    REMOVE_NODE_FROM_SUBSCRIPTION;
}
