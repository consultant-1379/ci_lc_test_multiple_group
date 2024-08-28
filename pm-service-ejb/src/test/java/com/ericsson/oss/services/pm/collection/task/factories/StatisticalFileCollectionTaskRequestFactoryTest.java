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
import java.util.HashMap;
import java.util.Map;
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
import com.ericsson.oss.services.pm.initiation.utils.CommonUtil;

public class StatisticalFileCollectionTaskRequestFactoryTest {

    private StatisticalFileCollectionTaskRequestFactory objectUnderTest;

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);
        objectUnderTest = new StatisticalFileCollectionTaskRequestFactory();
    }

    @Test
    public void shouldCreateFileCollectionTask() {
        final String nodeFdn = "NetworkElement=LTEERBS001";
        final String subscriptionType = SubscriptionType.STATISTICAL.name();
        final long ropStartTime = new Date().getTime();
        final RopTime ropTime = new RopTime(ropStartTime, 900);
        final RopTimeInfo ropTimeInfo = new RopTimeInfo(RopPeriod.FIFTEEN_MIN.getDurationInSeconds(), 300);
        final boolean recoverInNextRop = true;
        final String taskId = new StringBuilder(subscriptionType).append("_").append(FileCollectionConstant.FILE_COLLECTION_TASK_ID_PREFIX)
            .append("|nodeFdn=").append(nodeFdn).append("|ropStartTime=").append(ropTime.getCurrentRopStartTimeInMilliSecs())
            .append("|ropPeriod=")
            .append(ropTime.getRopPeriod()).append("|recoverInNextRop=").append(recoverInNextRop).append("|").toString();

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

    @Test
    public void fileCollectionPriorityForRopParseAndUpdate() {
        final String fileCollectionPriorityForRop = "ONE_MIN:5,FIFTEEN_MIN:3";
        final String badFormmattedFileCollectionPriorityForRop1 = "ONE_MIN:,FIFTEEN_MIN:7";
        final String badFormmattedFileCollectionPriorityForRop2 = "ONE_MIN:5,FIFTEEN_MIN:3";
        final String defaultDileCollectionPriorityForRop = "ONE_MIN:6,FIFTEEN_MIN:6";
        final String fileCollectionProperty = "pmicStatisticalFileCollectionPriorityForROP";
        final Map<Integer, Integer> ropToPriorityMap = new HashMap<>();
        CommonUtil.parseAndUpdateFileCollectionPriorityForRop(ropToPriorityMap, 6, badFormmattedFileCollectionPriorityForRop1, fileCollectionProperty);
        assertTrue(ropToPriorityMap.get(60) == 6);
        assertTrue(ropToPriorityMap.get(900) == 7);
        CommonUtil.parseAndUpdateFileCollectionPriorityForRop(ropToPriorityMap, 6, badFormmattedFileCollectionPriorityForRop2, fileCollectionProperty);
        assertTrue(ropToPriorityMap.get(60) == 5);
        assertTrue(ropToPriorityMap.get(900) == 3);
        CommonUtil.parseAndUpdateFileCollectionPriorityForRop(ropToPriorityMap, 6, fileCollectionPriorityForRop, fileCollectionProperty);
        assertTrue(ropToPriorityMap.get(60) == 5);
        assertTrue(ropToPriorityMap.get(900) == 3);
        CommonUtil.parseAndUpdateFileCollectionPriorityForRop(ropToPriorityMap, 6, defaultDileCollectionPriorityForRop, fileCollectionProperty);
        assertTrue(ropToPriorityMap.get(60) == 6);
        assertTrue(ropToPriorityMap.get(900) == 6);
    }
}
