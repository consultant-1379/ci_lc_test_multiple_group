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

import static com.ericsson.oss.pmic.api.collection.constants.FileCollectionModelledConfigConstants.EbmSymlinks.PROP_MAX_SYMLINKS_ALLOWED;
import static com.ericsson.oss.pmic.api.collection.constants.FileCollectionModelledConfigConstants.EbmSymlinks.PROP_MAX_SYMLINKS_SUBDIRS;
import static com.ericsson.oss.pmic.api.collection.constants.FileCollectionModelledConfigConstants.EbmSymlinks.PROP_PMIC_SYMLINK_DELETION_INTERVAL_IN_MINUTES;
import static com.ericsson.oss.pmic.api.collection.constants.FileCollectionModelledConfigConstants.EbmSymlinks.PROP_PMIC_SYMLINK_PMIC_RETENTION_PERIOD_IN_MINUTES;
import static com.ericsson.oss.pmic.api.collection.constants.FileCollectionModelledConfigConstants.EbmSymlinks.PROP_SYMLINK_BASE_DIR;
import static com.ericsson.oss.pmic.api.collection.constants.FileCollectionModelledConfigConstants.EbmSymlinks.PROP_SYMLINK_SUBDIR_NAME_PREFIX;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Observes;
import javax.inject.Inject;

import com.ericsson.oss.itpf.sdk.config.annotation.ConfigurationChangeNotification;
import com.ericsson.oss.itpf.sdk.config.annotation.Configured;
import com.ericsson.oss.services.pm.deletion.schedulers.EbmSymlinkDeletionSchedulerBean;

/**
 * Listener for Configuration parameter (PIB) changes
 */
@ApplicationScoped
public class EbmSymlinkConfigChangeListener extends AbstractConfigurationChangeListener {

    @Inject
    @Configured(propertyName = PROP_SYMLINK_BASE_DIR)
    private String pmicEbmSymbolicLinkBaseDir;

    @Inject
    @Configured(propertyName = PROP_SYMLINK_SUBDIR_NAME_PREFIX)
    private String pmicEbmSymbolicLinkSubdirNamePrefix;

    @Inject
    @Configured(propertyName = PROP_MAX_SYMLINKS_ALLOWED)
    private Integer pmicEbmMaxSymbolicLinksAllowed;

    @Inject
    @Configured(propertyName = PROP_MAX_SYMLINKS_SUBDIRS)
    private Integer pmicEbmMaxSymbolicLinkSubdirs;

    @Inject
    @Configured(propertyName = PROP_PMIC_SYMLINK_PMIC_RETENTION_PERIOD_IN_MINUTES)
    private Integer pmicEbmSymbolicLinkRetentionPeriodInMinutes;

    @Inject
    @Configured(propertyName = PROP_PMIC_SYMLINK_DELETION_INTERVAL_IN_MINUTES)
    private Integer pmicEbmSymbolicLinkDeletionIntervalInMinutes;

    @Inject
    private EbmSymlinkDeletionSchedulerBean pmicEbmSymbolicLinkDeletionSchedulerBean;

    /**
     * Listen for pmic ebm symbolic link base dir changes.
     *
     * @param pmicEbmSymbolicLinkBaseDir
     *         the pmic ebm symbolic link base dir
     */
    void listenForPmicEbmSymbolicLinkBaseDirChanges(
            @Observes @ConfigurationChangeNotification(propertyName = PROP_SYMLINK_BASE_DIR) final String pmicEbmSymbolicLinkBaseDir) {
        final String valueBefore = this.pmicEbmSymbolicLinkBaseDir;
        this.pmicEbmSymbolicLinkBaseDir = pathValidator.formAndValidatePath(this.pmicEbmSymbolicLinkBaseDir, pmicEbmSymbolicLinkBaseDir);
        validatePathForStringValue(PROP_SYMLINK_BASE_DIR, valueBefore, pmicEbmSymbolicLinkBaseDir);
    }

    /**
     * Listen for pmic ebm symbolic link subdir name prefix changes.
     *
     * @param pmicEbmSymbolicLinkSubdirNamePrefix
     *         the pmic ebm symbolic link subdir name prefix
     */
    void listenForPmicEbmSymbolicLinkSubdirNamePrefixChanges(
            @Observes @ConfigurationChangeNotification(
                    propertyName = PROP_SYMLINK_SUBDIR_NAME_PREFIX) final String pmicEbmSymbolicLinkSubdirNamePrefix) {
        logChange(PROP_SYMLINK_SUBDIR_NAME_PREFIX, this.pmicEbmSymbolicLinkSubdirNamePrefix, pmicEbmSymbolicLinkSubdirNamePrefix);
        this.pmicEbmSymbolicLinkSubdirNamePrefix = pmicEbmSymbolicLinkSubdirNamePrefix;
    }

    /**
     * Listen for pmic ebms max symbolic links allowed changes.
     *
     * @param pmicEbmMaxSymbolicLinksAllowed
     *         the pmic ebm max symbolic links allowed
     */
    void listenForPmicEbmsMaxSymbolicLinksAllowedChanges(
            @Observes @ConfigurationChangeNotification(propertyName = PROP_MAX_SYMLINKS_ALLOWED) final Integer pmicEbmMaxSymbolicLinksAllowed) {

        logChange(PROP_MAX_SYMLINKS_ALLOWED, this.pmicEbmMaxSymbolicLinksAllowed, pmicEbmMaxSymbolicLinksAllowed);
        this.pmicEbmMaxSymbolicLinksAllowed = pmicEbmMaxSymbolicLinksAllowed;
    }

    /**
     * Listen for pmic ebm max symbolic link subdirs changes.
     *
     * @param pmicEbmMaxSymbolicLinkSubdirs
     *         the pmic ebm max symbolic link subdirs
     */
    void listenForPmicEbmMaxSymbolicLinkSubdirsChanges(
            @Observes @ConfigurationChangeNotification(propertyName = PROP_MAX_SYMLINKS_SUBDIRS) final Integer pmicEbmMaxSymbolicLinkSubdirs) {
        logChange(PROP_MAX_SYMLINKS_SUBDIRS, this.pmicEbmMaxSymbolicLinkSubdirs, pmicEbmMaxSymbolicLinkSubdirs);
        this.pmicEbmMaxSymbolicLinkSubdirs = pmicEbmMaxSymbolicLinkSubdirs;
    }

    /**
     * Listen for pmic ebm symbolic link retention period in minutes changes.
     *
     * @param pmicEbmSymbolicLinkRetentionPeriodInMinutes
     *         the pmic ebm symbolic link retention period in minutes
     */
    void listenForPmicEbmSymbolicLinkRetentionPeriodInMinutesChanges(
            @Observes @ConfigurationChangeNotification(
                    propertyName = PROP_PMIC_SYMLINK_PMIC_RETENTION_PERIOD_IN_MINUTES) final Integer pmicEbmSymbolicLinkRetentionPeriodInMinutes) {
        logChange(PROP_PMIC_SYMLINK_PMIC_RETENTION_PERIOD_IN_MINUTES, this.pmicEbmSymbolicLinkRetentionPeriodInMinutes,
                pmicEbmSymbolicLinkRetentionPeriodInMinutes);
        this.pmicEbmSymbolicLinkRetentionPeriodInMinutes = pmicEbmSymbolicLinkRetentionPeriodInMinutes;
    }

    /**
     * Listen for pmic ebm symbolic link deletion interval in minutes changes.
     *
     * @param pmicEbmSymbolicLinkDeletionIntervalInMinutes
     *         the pmic ebm symbolic link deletion interval in minutes
     */
    void listenForPmicEbmSymbolicLinkDeletionIntervalInMinutesChanges(
            @Observes @ConfigurationChangeNotification(
                    propertyName = PROP_PMIC_SYMLINK_DELETION_INTERVAL_IN_MINUTES) final Integer pmicEbmSymbolicLinkDeletionIntervalInMinutes) {
        logChange(PROP_PMIC_SYMLINK_DELETION_INTERVAL_IN_MINUTES, this.pmicEbmSymbolicLinkDeletionIntervalInMinutes,
                pmicEbmSymbolicLinkDeletionIntervalInMinutes);
        this.pmicEbmSymbolicLinkDeletionIntervalInMinutes = pmicEbmSymbolicLinkDeletionIntervalInMinutes;
        pmicEbmSymbolicLinkDeletionSchedulerBean.resetTimer(pmicEbmSymbolicLinkDeletionIntervalInMinutes);
    }

    /**
     * Gets pmic ebm symbolic link base dir.
     *
     * @return the pmic ebm symbolic link base dir
     */
    public String getPmicEbmSymbolicLinkBaseDir() {
        return pmicEbmSymbolicLinkBaseDir;
    }

    /**
     * Gets pmic ebm symbolic link subdir name prefix.
     *
     * @return the pmic ebm symbolic link subdir name prefix
     */
    public String getPmicEbmSymbolicLinkSubdirNamePrefix() {
        return pmicEbmSymbolicLinkSubdirNamePrefix;
    }

    /**
     * Gets pmic ebm max symbolic links allowed.
     *
     * @return the pmic ebm max symbolic links allowed
     */
    public Integer getPmicEbmMaxSymbolicLinksAllowed() {
        return pmicEbmMaxSymbolicLinksAllowed;
    }

    /**
     * Gets pmic ebm max symbolic link subdirs.
     *
     * @return the pmic ebm max symbolic link subdirs
     */
    public Integer getPmicEbmMaxSymbolicLinkSubdirs() {
        return pmicEbmMaxSymbolicLinkSubdirs;
    }

    /**
     * Gets pmic ebm symbolic link retention period in minutes.
     *
     * @return the pmic ebm symbolic link retention period in minutes
     */
    public Integer getPmicEbmSymbolicLinkRetentionPeriodInMinutes() {
        return pmicEbmSymbolicLinkRetentionPeriodInMinutes;
    }

    /**
     * Gets pmic ebm symbolic link deletion interval in minutes.
     *
     * @return the pmic ebm symbolic link deletion interval in minutes
     */
    public Integer getPmicEbmSymbolicLinkDeletionIntervalInMinutes() {
        return pmicEbmSymbolicLinkDeletionIntervalInMinutes;
    }
}
