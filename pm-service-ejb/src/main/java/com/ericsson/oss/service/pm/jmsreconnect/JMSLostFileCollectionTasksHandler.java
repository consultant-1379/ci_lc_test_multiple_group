/*------------------------------------------------------------------------------
 *******************************************************************************
 * COPYRIGHT Ericsson 2016
 *
 * The copyright to the computer program(s) herein is the property of
 * Ericsson Inc. The programs may be used and/or copied only with written
 * permission from Ericsson Inc. or in accordance with the terms and
 * conditions stipulated in the agreement/contract under which the
 * program(s) have been supplied.
 *******************************************************************************
 *----------------------------------------------------------------------------*/

package com.ericsson.oss.service.pm.jmsreconnect;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import javax.annotation.PostConstruct;
import javax.ejb.ConcurrencyManagement;
import javax.ejb.ConcurrencyManagementType;
import javax.ejb.Lock;
import javax.ejb.LockType;
import javax.ejb.Singleton;
import javax.inject.Inject;

import org.slf4j.Logger;

import com.ericsson.oss.pmic.dto.subscription.enums.RopPeriod;
import com.ericsson.oss.services.pm.collection.cache.FileCollectionTaskCacheWrapper;
import com.ericsson.oss.services.pm.collection.cache.utils.FileCollectionSorter;
import com.ericsson.oss.services.pm.initiation.cache.model.value.FileCollectionTaskWrapper;

/**
 * Singleton bean JMSLostFileCollectionTasksHandler maintain the list of sent file collection tasks ids, so that in case of jms fail over, these tasks
 * can be resend, as some of these tasks may be lost during jms failover.
 *
 * @author eilmchi
 */
@Singleton
@ConcurrencyManagement(ConcurrencyManagementType.CONTAINER)
@Lock(LockType.READ)
public class JMSLostFileCollectionTasksHandler {
    private static final Integer FIFTEEN_MINUTES_ROP_SIZE = 2;

    private static final Integer ONE_MINUTES_ROP_SIZE = 10;

    private static final Integer ONE_DAY_ROP_SIZE = 2;

    @Inject
    private Logger logger;

    @Inject
    private FileCollectionSorter sorter;

    @Inject
    private FileCollectionTaskCacheWrapper fileCollectionTaskCache;

    private Map<Integer, List<Set<String>>> sentFileCollectionTasks;

    private Set<String> fileCollectionTaskIds;

    private Map<Integer, Integer> taskIdIndices;

    private Map<String, FileCollectionTaskWrapper> oneMinuteRopTasks;

    /**
     * Initialize the bean
     */
    @PostConstruct
    public void init() {
        sentFileCollectionTasks = new HashMap<>();
        fileCollectionTaskIds = new HashSet<>();
        taskIdIndices = new HashMap<>();
        for (final RopPeriod ropPeriod : RopPeriod.values()) {
            taskIdIndices.put(ropPeriod.getDurationInSeconds(), 0);

        }
        oneMinuteRopTasks = new HashMap<>();
    }

    /**
     * set the sentFileCollectionTasks map with sent tasks
     *
     * @param ropInfo
     *            - ropInfo
     * @param sentTasks
     *            - sent FileCollectionTaskWrapper
     */
    public void setSentFileCollectionTasks(final String ropInfo, final List<FileCollectionTaskWrapper> sentTasks) {
        final Set<String> fileCollectionIds = new HashSet<>();
        for (final FileCollectionTaskWrapper fileCollectionTaskWrapper : sentTasks) {
            fileCollectionIds.add(fileCollectionTaskWrapper.getFileCollectionTaskRequest().getJobId());
        }
        final int index = getTaskListIndex(ropInfo);
        if (sentFileCollectionTasks.get(Integer.parseInt(ropInfo)) != null) {
            sentFileCollectionTasks.get(Integer.parseInt(ropInfo)).set(index, fileCollectionIds);
        } else {
            initSentFileCollectionTasksMap(ropInfo);
            sentFileCollectionTasks.get(Integer.parseInt(ropInfo)).set(index, fileCollectionIds);
        }
        logger.debug("Updated {} Tasks to handle JMS failover for rop: {}, Total task in memory:{}", fileCollectionIds.size(), ropInfo,
                getSentFileCollectionTasksSize(ropInfo));
    }

    /**
     * get the all sent file collection tasks
     *
     * @return List of FileCollectionTaskWrapper
     */
    public List<FileCollectionTaskWrapper> getAllSentFileCollectionTasks() {
        populateFileCollectionTaskIdsFromSentTasks();
        final List<FileCollectionTaskWrapper> sentTasks = new ArrayList<>();
        final Set<FileCollectionTaskWrapper> sentTasksset = new HashSet<>();
        if(!fileCollectionTaskIds.isEmpty()) {
            for (final String taskId : fileCollectionTaskIds) {
                if (fileCollectionTaskCache.getTask(taskId) != null) {
                    sentTasksset.add(fileCollectionTaskCache.getTask(taskId));
                } else if (oneMinuteRopTasks.get(taskId) != null) {
                    sentTasksset.add(oneMinuteRopTasks.get(taskId));
                }
            }
            sentTasksset.addAll(oneMinuteRopTasks.values());
        } else {
            logger.info("JMS failover tasks for the current ROP is empty. Retreiving tasks from cache");
            sentTasksset.addAll(fileCollectionTaskCache.getAllTasks());
        }
        sentTasks.addAll(sentTasksset);
        sorter.sortFileCollectionTaskRequests(sentTasks);
        cleanup();
        return sentTasks;
    }

    /**
     * get the all one minute rop file collection tasks from cache
     */
    public void fetchOneMinuteFileCollectionTasksFromCache() {
        final Set<FileCollectionTaskWrapper> fileCollectiontaskwrappers = fileCollectionTaskCache.getAllTasks();
        for (final FileCollectionTaskWrapper fileCollectionTaskWrapper : fileCollectiontaskwrappers) {
            if (fileCollectionTaskWrapper.getRopTimeInfo().getRopTimeInSeconds() == RopPeriod.ONE_MIN.getDurationInSeconds()) {
                oneMinuteRopTasks.put(fileCollectionTaskWrapper.getFileCollectionTaskRequest().getJobId(), fileCollectionTaskWrapper);
            }
        }
        logger.debug("one minute rop fileCollectionTaskIds: {}", oneMinuteRopTasks.keySet());
    }

    /**
     * clean the one minute file collection tasks map
     */
    private void cleanup() {
        oneMinuteRopTasks.clear();
    }

    /**
     * get the index from the list of FileCollectionTask ids
     *
     * @param ropInfo
     *            - ropInfo
     * @return Index of List
     */
    private Integer getTaskListIndex(final String ropInfo) {
        Integer listIndex = 0;
        Integer index = taskIdIndices.get(Integer.parseInt(ropInfo));
        index++;
        if (index == Integer.MIN_VALUE) {
            index = 0;
        }
        taskIdIndices.put(Integer.parseInt(ropInfo), index);
        if (Long.parseLong(ropInfo) == RopPeriod.ONE_MIN.getDurationInSeconds()) {
            listIndex = index % ONE_MINUTES_ROP_SIZE;
        } else if (Long.parseLong(ropInfo) == RopPeriod.ONE_DAY.getDurationInSeconds()) {
            listIndex = index % ONE_DAY_ROP_SIZE;
        } else {
            listIndex = index % FIFTEEN_MINUTES_ROP_SIZE;
        }
        logger.debug("File Collection Task List Index is: {} for rop: {}", listIndex, ropInfo);
        return listIndex;
    }

    /**
     * get the total number of tasks for given rop period
     *
     * @param ropInfo
     *            - ropInfo
     * @return size of tasklist
     */
    private int getSentFileCollectionTasksSize(final String ropInfo) {
        int size = 0;
        final List<Set<String>> taskList = sentFileCollectionTasks.get(Integer.parseInt(ropInfo));
        for (final Set<String> taskSet : taskList) {
            size = size + taskSet.size();
        }
        return size;
    }

    /**
     * Initialize the sentFileCollectionTasks
     *
     * @param ropInfo
     *            - ropInfo
     * @return void
     */
    private void initSentFileCollectionTasksMap(final String ropInfo) {
        final List<Set<String>> taskIdLists = new ArrayList<>();
        if (Long.parseLong(ropInfo) == RopPeriod.ONE_MIN.getDurationInSeconds()) {
            for (int index = 0; index < ONE_MINUTES_ROP_SIZE; index++) {
                taskIdLists.add(new HashSet<String>());
            }
        } else if (Long.parseLong(ropInfo) == RopPeriod.ONE_DAY.getDurationInSeconds()) {
            for (int index = 0; index < ONE_DAY_ROP_SIZE; index++) {
                taskIdLists.add(new HashSet<String>());
            }
        } else {
            for (int index = 0; index < FIFTEEN_MINUTES_ROP_SIZE; index++) {
                taskIdLists.add(new HashSet<String>());
            }
        }
        sentFileCollectionTasks.put(Integer.parseInt(ropInfo), taskIdLists);
    }

    /**
     * get the all sent file collection task id
     *
     * @return Set of sent filecollectiontaskid
     */
    private Set<String> populateFileCollectionTaskIdsFromSentTasks() {
        final Set<String> sentTasksIds = new HashSet<>();
        for (final Entry<Integer, List<Set<String>>> entry : sentFileCollectionTasks.entrySet()) {
            final List<Set<String>> taskIdsList = entry.getValue();
            for (final Set<String> taskIds : taskIdsList) {
                sentTasksIds.addAll(taskIds);
            }
        }
        fileCollectionTaskIds = sentTasksIds;
        logger.debug("All fileCollectionTaskIds: {}", fileCollectionTaskIds.size());
        return sentTasksIds;
    }
}
