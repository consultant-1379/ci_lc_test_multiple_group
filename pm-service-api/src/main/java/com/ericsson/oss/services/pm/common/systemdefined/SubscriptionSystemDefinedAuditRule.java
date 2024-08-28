/*------------------------------------------------------------------------------
 *******************************************************************************
 * COPYRIGHT Ericsson 2018
 *
 * The copyright to the computer program(s) herein is the property of
 * Ericsson Inc. The programs may be used and/or copied only with written
 * permission from Ericsson Inc. or in accordance with the terms and
 * conditions stipulated in the agreement/contract under which the
 * program(s) have been supplied.
 *******************************************************************************
 *----------------------------------------------------------------------------*/

package com.ericsson.oss.services.pm.common.systemdefined;

import java.util.List;

import com.ericsson.oss.pmic.dto.node.Node;
import com.ericsson.oss.pmic.dto.subscription.ResourceSubscription;

/**
 * Audit rule to be applied to system defined subscription.
 *
 * @param <T>
 *         The subscription type for this rule.
 */
public interface SubscriptionSystemDefinedAuditRule<T extends ResourceSubscription> {

    /**
     * Audit rule to be applied when system defined subscription is updated.
     *
     * @param nodes
     *         Nodes for the subscritpion.
     * @param subscription
     *         The subscription to be updated.
     */
    void applyRuleOnUpdate(final List<Node> nodes, final T subscription);

    /**
     * Audit rule to be applied when system defined subscription is created.
     *
     * @param nodes
     *         Nodes for the subscription.
     * @param subscription
     *         The subscritpion to be created.
     */
    void applyRuleOnCreate(final List<Node> nodes, final T subscription);

    /**
     * Remove unsupported nodes from the list using the system defined subscription capabilities for the verification.
     *
     * @param nodes
     *         list of nodes
     * @param systemDefiniedPmCapabilities
     *         system defined capabilities used in filtering
     */
    void removeUnsupportedNodes(final List<Node> nodes, final SystemDefinedPmCapabilities systemDefiniedPmCapabilities);
}
