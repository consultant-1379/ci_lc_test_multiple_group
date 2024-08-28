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

package com.ericsson.oss.services.pm.collection.cache;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import javax.annotation.PostConstruct;
import javax.cache.Cache;
import javax.cache.Cache.Entry;
import javax.ejb.Lock;
import javax.ejb.LockType;
import javax.ejb.Singleton;
import javax.inject.Inject;

import org.slf4j.Logger;

import com.ericsson.oss.itpf.sdk.cache.annotation.NamedCache;
import com.ericsson.oss.services.pm.collection.cache.utils.FileCollectionSorter;
import com.ericsson.oss.services.pm.initiation.cache.model.value.FileCollectionTaskWrapper;

/**
 * This class acts as a wrapper for PMICFileCollectionTaskCache
 * Should not change this class, as wee need to handle synchronize during cache operation
 */
@Singleton
@Lock(LockType.READ)
public class FileCollectionTaskCacheWrapper {

    private final AtomicInteger cacheSize = new AtomicInteger();
    @Inject
    @NamedCache("PMICFileCollectionTaskCache")
    private Cache<String, FileCollectionTaskWrapper> cache;
    @Inject
    private Logger logger;
    @Inject
    private FileCollectionSorter sorter;

    /**
     * Retrieves the initial size of the cache.
     */
    @PostConstruct
    public void init() {
        initSize();
    }

    private void initSize() {
        final int size = getAllTasks().size();
        cacheSize.set(size);
    }

    /**
     * @param value
     *            {@link FileCollectionTaskWrapper} to be added
     */
    public void addTask(final FileCollectionTaskWrapper value) {
        logger.debug("Adding following task to cache {}", value);
        if (cache.putIfAbsent(value.getFileCollectionTaskRequest().getJobId(), value)) {
            logger.debug("Following task has been added to cache {}", value.getFileCollectionTaskRequest());
            cacheSize.incrementAndGet();
        }
    }

    /**
     * @param key
     *            - Key to be removed from cache
     * @return true if the task was removed
     */
    public boolean removeTask(final String key) {
        if (cache.remove(key)) {
            cacheSize.decrementAndGet();
            logger.debug("Removed FileCollectionTaskId :{}", key);
            return true;
        } else {
            logger.debug("Could not remove FileCollectionTaskId :{}", key);
        }
        return false;

    }

    /**
     * @param keys
     *            - Remove all matching keys
     */
    public void removeTasks(final Set<String> keys) {
        logger.info("Removing FileCollectionTasks :{}", keys.size());
        for (final String key : keys) {
            removeTask(key);
        }
    }

    /**
     * @param key
     *            - Find {@link FileCollectionTaskWrapper} for matching key
     * @return - instance of {@link FileCollectionTaskWrapper}
     */
    public FileCollectionTaskWrapper getTask(final String key) {
        return cache.get(key);
    }

    /**
     * @return - All {@link FileCollectionTaskWrapper} in the cache
     */
    public Set<FileCollectionTaskWrapper> getAllTasks() {
        final Iterator<Entry<String, FileCollectionTaskWrapper>> cacheIterator = cache.iterator();
        final Set<FileCollectionTaskWrapper> tasks = new HashSet<>();
        while (cacheIterator.hasNext()) {
            final Entry<String, FileCollectionTaskWrapper> cacheEntry = cacheIterator.next();
            tasks.add(cacheEntry.getValue());
        }
        return tasks;
    }

    /**
     * Returns a list of sorted {@link FileCollectionTaskWrapper} objects. The sorting order depends on SubscriptionType of individual
     * {@link com.ericsson.oss.services.pm.collection.tasks.FileCollectionTaskRequest} as defined in
     * {@link FileCollectionSorter}
     *
     * @return A list of sorted {@link FileCollectionTaskWrapper} objects
     */
    public List<FileCollectionTaskWrapper> getAllTasksSorted() {
        final Iterator<Entry<String, FileCollectionTaskWrapper>> cacheIterator = cache.iterator();
        final List<FileCollectionTaskWrapper> tasks = new ArrayList<>();
        while (cacheIterator.hasNext()) {
            final Entry<String, FileCollectionTaskWrapper> cacheEntry = cacheIterator.next();
            tasks.add(cacheEntry.getValue());
        }
        sorter.sortFileCollectionTaskRequests(tasks);
        return tasks;
    }

    /**
     * Returns a list of sorted {@link FileCollectionTaskWrapper} objects. The sorting order depends on SubscriptionType of individual
     * {@link com.ericsson.oss.services.pm.collection.tasks.FileCollectionTaskRequest} as defined in
     * {@link FileCollectionSorter}
     *
     * @param ropPeriodInSec
     *            - requested rop period in seconds
     * @return A list of sorted {@link FileCollectionTaskWrapper} objects
     */
    public List<FileCollectionTaskWrapper> getAllTasksSorted(final int ropPeriodInSec) {
        final Iterator<Entry<String, FileCollectionTaskWrapper>> cacheIterator = cache.iterator();
        final List<FileCollectionTaskWrapper> tasks = new ArrayList<>();
        while (cacheIterator.hasNext()) {
            final Entry<String, FileCollectionTaskWrapper> cacheEntry = cacheIterator.next();
            final FileCollectionTaskWrapper task = cacheEntry.getValue();
            if (ropPeriodInSec == task.getRopTimeInfo().getRopTimeInSeconds()) {
                logger.trace("Found valid FileCollectionTaskWrapper: {}", task);
                tasks.add(cacheEntry.getValue());
            }
        }
        sorter.sortFileCollectionTaskRequests(tasks);
        return tasks;
    }

    /**
     * @return - size of cache.
     */
    public int size() {
        return cacheSize.get();
    }
}
