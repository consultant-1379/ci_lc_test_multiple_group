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

package com.ericsson.oss.services.pm.initiation.scanner.polling;

import java.util.List;
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
import com.ericsson.oss.services.pm.initiation.node.data.NodeCreationHelperBean;
import com.ericsson.oss.services.pm.initiation.node.data.NodeData;
import com.ericsson.oss.services.pm.initiation.node.data.NodeDataReader;
import com.ericsson.oss.services.pm.integration.test.steps.PibSteps;

@RunWith(Arquillian.class)
public class ScannerPollingSchedulerITInTest extends InputBaseArquillian {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    @Inject
    private NodeCreationHelperBean nodeCreationHelperBean;

    @Inject
    private PibSteps pibSteps;

    @Test
    @InSequence(3)
    @OperateOnDeployment(PM_SERVICE_TEST)
    public void createAllNode() throws InterruptedException {
        logger.debug("executing #createAllNode");
        nodeCreationHelperBean.deleteAllSubscriptionsAndNodes();
        nodeCreationHelperBean.createNodes(this.getClass().getSimpleName());
        nodeCreationHelperBean.createNodes(this.getClass().getSimpleName() + "_MediationAutonomyEnabled");
        nodeCreationHelperBean.createNodes(this.getClass().getSimpleName() + "_ScannerMasterDisabled");
    }

    @Test
    @InSequence(4)
    @OperateOnDeployment(PM_SERVICE_TEST)
    public void scanner_polling_timer_should_change_on_configuration_param_change() throws Exception {
        pibSteps.updateConfigParam("scannerPollingIntervalMinutes", "1", Integer.class);
    }

    @Test
    @InSequence(6)
    @OperateOnDeployment(PM_SERVICE_TEST)
    public void scanner_polling_jobs_should_not_be_sent_with_mediation_autonomy_enabled() throws InterruptedException {
        final NodeDataReader nodeDataReader = new NodeDataReader();
        final List<NodeData> nodesData = nodeDataReader.getNodeData(getClass().getSimpleName() + "_MediationAutonomyEnabled");
        final CountDownLatch cl = new CountDownLatch(nodesData.size());
        tasksListener.setScannerPollingCountDownLatch(cl);
        cl.await(80, TimeUnit.SECONDS);
        for (final NodeData nodeData : nodesData) {
            logger.debug("Asserting that no ScannerPollingTask has been sent for node {} with mediation autonomy enabled", nodeData.getNodeName());
            Assert.assertTrue(tasksListener.getScannerPollingTasks("NetworkElement=" + nodeData.getNodeName()).size() == 0);
        }
    }

    @Test
    @InSequence(7)
    @OperateOnDeployment(PM_SERVICE_TEST)
    public void scanner_polling_jobs_should_be_sent_with_scanner_master_disabled() throws InterruptedException {
        final NodeDataReader nodeDataReader = new NodeDataReader();
        final List<NodeData> nodesData = nodeDataReader.getNodeData(getClass().getSimpleName() + "_ScannerMasterDisabled");
        final CountDownLatch cl = new CountDownLatch(nodesData.size());
        tasksListener.setScannerPollingCountDownLatch(cl);
        cl.await(80, TimeUnit.SECONDS);
        for (final NodeData nodeData : nodesData) {
            logger.debug("Asserting that no ScannerPollingTask has been sent for node {} with scanner master disabled", nodeData.getNodeName());
            Assert.assertTrue(tasksListener.getScannerPollingTasks("NetworkElement=" + nodeData.getNodeName()).size() == 0);
        }
    }
}
