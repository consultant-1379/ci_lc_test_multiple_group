/*------------------------------------------------------------------------------
 *******************************************************************************
 * COPYRIGHT Ericsson 2016
 *
 * The copyright to the computer program(s) herein is the property of
 * Ericsson Inc. The programs may be used and/or copied only with written
 * permission from Ericsson Inc. or in accordance with the terms and
 * conditions stipulated in the agreement/contract under which the
 * program(s) have been supplied.
 *******************************************************************************
 *----------------------------------------------------------------------------*/

package com.ericsson.oss.services.pm.scheduler

import static com.ericsson.oss.pmic.api.constants.ModelConstants.SubscriptionConstants.CONTINUOUS_CELLTRACE_SUBSCRIPTION_NRAN_NAME
import static com.ericsson.oss.pmic.cdi.test.util.Constants.NODE_NAME_1
import static com.ericsson.oss.pmic.cdi.test.util.Constants.NODE_NAME_2
import static com.ericsson.oss.pmic.cdi.test.util.Constants.PM_ENABLED
import static com.ericsson.oss.services.pm.initiation.common.Constants.PMIC_CONTINUOUSCELLTRACE_SUBSCRIPTION_NAME

import spock.lang.Unroll

import javax.ejb.TimerService
import javax.inject.Inject

import com.ericsson.cds.cdi.support.rule.ImplementationClasses
import com.ericsson.cds.cdi.support.rule.ImplementationInstance
import com.ericsson.cds.cdi.support.rule.ObjectUnderTest
import com.ericsson.oss.itpf.datalayer.dps.persistence.ManagedObject
import com.ericsson.oss.pmic.cdi.test.util.builder.subscription.StatisticalSubscriptionBuilder
import com.ericsson.oss.pmic.dao.SubscriptionDao
import com.ericsson.oss.pmic.dto.node.Node
import com.ericsson.oss.pmic.dto.subscription.ContinuousCellTraceSubscription
import com.ericsson.oss.pmic.dto.subscription.ResourceSubscription
import com.ericsson.oss.pmic.dto.subscription.StatisticalSubscription
import com.ericsson.oss.pmic.dto.subscription.cdts.StreamInfo
import com.ericsson.oss.pmic.dto.subscription.enums.AdministrationState
import com.ericsson.oss.pmic.dto.subscription.enums.OutputModeType
import com.ericsson.oss.pmic.dto.subscription.enums.RopPeriod
import com.ericsson.oss.pmic.dto.subscription.enums.SubscriptionType
import com.ericsson.oss.pmic.dto.subscription.enums.TaskStatus
import com.ericsson.oss.pmic.dto.subscription.enums.UserType
import com.ericsson.oss.pmic.impl.counters.PmCountersLifeCycleResolverImpl
import com.ericsson.oss.pmic.impl.modelservice.PmCapabilityReaderImpl
import com.ericsson.oss.pmic.subscription.capability.SubscriptionCapabilityReader
import com.ericsson.oss.services.pm.PmServiceEjbFullSpec
import com.ericsson.oss.services.pm.initiation.model.metadata.PMICModelDeploymentValidator
import com.ericsson.oss.services.pm.initiation.notification.events.Activate
import com.ericsson.oss.services.pm.initiation.notification.events.Deactivate
import com.ericsson.oss.services.pm.initiation.notification.events.InitiationEvent
import com.ericsson.oss.services.pm.scheduling.impl.SystemDefinedSubscriptionAuditScheduler
import com.ericsson.oss.services.pm.services.generic.SubscriptionReadOperationService
import com.ericsson.oss.services.pm.services.generic.SubscriptionWriteOperationService

/***
 * This class will test System Defined Subscription creation and deletion.
 */
class SystemDefinedSubscriptionAuditSchedulerSpec extends PmServiceEjbFullSpec {

    @ObjectUnderTest
    SystemDefinedSubscriptionAuditScheduler systemDefinedSubscriptionAuditScheduler
    @Inject
    SubscriptionDao subscriptionDao
    @Inject
    SubscriptionReadOperationService subscriptionReadOperationService
    @Inject
    SubscriptionWriteOperationService subscriptionWriteOperationService
    @ImplementationInstance
    TimerService timerService = Mock(TimerService)
    @ImplementationInstance
    PMICModelDeploymentValidator pmicModelDeploymentValidator = Mock(PMICModelDeploymentValidator)
    @Inject
    SubscriptionCapabilityReader capabilityReader
    @ImplementationInstance
    @Activate
    InitiationEvent activationEvent = Mock(InitiationEvent)
    @ImplementationInstance
    @Deactivate
    InitiationEvent deactivationEvent = Mock(InitiationEvent)

    @ImplementationClasses
    def classes = [PmCapabilityReaderImpl.class, PmCountersLifeCycleResolverImpl.class]

    ManagedObject subscriptionMO

    def setup() {
        timerService.timers >> []
    }

    private static final String ERBS_STAT_SYS_DEF_SUBSCRIPTION = 'ERBS System Defined Statistical Subscription'
    private static final String RBS_STAT_SYS_DEF_SUBSCRIPTION = 'RBS System Defined Statistical Subscription'
    private static final String RNC_PRI_STAT_SYS_DEF_SUBSCRIPTION = 'RNC Primary System Defined Statistical Subscription'
    private static final String RNC_SEC_STAT_SYS_DEF_SUBSCRIPTION = 'RNC Secondary System Defined Statistical Subscription'
    private static final String RADIONODE_STAT_SYS_DEF_SUBSCRIPTION = 'RadioNode System Defined Statistical Subscription'
    private static final String EPG_VEPG_STAT_SYS_DEF_SUBSCRIPTION = '(v)EPG Statistical Subscription'
    private static final String FRONTHAUL_6080_15MIN_STAT_SYS_DEF_SUBSCRIPTION = 'FRONTHAUL-6080 Primary15min System Defined Statistical Subscription'
    private static final String FRONTHAUL_6080_24HR_STAT_SYS_DEF_SUBSCRIPTION = 'FRONTHAUL-6080 Primary24h System Defined Statistical Subscription'
    private static final String CCTR_SYS_DEF_SUBSCRIPTION = 'ContinuousCellTraceSubscription'
    private static final String CCTR_NRAN_SYS_DEF_SUBSCRIPTION = 'Continuous Cell Trace NRAN'
    private static final String FIVE_G_STAT_SYS_DEF_SUBSCRIPTION = '5GRadioNode System Defined Statistical Subscription'

    private static final String FRONTHAUL_6080_NODE = 'FRONTHAUL-6080'
    private static final String FRONTHAUL_6080_OSS_MODEL = '17B'
    private static final String RNC_NODE = 'RNC'
    private static final String RNC_OSS_MODEL = '16B-V.7.1659'
    private static final String ERBS_NODE = 'ERBS'
    private static final String ERBS_OSS_MODEL = '16b-G.1.281'
    private static final String RBS_NODE = 'RBS'
    private static final String RBS_OSS_MODEL = '17A-U.4.460'
    private static final String RADIONODE_NODE = 'RadioNode'
    private static final String RADIONODE_OSS_MODEL = '16B-R28GY'
    private static final String RADIONODE_5GS_OSS_MODEL = '19.Q1-R31A14'
    private static final String RADIONODE_MIXEDMODE_OSS_MODEL = '19.Q1-R31A14'
    private static final String EPG_NODE = 'EPG'
    private static final String EPG_OSS_MODEL = '16B-R13C'
    private static final String VEPG_NODE = 'VEPG'
    private static final String VEPG_OSS_MODEL = '16B-R13C'
    private static final String MSRBSV1_NODE = 'MSRBS_V1'
    private static final String MSRBSV1_OSS_MODEL = '16a-R9A'
    private static final String SGSN_NODE = 'SGSN-MME'
    private static final String SGSN_OSS_MODEL = '6607-651-025'
    private static final String FIVE_G_OSS_MODEL = '18.Q3.2'
    private static final String FIVE_G_OSS_MODEL_18_Q4 = '18.Q4-R5A77'
    private static final String FIVE_G_OSS_MODEL_19_Q2 = '19.Q2-R15A16'
    private static final String FIVE_G_NODE = '5GRadioNode'

    @Unroll
    def 'On Timeout CTUM Subscription should be created or deleted when pmEnable is true'() {
        given: 'Add SGSN-MME nodes in DPS with pmEnable true'
            def sgsnMmeNodes = [nodeUtil.builder('SGSN-16A-V1-CP0220').neType(SGSN_NODE).ossModelIdentity(SGSN_OSS_MODEL).build(),
                                nodeUtil.builder('SGSN-16A-V1-CP0221').neType(SGSN_NODE).ossModelIdentity(SGSN_OSS_MODEL).build()]
            nodeUtil.builder('1').neType(EPG_NODE).ossModelIdentity(EPG_OSS_MODEL).build()
            nodeUtil.builder('2').neType(ERBS_NODE).ossModelIdentity(ERBS_OSS_MODEL).build()
            nodeUtil.builder('3').neType(EPG_NODE).ossModelIdentity('16B-R14C').build()
        when: 'ConfigurationSyncScheduler Timeout Occurs'
            systemDefinedSubscriptionAuditScheduler.onTimeout()
        then: 'Subscription Object should exist in DPS'
            subscriptionDao.existsBySubscriptionName('CTUM')
        when: 'Delete All SGSN-MME nodes from DPS'
            nodeUtil.deleteManagedObjects(sgsnMmeNodes)
        and: 'ON CTUM AUDIT, DPS should delete subscription Object'
            systemDefinedSubscriptionAuditScheduler.onTimeout()
        then: 'CTUM Subscription no longer exist in DPS'
            !subscriptionDao.existsBySubscriptionName('CTUM')
    }

    @Unroll
    def 'Continuous Cell Trace NRAN subscription is created on audit when a node with technologyDomain #technologyDomain and neType #neType is present in the dps'() {
        given: 'one node present in the dps with PmFunction enabled'
            nodeUtil.builder(NODE_NAME_1)
                .technologyDomain(technologyDomain)
                .neType(neType)
                .pmEnabled(true)
                .build()
        when: 'subscription audit scheduler runs'
            systemDefinedSubscriptionAuditScheduler.onTimeout()
        then: 'the Continuous Cell Trace NRAN subscription is created in dps'
            subscriptionDao.existsBySubscriptionName(CCTR_NRAN_SYS_DEF_SUBSCRIPTION)
        and: 'subscription contains one node'
            def subscription = subscriptionDao.findOneByExactName(CCTR_NRAN_SYS_DEF_SUBSCRIPTION, true)
            (subscription as ContinuousCellTraceSubscription).getNumberOfNodes() == 1
        where:
            technologyDomain      | neType
            ['5GS']               | RADIONODE_NODE
            ['EPS', '5GS']        | RADIONODE_NODE
            [null, '5GS']         | RADIONODE_NODE
            ["5GS", "5GS", "5GS"] | RADIONODE_NODE
            ['EPS']               | FIVE_G_NODE
            ['EPS', '5GS']        | FIVE_G_NODE
            ['']                  | FIVE_G_NODE
            [null]                | FIVE_G_NODE
    }

    @Unroll
    def 'Continuous Cell Trace NRAN subscription is NOT created on audit when a node with technologyDomain #technologyDomain and neType #neType is present in the dps'() {
        given: 'one node present in the dps with PmFunction enabled'
            nodeUtil.builder(NODE_NAME_1)
                .technologyDomain(technologyDomain)
                .neType(neType)
                .pmEnabled(true)
                .build()
        when: 'subscription audit scheduler runs'
            systemDefinedSubscriptionAuditScheduler.onTimeout()
        then: 'the Continuous Cell Trace NRAN subscription is not created in dps'
            !subscriptionDao.existsBySubscriptionName(CCTR_NRAN_SYS_DEF_SUBSCRIPTION)
        where:
            technologyDomain | neType
            ['EPS', 'EPS']   | RADIONODE_NODE
            ['EPS']          | RADIONODE_NODE
            ['UMTS']         | RADIONODE_NODE
            ['']             | RADIONODE_NODE
            [null]           | RADIONODE_NODE
    }

    @Unroll
    def 'On Timeout when the last remaining Node in a #subscriptionDeleted subscription is deleted and a #subscriptionNotDeleted subscription exists, only the #subscriptionDeleted subscription is deleted'() {
        given: 'Both 4G RadioNode & RadioNode_NRAT added with both pmEnables set to true'
            def fourGRadioNode = nodeUtil.builder(NODE_NAME_1)
                .technologyDomain(["EPS"])
                .neType('RadioNode')
                .ossModelIdentity('19.Q1-R31A16')
                .pmEnabled(true)
                .build()
            def fiveGRadioNode = nodeUtil.builder(NODE_NAME_2)
                .technologyDomain(["5GS"])
                .neType('RadioNode')
                .ossModelIdentity('19.Q1-R31A16')
                .pmEnabled(true)
                .build()
        when: 'SystemDefinedSubscriptionAuditScheduler Timeout Occurs'
            systemDefinedSubscriptionAuditScheduler.onTimeout()
        then: 'Both subscriptions created'
            subscriptionDao.existsBySubscriptionName(CCTR_SYS_DEF_SUBSCRIPTION)
            subscriptionDao.existsBySubscriptionName(CONTINUOUS_CELLTRACE_SUBSCRIPTION_NRAN_NAME)
        when: 'A node is deleted and another audit is ran'
            nodeToDelete.equals("4G") ? liveBucket().deletePo(fourGRadioNode) : liveBucket().deletePo(fiveGRadioNode)
            systemDefinedSubscriptionAuditScheduler.onTimeout()
        then: 'Only the correct subscription remains'
            !subscriptionDao.existsBySubscriptionName(subscriptionDeleted)
            subscriptionDao.existsBySubscriptionName(subscriptionNotDeleted)
        where:
            nodeToDelete || subscriptionDeleted                         || subscriptionNotDeleted
            "4G"         || CCTR_SYS_DEF_SUBSCRIPTION                   || CONTINUOUS_CELLTRACE_SUBSCRIPTION_NRAN_NAME
            "5G"         || CONTINUOUS_CELLTRACE_SUBSCRIPTION_NRAN_NAME || CCTR_SYS_DEF_SUBSCRIPTION
    }

    def 'on timeout, CTUM subscription will not be created if SGSN MME nodes exist with pm function OFF'() {
        given: 'Add SGSN-MME nodes in DPS with pmEnable true'
            nodeUtil.builder('SGSN-16A-V1-CP0220').neType(SGSN_NODE).ossModelIdentity(SGSN_OSS_MODEL).pmEnabled(false).build()
            nodeUtil.builder('SGSN-16A-V1-CP0221').neType(SGSN_NODE).ossModelIdentity(SGSN_OSS_MODEL).pmEnabled(false).build()
        when:
            systemDefinedSubscriptionAuditScheduler.onTimeout()
        then:
            subscriptionDao.findAll().empty
    }

    def 'on timeout, CTUM subscription will not be deleted if no SGSN MME nodes exist with pm function ON'() {
        given: 'Add SGSN-MME nodes in DPS with pmEnable true'
            def node = nodeUtil.builder('SGSN-16A-V1-CP0220').neType(SGSN_NODE).ossModelIdentity(SGSN_OSS_MODEL).pmEnabled(true).build()
            nodeUtil.builder('SGSN-16A-V1-CP0221').neType(SGSN_NODE).ossModelIdentity(SGSN_OSS_MODEL).pmEnabled(false).build()
        when:
            systemDefinedSubscriptionAuditScheduler.onTimeout()
        then:
            subscriptionDao.existsBySubscriptionName('CTUM')
        when:
            liveBucket().deletePo(node)
            systemDefinedSubscriptionAuditScheduler.onTimeout()
        then:
            subscriptionDao.findAll().empty
    }

    @Unroll
    def 'On Timeout subscription #subscriptionName for #nodeType should be created when pmEnable is true, only pmEnabled=true nodes added'() {
        given: 'Add #nodeType nodes in DPS with pmEnable true'
            dps.node().name('1').ossModelIdentity(ossModel).neType(nodeType).build()
            dps.node().name('2').ossModelIdentity(ossModel).neType(nodeType).build()
            dps.node().name('3').ossModelIdentity(ossModel).neType(nodeType).pmEnabled(false).build()
        when: 'ConfigurationSyncScheduler Timeout Occurs'
            systemDefinedSubscriptionAuditScheduler.onTimeout()
            def subscription = subscriptionDao.findOneByExactName(subscriptionName, true) as ResourceSubscription
        then: 'Subscription Object should exist in DPS and is of type StatisticalSubscription'
            subscription.nodes.collect({ it.name }).sort() == ['1', '2']
            subscription.numberOfNodes == 2
        when: 'New nodes added to DPS'
            dps.node().name('4').ossModelIdentity(ossModel).neType(nodeType).build()
            dps.node().name('5').ossModelIdentity(ossModel).neType(nodeType).build()
            dps.node().name('6').ossModelIdentity(ossModel).neType(nodeType).pmEnabled(false).build()
        and: 'Subscription audit runs again'
            systemDefinedSubscriptionAuditScheduler.onTimeout()
            subscription = subscriptionDao.findOneByExactName(subscriptionName, true)
        then: 'Subscription is updated with new nodes pmEnabled = true'
            (subscription as ResourceSubscription).getNodes().collect({ it.name }).sort() == ['1', '2', '4', '5']
        where:
            nodeType            | ossModel                 | subscriptionName
            FIVE_G_NODE         | FIVE_G_OSS_MODEL         | FIVE_G_STAT_SYS_DEF_SUBSCRIPTION
            ERBS_NODE           | ERBS_OSS_MODEL           | ERBS_STAT_SYS_DEF_SUBSCRIPTION
            RNC_NODE            | RNC_OSS_MODEL            | RNC_SEC_STAT_SYS_DEF_SUBSCRIPTION
            RNC_NODE            | RNC_OSS_MODEL            | RNC_PRI_STAT_SYS_DEF_SUBSCRIPTION
            RBS_NODE            | RBS_OSS_MODEL            | RBS_STAT_SYS_DEF_SUBSCRIPTION
            RADIONODE_NODE      | RADIONODE_OSS_MODEL      | RADIONODE_STAT_SYS_DEF_SUBSCRIPTION
            EPG_NODE            | EPG_OSS_MODEL            | EPG_VEPG_STAT_SYS_DEF_SUBSCRIPTION
            VEPG_NODE           | VEPG_OSS_MODEL           | EPG_VEPG_STAT_SYS_DEF_SUBSCRIPTION
            FRONTHAUL_6080_NODE | FRONTHAUL_6080_OSS_MODEL | FRONTHAUL_6080_15MIN_STAT_SYS_DEF_SUBSCRIPTION
            FRONTHAUL_6080_NODE | FRONTHAUL_6080_OSS_MODEL | FRONTHAUL_6080_24HR_STAT_SYS_DEF_SUBSCRIPTION
            ERBS_NODE           | ERBS_OSS_MODEL           | CCTR_SYS_DEF_SUBSCRIPTION
            MSRBSV1_NODE        | MSRBSV1_OSS_MODEL        | CCTR_SYS_DEF_SUBSCRIPTION
            RADIONODE_NODE      | RADIONODE_OSS_MODEL      | CCTR_SYS_DEF_SUBSCRIPTION
            FIVE_G_NODE         | FIVE_G_OSS_MODEL         | CCTR_NRAN_SYS_DEF_SUBSCRIPTION
    }

    @Unroll
    def 'On Timeout ACTIVE subscription #subscription should be updated with new node and activation request sent'() {
        given:
            dps.node().name('1').ossModelIdentity(ossModel).neType(nodeType).build()
            def nodeToRemoveLater = dps.node().name('2').ossModelIdentity(ossModel).neType(nodeType).build()
            systemDefinedSubscriptionAuditScheduler.onTimeout()
            ManagedObject sub = liveBucket().findMoByFdn(subscriptionType + '=' + subscriptionName)
            sub.setAttribute('administrationState', 'ACTIVE')
        and: 'one node is added and other removed'
            dps.node().name('3').ossModelIdentity(ossModel).neType(nodeType).build()
            sub.removeAssociation('nodes', nodeToRemoveLater)
            liveBucket().deletePo(nodeToRemoveLater)
        when: 'audit is called, subscription will add a node and fire respective event'
            systemDefinedSubscriptionAuditScheduler.onTimeout()
        then:
            sub.getAssociations('nodes').size() == 2
            sub.getAssociations('nodes').each {
                ((ManagedObject) it).name != '2'
            }
            1 * activationEvent.execute({ it -> it.get(0).fdn == 'NetworkElement=3' } as List<Node>, _ as ResourceSubscription)
        where:
            nodeType            | ossModel                 | subscriptionName                               | subscriptionType
            ERBS_NODE           | ERBS_OSS_MODEL           | ERBS_STAT_SYS_DEF_SUBSCRIPTION                 | 'StatisticalSubscription'
            RNC_NODE            | RNC_OSS_MODEL            | RNC_SEC_STAT_SYS_DEF_SUBSCRIPTION              | 'StatisticalSubscription'
            RNC_NODE            | RNC_OSS_MODEL            | RNC_PRI_STAT_SYS_DEF_SUBSCRIPTION              | 'StatisticalSubscription'
            RBS_NODE            | RBS_OSS_MODEL            | RBS_STAT_SYS_DEF_SUBSCRIPTION                  | 'StatisticalSubscription'
            RADIONODE_NODE      | RADIONODE_OSS_MODEL      | RADIONODE_STAT_SYS_DEF_SUBSCRIPTION            | 'StatisticalSubscription'
            EPG_NODE            | EPG_OSS_MODEL            | EPG_VEPG_STAT_SYS_DEF_SUBSCRIPTION             | 'StatisticalSubscription'
            VEPG_NODE           | VEPG_OSS_MODEL           | EPG_VEPG_STAT_SYS_DEF_SUBSCRIPTION             | 'StatisticalSubscription'
            FRONTHAUL_6080_NODE | FRONTHAUL_6080_OSS_MODEL | FRONTHAUL_6080_15MIN_STAT_SYS_DEF_SUBSCRIPTION | 'StatisticalSubscription'
            FRONTHAUL_6080_NODE | FRONTHAUL_6080_OSS_MODEL | FRONTHAUL_6080_24HR_STAT_SYS_DEF_SUBSCRIPTION  | 'StatisticalSubscription'
            ERBS_NODE           | ERBS_OSS_MODEL           | CCTR_SYS_DEF_SUBSCRIPTION                      | 'ContinuousCellTraceSubscription'
            MSRBSV1_NODE        | MSRBSV1_OSS_MODEL        | CCTR_SYS_DEF_SUBSCRIPTION                      | 'ContinuousCellTraceSubscription'
            RADIONODE_NODE      | RADIONODE_OSS_MODEL      | CCTR_SYS_DEF_SUBSCRIPTION                      | 'ContinuousCellTraceSubscription'
            FIVE_G_NODE         | FIVE_G_OSS_MODEL         | CCTR_NRAN_SYS_DEF_SUBSCRIPTION                 | 'ContinuousCellTraceSubscription'
    }

    @Unroll
    def 'Active #subscriptionName subscription will add new nodes and remove nodes on audit and send activation/deactivation event'() {
        given:
            dps.node().name('1').ossModelIdentity(ossModel).neType(nodeType).pmEnabled(true).build()
            def nodeToChangePmFunctionOnLater = dps.node().name('2').ossModelIdentity(ossModel).neType(nodeType).pmEnabled(true).build()
            systemDefinedSubscriptionAuditScheduler.onTimeout()

            ManagedObject sub = liveBucket().findMoByFdn(subscriptionType + '=' + subscriptionName)
            sub.setAttribute('administrationState', 'ACTIVE')
        and: "one node is added and other's pm enabled is set to false"
            dps.node().name('3').ossModelIdentity(ossModel).neType(nodeType).pmEnabled(true).build()
            nodeToChangePmFunctionOnLater.getChild('PmFunction=1').setAttribute('pmEnabled', false)
        when: 'audit is called, subscription will remove one node and add another and fire respective events'
            systemDefinedSubscriptionAuditScheduler.onTimeout()
        then:
            sub.getAssociations('nodes').size() == 2
            sub.getAssociations('nodes').each { ((ManagedObject) it).getName() != '2' }
            1 * activationEvent.execute({ it -> it.get(0).fdn == 'NetworkElement=3' } as List<Node>, _ as ResourceSubscription)
            1 * deactivationEvent.execute({ it -> it.get(0).fdn == 'NetworkElement=2' } as List<Node>, _ as ResourceSubscription)
        where:
            nodeType            | ossModel                 | subscriptionName                               | subscriptionType
            ERBS_NODE           | ERBS_OSS_MODEL           | ERBS_STAT_SYS_DEF_SUBSCRIPTION                 | 'StatisticalSubscription'
            RNC_NODE            | RNC_OSS_MODEL            | RNC_SEC_STAT_SYS_DEF_SUBSCRIPTION              | 'StatisticalSubscription'
            RNC_NODE            | RNC_OSS_MODEL            | RNC_PRI_STAT_SYS_DEF_SUBSCRIPTION              | 'StatisticalSubscription'
            RBS_NODE            | RBS_OSS_MODEL            | RBS_STAT_SYS_DEF_SUBSCRIPTION                  | 'StatisticalSubscription'
            RADIONODE_NODE      | RADIONODE_OSS_MODEL      | RADIONODE_STAT_SYS_DEF_SUBSCRIPTION            | 'StatisticalSubscription'
            EPG_NODE            | EPG_OSS_MODEL            | EPG_VEPG_STAT_SYS_DEF_SUBSCRIPTION             | 'StatisticalSubscription'
            VEPG_NODE           | VEPG_OSS_MODEL           | EPG_VEPG_STAT_SYS_DEF_SUBSCRIPTION             | 'StatisticalSubscription'
            FRONTHAUL_6080_NODE | FRONTHAUL_6080_OSS_MODEL | FRONTHAUL_6080_15MIN_STAT_SYS_DEF_SUBSCRIPTION | 'StatisticalSubscription'
            FRONTHAUL_6080_NODE | FRONTHAUL_6080_OSS_MODEL | FRONTHAUL_6080_24HR_STAT_SYS_DEF_SUBSCRIPTION  | 'StatisticalSubscription'
            ERBS_NODE           | ERBS_OSS_MODEL           | CCTR_SYS_DEF_SUBSCRIPTION                      | 'ContinuousCellTraceSubscription'
            MSRBSV1_NODE        | MSRBSV1_OSS_MODEL        | CCTR_SYS_DEF_SUBSCRIPTION                      | 'ContinuousCellTraceSubscription'
            RADIONODE_NODE      | RADIONODE_OSS_MODEL      | CCTR_SYS_DEF_SUBSCRIPTION                      | 'ContinuousCellTraceSubscription'
            FIVE_G_NODE         | FIVE_G_OSS_MODEL         | CCTR_NRAN_SYS_DEF_SUBSCRIPTION                 | 'ContinuousCellTraceSubscription'

    }

    @Unroll
    def 'On Timeout #subscriptionName should be created when pmEnable is true on 2 nodes but only the node with valid ossModelIdentity has to be added'() {
        given: 'Add #nodeType nodes in DPS with pmEnable true'
            dps.node().name('1').ossModelIdentity(ossModel).neType(nodeType).build()
            dps.node().name('2').ossModelIdentity('').neType(nodeType).build()
        when: 'ConfigurationSyncScheduler Timeout Occurs'
            systemDefinedSubscriptionAuditScheduler.onTimeout()
            def subscription = subscriptionDao.findOneByExactName(subscriptionName, true) as ResourceSubscription
        then: 'Subscription Object should exist in DPS and is of type StatisticalSubscription'
            subscription.nodes.collect({ it.name }).sort() == ['1']
            subscription.numberOfNodes == 1
        where:
            nodeType            | ossModel                 | subscriptionName
            FIVE_G_NODE         | FIVE_G_OSS_MODEL         | FIVE_G_STAT_SYS_DEF_SUBSCRIPTION
            ERBS_NODE           | ERBS_OSS_MODEL           | ERBS_STAT_SYS_DEF_SUBSCRIPTION
            RNC_NODE            | RNC_OSS_MODEL            | RNC_SEC_STAT_SYS_DEF_SUBSCRIPTION
            RNC_NODE            | RNC_OSS_MODEL            | RNC_PRI_STAT_SYS_DEF_SUBSCRIPTION
            RBS_NODE            | RBS_OSS_MODEL            | RBS_STAT_SYS_DEF_SUBSCRIPTION
            RADIONODE_NODE      | RADIONODE_OSS_MODEL      | RADIONODE_STAT_SYS_DEF_SUBSCRIPTION
            FRONTHAUL_6080_NODE | FRONTHAUL_6080_OSS_MODEL | FRONTHAUL_6080_15MIN_STAT_SYS_DEF_SUBSCRIPTION
            FRONTHAUL_6080_NODE | FRONTHAUL_6080_OSS_MODEL | FRONTHAUL_6080_24HR_STAT_SYS_DEF_SUBSCRIPTION
            ERBS_NODE           | ERBS_OSS_MODEL           | CCTR_SYS_DEF_SUBSCRIPTION
            MSRBSV1_NODE        | MSRBSV1_OSS_MODEL        | CCTR_SYS_DEF_SUBSCRIPTION
            RADIONODE_NODE      | RADIONODE_OSS_MODEL      | CCTR_SYS_DEF_SUBSCRIPTION
            FIVE_G_NODE         | FIVE_G_OSS_MODEL         | CCTR_NRAN_SYS_DEF_SUBSCRIPTION
    }

    @Unroll
    def 'On Timeout for node #nodeType subscription will be created even if ossModelIdentity is not present because metadata validation is not required'() {
        given: 'Add #nodeType nodes in DPS with pmEnable true'
            dps.node().name('1').ossModelIdentity(ossModel).neType(nodeType).build()
            dps.node().name('2').ossModelIdentity('').neType(nodeType).build()
        when: 'ConfigurationSyncScheduler Timeout Occurs'
            systemDefinedSubscriptionAuditScheduler.onTimeout()
            def subscription = subscriptionDao.findOneByExactName(subscriptionName, true) as ResourceSubscription
        then: 'Subscription Object should exist in DPS and is of type StatisticalSubscription'
            subscription.nodes.collect({ it.name }).sort() == ['1', '2']
            subscription.numberOfNodes == 2
        where:
            nodeType  | ossModel       | subscriptionName
            EPG_NODE  | EPG_OSS_MODEL  | EPG_VEPG_STAT_SYS_DEF_SUBSCRIPTION
            VEPG_NODE | VEPG_OSS_MODEL | EPG_VEPG_STAT_SYS_DEF_SUBSCRIPTION
    }

    @Unroll
    def 'On Timeout #subscriptionName should not be created when pmEnable is false for all nodes'() {
        given:
            dps.node().name('1').ossModelIdentity(ossModel).neType(nodeType).pmEnabled(false).build()
        when: 'ConfigurationSyncScheduler Timeout Occurs'
            systemDefinedSubscriptionAuditScheduler.onTimeout()
        then: 'Subscription #subscriptionName no longer exist in DPS'
            subscriptionDao.findAll().empty
        where:
            nodeType            | ossModel                 | subscriptionName
            FIVE_G_NODE         | FIVE_G_OSS_MODEL         | FIVE_G_STAT_SYS_DEF_SUBSCRIPTION
            ERBS_NODE           | ERBS_OSS_MODEL           | ERBS_STAT_SYS_DEF_SUBSCRIPTION
            RNC_NODE            | RNC_OSS_MODEL            | RNC_SEC_STAT_SYS_DEF_SUBSCRIPTION
            RNC_NODE            | RNC_OSS_MODEL            | RNC_PRI_STAT_SYS_DEF_SUBSCRIPTION
            RBS_NODE            | RBS_OSS_MODEL            | RBS_STAT_SYS_DEF_SUBSCRIPTION
            RADIONODE_NODE      | RADIONODE_OSS_MODEL      | RADIONODE_STAT_SYS_DEF_SUBSCRIPTION
            EPG_NODE            | EPG_OSS_MODEL            | EPG_VEPG_STAT_SYS_DEF_SUBSCRIPTION
            VEPG_NODE           | VEPG_OSS_MODEL           | EPG_VEPG_STAT_SYS_DEF_SUBSCRIPTION
            FRONTHAUL_6080_NODE | FRONTHAUL_6080_OSS_MODEL | FRONTHAUL_6080_15MIN_STAT_SYS_DEF_SUBSCRIPTION
            FRONTHAUL_6080_NODE | FRONTHAUL_6080_OSS_MODEL | FRONTHAUL_6080_24HR_STAT_SYS_DEF_SUBSCRIPTION
            ERBS_NODE           | ERBS_OSS_MODEL           | CCTR_SYS_DEF_SUBSCRIPTION
            MSRBSV1_NODE        | MSRBSV1_OSS_MODEL        | CCTR_SYS_DEF_SUBSCRIPTION
            RADIONODE_NODE      | RADIONODE_OSS_MODEL      | CCTR_SYS_DEF_SUBSCRIPTION
            FIVE_G_NODE         | FIVE_G_OSS_MODEL         | CCTR_NRAN_SYS_DEF_SUBSCRIPTION
    }

    @Unroll
    def 'On Timeout #subscriptionName should be deleted if all nodes have pm function off'() {
        given:
            def nodeMO = dps.node().name('1').ossModelIdentity(ossModel).neType(nodeType).pmEnabled(false).build()
            dps.subscription().
                type(subscriptionType).
                name(subscriptionName).
                nodes(nodeMO).
                administrationState(AdministrationState.INACTIVE).
                build()
        when:
            systemDefinedSubscriptionAuditScheduler.onTimeout()
        then: 'Subscription #subscriptionName no longer exist in DPS'
            subscriptionDao.findAll().empty
        where:
            nodeType            | subscriptionType                     | ossModel                 | subscriptionName
            FIVE_G_NODE         | SubscriptionType.STATISTICAL         | FIVE_G_OSS_MODEL         | FIVE_G_STAT_SYS_DEF_SUBSCRIPTION
            ERBS_NODE           | SubscriptionType.STATISTICAL         | ERBS_OSS_MODEL           | ERBS_STAT_SYS_DEF_SUBSCRIPTION
            RNC_NODE            | SubscriptionType.STATISTICAL         | RNC_OSS_MODEL            | RNC_SEC_STAT_SYS_DEF_SUBSCRIPTION
            RNC_NODE            | SubscriptionType.STATISTICAL         | RNC_OSS_MODEL            | RNC_PRI_STAT_SYS_DEF_SUBSCRIPTION
            RBS_NODE            | SubscriptionType.STATISTICAL         | RBS_OSS_MODEL            | RBS_STAT_SYS_DEF_SUBSCRIPTION
            RADIONODE_NODE      | SubscriptionType.STATISTICAL         | RADIONODE_OSS_MODEL      | RADIONODE_STAT_SYS_DEF_SUBSCRIPTION
            EPG_NODE            | SubscriptionType.STATISTICAL         | EPG_OSS_MODEL            | EPG_VEPG_STAT_SYS_DEF_SUBSCRIPTION
            VEPG_NODE           | SubscriptionType.STATISTICAL         | VEPG_OSS_MODEL           | EPG_VEPG_STAT_SYS_DEF_SUBSCRIPTION
            FRONTHAUL_6080_NODE | SubscriptionType.STATISTICAL         | FRONTHAUL_6080_OSS_MODEL | FRONTHAUL_6080_15MIN_STAT_SYS_DEF_SUBSCRIPTION
            FRONTHAUL_6080_NODE | SubscriptionType.STATISTICAL         | FRONTHAUL_6080_OSS_MODEL | FRONTHAUL_6080_24HR_STAT_SYS_DEF_SUBSCRIPTION
            ERBS_NODE           | SubscriptionType.CONTINUOUSCELLTRACE | ERBS_OSS_MODEL           | CCTR_SYS_DEF_SUBSCRIPTION
            MSRBSV1_NODE        | SubscriptionType.CONTINUOUSCELLTRACE | MSRBSV1_OSS_MODEL        | CCTR_SYS_DEF_SUBSCRIPTION
            RADIONODE_NODE      | SubscriptionType.CONTINUOUSCELLTRACE | RADIONODE_OSS_MODEL      | CCTR_SYS_DEF_SUBSCRIPTION
            FIVE_G_NODE         | SubscriptionType.CONTINUOUSCELLTRACE | FIVE_G_OSS_MODEL         | CCTR_NRAN_SYS_DEF_SUBSCRIPTION
    }

    @Unroll
    def 'On Timeout #subscriptionName should be deleted if no more nodes exist'() {
        given:
            dps.subscription().
                type(subscriptionType).
                name(subscriptionName).
                administrationState(AdministrationState.INACTIVE).
                build()
        when:
            systemDefinedSubscriptionAuditScheduler.onTimeout()
        then: 'Subscription #subscriptionName no longer exist in DPS'
            subscriptionDao.findAll().empty
        where:
            subscriptionName                               | subscriptionType
            FIVE_G_STAT_SYS_DEF_SUBSCRIPTION               | SubscriptionType.STATISTICAL
            ERBS_STAT_SYS_DEF_SUBSCRIPTION                 | SubscriptionType.STATISTICAL
            RNC_SEC_STAT_SYS_DEF_SUBSCRIPTION              | SubscriptionType.STATISTICAL
            RNC_PRI_STAT_SYS_DEF_SUBSCRIPTION              | SubscriptionType.STATISTICAL
            RBS_STAT_SYS_DEF_SUBSCRIPTION                  | SubscriptionType.STATISTICAL
            RADIONODE_STAT_SYS_DEF_SUBSCRIPTION            | SubscriptionType.STATISTICAL
            EPG_VEPG_STAT_SYS_DEF_SUBSCRIPTION             | SubscriptionType.STATISTICAL
            EPG_VEPG_STAT_SYS_DEF_SUBSCRIPTION             | SubscriptionType.STATISTICAL
            FRONTHAUL_6080_15MIN_STAT_SYS_DEF_SUBSCRIPTION | SubscriptionType.STATISTICAL
            FRONTHAUL_6080_24HR_STAT_SYS_DEF_SUBSCRIPTION  | SubscriptionType.STATISTICAL
            CCTR_SYS_DEF_SUBSCRIPTION                      | SubscriptionType.CONTINUOUSCELLTRACE
            CCTR_NRAN_SYS_DEF_SUBSCRIPTION                 | SubscriptionType.CONTINUOUSCELLTRACE
    }

    def 'On Timeout CCTR Subscription should be created with 3 node types in CCTR and adminState INACTIVE'() {
        given:
            dps.node().name('1').ossModelIdentity(ERBS_OSS_MODEL).neType(ERBS_NODE).build()
            dps.node().name('2').ossModelIdentity(MSRBSV1_OSS_MODEL).neType(MSRBSV1_NODE).build()
            dps.node().name('3').ossModelIdentity(RADIONODE_OSS_MODEL).neType(RADIONODE_NODE).build()
        when:
            systemDefinedSubscriptionAuditScheduler.onTimeout()
            def subscription = subscriptionDao.findOneByExactName(CCTR_SYS_DEF_SUBSCRIPTION, true) as ContinuousCellTraceSubscription
        then:
            subscription.events.size() == 26
            subscription.events.collect({ it.groupName }).every({ it -> it == 'CCTR' })
            subscription.nodes.collect({ it.name }).sort() == ['1', '2', '3']
            subscription.numberOfNodes == 3
            subscription.administrationState == AdministrationState.INACTIVE
    }

    @Unroll
    def 'CCTR subscription should be created with properly filtered events'() {
        given: 'number of nodes'
            neType.eachWithIndex { String entry, int i ->
                dps.node().name(i as String).ossModelIdentity(ossModelIdentity[i]).neType(neType[i]).technologyDomain(technologyDomain[i]).build()
            }
        when: 'subscription audit scheduler runs'
            systemDefinedSubscriptionAuditScheduler.onTimeout()
        then: 'the #expectedSubscriptionName subscription is created in dps'
            def subscription = subscriptionDao.findOneByExactName(expectedSubscriptionName, true) as ContinuousCellTraceSubscription
        and: ' the subscription contains the correct data'
            subscription.events.size() == expectedNumOfEvents
            subscription.events.collect({ it.groupName }).every({ it -> it == 'CCTR' })
            subscription.events.collect({ it.eventProducerId }).every({ it -> supportedEventProducerIds.contains(it) })
        where: 'nodes with the following details are present'
            technologyDomain            | neType                                    | ossModelIdentity                                         | supportedEventProducerIds || expectedSubscriptionName       || expectedNumOfEvents
            [['EPS'], ['UMTS', 'EPS']]  | [ERBS_NODE, MSRBSV1_NODE]                 | [ERBS_OSS_MODEL, MSRBSV1_OSS_MODEL]                      | ['Lrat', null]            || CCTR_SYS_DEF_SUBSCRIPTION      || 0
            [['EPS'], ['EPS'], ['EPS']] | [ERBS_NODE, MSRBSV1_NODE, RADIONODE_NODE] | [ERBS_OSS_MODEL, MSRBSV1_OSS_MODEL, RADIONODE_OSS_MODEL] | ['Lrat', null]            || CCTR_SYS_DEF_SUBSCRIPTION      || 26
            [['EPS'], ['5GS']]          | [MSRBSV1_NODE, RADIONODE_NODE]            | [MSRBSV1_OSS_MODEL, RADIONODE_5GS_OSS_MODEL]             | ['Lrat', null]            || CCTR_SYS_DEF_SUBSCRIPTION      || 0
            [['EPS'], ['5GS']]          | [RADIONODE_NODE, RADIONODE_NODE]          | [RADIONODE_OSS_MODEL, RADIONODE_5GS_OSS_MODEL]           | ['Lrat', null]            || CCTR_SYS_DEF_SUBSCRIPTION      || 26
            [['EPS', '5GS']]            | [RADIONODE_NODE]                          | [RADIONODE_MIXEDMODE_OSS_MODEL]                          | ['Lrat']                  || CCTR_SYS_DEF_SUBSCRIPTION      || 32
            [['EPS']]                   | [FIVE_G_NODE]                             | [FIVE_G_OSS_MODEL_18_Q4]                                 | ['RC', null]              || CCTR_NRAN_SYS_DEF_SUBSCRIPTION || 1
            [['5GS']]                   | [FIVE_G_NODE]                             | [FIVE_G_OSS_MODEL_18_Q4]                                 | ['RC', null]              || CCTR_NRAN_SYS_DEF_SUBSCRIPTION || 1
            [['EPS']]                   | [FIVE_G_NODE]                             | [FIVE_G_OSS_MODEL_19_Q2]                                 | ['CUCP', 'CUUP', 'DU']    || CCTR_NRAN_SYS_DEF_SUBSCRIPTION || 5
            [['5GS']]                   | [FIVE_G_NODE]                             | [FIVE_G_OSS_MODEL_19_Q2]                                 | ['CUCP', 'CUUP', 'DU']    || CCTR_NRAN_SYS_DEF_SUBSCRIPTION || 5
            [['EPS'], ['5GS']]          | [MSRBSV1_NODE, RADIONODE_NODE]            | [MSRBSV1_OSS_MODEL, RADIONODE_5GS_OSS_MODEL]             | ['CUCP', 'CUUP', 'DU']    || CCTR_NRAN_SYS_DEF_SUBSCRIPTION || 4
            [['EPS'], ['5GS']]          | [RADIONODE_NODE, RADIONODE_NODE]          | [RADIONODE_OSS_MODEL, RADIONODE_5GS_OSS_MODEL]           | ['CUCP', 'CUUP', 'DU']    || CCTR_NRAN_SYS_DEF_SUBSCRIPTION || 4
            [['EPS', '5GS']]            | [RADIONODE_NODE]                          | [RADIONODE_5GS_OSS_MODEL]                                | ['CUCP', 'CUUP', 'DU']    || CCTR_NRAN_SYS_DEF_SUBSCRIPTION || 4
    }

    @Unroll
    def '#subscriptionsExpectedInDPS is created on audit when a node with technologyDomain #technologyDomain and neType #neType is present in the dps'() {
        given: 'one node present in the dps with PmFunction enabled'
            def subscriptionType = SubscriptionType.CONTINUOUSCELLTRACE as SubscriptionType[]
            nodeUtil.builder(NODE_NAME_1)
                .technologyDomain(technologyDomain)
                .neType(neType)
                .pmEnabled(true)
                .build()
        when: 'subscription audit scheduler runs'
            systemDefinedSubscriptionAuditScheduler.onTimeout()
        then: "the #subscriptionsExpectedInDPS is created in dps"
            if (subscriptionsExpectedInDPS) {
                assert subscriptionDao.count(subscriptionType, null) == subscriptionsExpectedInDPS.size()
                subscriptionsExpectedInDPS.each { it -> assert subscriptionDao.existsBySubscriptionName(it) }
            } else {
                assert subscriptionDao.count(subscriptionType, null) == 0
            }
        and: 'subscription contains one node'
            if (subscriptionsExpectedInDPS) {
                subscriptionsExpectedInDPS.each { it ->
                    def subscription = subscriptionDao.findOneByExactName(it, true)
                    assert (subscription as ContinuousCellTraceSubscription).getNumberOfNodes() == 1
                }
            }
        where:
            technologyDomain      | neType         || subscriptionsExpectedInDPS
            ['UMTS', 'EPS']       | MSRBSV1_NODE   || [CCTR_SYS_DEF_SUBSCRIPTION]
            ['EPS']               | MSRBSV1_NODE   || [CCTR_SYS_DEF_SUBSCRIPTION]
            ['UMTS']              | MSRBSV1_NODE   || []
            ['']                  | MSRBSV1_NODE   || []
            ['EPS']               | ERBS_NODE      || [CCTR_SYS_DEF_SUBSCRIPTION]
            ['']                  | ERBS_NODE      || [CCTR_SYS_DEF_SUBSCRIPTION]
            ['5GS']               | RADIONODE_NODE || [CCTR_NRAN_SYS_DEF_SUBSCRIPTION]
            ['EPS', '5GS']        | RADIONODE_NODE || [CCTR_SYS_DEF_SUBSCRIPTION, CCTR_NRAN_SYS_DEF_SUBSCRIPTION]
            [null, '5GS']         | RADIONODE_NODE || [CCTR_NRAN_SYS_DEF_SUBSCRIPTION]
            ["5GS", "5GS", "5GS"] | RADIONODE_NODE || [CCTR_NRAN_SYS_DEF_SUBSCRIPTION]
            ['UMTS', 'EPS']       | RADIONODE_NODE || [CCTR_SYS_DEF_SUBSCRIPTION]
            ['EPS', 'EPS']        | RADIONODE_NODE || [CCTR_SYS_DEF_SUBSCRIPTION]
            ['EPS']               | RADIONODE_NODE || [CCTR_SYS_DEF_SUBSCRIPTION]
            ['UMTS']              | RADIONODE_NODE || []
            ['']                  | RADIONODE_NODE || []
            ['EPS']               | FIVE_G_NODE    || [CCTR_NRAN_SYS_DEF_SUBSCRIPTION]
            ['EPS', '5GS']        | FIVE_G_NODE    || [CCTR_NRAN_SYS_DEF_SUBSCRIPTION]
            ['']                  | FIVE_G_NODE    || [CCTR_NRAN_SYS_DEF_SUBSCRIPTION]
            [null]                | FIVE_G_NODE    || [CCTR_NRAN_SYS_DEF_SUBSCRIPTION]
    }

    def 'On Timeout single Statistical Subscription should be created with both EPG and VEPG nodes'() {
        given:
            dps.node().name('1').ossModelIdentity(EPG_OSS_MODEL).neType(EPG_NODE).build()
            dps.node().name('2').ossModelIdentity(VEPG_OSS_MODEL).neType(VEPG_NODE).build()
        when:
            systemDefinedSubscriptionAuditScheduler.onTimeout()
            def subscription = subscriptionDao.findOneByExactName(EPG_VEPG_STAT_SYS_DEF_SUBSCRIPTION, true) as StatisticalSubscription
        then:
            subscription.nodes.collect({ it.name }).sort() == ['1', '2']
            subscription.numberOfNodes == 2
    }

    def 'On Timeout Two Statistical subscriptions with 15 minute and 24 hour ROP created for #nodeType'() {
        given:
            dps.node().name('1').ossModelIdentity(ossModel).neType(nodeType).build()
        when:
            systemDefinedSubscriptionAuditScheduler.onTimeout()
            def subscription24Hr = (StatisticalSubscription) subscriptionDao.findOneByExactName(sub24HrName, true)
            def subscription15Min = (StatisticalSubscription) subscriptionDao.findOneByExactName(sub15MinName, true)
        then:
            subscription24Hr.rop == RopPeriod.ONE_DAY
            subscription15Min.rop == RopPeriod.FIFTEEN_MIN
            subscription24Hr.numberOfNodes == 1
            subscription15Min.numberOfNodes == 1

        where:
            nodeType            | ossModel                 | sub24HrName                                   | sub15MinName
            FRONTHAUL_6080_NODE | FRONTHAUL_6080_OSS_MODEL | FRONTHAUL_6080_24HR_STAT_SYS_DEF_SUBSCRIPTION | FRONTHAUL_6080_15MIN_STAT_SYS_DEF_SUBSCRIPTION
    }

    def 'On Timeout RadioNode System Defined Statistical Subscription should be updated with new node'() {
        given:
            dps.node().name('originalNode').ossModelIdentity(RADIONODE_OSS_MODEL).neType(RADIONODE_NODE).build()
            systemDefinedSubscriptionAuditScheduler.onTimeout()
            def createdSubscription = subscriptionDao.findOneByExactName(RADIONODE_STAT_SYS_DEF_SUBSCRIPTION, true)
        and: 'new node is added to ENM'
            dps.node().name('newNode').ossModelIdentity('17A-R2YX').neType(RADIONODE_NODE).build()
        when:
            systemDefinedSubscriptionAuditScheduler.onTimeout()
            def updatedSubscription = subscriptionDao.findOneById(createdSubscription.getId(), true) as StatisticalSubscription
        then: 'Subscription Object should be updated with the new node'
            updatedSubscription.nodes.size() == 2
            updatedSubscription.numberOfNodes == 2
    }

    def 'On Timeout RadioNode System Defined Statistical Subscription should be updated if a node is removed. Counters will also change'() {
        given:
            dps.node().name('originalNode').ossModelIdentity(RADIONODE_OSS_MODEL).neType(RADIONODE_NODE).build()
            def nodeMO1 = dps.node().name('newNode').ossModelIdentity('17A-R2YX').neType(RADIONODE_NODE).build()
            systemDefinedSubscriptionAuditScheduler.onTimeout()
            def createdSubscription = subscriptionDao.findOneByExactName(RADIONODE_STAT_SYS_DEF_SUBSCRIPTION, true) as StatisticalSubscription
            createdSubscription.administrationState = AdministrationState.ACTIVE
            subscriptionDao.saveOrUpdate(createdSubscription)
            configurableDps.build().liveBucket.deletePo(nodeMO1)
        when:
            systemDefinedSubscriptionAuditScheduler.onTimeout()
            def updatedSubscription = subscriptionDao.findOneById(createdSubscription.getId(), true) as StatisticalSubscription
        then: 'Subscription Object should be updated'
            updatedSubscription.nodes.size() == 1
            updatedSubscription.numberOfNodes == 1
            createdSubscription.counters.size() > 0
            updatedSubscription.counters.size() <= createdSubscription.counters.size()
    }

    def 'On Timeout System defined Subscription should be created when node with capability systemDefinedSubscription is added and pmEnable is true'() {
        given: 'Add EPG or vEPG nodes in DPS with pmEnable true'
            nodeUtil.createNetworkElementsInDPS(5, EPG_NODE, 'EPG-01', EPG_OSS_MODEL, PM_ENABLED)
            pmicModelDeploymentValidator.isCounterValidationSupportedForGivenTargetType(EPG_NODE) >> true
        when: 'ConfigurationSyncScheduler Timeout Occurs'
            systemDefinedSubscriptionAuditScheduler.onTimeout()
            def subscription = (StatisticalSubscription) subscriptionDao.findOneByExactName(EPG_VEPG_STAT_SYS_DEF_SUBSCRIPTION, false)
            subscription.administrationState = AdministrationState.ACTIVE
            subscriptionDao.saveOrUpdate(subscription)
        then: 'Subscription attributes are mapped from capability'
            subscription.name == EPG_VEPG_STAT_SYS_DEF_SUBSCRIPTION
            subscription.type == SubscriptionType.STATISTICAL
            subscription.description == 'All counters for all EPG and vEPG nodes'
            subscription.userType == UserType.SYSTEM_DEF
            subscription.rop == RopPeriod.FIFTEEN_MIN
            subscription.owner == 'PMIC'
            subscription.cbs == false
    }

    def 'On Timeout System defined Subscription should be updated when a new node with capability systemDefinedSubscription is added to ENM'() {
        given: 'SystemDefinedSubscription already exists with 5 nodes'
            Collection<ManagedObject> epgVepgNodes = nodeUtil.createNetworkElementsInDPS(5, EPG_NODE, 'EPG-01', EPG_OSS_MODEL,
                PM_ENABLED)
            pmicModelDeploymentValidator.isCounterValidationSupportedForGivenTargetType(EPG_NODE) >> true
            systemDefinedSubscriptionAuditScheduler.onTimeout()
        when: 'Add new EPG nodes in DPS with pmEnable true'
            epgVepgNodes.addAll(nodeUtil.createNetworkElementsInDPS(2, EPG_NODE, 'EPG-06', EPG_OSS_MODEL, PM_ENABLED))
        and: 'SystemDefinedSubscriptionAudit Timeout occurred'
            systemDefinedSubscriptionAuditScheduler.onTimeout()
            subscriptionMO = liveBucket().findMoByFdn('StatisticalSubscription=(v)EPG Statistical Subscription')
        then: 'Subscription should have the updated node list'
            subscriptionMO.getAttribute('name') == EPG_VEPG_STAT_SYS_DEF_SUBSCRIPTION
            subscriptionMO.getAttribute('type') == SubscriptionType.STATISTICAL.name()
            subscriptionMO.getAttribute('description') == 'All counters for all EPG and vEPG nodes'
            subscriptionMO.getAttribute('userType') == UserType.SYSTEM_DEF.name()
            subscriptionMO.getAttribute('rop') == RopPeriod.FIFTEEN_MIN.name()
            subscriptionMO.getAttribute('owner') == 'PMIC'
            subscriptionMO.getAttribute('cbs') == false
            subscriptionMO.getAttribute('pnpEnabled') == false
            subscriptionMO.getAttribute('filterOnManagedElement') == false
            subscriptionMO.getAttribute('filterOnManagedFunction') == false
            (subscriptionMO.getAssociations('nodes').collect {
                ((ManagedObject) it).fdn
            } as Set).containsAll(epgVepgNodes.collect {
                it.fdn
            } as Set)
    }

    def 'On Timeout Inactive System defined Subscription should be deleted when no nodes are available to ENM'() {
        given: 'SystemDefinedSubscription already exists with 5 nodes'
            Collection<ManagedObject> epgVepgNodes = nodeUtil.createNetworkElementsInDPS(5, EPG_NODE, 'EPG-01', EPG_OSS_MODEL,
                PM_ENABLED)
            StatisticalSubscriptionBuilder builder = new StatisticalSubscriptionBuilder(dpsUtils)
            subscriptionMO = builder.name(EPG_VEPG_STAT_SYS_DEF_SUBSCRIPTION)
                .administrativeState(AdministrationState.INACTIVE).taskStatus(TaskStatus.OK).build()
        when: 'All nodes are removed from ENM'
            nodeUtil.deleteManagedObjects(epgVepgNodes)
        and: 'SystemDefinedSubscriptionAudit Timeout occurred'
            systemDefinedSubscriptionAuditScheduler.onTimeout()
        then: 'Subscription should not be available in ENM'
            configurableDps.build().liveBucket.findMoByFdn('StatisticalSubscription=(v)EPG Statistical Subscription') == null
    }

    def 'Active System defined Subscription should be deactivated when no nodes are available with PM Function on in ENM on timeout'() {
        given: 'SystemDefinedSubscription already exists in ENM'
            Collection<ManagedObject> epgVepgNodes = nodeUtil.createNetworkElementsInDPS(5, EPG_NODE, 'EPG-01', EPG_OSS_MODEL, false)
            List<String> nodeFdns = epgVepgNodes.collect { it -> it.fdn }.sort()
            StatisticalSubscriptionBuilder builder = new StatisticalSubscriptionBuilder(dpsUtils)
            subscriptionMO = builder.name(EPG_VEPG_STAT_SYS_DEF_SUBSCRIPTION)
                .administrativeState(AdministrationState.ACTIVE).taskStatus(TaskStatus.OK).nodes(epgVepgNodes).build()
        when: 'SystemDefinedSubscriptionAudit timeout occurred'
            systemDefinedSubscriptionAuditScheduler.onTimeout()
            def subscription = (StatisticalSubscription) subscriptionDao.findOneByExactName(EPG_VEPG_STAT_SYS_DEF_SUBSCRIPTION, false)
        then: 'Subscription exists and should be in UPDATING status and deactivation event should be sent'
            subscription.administrationState == AdministrationState.UPDATING
            1 * deactivationEvent.execute({ it -> it.collect { node -> node.fdn }.sort() == nodeFdns } as List<Node>, _ as StatisticalSubscription)
    }

    def 'Inactive System defined Subscription should be deleted from ENM when no nodes are attached to the subscription on timeout'() {
        given: 'SystemDefinedSubscription already exists with 0 nodes'
            nodeUtil.createNetworkElementsInDPS(5, EPG_NODE, 'EPG-01', EPG_OSS_MODEL, false)
            StatisticalSubscriptionBuilder builder = new StatisticalSubscriptionBuilder(dpsUtils)
            subscriptionMO = builder.name(EPG_VEPG_STAT_SYS_DEF_SUBSCRIPTION)
                .administrativeState(AdministrationState.INACTIVE).taskStatus(TaskStatus.OK).build()
        when: 'SystemDefinedSubscriptionAudit timeout occurred'
            systemDefinedSubscriptionAuditScheduler.onTimeout()
        then: 'Subscription should not be available in ENM'
            subscriptionDao.findOneByExactName(EPG_VEPG_STAT_SYS_DEF_SUBSCRIPTION, false) == null
    }

    def 'Inactive System defined Subscription should be deleted from ENM when nodes are attached to the subscription that have pmFunction turned off on timeout'() {
        given: 'SystemDefinedSubscription already exists with 5 nodes with pmFunction off'
            Collection<ManagedObject> epgVepgNodes = nodeUtil.createNetworkElementsInDPS(5, EPG_NODE, 'EPG-01', EPG_OSS_MODEL, false)
            StatisticalSubscriptionBuilder builder = new StatisticalSubscriptionBuilder(dpsUtils)
            subscriptionMO = builder.name(EPG_VEPG_STAT_SYS_DEF_SUBSCRIPTION)
                .administrativeState(AdministrationState.INACTIVE).taskStatus(TaskStatus.OK).nodes(epgVepgNodes).build()
        when: 'SystemDefinedSubscriptionAudit timeout occurred'
            systemDefinedSubscriptionAuditScheduler.onTimeout()
        then: 'Subscription should not be available in ENM'
            subscriptionDao.findOneByExactName(EPG_VEPG_STAT_SYS_DEF_SUBSCRIPTION, false) == null
    }

    def 'User should be able to update the Output mode to #outputMode and UE Fraction to #ueFraction for an INACTIVE ContinuousCelltrace subscription'() {
        given: 'Radio Nodes  in DPS'
            dps.node().name('2').ossModelIdentity(RADIONODE_OSS_MODEL).neType(RADIONODE_NODE).build()
        when: 'ContinuousCelltrace subscription is created after the audit'
            systemDefinedSubscriptionAuditScheduler.onTimeout()
            ContinuousCellTraceSubscription subscription = subscriptionDao.findOneByExactName(CCTR_SYS_DEF_SUBSCRIPTION, false) as ContinuousCellTraceSubscription

        and: 'Update the output mode of ContinuousCelltrace subscription to Streaming'
            subscription.setUeFraction(ueFraction)
            setOutputModeAndUpdateSubscription(subscription, outputMode)
            subscription = subscriptionDao.findOneByExactName(PMIC_CONTINUOUSCELLTRACE_SUBSCRIPTION_NAME, false) as ContinuousCellTraceSubscription

        then: 'ContinuousCelltrace subscription should be updated with new Output mode'
            subscription.outputMode == outputMode
            subscription.streamInfoList == streamInfoList
        where:
            outputMode                        | ueFraction
            OutputModeType.STREAMING          | 123
            OutputModeType.FILE_AND_STREAMING | 777
    }

    def setOutputModeAndUpdateSubscription(ContinuousCellTraceSubscription cctrSubscription, OutputModeType outputMode) {
        cctrSubscription.setOutputMode(outputMode)
        cctrSubscription.setStreamInfoList(streamInfoList)
        subscriptionWriteOperationService.saveOrUpdate(cctrSubscription)
    }

    def getStreamInfoList() {
        return [new StreamInfo('192.168.11.22', 8080)]
    }

    def 'Should have countersEventsValidationApplicable = #countersEventsValidationApplicable for #subscriptionName'() {
        when: 'SystemDefinedCapabilityReader is called to check if metadata validation is required'
            final boolean isCountersEventsValidationApplicable = capabilityReader.isEventCounterVerificationNeeded(subscriptionName)
        then:
            countersEventsValidationApplicable == isCountersEventsValidationApplicable
        where:
            countersEventsValidationApplicable | subscriptionName
            false                      | '(v)EPG Statistical Subscription'
            true                       | 'RNC Primary System Defined Statistical Subscription'
            true                       | 'RNC Secondary System Defined Statistical Subscription'
            true                       | 'ERBS System Defined Statistical Subscription'
            true                       | 'RBS System Defined Statistical Subscription'
            true                       | '5GRadioNode System Defined Statistical Subscription'
            true                       | 'ContinuousCellTraceSubscription'
    }

    @Unroll
    def 'Performance Test #On Timeout subscription #subscriptionName for #nodeType should be created when pmEnable is true, only pmEnabled=true nodes added'() {
        given: 'Add #nodeType nodes in DPS with pmEnable true'
            (1..20000).each { it ->
                dps.node().name(NODE_NAME_1 + it).ossModelIdentity(ossModel).neType(nodeType).build();
            }
            dps.node().name('1').ossModelIdentity(ossModel).neType(nodeType).build()
            dps.node().name('2').ossModelIdentity(ossModel).neType(nodeType).build()
            dps.node().name('3').ossModelIdentity(ossModel).neType(nodeType).pmEnabled(false).build()
        when: 'ConfigurationSyncScheduler Timeout Occurs'
            def start_time = System.currentTimeMillis()
            systemDefinedSubscriptionAuditScheduler.onTimeout()
            def end_time = System.currentTimeMillis()
        then: 'Test execution should not be more than 5sec'
            (end_time - start_time) / 1000 < 5
        and: 'Subscription Object should exist in DPS and is of type StatisticalSubscription'
            def subscription = subscriptionDao.findOneByExactName(subscriptionName, true) as ResourceSubscription
            subscription.numberOfNodes == 20002
        where:
            nodeType       | ossModel            | subscriptionName
            ERBS_NODE      | ERBS_OSS_MODEL      | ERBS_STAT_SYS_DEF_SUBSCRIPTION
            RADIONODE_NODE | RADIONODE_OSS_MODEL | RADIONODE_STAT_SYS_DEF_SUBSCRIPTION
    }
}
