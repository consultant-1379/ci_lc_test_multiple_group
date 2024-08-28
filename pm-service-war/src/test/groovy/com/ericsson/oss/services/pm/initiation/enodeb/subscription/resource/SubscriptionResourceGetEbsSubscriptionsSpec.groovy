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
package com.ericsson.oss.services.pm.initiation.enodeb.subscription.resource

import com.ericsson.oss.services.pm.modelservice.PmCapabilityModelService

import static com.ericsson.oss.services.pm.common.constants.PmCapabilityModelConstants.CELLTRACE_LRAN_SUBSCRIPTION_ATTRIBUTES
import static com.ericsson.oss.services.pm.common.constants.PmCapabilityModelConstants.CELLTRACE_NRAN_SUBSCRIPTION_ATTRIBUTES
import static com.ericsson.oss.services.pm.common.constants.PmCapabilityModelConstants.SUPPORTED_MODEL_DEFINERS_FOR_COUNTERS

import static com.ericsson.oss.pmic.dto.subscription.enums.AdministrationState.ACTIVE
import static com.ericsson.oss.pmic.dto.subscription.enums.AdministrationState.INACTIVE
import static com.ericsson.oss.pmic.dto.subscription.enums.CellTraceCategory.ASR
import static com.ericsson.oss.pmic.dto.subscription.enums.CellTraceCategory.CELLTRACE_AND_EBSL_FILE
import static com.ericsson.oss.pmic.dto.subscription.enums.CellTraceCategory.CELLTRACE_AND_EBSL_STREAM
import static com.ericsson.oss.pmic.dto.subscription.enums.CellTraceCategory.CELLTRACE_NRAN_AND_EBSN_FILE
import static com.ericsson.oss.pmic.dto.subscription.enums.CellTraceCategory.CELLTRACE_NRAN_AND_EBSN_STREAM
import static com.ericsson.oss.pmic.dto.subscription.enums.CellTraceCategory.NRAN_EBSN_STREAM
import static com.ericsson.oss.pmic.dto.subscription.enums.CellTraceCategory.EBSL_STREAM
import static com.ericsson.oss.pmic.dto.subscription.enums.CellTraceCategory.ESN
import static com.ericsson.oss.pmic.dto.subscription.enums.SubscriptionType.CELLTRACE

import javax.ws.rs.core.Response
import spock.lang.Unroll

import com.ericsson.cds.cdi.support.rule.MockedImplementation
import com.ericsson.cds.cdi.support.rule.ObjectUnderTest
import com.ericsson.oss.itpf.datalayer.dps.persistence.ManagedObject
import com.ericsson.oss.pmic.cdi.test.util.SkeletonSpec
import com.ericsson.oss.pmic.dto.subscription.CellTraceSubscription
import com.ericsson.oss.pmic.dto.subscription.enums.CellTraceCategory
import com.ericsson.oss.pmic.dto.subscription.enums.OperationalState
import com.ericsson.oss.pmic.dto.subscription.enums.TaskStatus
import com.ericsson.oss.services.pm.modelservice.PmGlobalCapabilities
import com.ericsson.services.pm.initiation.restful.api.CounterTableRow
import com.ericsson.services.pm.initiation.restful.api.PmMetaDataLookupLocal

class SubscriptionResourceGetEbsSubscriptionsSpec extends SkeletonSpec {

    @MockedImplementation
    PmMetaDataLookupLocal counterLookup

    @MockedImplementation
    private PmCapabilityModelService pmCapabilityModelService

    @ObjectUnderTest
    SubscriptionResource subscriptionResource

    def setup() {
        def nranCapabilities = new PmGlobalCapabilities()
        nranCapabilities.updateAttributes([(SUPPORTED_MODEL_DEFINERS_FOR_COUNTERS): ['/NE-defined-CUCP-EBS/', '/NE-defined-CUUP-EBS/', '/NE-defined-DU-EBS/']])
        def lranCapabilities = new PmGlobalCapabilities()
        lranCapabilities.updateAttributes([(SUPPORTED_MODEL_DEFINERS_FOR_COUNTERS): ['/NE-defined-EBS/']])
        pmCapabilityModelService.getGlobalCapabilitiesByFunction(CELLTRACE_NRAN_SUBSCRIPTION_ATTRIBUTES, SUPPORTED_MODEL_DEFINERS_FOR_COUNTERS) >> nranCapabilities
        pmCapabilityModelService.getGlobalCapabilitiesByFunction(CELLTRACE_LRAN_SUBSCRIPTION_ATTRIBUTES, SUPPORTED_MODEL_DEFINERS_FOR_COUNTERS) >> lranCapabilities
    }

    def 'When getEbsSubscriptionFilteredList is called with an invalid subscription type, the response returned is 403'() {
        given: 'an active cell trace subscription in DPS'
            createCellTraceSubscription('celltrace1', CELLTRACE_AND_EBSL_FILE, ACTIVE)

        when: 'the getEbsSubscriptionsFilteredList is called'
            def response = subscriptionResource.getEbsSubscriptionsFilteredList('EVENTS', '')

        then: 'the response code returned is 403.'
            response.status == Response.Status.FORBIDDEN.statusCode
    }

    def 'When getEbsSubscriptionFilteredList is called with a subscription type equals null, the response returned is 403'() {
        given: 'an active cell trace subscription in DPS'
            createCellTraceSubscription('celltrace1', CELLTRACE_AND_EBSL_FILE, ACTIVE)

        when: 'the getEbsSubscriptionsFilteredList is called'
            def response = subscriptionResource.getEbsSubscriptionsFilteredList(null, '')

        then: 'the response code returned is 403.'
            response.status == Response.Status.FORBIDDEN.statusCode
    }

    def 'When getEbsSubscriptionFilteredList is called and no subscriptions match the query paramters suuplied the status returned is 204'() {
        given: '2 Cell trace subscriptions exist in DPS'
            createCellTraceSubscription('celltrace2', CELLTRACE_AND_EBSL_FILE, ACTIVE)
            createCellTraceSubscription('celltrace3', CELLTRACE_AND_EBSL_FILE, INACTIVE)

        when: 'the getEbsSubscriptionsFilteredList is called'
            def response = subscriptionResource.getEbsSubscriptionsFilteredList('EBM', 'ACTIVE')

        then: 'the response code returned is 204.'
            response.status == Response.Status.NO_CONTENT.statusCode
    }

    def 'When getEbsSubscriptionFilteredList is called with a valid type, and empty administrative status the response returned is 200'() {
        given: '2 Cell trace subscriptions exist in DPS'
            createCellTraceSubscription('celltrace4', EBSL_STREAM, ACTIVE)
            createCellTraceSubscription('celltrace5', EBSL_STREAM, INACTIVE)

        when: 'the getEbsSubscriptionsFilteredList is called'
            def response = subscriptionResource.getEbsSubscriptionsFilteredList('CELLTRACE', '')

        then: 'the subscription is retrieved.'
            response.status == Response.Status.OK.statusCode
    }

    def 'When getEbsSubscriptionFilteredList is called on a active CellTrace subscription, the response returned is 200'() {
        given: 'an active cell trace subscription in DPS'
            createCellTraceSubscription('celltrace2', EBSL_STREAM, ACTIVE)

        when: 'the getEbsSubscriptionsFilteredList is called'
            def response = subscriptionResource.getEbsSubscriptionsFilteredList('CELLTRACE', 'ACTIVE')

        then: 'the subscription is retrieved.'
            response.status == Response.Status.OK.statusCode
    }

    def 'When getEbsSubscriptionFilteredList is called on a active EBM subscription, the response returned is 200'() {
        given: 'an active ebm subscription in DPS'
            createEbmSubscription('ebm1', true, ACTIVE)

        when: 'the getEbsSubscriptionsFilteredList is called'
            def response = subscriptionResource.getEbsSubscriptionsFilteredList('EBM', 'ACTIVE')

        then: 'the subscription is retrieved.'
            response.status == Response.Status.OK.statusCode
    }

    def 'When getEbsSubscriptionFilteredList is called on a active CellTrace Lrat & Nran subscription, the response returned is 200'() {
        given: 'an active EBS-l & EBS-N cell trace subscription in DPS'
            createCellTraceSubscription('celltrace_lrat',EBSL_STREAM, ACTIVE)
            createCellTraceSubscription('celltrace_nran',CELLTRACE_NRAN_AND_EBSN_FILE, ACTIVE)
            counterLookup.getCountersForAllVersions(_,_) >> counters

        when: 'the getEbsSubscriptionsFilteredList is called'
            def response = subscriptionResource.getEbsSubscriptionsFilteredList('CELLTRACE','ACTIVE')

        then: 'the subscription is retrieved.'
            response.status == Response.Status.OK.statusCode
            ((CellTraceSubscription)response.entity[0]).name == 'celltrace_lrat'
            ((CellTraceSubscription)response.entity[0]).type == CELLTRACE
            ((CellTraceSubscription)response.entity[0]).cellTraceCategory == EBSL_STREAM
            ((CellTraceSubscription)response.entity[0]).ebsCounters[0].moClassType == 'EUtranCellFDD'
            ((CellTraceSubscription)response.entity[0]).ebsCounters[0].name == 'pmUeCtxtRelTimeX2HoSum'
            ((CellTraceSubscription)response.entity[0]).ebsCounters[1].moClassType == 'EUtranCellTDD'
            ((CellTraceSubscription)response.entity[0]).ebsCounters[1].name == 'pmAdvCellSupDetection'

            ((CellTraceSubscription)response.entity[1]).name == 'celltrace_nran'
            ((CellTraceSubscription)response.entity[1]).type == CELLTRACE
            ((CellTraceSubscription)response.entity[1]).cellTraceCategory == CELLTRACE_NRAN_AND_EBSN_FILE
            ((CellTraceSubscription)response.entity[1]).ebsCounters[0].moClassType == 'EUtranCellFDD'
            ((CellTraceSubscription)response.entity[1]).ebsCounters[0].name == 'pmUeCtxtRelTimeX2HoSum'
            ((CellTraceSubscription)response.entity[1]).ebsCounters[1].moClassType == 'EUtranCellTDD'
            ((CellTraceSubscription)response.entity[1]).ebsCounters[1].name == 'pmAdvCellSupDetection'
    }

    @Unroll
    def 'getSubscriptionFilteredList for celltrace type subscriptions will return only #adminStatusToFilter subscriptions of the correct category'() {
        given:
            def nodes = [nodeUtil.builder('node1').build(), nodeUtil.builder('node2').build()] as ManagedObject[]
            createCellTraceSubscription('Subscription1', category1, adminStatus1, nodes)
            createCellTraceSubscription('Subscription2', category2, adminStatus2, nodes[0])

        when:
            def result = subscriptionResource.getEbsSubscriptionsFilteredList('CELLTRACE', adminStatusToFilter)

        then:
            (result.entity as List<CellTraceSubscription>).every{it.type == CELLTRACE && it.cellTraceCategory == resCategory && it.numberOfNodes == nodesNum && it.name == resName}

        where:
            category1                               | category2                             | adminStatus1 | adminStatus2 | adminStatusToFilter | resCategory                     | nodesNum | resName
            CELLTRACE_AND_EBSL_STREAM.name()        | CELLTRACE_AND_EBSL_STREAM.name()      | ACTIVE       | INACTIVE     | 'ACTIVE'            | CELLTRACE_AND_EBSL_STREAM       | 2        | 'Subscription1'
            CELLTRACE_AND_EBSL_STREAM.name()        | CELLTRACE_AND_EBSL_STREAM.name()      | INACTIVE     | ACTIVE       | 'ACTIVE'            | CELLTRACE_AND_EBSL_STREAM       | 1        | 'Subscription2'
            CELLTRACE_AND_EBSL_STREAM.name()        | CELLTRACE_AND_EBSL_STREAM.name()      | ACTIVE       | INACTIVE     | 'INACTIVE'          | CELLTRACE_AND_EBSL_STREAM       | 1        | 'Subscription2'
            CELLTRACE_AND_EBSL_STREAM.name()        | CELLTRACE_AND_EBSL_STREAM.name()      | INACTIVE     | ACTIVE       | 'INACTIVE'          | CELLTRACE_AND_EBSL_STREAM       | 2        | 'Subscription1'
            CELLTRACE_AND_EBSL_STREAM.name()        | CellTraceCategory.CELLTRACE.name()    | ACTIVE       | ACTIVE       | 'ACTIVE'            | CELLTRACE_AND_EBSL_STREAM       | 2        | 'Subscription1'
            CELLTRACE_AND_EBSL_STREAM.name()        | CellTraceCategory.CELLTRACE.name()    | INACTIVE     | INACTIVE     | 'INACTIVE'          | CELLTRACE_AND_EBSL_STREAM       | 2        | 'Subscription1'
            CELLTRACE_AND_EBSL_FILE.name()          | ASR.name()                            | ACTIVE       | ACTIVE       | 'ACTIVE'            | CELLTRACE_AND_EBSL_FILE         | 2        | 'Subscription1'
            CELLTRACE_AND_EBSL_FILE.name()          | ASR.name()                            | INACTIVE     | INACTIVE     | 'INACTIVE'          | CELLTRACE_AND_EBSL_FILE         | 2        | 'Subscription1'
            EBSL_STREAM.name()                      | ESN.name()                            | ACTIVE       | ACTIVE       | 'ACTIVE'            | EBSL_STREAM                     | 2        | 'Subscription1'
            EBSL_STREAM.name()                      | ESN.name()                            | INACTIVE     | INACTIVE     | 'INACTIVE'          | EBSL_STREAM                     | 2        | 'Subscription1'
            CELLTRACE_NRAN_AND_EBSN_FILE.name()     | CELLTRACE_NRAN_AND_EBSN_FILE.name()   | ACTIVE       | INACTIVE     | 'ACTIVE'            | CELLTRACE_NRAN_AND_EBSN_FILE    | 2        | 'Subscription1'
            CELLTRACE_NRAN_AND_EBSN_FILE.name()     | CELLTRACE_NRAN_AND_EBSN_FILE.name()   | INACTIVE     | ACTIVE       | 'ACTIVE'            | CELLTRACE_NRAN_AND_EBSN_FILE    | 1        | 'Subscription2'
            CELLTRACE_NRAN_AND_EBSN_FILE.name()     | ASR.name()                            | ACTIVE       | ACTIVE       | 'ACTIVE'            | CELLTRACE_NRAN_AND_EBSN_FILE    | 2        | 'Subscription1'
            CELLTRACE_NRAN_AND_EBSN_STREAM.name()   | CELLTRACE_NRAN_AND_EBSN_STREAM.name() | ACTIVE       | INACTIVE     | 'ACTIVE'            | CELLTRACE_NRAN_AND_EBSN_STREAM  | 2        | 'Subscription1'
            CELLTRACE_NRAN_AND_EBSN_STREAM.name()   | CELLTRACE_NRAN_AND_EBSN_STREAM.name() | INACTIVE     | ACTIVE       | 'ACTIVE'            | CELLTRACE_NRAN_AND_EBSN_STREAM  | 1        | 'Subscription2'
            CELLTRACE_NRAN_AND_EBSN_STREAM.name()   | ASR.name()                            | ACTIVE       | ACTIVE       | 'ACTIVE'            | CELLTRACE_NRAN_AND_EBSN_STREAM  | 2        | 'Subscription1'
            NRAN_EBSN_STREAM.name()                 | NRAN_EBSN_STREAM.name()               | ACTIVE       | INACTIVE     | 'ACTIVE'            | NRAN_EBSN_STREAM                | 2        | 'Subscription1'
            NRAN_EBSN_STREAM.name()                 | NRAN_EBSN_STREAM.name()               | INACTIVE     | ACTIVE       | 'ACTIVE'            | NRAN_EBSN_STREAM                | 1        | 'Subscription2'
            NRAN_EBSN_STREAM.name()                 | ASR.name()                            | ACTIVE       | ACTIVE       | 'ACTIVE'            | NRAN_EBSN_STREAM                | 2        | 'Subscription1'
    }

    def createCellTraceSubscription(name, cellTraceCategory, state, nodes = [] as ManagedObject[]) {
        cellTraceSubscriptionBuilder.cellTraceCategory(cellTraceCategory)
                                    .name(name).administrativeState(state)
                                    .operationalState(OperationalState.RUNNING).taskStatus(TaskStatus.OK)
                                    .addEbsCounter('EUtranCellFDD', 'pmUeCtxtRelTimeX2HoSum')
                                    .addEbsCounter('EUtranCellTDD', 'pmAdvCellSupDetection')
                                    .nodes(nodes)
                                    .build()
    }

    def getCounters() {
        [new CounterTableRow('pmUeCtxtRelTimeX2HoSum', 'EUtranCellFDD'),
         new CounterTableRow('pmAdvCellSupDetection', 'EUtranCellTDD'),
         new CounterTableRow('pmAdvCellSupDetection', 'EUtranCellFDD'),
         new CounterTableRow('pmEenbPktLostDlCa', 'ExternalENodeBFunction'),
         new CounterTableRow('pmHoPrepSuccLb', 'ExternalENodeBFunction'),
         new CounterTableRow('pmEenbPktLostDlCa', 'EUtranCellTDD')]
    }

    def createEbmSubscription(name, ebsEnabled, state){
        ebmSubscriptionBuilder.name(name).administrativeState(state)
                              .operationalState(OperationalState.RUNNING).taskStatus(TaskStatus.OK)
                              .addEbsCounter('EUtranCellFDD', 'pmUeCtxtRelTimeX2HoSum')
                              .addEbsCounter('EUtranCellTDD', 'pmAdvCellSupDetection')
                              .setEbsEnabled(ebsEnabled)
                              .build()
    }

}
