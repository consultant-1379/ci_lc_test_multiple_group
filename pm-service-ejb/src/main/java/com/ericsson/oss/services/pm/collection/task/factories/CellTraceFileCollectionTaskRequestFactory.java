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

import javax.enterprise.context.ApplicationScoped;

import com.ericsson.oss.pmic.dto.subscription.enums.SubscriptionType;
import com.ericsson.oss.services.pm.collection.roptime.RopTimeInfo;
import com.ericsson.oss.services.pm.collection.task.factories.qualifier.FileCollectionTaskRequestFactory;
import com.ericsson.oss.services.pm.collection.tasks.FileCollectionTaskRequest;
import com.ericsson.oss.services.pm.initiation.cache.model.value.FileCollectionTaskWrapper;
import com.ericsson.oss.services.pm.initiation.util.RopTime;

/**
 * The Cell trace file collection task request factory.
 */
@ApplicationScoped
@FileCollectionTaskRequestFactory(subscriptionType = SubscriptionType.CELLTRACE)
public class CellTraceFileCollectionTaskRequestFactory implements FileCollectionTaskRequestFactoryService {

    @Override
    public FileCollectionTaskWrapper createFileCollectionTaskRequestWrapper(final String nodeFdn, final RopTime ropTime,
                                                                            final RopTimeInfo ropTimeInfo) {
        final String taskId = FileCollectionTaskIdUtil.createRegularFileCollectionTaskId(SubscriptionType.CELLTRACE, nodeFdn,
                ropTime.getCurrentRopStartTimeInMilliSecs(), ropTime.getRopPeriod(), true);

        final FileCollectionTaskRequest fileCollectionTaskRequest = new FileCollectionTaskRequest(nodeFdn, taskId, SubscriptionType.CELLTRACE.name(),
                ropTime.getCurrentRopStartTimeInMilliSecs(), ropTime.getRopPeriod(), true);
        final FileCollectionTaskWrapper fileCollectionTaskWrapper = new FileCollectionTaskWrapper(fileCollectionTaskRequest,
                ropTime.getCurrentROPPeriodEndTime(), ropTimeInfo);
        fileCollectionTaskWrapper.setPriority(7);
        return fileCollectionTaskWrapper;
    }
}
