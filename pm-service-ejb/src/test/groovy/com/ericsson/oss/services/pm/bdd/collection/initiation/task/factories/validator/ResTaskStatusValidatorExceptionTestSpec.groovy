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

import spock.lang.Unroll

import org.slf4j.Logger

import com.ericsson.cds.cdi.support.rule.MockedImplementation
import com.ericsson.cds.cdi.support.rule.ObjectUnderTest
import com.ericsson.oss.services.pm.exception.DataAccessException
import com.ericsson.oss.services.pm.generic.ScannerService
import com.ericsson.oss.services.pm.initiation.task.factories.validator.ResTaskStatusValidator
import com.ericsson.oss.services.pm.services.generic.SubscriptionReadOperationService

class ResTaskStatusValidatorExceptionTestSpec  extends ResValidatorUtil {

    @ObjectUnderTest
    ResTaskStatusValidator objectUnderTest

    @MockedImplementation
    SubscriptionReadOperationService readOperationService

    @MockedImplementation
    ScannerService scannerService

    @MockedImplementation
    Logger logger

    @Unroll
    def 'Should not throw exception if exception thrown by subscription read service'() {
        given: 'an active RES subscription'
            def subscriptionMo = createSubscription()
            def subscription = subscriptionDao.findOneById(subscriptionMo.poId, true)
            readOperationService.findOneById(subscription.id, true) >> { throw exception }

        when: 'validation is executed for the subscription'
            objectUnderTest.validateTaskStatusAndAdminState(subscription, [] as Set)

        then: 'no exception is thrown from the validator'
            noExceptionThrown()
            1 * logger.error("Unable to validate subscription {} with id {}. Exception message {}", subscription.name, subscription.id, exception.message)
            1 * logger.info("Unable to validate subscription {} with id {}.", subscription.name, subscription.id, exception)

        where:
            exception << [new DataAccessException(''), new IllegalArgumentException()]
    }

    def 'Should not throw an exception if exception thrown by scanner service when trying to fetch scanners'() {
        given: 'an active RES subscription with nodes and attached nodes'
            def subscriptionMo = createSubscription()
            def primaryNodes = createNodes(1, 1, subscriptionMo, false, 'nodes')
            createNodes(2, 2, subscriptionMo, false, 'attachedNodes', primaryNodes[0]).collect{it.fdn}
            def subscription = subscriptionDao.findOneById(subscriptionMo.poId,true)
            pmFunctionConfig.pmFunctionConfig >> PM_FUNCTION_LEGACY
            readOperationService.findOneById(subscriptionMo.poId, true) >> subscription
            scannerService.findAllByNodeFdnAndSubscriptionId(_, _) >> {throw new DataAccessException('')}

        when: 'validation is executed for the subscription'
            objectUnderTest.validateTaskStatusAndAdminState(subscription)

        then: 'no exception is thrown from the validator'
            noExceptionThrown()
            1 * logger.error("Scanner deletion for subscription {} with id {}. Exception message {}", subscription.name, subscription.id, '')
            1 * logger.info("Scanner deletion for subscription {} with id {}.", subscription.name, subscription.id, _ as DataAccessException)
    }

    def 'Should not throw an exception if exception thrown by scanner service when trying to delete scanners'() {
        given: 'an active RES subscription with nodes and attached nodes'
            def subscriptionMo = createSubscription()
            def primaryNodes = createNodes(1, 1, subscriptionMo, false, 'nodes')
            createNodes(2, 2, subscriptionMo, false, 'attachedNodes', primaryNodes[0])
            def subscription = subscriptionDao.findOneById(subscriptionMo.poId,true)
            pmFunctionConfig.pmFunctionConfig >> PM_FUNCTION_LEGACY
            readOperationService.findOneById(subscriptionMo.poId, true) >> subscription
            scannerService.delete(null) >> {throw new DataAccessException('')}

        when: 'validation is executed for the subscription'
            objectUnderTest.validateTaskStatusAndAdminState(subscription)

        then: 'no exception is thrown from the validator'
            noExceptionThrown()
            1 * logger.error("Scanner deletion for subscription {} with id {}. Exception message {}", subscription.name, subscription.id, '')
            1 * logger.info("Scanner deletion for subscription {} with id {}.", subscription.name, subscription.id, _ as DataAccessException)
    }

}