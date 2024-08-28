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

package com.ericsson.oss.services.pm.common.systemdefined.rule;

import java.util.List;
import javax.inject.Inject;

import org.slf4j.Logger;

import com.ericsson.oss.pmic.dto.node.Node;
import com.ericsson.oss.pmic.dto.subscription.ResourceSubscription;
import com.ericsson.oss.services.pm.common.systemdefined.SubscriptionSystemDefinedAuditRule;
import com.ericsson.oss.services.pm.common.systemdefined.SystemDefinedPmCapabilities;

/**
 * This class applys audit rules Resource Subscription.
 *
 * @param <T>
 *         The subscription type.
 */
public class ResourceSubscriptionSystemDefinedAuditRule<T extends ResourceSubscription> implements SubscriptionSystemDefinedAuditRule<T> {

    @Inject
    Logger logger;

    @Override
    public void applyRuleOnUpdate(final List<Node> nodes, final T subscription) {
        logger.trace("applyRuleOnUpdate() Adding nodes {} to system defined subscription {}", nodes, subscription.getName());
        subscription.setNodes(nodes);
    }

    @Override
    public void applyRuleOnCreate(final List<Node> nodes, final T subscription) {
        logger.trace("applyRuleOnCreate() Adding nodes {} to system defined subscription {}", nodes, subscription.getName());
        subscription.setNodes(nodes);
    }

    @Override
    public void removeUnsupportedNodes(final List<Node> nodes, final SystemDefinedPmCapabilities systemDefinedPmCapabilites) {
        //no default behaviour defined for the ResourceSubscription type.
    }
}
