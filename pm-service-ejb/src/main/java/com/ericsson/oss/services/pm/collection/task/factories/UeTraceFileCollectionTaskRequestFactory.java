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

import com.ericsson.oss.pmic.dto.subscription.enums.SubscriptionType;
import com.ericsson.oss.services.pm.collection.tasks.FileCollectionTaskRequest;

/**
 * This class provides functionality to create a {@link FileCollectionTaskRequest} for a subscription type of UETRACE.
 * Uses {@link FileCollectionTaskIdUtil} to generate the task id for the request
 *
 * @author enichyl
 */
public class UeTraceFileCollectionTaskRequestFactory {

    /**
     * Creates {@link FileCollectionTaskRequest} for UETRACE subscription type.
     *
     * @param nodeFdn
     *         - The FDN of the target NetworkElement to be sent in the {@link FileCollectionTaskRequest}
     * @param ropStartTime
     *         - The <b>start</b> time of the current ROP, see {@link RopTime#getCurrentRopStartTimeInMilliSecs()}
     * @param ropPeriodInMilliseconds
     *         - The length of the current ROP in milliseconds e.g. for 15mins: 900000
     *
     * @return {@link FileCollectionTaskRequest}
     */
    public FileCollectionTaskRequest createFileCollectionTaskRequest(final String nodeFdn, final long ropStartTime,
                                                                     final long ropPeriodInMilliseconds) {
        final String taskId = FileCollectionTaskIdUtil.createRegularFileCollectionTaskId(SubscriptionType.UETRACE,
                nodeFdn, ropStartTime, ropPeriodInMilliseconds, true);
        return new FileCollectionTaskRequest(nodeFdn, taskId, SubscriptionType.UETRACE.name(), ropStartTime, ropPeriodInMilliseconds, true);
    }
}
