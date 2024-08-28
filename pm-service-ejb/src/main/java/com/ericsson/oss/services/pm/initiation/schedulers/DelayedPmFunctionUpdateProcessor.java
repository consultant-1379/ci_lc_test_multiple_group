/*------------------------------------------------------------------------------
 *******************************************************************************
 * COPYRIGHT Ericsson 2018
 *
 * The copyright to the computer program(s) herein is the property of
 * Ericsson Inc. The programs may be used and/or copied only with written
 * permission from Ericsson Inc. or in accordance with the terms and
 * conditions stipulated in the agreement/contract under which the
 * program(s) have been supplied.
 *******************************************************************************
 *----------------------------------------------------------------------------*/

package com.ericsson.oss.services.pm.initiation.schedulers;

import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import javax.annotation.PostConstruct;
import javax.ejb.ConcurrencyManagement;
import javax.ejb.ConcurrencyManagementType;
import javax.ejb.Singleton;
import javax.ejb.Timeout;
import javax.ejb.TimerConfig;
import javax.ejb.TimerService;
import javax.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ericsson.oss.pmic.api.cache.PmFunctionData;
import com.ericsson.oss.pmic.util.TimeGenerator;
import com.ericsson.oss.services.pm.initiation.notification.DpsPmEnabledUpdateNotificationProcessor;

/**
 * This class performs processing for the given PmFunction AVC Update data after a timeout. The reason for the timeout is to prevent the
 * validation taking place many times if many notifications are received simultaneously
 */
@Singleton
@ConcurrencyManagement(ConcurrencyManagementType.BEAN)
public class DelayedPmFunctionUpdateProcessor {
    private static final Logger logger = LoggerFactory.getLogger(DelayedPmFunctionUpdateProcessor.class);
    private static final int MAX_SIZE_FOR_PROCESSING_BATCH = 50;
    private static final int TIMER_INTERVAL_IN_SECONDS = 8;
    private static final int DEFAULT_DELAY_IN_SECONDS = 10;
    private final Map<String, ScheduleData> pmFunctionDataAndDelay = Collections.synchronizedMap(new LinkedHashMap<>());

    @Inject
    private TimerService timerService;

    @Inject
    private TimeGenerator timeGenerator;

    @Inject
    private DpsPmEnabledUpdateNotificationProcessor pmUpdateNotificationProcessor;

    /**
     * Post construct to start a Timer
     */
    @PostConstruct
    public void postConstruct() {
        final TimerConfig timerConfig = new TimerConfig("DelayedPmFunctionUpdateProcessor", false);
        timerService.createIntervalTimer(TimeUnit.SECONDS.toMillis(TIMER_INTERVAL_IN_SECONDS), TimeUnit.SECONDS.toMillis(TIMER_INTERVAL_IN_SECONDS),
                timerConfig);
        logger.info("Timer DelayedPmFunctionUpdateProcessor Initiation Successful");
    }

    /**
     * Start a timer to validate the given PmFunctionData after a timeout
     *
     * @param nodeFdn
     *         - the fdn of the NetworkElement MO
     * @param avcPmFunctionData
     *         - avc PmFunctionData
     * @param oldPmFunctionData
     *         - old PmFunctionData
     */
    public synchronized void scheduleDelayedPmFunctionUpdateProcessor(final String nodeFdn, final PmFunctionData avcPmFunctionData,
                                                                      final PmFunctionData oldPmFunctionData) {
        final ScheduleData scheduleDataByNode = pmFunctionDataAndDelay.get(nodeFdn);
        final ScheduleData scheduleData = scheduleDataByNode != null
                ? scheduleDataByNode.add(avcPmFunctionData, oldPmFunctionData).reschedule()
                : new ScheduleData(avcPmFunctionData, oldPmFunctionData);
        pmFunctionDataAndDelay.put(nodeFdn, scheduleData);
    }

    /**
     * Start a timer to validate the given PmFunctionData after a timeout
     *                               *
     * @param nodeFdn
     *         - the fdn of the NetworkElement MO
     * @param rescheduledData
     *         - rescheduledData to be scheduled
     */
    private synchronized void scheduleDelayedPmFunctionUpdateProcessor(final String nodeFdn, final ScheduleData rescheduledData) {
        final ScheduleData scheduleDataByNode = pmFunctionDataAndDelay.get(nodeFdn);
        final ScheduleData scheduleData = scheduleDataByNode != null
                ? scheduleDataByNode.addAll(rescheduledData).reschedule()
                : new ScheduleData(rescheduledData);
        pmFunctionDataAndDelay.put(nodeFdn, scheduleData);
    }

    /**
     * Perform the processing
     */
    @Timeout
    public void processPmFunctionData() {
        if (pmFunctionDataAndDelay.isEmpty()) {
            return;
        }
        final Map<String, ScheduleData> pmFunctionDataAndDelayLocal;
        synchronized (pmFunctionDataAndDelay) {
            pmFunctionDataAndDelayLocal = new LinkedHashMap<>(pmFunctionDataAndDelay);
        }
        logger.info("Total Number of entries for pmFunctionData: {}", pmFunctionDataAndDelayLocal.size());
        int batchSize = MAX_SIZE_FOR_PROCESSING_BATCH;
        final Iterator<Map.Entry<String, ScheduleData>> entryIterator = pmFunctionDataAndDelayLocal.entrySet().iterator();
        while (entryIterator.hasNext() && batchSize >= 0) {
            final Map.Entry<String, ScheduleData> mapEntry = entryIterator.next();
            final String nodeFdn = mapEntry.getKey();
            final ScheduleData scheduleData = mapEntry.getValue();
            if (scheduleData.delay < timeGenerator.currentTimeMillis()) {
                entryIterator.remove();
                synchronized (pmFunctionDataAndDelay) {
                    pmFunctionDataAndDelay.remove(nodeFdn);
                    logger.debug("Number of outstanding entries in pmFunctionData local cache: {}", pmFunctionDataAndDelay.size());
                }
                batchSize = processScheduleData(scheduleData, nodeFdn, batchSize);
            }
        }
    }

    private int processScheduleData(final ScheduleData scheduleData, final String nodeFdn, final int batchSize) {
        int batchSizeLocal = batchSize;
        logger.debug("nodeFdn: {} - scheduleData: {}", nodeFdn, scheduleData);
        final Iterator<ScheduledEntry> entriesIterator = scheduleData.getScheduledEntries().iterator();
        while (entriesIterator.hasNext()) {
            if (--batchSizeLocal < 0) {
                scheduleDelayedPmFunctionUpdateProcessor(nodeFdn, scheduleData);
                break;
            }
            final ScheduledEntry entry = entriesIterator.next();
            try {
                pmUpdateNotificationProcessor.processPmFunctionChange(nodeFdn, entry.getAvcPmFunctionData(), entry.getOldPmFunctionData());
            } catch (final Exception e) {
                logger.error("Can not process update for node Fdn {}. Exception:{}", nodeFdn, e.getMessage());
                logger.info("Can not process update for node Fdn {}.", nodeFdn, e);
            } finally {
                entriesIterator.remove();
            }
        }
        return batchSizeLocal;
    }

    private class ScheduleData {
        private final List<ScheduledEntry> scheduledEntries = new LinkedList<>();
        private long delay;

        private ScheduleData(final PmFunctionData avcPmFunctionData, final PmFunctionData oldPmFunctionData) {
            delay = timeGenerator.currentTimeMillis() + TimeUnit.SECONDS.toMillis(DEFAULT_DELAY_IN_SECONDS);
            scheduledEntries.add(new ScheduledEntry(avcPmFunctionData, oldPmFunctionData));
        }

        private ScheduleData() {
            this(null, null);
        }

        private ScheduleData(final ScheduleData scheduleData) {
            delay = timeGenerator.currentTimeMillis() + TimeUnit.SECONDS.toMillis(DEFAULT_DELAY_IN_SECONDS);
            if (scheduleData != null) {
                scheduledEntries.addAll(scheduleData.getScheduledEntries());
            }
        }

        private ScheduleData add(final PmFunctionData avcPmFunctionData, final PmFunctionData oldPmFunctionData) {
            scheduledEntries.add(new ScheduledEntry(avcPmFunctionData, oldPmFunctionData));
            return this;
        }

        private ScheduleData addAll(final ScheduleData scheduleData) {
            if (scheduleData != null) {
                scheduledEntries.addAll(scheduleData.getScheduledEntries());
            }
            return this;
        }

        private ScheduleData reschedule() {
            delay = timeGenerator.currentTimeMillis() + TimeUnit.SECONDS.toMillis(DEFAULT_DELAY_IN_SECONDS);
            return this;
        }

        private List<ScheduledEntry> getScheduledEntries() {
            return scheduledEntries;
        }

        @Override
        public String toString() {
            return "[delay: " + delay + " - scheduledEntries: " + scheduledEntries + "]";
        }
    }

    private class ScheduledEntry {
        private final PmFunctionData avcPmFunctionData;
        private final PmFunctionData oldPmFunctionData;

        private ScheduledEntry(final PmFunctionData avcPmFunctionData, final PmFunctionData oldPmFunctionData) {
            this.avcPmFunctionData = avcPmFunctionData == null ? new PmFunctionData() : avcPmFunctionData;
            this.oldPmFunctionData = oldPmFunctionData == null ? new PmFunctionData() : oldPmFunctionData;
        }

        private ScheduledEntry() {
            this(null, null);
        }

        private PmFunctionData getAvcPmFunctionData() {
            return avcPmFunctionData;
        }

        private PmFunctionData getOldPmFunctionData() {
            return oldPmFunctionData;
        }

        @Override
        public String toString() {
            return "[avcPmFunctionData: " + avcPmFunctionData + " - oldPmFunctionData: " + oldPmFunctionData + "]";
        }
    }
}
