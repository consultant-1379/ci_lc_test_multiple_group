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

import com.ericsson.oss.pmic.dto.subscription.enums.RopPeriod;
import com.ericsson.oss.pmic.dto.subscription.enums.SubscriptionType;
import com.ericsson.oss.services.pm.collection.constants.FileCollectionConstant;
import com.ericsson.oss.services.pm.collection.roptime.RopTimeInfo;
import com.ericsson.oss.services.pm.initiation.cache.model.value.FileCollectionTaskWrapper;
import com.ericsson.oss.services.pm.initiation.util.RopTime;

public class CellTraceFileCollectionTaskRequestFactoryTest {

    private CellTraceFileCollectionTaskRequestFactory objectUnderTest;

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);
        objectUnderTest = new CellTraceFileCollectionTaskRequestFactory();
    }

    @Test
    public void shouldCreateFileCollectionTask() {
        final String nodeFdn = "NetworkElement=LTEERBS001";
        final String subscriptionType = SubscriptionType.CELLTRACE.name();
        final long ropStartTime = new Date().getTime();
        final RopTime ropTime = new RopTime(ropStartTime, 900);
        final RopTimeInfo ropTimeInfo = new RopTimeInfo(RopPeriod.FIFTEEN_MIN.getDurationInSeconds(), 300);
        final boolean recoverInNextRop = true;
        final String taskId = new StringBuilder(subscriptionType).append("_")
                .append(FileCollectionConstant.FILE_COLLECTION_TASK_ID_PREFIX).append("|nodeFdn=").append(nodeFdn).append("|ropStartTime=")
                .append(ropTime.getCurrentRopStartTimeInMilliSecs()).append("|ropPeriod=").append(ropTime.getRopPeriod())
                .append("|recoverInNextRop=").append(recoverInNextRop)
                .append("|").toString();

        final FileCollectionTaskWrapper taskWrapper = objectUnderTest.createFileCollectionTaskRequestWrapper(nodeFdn, ropTime, ropTimeInfo);

        assertNotNull(taskWrapper);
        assertEquals(taskWrapper.getFileCollectionTaskRequest().getNodeAddress(), nodeFdn);
        assertEquals(taskWrapper.getFileCollectionTaskRequest().getSubscriptionType(), subscriptionType);
        assertEquals(taskWrapper.getFileCollectionTaskRequest().getRopStartTime(), ropTime.getCurrentRopStartTimeInMilliSecs());
        assertEquals(taskWrapper.getFileCollectionTaskRequest().getRopPeriod(), ropTime.getRopPeriod());
        assertEquals(taskWrapper.getFileCollectionTaskRequest().isRecoverInNextRop(), recoverInNextRop);

        final String actualTaskId = taskWrapper.getFileCollectionTaskRequest().getJobId();
        assertTrue(actualTaskId.startsWith(taskId));
        assertNotNull(UUID.fromString(actualTaskId.substring(actualTaskId.lastIndexOf('|') + 1)));
    }
}
