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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import javax.inject.Inject;

import org.jboss.arquillian.container.test.api.OperateOnDeployment;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.arquillian.junit.InSequence;
import org.jboss.arquillian.transaction.api.annotation.Transactional;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ericsson.oss.pmic.dao.SubscriptionDao;
import com.ericsson.oss.pmic.dto.subscription.Subscription;
import com.ericsson.oss.pmic.dto.subscription.enums.SubscriptionType;
import com.ericsson.oss.services.pm.exception.DataAccessException;
import com.ericsson.oss.services.pm.initiation.integration.InputBaseArquillian;
import com.ericsson.oss.services.pm.initiation.integration.SubscriptionOperationMessageSender;
import com.ericsson.oss.services.pm.initiation.node.data.NodeCreationHelperBean;
import com.ericsson.oss.services.pm.test.requests.PmServiceRequestsTypes;
import com.ericsson.oss.services.pm.test.requests.SubscriptionOperationRequest;

@RunWith(Arquillian.class)
@Transactional
public class CellTraceSubscriptionNegativeITTest extends InputBaseArquillian {

    Logger logger = LoggerFactory.getLogger(CellTraceSubscriptionNegativeITTest.class);

    @Inject
    private SubscriptionOperationMessageSender sender;

    @Inject
    private NodeCreationHelperBean nodeCreationHelperBean;

    @Inject
    private SubscriptionDao subscriptionDao;

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
    public void createCellTraceSubscriptionNegativeTest() throws InterruptedException, DataAccessException {
        testResponseHolder.clear();
        final CountDownLatch cl1 = new CountDownLatch(1);
        testResponseHolder.setCountDownLatch(cl1);
        final SubscriptionOperationRequest createMessage = new SubscriptionOperationRequest(PmServiceRequestsTypes.CREATE, null, null, getClass()
                .getSimpleName());
        sender.sendTestOperationMessageToPmService(createMessage);
        cl1.await(20, TimeUnit.SECONDS);

        final List<Subscription> dpsSubscriptions = subscriptionDao.findAllBySubscriptionType(new SubscriptionType[]{SubscriptionType.CELLTRACE}, false);
        Assert.assertEquals("No subscriptions have been persisted because of ConstraintViolationException", 0, dpsSubscriptions.size());
    }
}
