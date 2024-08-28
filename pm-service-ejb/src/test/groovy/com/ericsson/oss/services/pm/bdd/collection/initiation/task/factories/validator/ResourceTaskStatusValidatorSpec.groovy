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
package com.ericsson.oss.services.pm.bdd.collection.initiation.task.factories.validator

import com.ericsson.oss.services.pm.services.generic.SubscriptionWriteOperationService

import static com.ericsson.oss.services.pm.initiation.utils.PmFunctionUtil.PmFunctionPropertyValue.PM_FUNCTION_LEGACY

import javax.inject.Inject

import spock.lang.Unroll

import com.ericsson.cds.cdi.support.rule.MockedImplementation
import com.ericsson.oss.services.pm.initiation.utils.PmFunctionConfig
import com.ericsson.cds.cdi.support.rule.ObjectUnderTest
import com.ericsson.oss.itpf.datalayer.dps.persistence.ManagedObject
import com.ericsson.oss.pmic.cdi.test.util.SkeletonSpec
import com.ericsson.oss.pmic.cdi.test.util.builder.TestDpsUtils
import com.ericsson.oss.pmic.cdi.test.util.builder.node.TestNetworkElementDpsUtils
import com.ericsson.oss.pmic.cdi.test.util.builder.scanner.TestScannerDpsUtils
import com.ericsson.oss.pmic.cdi.test.util.builder.subscription.StatisticalSubscriptionBuilder
import com.ericsson.oss.pmic.dao.SubscriptionDao
import com.ericsson.oss.pmic.dto.scanner.enums.ProcessType
import com.ericsson.oss.pmic.dto.scanner.enums.ScannerStatus
import com.ericsson.oss.pmic.dto.subscription.StatisticalSubscription
import com.ericsson.oss.pmic.dto.subscription.Subscription
import com.ericsson.oss.pmic.dto.subscription.enums.AdministrationState
import com.ericsson.oss.pmic.dto.subscription.enums.SubscriptionType
import com.ericsson.oss.pmic.dto.subscription.enums.TaskStatus
import com.ericsson.oss.pmic.dto.subscription.enums.UserType
import com.ericsson.oss.services.pm.common.constants.PmCapabilityModelConstants
import com.ericsson.oss.services.pm.initiation.task.factories.validator.ResourceTaskStatusValidator

class ResourceTaskStatusValidatorSpec extends SkeletonSpec {

    private static final NODE_NAME_1 = 'LTE01ERBS0001'
    private static final NODE_NAME_2 = 'LTE01ERBS0002'

    public TestDpsUtils testDpsUtils = new TestDpsUtils(configurableDps)

    public TestNetworkElementDpsUtils node = new TestNetworkElementDpsUtils(configurableDps)

    public TestScannerDpsUtils scanner = new TestScannerDpsUtils(configurableDps)

    StatisticalSubscriptionBuilder builder = new StatisticalSubscriptionBuilder(testDpsUtils)

    @ObjectUnderTest
    ResourceTaskStatusValidator objectUnderTest

    @MockedImplementation
    PmFunctionConfig pmFunctionConfig

    @Inject
    SubscriptionDao subscriptionDao

    @Inject
    SubscriptionWriteOperationService writeOperationService

    private List<ManagedObject> nodes
    private ManagedObject subscriptionMO

    def addStatsScannerToNodeInSubscriptionMo(ManagedObject subscriptionMo, String nodeName, String scannerName, ScannerStatus scannerStatus) {
        scanner.builder(scannerName, nodeName)
               .subscriptionId(subscriptionMo.poId)
               .status(scannerStatus)
               .processType(ProcessType.STATS)
               .build()
    }

    def buildSystemDefinedSubscriptionDtoFromSubscriptionMo(ManagedObject subscriptionMo) {
        final Subscription subscription = new StatisticalSubscription()
        subscription.setId(subscriptionMo.getPoId())
        subscription.setName(subscriptionMo.getName())
        subscription.setType(SubscriptionType.STATISTICAL)
        subscription.setUserType(UserType.SYSTEM_DEF)
        subscription.setTaskStatus(TaskStatus.valueOf(subscriptionMo.getAttribute('taskStatus')))
        subscription.setAdministrationState(AdministrationState.valueOf(subscriptionMo.getAttribute('administrationState')))
        return subscription
    }

    ManagedObject buildSystemDefinedSubscriptionMo(String subscriptionName, AdministrationState adminState, TaskStatus taskStatus) {
        def subscriptionMo = builder.name(subscriptionName).administrativeState(adminState).taskStatus(taskStatus).build()
        testDpsUtils.addAssociation(subscriptionMo, 'nodes', nodes[0], nodes[1])
        return subscriptionMo
    }

    def setup() {
        nodes = [node.builder(NODE_NAME_1).build(), node.builder(NODE_NAME_2).build()]
        subscriptionMO = buildSystemDefinedSubscriptionMo('Test', AdministrationState.ACTIVATING, TaskStatus.OK)
        addStatsScannerToNodeInSubscriptionMo(subscriptionMO, 'USERDEF.TEST.Cont.Y.Stats', NODE_NAME_1, ScannerStatus.ACTIVE)
    }

    def 'getTaskStatus will return OK status if active scanner count is equal with node count'() {
        given: 'two active scanners exist in dps for 2 nodes attached to one subscription'
            scanner.builder('USERDEF.TEST.Cont.Y.Stats', NODE_NAME_2)
                   .subscriptionId(subscriptionMO.poId)
                   .status(ScannerStatus.ACTIVE)
                   .processType(ProcessType.STATS)
                   .build()

        when: 'getTaskStatus is called'
            final TaskStatus taskStatus = objectUnderTest.getTaskStatus(subscriptionDao.findOneById(subscriptionMO.poId))

        then: 'task status will be OK'
            taskStatus == TaskStatus.OK
    }

    @Unroll
    def 'getTaskStatus will return ERROR if 2 nodes with ACTIVE and #status scanners exist for subscription'() {
        given: 'one active scanner and one #status scanner exists in dps for 2 nodes attached to one subscription'
            scanner.builder('USERDEF.TEST.Cont.Y.Stats', NODE_NAME_2)
                   .subscriptionId(subscriptionMO.poId)
                   .status(status)
                   .processType(ProcessType.STATS)
                   .build()

        when: 'getTaskStatus is called'
            final TaskStatus taskStatus = objectUnderTest.getTaskStatus(subscriptionDao.findOneById(subscriptionMO.getPoId()))

        then: 'task status will be ERROR'
            taskStatus == TaskStatus.ERROR

        where: 'The second scanner is not ACTIVE'
            status << ['INACTIVE', 'ERROR', 'UNKNOWN']
    }

    def 'getTaskStatus will return ERROR status when there are less active scanners than nodes'() {
        when: 'getTaskStatus is called'
            final TaskStatus taskStatus = objectUnderTest.getTaskStatus(subscriptionDao.findOneById(subscriptionMO.poId))

        then: 'task status will be ERROR'
            taskStatus == TaskStatus.ERROR
    }

    def 'getTaskStatus will return OK status if active scanner count is equal with node count based on Subscription object'() {
        given: 'two active scanners exist in dps for 2 nodes attached to one subscription'
            scanner.builder('USERDEF.TEST.Cont.Y.Stats', NODE_NAME_2)
                   .subscriptionId(subscriptionMO.poId)
                   .status(ScannerStatus.ACTIVE)
                   .processType(ProcessType.STATS)
                   .build()
            final Subscription subscription = buildSystemDefinedSubscriptionDtoFromSubscriptionMo(subscriptionMO)

        when: 'getTaskStatus is called'
            final TaskStatus taskStatus = objectUnderTest.getTaskStatus(subscription)

        then: 'task status will be OK'
            taskStatus == TaskStatus.OK
    }

    @Unroll
    def 'getTaskStatus will return ERROR if 2 nodes with ACTIVE and #status scanners exist for subscription based on Subscription object'() {
        given: 'one active scanner and one #status scanner exists in dps for 2 nodes attached to one subscription'
            scanner.builder('USERDEF.TEST.Cont.Y.Stats', NODE_NAME_2)
                   .subscriptionId(subscriptionMO.poId)
                   .status(status)
                   .processType(ProcessType.STATS)
                   .build()

        when: 'getTaskStatus is called'
            final TaskStatus taskStatus = objectUnderTest.getTaskStatus(subscriptionDao.findOneById(subscriptionMO.getPoId()))

        then: 'task status will be ERROR'
            taskStatus == TaskStatus.ERROR

        where: 'The second scanner is not ACTIVE'
            status << ['INACTIVE', 'ERROR', 'UNKNOWN']
    }

    def 'getTaskStatus will return ERROR status wen there are less active scanners than nodes based on Subscription object'() {
        when: 'getTaskStatus is called'
            final TaskStatus taskStatus = objectUnderTest.getTaskStatus(subscriptionDao.findOneById(subscriptionMO.poId))

        then: 'task status will be ERROR'
            taskStatus == TaskStatus.ERROR
    }

    def 'isTaskStatusError should return false if subscription is null'() {
        expect: 'the return value to be false if I call isTaskStatusError with null subscription object'
            !objectUnderTest.isTaskStatusError(null)
    }


    def 'Should return false for isTskStatusError if null subscription provided'() {
        expect: 'that is task status error returns false when called with null exception'
            !objectUnderTest.isTaskStatusError(1, null)
    }

    def 'Should not try to update the subscription if null subscription is provided'() {
        when: 'I call update subscription with a null subscription'
            objectUnderTest.updateSubscription(1, null as Subscription, [] as Set<String>, TaskStatus.ERROR)

        then: 'no subscription update is attempted'
            noExceptionThrown()
            0 * writeOperationService.update(_)
    }

    @Unroll
    def 'Querying the TaskStatus of a system defined subscription of node type #nodeTypeThatSupportsMultiplePredefScanners'() {
        given: 'A System defined subscription with predefined scanners'
            def subscriptionName = nodeTypeThatSupportsMultiplePredefScanners
            PmCapabilityModelConstants.SYSTEM_DEFINED_STATISTICAL_SUBSCRIPTION_NAME
            def systemDefinedSubscriptionMo = buildSystemDefinedSubscriptionMo(subscriptionName, AdministrationState.ACTIVE, TaskStatus.OK)
            addStatsScannerToNodeInSubscriptionMo(systemDefinedSubscriptionMo, NODE_NAME_1, 'PREDEF.PREDEF_Apc.STATS', scannerStatus)
            addStatsScannerToNodeInSubscriptionMo(systemDefinedSubscriptionMo, NODE_NAME_2, 'PREDEF.PREDEF_Apc.STATS', scannerStatus)
            Subscription subscription = buildSystemDefinedSubscriptionDtoFromSubscriptionMo(systemDefinedSubscriptionMo)

        when: 'We check its TaskStatus'
            boolean isTaskStatusError = objectUnderTest.isTaskStatusError(subscription)

        then: 'TaskStatus matches the expected value'
            isTaskStatusError == expectedIsTaskStatusError

        where:
            nodeTypeThatSupportsMultiplePredefScanners | scannerStatus          || expectedIsTaskStatusError
            'ERBS'                                     | ScannerStatus.ACTIVE   || false
            'RadioNode'                                | ScannerStatus.ACTIVE   || false
            'VTFRadioNode'                             | ScannerStatus.ACTIVE   || false
            'ERBS'                                     | ScannerStatus.INACTIVE || true
            'RadioNode'                                | ScannerStatus.INACTIVE || true
            'VTFRadioNode'                             | ScannerStatus.INACTIVE || true
            'ERBS'                                     | ScannerStatus.UNKNOWN  || true
            'RadioNode'                                | ScannerStatus.UNKNOWN  || true
            'VTFRadioNode'                             | ScannerStatus.UNKNOWN  || true
            'ERBS'                                     | ScannerStatus.ERROR    || true
            'RadioNode'                                | ScannerStatus.ERROR    || true
            'VTFRadioNode'                             | ScannerStatus.ERROR    || true
    }

    @Unroll
    def 'Subscription validation and status update of a System Defined subscription that has predefined scanners'() {
        given: 'A system defined subscription'
            def subscriptionName = "RadioNode${PmCapabilityModelConstants.SYSTEM_DEFINED_STATISTICAL_SUBSCRIPTION_NAME}"
            def systemDefinedSubscriptionMo = buildSystemDefinedSubscriptionMo(subscriptionName, initialAdministrationState, initialTaskStatus)
            addStatsScannerToNodeInSubscriptionMo(systemDefinedSubscriptionMo, NODE_NAME_1, 'PREDEF.PREDEF_Apc.STATS', scannerStatus)
            addStatsScannerToNodeInSubscriptionMo(systemDefinedSubscriptionMo, NODE_NAME_2, 'PREDEF.PREDEF_Apc.STATS', scannerStatus)
            Subscription subscription = buildSystemDefinedSubscriptionDtoFromSubscriptionMo(systemDefinedSubscriptionMo)

        when: 'Subscription is validated'
            objectUnderTest.validateTaskStatusAndAdminState(subscription)

        then: 'Subscription state is updated if applicable'
            subscriptionDao.findAllByName(subscriptionName, false)[0].taskStatus == expectedTaskStatus

        where:
            initialAdministrationState     | initialTaskStatus | scannerStatus          || expectedTaskStatus
            AdministrationState.ACTIVE     | TaskStatus.ERROR  | ScannerStatus.ACTIVE   || TaskStatus.OK
            AdministrationState.ACTIVE     | TaskStatus.OK     | ScannerStatus.ACTIVE   || TaskStatus.OK
            AdministrationState.ACTIVE     | TaskStatus.OK     | ScannerStatus.INACTIVE || TaskStatus.ERROR
            AdministrationState.ACTIVE     | TaskStatus.OK     | ScannerStatus.UNKNOWN  || TaskStatus.ERROR
            AdministrationState.ACTIVE     | TaskStatus.OK     | ScannerStatus.ERROR    || TaskStatus.ERROR
            AdministrationState.ACTIVATING | TaskStatus.ERROR  | ScannerStatus.ACTIVE   || TaskStatus.ERROR
    }

    @Unroll
    def 'Subscription validation and status update of a System Defined subscription that has predefined scanners with PM_FUNCTION_LEGACY'() {
        given: 'A system defined subscription'
            def subscriptionName = "RadioNode${PmCapabilityModelConstants.SYSTEM_DEFINED_STATISTICAL_SUBSCRIPTION_NAME}"
            def systemDefinedSubscriptionMo = buildSystemDefinedSubscriptionMo(subscriptionName, initialAdministrationState, initialTaskStatus)
            addStatsScannerToNodeInSubscriptionMo(systemDefinedSubscriptionMo, NODE_NAME_1, 'PREDEF.PREDEF_Apc.STATS', scannerStatus)
            Subscription subscription = buildSystemDefinedSubscriptionDtoFromSubscriptionMo(systemDefinedSubscriptionMo)
            pmFunctionConfig.pmFunctionConfig >> PM_FUNCTION_LEGACY

        when: 'Subscription is validated'
            objectUnderTest.validateTaskStatusAndAdminState(subscription, nodes[0].fdn)

        then: 'Subscription state is updated if applicable'
            subscriptionDao.findAllByName(subscriptionName, false)[0].taskStatus == expectedTaskStatus

        where:
            initialAdministrationState     | initialTaskStatus | scannerStatus          || expectedTaskStatus
            AdministrationState.ACTIVE     | TaskStatus.ERROR  | ScannerStatus.ACTIVE   || TaskStatus.OK
            AdministrationState.ACTIVE     | TaskStatus.OK     | ScannerStatus.ACTIVE   || TaskStatus.OK
            AdministrationState.ACTIVE     | TaskStatus.OK     | ScannerStatus.INACTIVE || TaskStatus.ERROR
            AdministrationState.ACTIVE     | TaskStatus.OK     | ScannerStatus.UNKNOWN  || TaskStatus.ERROR
            AdministrationState.ACTIVE     | TaskStatus.OK     | ScannerStatus.ERROR    || TaskStatus.ERROR
            AdministrationState.ACTIVATING | TaskStatus.ERROR  | ScannerStatus.ACTIVE   || TaskStatus.ERROR
    }

    def 'Should update subscription status to inactive if all nodes have pmFunction OFF'() {
        given: 'a subscription with one node with pm function disabled'
            def subscriptionMo = dps.subscription()
                                    .type(SubscriptionType.STATISTICAL)
                                    .name('testSub')
                                    .administrationState(AdministrationState.ACTIVE)
                                    .userType(UserType.USER_DEF)
                                    .build()
            def node = dps.node()
                          .name('node_1')
                          .pmFunction(false)
                          .build()
            dpsUtils.addAssociation(subscriptionMo, 'nodes', node)

        when: 'Subscription is validated'
            objectUnderTest.validateTaskStatusAndAdminState(subscriptionDao.findOneById(subscriptionMo.poId), node.fdn)

        then: 'Subscription state is updated if applicable'
            subscriptionDao.findAllByName('testSub', false)[0].administrationState == AdministrationState.INACTIVE

    }
}
