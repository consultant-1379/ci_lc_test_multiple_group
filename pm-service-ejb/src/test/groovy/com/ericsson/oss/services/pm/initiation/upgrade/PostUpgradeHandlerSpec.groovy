package com.ericsson.oss.services.pm.initiation.upgrade

import static com.ericsson.oss.pmic.cdi.test.util.Constants.*

import spock.lang.Shared
import spock.lang.Unroll

import com.ericsson.cds.cdi.support.rule.ObjectUnderTest
import com.ericsson.oss.pmic.cdi.test.util.SkeletonSpec
import com.ericsson.oss.pmic.dto.subscription.AccessType
import com.ericsson.oss.pmic.dto.subscription.cdts.CounterInfo
import com.ericsson.oss.pmic.dto.subscription.cdts.EventInfo
import com.ericsson.oss.pmic.dto.subscription.enums.AdministrationState
import com.ericsson.oss.pmic.dto.subscription.enums.CellTraceCategory
import com.ericsson.oss.pmic.dto.subscription.enums.SubscriptionType
import com.ericsson.oss.pmic.dto.subscription.enums.TaskStatus
import com.ericsson.oss.services.pm.upgrade.PostUpgradeHandler

/**
 * Post upgrade spoc test.
 */
class PostUpgradeHandlerSpec extends SkeletonSpec {
    @ObjectUnderTest
    private PostUpgradeHandler upgradeHandler

    @Shared
    def counter = new CounterInfo('counter1', 'groupName1')

    @Shared
    def event = new EventInfo('event1', 'groupName1')

    @Unroll
    def 'When updateSubscriptionAttributeForUpgrade is called all EBM subscriptions with ebsCounters should be updated'() {
        given: 'an EBM subscription with ebsCounters exists in DPS'
            def ebmSubscriptionMo = createEbmSubscription([counter])
            ebmSubscriptionMo.setAttribute('ebsEnabled', ebsEnabled)


        when: 'updateEbsEnabled is called'
            upgradeHandler.updateAttributeForUpgrade()

        then: 'the Subscription values in dps are updated'
            ebmSubscriptionMo.getAttribute('ebsEnabled') == expectedState

        where:
            ebsEnabled | expectedState
            true       | true
            false      | true
            null       | true
    }

    @Unroll
    def 'When updateSubscriptionAttributeForUpgrade is called all EBM subscriptions without ebsCounters, ebsEnabled should not be updated'() {
        given: 'an EBM subscription with no ebsCounters exists in DPS'
            def ebmSubscriptionMO = createEbmSubscription()
            ebmSubscriptionMO.setAttribute('ebsEnabled', ebsEnabled)

        when: 'updateEbsEnabled is called'
            upgradeHandler.updateAttributeForUpgrade()

        then: 'the Subscription values should remain the same'
            ebmSubscriptionMO.getAttribute('ebsEnabled') == ebsEnabled

        where:
            ebsEnabled << [true, false, null]
    }

    def 'When updateSubscriptionAttributeForUpgrade is called, all subscriptions with numberOfNodes attribute (no ebs), should not be updated'() {
        given: 'a CellTrace subscription with 2 nodes exists in DPS'
            def nodeMo = dps.node().name(NODE_NAME_1).build()
            def nodeMo2 = dps.node().name(NODE_NAME_2).build()
            def subscriptionMo = createCellTraceSubscription(SubscriptionType.CELLTRACE, null, [nodeMo, nodeMo2])

        when: 'upgradeHandler is called'
            upgradeHandler.updateAttributeForUpgrade()

        then: 'the Subscription values should remain the same'
            subscriptionMo.getAttribute('numberOfNodes') == 2
    }

    def 'When updateSubscriptionAttributeForUpgrade is called, all ebm subscriptions without numberOfNodes attribute and old model, should be updated'() {
        given: 'an EBM subscription with 2 nodes exists in DPS'
            def nodeMo = dps.node().name(SGSN_NODE_NAME_1).build()
            def nodeMo2 = dps.node().name(SGSN_NODE_NAME_2).build()
            def ebmSubscriptionMo = createEbmSubscription([counter], [nodeMo, nodeMo2])
            ebmSubscriptionMo.setAttribute('version', '1.2.0')
            ebmSubscriptionMo.setAttribute('numberOfNodes', 0)

        when: 'upgradeHandler is called'
            upgradeHandler.updateAttributeForUpgrade()

        then: 'the Subscription values should remain the same'
            ebmSubscriptionMo.getAttribute('numberOfNodes') == 2
            ebmSubscriptionMo.getAttribute('ebsEnabled') == true
    }

    def 'When updateSubscriptionAttributeForUpgrade is called, all ebm subscriptions with numberOfNodes attribute and old model, should be updated'() {
        given: 'an EBM subscription with 2 nodes exists in DPS'
            def nodeMo = dps.node().name(SGSN_NODE_NAME_1).build()
            def nodeMo2 = dps.node().name(SGSN_NODE_NAME_2).build()
            def ebmSubscriptionMo = createEbmSubscription([counter], [nodeMo, nodeMo2])
            ebmSubscriptionMo.setAttribute('version', '1.2.0')

        when: 'upgradeHandler is called'
            upgradeHandler.updateAttributeForUpgrade()

        then: 'the Subscription values should remain the same'
            ebmSubscriptionMo.getAttribute('numberOfNodes') == 2
            ebmSubscriptionMo.getAttribute('ebsEnabled') == true
    }

    def 'When updateSubscriptionAttributeForUpgrade is called, all ContinuousCellTraceSubscriptions model with CellTraceCategory attribute null, should be updated'() {
        given: 'ContinuousCellTraceSubscriptions in dps is been upgraded '
            def node = dps.node().name(NODE_NAME_1).neType('ERBS').build()

            def cellTraceSubscription = createCellTraceSubscription(SubscriptionType.CONTINUOUSCELLTRACE, null, [node], [event])

        when: 'updateAttributeForUpgrade method is called'
            upgradeHandler.updateAttributeForUpgrade()

        then: 'ContinuousCellTraceSubscriptions CellTraceCategory should not be null and set as "CELLTRACE"'
            cellTraceSubscription.getAttribute('cellTraceCategory') as CellTraceCategory == CellTraceCategory.CELLTRACE
    }

    @Unroll
    def 'When updateSubscriptionAttributeForUpgrade is called, all CellTraceSubscriptions model with CellTraceCategory attribute null, should be updated'() {
        given: 'CellTraceSubscription in dps is been upgraded '
            def node = dps.node().name(NODE_NAME_1).neType('ERBS').build()

            def cellTraceSubscription = createCellTraceSubscription(SubscriptionType.CELLTRACE, actualCellTraceCategory, [node], events, ebsCounters, ebsEvents)

        when: 'PostUpgradeHandler updateAttributeForUpgrade method is called'
            upgradeHandler.updateAttributeForUpgrade()

        then: 'CellTraceSubscription CellTraceCategory should not be null'
            cellTraceSubscription.getAttribute('cellTraceCategory') as CellTraceCategory == exepectedCellTraceCategory

        where: 'values of EbsCounter, EbsEvent and Events are as follows'
            ebsCounters | ebsEvents | events  | actualCellTraceCategory                     || exepectedCellTraceCategory
            [counter]   | [event]   | [event] | null                                        || CellTraceCategory.CELLTRACE_AND_EBSL_STREAM
            [counter]   | [event]   | []      | null                                        || CellTraceCategory.EBSL_STREAM
            [counter]   | [event]   | null    | null                                        || CellTraceCategory.EBSL_STREAM
            [counter]   | []        | [event] | null                                        || CellTraceCategory.CELLTRACE_AND_EBSL_FILE
            [counter]   | []        | []      | null                                        || CellTraceCategory.CELLTRACE_AND_EBSL_FILE
            [counter]   | null      | null    | null                                        || CellTraceCategory.CELLTRACE_AND_EBSL_FILE
            []          | []        | [event] | null                                        || CellTraceCategory.CELLTRACE
            null        | null      | [event] | null                                        || CellTraceCategory.CELLTRACE
            []          | []        | []      | null                                        || CellTraceCategory.CELLTRACE
            null        | null      | null    | null                                        || CellTraceCategory.CELLTRACE
            []          | []        | []      | CellTraceCategory.CELLTRACE_AND_EBSL_FILE   || CellTraceCategory.CELLTRACE_AND_EBSL_FILE
            null        | null      | null    | CellTraceCategory.CELLTRACE_AND_EBSL_STREAM || CellTraceCategory.CELLTRACE_AND_EBSL_STREAM
    }

    def 'Update accessType on startup if empty'() {
        given: 'a Statistical subscription with ebsCounters exists in DPS'
            def ebmSubscriptionMO = ebmSubscriptionBuilder.name('EBMSubscription').build()
            def ctumSubscriptionMO = ctumSubscriptionBuilder.name('CTUMSubscription').build()
            def ueTraceSubscriptionMO = ueTraceSubscriptionBuilder.name('UeTraceSubscription').build()
            def cellTraceSubscriptionMO = cellTraceSubscriptionBuilder.name('CellTraceSubscription').build()
            def statisticalSubscriptionMO = statisticalSubscriptionBuilder.name('UeTraceSubscription').build()
            assert ebmSubscriptionMO.getAttribute('accessType') as AccessType == null
            assert ctumSubscriptionMO.getAttribute('accessType') as AccessType == null
            assert ueTraceSubscriptionMO.getAttribute('accessType') as AccessType == null
            assert cellTraceSubscriptionMO.getAttribute('accessType') as AccessType == null
            assert statisticalSubscriptionMO.getAttribute('accessType') as AccessType == null

        when: 'updateAccessType is called'
            upgradeHandler.updateAttributeForUpgrade()

        then: 'the Subscription values in dps are updated'
            ebmSubscriptionMO.getAttribute('accessType') as AccessType == AccessType.FULL
            ctumSubscriptionMO.getAttribute('accessType') as AccessType == AccessType.FULL
            ueTraceSubscriptionMO.getAttribute('accessType') as AccessType == AccessType.FULL
            cellTraceSubscriptionMO.getAttribute('accessType') as AccessType == AccessType.FULL
            statisticalSubscriptionMO.getAttribute('accessType') as AccessType == AccessType.FULL
    }

    def createCellTraceSubscription(type = SubscriptionType.CELLTRACE, cellTraceCategory = CellTraceCategory.CELLTRACE, nodes = [], events = [], ebsCounters = [], ebsEvents = [], name = 'Subscription1') {
        subscriptionBuilder(type, name).cellTraceCategory(cellTraceCategory)
                                       .ebsCounters(ebsCounters)
                                       .ebsEvents(ebsEvents)
                                       .events(events)
                                       .nodes(nodes)
                                       .build()
    }

    def createEbmSubscription(ebsCountes = [], nodes = [], events = [], name = 'subscription') {
        def sub = subscriptionBuilder(SubscriptionType.EBM, name).nodes(nodes)
                                                                 .events(events)
                                                                 .ebsCounters(ebsCountes)
                                                                 .build()
        sub.setAttribute('ebsEnabled', null)
        return sub
    }

    def subscriptionBuilder(type, name) {
        dps.subscription()
           .type(type)
           .name(name)
           .taskStatus(TaskStatus.OK)
           .administrationState(AdministrationState.ACTIVE)
    }
}
