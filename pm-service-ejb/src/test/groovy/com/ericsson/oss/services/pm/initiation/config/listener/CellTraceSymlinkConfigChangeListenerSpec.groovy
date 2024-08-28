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
import com.ericsson.cds.cdi.support.rule.MockedImplementation
import com.ericsson.oss.services.pm.deletion.schedulers.CellTraceSymlinkDeletionSchedulerBean

import static com.ericsson.oss.pmic.api.collection.constants.FileCollectionModelledConfigConstants.CelltraceSymlinks.PROP_MAX_SYMLINKS_ALLOWED
import static com.ericsson.oss.pmic.api.collection.constants.FileCollectionModelledConfigConstants.CelltraceSymlinks.PROP_MAX_SYMLINK_SUB_DIRS
import static com.ericsson.oss.pmic.api.collection.constants.FileCollectionModelledConfigConstants.CelltraceSymlinks.PROP_SYMLINK_BASE_DIR
import static com.ericsson.oss.pmic.api.collection.constants.FileCollectionModelledConfigConstants.CelltraceSymlinks.PROP_SYMLINK_CREATION_ENABLED
import static com.ericsson.oss.pmic.api.collection.constants.FileCollectionModelledConfigConstants.CelltraceSymlinks.PROP_SYMLINK_DELETION_INTERVAL_IN_MINUTES
import static com.ericsson.oss.pmic.api.collection.constants.FileCollectionModelledConfigConstants.CelltraceSymlinks.PROP_SYMLINK_RETENTION_IN_MINUTES
import static com.ericsson.oss.pmic.api.collection.constants.FileCollectionModelledConfigConstants.CelltraceSymlinks.PROP_SYMLINK_SUB_DIR_NAME_PREFIX
import static com.ericsson.oss.pmic.api.collection.constants.FileCollectionModelledConfigConstants.CelltraceSymlinks.PROP_SYMLINK_TARGET_PREFIX
import static com.ericsson.oss.pmic.api.collection.constants.FileCollectionModelledConfigConstants.CelltraceSymlinks.PROP_SYMLINK_VOLUME
import static com.ericsson.oss.services.pm.common.logging.PMICLog.Event.CONFIGURATION_CHANGE_NOTIFICATION

import javax.inject.Inject

import spock.lang.Unroll

class CellTraceSymlinkConfigChangeListenerSpec extends ConfigChangeListenerParent {

    @Inject
    CellTraceSymlinkConfigChangeListener cellTraceSymlinkConfigChangeListener

    @MockedImplementation
    CellTraceSymlinkDeletionSchedulerBean symbolicLinkDeletionSchedulerBean

    @Unroll
    @PropertiesForTest(properties = [@SuppliedProperty(name = 'pmicEventsSymbolicLinkVolume', value = 'mnt/vol')])
    def 'Should update the events symbolic link volume'() {
        expect: 'the initial symbolic link volume to have default value'
            cellTraceSymlinkConfigChangeListener.pmicEventsSymbolicLinkVolume == 'mnt/vol'

        when: 'the listener is activated'
            cellTraceSymlinkConfigChangeListener.listenForPmicEventsSymbolicLinkVolumeChanges(value)

        then: 'the value is updated'
            cellTraceSymlinkConfigChangeListener.pmicEventsSymbolicLinkVolume == value

        and: 'correct log is recorded'
            1 * systemRecorder.eventCoarse(CONFIGURATION_CHANGE_NOTIFICATION,
                                           PROP_SYMLINK_VOLUME,
                                           "${PROP_SYMLINK_VOLUME} parameter value changed, old value = 'mnt/vol' new value = '${value}'")

        when: 'the listener is activated without a value change'
            cellTraceSymlinkConfigChangeListener.listenForPmicEventsSymbolicLinkVolumeChanges(value)

        then: 'the correct log is recorded'
            1 * systemRecorder.eventCoarse(CONFIGURATION_CHANGE_NOTIFICATION,
                                           PROP_SYMLINK_VOLUME,
                                           "${PROP_SYMLINK_VOLUME} parameter was not changed. Either the new value is the same or the new value was malformed and the change was ignored. Value is still ${value}")

        where:
            value << ["mnt${File.separator}a", "mnt${File.separator}b", "mnt${File.separator}fff"]
    }

    @Unroll
    def 'Should update the events symbolic link base dir'() {
        expect: 'the initial symbolic link volume to have default value'
            cellTraceSymlinkConfigChangeListener.pmicEventsSymbolicLinkBaseDir == 'target/symlinks/'

        when: 'the listener is activated'
            cellTraceSymlinkConfigChangeListener.listenForPmicEventsSymbolicLinkBaseDirChanges(value)

        then: 'the value is updated'
            cellTraceSymlinkConfigChangeListener.pmicEventsSymbolicLinkBaseDir == value

        and: 'correct log is recorded'
            1 * systemRecorder.eventCoarse(CONFIGURATION_CHANGE_NOTIFICATION,
                                           PROP_SYMLINK_BASE_DIR,
                                           "${PROP_SYMLINK_BASE_DIR} parameter value changed, old value = 'target/symlinks/' new value = '${value}'")

        when: 'the listener is activated without a value change'
            cellTraceSymlinkConfigChangeListener.listenForPmicEventsSymbolicLinkBaseDirChanges(value)

        then: 'the correct log is recorded'
            1 * systemRecorder.eventCoarse(CONFIGURATION_CHANGE_NOTIFICATION,
                                           PROP_SYMLINK_BASE_DIR,
                                           "${PROP_SYMLINK_BASE_DIR} parameter was not changed. Either the new value is the same or the new value was malformed and the change was ignored. Value is still ${value}")

        where:
            value << ['a', 'b', 'fff', "path${File.separator}to${File.separator}something"]
    }

    @Unroll
    @PropertiesForTest(properties = [@SuppliedProperty(name = 'pmicEventsSymbolicLinkSubdirNamePrefix', value = 'symlinkdir')])
    def 'Should update the events symbolic link subdir name prefix'() {
        expect: 'that the events symbolic link subdir name prefix the is default value'
            cellTraceSymlinkConfigChangeListener.pmicEventsSymbolicLinkSubdirNamePrefix == 'symlinkdir'

        when: 'the change listener is activated'
            cellTraceSymlinkConfigChangeListener.listenForPmicEventsSymbolicLinkSubdirNamePrefixChanges(value)

        then: 'the events symbolic link subdir name prefix has  been updated'
            cellTraceSymlinkConfigChangeListener.pmicEventsSymbolicLinkSubdirNamePrefix == value

        and: 'the correct log has been recorded'
            1 * systemRecorder.eventCoarse(CONFIGURATION_CHANGE_NOTIFICATION,
                                           PROP_SYMLINK_SUB_DIR_NAME_PREFIX,
                                           "${PROP_SYMLINK_SUB_DIR_NAME_PREFIX} parameter value changed, old value = 'symlinkdir' new value = '${value}'")

        where:
            value << ['prefix1_', 'prefix2_', 'prefix3_', 'something_else']
    }

    @Unroll
    @PropertiesForTest(properties = [@SuppliedProperty(name = 'pmicEventsSymbolicLinkTargetPrefix', value = 'symlinkTarget')])
    def 'Should update the events symbolic link target prefix'() {
        expect: 'the initial symbolic link volume to have default value'
            cellTraceSymlinkConfigChangeListener.pmicEventsSymbolicLinkTargetPrefix == 'symlinkTarget'

        when: 'the listener is activated'
            cellTraceSymlinkConfigChangeListener.listenForPmicEventsSymbolicLinkTargetPrefixChanges(value)

        then: 'the value is updated'
            cellTraceSymlinkConfigChangeListener.pmicEventsSymbolicLinkTargetPrefix == value

        and: 'correct log is recorded'
            1 * systemRecorder.eventCoarse(CONFIGURATION_CHANGE_NOTIFICATION,
                                           PROP_SYMLINK_TARGET_PREFIX,
                                           "${PROP_SYMLINK_TARGET_PREFIX} parameter value changed, old value = 'symlinkTarget' new value = '${value}'")

        when: 'the listener is activated without a value change'
            cellTraceSymlinkConfigChangeListener.listenForPmicEventsSymbolicLinkTargetPrefixChanges(value)

        then: 'the correct log is recorded'
            1 * systemRecorder.eventCoarse(CONFIGURATION_CHANGE_NOTIFICATION,
                                           PROP_SYMLINK_TARGET_PREFIX,
                                           "${PROP_SYMLINK_TARGET_PREFIX} parameter was not changed. Either the new value is the same or the new value was malformed and the change was ignored. Value is still ${value}")

        where:
            value << ['tar', 'get', '_BBB', 'something']
    }

    @Unroll
    @PropertiesForTest(properties = [@SuppliedProperty(name = 'pmicEventsSymbolicLinkCreationEnabled', value = 'true')])
    def 'Should update the events symbolic link creation enabled'() {
        expect: 'that symbolic link creation is disabled'
            cellTraceSymlinkConfigChangeListener.pmicEventsSymbolicLinkCreationEnabled

        when: 'the symbolic link creation cctr rop listener is activated'
            cellTraceSymlinkConfigChangeListener.listenForPmicEventsSymbolicLinkCreationEnabledChanges(value)

        then: 'the cctr rop has  been updated'
            cellTraceSymlinkConfigChangeListener.pmicEventsSymbolicLinkCreationEnabled == value

        and: 'the correct log has been recorded'
            1 * systemRecorder.eventCoarse(CONFIGURATION_CHANGE_NOTIFICATION,
                                           PROP_SYMLINK_CREATION_ENABLED,
                                           "${PROP_SYMLINK_CREATION_ENABLED} parameter value changed, old value = 'true' new value = '${value}'")

        where:
            value << [true, false, new Boolean(null)]
    }

    @Unroll
    @PropertiesForTest(properties = [@SuppliedProperty(name = 'pmicEventsMaxSymbolicLinksAllowed', value = '12')])
    def 'Should update the max symbolic links allowed'() {
        expect: 'that the max symbolic links allowed is default value'
            cellTraceSymlinkConfigChangeListener.pmicEventsMaxSymbolicLinksAllowed == 12

        when: 'the config change listener is activated'
            cellTraceSymlinkConfigChangeListener.listenForPmicEventsMaxSymbolicLinksAllowedChanges(value)

        then: 'the max symbolic links allowed has been updated'
            cellTraceSymlinkConfigChangeListener.pmicEventsMaxSymbolicLinksAllowed == value

        and: 'the correct log has been recorded'
            1 * systemRecorder.eventCoarse(CONFIGURATION_CHANGE_NOTIFICATION,
                                           PROP_MAX_SYMLINKS_ALLOWED,
                                           "${PROP_MAX_SYMLINKS_ALLOWED} parameter value changed, old value = '12' new value = '${value}'")

        where:
            value << [1, 10000, -1, 1_000_000_000]
    }

    @Unroll
    @PropertiesForTest(properties = [@SuppliedProperty(name = 'pmicEventsMaxSymbolicLinkSubdirs', value = '12')])
    def 'Should update max symbolic links subdirs'() {
        expect: 'that the max symbolic link subdirs is the default value'
            cellTraceSymlinkConfigChangeListener.pmicEventsMaxSymbolicLinkSubdirs == 12

        when: 'the change listener is activated'
            cellTraceSymlinkConfigChangeListener.listenForPmicEventsMaxSymbolicLinkSubdirsChanges(value)

        then: 'the max symbolic link subdirs has been updated'
            cellTraceSymlinkConfigChangeListener.pmicEventsMaxSymbolicLinkSubdirs == value

        and: 'the correct log has been recorded'
            1 * systemRecorder.eventCoarse(CONFIGURATION_CHANGE_NOTIFICATION,
                                           PROP_MAX_SYMLINK_SUB_DIRS,
                                           "${PROP_MAX_SYMLINK_SUB_DIRS} parameter value changed, old value = '12' new value = '${value}'")

        where:
            value << [1, 10000, -1, 1_000_000_000]
    }

    @Unroll
    def 'Should update symbolic links retention period'() {
        expect: 'that the max symbolic link subdirs is the default value'
            cellTraceSymlinkConfigChangeListener.pmicEventsSymbolicLinkRetentionPeriodInMinutes == 180

        when: 'the change listener is activated'
            cellTraceSymlinkConfigChangeListener.listenForPmicEventsSymbolicLinkRetentionPeriodInMinutesChanges(value)

        then: 'the max symbolic link subdirs has been updated'
            cellTraceSymlinkConfigChangeListener.pmicEventsSymbolicLinkRetentionPeriodInMinutes == value

        and: 'the correct log has been recorded'
            1 * systemRecorder.eventCoarse(CONFIGURATION_CHANGE_NOTIFICATION,
                                           PROP_SYMLINK_RETENTION_IN_MINUTES,
                                           "${PROP_SYMLINK_RETENTION_IN_MINUTES} parameter value changed, old value = '180' new value = '${value}'")

        where:
            value << [1, 10000, -1, 1_000_000_000]
    }

    @Unroll
    def 'Should update symbolic links deletion interval'() {
        expect: 'that the symbolic links deletion interval is the default value'
            cellTraceSymlinkConfigChangeListener.pmicEventsSymbolicLinkDeletionIntervalInMinutes == 15

        when: 'the change listener is activated'
            cellTraceSymlinkConfigChangeListener.listenForPmicEventsSymbolicLinkDeletionIntervalInMinutesChanges(value)

        then: 'the max symbolic link subdirs has been updated'
            cellTraceSymlinkConfigChangeListener.pmicEventsSymbolicLinkDeletionIntervalInMinutes == value

        and: 'the correct log has been recorded'
            1 * systemRecorder.eventCoarse(CONFIGURATION_CHANGE_NOTIFICATION,
                                           PROP_SYMLINK_DELETION_INTERVAL_IN_MINUTES,
                                           "${PROP_SYMLINK_DELETION_INTERVAL_IN_MINUTES} parameter value changed, old value = '15' new value = '${value}'")

        and: 'the deletion timer is reset'
            1 * symbolicLinkDeletionSchedulerBean.resetTimer(value)

        where:
            value << [1, 10000, -1, 1_000_000_000]
    }
}
