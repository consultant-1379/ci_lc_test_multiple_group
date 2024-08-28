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

import spock.lang.Unroll

import org.slf4j.Logger

import com.ericsson.cds.cdi.support.rule.MockedImplementation
import com.ericsson.cds.cdi.support.rule.ObjectUnderTest
import com.ericsson.oss.pmic.cdi.test.util.SkeletonSpec
import com.ericsson.oss.pmic.dto.scanner.enums.ScannerStatus
import com.ericsson.oss.pmic.dto.subscription.enums.AdministrationState
import com.ericsson.oss.pmic.dto.subscription.enums.SubscriptionType
import com.ericsson.oss.pmic.dto.subscription.enums.UserType
import com.ericsson.oss.services.pm.exception.DataAccessException
import com.ericsson.oss.services.pm.generic.ScannerService
import com.ericsson.oss.services.pm.generic.NodeService
import com.ericsson.oss.services.pm.initiation.task.factories.validator.ResourceTaskStatusValidator

class ResourceTaskStatusValidatorExceptionTestSpec extends SkeletonSpec {

    @ObjectUnderTest
    ResourceTaskStatusValidator objectUnderTest

    @MockedImplementation
    NodeService nodeService

    @MockedImplementation
    ScannerService scannerService

    @MockedImplementation
    Logger logger

    @Unroll
    def 'Should not throw exception if exception thrown by node service service'() {
        given: 'an active RES subscription'
            def subscriptionMo = createSubscription()
            def subscription = subscriptionDao.findOneById(subscriptionMo.poId, true)
            nodeService.countBySubscriptionId(subscription.id) >> { throw exception }

        when: 'validation is executed for the subscription'
            objectUnderTest.validateTaskStatusAndAdminState(subscription, [] as Set)

        then: 'no exception is thrown from the validator'
            noExceptionThrown()
            1 * logger.error('Unable to validate subscription {} with id {}. Exception message {}', subscription.name, subscription.id, exception.message)
            1 * logger.info('Unable to validate subscription {} with id {}.', subscription.name, subscription.id, exception)

        where:
            exception << [new DataAccessException(''), new IllegalArgumentException()]
    }

    @Unroll
    def 'Should not throw exception if exception thrown by node service service when retrieving PmFunction value'() {
        given: 'an active RES subscription'
            def subscriptionMo = createSubscription()
            def subscription = subscriptionDao.findOneById(subscriptionMo.poId, true)
            nodeService.isPmFunctionEnabled(_) >> { throw new IllegalArgumentException('message') }

        when: 'validation is executed for the subscription'
            objectUnderTest.validateTaskStatusAndAdminState(subscription, [''] as Set)

        then: 'no exception is thrown from the validator'
            noExceptionThrown()
            1 * logger.error('Unable to validate subscription {} with id {}. Exception message {}', subscription.name, subscription.id, 'message')
            1 * logger.info('Unable to validate subscription {} with id {}.', subscription.name, subscription.id, _ as IllegalArgumentException)
    }

    def 'Should not throw an exception if exception thrown by scanner service'() {
        given: 'an active RES subscription with 1 node pm function disableds'
            def subscriptionMo = createSubscription()
            def nodeMo = dps.node().name('node_1').pmFunction(false).build()
            dpsUtils.addAssociation(subscriptionMo, 'nodes', nodeMo)
            def subscription = subscriptionDao.findOneById(subscriptionMo.poId,true)
            scannerService.countBySubscriptionIdAndScannerStatus(Collections.singleton(subscription.id), ScannerStatus.ACTIVE) >> {throw new DataAccessException('a message')}

        when: 'validation is executed for the subscription'
            objectUnderTest.validateTaskStatusAndAdminState(subscription, [] as Set<String>)

        then: 'no exception is thrown from the validator'
            noExceptionThrown()
            1 * logger.error('Unable to get taskStatus for subscription {} with id {}. Exception message {}', subscription.name, subscription.id, 'a message')
            1 * logger.info('Unable to get taskStatus for subscription {} with id {}.', subscription.name, subscription.id, _ as DataAccessException)

    }

    def createSubscription() {
        dps.subscription()
           .type(SubscriptionType.STATISTICAL)
           .name('testSub')
           .administrationState(AdministrationState.ACTIVE)
           .userType(UserType.USER_DEF)
           .build()
    }
}