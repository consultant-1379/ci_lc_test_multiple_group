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

import java.util.List;
import javax.ejb.Asynchronous;
import javax.ejb.EJB;
import javax.ejb.Stateless;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;
import javax.inject.Inject;

import org.slf4j.Logger;

import com.ericsson.oss.pmic.dto.node.Node;
import com.ericsson.oss.pmic.impl.handler.InvokeInTransaction;
import com.ericsson.oss.pmic.impl.handler.ReadOnly;
import com.ericsson.oss.pmic.profiler.logging.LogProfiler;
import com.ericsson.oss.pmic.subscription.capability.SubscriptionCapabilityReader;

/**
 * Audit system defined subscription.
 */
@Stateless
public class SubscriptionAuditorImpl implements SubscriptionAuditorLocal {

    @Inject
    private Logger logger;
    @Inject
    private SubscriptionCapabilityReader systemDefinedCapabilityReader;
    @Inject
    private SystemDefinedSubscriptionManager systemDefinedSubscriptionManager;
    @EJB
    private SubscriptionAuditorLocal self;

    @Override
    @Asynchronous
    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    @LogProfiler(name = "System Defined Subscriptions periodic audit")
    public void auditSystemDefinedSubscriptions(final SubscriptionSystemDefinedAuditRule rule, final List<SystemDefinedPmCapabilities> capabilities) {
        logger.debug("Starting System Defined Subscription Audit");
        for (final SystemDefinedPmCapabilities systemDefinedCapabilities : capabilities) {
            try {
                final List<Node> nodes = self.getNodesInReadTx(systemDefinedCapabilities, rule);
                systemDefinedSubscriptionManager.createUpdateOrDeleteSystemDefinedSubscription(systemDefinedCapabilities, nodes, rule);
            } catch (final Exception exception) {
                logger.error("Error occurred in system defined audit {}", systemDefinedCapabilities.getEnumSubscriptionNameString(), exception);
            }
        }
    }

    @ReadOnly
    @InvokeInTransaction
    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public List<Node> getNodesInReadTx(final SystemDefinedPmCapabilities systemDefinedCapabilities,
                                       final SubscriptionSystemDefinedAuditRule rule) {
        return systemDefinedSubscriptionManager.getNodes(systemDefinedCapabilities, rule);
    }
}
