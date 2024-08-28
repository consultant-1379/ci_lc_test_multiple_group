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

package com.ericsson.oss.services.pm.initiation.schedulers

import com.ericsson.cds.cdi.support.rule.MockedImplementation
import com.ericsson.cds.cdi.support.rule.ObjectUnderTest
import com.ericsson.cds.cdi.support.spock.CdiSpecification
import com.ericsson.oss.services.pm.exception.DataAccessException
import com.ericsson.oss.pmic.dto.subscription.ResSubscription
import com.ericsson.oss.pmic.dto.subscription.cdts.CellInfo
import com.ericsson.oss.pmic.dto.subscription.cdts.ResInfo
import com.ericsson.oss.pmic.dto.subscription.enums.ResServiceCategory
import com.ericsson.oss.pmic.dto.subscription.enums.ResSpreadingFactor
import com.ericsson.oss.services.pm.initiation.model.metadata.res.PmResLookUp
import com.ericsson.oss.services.pm.initiation.task.factories.auditor.ResSubscriptionHelper
import org.slf4j.Logger

class ResSubscriptionHelperExceptionSpec extends CdiSpecification {

    @ObjectUnderTest
    ResSubscriptionHelper resSubscriptionHelper

    @MockedImplementation
    PmResLookUp pmResLookUp

    @MockedImplementation
    Logger logger

    def 'Should not throw exception if DataAccessException thrown by PmResLookup'() {
        given: 'a RES subscription'
            def subscription = new ResSubscription([] as List<CellInfo>, true, 1000, [:] as Map<ResServiceCategory, Integer>,
                                                   [:] as Map<String, ResInfo>, [] as List<ResSpreadingFactor>)
            subscription.setResSpreadingFactor([ResSpreadingFactor.fromInteger(100)])
            subscription.setName('some name')
            pmResLookUp.fetchAttachedNodes([] as List<CellInfo>, true, subscription.getNodesFdns(), true) >> {throw new DataAccessException('a message')}
        when: 'the helper is executed for additional criteria'
            resSubscriptionHelper.checkSubscriptionForExtraCriteria(subscription)

        then: 'no exception is thrown and correct messages are logged'
            1 * logger.warn('Audit for ResSubscription {} failed! Could not fetch attached Nodes from dps :: {}', subscription.name, 'a message')
            1 * logger.debug('fetchAttachedNodes failed during auditing of ResSubscription {} :: {}', subscription.name, _ as StackTraceElement[])

    }
}
