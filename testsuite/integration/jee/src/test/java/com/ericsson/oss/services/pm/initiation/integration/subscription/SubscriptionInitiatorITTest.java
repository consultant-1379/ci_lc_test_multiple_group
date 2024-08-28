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

package com.ericsson.oss.services.pm.initiation.integration.subscription;

import static com.ericsson.oss.services.pm.initiation.subscription.data.SubscriptionAttributes.ID;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import javax.inject.Inject;

import org.awaitility.Duration;
import org.jboss.arquillian.container.test.api.OperateOnDeployment;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.arquillian.junit.InSequence;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;

import com.ericsson.oss.pmic.dto.subscription.enums.AdministrationState;
import com.ericsson.oss.pmic.dto.subscription.enums.TaskStatus;
import com.ericsson.oss.services.pm.initiation.integration.InputBaseArquillian;
import com.ericsson.oss.services.pm.initiation.integration.SubscriptionTestUtils;
import com.ericsson.oss.services.pm.initiation.node.data.NodeCreationHelperBean;

@RunWith(Arquillian.class)
public class SubscriptionInitiatorITTest extends InputBaseArquillian {

    @Inject
    private Logger logger;

    @Inject
    private SubscriptionTestUtils testUtils;

    @Inject
    private NodeCreationHelperBean nodeCreationHelperBean;

    @Test
    @InSequence(4)
    @OperateOnDeployment(PM_SERVICE_TEST)
    public void should_activate_subscriptions_immediatelly_and_scheduled() throws Exception {
        tasksListener.setUseStubbedMediation(true);
        nodeCreationHelperBean.deleteAllSubscriptionsAndNodes();
        nodeCreationHelperBean.createNodes(getClass().getSimpleName());
        testUtils.createSubscriptions(getClass().getSimpleName());
        try {
            executeSubscriptionActivationDeactivationAndStateAssertions();
        } finally {
            cleanUpTestState();
        }
    }

    /**
     * Cleans up test state by deleting the subscriptions, nodes and the state of the listener.
     *
     * @throws Exception
     */
    private void cleanUpTestState() throws Exception {
        try {
            testUtils.deleteSubscriptions(getClass().getSimpleName());
            nodeCreationHelperBean.deleteAllNodes(10, 10);
            testResponseHolder.clear();
        } catch (final Exception exception) {
            logger.error("Exception during cleanup", exception);
        }
    }

    private void executeSubscriptionActivationDeactivationAndStateAssertions() throws InterruptedException {
        activateSubscriptionAndAssert();
        waitUntil(AdministrationState.ACTIVE, new Duration(2, TimeUnit.MINUTES));

        deactivateSubscriptionAndAssert();
        waitUntil(AdministrationState.INACTIVE, new Duration(2, TimeUnit.MINUTES));
    }

    private void executeSubscriptionActivationOnlyAndStateAssertions() throws InterruptedException {
        activateSubscriptionAndAssert();
        waitUntil(AdministrationState.ACTIVE, new Duration(1, TimeUnit.MINUTES));
        findSubscriptionAndAssertTaskStatus(TaskStatus.ERROR);
    }

    /**
     * @throws InterruptedException
     */
    private void activateSubscriptionAndAssert() throws InterruptedException {
        final Map<String, Map<String, Object>> createdSubscriptions = testUtils.findSubscriptions(getClass().getSimpleName());
        final Set<String> subscriptionNames = createdSubscriptions.keySet();
        for (final String name : subscriptionNames) {
            Map<String, Object> subscriptionAttr = createdSubscriptions.get(name);
            final String subscriptionId = (String) subscriptionAttr.get(ID.name());
            subscriptionAttr = testUtils.activate(name, subscriptionAttr);
            testUtils.assertActivationSucceed(subscriptionId, name, subscriptionAttr);
        }
    }

    /**
     * @throws InterruptedException
     */
    private void deactivateSubscriptionAndAssert() throws InterruptedException {
        final Map<String, Map<String, Object>> testSubscriptions = testUtils.findSubscriptions(getClass().getSimpleName());
        for (final String name : testSubscriptions.keySet()) {
            Map<String, Object> subscriptionAttr = testSubscriptions.get(name);
            final String subscriptionId = (String) subscriptionAttr.get(ID.name());
            testUtils.assertAdministrationState(subscriptionId, name, subscriptionAttr, AdministrationState.ACTIVE);
            subscriptionAttr = testUtils.deactivate(name, subscriptionAttr);
            testUtils.assertAdministrationState(subscriptionId, name, subscriptionAttr, AdministrationState.DEACTIVATING);
        }
    }

    /**
     * Finds the subscriptions part of this test and asserts on the value for the susbcription's task status
     *
     * @throws InterruptedException
     */
    private void findSubscriptionAndAssertTaskStatus(final TaskStatus expectedTaskStatus) throws InterruptedException {
        final Map<String, Map<String, Object>> testSubscriptions = testUtils.findSubscriptions(getClass().getSimpleName());
        for (final String name : testSubscriptions.keySet()) {
            final Map<String, Object> subscriptionAttr = testSubscriptions.get(name);
            final String subscriptionId = (String) subscriptionAttr.get(ID.name());
            assertTaskStatus(subscriptionId, name, subscriptionAttr, expectedTaskStatus);
        }
    }
}
