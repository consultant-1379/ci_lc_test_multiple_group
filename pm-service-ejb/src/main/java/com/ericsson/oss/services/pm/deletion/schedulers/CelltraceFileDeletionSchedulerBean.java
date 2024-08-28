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

package com.ericsson.oss.services.pm.deletion.schedulers;

import java.util.ArrayList;
import java.util.List;
import javax.ejb.Singleton;
import javax.ejb.Startup;
import javax.inject.Inject;

import org.slf4j.Logger;

import com.ericsson.oss.pmic.dto.subscription.enums.SubscriptionType;
import com.ericsson.oss.services.pm.initiation.config.listener.ConfigurationChangeListener;

/**
 * Singleton bean responsible for removing celltrace files older than the file retention period from NFS share (default is found in
 * PMICModeledConfiguration). If current node is master node, it will execute the performCleanup method a minute after startup, and every interval
 * duration after that (default is found in PMICModeledConfiguration). If the interval duration attribute is changed, then performCleanup will be
 * executed at the end of the current interval, then the timer will be reset with the new interval value.
 *
 * @author ekalkur
 */
@Singleton
@Startup
public class CelltraceFileDeletionSchedulerBean extends AbstractFileDeletionScheduler {

    private static final String CELLTRACE = "CELLTRACE";
    private static final List<String> validDataTypes = new ArrayList<>();

    static {
        for (final CellTraceDataType value : CellTraceDataType.values()) {
            validDataTypes.add(value.toString());
        }
    }

    @Inject
    private ConfigurationChangeListener configurationChangeListener;
    @Inject
    private Logger logger;

    /*
     * (non-Javadoc)
     * @see com.ericsson.oss.services.pm.deletion.schedulers.AbstractFileDeletionScheduler#getSubscriptionType()
     */
    @Override
    public SubscriptionType getSubscriptionType() {
        return SubscriptionType.CELLTRACE;
    }

    /*
     * (non-Javadoc)
     * @see com.ericsson.oss.services.pm.deletion.schedulers.AbstractFileDeletionScheduler#isDeletionFromFLSRequired()
     */
    @Override
    public boolean isDeletionFromFLSRequired() {
        return true;
    }

    /*
     * (non-Javadoc)
     * @see com.ericsson.oss.services.pm.deletion.schedulers.AbstractFileDeletionScheduler#getDeleteionInterval()
     */
    @Override
    public Integer getDeletionInterval() {
        return configurationChangeListener.getPmicCelltraceFileDeletionIntervalInMinutes();
    }

    /*
     * (non-Javadoc)
     * @see com.ericsson.oss.services.pm.deletion.schedulers.AbstractFileDeletionScheduler#getRetentionPeriod()
     */
    @Override
    public Integer getRetentionPeriod() {
        return configurationChangeListener.getPmicCelltraceFileRetentionPeriodInMinutes();
    }

    /*
     * (non-Javadoc)
     * @see com.ericsson.oss.services.pm.deletion.schedulers.AbstractFileDeletionScheduler#systemLogFileDeletion(java.lang.Integer, java.lang.String)
     */
    @Override
    protected void logFileDeletion(final Integer retentionPeriodInMinutes, final String destinationDirectory) {
        logger.info("DESTINATION DIRECTORY: {}. Removing Celltrace files from NFS Share older than {} minutes",
                destinationDirectory, retentionPeriodInMinutes);

    }

    /*
     * (non-Javadoc)
     * @see com.ericsson.oss.services.pm.deletion.schedulers.AbstractFileDeletionScheduler#getDestinationDirectory()
     */
    @Override
    public List<String> getDestinationDirectories() {
        return prepareDestinationDirectoriesForFiles(CELLTRACE);
    }

    /*
     * (non-Javadoc)
     * @see com.ericsson.oss.services.pm.deletion.schedulers.AbstractFileDeletionScheduler#isDeletionOfEmptyDirectoryRequired()
     */
    @Override
    public boolean isDeletionOfEmptyDirectoryRequired() {
        return true;
    }

    /*
     * (non-Javadoc)
     *
     * @see com.ericsson.oss.services.pm.deletion.schedulers.AbstractFileDeletionScheduler#isSubscriptionHasMultiptleDataTypes()
     */
    @Override
    public boolean isSubscriptionHasMultiptleDataTypes() {
        return true;
    }

    /*
     * (non-Javadoc)
     *
     * @see com.ericsson.oss.services.pm.deletion.schedulers.AbstractFileDeletionScheduler#getDataTypesForSubscription()
     */
    @Override
    public List<String> getDataTypesForSubscription() {
        return validDataTypes;
    }

    /*
     * This enum lists the dataTypes valid for RadioNode CELLTRACE type Subscription
     */
    public enum CellTraceDataType {
        CELLTRACE,
        CELLTRACE_CUCP,
        CELLTRACE_CUUP,
        CELLTRACE_DU
    }

}
