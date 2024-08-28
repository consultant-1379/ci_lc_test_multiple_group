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

import java.util.Map;

import com.ericsson.oss.services.pm.collection.tasks.FileCollectionTaskRequest;

/**
 * The type File collection single rop recovery task request factory.
 */
public class FileCollectionSingleRopRecoveryTaskRequestFactory {

    /**
     * Create file collection single rop recovery task request file collection task request.
     *
     * @param taskId
     *            the task id
     * @param nodeFdn
     *            the node fully distinguished name
     * @param subscriptionType
     *            the subscription type
     * @param ropStartTime
     *            the rop start time
     * @param ropPeriodInMilliseconds
     *            the rop period in milliseconds
     * @return the file collection task request
     */
    public FileCollectionTaskRequest createFileCollectionSingleRopRecoveryTaskRequest(final String taskId, final String nodeFdn,
            final String subscriptionType, final long ropStartTime, final long ropPeriodInMilliseconds) {
        final String recoveryTaskId = FileCollectionTaskIdUtil.createFileCollectionSingleRopRecoveryTaskId(taskId, ropPeriodInMilliseconds);
        return new FileCollectionTaskRequest(nodeFdn, recoveryTaskId, subscriptionType, ropStartTime, ropPeriodInMilliseconds, false);
    }

    /**
     * Create file collection single rop recovery task request file collection task request with source and destination file name
     *
     * @param taskId
     *            the task id
     * @param nodeFdn
     *            the node fully distinguished name
     * @param subscriptionType
     *            the subscription type
     * @param ropStartTime
     *            the rop start time
     * @param ropPeriodInMilliseconds
     *            the rop period in milliseconds
     * @param destAndSourceFileNames
     *            source and destination file name (Key Destination File Name and value Source File Name)
     * @return the file collection task request
     */
    public FileCollectionTaskRequest createFileCollectionSingleRopRecoveryTaskRequest(final String taskId, final String nodeFdn,
            final String subscriptionType, final long ropStartTime, final long ropPeriodInMilliseconds,
            final Map<String, String> destAndSourceFileNames) {
        final String recoveryTaskId = FileCollectionTaskIdUtil.createFileCollectionSingleRopRecoveryTaskId(taskId, ropPeriodInMilliseconds);
        return new FileCollectionTaskRequest(nodeFdn, recoveryTaskId, subscriptionType, ropStartTime, ropPeriodInMilliseconds, false,
                destAndSourceFileNames);
    }

}
