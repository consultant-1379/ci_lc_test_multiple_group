/*------------------------------------------------------------------------------
 *******************************************************************************
 * COPYRIGHT Ericsson 2017
 *
 * The copyright to the computer program(s) herein is the property of
 * Ericsson Inc. The programs may be used and/or copied only with written
 * permission from Ericsson Inc. or in accordance with the terms and
 * conditions stipulated in the agreement/contract under which the
 * program(s) have been supplied.
 *******************************************************************************
 *----------------------------------------------------------------------------*/

package com.ericsson.oss.services.pm.collection.schedulers;

import java.util.Set;
import javax.ejb.Asynchronous;
import javax.ejb.Stateless;
import javax.inject.Inject;

import org.slf4j.Logger;

import com.ericsson.oss.services.pm.collection.cache.FileCollectionTaskCacheWrapper;

/**
 * Class FileCollectionTaskCacheAsyncCleaner removes the expired FileCollectionTask from the cache.
 */
@Stateless
public class FileCollectionTaskCacheAsyncCleaner {

    @Inject
    private Logger logger;

    @Inject
    private FileCollectionTaskCacheWrapper fileCollectionTaskCache;

    /**
     * Delete Task from the FileCollectionTaskCache.
     *
     * @param jobIdsOfTasksForRemoval
     *         list of jobIds to be removed.
     */
    @Asynchronous
    public void removeExpiredTasks(final Set<String> jobIdsOfTasksForRemoval) {

        logger.debug("Number Of Expired Task : {}", jobIdsOfTasksForRemoval.size());
        fileCollectionTaskCache.removeTasks(jobIdsOfTasksForRemoval);
    }
}
