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

package com.ericsson.oss.services.pm.collection.schedulers;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.inject.Inject;

import org.slf4j.Logger;

import com.ericsson.oss.mediation.sdk.event.MediationTaskRequest;
import com.ericsson.oss.services.pm.common.eventsender.FileCollectionSender;
import com.ericsson.oss.services.pm.common.logging.PMICLog;
import com.ericsson.oss.services.pm.common.logging.SystemRecorderWrapperLocal;
import com.ericsson.oss.services.pm.generic.NodeService;
import com.ericsson.oss.services.pm.initiation.cache.model.value.FileCollectionTaskWrapper;
import com.ericsson.oss.services.pm.instrumentation.ExtendedFileCollectionInstrumentation;
import com.ericsson.oss.services.pm.time.TimeGenerator;

import static com.ericsson.oss.services.pm.common.logging.PMICLog.Event.SEND_FILE_COLLECTION_TASK_LIST;

/**
 * Helper class for FileCollectionTaskSender. It's useful to implement a retry mechanism of JMS message in a ROP.
 *
 * @author ealcasa
 */
public class FileCollectionTaskSenderBeanHelper {

    @Inject
    private Logger logger;

    @Inject
    private ExtendedFileCollectionInstrumentation fileCollectionInstrumentation;

    @Inject
    private TimeGenerator timeGenerator;

    @Inject
    private NodeService nodeService;

    @Inject
    private FileCollectionSender fileCollectionSender;

    @Inject
    private FileCollectionTaskCacheAsyncCleaner fileCollectionTaskCacheAsyncCleaner;

    @Inject
    private SystemRecorderWrapperLocal systemRecorder;

    /**
     * Send or skip executable file collection tasks int.
     *
     * @param ropInfo
     *            - Record Output Period Info to determine if file collection task is executable at current time
     * @param sortedTasks
     *            - file collection tasks to be sent. Please be mindful that elements from input list may be removed
     * @return List of sent tasks
     */
    public List<FileCollectionTaskWrapper> sendOrSkipExecutableFileCollectionTasks(final String ropInfo,
            final List<FileCollectionTaskWrapper> sortedTasks) {
        final long currentTimeMillis = timeGenerator.currentTimeMillis();
        logger.debug("sendOrSkipExecutableFileCollectionTasks was launched on {}", currentTimeMillis);

        final Set<String> jobIdsOfTasksForRemoval = removeTasksThatWillNotExecuteNow(sortedTasks, ropInfo, currentTimeMillis);

        fileCollectionTaskCacheAsyncCleaner.removeExpiredTasks(jobIdsOfTasksForRemoval);
        final Set<String> sentTaskKeys = sendTasksForNodesWithPmFunctionOn(sortedTasks);

        fileCollectionInstrumentation.fileCollectionTasksStarted(sentTaskKeys);
        return sortedTasks;
    }

    /**
     * Send the provided tasks provided the pm function is turned ON for those nodes. Does not remove old tasks with this action
     *
     * @param tasks
     *            - File collection tasks to send
     * @return - number of tasks that have successfully been sent
     */
    public int sendFileCollectionTasks(final List<FileCollectionTaskWrapper> tasks) {
        final Set<String> sentTaskKeys = sendTasksForNodesWithPmFunctionOn(tasks);
        fileCollectionInstrumentation.fileCollectionTasksStarted(sentTaskKeys);
        return tasks.size();
    }

    /**
     * Send file collection tasks that were unresponsive during fail over
     *
     * @param sortedTasks
     *            the tasks to be sent
     * @return returns the number of tasks sent
     */
    public int sendUnresponsedFileCollectionTasks(final List<FileCollectionTaskWrapper> sortedTasks) {
        final long currentTimeMillis = timeGenerator.currentTimeMillis();
        logger.debug("sendUnresponsedFileCollectionTasks was launched on {}", new Date(currentTimeMillis));

        for (final Iterator<FileCollectionTaskWrapper> iterator = sortedTasks.iterator(); iterator.hasNext();) {
            final FileCollectionTaskWrapper fileCollectionTaskWrapper = iterator.next();
            if (!fileCollectionTaskWrapper.isTaskExecutionTimeOlderThanCurrentTime(currentTimeMillis)) {
                iterator.remove();
            }
        }
        final Set<String> sentTaskKeys = sendTasksForNodesWithPmFunctionOn(sortedTasks);
        fileCollectionInstrumentation.fileCollectionTasksStarted(sentTaskKeys);

        return sentTaskKeys.size();
    }

    /**
     * Sends tasks and returns their jobIds. Will not send tasks for nodes with pm function attribute pmEnabled==false;
     *
     * @param sortedTasks
     *            - File Collection MTRs to send. Removes tasks from this list if pm function is OFF
     * @return a set of job ids representing tasks that have been sent.
     */
    private Set<String> sendTasksForNodesWithPmFunctionOn(final List<FileCollectionTaskWrapper> sortedTasks) {
        final Map<String,Integer> subscriptionTypeMTRs = new HashMap<>();
        final Set<String> sentTaskKeys = new HashSet<>();
        if (sortedTasks.isEmpty()) {
            return sentTaskKeys;
        }
        Map<Integer, List<MediationTaskRequest>> prioToMTRs = new HashMap<>();
        String subscriptionType = sortedTasks.get(0).getFileCollectionTaskRequest().getSubscriptionType();
        for (final Iterator<FileCollectionTaskWrapper> iterator = sortedTasks.iterator(); iterator.hasNext();) {
            final FileCollectionTaskWrapper fileCollectionTaskWrapper = iterator.next();
            final Integer priority = fileCollectionTaskWrapper.getPriority();
            final String nodeAddress = fileCollectionTaskWrapper.getFileCollectionTaskRequest().getNodeAddress();
            if (!nodeService.isPmFunctionEnabled(nodeAddress)) {
                systemRecorder.eventCoarse(PMICLog.Event.SKIPPED_MEDIATION_EVENT, nodeAddress,
                        "PM is disabled hence Mediation Task Event SKIPPED for node: {} ", nodeAddress);
                iterator.remove();
                continue;
            }
            if (hasDifferentSubscriptionType(subscriptionType, fileCollectionTaskWrapper)) {
                sendTasksBatch(prioToMTRs);
                updateSubscriptionMTRMap(prioToMTRs,subscriptionTypeMTRs,subscriptionType);
                subscriptionType = fileCollectionTaskWrapper.getFileCollectionTaskRequest().getSubscriptionType();
                prioToMTRs = new HashMap<>();
            }
            if (prioToMTRs.containsKey(priority)) {
                prioToMTRs.get(priority).add(fileCollectionTaskWrapper.getFileCollectionTaskRequest());
            } else {
                final List<MediationTaskRequest> batchMTRs = new ArrayList<>();
                batchMTRs.add(fileCollectionTaskWrapper.getFileCollectionTaskRequest());
                prioToMTRs.put(priority, batchMTRs);
            }
            sentTaskKeys.add(fileCollectionTaskWrapper.getFileCollectionTaskRequest().getJobId());
        }
        // send the remainder
        sendTasksBatch(prioToMTRs);
        updateSubscriptionMTRMap(prioToMTRs,subscriptionTypeMTRs,subscriptionType);
        systemRecorder.eventCoarse(SEND_FILE_COLLECTION_TASK_LIST, "File collection Task statistics sent to Mediation",
                "SubscriptionTypes: {} for which File Collection tasks sent {}", subscriptionTypeMTRs.keySet(), subscriptionTypeMTRs.values());
        return sentTaskKeys;
    }

    /**
     * Instrumentation method to update the file collection tasks for each subscription type in the current ROP
     * @param prioToMTRs
     * @param subscriptionTypeMTRs
     * @param subscriptionType
     */
    private void updateSubscriptionMTRMap(Map<Integer, List<MediationTaskRequest>> prioToMTRs, Map<String, Integer> subscriptionTypeMTRs,String subscriptionType) {
        try {
            int count = 0;
            if(subscriptionTypeMTRs.containsKey(subscriptionType)){
                count = subscriptionTypeMTRs.get(subscriptionType);
            }
            for (Map.Entry<Integer, List<MediationTaskRequest>> entry : prioToMTRs.entrySet()){
                count += entry.getValue().size();
            }
            subscriptionTypeMTRs.put(subscriptionType, count);
        }catch(Exception e){
            logger.error(e.getMessage());
        }
    }

    private boolean hasDifferentSubscriptionType(final String subscriptionType, final FileCollectionTaskWrapper fileCollectionTaskWrapper) {
        return !fileCollectionTaskWrapper.getFileCollectionTaskRequest().getSubscriptionType().equals(subscriptionType);
    }

    private void sendTasksBatch(final Map<Integer, List<MediationTaskRequest>> prioToMTRs) {
        if (!prioToMTRs.isEmpty()) {
            for (final Map.Entry<Integer, List<MediationTaskRequest>> entry : prioToMTRs.entrySet()) {
                logger.debug("List of task {}", entry.getValue().size());
                fileCollectionSender.sendTasksBatch(entry.getValue(), entry.getKey());
                logger.debug("Successfully sent task");
            }
        }
    }

    private Set<String> removeTasksThatWillNotExecuteNow(final List<FileCollectionTaskWrapper> tasks, final String ropInfo,
            final long currentTimeMillis) {
        final int taskSize = tasks.size();
        final Set<String> jobIdsOfTasksForRemoval = new HashSet<>();
        for (final Iterator<FileCollectionTaskWrapper> iterator = tasks.iterator(); iterator.hasNext();) {
            logger.debug("Number Of Task before removing : {}, Total task : {}, Rop Info : {}", tasks.size(), taskSize, ropInfo);
            final FileCollectionTaskWrapper task = iterator.next();
            if (!task.isTaskExecutableNow(currentTimeMillis, ropInfo)) {
                iterator.remove();
            } else if (task.isTaskReadyForRemoval(currentTimeMillis, ropInfo)) {
                jobIdsOfTasksForRemoval.add(task.getFileCollectionTaskRequest().getJobId());
                iterator.remove();
            }
        }
        logger.debug("Number Of executableTask : {}, Total task : {}, Rop Info : {}", tasks.size(), taskSize, ropInfo);
        return jobIdsOfTasksForRemoval;
    }
}
