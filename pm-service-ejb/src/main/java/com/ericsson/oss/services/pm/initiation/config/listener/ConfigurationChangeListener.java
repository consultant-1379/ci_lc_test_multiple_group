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

package com.ericsson.oss.services.pm.initiation.config.listener;

import static com.ericsson.oss.pmic.api.collection.constants.FileCollectionModelledConfigConstants.PROP_PMIC_NFS_SHARE;
import static com.ericsson.oss.pmic.api.collection.constants.FileCollectionModelledConfigConstants.PROP_PMIC_NFS_SHARE_LIST;
import static com.ericsson.oss.pmic.api.collection.constants.FileCollectionModelledConfigConstants.PROP_STARTUP_RECOVERY_HOURS;
import static com.ericsson.oss.pmic.api.constants.ModelledConfigurationConstants.ContinuousCelltrace.PROP_CCTR_SUBSCRIPTION_ENABLED;
import static com.ericsson.oss.pmic.api.constants.ModelledConfigurationConstants.PROP_INITIATION_TIMEOUT_IN_MILLIS;
import static com.ericsson.oss.pmic.api.constants.ModelledConfigurationConstants.PROP_MIGRATION_ENABLED;
import static com.ericsson.oss.pmic.api.constants.ModelledConfigurationConstants.PROP_SCANNER_POLLING_INTERVAL_IN_MINUTES;
import static com.ericsson.oss.pmic.api.constants.ModelledConfigurationConstants.PROP_SYSTEM_DEFINED_SUBSCRIPTION_AUDIT_INTERVAL;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Observes;
import javax.inject.Inject;

import org.slf4j.Logger;

import com.ericsson.oss.itpf.sdk.config.annotation.ConfigurationChangeNotification;
import com.ericsson.oss.itpf.sdk.config.annotation.Configured;
import com.ericsson.oss.pmic.api.collection.constants.FileCollectionModelledConfigConstants;
import com.ericsson.oss.pmic.api.collection.constants.FileCollectionModelledConfigConstants.Celltrace;
import com.ericsson.oss.pmic.api.collection.constants.FileCollectionModelledConfigConstants.UeTrace;
import com.ericsson.oss.services.pm.collection.mountpoints.DestinationMountPointConfigSource;
import com.ericsson.oss.services.pm.common.scheduling.CreateSchedulingServiceTimerException;
import com.ericsson.oss.services.pm.deletion.schedulers.CelltraceFileDeletionSchedulerBean;
import com.ericsson.oss.services.pm.initiation.util.constants.TimeConstants;
import com.ericsson.oss.services.pm.scheduling.impl.ScannerPollSyncScheduler;
import com.ericsson.oss.services.pm.scheduling.impl.SystemDefinedSubscriptionAuditScheduler;

/**
 * Listener for Configuration parameter (PIB) changes.
 */
@ApplicationScoped
@SuppressWarnings("PMD")
public class ConfigurationChangeListener extends AbstractConfigurationChangeListener {
    private static final String DEBUG_RESET_TIMER_MESSAGE = "Calling reset timer with new interval: {} minutes";

    @Inject
    @Configured(propertyName = PROP_STARTUP_RECOVERY_HOURS)
    private Integer startupFileRecoveryHoursInfo;

    @Inject
    @Configured(propertyName = PROP_PMIC_NFS_SHARE_LIST)
    private String pmicNfsShareList;

    /**
     * @deprecated pmicNfsShare is being replaced by pmicNfsShareList. pmicNfsShare takes only one value whereas pmicNfsShareList takes multiple
     *             parameters hence can accommodate multiple mount points.
     */
    @Deprecated
    @Inject
    @Configured(propertyName = PROP_PMIC_NFS_SHARE)
    private String pmicNfsShare;

    @Inject
    @Configured(propertyName = FileCollectionModelledConfigConstants.Celltrace.PROP_FILE_RETENTION_PERIOD_IN_MINUTES)
    private Integer pmicCelltraceFileRetentionPeriodInMinutes;

    @Inject
    @Configured(propertyName = FileCollectionModelledConfigConstants.Celltrace.PROP_FILE_DELETION_INTERVAL_IN_MINUTES)
    private Integer pmicCelltraceFileDeletionIntervalInMinutes;

    @Inject
    @Configured(propertyName = PROP_SCANNER_POLLING_INTERVAL_IN_MINUTES)
    private Integer scannerPollingIntervalMinutes;

    @Inject
    @Configured(propertyName = PROP_INITIATION_TIMEOUT_IN_MILLIS)
    private Long initiationTimeoutInMillis;

    @Inject
    @Configured(propertyName = PROP_MIGRATION_ENABLED)
    private Boolean pmMigrationEnabled;

    @Inject
    @Configured(propertyName = PROP_CCTR_SUBSCRIPTION_ENABLED)
    private Boolean cctrSubscriptionEnabled;

    @Inject
    @Configured(propertyName = FileCollectionModelledConfigConstants.UeTrace.PROP_COLLECTION_ENABLED)
    private Boolean ueTraceCollectionEnabled;

    @Inject
    @Configured(propertyName = PROP_SYSTEM_DEFINED_SUBSCRIPTION_AUDIT_INTERVAL)
    private Long sysDefSubscriptionAuditInterval;

    @Inject
    private Logger logger;

    @Inject
    private ScannerPollSyncScheduler masterPollingSchedulerBean;

    @Inject
    private CelltraceFileDeletionSchedulerBean celltraceFileDeletionSchedulerBean;

    @Inject
    private DestinationMountPointConfigSource destinationMountPointConfigSource;

    @Inject
    private SystemDefinedSubscriptionAuditScheduler sysDefSubscriptionAuditScheduler;

    /**
     * Listens for PROP_FILE_RECOVERY_HOURS changes.
     *
     * @param ropInfo
     *            - Record Output Period info to update fileRecoveryHoursInfo object
     */
    void listenForStartupRecoveryHoursInfoChanges(
            @Observes @ConfigurationChangeNotification(propertyName = PROP_STARTUP_RECOVERY_HOURS) final Integer ropInfo) {
        logChange(PROP_STARTUP_RECOVERY_HOURS, startupFileRecoveryHoursInfo, ropInfo);
        startupFileRecoveryHoursInfo = ropInfo;
    }

    /**
     * Listens for PROP_PMIC_NFS_SHARE_LIST changes.
     *
     * @param newValueForPmicNfsShareList
     *            - new value for PMIC Network File System Share list
     */
    void listenForPmicNfsShareList(
            @Observes @ConfigurationChangeNotification(propertyName = PROP_PMIC_NFS_SHARE_LIST) final String newValueForPmicNfsShareList) {
        logChange(PROP_PMIC_NFS_SHARE_LIST, pmicNfsShareList, newValueForPmicNfsShareList);
        pmicNfsShareList = newValueForPmicNfsShareList;
        destinationMountPointConfigSource.reload();
    }

    /**
     * @param pmicNfsShare
     *            - pmic network file system share location
     * @deprecated pmicNfsShare is being replaced by pmicNfsShareList. Any update made to pmicNfsShare will change pmicNfsShareList.
     */
    @Deprecated
    void listenForNfsShareChanges(
            @Observes @ConfigurationChangeNotification(propertyName = PROP_PMIC_NFS_SHARE) final String pmicNfsShare) {
        logChange(PROP_PMIC_NFS_SHARE, pmicNfsShareList, pmicNfsShare);
        pmicNfsShareList = pmicNfsShare;
    }

    /**
     * Listens for PROP_MIGRATION_ENABLED changes.
     *
     * @param pmMigrationEnabled
     *            - boolean value, true if migration is enabled
     */
    void listenForPmMigrationEnabledChanges(
            @Observes @ConfigurationChangeNotification(propertyName = PROP_MIGRATION_ENABLED) final Boolean pmMigrationEnabled) {
        logChange(PROP_MIGRATION_ENABLED, this.pmMigrationEnabled, pmMigrationEnabled);
        this.pmMigrationEnabled = pmMigrationEnabled;
    }

    /**
     * Listens for PROP_FILE_RETENTION_PERIOD_IN_MINUTES changes.
     *
     * @param pmicCelltraceFileRetentionPeriodInMinutes
     *            - file retention period in minutes for Celltrace files
     */
    void listenForPmicCelltraceFileRetentionPeriodInMinutesChanges(
            @Observes @ConfigurationChangeNotification(
                    propertyName = FileCollectionModelledConfigConstants.Celltrace.PROP_FILE_RETENTION_PERIOD_IN_MINUTES)
                            final Integer pmicCelltraceFileRetentionPeriodInMinutes) {
        logChange(FileCollectionModelledConfigConstants.Celltrace.PROP_FILE_RETENTION_PERIOD_IN_MINUTES,
                this.pmicCelltraceFileRetentionPeriodInMinutes, pmicCelltraceFileRetentionPeriodInMinutes);
        this.pmicCelltraceFileRetentionPeriodInMinutes = pmicCelltraceFileRetentionPeriodInMinutes;
    }

    /**
     * Listens for PROP_FILE_DELETION_INTERVAL_IN_MINUTES changes.
     *
     * @param pmicCelltraceFileDeletionIntervalInMinutes
     *            - file deletion interval in minutes for Celltrace files
     */
    void listenForPmicCelltraceFileDeletionIntervalInMinutesChanges(
            @Observes @ConfigurationChangeNotification(
                    propertyName = Celltrace.PROP_FILE_DELETION_INTERVAL_IN_MINUTES) final Integer pmicCelltraceFileDeletionIntervalInMinutes) {
        logChange(Celltrace.PROP_FILE_DELETION_INTERVAL_IN_MINUTES,
                this.pmicCelltraceFileDeletionIntervalInMinutes, pmicCelltraceFileDeletionIntervalInMinutes);
        this.pmicCelltraceFileDeletionIntervalInMinutes = pmicCelltraceFileDeletionIntervalInMinutes;
        logger.debug(DEBUG_RESET_TIMER_MESSAGE, pmicCelltraceFileDeletionIntervalInMinutes);
        celltraceFileDeletionSchedulerBean.resetTimer(pmicCelltraceFileDeletionIntervalInMinutes);
    }

    /**
     * Listens for PROP_INITIATION_TIMEOUT_IN_MILLIS changes.
     *
     * @param initiationTimeoutInMillis
     *            - initiation timeout in milliseconds
     */
    void listenForInitiationTimeoutChanges(
            @Observes @ConfigurationChangeNotification(propertyName = PROP_INITIATION_TIMEOUT_IN_MILLIS) final Long initiationTimeoutInMillis) {
        logChange(PROP_INITIATION_TIMEOUT_IN_MILLIS, this.initiationTimeoutInMillis, initiationTimeoutInMillis);
        this.initiationTimeoutInMillis = initiationTimeoutInMillis;
    }

    /**
     * Listens for PROP_SCANNER_POLLING_INTERVAL_IN_MINUTES changes.
     *
     * @param scannerPollingIntervalMinutes
     *            - scanner polling interval in minutes
     * @throws CreateSchedulingServiceTimerException
     *             - thrown if timer cannot be created
     */
    void listenForScannerPollingTimeChanges(
            @Observes @ConfigurationChangeNotification(
                    propertyName = PROP_SCANNER_POLLING_INTERVAL_IN_MINUTES) final Integer scannerPollingIntervalMinutes)
            throws CreateSchedulingServiceTimerException {
        logChange(PROP_SCANNER_POLLING_INTERVAL_IN_MINUTES, this.scannerPollingIntervalMinutes, scannerPollingIntervalMinutes);

        final Long oldScannerPollingInterval = this.scannerPollingIntervalMinutes * TimeConstants.ONE_MINUTE_IN_MILLISECONDS;
        final Long newScannerPollingInterval = scannerPollingIntervalMinutes * TimeConstants.ONE_MINUTE_IN_MILLISECONDS;
        this.scannerPollingIntervalMinutes = scannerPollingIntervalMinutes;

        masterPollingSchedulerBean.resetIntervalTimer(oldScannerPollingInterval, newScannerPollingInterval);
    }

    /**
     * Listens for PROP_CCTR_SUBSCRIPTION_ENABLED changes.
     *
     * @param cctrSubscriptionEnabled
     *            - boolean value, true if Continuous Celltrace Subscription is enabled
     */
    void listenForCctrSubscriptionEnabledChanges(@Observes @ConfigurationChangeNotification(
            propertyName = PROP_CCTR_SUBSCRIPTION_ENABLED) final Boolean cctrSubscriptionEnabled) {
        logChange(PROP_CCTR_SUBSCRIPTION_ENABLED, this.cctrSubscriptionEnabled,
                cctrSubscriptionEnabled);
        this.cctrSubscriptionEnabled = cctrSubscriptionEnabled;
    }

    /**
     * Listens for PROP_COLLECTION_ENABLED changes.
     *
     * @param ueTraceCollectionEnabled
     *            UeTrace File Collection enabled
     */
    void listenForPmUeTraceCollectionEnabledChanges(
            @Observes @ConfigurationChangeNotification(propertyName = UeTrace.PROP_COLLECTION_ENABLED) final Boolean ueTraceCollectionEnabled) {
        logChange(UeTrace.PROP_COLLECTION_ENABLED, this.ueTraceCollectionEnabled,
                ueTraceCollectionEnabled);
        this.ueTraceCollectionEnabled = ueTraceCollectionEnabled;
    }

    /**
     * Listens for PROP_SYSTEM_DEFINED_SUBSCRIPTION_AUDIT_INTERVAL changes.
     *
     * @param sysDefSubscriptionAuditInterval
     *            System defined subscription audit interval
     * @throws CreateSchedulingServiceTimerException
     *             - thrown if timer cannot be created
     */
    void listenForCTUMAuditScheduleInterval(
            @Observes @ConfigurationChangeNotification(
                    propertyName = PROP_SYSTEM_DEFINED_SUBSCRIPTION_AUDIT_INTERVAL) final long sysDefSubscriptionAuditInterval)
            throws CreateSchedulingServiceTimerException {
        logChange(PROP_SYSTEM_DEFINED_SUBSCRIPTION_AUDIT_INTERVAL, this.sysDefSubscriptionAuditInterval, sysDefSubscriptionAuditInterval);
        final long newCctrScheduleTimerInterval = sysDefSubscriptionAuditInterval * TimeConstants.ONE_MINUTE_IN_MILLISECONDS;
        final long oldCctrScheduleTimerInterval = this.sysDefSubscriptionAuditInterval * TimeConstants.ONE_MINUTE_IN_MILLISECONDS;
        sysDefSubscriptionAuditScheduler.resetIntervalTimer(oldCctrScheduleTimerInterval, newCctrScheduleTimerInterval);
        this.sysDefSubscriptionAuditInterval = sysDefSubscriptionAuditInterval;
    }

    public Integer getStartupFileRecoveryHoursInfo() {
        return startupFileRecoveryHoursInfo;
    }

    public String getPmicNfsShareList() {
        return pmicNfsShareList;
    }

    public Integer getPmicCelltraceFileRetentionPeriodInMinutes() {
        return pmicCelltraceFileRetentionPeriodInMinutes;
    }

    public Integer getPmicCelltraceFileDeletionIntervalInMinutes() {
        return pmicCelltraceFileDeletionIntervalInMinutes;
    }

    public Long getInitiationTimeout() {
        return initiationTimeoutInMillis;
    }

    public Integer getScannerPollingIntervalMinutes() {
        return scannerPollingIntervalMinutes;
    }

    public Boolean getPmMigrationEnabled() {
        return pmMigrationEnabled;
    }

    /**
     * It is used to know if the ueTrace Collection is enabled.
     * @return - returns true if ueTrace Collection is enabled
     */
    public Boolean getUeTraceCollectionEnabled() {
        return ueTraceCollectionEnabled;
    }

    /**
     * It is used to get the sysDefSubscriptionAuditInterval.
     * @return the sysDefSubscriptionAuditInterval
     */
    public long getSysDefSubscriptionAuditInterval() {
        return sysDefSubscriptionAuditInterval;
    }

}
