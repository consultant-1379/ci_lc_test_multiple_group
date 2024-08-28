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

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import javax.inject.Inject;

import org.jboss.arquillian.container.test.api.OperateOnDeployment;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.arquillian.junit.InSequence;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ericsson.oss.services.pm.initiation.integration.InputBaseArquillian;
import com.ericsson.oss.services.pm.initiation.integration.SubscriptionOperationMessageSender;
import com.ericsson.oss.services.pm.initiation.node.data.NodeCreationHelperBean;
import com.ericsson.oss.services.pm.initiation.subscription.data.SubscriptionDataReader;
import com.ericsson.oss.services.pm.initiation.subscription.data.SubscriptionInfo;
import com.ericsson.oss.services.pm.test.requests.PmServiceRequestsTypes;
import com.ericsson.oss.services.pm.test.requests.SubscriptionOperationRequest;

@RunWith(Arquillian.class)
public class UpdateDeleteSubscriptionFailureCaseITTest extends InputBaseArquillian {

    Logger logger = LoggerFactory.getLogger(UpdateDeleteSubscriptionFailureCaseITTest.class);

    @Inject
    private SubscriptionOperationMessageSender sender;

    @Inject
    private NodeCreationHelperBean nodeCreationHelperBean;

    @Test
    @InSequence(3)
    @OperateOnDeployment(PM_SERVICE_TEST)
    public void createNodeForTest() throws InterruptedException {
        nodeCreationHelperBean.deleteAllSubscriptionsAndNodes();
        nodeCreationHelperBean.createNodes(getClass().getSimpleName());
    }

    @Test
    @InSequence(4)
    @OperateOnDeployment(PM_SERVICE_TEST)
    public void deleteSubscription_should_not_delete_UPDATING_SCHEDULED_ACTIVATING_DEACTIVATING_SubscriptionsTest() throws InterruptedException {
        testResponseHolder.clear();
        final SubscriptionOperationRequest createMessage = new SubscriptionOperationRequest(PmServiceRequestsTypes.CREATE, null, null, getClass()
                .getSimpleName());
        sender.sendTestOperationMessageToPmService(createMessage);

        final SubscriptionDataReader sdr = new SubscriptionDataReader(createMessage.getSubscriptionDataFile());
        final List<SubscriptionInfo> subscriptionList = sdr.getSubscriptionList();

        final CountDownLatch cl1 = new CountDownLatch(1);
        testResponseHolder.setCountDownLatch(cl1);
        cl1.await(20, TimeUnit.SECONDS);
        final Map<String, Map<String, Object>> createdSubscriptions = testResponseHolder.getAllSubs();
        Assert.assertEquals(subscriptionList.size(), testResponseHolder.getAllSubs().size());
        logger.debug("All the subscriptions with name {} are created successfully", createdSubscriptions.keySet());

        final Set<String> subscriptionNames = createdSubscriptions.keySet();
        for (final String name : subscriptionNames) {
            final Map<String, Object> subscriptionAttr = createdSubscriptions.get(name);
            Assert.assertEquals(null, subscriptionAttr.get("Exception"));
            Assert.assertEquals(null, subscriptionAttr.get("ExceptionMessage"));
        }
        testResponseHolder.clear();

        final SubscriptionOperationRequest deleteMessage = new SubscriptionOperationRequest(PmServiceRequestsTypes.DELETE_SUB_PERSISTENCE_SERVICE,
                null, null, getClass().getSimpleName());
        sender.sendTestOperationMessageToPmService(deleteMessage);
        final CountDownLatch cl2 = new CountDownLatch(1);
        testResponseHolder.setCountDownLatch(cl2);
        cl2.await(60, TimeUnit.SECONDS);

        final Map<String, Map<String, Object>> deletedSubscriptions = testResponseHolder.getAllSubs();
        Assert.assertEquals(subscriptionList.size(), testResponseHolder.getAllSubs().size());
        logger.debug("All the subscriptions with name {} should not have deleted", deletedSubscriptions.keySet());

        final Set<String> subNames = deletedSubscriptions.keySet();
        for (final String name : subNames) {
            final Map<String, Object> subscriptionAttr = deletedSubscriptions.get(name);
            logger.debug("ExceptionMessage : {} ", subscriptionAttr.get("ExceptionMessage"));
            logger.debug("Exception : {} ", subscriptionAttr.get("Exception"));
        }
        testResponseHolder.clear();
    }

    @Test
    @InSequence(5)
    @OperateOnDeployment(PM_SERVICE_TEST)
    public void updateSubscription_should_not_update_UPDATING_SCHEDULED_ACTIVATING_DEACTIVATING_SubscriptionsTest() throws InterruptedException {
        testResponseHolder.clear();

        final SubscriptionOperationRequest updateMessage = new SubscriptionOperationRequest(PmServiceRequestsTypes.UPDATE_SUB_PERSISTENCE_SERVICE,
                null, null, getClass().getSimpleName());
        sender.sendTestOperationMessageToPmService(updateMessage);
        final CountDownLatch cl2 = new CountDownLatch(1);
        testResponseHolder.setCountDownLatch(cl2);
        cl2.await(60, TimeUnit.SECONDS);

        final Map<String, Map<String, Object>> updatedSubscriptions = testResponseHolder.getAllSubs();
        logger.debug("All the subscriptions with name {} should not have updated", updatedSubscriptions.keySet());

        final Set<String> subNames = updatedSubscriptions.keySet();
        for (final String name : subNames) {
            final Map<String, Object> subscriptionAttr = updatedSubscriptions.get(name);
            logger.debug("ExceptionMessage : {} ", subscriptionAttr.get("ExceptionMessage"));
            logger.debug("Exception : {} ", subscriptionAttr.get("Exception"));
            Assert.assertEquals("InvalidSubscriptionOperationException", subscriptionAttr.get("Exception"));
        }
        testResponseHolder.clear();
    }
}
