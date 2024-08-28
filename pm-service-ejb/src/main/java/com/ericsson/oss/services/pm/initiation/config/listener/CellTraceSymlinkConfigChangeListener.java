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

import static com.ericsson.oss.pmic.api.collection.constants.FileCollectionModelledConfigConstants.CelltraceSymlinks.PROP_MAX_SYMLINKS_ALLOWED;
import static com.ericsson.oss.pmic.api.collection.constants.FileCollectionModelledConfigConstants.CelltraceSymlinks.PROP_MAX_SYMLINK_SUB_DIRS;
import static com.ericsson.oss.pmic.api.collection.constants.FileCollectionModelledConfigConstants.CelltraceSymlinks.PROP_SYMLINK_BASE_DIR;
import static com.ericsson.oss.pmic.api.collection.constants.FileCollectionModelledConfigConstants.CelltraceSymlinks.PROP_SYMLINK_CREATION_ENABLED;
import static com.ericsson.oss.pmic.api.collection.constants.FileCollectionModelledConfigConstants.CelltraceSymlinks.PROP_SYMLINK_DELETION_INTERVAL_IN_MINUTES;
import static com.ericsson.oss.pmic.api.collection.constants.FileCollectionModelledConfigConstants.CelltraceSymlinks.PROP_SYMLINK_RETENTION_IN_MINUTES;
import static com.ericsson.oss.pmic.api.collection.constants.FileCollectionModelledConfigConstants.CelltraceSymlinks.PROP_SYMLINK_SUB_DIR_NAME_PREFIX;
import static com.ericsson.oss.pmic.api.collection.constants.FileCollectionModelledConfigConstants.CelltraceSymlinks.PROP_SYMLINK_TARGET_PREFIX;
import static com.ericsson.oss.pmic.api.collection.constants.FileCollectionModelledConfigConstants.CelltraceSymlinks.PROP_SYMLINK_VOLUME;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Observes;
import javax.inject.Inject;

import com.ericsson.oss.itpf.sdk.config.annotation.ConfigurationChangeNotification;
import com.ericsson.oss.itpf.sdk.config.annotation.Configured;
import com.ericsson.oss.services.pm.deletion.schedulers.CellTraceSymlinkDeletionSchedulerBean;

/**
 * Listener for Configuration parameter (PIB) changes
 */
@ApplicationScoped
public class CellTraceSymlinkConfigChangeListener extends AbstractConfigurationChangeListener {

    @Inject
    @Configured(propertyName = PROP_SYMLINK_VOLUME)
    private String pmicEventsSymbolicLinkVolume;

    @Inject
    @Configured(propertyName = PROP_SYMLINK_BASE_DIR)
    private String pmicEventsSymbolicLinkBaseDir;

    @Inject
    @Configured(propertyName = PROP_SYMLINK_SUB_DIR_NAME_PREFIX)
    private String pmicEventsSymbolicLinkSubdirNamePrefix;

    @Inject
    @Configured(propertyName = PROP_SYMLINK_TARGET_PREFIX)
    private String pmicEventsSymbolicLinkTargetPrefix;

    @Inject
    @Configured(propertyName = PROP_SYMLINK_CREATION_ENABLED)
    private Boolean pmicEventsSymbolicLinkCreationEnabled;

    @Inject
    @Configured(propertyName = PROP_MAX_SYMLINKS_ALLOWED)
    private Integer pmicEventsMaxSymbolicLinksAllowed;

    @Inject
    @Configured(propertyName = PROP_MAX_SYMLINK_SUB_DIRS)
    private Integer pmicEventsMaxSymbolicLinkSubdirs;

    @Inject
    @Configured(propertyName = PROP_SYMLINK_RETENTION_IN_MINUTES)
    private Integer pmicEventsSymbolicLinkRetentionPeriodInMinutes;

    @Inject
    @Configured(propertyName = PROP_SYMLINK_DELETION_INTERVAL_IN_MINUTES)
    private Integer pmicEventsSymbolicLinkDeletionIntervalInMinutes;

    @Inject
    private CellTraceSymlinkDeletionSchedulerBean symbolicLinkDeletionSchedulerBean;

    /**
     * @param pmicEventsSymbolicLinkVolume
     *         - path to celltrace symbolic link volume
     */
    void listenForPmicEventsSymbolicLinkVolumeChanges(
            @Observes @ConfigurationChangeNotification(propertyName = PROP_SYMLINK_VOLUME) final String pmicEventsSymbolicLinkVolume) {
        final String valueBefore = this.pmicEventsSymbolicLinkVolume;
        this.pmicEventsSymbolicLinkVolume = pathValidator.formAndValidatePath(this.pmicEventsSymbolicLinkVolume, pmicEventsSymbolicLinkVolume);
        validatePathForStringValue(PROP_SYMLINK_VOLUME, valueBefore, this.pmicEventsSymbolicLinkVolume);
    }

    /**
     * @param pmicEventsSymbolicLinkBaseDir
     *         - path to celltrace symbolic link base directory
     */
    void listenForPmicEventsSymbolicLinkBaseDirChanges(
            @Observes @ConfigurationChangeNotification(propertyName = PROP_SYMLINK_BASE_DIR) final String pmicEventsSymbolicLinkBaseDir) {
        final String valueBefore = this.pmicEventsSymbolicLinkBaseDir;
        this.pmicEventsSymbolicLinkBaseDir = pathValidator
                .formAndValidatePath(this.pmicEventsSymbolicLinkBaseDir, pmicEventsSymbolicLinkBaseDir);
        validatePathForStringValue(PROP_SYMLINK_BASE_DIR, valueBefore, this.pmicEventsSymbolicLinkBaseDir);
    }

    /**
     * @param pmicEventsSymbolicLinkSubdirNamePrefix
     *         - prefix value for celltrace symbolic link sub-directory name
     */
    void listenForPmicEventsSymbolicLinkSubdirNamePrefixChanges(@Observes @ConfigurationChangeNotification(
            propertyName = PROP_SYMLINK_SUB_DIR_NAME_PREFIX) final String pmicEventsSymbolicLinkSubdirNamePrefix) {
        logChange(PROP_SYMLINK_SUB_DIR_NAME_PREFIX, this.pmicEventsSymbolicLinkSubdirNamePrefix, pmicEventsSymbolicLinkSubdirNamePrefix);
        this.pmicEventsSymbolicLinkSubdirNamePrefix = pmicEventsSymbolicLinkSubdirNamePrefix;
    }

    /**
     * @param pmicEventsSymbolicLinkTargetPrefix
     *         - prefix value for celltrace symbolic link target directory name
     */
    void listenForPmicEventsSymbolicLinkTargetPrefixChanges(
            @Observes @ConfigurationChangeNotification(propertyName = PROP_SYMLINK_TARGET_PREFIX) final String pmicEventsSymbolicLinkTargetPrefix) {
        final String valueBefore = this.pmicEventsSymbolicLinkTargetPrefix;
        this.pmicEventsSymbolicLinkTargetPrefix = pathValidator.formAndValidatePath(this.pmicEventsSymbolicLinkTargetPrefix,
                pmicEventsSymbolicLinkTargetPrefix);
        validatePathForStringValue(PROP_SYMLINK_TARGET_PREFIX, valueBefore, this.pmicEventsSymbolicLinkTargetPrefix);
    }

    /**
     * @param pmicEventsSymbolicLinkCreationEnabled
     *         - boolean value to configure whether celltrace symbolic link creation is enabled
     */
    void listenForPmicEventsSymbolicLinkCreationEnabledChanges(@Observes @ConfigurationChangeNotification(
            propertyName = PROP_SYMLINK_CREATION_ENABLED) final Boolean pmicEventsSymbolicLinkCreationEnabled) {
        logChange(PROP_SYMLINK_CREATION_ENABLED, this.pmicEventsSymbolicLinkCreationEnabled, pmicEventsSymbolicLinkCreationEnabled);
        this.pmicEventsSymbolicLinkCreationEnabled = pmicEventsSymbolicLinkCreationEnabled;
    }

    /**
     * @param pmicEventsMaxSymbolicLinksAllowed
     *         - configuration value for the maximum number of celltrace symbolic links allowed
     */
    void listenForPmicEventsMaxSymbolicLinksAllowedChanges(
            @Observes @ConfigurationChangeNotification(propertyName = PROP_MAX_SYMLINKS_ALLOWED) final Integer pmicEventsMaxSymbolicLinksAllowed) {
        logChange(PROP_MAX_SYMLINKS_ALLOWED, this.pmicEventsMaxSymbolicLinksAllowed, pmicEventsMaxSymbolicLinksAllowed);
        this.pmicEventsMaxSymbolicLinksAllowed = pmicEventsMaxSymbolicLinksAllowed;
    }

    /**
     * @param pmicEventsMaxSymbolicLinkSubdirs
     *         - configuration value for the maximum number of celltrace symbolic link sub-directories allowed
     */
    void listenForPmicEventsMaxSymbolicLinkSubdirsChanges(
            @Observes @ConfigurationChangeNotification(propertyName = PROP_MAX_SYMLINK_SUB_DIRS) final Integer pmicEventsMaxSymbolicLinkSubdirs) {
        logChange(PROP_MAX_SYMLINK_SUB_DIRS, this.pmicEventsMaxSymbolicLinkSubdirs, pmicEventsMaxSymbolicLinkSubdirs);
        this.pmicEventsMaxSymbolicLinkSubdirs = pmicEventsMaxSymbolicLinkSubdirs;
    }

    /**
     * @param pmicEventsSymbolicLinkRetentionPeriodInMinutes
     *         - retention period for  celltrace symbolic links in minutes
     */
    void listenForPmicEventsSymbolicLinkRetentionPeriodInMinutesChanges(@Observes @ConfigurationChangeNotification(
            propertyName = PROP_SYMLINK_RETENTION_IN_MINUTES) final Integer pmicEventsSymbolicLinkRetentionPeriodInMinutes) {
        logChange(PROP_SYMLINK_RETENTION_IN_MINUTES, this.pmicEventsSymbolicLinkRetentionPeriodInMinutes,
                pmicEventsSymbolicLinkRetentionPeriodInMinutes);
        this.pmicEventsSymbolicLinkRetentionPeriodInMinutes = pmicEventsSymbolicLinkRetentionPeriodInMinutes;
    }

    /**
     * @param pmicEventsSymbolicLinkDeletionIntervalInMinutes
     *         - frequency in minutes that celltrace symbolic link deletion occurs
     */
    void listenForPmicEventsSymbolicLinkDeletionIntervalInMinutesChanges(@Observes @ConfigurationChangeNotification(
            propertyName = PROP_SYMLINK_DELETION_INTERVAL_IN_MINUTES) final Integer pmicEventsSymbolicLinkDeletionIntervalInMinutes) {
        logChange(PROP_SYMLINK_DELETION_INTERVAL_IN_MINUTES, this.pmicEventsSymbolicLinkDeletionIntervalInMinutes,
                pmicEventsSymbolicLinkDeletionIntervalInMinutes);
        this.pmicEventsSymbolicLinkDeletionIntervalInMinutes = pmicEventsSymbolicLinkDeletionIntervalInMinutes;
        symbolicLinkDeletionSchedulerBean.resetTimer(pmicEventsSymbolicLinkDeletionIntervalInMinutes);
    }

    /**
     * @return - returns the value for celltrace symbolic link volume
     */
    public String getPmicEventsSymbolicLinkVolume() {
        return pmicEventsSymbolicLinkVolume;
    }

    /**
     * @return - returns the value for the celltrace symbolic link base directory
     */
    public String getPmicEventsSymbolicLinkBaseDir() {
        return pmicEventsSymbolicLinkBaseDir;
    }

    /**
     * @return - returns the prefix value for celltrace symbolic link sub-directories
     */
    public String getPmicEventsSymbolicLinkSubdirNamePrefix() {
        return pmicEventsSymbolicLinkSubdirNamePrefix;
    }

    /**
     * @return - returns the prefix value for celltrace symbolic link target-directories
     */
    public String getPmicEventsSymbolicLinkTargetPrefix() {
        return pmicEventsSymbolicLinkTargetPrefix;
    }

    /**
     * @return - returns true if symbolic link creation is enabled for celltrace
     */
    public Boolean isPmicEventsSymbolicLinkCreationEnabled() {
        return pmicEventsSymbolicLinkCreationEnabled;
    }

    /**
     * @return - returns the maximum number of symbolic links allowed for celltrace
     */
    public Integer getPmicEventsMaxSymbolicLinksAllowed() {
        return pmicEventsMaxSymbolicLinksAllowed;
    }

    /**
     * @return - returns the maximum number of symbolic link sub directories allowed for celltrace
     */
    public Integer getPmicEventsMaxSymbolicLinkSubdirs() {
        return pmicEventsMaxSymbolicLinkSubdirs;
    }

    /**
     * @return - returns the retention period in minutes for celltrace symbolic links
     */
    public Integer getPmicEventsSymbolicLinkRetentionPeriodInMinutes() {
        return pmicEventsSymbolicLinkRetentionPeriodInMinutes;
    }

    /**
     * @return - returns the retention period in minutes for celltrace symbolic links
     */
    public Integer getPmicEventsSymbolicLinkDeletionIntervalInMinutes() {
        return pmicEventsSymbolicLinkDeletionIntervalInMinutes;
    }
}
