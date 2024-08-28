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

package com.ericsson.oss.services.pm.collection.task.factories;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

import java.util.Date;
import java.util.UUID;

import org.junit.Before;
import org.junit.Test;
import org.mockito.MockitoAnnotations;

import com.ericsson.oss.pmic.dto.subscription.enums.SubscriptionType;
import com.ericsson.oss.services.pm.collection.constants.FileCollectionConstant;
import com.ericsson.oss.services.pm.collection.tasks.FileCollectionDeltaRecoveryTaskRequest;
import com.ericsson.oss.services.pm.collection.tasks.FileCollectionTaskRequest;
import com.ericsson.oss.services.pm.initiation.util.constants.TimeConstants;

public class StatisticalRecoveryTaskRequestFactoryTest {

    private StatisticalRecoveryTaskRequestFactory objectUnderTest;

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);
        objectUnderTest = new StatisticalRecoveryTaskRequestFactory();
    }

    @Test
    public void shouldCreateFileCollectionDeltaRecoveryTask() {
        final String nodeFdn = "NetworkElement=LTEERBS001";
        final String subscriptionType = SubscriptionType.STATISTICAL.name();
        final long ropStartTime = new Date().getTime();
        final long ropPeriodInMilliseconds = 900 * TimeConstants.ONE_SECOND_IN_MILLISECONDS;
        final long numberOfRops = 10;
        final boolean recoverInNextRop = false;
        final String taskId = new StringBuilder(subscriptionType).append("_")
                .append(FileCollectionConstant.FILE_COLLECTION_DELTA_RECOVERY_TASK_ID_PREFIX).append("|nodeFdn=").append(nodeFdn)
                .append("|ropStartTime=").append(ropStartTime).append("|ropPeriod=").append(ropPeriodInMilliseconds).append("|recoverInNextRop=")
                .append(recoverInNextRop).append("|numberOfRops=").append(numberOfRops).append("|").toString();

        final FileCollectionDeltaRecoveryTaskRequest task = (FileCollectionDeltaRecoveryTaskRequest) objectUnderTest
                .createFileCollectionDeltaRecoveryTaskRequest(nodeFdn, ropStartTime, ropPeriodInMilliseconds, numberOfRops);

        assertNotNull(task);
        assertEquals(task.getNodeAddress(), nodeFdn);
        assertEquals(task.getSubscriptionType(), subscriptionType);
        assertEquals(task.getRopStartTime(), ropStartTime);
        assertEquals(task.getRopPeriod(), ropPeriodInMilliseconds);
        assertEquals((long) task.getNumberOfRops(), numberOfRops);
        assertEquals(task.isRecoverInNextRop(), recoverInNextRop);

        final String actualTaskId = task.getJobId();
        assertTrue(actualTaskId.startsWith(taskId));
        assertNotNull(UUID.fromString(actualTaskId.substring(actualTaskId.lastIndexOf('|') + 1)));
    }

    @Test
    public void shouldCreateFileCollectionRecoveryOnNodeReconnectTask() {
        final String nodeFdn = "NetworkElement=LTEERBS001";
        final String subscriptionType = SubscriptionType.STATISTICAL.name();
        final long ropStartTime = new Date().getTime();
        final long ropPeriodInMilliseconds = 900 * TimeConstants.ONE_SECOND_IN_MILLISECONDS;
        final long numberOfRops = 10;
        final boolean recoverInNextRop = false;
        final String taskId = new StringBuilder(subscriptionType).append("_")
                .append(FileCollectionConstant.FILE_COLLECTION_RECOVERY_ON_NODE_RECONNECT_TASK_ID_PREFIX).append("|nodeFdn=").append(nodeFdn)
                .append("|ropStartTime=").append(ropStartTime).append("|ropPeriod=").append(ropPeriodInMilliseconds).append("|recoverInNextRop=")
                .append(recoverInNextRop).append("|numberOfRops=").append(numberOfRops).append("|").toString();

        final FileCollectionDeltaRecoveryTaskRequest task = (FileCollectionDeltaRecoveryTaskRequest) objectUnderTest
                .createFileCollectionRecoveryOnNodeReconnectTaskRequest(nodeFdn, ropStartTime, ropPeriodInMilliseconds, numberOfRops);

        assertNotNull(task);
        assertEquals(task.getNodeAddress(), nodeFdn);
        assertEquals(task.getSubscriptionType(), subscriptionType);
        assertEquals(task.getRopStartTime(), ropStartTime);
        assertEquals(task.getRopPeriod(), ropPeriodInMilliseconds);
        assertEquals((long) task.getNumberOfRops(), numberOfRops);
        assertEquals(task.isRecoverInNextRop(), recoverInNextRop);

        final String actualTaskId = task.getJobId();
        assertTrue(actualTaskId.startsWith(taskId));
        assertNotNull(UUID.fromString(actualTaskId.substring(actualTaskId.lastIndexOf('|') + 1)));
    }

    @Test
    public void shouldCreateFileCollectionRecoveryOnStartupTask() {
        final String nodeFdn = "NetworkElement=LTEERBS001";
        final String subscriptionType = SubscriptionType.STATISTICAL.name();
        final long ropStartTime = new Date().getTime();
        final long ropPeriodInMilliseconds = 900 * TimeConstants.ONE_SECOND_IN_MILLISECONDS;
        final boolean recoverInNextRop = false;
        final String taskId = new StringBuilder(subscriptionType).append("_")
                .append(FileCollectionConstant.FILE_COLLECTION_RECOVERY_ON_STARTUP_TASK_ID_PREFIX).append("|nodeFdn=").append(nodeFdn)
                .append("|ropStartTime=").append(ropStartTime).append("|ropPeriod=").append(ropPeriodInMilliseconds).append("|recoverInNextRop=")
                .append(recoverInNextRop).append("|").toString();

        final FileCollectionTaskRequest task = objectUnderTest.createFileCollectionRecoveryOnStartupTaskRequest(nodeFdn, ropStartTime,
                ropPeriodInMilliseconds, 1);

        assertNotNull(task);
        assertEquals(task.getNodeAddress(), nodeFdn);
        assertEquals(task.getSubscriptionType(), subscriptionType);
        assertEquals(task.getRopStartTime(), ropStartTime);
        assertEquals(task.getRopPeriod(), ropPeriodInMilliseconds);
        assertEquals(task.isRecoverInNextRop(), recoverInNextRop);

        final String actualTaskId = task.getJobId();
        assertTrue(actualTaskId.startsWith(taskId));
        assertNotNull(UUID.fromString(actualTaskId.substring(actualTaskId.lastIndexOf('|') + 1)));
    }

}
