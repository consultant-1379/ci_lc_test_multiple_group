/*------------------------------------------------------------------------------
 *******************************************************************************
 * COPYRIGHT Ericsson 2016
 *
 * The copyright to the computer program(s) herein is the property of
 * Ericsson Inc. The programs may be used and/or copied only with written
 * permission from Ericsson Inc. or in accordance with the terms and
 * conditions stipulated in the agreement/contract under which the
 * program(s) have been supplied.
 *******************************************************************************
 *----------------------------------------------------------------------------*/
package com.ericsson.oss.services.pm.common.systemdefined;

import com.ericsson.oss.pmic.dto.node.Node;

import java.util.List;

/**
 * This API provides general methods to perform auditing for System Defined Subscriptions.
 */
public interface SubscriptionAuditorLocal {

    /**
     * Performs audits such as creating, updating and deleting System defined subscriptions.
     *
     * @param rule
     *         subsccription audit rules to apply
     * @param capabilities
     *         capabilities to audit
     */
    void auditSystemDefinedSubscriptions(final SubscriptionSystemDefinedAuditRule rule, final List<SystemDefinedPmCapabilities> capabilities);

    /**
     * Performs get of nodes in new TX read only, DO NOT USE THIS METHOD DIRECTLY.
     *
     * @param rule
     *         subscription audit rules to apply
     * @param systemDefinedCapabilities
     *         capabilities to audit
     */
    List<Node> getNodesInReadTx(final SystemDefinedPmCapabilities systemDefinedCapabilities, final SubscriptionSystemDefinedAuditRule rule);
}