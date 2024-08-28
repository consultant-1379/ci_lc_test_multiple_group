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

package com.ericsson.oss.services.pm.bdd.collection.initiation.task.factories.validator

import spock.lang.Unroll

import org.slf4j.Logger

import com.ericsson.cds.cdi.support.rule.MockedImplementation
import com.ericsson.cds.cdi.support.rule.ObjectUnderTest
import com.ericsson.oss.pmic.cdi.test.util.SkeletonSpec
import com.ericsson.oss.pmic.dto.pmjob.enums.PmJobStatus
import com.ericsson.oss.pmic.dto.scanner.enums.ProcessType
import com.ericsson.oss.pmic.dto.subscription.enums.AdministrationState
import com.ericsson.oss.pmic.dto.subscription.enums.SubscriptionType
import com.ericsson.oss.pmic.dto.subscription.enums.TaskStatus
import com.ericsson.oss.services.pm.initiation.ctum.CtumSubscriptionServiceLocal
import com.ericsson.oss.services.pm.initiation.task.factories.validator.CtumTaskStatusValidator
import com.ericsson.oss.services.pm.initiation.task.factories.validator.UeTraceTaskStatusValidator
import com.ericsson.oss.services.pm.modelservice.PmCapabilityModelService

class CtumAndUeTraceTaskValidatorSpec extends SkeletonSpec {

    @ObjectUnderTest
    CtumTaskStatusValidator ctumTaskStatusValidator

    @ObjectUnderTest
    UeTraceTaskStatusValidator ueTaskStatusValidator

    @MockedImplementation
    CtumSubscriptionServiceLocal ctumAuditor

    @MockedImplementation
    private PmCapabilityModelService capabilityAccess

    @MockedImplementation
    Logger logger

    def processTypes = [
            ("CtumSubscription") : ProcessType.CTUM,
            ("UETraceSubscription") : ProcessType.UETRACE
    ]

    def subscriptionMo

    def 'Should call CTUM audit when validateTaskStatusAndAdminState is called for CTUM validator'() {
        given: 'a CTUM subscription'
            createSubscription(SubscriptionType.CTUM)
            def sub = subscriptionDao.findAll()[0]
        when: 'validateTaskStatusAndAdminState is called with a subscription and set of node fdns'
            ctumTaskStatusValidator.validateTaskStatusAndAdminState(sub, ['nodeFdnA', 'nodeFdnB'] as Set)
        then: 'the ctum auditor is called'
            1 * ctumAuditor.ctumAudit()
        when: 'validateTaskStatusAndAdminState is called with a subscription and a single node fdn'
            ctumTaskStatusValidator.validateTaskStatusAndAdminState(subscriptionDao.findAll()[0], 'nodeFdn')
        then: 'the ctum auditor is called'
            1 * ctumAuditor.ctumAudit()
        when: 'validateTaskStatusAndAdminState is called with a subscription'
            ctumTaskStatusValidator.validateTaskStatusAndAdminState(subscriptionDao.findAll()[0])
        then: 'the ctum auditor is called'
            1 * ctumAuditor.ctumAudit()
    }

    def 'Should do no validation for UE Trace for task status and admin state'() {
        given: 'a UeTrace subscription'
            createSubscription(SubscriptionType.UETRACE)
            def sub = subscriptionDao.findAll()[0]
        when: 'validateTaskStatusAndAdminState is called with a subscription and set of node fdns'
            ueTaskStatusValidator.validateTaskStatusAndAdminState(sub, ['nodeFdnA', 'nodeFdnB'] as Set)
        then: 'the ctum auditor is called'
            1 * logger.debug("No validation action for UeTrace subscription {} with id {}", sub.name, sub.id)
        when: 'validateTaskStatusAndAdminState is called with a subscription and a single node fdn'
            ueTaskStatusValidator.validateTaskStatusAndAdminState(subscriptionDao.findAll()[0], 'nodeFdn')
        then: 'the ctum auditor is called'
            1 * logger.debug("No validation action for UeTrace subscription {} with id {}", sub.name, sub.id)
        when: 'validateTaskStatusAndAdminState is called with a subscription'
            ueTaskStatusValidator.validateTaskStatusAndAdminState(subscriptionDao.findAll()[0])
        then: 'the ctum auditor is called'
            1 * logger.debug("No validation action for UeTrace subscription {} with id {}", sub.name, sub.id)
    }

    @Unroll
    def 'Should return correct value for isTaskStatusError for validator'() {
        given: 'a CTUM subscription, #numberOfNodes nodes, #activePmJobs active jobs and #inactivePmJobs inactive jobs'
            def validators = [
                    (SubscriptionType.CTUM) : ctumTaskStatusValidator,
                    (SubscriptionType.UETRACE) : ueTaskStatusValidator
            ]
            capabilityAccess.getSupportedNodeTypesForPmFeatureCapability(_, _) >> neTypesToReturn
            def subscriptionMo = createSubscription(type)
            def nodes = createNodes(numberOfNodes)
            createPmJobs(nodes, subscriptionMo, activePmJobs, inactivePmJobs)
        expect: 'task status validation returns #isTaskStatusErrorValue'
            validators[type].isTaskStatusError(subscriptionDao.findAll()[0]) == isTaskStatusErrorValue
        where:
            neTypesToReturn | numberOfNodes | activePmJobs  | inactivePmJobs    | type                      || isTaskStatusErrorValue
            ['SGSN-MME']    | 1             | 1             | 0                 | SubscriptionType.CTUM     || false
            []              | 1             | 1             | 0                 | SubscriptionType.CTUM     || true
            ['SGSN-MME']    | 2             | 1             | 1                 | SubscriptionType.CTUM     || true
            ['SGSN-MME']    | 0             | 0             | 1                 | SubscriptionType.CTUM     || false
            ['SGSN-MME']    | 1             | 1             | 0                 | SubscriptionType.UETRACE  || false
            []              | 1             | 1             | 0                 | SubscriptionType.UETRACE  || true
            ['SGSN-MME']    | 2             | 1             | 1                 | SubscriptionType.UETRACE  || true
            ['SGSN-MME']    | 0             | 0             | 1                 | SubscriptionType.UETRACE  || false
    }

    def createSubscription(type) {
        dps.subscription()
           .type(type)
           .administrationState(AdministrationState.ACTIVE)
           .taskStatus(TaskStatus.OK)
           .build()
    }

    def createNodes(numberOfNodes) {
        def nodes = []
        if (numberOfNodes == 0) {
            return nodes
        }
        (1..numberOfNodes).each {
            def node = dps.node()
                          .fdn("NetworkElement=node_${it}")
                          .neType('SGSN-MME')
                          .build()
            nodes.add(node)
        }
        return nodes
    }

    def createPmJobs(nodes, subscriptionMo, activePmJobs, inactivePmJobs) {
        nodes.each {
            def jobStatus
            if (activePmJobs-- > 0) {
                jobStatus = PmJobStatus.ACTIVE
            } else if (inactivePmJobs-- > 0) {
                jobStatus = PmJobStatus.INACTIVE
            } else {
                return
            }
            dps.pmJob()
               .nodeName(it.name)
               .processType(processTypes[subscriptionMo.type])
               .subscriptionId(subscriptionMo.poId)
               .status(jobStatus)
               .build()
        }
    }

}
