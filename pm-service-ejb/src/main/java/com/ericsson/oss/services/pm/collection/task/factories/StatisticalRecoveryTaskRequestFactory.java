/*
 * COPYRIGHT Ericsson 2017
 *
 * The copyright to the computer program(s) herein is the property of
 * Ericsson Inc. The programs may be used and/or copied only with written
 * permission from Ericsson Inc. or in accordance with the terms and
 * conditions stipulated in the agreement/contract under which the
 * program(s) have been supplied.
 */

package com.ericsson.oss.services.pm.collection.task.factories;

import com.ericsson.oss.pmic.dto.subscription.enums.SubscriptionType;
import com.ericsson.oss.services.pm.collection.tasks.FileCollectionDeltaRecoveryTaskRequest;
import com.ericsson.oss.services.pm.collection.tasks.FileCollectionTaskRequest;

/**
 * Created by eushmar on 13/07/2016.
 */
public class StatisticalRecoveryTaskRequestFactory {


    /**
     * @param nodeFdn
     *         - Fdn of the node
     * @param ropStartTime
     *         - Rop start time
     * @param ropPeriodInMilliseconds
     *         - Rop period in millseconds (i.e 900000 for 15 minutes rop)
     * @param numberOfRops
     *         - Number of rops to collect
     *
     * @return instance of {@link FileCollectionTaskRequest}
     */
    public FileCollectionTaskRequest createFileCollectionRecoveryOnStartupTaskRequest(final String nodeFdn, final long ropStartTime,
                                                                                      final long ropPeriodInMilliseconds, final long numberOfRops) {
        final String taskId = FileCollectionTaskIdUtil.createFileCollectionRecoveryOnStartUpTaskId(nodeFdn, ropStartTime, ropPeriodInMilliseconds);
        return new FileCollectionDeltaRecoveryTaskRequest(nodeFdn, taskId, SubscriptionType.STATISTICAL.name(), ropStartTime,
                ropPeriodInMilliseconds, false, numberOfRops);
    }

    /**
     * @param nodeFdn
     *         - Fdn of the node
     * @param ropStartTime
     *         - Rop start time
     * @param ropPeriodInMilliseconds
     *         - Rop period in millseconds (i.e 900000 for 15 minutes rop)
     * @param numberOfRops
     *         - Number of rops to collect
     *
     * @return instance of {@link FileCollectionTaskRequest}
     */
    public FileCollectionTaskRequest createFileCollectionDeltaRecoveryTaskRequest(final String nodeFdn, final long ropStartTime,
                                                                                  final long ropPeriodInMilliseconds, final long numberOfRops) {
        final String taskId = FileCollectionTaskIdUtil.createFileCollectionDeltaRecoveryTaskId(nodeFdn, ropStartTime, ropPeriodInMilliseconds,
                numberOfRops);
        return new FileCollectionDeltaRecoveryTaskRequest(nodeFdn, taskId, SubscriptionType.STATISTICAL.name(), ropStartTime,
                ropPeriodInMilliseconds, false, numberOfRops);
    }

    /**
     * @param nodeFdn
     *         - Fdn of the node
     * @param ropStartTime
     *         - Rop start time
     * @param ropPeriodInMilliseconds
     *         - Rop period in millseconds (i.e 900000 for 15 minutes rop)
     * @param numberOfRops
     *         - Number of rops to collect
     *
     * @return instance of {@link FileCollectionTaskRequest}
     */
    public FileCollectionTaskRequest createFileCollectionRecoveryOnNodeReconnectTaskRequest(final String nodeFdn, final long ropStartTime,
                                                                                            final long ropPeriodInMilliseconds,
                                                                                            final long numberOfRops) {
        final String taskId = FileCollectionTaskIdUtil.createFileCollectionRecoveryOnNodeReconnectTaskId(nodeFdn, ropStartTime,
                ropPeriodInMilliseconds, numberOfRops);
        return new FileCollectionDeltaRecoveryTaskRequest(nodeFdn, taskId, SubscriptionType.STATISTICAL.name(), ropStartTime,
                ropPeriodInMilliseconds, false, numberOfRops);
    }

}
