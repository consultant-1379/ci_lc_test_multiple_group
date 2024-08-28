/*
 * COPYRIGHT Ericsson 2017
 * The copyright to the computer program(s) herein is the property of
 * Ericsson Inc. The programs may be used and/or copied only with written
 * permission from Ericsson Inc. or in accordance with the terms and
 * conditions stipulated in the agreement/contract under which the
 * program(s) have been supplied.
 */

package com.ericsson.oss.services.pm.common.eventsender;

import java.util.List;

import javax.ejb.Local;

import com.ericsson.oss.mediation.sdk.event.MediationTaskRequest;

/**
 * Helper Interface for sending {@link com.ericsson.oss.services.pm.collection.tasks.FileCollectionTaskRequest}
 */
@Local
public interface FileCollectionSender {

    /**
     * Will send tasks using batch sending.
     *
     * @param tasks
     *            - {@link com.ericsson.oss.services.pm.collection.tasks.FileCollectionTaskRequest} tasks to send
     */
    void sendTasksBatch(final List<MediationTaskRequest> tasks);

    /**
     * Will send tasks using batch sending and provided priority.
     *
     * @param tasks
     *            - {@link com.ericsson.oss.services.pm.collection.tasks.FileCollectionTaskRequest} tasks to send
     * @param priority
     *            - priority
     */
    void sendTasksBatch(final List<MediationTaskRequest> tasks, final int priority);

    /**
     * Will send tasks one by one using batch sending and provided priority
     *
     * @param tasks
     *            {@link com.ericsson.oss.services.pm.collection.tasks.FileCollectionTaskRequest} tasks to send
     * @param priority
     *            priority
     */
    void sendTasksOneByOne(final List<MediationTaskRequest> tasks, final int priority);
}
