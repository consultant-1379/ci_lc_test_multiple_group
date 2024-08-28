/*
 * ------------------------------------------------------------------------------
 *  COPYRIGHT Ericsson  2019
 *
 *  The copyright to the computer program(s) herein is the property of
 *  Ericsson Inc. The programs may be used and/or copied only with written
 *  permission from Ericsson Inc. or in accordance with the terms and
 *  conditions stipulated in the agreement/contract under which the
 *  program(s) have been supplied.
 * ----------------------------------------------------------------------------
 */
package com.ericsson.oss.services.pm.collection.task.factories

import static org.testng.Assert.assertEquals
import static org.testng.Assert.assertNotNull
import static org.testng.Assert.assertTrue

import spock.lang.Unroll

import com.ericsson.cds.cdi.support.rule.ObjectUnderTest
import com.ericsson.cds.cdi.support.spock.CdiSpecification
import com.ericsson.oss.pmic.dto.subscription.enums.RopPeriod
import com.ericsson.oss.pmic.dto.subscription.enums.SubscriptionType
import com.ericsson.oss.services.pm.collection.constants.FileCollectionConstant
import com.ericsson.oss.services.pm.collection.roptime.RopTimeInfo
import com.ericsson.oss.services.pm.initiation.cache.model.value.FileCollectionTaskWrapper
import com.ericsson.oss.services.pm.initiation.util.RopTime
import com.ericsson.oss.services.pm.initiation.utils.CommonUtil;

class EbmFileCollectionTaskRequestFactorySpec extends CdiSpecification {

    @ObjectUnderTest
    EbmFileCollectionTaskRequestFactory ebmFileCollectionTaskRequestFactory

    def "create file collection task on valid cases"() {
        given:
            def nodeFdn = "NetworkElement=CORE01SGSN001";
            def subscriptionType = SubscriptionType.EBM.name();
            def ropStartTime = new Date().getTime();
            RopTime ropTime = new RopTime(ropStartTime, 60);
            RopTimeInfo ropTimeInfo = new RopTimeInfo(RopPeriod.ONE_MIN.getDurationInSeconds(), 300);
            def recoverInNextRop = true;
            def taskId = new StringBuilder(subscriptionType).append("_").append(FileCollectionConstant.FILE_COLLECTION_TASK_ID_PREFIX)
                .append("|nodeFdn=").append(nodeFdn).append("|ropStartTime=").append(ropTime.getCurrentRopStartTimeInMilliSecs())
                .append("|ropPeriod=")
                .append(ropTime.getRopPeriod()).append("|recoverInNextRop=").append(recoverInNextRop).append("|").toString();

        when:
            final FileCollectionTaskWrapper taskWrapper = ebmFileCollectionTaskRequestFactory.createFileCollectionTaskRequestWrapper(nodeFdn, ropTime, ropTimeInfo);

        then:
            assertNotNull(taskWrapper);
            assertEquals(taskWrapper.getFileCollectionTaskRequest().getNodeAddress(), nodeFdn);
            assertEquals(taskWrapper.getFileCollectionTaskRequest().getSubscriptionType(), subscriptionType);
            assertEquals(taskWrapper.getFileCollectionTaskRequest().getRopStartTime(), ropTime.getCurrentRopStartTimeInMilliSecs());
            assertEquals(taskWrapper.getFileCollectionTaskRequest().getRopPeriod(), ropTime.getRopPeriod());
            assertEquals(taskWrapper.getFileCollectionTaskRequest().isRecoverInNextRop(), recoverInNextRop);

            def actualTaskId = taskWrapper.getFileCollectionTaskRequest().getJobId();
            assertTrue(actualTaskId.startsWith(taskId));
            assertNotNull(UUID.fromString(actualTaskId.substring(actualTaskId.lastIndexOf('|') + 1)));
    }

    @Unroll
    def "File collection priority #fileCollectionPriorityForRop should be set based on pib value, on incorrect cases, default priority to be set"() {
        given:
            def fileCollectionProperty = "pmicEbmFileCollectionPriorityForROP";
            def Map<Integer, Integer> ropToPriorityMap = new HashMap<>();
        when:
            CommonUtil.parseAndUpdateFileCollectionPriorityForRop(ropToPriorityMap, 7, fileCollectionPriorityForRop,
                fileCollectionProperty);
        then:
            assertTrue(ropToPriorityMap.get(60) == 9);
            assertTrue(ropToPriorityMap.get(900) == 7);
        where:
            fileCollectionPriorityForRop << ["ONE_MIN:9", "ONE_MIN:9,FIFTEEN_MIN:7"]
    }
}