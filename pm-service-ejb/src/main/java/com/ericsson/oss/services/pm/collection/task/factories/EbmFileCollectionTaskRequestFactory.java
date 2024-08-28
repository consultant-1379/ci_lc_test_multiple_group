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

import static com.ericsson.oss.pmic.api.collection.constants.FileCollectionModelledConfigConstants.Ebm.PROP_FILE_COLLECTION_PRIORITY_FOR_ROP;
import static com.ericsson.oss.services.pm.initiation.utils.CommonUtil.parseAndUpdateFileCollectionPriorityForRop;

import java.util.HashMap;
import java.util.Map;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import com.ericsson.oss.itpf.sdk.config.annotation.Configured;
import com.ericsson.oss.pmic.dto.subscription.enums.RopPeriod;
import com.ericsson.oss.pmic.dto.subscription.enums.SubscriptionType;
import com.ericsson.oss.services.pm.collection.roptime.RopTimeInfo;
import com.ericsson.oss.services.pm.collection.task.factories.qualifier.FileCollectionTaskRequestFactory;
import com.ericsson.oss.services.pm.collection.tasks.FileCollectionTaskRequest;
import com.ericsson.oss.services.pm.collection.tasks.HighPriorityFileCollectionTaskRequest;
import com.ericsson.oss.services.pm.initiation.cache.model.value.FileCollectionTaskWrapper;
import com.ericsson.oss.services.pm.initiation.util.RopTime;

/**
 * The Ebm file collection task request factory.
 */
@ApplicationScoped
@FileCollectionTaskRequestFactory(subscriptionType = SubscriptionType.EBM)
public class EbmFileCollectionTaskRequestFactory implements FileCollectionTaskRequestFactoryService {
    private static final Integer DEFAULT_PRIORITY = 7;

    @Inject
    @Configured(propertyName = PROP_FILE_COLLECTION_PRIORITY_FOR_ROP)
    private String pmicEbmFileCollectionPriorityForROP;

    protected final Map<Integer, Integer> ropToPriorityMap = new HashMap<>();

    @Override
    public FileCollectionTaskWrapper createFileCollectionTaskRequestWrapper(final String nodeFdn, final RopTime ropTime,
                                                                            final RopTimeInfo ropTimeInfo) {

        final String taskId = FileCollectionTaskIdUtil.createRegularFileCollectionTaskId(SubscriptionType.EBM, nodeFdn,
            ropTime.getCurrentRopStartTimeInMilliSecs(), ropTime.getRopPeriod(), true);

        FileCollectionTaskRequest fileCollectionTaskRequest = null;
        if (RopPeriod.ONE_MIN.getDurationInMilliseconds() == ropTime.getRopPeriod()) {
            fileCollectionTaskRequest = new HighPriorityFileCollectionTaskRequest(nodeFdn, taskId, SubscriptionType.EBM.name(),
                ropTime.getCurrentRopStartTimeInMilliSecs(), ropTime.getRopPeriod(),
                true);
        } else {
            fileCollectionTaskRequest = new FileCollectionTaskRequest(nodeFdn, taskId, SubscriptionType.EBM.name(),
                ropTime.getCurrentRopStartTimeInMilliSecs(),
                ropTime.getRopPeriod(), true);
        }
        final FileCollectionTaskWrapper fileCollectionTaskWrapper = new FileCollectionTaskWrapper(fileCollectionTaskRequest,
            ropTime.getCurrentROPPeriodEndTime(), ropTimeInfo);

        parseAndUpdateFileCollectionPriorityForRop(ropToPriorityMap, DEFAULT_PRIORITY, pmicEbmFileCollectionPriorityForROP,
            PROP_FILE_COLLECTION_PRIORITY_FOR_ROP);
        fileCollectionTaskWrapper.setPriority(ropToPriorityMap.get((int) ropTimeInfo.getRopTimeInSeconds()));
        return fileCollectionTaskWrapper;
    }

    public Integer getPriority(final int ropInSeconds) {
        return ropToPriorityMap.get(ropInSeconds);
    }
}
