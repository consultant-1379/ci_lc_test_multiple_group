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

package com.ericsson.oss.services.pm.collection.recovery;

import static org.junit.Assert.assertEquals;

import static com.ericsson.oss.services.pm.collection.constants.FileCollectionConstant.FILE_COLLECTION_DELTA_RECOVERY_TASK_ID_PREFIX;

import java.util.ArrayList;
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

import com.ericsson.oss.mediation.sdk.event.MediationTaskRequest;
import com.ericsson.oss.services.pm.initiation.integration.InputBaseArquillian;
import com.ericsson.oss.services.pm.initiation.integration.SubscriptionOperationMessageSender;
import com.ericsson.oss.services.pm.initiation.model.resource.PMICScannerStatus;
import com.ericsson.oss.services.pm.initiation.node.data.NodeCreationHelperBean;
import com.ericsson.oss.services.pm.initiation.scanner.data.PMICScannerData;

@RunWith(Arquillian.class)
public class ScheduledRecoveryInTest extends InputBaseArquillian {

    @Inject
    SubscriptionOperationMessageSender sender;

    @Inject
    private NodeCreationHelperBean nodeCreationHelperBean;

    @Inject
    private FileCollectionJobCacheForRecovery fileCollectionRecoverySchedulerChanger;

    @Test
    @InSequence(2)
    @OperateOnDeployment(PM_SERVICE_TEST)
    public void createNodesAndScannersForTest() throws InterruptedException {
        nodeCreationHelperBean.createNodes(getClass().getSimpleName());

        final List<String> nodeList = new ArrayList<>();
        nodeList.add("NodeForFifteenMiute0001");
        nodeList.add("NodeForFifteenMiute0002");
        final Map<String, Object> scannerAttributesMap = createScannerAttributesMap(900, "100000", PMICScannerStatus.ACTIVE, "1",
                "USERDEF-Stats.Cont.Y.STATS", "STATS");
        final PMICScannerData pmicscannerData = new PMICScannerData("USERDEF-Stats.Cont.Y.STATS", nodeList, scannerAttributesMap);
        scannerCreationHelperBean.createScannerOnNodes(pmicscannerData);
    }

    @Test
    @InSequence(3)
    @OperateOnDeployment(PM_SERVICE_TEST)
    public void testIfRecoveryJobsHaveBeenReceived() throws Exception {
        fileCollectionRecoverySchedulerChanger.addNodesForScheduledRecoveryTest();
        final List<String> nodesToRecover = new ArrayList<>();
        nodesToRecover.add("NetworkElement=WithInRecovery0001");
        nodesToRecover.add("NetworkElement=WithInRecovery0002");
        nodesToRecover.add("NetworkElement=NodeForFifteenMiute0001");
        nodesToRecover.add("NetworkElement=NodeForFifteenMiute0002");
        final CountDownLatch cl = new CountDownLatch(4);
        tasksListener.setFileCollectionRecoveryTaskCountDownLatchForListOfNodes(cl, nodesToRecover);
        cl.await(120, TimeUnit.SECONDS);
        // Nodes in assert statements are added by FileCollectionJobCacheForRecovery
        assertEquals(1, tasksListener.getNumberOfRecoveryTasks("NetworkElement=WithInRecovery0001", FILE_COLLECTION_DELTA_RECOVERY_TASK_ID_PREFIX));
        assertEquals(1, tasksListener.getNumberOfRecoveryTasks("NetworkElement=WithInRecovery0002", FILE_COLLECTION_DELTA_RECOVERY_TASK_ID_PREFIX));
        assertEquals(1,
                tasksListener.getNumberOfRecoveryTasks("NetworkElement=NodeForFifteenMiute0001", FILE_COLLECTION_DELTA_RECOVERY_TASK_ID_PREFIX));
        assertEquals(1,
                tasksListener.getNumberOfRecoveryTasks("NetworkElement=NodeForFifteenMiute0002", FILE_COLLECTION_DELTA_RECOVERY_TASK_ID_PREFIX));
        assertEquals(0, tasksListener.getNumberOfRecoveryTasks("NetworkElement=OutOfRecovery0001", FILE_COLLECTION_DELTA_RECOVERY_TASK_ID_PREFIX));
        assertEquals(0, tasksListener.getNumberOfRecoveryTasks("NetworkElement=OutOfRecovery0002", FILE_COLLECTION_DELTA_RECOVERY_TASK_ID_PREFIX));

        final List<MediationTaskRequest> jobs = tasksListener.getRecoveryTasks("NetworkElement=WithInRecovery0001");
        Assert.assertFalse(jobs.isEmpty());
    }

    @Test
    @InSequence(4)
    @OperateOnDeployment(PM_SERVICE_TEST)
    public void cleanupNodesAndScanners() {
        nodeCreationHelperBean.deleteAllNodes();
    }
}
