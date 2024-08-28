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

import java.util.List;
import javax.ejb.Singleton;
import javax.ejb.Startup;
import javax.inject.Inject;

import org.slf4j.Logger;

import com.ericsson.oss.pmic.dto.subscription.enums.SubscriptionType;
import com.ericsson.oss.services.pm.initiation.config.listener.CellTraceSymlinkConfigChangeListener;
import com.ericsson.oss.services.pm.initiation.config.listener.EbmSymlinkConfigChangeListener;

/**
 * Singleton bean responsible for removing symbolic links older than the
 * symbolic link retention period from NFS share (defaults are found in
 * PMICEbmSymlinkModelledConfiguration,
 * PMICCellTraceSymlinkModelledConfiguration or
 * PMICStatsSymlinkModelledConfiguration). If current node is master node, it
 * will execute the performCleanup method a minute after startup, and every
 * interval duration after that (default is found in PMICModeledConfiguration).
 * If the interval duration attribute is changed, then performCleanup will be
 * executed at the end of the current interval, then the timer will be reset
 * with the new interval value.
 */
@Singleton
@Startup
public class EbmSymlinkDeletionSchedulerBean extends AbstractFileDeletionScheduler {

    @Inject
    private EbmSymlinkConfigChangeListener ebmSymlinkConfigChangeListener;

    @Inject
    private CellTraceSymlinkConfigChangeListener eventsSymlinkConfigChangeListener;

    @Inject
    private Logger logger;

    /**
     * {@inheritDoc}
     */
    @Override
    public SubscriptionType getSubscriptionType() {
        return SubscriptionType.EBM;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isDeletionFromFLSRequired() {
        return false;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Integer getDeletionInterval() {
        return ebmSymlinkConfigChangeListener.getPmicEbmSymbolicLinkDeletionIntervalInMinutes();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Integer getRetentionPeriod() {
        return ebmSymlinkConfigChangeListener.getPmicEbmSymbolicLinkRetentionPeriodInMinutes();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<String> getDestinationDirectories() {
        final String symlinkDirectory = formSymbolicLinkPath();
        return prepareDestinationDirectoriesForSymlink(symlinkDirectory);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void logFileDeletion(final Integer retentionPeriodInMinutes, final String destinationDirectory) {
        logger.info("DESTINATION DIRECTORY: {}. Removing EBM symlinks from NFS Share older than {} minutes",
                destinationDirectory, retentionPeriodInMinutes);
    }

    @Override
    public boolean isDeletionOfEmptyDirectoryRequired() {
        return false;
    }

    private String formSymbolicLinkPath() {
        return pathValidator.formSymbolicLinkPath(eventsSymlinkConfigChangeListener.getPmicEventsSymbolicLinkVolume(),
                ebmSymlinkConfigChangeListener.getPmicEbmSymbolicLinkBaseDir());
    }

}
