/*------------------------------------------------------------------------------
 *******************************************************************************
 * COPYRIGHT Ericsson 2015
 *
 * The copyright to the computer program(s) herein is the property of
 * Ericsson Inc. The programs may be used and/or copied only with written
 * permission from Ericsson Inc. or in accordance with the terms and
 * conditions stipulated in the agreement/contract under which the
 * program(s) have been supplied.
 *******************************************************************************
 *----------------------------------------------------------------------------*/

package com.ericsson.oss.services.pm.initiation.integration.subscription;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import javax.inject.Inject;

import org.jboss.arquillian.container.test.api.OperateOnDeployment;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.arquillian.junit.InSequence;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;

import com.ericsson.oss.services.pm.exception.DataAccessException;
import com.ericsson.oss.services.pm.initiation.integration.InputBaseArquillian;
import com.ericsson.oss.services.pm.initiation.integration.SubscriptionTestUtils;
import com.ericsson.oss.services.pm.initiation.node.data.NodeCreationHelperBean;

@RunWith(Arquillian.class)
public class DeleteNodeFromEnmITTest extends InputBaseArquillian {

    @Inject
    private Logger logger;

    @Inject
    private SubscriptionTestUtils testUtils;

    @Inject
    private NodeCreationHelperBean nodeCreationHelperBean;

    @Test
    @InSequence(4)
    @OperateOnDeployment(PM_SERVICE_TEST)
    public void should_create_subscription() throws Exception {
        tasksListener.setUseStubbedMediation(true);
        nodeCreationHelperBean.deleteAllSubscriptionsAndNodes();
        nodeCreationHelperBean.createNodes(getClass().getSimpleName());
        testUtils.createSubscriptions(getClass().getSimpleName());
        testUtils.assertNumberOfNodesInSubscription(3, "DeleteNodeFromEnm");
    }

    @Test
    @InSequence(5)
    @OperateOnDeployment(PM_SERVICE_TEST)
    public void should_delete_one_node_and_assert_number_of_nodes_in_subscription() throws InterruptedException, DataAccessException {
        logger.debug("Delete Node3");
        nodeCreationHelperBean.deleteOneNode("Node3");
        final CountDownLatch cl1 = new CountDownLatch(1);
        testResponseHolder.setCountDownLatch(cl1);
        cl1.await(60, TimeUnit.SECONDS);
        testUtils.assertNumberOfNodesInSubscription(2, "DeleteNodeFromEnm");
        nodeCreationHelperBean.deleteAllSubscriptionsAndNodes();
    }
}
