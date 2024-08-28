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

package com.ericsson.oss.services.pm.collection.task.factories;

import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

import static com.ericsson.oss.services.pm.collection.constants.FileCollectionConstant.FILE_COLLECTION_SINGLE_ROP_RECOVERY_TASK_ID_PREFIX;

import java.util.Date;
import java.util.UUID;

import org.junit.Before;
import org.junit.Test;
import org.mockito.MockitoAnnotations;
import org.testng.Assert;

import com.ericsson.oss.pmic.dto.subscription.enums.SubscriptionType;
import com.ericsson.oss.services.pm.collection.constants.FileCollectionConstant;
import com.ericsson.oss.services.pm.collection.tasks.FileCollectionTaskRequest;
import com.ericsson.oss.services.pm.initiation.util.constants.TimeConstants;

public class FileCollectionSingleRopRecoveryTaskFactoryTest {

    private FileCollectionSingleRopRecoveryTaskRequestFactory objectUnderTest;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        objectUnderTest = new FileCollectionSingleRopRecoveryTaskRequestFactory();
    }

    @Test
    public void shouldCreateFileCollectionTask() {
        final String nodeFdn = "NetworkElement=LTEERBS001";
        final String subscriptionType = SubscriptionType.CELLTRACE.name();
        final long ropStartTime = new Date().getTime();
        final long ropPeriodInMilliseconds = 900 * TimeConstants.ONE_SECOND_IN_MILLISECONDS;
        final boolean recoverInNextRop = false;
        final String taskId = new StringBuilder(subscriptionType).append("_")
                .append(FileCollectionConstant.FILE_COLLECTION_TASK_ID_PREFIX).append("|nodeFdn=").append(nodeFdn).append("|ropStartTime=")
                .append(ropStartTime).append("|ropPeriod=").append(ropPeriodInMilliseconds).append("|recoverInNextRop=").append(true).append("|")
                .append(UUID.randomUUID()).toString();
        final String recoveryTaskId = new StringBuilder(FILE_COLLECTION_SINGLE_ROP_RECOVERY_TASK_ID_PREFIX).append("|taskId=").append(taskId)
                .append("|").toString();

        final FileCollectionTaskRequest task = objectUnderTest.createFileCollectionSingleRopRecoveryTaskRequest(taskId, nodeFdn, subscriptionType,
                ropStartTime, ropPeriodInMilliseconds);

        assertNotNull(task);
        Assert.assertEquals(task.getNodeAddress(), nodeFdn);
        Assert.assertEquals(task.getSubscriptionType(), subscriptionType);
        Assert.assertEquals(task.getRopStartTime(), ropStartTime);
        Assert.assertEquals(task.getRopPeriod(), ropPeriodInMilliseconds);
        Assert.assertEquals(task.isRecoverInNextRop(), recoverInNextRop);

        final String actualTaskId = task.getJobId();
        assertTrue(actualTaskId.startsWith(recoveryTaskId));
        assertNotNull(UUID.fromString(actualTaskId.substring(actualTaskId.lastIndexOf('|') + 1)));
    }
}
