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

package com.ericsson.oss.services.pm.collection.scheduling;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
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

import com.ericsson.oss.itpf.sdk.eventbus.model.EventSender;
import com.ericsson.oss.itpf.sdk.eventbus.model.annotation.Modeled;
import com.ericsson.oss.mediation.sdk.event.MediationTaskResult;
import com.ericsson.oss.pmic.dto.subscription.enums.SubscriptionType;
import com.ericsson.oss.services.pm.collection.constants.FileCollectionConstant;
import com.ericsson.oss.services.pm.collection.events.FileCollectionFailure;
import com.ericsson.oss.services.pm.collection.events.FileCollectionResult;
import com.ericsson.oss.services.pm.collection.events.FileCollectionSuccess;
import com.ericsson.oss.services.pm.initiation.integration.InputBaseArquillian;
import com.ericsson.oss.services.pm.initiation.node.data.NodeCreationHelperBean;
import com.ericsson.oss.services.pm.initiation.node.data.NodeData;
import com.ericsson.oss.services.pm.initiation.node.data.NodeDataReader;

@RunWith(Arquillian.class)
public class FileCollectionSchedulingInTest extends InputBaseArquillian {

    @Inject
    @Modeled
    private EventSender<MediationTaskResult> resultSender;

    @Inject
    private NodeCreationHelperBean nodeCreationHelperBean;

    @Test
    @InSequence(2)
    @OperateOnDeployment(PM_SERVICE_TEST)
    public void should_recover_missed_files() throws InterruptedException {
        nodeCreationHelperBean.deleteAllSubscriptionsAndNodes();
        nodeCreationHelperBean.createNodes(getClass().getSimpleName());
        tasksListener.clear();
        final NodeDataReader ndr = new NodeDataReader();
        final List<NodeData> nodeDataList = ndr.getNodeData(getClass().getSimpleName());
        final List<String> nodeNames = new ArrayList<>();

        for (final NodeData nodeData : nodeDataList) {
            nodeNames.add(nodeData.getNodeName());
        }
        sendFileCollectionFailures(nodeNames, 60);

        final CountDownLatch rcl1 = new CountDownLatch(nodeNames.size());
        tasksListener.setRecoveryOneRopFileCollectionTaskCountDownLatch(rcl1);
        rcl1.await(360, TimeUnit.SECONDS);

        for (final String nodeName : nodeNames) {
            final String nodeFdn = "NetworkElement=" + nodeName;
            final String recoveryMessage = String.format(
                    "No file recovery tasks have been sent to mediation for node: %s. Total Recovery Tasks received so far: %d.", nodeFdn,
                    tasksListener.getNumberOfRecoveryTasks(nodeFdn, FileCollectionConstant.FILE_COLLECTION_SINGLE_ROP_RECOVERY_TASK_ID_PREFIX));
            Assert.assertTrue(recoveryMessage,
                    tasksListener.getNumberOfRecoveryTasks(nodeFdn, FileCollectionConstant.FILE_COLLECTION_SINGLE_ROP_RECOVERY_TASK_ID_PREFIX) > 0);
        }
        nodeCreationHelperBean.deleteNodes(getClass().getSimpleName());
        tasksListener.clear();
    }

    private void sendFileCollectionFailures(final List<String> nodeNames, final long ropPeriodInSeconds) {
        final int recoverableError = 100;
        final List<MediationTaskResult> fileCollectionFailures = new ArrayList<>();
        for (final String nodeName : nodeNames) {
            final String nodeFdn = "NetworkElement=" + nodeName;
            final Map<String, String> fileNames = new HashMap<String, String>();
            fileNames.put("file1", "file1");
            final FileCollectionFailure failure = new FileCollectionFailure("file1", "Error collecting file for node " + nodeFdn, recoverableError);
            final List<FileCollectionFailure> failures = new ArrayList<FileCollectionFailure>();
            failures.add(failure);
            final long taskStartTime = System.currentTimeMillis() - 1000;
            final long taskEndTime = System.currentTimeMillis() - 100;
            final long ropStartTime = System.currentTimeMillis() - 1000;

            final FileCollectionResult recoverableFailure = new FileCollectionResult(nodeFdn, taskStartTime, taskEndTime, "taskId",
                    SubscriptionType.STATISTICAL.name(), fileNames, "/var", "/var", ropStartTime, ropPeriodInSeconds, true, "ERBS", "3520-829-806",
                    Collections.<FileCollectionSuccess>emptyList(), failures);

            fileCollectionFailures.add(recoverableFailure);
        }
        resultSender.send(fileCollectionFailures);
    }
}
