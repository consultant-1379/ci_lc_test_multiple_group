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

package com.ericsson.oss.services.pm.collection.api;

import java.util.List;

import com.ericsson.oss.services.pm.initiation.cache.model.value.FileCollectionTaskWrapper;
import com.ericsson.oss.services.pm.initiation.util.RopTime;

/**
 * Interface to provide operation needed to send file collection jobs to mediation.
 *
 * @author ekamkal
 */
public interface FileCollectionTaskSenderLocal extends RopSchedulerServiceLocal {

    /**
     * Send the provided tasks provided the pm function is turned ON for those nodes. Does not remove old tasks with this action
     *
     * @param tasksToSend
     *         - Tasks to be sent for Collection
     * @param ropOfTasksToSend
     *         - The ROP for which the tasks are to be sent
     */
    void sendFileCollectionTasks(final List<FileCollectionTaskWrapper> tasksToSend, final RopTime ropOfTasksToSend);

}
