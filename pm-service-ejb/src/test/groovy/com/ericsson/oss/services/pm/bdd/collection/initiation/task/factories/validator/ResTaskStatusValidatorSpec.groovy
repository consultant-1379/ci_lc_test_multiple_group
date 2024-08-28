/*------------------------------------------------------------------------------
 *******************************************************************************
 * COPYRIGHT Ericsson 2018
 *
 * The copyright to the computer program(s) herein is the property of
 * Ericsson Inc. The programs may be used and/or copied only with written
 * permission from Ericsson Inc. or in accordance with the terms and
 * conditions stipulated in the agreement/contract under which the
 * program(s) have been supplied.
 *******************************************************************************
 *----------------------------------------------------------------------------*/

package com.ericsson.oss.services.pm.bdd.collection.initiation.task.factories.validator

import static  com.ericsson.oss.services.pm.initiation.utils.PmFunctionUtil.PmFunctionPropertyValue.PM_FUNCTION_LEGACY

import javax.inject.Inject

import spock.lang.Unroll

import com.ericsson.cds.cdi.support.rule.MockedImplementation
import com.ericsson.cds.cdi.support.rule.ObjectUnderTest
import com.ericsson.oss.services.pm.generic.ScannerService
import com.ericsson.oss.services.pm.initiation.notification.events.Deactivate
import com.ericsson.oss.services.pm.initiation.notification.events.InitiationEvent
import com.ericsson.oss.pmic.dto.scanner.enums.ProcessType
import com.ericsson.oss.pmic.dto.scanner.enums.ScannerStatus
import com.ericsson.oss.pmic.dto.subscription.enums.AdministrationState
import com.ericsson.oss.pmic.dto.subscription.enums.SubscriptionType
import com.ericsson.oss.pmic.dto.subscription.enums.TaskStatus
import com.ericsson.oss.pmic.dto.node.Node
import com.ericsson.oss.pmic.dto.subscription.Subscription
import com.ericsson.oss.services.pm.initiation.task.factories.validator.ResTaskStatusValidator

class ResTaskStatusValidatorSpec extends ResValidatorUtil {

    private static final RES_SUBSCRIPTION_NAME = "Test_RES"

    @ObjectUnderTest
    ResTaskStatusValidator objectUnderTest

    @MockedImplementation
    @Deactivate
    InitiationEvent deactivationEvent

    @Inject
    ScannerService scannerService

    def rncNodeAttributes = [
            name             : "RNC01",
            networkElementId : "RNC01",
            fdn              : "NetworkElement=RNC01",
            platformType     : "CPP",
            neType           : "RNC",
            nodeModelIdentity: "16B-V.7.1659",
            ossModelIdentity : "16B-V.7.1659",
            ossPrefix        : "MeContext=RNC01",
            pmFunction       : true]

    def rbsNodeAttributes = [
            platformType     : "CPP",
            neType           : "RBS",
            nodeModelIdentity: "16A-U.4.210",
            ossModelIdentity : "16A-U.4.210",
            ossPrefix        : "MeContext=RBS",
            controllingRnc   : "",
            pmFunction       : true]

    def attachedNode1Mo
    def attachedNode2Mo
    def nodeMo
    def node1Mo
    def resSubscriptionMo
    def resSubscriptionId

    def setup() {
        attachedNode1Mo = dps.node().name("1").neType("RBS").attributes(rbsNodeAttributes).build()
        attachedNode2Mo = dps.node().name("2").neType("RBS").attributes(rbsNodeAttributes).build()
        nodeMo = dps.node().name("3").neType("RNC").attributes(rncNodeAttributes).build()
        node1Mo = dps.node().name("4").neType("RNC").attributes(rncNodeAttributes).build()
        resSubscriptionMo = dps.subscription().type(SubscriptionType.RES).name(RES_SUBSCRIPTION_NAME).nodes(nodeMo).attachedNodes(attachedNode1Mo, attachedNode2Mo).administrationState(AdministrationState.ACTIVATING).build()
        resSubscriptionId = resSubscriptionMo.getPoId()
    }

    def "getTaskStatus will return OK status if active scanner count is equal with node count"() {
        given: "three active scanners exist in dps for 3 nodes attached to one RES subscription"
        dps.scanner().nodeName(nodeMo).name(SCANNER_NAME).processType(ProcessType.STATS).status(ScannerStatus.ACTIVE).subscriptionId(resSubscriptionId).build()
        dps.scanner().nodeName(attachedNode1Mo).name(SCANNER_NAME).processType(ProcessType.STATS).status(ScannerStatus.ACTIVE).subscriptionId(resSubscriptionId).build()
        dps.scanner().nodeName(attachedNode2Mo).name(SCANNER_NAME).processType(ProcessType.STATS).status(ScannerStatus.ACTIVE).subscriptionId(resSubscriptionId).build()
        when: "getTaskStatus is called"
        final TaskStatus taskStatus = objectUnderTest.getTaskStatus(subscriptionDao.findOneById(resSubscriptionId))
        then: "task status will be OK"
        taskStatus == TaskStatus.OK
    }

    def "getTaskStatus will return OK status if active scanner count is equal with node count (case subscription find with associations)"() {
        given: "three active scanners exist in dps for 3 nodes attached to one RES subscription"
        dps.scanner().nodeName(nodeMo).name(SCANNER_NAME).processType(ProcessType.STATS).status(ScannerStatus.ACTIVE).subscriptionId(resSubscriptionId).build()
        dps.scanner().nodeName(attachedNode1Mo).name(SCANNER_NAME).processType(ProcessType.STATS).status(ScannerStatus.ACTIVE).subscriptionId(resSubscriptionId).build()
        dps.scanner().nodeName(attachedNode2Mo).name(SCANNER_NAME).processType(ProcessType.STATS).status(ScannerStatus.ACTIVE).subscriptionId(resSubscriptionId).build()
        when: "getTaskStatus is called"
        final TaskStatus taskStatus = objectUnderTest.getTaskStatus(subscriptionDao.findOneById(resSubscriptionId, true))
        then: "task status will be OK"
        taskStatus == TaskStatus.OK
    }

    @Unroll
    def "getTaskStatus will return ERROR if 3 nodes with #status, #status1 and #status2 scanners exist for subscription"() {
        given: "three scanners with different status exists in dps for 3 nodes (RNC and RBS) attached to one RES subscription"
        dps.scanner().nodeName(nodeMo).name(SCANNER_NAME).processType(ProcessType.STATS).status(status).subscriptionId(resSubscriptionId).build()
        dps.scanner().nodeName(attachedNode1Mo).name(SCANNER_NAME).processType(ProcessType.STATS).status(status1).subscriptionId(resSubscriptionId).build()
        dps.scanner().nodeName(attachedNode2Mo).name(SCANNER_NAME).processType(ProcessType.STATS).status(status2).subscriptionId(resSubscriptionId).build()
        when: "getTaskStatus is called"
        final TaskStatus taskStatus = objectUnderTest.getTaskStatus(subscriptionDao.findOneById(resSubscriptionId))
        then: "task status will be ERROR"
        taskStatus == TaskStatus.ERROR
        where:
        status               | status1                | status2
        ScannerStatus.ERROR  | ScannerStatus.ACTIVE   | ScannerStatus.ACTIVE
        ScannerStatus.ACTIVE | ScannerStatus.INACTIVE | ScannerStatus.ACTIVE
        ScannerStatus.ACTIVE | ScannerStatus.ACTIVE   | ScannerStatus.UNKNOWN
        ScannerStatus.ACTIVE | ScannerStatus.ERROR    | ScannerStatus.UNKNOWN
    }

    def "getTaskStatus will return ERROR status when there are less active scanners than nodes"() {
        given: "two scanners with ACTIVE status exists in dps"
        dps.scanner().nodeName(attachedNode1Mo).name(SCANNER_NAME).processType(ProcessType.STATS).status(ScannerStatus.ACTIVE).subscriptionId(resSubscriptionId).build()
        dps.scanner().nodeName(attachedNode2Mo).name(SCANNER_NAME).processType(ProcessType.STATS).status(ScannerStatus.ACTIVE).subscriptionId(resSubscriptionId).build()
        when: "getTaskStatus is called"
        final TaskStatus taskStatus = objectUnderTest.getTaskStatus(subscriptionDao.findOneById(resSubscriptionId))
        then: "task status will be ERROR"
        taskStatus == TaskStatus.ERROR
    }

    def "getTaskStatus will return ERROR status when there are less nodes than active scanners"() {
        given: "two scanners with ACTIVE status exists in dps"
        dps.scanner().nodeName(nodeMo).name(SCANNER_NAME).processType(ProcessType.STATS).status(ScannerStatus.ACTIVE).subscriptionId(resSubscriptionId).build()
        dps.scanner().nodeName(attachedNode1Mo).name(SCANNER_NAME).processType(ProcessType.STATS).status(ScannerStatus.ACTIVE).subscriptionId(resSubscriptionId).build()
        dps.scanner().nodeName(attachedNode2Mo).name(SCANNER_NAME).processType(ProcessType.STATS).status(ScannerStatus.ACTIVE).subscriptionId(resSubscriptionId).build()
        dps.scanner().nodeName(node1Mo).name(SCANNER_NAME).processType(ProcessType.STATS).status(ScannerStatus.ACTIVE).subscriptionId(resSubscriptionId).build()
        when: "getTaskStatus is called"
        final TaskStatus taskStatus = objectUnderTest.getTaskStatus(subscriptionDao.findOneById(resSubscriptionId))
        then: "task status will be ERROR"
        taskStatus == TaskStatus.ERROR
    }

    @Unroll
    def 'Should delete attached nodes from dps and send deactivation request when using PM_FUNCTION_LEGACY'() {
        given: 'A RES subscription with #nodes nodes and #attachedNodes attached nodes'
            pmFunctionConfig.pmFunctionConfig >> PM_FUNCTION_LEGACY
            def subscriptionMo = createSubscription()
            def primaryNodes = createNodes(1, 1, subscriptionMo, explicitPmFunction, 'nodes')
            def attachedNodes = createNodes(2, attachedNodeCount + 1, subscriptionMo, attachedPmFunction, 'attachedNodes', primaryNodes[0])
            def nodeIdsToDeactivate = explicitPmFunction ? [] : attachedNodes.collect{it.poId}
            def attachedNodeScanners = createScanners(attachedNodes, subscriptionMo.poId)

        when: 'validation is executed for the subscription'
            objectUnderTest.validateTaskStatusAndAdminState(subscriptionDao.findOneById(subscriptionMo.poId, true))

        then: '#attachedNodes fdns are send to deactivate and scanners to be deleted'
            scannerService.findAllById(attachedNodeScanners.collect{it.poId}).size() == expectedAttachedScanners
            1 * deactivationEvent.execute({ it -> it.collect { node -> node.id }.sort() == nodeIdsToDeactivate } as List<Node>, {it -> it.id == subscriptionMo.poId} as Subscription)

        where:
            explicitPmFunction    | attachedNodeCount | attachedPmFunction || expectedAttachedScanners
            false                 | 1                 | false              || 0
            false                 | 2                 | false              || 0
            false                 | 1                 | true               || 1
            true                  | 2                 | false              || 0
    }

    def 'Subscription Task Status is unchanged in administration state is INACTIVE'() {
        given: 'an inactive RES subscription'
            def subscriptionMo = createSubscription(AdministrationState.INACTIVE, status)
            def sub = subscriptionDao.findOneById(subscriptionMo.poId, true)

        when: 'validation is executed for the subscription'
            objectUnderTest.validateTaskStatusAndAdminState(sub)

        then: 'subscriptions task status is unchanged'
            sub.taskStatus == status

        where:
            status << [TaskStatus.OK, TaskStatus.ERROR]
    }

    def 'Should not throw exception if trying to update subscription admin state but no nodes associated to be removed'() {
        given: 'an active RES subscription with no nodes'
            def subscriptionMo = createSubscription()

        when: 'validation is executed'
            objectUnderTest.validateTaskStatusAndAdminState(subscriptionDao.findOneById(subscriptionMo.poId, true), [] as Set)

        then: 'no exception is thrown when subscription has no nodes'
            noExceptionThrown()

        and: 'subscription is updated to INACTIVE'
            subscriptionDao.findOneById(subscriptionMo.poId).administrationState == AdministrationState.INACTIVE
    }

}
