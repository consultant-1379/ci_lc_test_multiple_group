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

package com.ericsson.oss.services.pm.initiation.subscription.data;

/**
 * This enum is used to check what attributes can be returned to test class in response.
 * See PmServiceIntegrationRequestListener
 *
 * @author ekamkal
 */
public enum SubscriptionAttributes {
    ID, NAME, DESCRIPTION, START_TIME, END_TIME, ADMIN_STATE, OP_STATE, TASK_STATUS, ROP_INTERVAL, CONFLICT_NODES, CONFLICT_COUNTERS_MOS,
    HAS_CONFLICTING_COUNTERS, ACTIVATION_TIME, PERSISTENCE_TIME, COUNTERS, EVENTS, NODES, CBS, CRITERIA_SPECIFICATION, NODE_LIST_IDENTITY, TYPE;
}
