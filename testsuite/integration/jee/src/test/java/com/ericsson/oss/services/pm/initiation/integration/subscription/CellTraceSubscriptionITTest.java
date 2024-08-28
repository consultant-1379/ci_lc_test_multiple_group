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
public class CellTraceSubscriptionITTest extends InputBaseArquillian {

    Logger logger = LoggerFactory.getLogger(CellTraceSubscriptionITTest.class);

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
    public void createCellTraceSubscriptionTest() throws InterruptedException {
        testResponseHolder.clear();
        final CountDownLatch cl1 = new CountDownLatch(1);
        testResponseHolder.setCountDownLatch(cl1);
        final SubscriptionOperationRequest createMessage = new SubscriptionOperationRequest(PmServiceRequestsTypes.CREATE, null, null, getClass()
                .getSimpleName());
        sender.sendTestOperationMessageToPmService(createMessage);

        final SubscriptionDataReader sdr = new SubscriptionDataReader(createMessage.getSubscriptionDataFile());
        final List<SubscriptionInfo> subscriptionList = sdr.getSubscriptionList();

        cl1.await(20, TimeUnit.SECONDS);
        final Map<String, Map<String, Object>> createdSubscriptions = testResponseHolder.getAllSubs();
        Assert.assertEquals(subscriptionList.size(), testResponseHolder.getAllSubs().size());

        for (final SubscriptionInfo subInfo : subscriptionList) {
            logger.debug("Created cell trace subscription with name : {}, adminState : {}, taskStatus : {}", subInfo.getName(),
                    subInfo.getAdminState(), subInfo.getJobStatus());
            final Map<String, Object> subscriptionAttr = createdSubscriptions.get(subInfo.getName());
            Assert.assertNotNull(subscriptionAttr);
            Assert.assertEquals(null, subscriptionAttr.get("Exception"));
            Assert.assertEquals(null, subscriptionAttr.get("ExceptionMessage"));
        }
        cleanUpSubscription();
    }

    public void cleanUpSubscription() throws InterruptedException {
        final SubscriptionOperationRequest deleteMessage = new SubscriptionOperationRequest(PmServiceRequestsTypes.DELETE, null, null, getClass()
                .getSimpleName());

        testResponseHolder.clear();
        final CountDownLatch cl2 = new CountDownLatch(1);
        testResponseHolder.setCountDownLatch(cl2);

        sender.sendTestOperationMessageToPmService(deleteMessage);
        cl2.await(60, TimeUnit.SECONDS);
    }
}
