/*------------------------------------------------------------------------------
 *******************************************************************************
 * COPYRIGHT Ericsson 2019
 *
 * The copyright to the computer program(s) herein is the property of
 * Ericsson Inc. The programs may be used and/or copied only with written
 * permission from Ericsson Inc. or in accordance with the terms and
 * conditions stipulated in the agreement/contract under which the
 * program(s) have been supplied.
 *******************************************************************************
 *----------------------------------------------------------------------------*/

package com.ericsson.oss.services.pm.initiation.config.listener

import com.ericsson.cds.cdi.support.providers.custom.sfwk.PropertiesForTest
import com.ericsson.cds.cdi.support.providers.custom.sfwk.SuppliedProperty

import static com.ericsson.oss.pmic.api.collection.constants.FileCollectionModelledConfigConstants.EbmSymlinks.PROP_MAX_SYMLINKS_ALLOWED
import static com.ericsson.oss.pmic.api.collection.constants.FileCollectionModelledConfigConstants.EbmSymlinks.PROP_MAX_SYMLINKS_SUBDIRS
import static com.ericsson.oss.pmic.api.collection.constants.FileCollectionModelledConfigConstants.EbmSymlinks.PROP_PMIC_SYMLINK_DELETION_INTERVAL_IN_MINUTES
import static com.ericsson.oss.pmic.api.collection.constants.FileCollectionModelledConfigConstants.EbmSymlinks.PROP_PMIC_SYMLINK_PMIC_RETENTION_PERIOD_IN_MINUTES
import static com.ericsson.oss.pmic.api.collection.constants.FileCollectionModelledConfigConstants.EbmSymlinks.PROP_SYMLINK_BASE_DIR
import static com.ericsson.oss.pmic.api.collection.constants.FileCollectionModelledConfigConstants.EbmSymlinks.PROP_SYMLINK_SUBDIR_NAME_PREFIX
import static com.ericsson.oss.services.pm.common.logging.PMICLog.Event.CONFIGURATION_CHANGE_NOTIFICATION

import com.ericsson.cds.cdi.support.rule.MockedImplementation
import com.ericsson.oss.services.pm.deletion.schedulers.EbmSymlinkDeletionSchedulerBean
import spock.lang.Unroll

import javax.inject.Inject

class EbmSymlinkConfigChangeListenerSpec extends ConfigChangeListenerParent {

    @Inject
    EbmSymlinkConfigChangeListener ebmSymlinkConfigChangeListener

    @MockedImplementation
    EbmSymlinkDeletionSchedulerBean symbolicLinkDeletionSchedulerBean

    @Unroll
    def 'Should update the ebm symbolic link base dir'() {
        expect: 'the initial symbolic link volume to have default value'
            ebmSymlinkConfigChangeListener.pmicEbmSymbolicLinkBaseDir == 'target/symlinks/'

        when: 'the listener is activated'
            ebmSymlinkConfigChangeListener.listenForPmicEbmSymbolicLinkBaseDirChanges(value)

        then: 'the value is updated'
            ebmSymlinkConfigChangeListener.pmicEbmSymbolicLinkBaseDir == value

        and: 'correct log is recorded'
            1 * systemRecorder.eventCoarse(CONFIGURATION_CHANGE_NOTIFICATION,
                                           PROP_SYMLINK_BASE_DIR,
                                           "${PROP_SYMLINK_BASE_DIR} parameter value changed, old value = 'target/symlinks/' new value = '${value}'")

        when: 'the listener is activated without a value change'
            ebmSymlinkConfigChangeListener.listenForPmicEbmSymbolicLinkBaseDirChanges(value)

        then: 'the correct log is recorded'
            1 * systemRecorder.eventCoarse(CONFIGURATION_CHANGE_NOTIFICATION,
                                           PROP_SYMLINK_BASE_DIR,
                                           "${PROP_SYMLINK_BASE_DIR} parameter was not changed. Either the new value is the same or the new value was malformed and the change was ignored. Value is still ${value}")

        where:
            value << ['a', 'b', 'fff', "path${File.separator}to${File.separator}something"]
    }

    @Unroll
    @PropertiesForTest(properties = [@SuppliedProperty(name = 'pmicEbmSymbolicLinkSubdirNamePrefix', value = 'myPrefix')])
    def 'Should update the ebm symbolic link subdir name prefix'() {
        expect: 'that the ebm symbolic link subdir name prefix is the default value'
            ebmSymlinkConfigChangeListener.pmicEbmSymbolicLinkSubdirNamePrefix == 'myPrefix'

        when: 'the change listener is activated'
            ebmSymlinkConfigChangeListener.listenForPmicEbmSymbolicLinkSubdirNamePrefixChanges(value)

        then: 'the ebm symbolic link subdir name prefix has been updated'
            ebmSymlinkConfigChangeListener.pmicEbmSymbolicLinkSubdirNamePrefix == value

        and: 'the correct log has been recorded'
            1 * systemRecorder.eventCoarse(CONFIGURATION_CHANGE_NOTIFICATION,
                                           PROP_SYMLINK_SUBDIR_NAME_PREFIX,
                                           "${PROP_SYMLINK_SUBDIR_NAME_PREFIX} parameter value changed, old value = 'myPrefix' new value = '${value}'")

        where:
            value << ['prefix1_', 'prefix2_', 'prefix3_', 'something_else']
    }

    @Unroll
    @PropertiesForTest(properties = [@SuppliedProperty(name = 'pmicEbmMaxSymbolicLinksAllowed', value = '7000')])
    def 'Should update the max symbolic links allowed'() {
        expect: 'that the max symbolic links allowed is default value'
            ebmSymlinkConfigChangeListener.pmicEbmMaxSymbolicLinksAllowed == 7000

        when: 'the config change listener is activated'
            ebmSymlinkConfigChangeListener.listenForPmicEbmsMaxSymbolicLinksAllowedChanges(value)

        then: 'the max symbolic links allowed has been updated'
            ebmSymlinkConfigChangeListener.pmicEbmMaxSymbolicLinksAllowed == value

        and: 'the correct log has been recorded'
            1 * systemRecorder.eventCoarse(CONFIGURATION_CHANGE_NOTIFICATION,
                                           PROP_MAX_SYMLINKS_ALLOWED,
                                           "${PROP_MAX_SYMLINKS_ALLOWED} parameter value changed, old value = '7000' new value = '${value}'")

        where:
            value << [1, 10000, -1, 1_000_000_000]
    }

    @Unroll
    @PropertiesForTest(properties = [@SuppliedProperty(name = 'pmicEbmMaxSymbolicLinkSubdirs', value = '8')])
    def 'Should update max symbolic links subdirs'() {
        expect: 'that the max symbolic link subdirs is the default value'
            ebmSymlinkConfigChangeListener.pmicEbmMaxSymbolicLinkSubdirs == 8

        when: 'the change listener is activated'
            ebmSymlinkConfigChangeListener.listenForPmicEbmMaxSymbolicLinkSubdirsChanges(value)

        then: 'the max symbolic link subdirs has been updated'
            ebmSymlinkConfigChangeListener.pmicEbmMaxSymbolicLinkSubdirs == value

        and: 'the correct log has been recorded'
            1 * systemRecorder.eventCoarse(CONFIGURATION_CHANGE_NOTIFICATION,
                                           PROP_MAX_SYMLINKS_SUBDIRS,
                                           "${PROP_MAX_SYMLINKS_SUBDIRS} parameter value changed, old value = '8' new value = '${value}'")

        where:
            value << [1, 10000, -1, 1_000_000_000]
    }

    @Unroll
    def 'Should update symbolic links retention period'() {
        expect: 'that the max symbolic link subdirs is the default value'
            ebmSymlinkConfigChangeListener.pmicEbmSymbolicLinkRetentionPeriodInMinutes == 180

        when: 'the change listener is activated'
            ebmSymlinkConfigChangeListener.listenForPmicEbmSymbolicLinkRetentionPeriodInMinutesChanges(value)

        then: 'the max symbolic link subdirs has been updated'
            ebmSymlinkConfigChangeListener.pmicEbmSymbolicLinkRetentionPeriodInMinutes == value

        and: 'the correct log has been recorded'
            1 * systemRecorder.eventCoarse(CONFIGURATION_CHANGE_NOTIFICATION,
                                           PROP_PMIC_SYMLINK_PMIC_RETENTION_PERIOD_IN_MINUTES,
                                           "${PROP_PMIC_SYMLINK_PMIC_RETENTION_PERIOD_IN_MINUTES} parameter value changed, old value = '180' new value = '${value}'")

        where:
            value << [1, 10000, -1, 1_000_000_000]
    }

    @Unroll
    def 'Should update symbolic links deletion interval'() {
        expect: 'that the symbolic links deletion interval is the default value'
            ebmSymlinkConfigChangeListener.pmicEbmSymbolicLinkDeletionIntervalInMinutes == 15

        when: 'the change listener is activated'
            ebmSymlinkConfigChangeListener.listenForPmicEbmSymbolicLinkDeletionIntervalInMinutesChanges(value)

        then: 'the max symbolic link subdirs has been updated'
            ebmSymlinkConfigChangeListener.pmicEbmSymbolicLinkDeletionIntervalInMinutes == value

        and: 'the correct log has been recorded'
            1 * systemRecorder.eventCoarse(CONFIGURATION_CHANGE_NOTIFICATION,
                                           PROP_PMIC_SYMLINK_DELETION_INTERVAL_IN_MINUTES,
                                           "${PROP_PMIC_SYMLINK_DELETION_INTERVAL_IN_MINUTES} parameter value changed, old value = '15' new value = '${value}'")

        and: 'the deletion timer is reset'
            1 * symbolicLinkDeletionSchedulerBean.resetTimer(value)

        where:
            value << [1, 10000, -1, 1_000_000_000]
    }
}
