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

import static com.ericsson.oss.pmic.api.collection.constants.FileCollectionModelledConfigConstants.PROP_PMIC_NFS_SHARE_LIST
import static com.ericsson.oss.pmic.api.collection.constants.FileCollectionModelledConfigConstants.PROP_PMIC_NFS_SHARE
import static com.ericsson.oss.pmic.api.collection.constants.FileCollectionModelledConfigConstants.PROP_STARTUP_RECOVERY_HOURS
import static com.ericsson.oss.pmic.api.constants.ModelledConfigurationConstants.Cbs.PROP_CBS_ENABLED
import static com.ericsson.oss.pmic.api.constants.ModelledConfigurationConstants.Cbs.PROP_MAX_NUMBER_OF_CBS_ALLOWED
import static com.ericsson.oss.pmic.api.constants.ModelledConfigurationConstants.ContinuousCelltrace.PROP_CCTR_SUBSCRIPTION_ENABLED
import static com.ericsson.oss.pmic.api.constants.ModelledConfigurationConstants.ContinuousCelltrace.PROP_ROP_PERIOD
import static com.ericsson.oss.pmic.api.constants.ModelledConfigurationConstants.Cbs.PROP_PERIODIC_AUDIT_DELAY_INTERVAL
import static com.ericsson.oss.pmic.api.constants.ModelledConfigurationConstants.PROP_INITIATION_TIMEOUT_IN_MILLIS
import static com.ericsson.oss.pmic.api.constants.ModelledConfigurationConstants.PROP_MIGRATION_ENABLED
import static com.ericsson.oss.pmic.api.constants.ModelledConfigurationConstants.PROP_SCANNER_POLLING_INTERVAL_IN_MINUTES
import static com.ericsson.oss.pmic.api.constants.ModelledConfigurationConstants.PROP_SYSTEM_DEFINED_SUBSCRIPTION_AUDIT_INTERVAL
import static com.ericsson.oss.services.pm.common.logging.PMICLog.Event.CONFIGURATION_CHANGE_NOTIFICATION
import static com.ericsson.oss.services.pm.initiation.config.listener.DeploymentReadyConfigurationListener.PM_DEPLOYMENT_PHASE_COMPLETE_PARAMETER
import static com.ericsson.oss.pmic.api.constants.ModelledConfigurationConstants.Moinstance.PROP_MAX_NUMBER_OF_MOINSTANCE_ALLOWED

import javax.inject.Inject

import spock.lang.Unroll

import com.ericsson.cds.cdi.support.providers.custom.sfwk.PropertiesForTest
import com.ericsson.cds.cdi.support.providers.custom.sfwk.SuppliedProperty
import com.ericsson.oss.services.pm.common.startup.StartupService
import com.ericsson.cds.cdi.support.rule.MockedImplementation
import com.ericsson.cds.cdi.support.rule.ObjectUnderTest
import com.ericsson.oss.pmic.api.collection.constants.FileCollectionModelledConfigConstants.Celltrace
import com.ericsson.oss.pmic.api.collection.constants.FileCollectionModelledConfigConstants.UeTrace
import com.ericsson.oss.services.pm.collection.mountpoints.DestinationMountPointConfigSource
import com.ericsson.oss.services.pm.collection.recovery.ScheduledRecovery
import com.ericsson.oss.services.pm.deletion.schedulers.CelltraceFileDeletionSchedulerBean

import com.ericsson.oss.services.pm.scheduling.impl.ScannerPollSyncScheduler
import com.ericsson.oss.services.pm.scheduling.impl.SystemDefinedSubscriptionAuditScheduler

class ConfigChangeListenersSpec extends ConfigChangeListenerParent {

    @ObjectUnderTest
    ConfigurationChangeListener objectUnderTest

    @Inject
    ActivationDelayIntervalConfigurationChangeListener activationDelayIntervalConfigurationChangeListener
    @Inject
    ContinuousCelltraceConfigurationChangeListener cctrConfigurationChangeListener
    @Inject
    CBSConfigurationChangeListener cbsConfigurationChangeListener

    @Inject
    DeploymentReadyConfigurationListener deploymentReadyConfigurationListener
    @MockedImplementation
    CacheHolder cacheHolder
    @MockedImplementation
    StartupService startupService

    @Inject
    MoInstanceConfigurationChangeListener moInstanceConfigurationChangeListener

    @Inject
    ConfigurationChangeListener configurationChangeListener
    @MockedImplementation
    DestinationMountPointConfigSource destinationMountPointConfigSource
    @MockedImplementation
    CelltraceFileDeletionSchedulerBean celltraceFileDeletionSchedulerBean
    @MockedImplementation
    SystemDefinedSubscriptionAuditScheduler sysDefSubscriptionAuditScheduler
    @MockedImplementation
    ScheduledRecovery scheduledRecoveryBean
    @MockedImplementation
    ScannerPollSyncScheduler masterPollingSchedulerBean

    @Unroll
    @PropertiesForTest(properties = [@SuppliedProperty(name = 'activationDelayInterval', value = '15')])
    def 'Should update the activation delay interval'() {
        expect: 'that the activation delay interval is default value'
            activationDelayIntervalConfigurationChangeListener.activationDelayInterval == 15

        when: 'the activation delay listener is activated'
            activationDelayIntervalConfigurationChangeListener.listenForActivationDelayInterval(value)

        then: 'the activation delay interval has  been updated'
            activationDelayIntervalConfigurationChangeListener.activationDelayInterval == value

        and: 'the correct log has been recorded'
            1 * systemRecorder.eventCoarse(CONFIGURATION_CHANGE_NOTIFICATION,
                                           PROP_PERIODIC_AUDIT_DELAY_INTERVAL,
                                           "${PROP_PERIODIC_AUDIT_DELAY_INTERVAL} parameter value changed, old value = '15' new value = '${value}'")

        where:
            value << [1, 2, 3, -1, 999, 9_999_999_999L]
    }

    @Unroll
    def 'Should update the Continuous Cell Trace ROP'() {
        expect: 'that the CCTR rop is default value'
            cctrConfigurationChangeListener.continuousCelltraceRopPeriod == 60

        when: 'the cctr rop listener is activated'
            cctrConfigurationChangeListener.listenForContinuousCelltraceRopPeriodChanges(value)

        then: 'the cctr rop has  been updated'
            cctrConfigurationChangeListener.continuousCelltraceRopPeriod == value

        and: 'the correct log has been recorded'
            1 * systemRecorder.eventCoarse(CONFIGURATION_CHANGE_NOTIFICATION,
                    PROP_ROP_PERIOD,
                    "${PROP_ROP_PERIOD} parameter value changed, old value = '60' new value = '${value}'")

        where:
            value << [1, 2, 3, -1, 999, 999_999_999]
    }

    @Unroll
    @PropertiesForTest(properties = [@SuppliedProperty(name = 'maxNoOfCBSAllowed', value = '15'),
                                     @SuppliedProperty(name = 'cbsEnabled', value = 'true')])
    def 'Should update the cbsEnabled and maxCbsAllowed attributes'() {
        expect: 'initial cbsEnabled and maxCbsAllowed attributes to have default values'
            cbsConfigurationChangeListener.cbsEnabled
            cbsConfigurationChangeListener.maxNoOfCBSAllowed == 15

        when: 'the configuration change listenervalues are updated'
            cbsConfigurationChangeListener.listenForCbsEnabledChanges(newCsbEnabled)
            cbsConfigurationChangeListener.listenForMaxNoOfCBSAllowedChanges(newMaxCbs)

        then: 'the values have been updated'
            cbsConfigurationChangeListener.maxNoOfCBSAllowed == newMaxCbs
            cbsConfigurationChangeListener.cbsEnabled == newCsbEnabled

        and: 'the correct log is recorded'
            1 * systemRecorder.eventCoarse(CONFIGURATION_CHANGE_NOTIFICATION,
                                           PROP_CBS_ENABLED,
                                           "${PROP_CBS_ENABLED} parameter value changed, old value = 'true' new value = '${newCsbEnabled}'")
            1 * systemRecorder.eventCoarse(CONFIGURATION_CHANGE_NOTIFICATION,
                                           PROP_MAX_NUMBER_OF_CBS_ALLOWED,
                                           "${PROP_MAX_NUMBER_OF_CBS_ALLOWED} parameter value changed, old value = '15' new value = '${newMaxCbs}'")

        where:
            newCsbEnabled | newMaxCbs
            true          | 1
            true          | 1000
            false         | 1
            false         | 1000
    }

    def 'Should update the deployment ready event attribute'() {
        expect: 'that the deployment ready event has not yet been triggered'
            !deploymentReadyConfigurationListener.hasDeploymentReadyEventBeenTriggered()

        when: 'the deployment enabled is triggered with an incorrect value'
            deploymentReadyConfigurationListener.listenForCbsEnabledChanges('anything')

        then: 'nothing happens'
            !deploymentReadyConfigurationListener.hasDeploymentReadyEventBeenTriggered()
            0 * cacheHolder.forceSynchronizationOfReplicatedCaches()
            0 * startupService.triggerStartupService()
            1 * systemRecorder.eventCoarse(CONFIGURATION_CHANGE_NOTIFICATION,
                                           PM_DEPLOYMENT_PHASE_COMPLETE_PARAMETER,
                                           "${PM_DEPLOYMENT_PHASE_COMPLETE_PARAMETER} parameter value changed, old value = 'NOT_INTERESTED' new value = 'anything'")


        when: 'the deployment enabled event change is triggered with correct value'
            deploymentReadyConfigurationListener.listenForCbsEnabledChanges('true')

        then: 'the deployment ready is set'
            deploymentReadyConfigurationListener.hasDeploymentReadyEventBeenTriggered()
            1 * cacheHolder.forceSynchronizationOfReplicatedCaches()
            1 * startupService.triggerStartupService()

        and: 'correct log is recorded'
            1 * systemRecorder.eventCoarse(CONFIGURATION_CHANGE_NOTIFICATION,
                                           PM_DEPLOYMENT_PHASE_COMPLETE_PARAMETER,
                                           "${PM_DEPLOYMENT_PHASE_COMPLETE_PARAMETER} parameter value changed, old value = 'NOT_INTERESTED' new value = 'true'")

        when: 'the deployment enabled event triggered after deployment ready already set'
            deploymentReadyConfigurationListener.listenForCbsEnabledChanges('true')

        then: 'nothing happens'
            deploymentReadyConfigurationListener.hasDeploymentReadyEventBeenTriggered()
            0 * cacheHolder.forceSynchronizationOfReplicatedCaches()
            0 * startupService.triggerStartupService()
            1 * systemRecorder.eventCoarse(CONFIGURATION_CHANGE_NOTIFICATION,
                                           PM_DEPLOYMENT_PHASE_COMPLETE_PARAMETER,
                                           "${PM_DEPLOYMENT_PHASE_COMPLETE_PARAMETER} parameter value changed, old value = 'NOT_INTERESTED' new value = 'true'")

    }

    @Unroll
    def 'Should update the max mo instances allowed attribute'() {
        expect: 'the max number of mo instances has the default value'
            moInstanceConfigurationChangeListener.maxNoOfMOInstanceAllowed == 72000

        when: 'the change listener is activated'
            moInstanceConfigurationChangeListener.listenForMaxNoOfMOInstanceAllowedChanges(value)

        then: 'the value is updated'
            moInstanceConfigurationChangeListener.maxNoOfMOInstanceAllowed == value
            1 * systemRecorder.eventCoarse(CONFIGURATION_CHANGE_NOTIFICATION,
                                           PROP_MAX_NUMBER_OF_MOINSTANCE_ALLOWED,
                                           "${PROP_MAX_NUMBER_OF_MOINSTANCE_ALLOWED} parameter value changed, old value = '72000' new value = '${value}'")

        where:
            value << [1, 5, -1, 1_000_000]

    }

    @Unroll
    @PropertiesForTest(properties = [@SuppliedProperty(name = 'startupFileRecoveryHoursInfo', value = '25')])
    def 'Should update the startup recovery hours'() {
        expect: 'that the correct default value is set'
        configurationChangeListener.startupFileRecoveryHoursInfo == 25

        when: 'the change listener is activated'
        configurationChangeListener.listenForStartupRecoveryHoursInfoChanges(value)

        then: 'the value is updated'
            configurationChangeListener.startupFileRecoveryHoursInfo == value
            1 * systemRecorder.eventCoarse(CONFIGURATION_CHANGE_NOTIFICATION,
                                           PROP_STARTUP_RECOVERY_HOURS,
                                           "${PROP_STARTUP_RECOVERY_HOURS} parameter value changed, old value = '25' new value = '${value}'")

        where:
            value << [1, 14, 1000, 1_000_000_000]
    }

    @Unroll
    def 'Should update the pmic nfs share list'() {
        expect: 'that the correct default value is set'
            configurationChangeListener.pmicNfsShareList == 'target/'

        when: 'the change listener is activated'
            configurationChangeListener.listenForPmicNfsShareList(value)

        then: 'the value is updated'
            configurationChangeListener.pmicNfsShareList == value
            1 * systemRecorder.eventCoarse(CONFIGURATION_CHANGE_NOTIFICATION,
                                           PROP_PMIC_NFS_SHARE_LIST,
                                           "${PROP_PMIC_NFS_SHARE_LIST} parameter value changed, old value = 'target/' new value = '${value}'")

        and: 'the destination mount point config is reloaded'
            1 * destinationMountPointConfigSource.reload()

        where:
            value << ['a', 'b', '1000,ksks']
    }

    @Unroll
    @PropertiesForTest(properties = [@SuppliedProperty(name = 'pmicNfsShareList', value = '25')])
    def 'Should update the nfs share list deprecated'() {
        expect: 'that the correct default value is set'
            configurationChangeListener.pmicNfsShareList == '25'

        when: 'the change listener is activated'
            configurationChangeListener.listenForNfsShareChanges(value)

        then: 'the value is updated'
            configurationChangeListener.pmicNfsShareList == value
            1 * systemRecorder.eventCoarse(CONFIGURATION_CHANGE_NOTIFICATION,
                                           PROP_PMIC_NFS_SHARE,
                                           "${PROP_PMIC_NFS_SHARE} parameter value changed, old value = '25' new value = '${value}'")

        where:
            value << ['a', 'b', '1000,ksks']
    }

    @Unroll
    @PropertiesForTest(properties = [@SuppliedProperty(name = 'pmMigrationEnabled', value = 'true')])
    def 'Should update the migration enabled'() {
        expect: 'that the correct default value is set'
            configurationChangeListener.pmMigrationEnabled

        when: 'the change listener is activated'
            configurationChangeListener.listenForPmMigrationEnabledChanges(value)

        then: 'the value is updated'
            configurationChangeListener.pmMigrationEnabled == value
            1 * systemRecorder.eventCoarse(CONFIGURATION_CHANGE_NOTIFICATION,
                                           PROP_MIGRATION_ENABLED,
                                           "${PROP_MIGRATION_ENABLED} parameter value changed, old value = 'true' new value = '${value}'")

        where:
            value << [true, false, new Boolean(null)]
    }

    @Unroll
    def 'Should update the celltrace file retention period'() {
        expect: 'that the correct default value is set'
            configurationChangeListener.pmicCelltraceFileRetentionPeriodInMinutes == 180

        when: 'the change listener is activated'
            configurationChangeListener.listenForPmicCelltraceFileRetentionPeriodInMinutesChanges(value)

        then: 'the value is updated'
        configurationChangeListener.pmicCelltraceFileRetentionPeriodInMinutes == value
            1 * systemRecorder.eventCoarse(CONFIGURATION_CHANGE_NOTIFICATION,
                                           Celltrace.PROP_FILE_RETENTION_PERIOD_IN_MINUTES,
                                           "${Celltrace.PROP_FILE_RETENTION_PERIOD_IN_MINUTES} parameter value changed, old value = '180' new value = '${value}'")

        where:
            value << [1, 100, -1, 1_000_000]
    }

    @Unroll
    def 'Should update the celltrace file deletion period'() {
        expect: 'that the correct default value is set'
            configurationChangeListener.pmicCelltraceFileDeletionIntervalInMinutes == 15

        when: 'the change listener is activated'
            configurationChangeListener.listenForPmicCelltraceFileDeletionIntervalInMinutesChanges(value)

        then: 'the value is updated'
            configurationChangeListener.pmicCelltraceFileDeletionIntervalInMinutes == value
            1 * systemRecorder.eventCoarse(CONFIGURATION_CHANGE_NOTIFICATION,
                    Celltrace.PROP_FILE_DELETION_INTERVAL_IN_MINUTES,
                    "${Celltrace.PROP_FILE_DELETION_INTERVAL_IN_MINUTES} parameter value changed, old value = '15' new value = '${value}'")

        and: 'the timer is reset'
            1 * celltraceFileDeletionSchedulerBean.resetTimer(value)

        where:
            value << [1, 100, -1, 1_000_000]
    }

    @Unroll
    @PropertiesForTest(properties = [@SuppliedProperty(name = 'initiationTimeoutInMillis', value = '25')])
    def 'Should update the initiation timeout period'() {
        expect: 'that the correct default value is set'
            configurationChangeListener.initiationTimeoutInMillis == 25

        when: 'the change listener is activated'
            configurationChangeListener.listenForInitiationTimeoutChanges(value)

        then: 'the value is updated'
            configurationChangeListener.initiationTimeoutInMillis == value
            1 * systemRecorder.eventCoarse(CONFIGURATION_CHANGE_NOTIFICATION,
                    PROP_INITIATION_TIMEOUT_IN_MILLIS,
                    "${PROP_INITIATION_TIMEOUT_IN_MILLIS} parameter value changed, old value = '25' new value = '${value}'")

        where:
            value << [1, 100, -1, 1_000_000]
    }

    @Unroll
    def 'Should update the scanner polling period'() {
        expect: 'that the correct default value is set'
            configurationChangeListener.scannerPollingIntervalMinutes == 15

        when: 'the change listener is activated'
            configurationChangeListener.listenForScannerPollingTimeChanges(value)

        then: 'the value is updated'
            configurationChangeListener.scannerPollingIntervalMinutes == value
            1 * systemRecorder.eventCoarse(CONFIGURATION_CHANGE_NOTIFICATION,
                    PROP_SCANNER_POLLING_INTERVAL_IN_MINUTES,
                    "${PROP_SCANNER_POLLING_INTERVAL_IN_MINUTES} parameter value changed, old value = '15' new value = '${value}'")

        and: 'the timer is reset'
            1 * masterPollingSchedulerBean.resetIntervalTimer(900000, value * 60_000L)

        where:
            value << [1, 100, -1, 1_000_000]
    }

    @Unroll
    @PropertiesForTest(properties = [@SuppliedProperty(name = 'cctrSubscriptionEnabled', value = 'true')])
    def 'Should update the cctr subscription enabled parameter'() {
        expect: 'that the correct default value is set'
            configurationChangeListener.cctrSubscriptionEnabled == true

        when: 'the change listener is activated'
            configurationChangeListener.listenForCctrSubscriptionEnabledChanges(value)

        then: 'the value is updated'
            configurationChangeListener.cctrSubscriptionEnabled == value
            1 * systemRecorder.eventCoarse(CONFIGURATION_CHANGE_NOTIFICATION,
                    PROP_CCTR_SUBSCRIPTION_ENABLED,
                    "${PROP_CCTR_SUBSCRIPTION_ENABLED} parameter value changed, old value = 'true' new value = '${value}'")

        where:
            value << [true, false, new Boolean(null)]
    }

    @Unroll
    @PropertiesForTest(properties = [@SuppliedProperty(name = 'ueTraceCollectionEnabled', value = 'true')])
    def 'Should update the ue trace file collection enabled parameter'() {
        expect: 'that the correct default value is set'
            configurationChangeListener.ueTraceCollectionEnabled == true

        when: 'the change listener is activated'
            configurationChangeListener.listenForPmUeTraceCollectionEnabledChanges(value)

        then: 'the value is updated'
            configurationChangeListener.ueTraceCollectionEnabled == value
            1 * systemRecorder.eventCoarse(CONFIGURATION_CHANGE_NOTIFICATION,
                    UeTrace.PROP_COLLECTION_ENABLED,
                    "${UeTrace.PROP_COLLECTION_ENABLED} parameter value changed, old value = 'true' new value = '${value}'")

        where:
            value << [true, false, new Boolean(null)]
    }

    @Unroll
    @PropertiesForTest(properties = [@SuppliedProperty(name = 'sysDefSubscriptionAuditInterval', value = '25')])
    def 'Should update the ctum audit schedule interval'() {
        expect: 'that the correct default value is set'
            configurationChangeListener.sysDefSubscriptionAuditInterval == 25

        when: 'the change listener is activated'
            configurationChangeListener.listenForCTUMAuditScheduleInterval(value)

        then: 'the value is updated'
            configurationChangeListener.sysDefSubscriptionAuditInterval == value
            1 * systemRecorder.eventCoarse(CONFIGURATION_CHANGE_NOTIFICATION,
                    PROP_SYSTEM_DEFINED_SUBSCRIPTION_AUDIT_INTERVAL,
                    "${PROP_SYSTEM_DEFINED_SUBSCRIPTION_AUDIT_INTERVAL} parameter value changed, old value = '25' new value = '${value}'")

        and: 'scheduler is reset'
            sysDefSubscriptionAuditScheduler.resetIntervalTimer(25_000, value * 1000)

        where:
            value << [1, 100, -1, 1_000_000]
    }
}
