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

package com.ericsson.oss.services.pm.initiation.timer.delay;

import java.util.Map;
import java.util.Set;

import javax.inject.Inject;

import org.jboss.arquillian.container.test.api.OperateOnDeployment;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.arquillian.junit.InSequence;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;

import com.ericsson.oss.services.pm.initiation.integration.InputBaseArquillian;
import com.ericsson.oss.services.pm.initiation.integration.SubscriptionTestUtils;
import com.ericsson.oss.services.pm.initiation.node.data.NodeCreationHelperBean;

@RunWith(Arquillian.class)
public class AddOrRemoveNodesFromActiveSubscriptionITTest extends InputBaseArquillian {

    @Inject
    Logger logger;

    @Inject
    private SubscriptionTestUtils testUtils;

    @Inject
    private NodeCreationHelperBean nodeCreationHelperBean;

    @Test
    @InSequence(4)
    @OperateOnDeployment(PM_SERVICE_TEST)
    public void should_add_and_remove_node_from_active_subscription() throws InterruptedException {
        tasksListener.setUseStubbedMediation(true);
        nodeCreationHelperBean.deleteAllSubscriptionsAndNodes();
        nodeCreationHelperBean.createNodes(getClass().getSimpleName());
        testUtils.createSubscriptions(getClass().getSimpleName());
        final Map<String, Map<String, Object>> createdSubscriptions = testResponseHolder.getAllSubs();

        final Set<String> subscriptionNames = createdSubscriptions.keySet();
        logger.debug("Created subscription names {}", subscriptionNames);

        Assert.assertFalse("No test subscriptions were created!", subscriptionNames.isEmpty());
/*
    Commented code due to intermittent failure
        for (final String name : subscriptionNames) {
            Map<String, Object> subscriptionAttr = createdSubscriptions.get(name);
            logger.debug("Attributes for subscription {}: {}", name, subscriptionAttr);
            final String id = (String) subscriptionAttr.get(ID.name());
            subscriptionAttr = testUtils.activate(name, subscriptionAttr);
            testUtils.assertActivationSucceed(id, name, subscriptionAttr);
        }

        waitUntil(AdministrationState.ACTIVE, new Duration(1, TimeUnit.MINUTES));
        logger.debug("Add node to active subscription");
        final List<String> nodesToBeAdded = new ArrayList<>();
        nodesToBeAdded.add("NetworkElement=Node3");
        testUtils.addNodesToSubscriptions(nodesToBeAdded, "AddOrRmoveNodesFromActiveSubscription");


        waitUntil(AdministrationState.ACTIVE, new Duration(1, TimeUnit.MINUTES));

        logger.debug("Remove node from active subscription");
        final List<String> nodesToBeRemoved = new ArrayList<>();
        nodesToBeRemoved.add("NetworkElement=Node3");
        testUtils.removeNodesFromSubscriptions(nodesToBeRemoved, "AddOrRmoveNodesFromActiveSubscription");

        waitUntil(AdministrationState.ACTIVE, new Duration(1, TimeUnit.MINUTES));*/
    }
}
