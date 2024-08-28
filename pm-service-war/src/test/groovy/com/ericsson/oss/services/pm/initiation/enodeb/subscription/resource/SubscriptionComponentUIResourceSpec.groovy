/*------------------------------------------------------------------------------
 *******************************************************************************
 * COPYRIGHT Ericsson 2017
 *
 * The copyright to the computer program(s) herein is the property of
 * Ericsson Inc. The programs may be used and/or copied only with written
 * permission from Ericsson Inc. or in accordance with the terms and
 * conditions stipulated in the agreement/contract under which the
 * program(s) have been supplied.
 *******************************************************************************
 *----------------------------------------------------------------------------*/

package com.ericsson.oss.services.pm.initiation.enodeb.subscription.resource

import com.ericsson.cds.cdi.support.rule.MockedImplementation
import com.ericsson.oss.pmic.api.modelservice.PmCapabilityReader

import static com.ericsson.oss.services.pm.initiation.constants.ApplicationMessages.UNABLE_TO_ACTIVATE_SUBSCRIPTION

import javax.inject.Inject
import javax.ws.rs.core.Response

import com.ericsson.cds.cdi.support.rule.ObjectUnderTest
import com.ericsson.cds.cdi.support.spock.CdiSpecification
import com.ericsson.oss.pmic.dto.node.Node
import com.ericsson.oss.pmic.dto.subscription.MtrSubscription
import com.ericsson.oss.pmic.dto.subscription.cdts.CellInfo
import com.ericsson.oss.pmic.dto.subscription.cdts.MoinstanceInfo
import com.ericsson.oss.pmic.dto.subscription.enums.MtrAccessType
import com.ericsson.oss.pmic.dto.subscription.enums.SubscriptionType
import com.ericsson.oss.services.pm.initiation.common.ResponseData
import com.ericsson.oss.services.pm.initiation.model.utils.ModelDefiner
import com.ericsson.oss.services.pm.initiation.model.utils.PmMetaDataConstants
import com.ericsson.oss.services.pm.initiation.restful.AttributesForAttachedNodes
import com.ericsson.oss.services.pm.modeling.schema.gen.pfm_measurement.ScannerType
import com.ericsson.oss.services.pm.modelservice.PmCapabilityModelService
import com.ericsson.oss.services.pm.services.generic.SubscriptionReadOperationService
import com.ericsson.services.pm.initiation.restful.api.CounterTableRow
import com.ericsson.services.pm.initiation.restful.api.EventTableRow
import com.ericsson.services.pm.initiation.restful.api.PmMetaDataLookupLocal
import com.ericsson.services.pm.initiation.restful.api.PmMimVersionQuery

import spock.lang.Shared
import spock.lang.Unroll

class SubscriptionComponentUIResourceSpec extends CdiSpecification {

    def NO_EVENT_FILTER = null

    @Shared
    def STATUS_OK = Response.Status.OK.statusCode

    @Shared
    def BAD_REQUEST = Response.Status.BAD_REQUEST.statusCode

    @ObjectUnderTest
    SubscriptionComponentUIResource objectUnderTest

    @Inject
    PmMetaDataLookupLocal pmMetaDataLookupLocal

    @Inject
    private PmCapabilityModelService pmCapabilityModelService

    @Inject
    private SubscriptionReadOperationService subscriptionReadOperationService

    @MockedImplementation
    PmCapabilityReader pmCapabilityReader

    def setup() {
        pmMetaDataLookupLocal.getCounterSubGroups(SubscriptionType.STATISTICAL.name()) >> getSupportedStatsCounterSubGroup()
        pmMetaDataLookupLocal.getCounterSubGroups(SubscriptionType.MOINSTANCE.name()) >> getCounterSubGroup()
    }

    def 'getCounters should return counters for single mimVersion deprecated'() {
        given: 'a pmMimVersionQuery, a counter'
            def pmvq = new PmMimVersionQuery('mim=ERBS:3.1.72')
            def counter = new CounterTableRow('pmMeasurement', 'moClass', 'Performance Measurment for traffic', ScannerType.PRIMARY)
            def result = new TreeSet<CounterTableRow>()
            result.add(counter)

        and: 'when pmMetadataLookup is called, it returns the required counters'
            pmMetaDataLookupLocal.getCounters(pmvq, ['/NE-defined/'], false, true, []) >> result

        when: 'a request to retrieve counters for the defined pmvq and NE model definer is sent'
            def response = objectUnderTest.getCounters(pmvq, ModelDefiner.NE.name())

        then: 'response status OK is returned and the returned entity is same as the counters expected'
            STATUS_OK == response.status
            result == response.entity
    }

    def 'getCounters should return counters for multiple mimVersions'() {
        given: 'multiple mim versions and counters'
            def pmvq = new PmMimVersionQuery('mim=ERBS:3.1.72,MGW:4.1.72')
            def result = new TreeSet<CounterTableRow>()
            result.add(new CounterTableRow('pmMeasurement', 'moClass3', 'Performance Measurment for traffic', ScannerType.PRIMARY))
            result.add(new CounterTableRow('pmMeasurement', 'moClass4', 'Performance Measurment for traffic', ScannerType.PRIMARY))

        and: 'when pmMetadataLookup is called, it returns the required counters'
            pmMetaDataLookupLocal.getCounters(pmvq, ['/NE-defined/'], false, true, []) >> result

        when: 'a request to retrieve counters for the defined pmvq and NE model definer is sent'
            def response = objectUnderTest.getCounters(pmvq, ModelDefiner.NE.name())

        then: 'response status OK is returned and the returned entity is same as the counters expected'
            STATUS_OK == response.status
            result == response.entity
    }

    @Unroll
    def 'getEvents request with correct #eventFilter with a specific #mimVersion should return correct events'(){
        given: 'a valid definer and valid mim with a valid eventFilter is defined'
            def pmMimVersionQueryObj = new PmMimVersionQuery("mim=${pmMimVersionQuery}")

        when: 'a request to fetch the events is sent with the parameters'
            objectUnderTest.getEvents(pmMimVersionQueryObj,eventFilter)

        then: 'pmMetaDataLookupLocal is called to getEvents with the expected filter'
            1 * pmMetaDataLookupLocal.getEvents(pmMimVersionQueryObj,expectedFilter)

        where:
            eventFilter     | pmMimVersionQuery                || expectedFilter
            'cellTraceLRan' | 'RadioNode:0166-930-179:EPS' || 'cellTraceLRan'
            'cellTraceNRan' | 'RadioNode:0166-930-179:5GS' || 'cellTraceNRan'
            null            | 'RadioNode:0166-930-179:5GS' || ''


    }

    def 'getEvents should return events for specific mimVersion'() {
        given: 'a pmMimVersionQuery, a event'
            def pmMimVersionQuery = new PmMimVersionQuery('mim=ERBS:3.1.72')
            def eventTableRow = new EventTableRow('eventName', 'sourceObject')
            def result = new TreeSet<>()
            result.add(eventTableRow)

        and: 'when pmMetadataLookup is called, it returns the required event'
            pmMetaDataLookupLocal.getEvents(pmMimVersionQuery, '') >> result

        when: 'a request to retrieve events for the defined pmvq and no event filter is sent'
            def response = objectUnderTest.getEvents(pmMimVersionQuery, NO_EVENT_FILTER)

        then: 'response status OK is returned and the returned entity is same as the events expected'
            STATUS_OK == response.status
            result == response.entity
    }

    def 'getStatsCounterSubGroup should return map of subgroup counters'() {
        when: 'a request to fetch counterSubGroups is sent with a STATISTICAL Subscription Type'
            def response = objectUnderTest.getCounterSubGroups(SubscriptionType.STATISTICAL.name())
        then: 'response status OK is returned and the returned entity is same as the supported stats counters sub group expected'
            STATUS_OK == response.getStatus()
            getSupportedStatsCounterSubGroup() == response.getEntity()
    }

    def 'getCellTrafficEvents should return ctr profile events for specific mimVersion'() {
        given: 'defined mim version and trigger and non trigger events'
            def pmMimVersionQuery = new PmMimVersionQuery('mim=RNC:3.1.72')

            def result = new HashMap<>()
            def triggeEventTableRow = new EventTableRow('TriggerEventName', 'OTHER')
            def nonTriggeEventTableRow = new EventTableRow('NonTriggerEventName', 'OTHER    ♦')

            def nonTriggerEvents = new TreeSet<>()
            nonTriggerEvents.add(nonTriggeEventTableRow)

            def triggerEvents = new TreeSet<>()
            triggerEvents.add(triggeEventTableRow)
            result.put(PmMetaDataConstants.TRIGGER_EVENTS, triggerEvents)
            result.put(PmMetaDataConstants.NON_TRIGGER_EVENTS, nonTriggerEvents)

        and: 'the respective pmMetaDataLookup method is called then return trigger and non trigger events'
            pmMetaDataLookupLocal.getCellTrafficNonTriggerEventsForAllVersions(pmMimVersionQuery) >> nonTriggerEvents
            pmMetaDataLookupLocal.getWideBandEventsForAllVersions(pmMimVersionQuery, SubscriptionType.CELLTRAFFIC.name()) >> triggerEvents

        when: 'the request to fetch cellTraffic events is called'
            def response = objectUnderTest.getCelltrafficEvents(pmMimVersionQuery, SubscriptionType.CELLTRAFFIC.name())

        then: 'response status OK is returned and the returned entity is same as the expected events'
            STATUS_OK == response.status
            result == response.entity
    }

    def 'getAttachedNodeCount will return correct attached nodes count'() {
        given: 'attributes with appropriate cellList and nodeFdns'
            def cellList = [new CellInfo('node', 'Cell1'), new CellInfo('node', 'Cell2')]
            def nodeFdns = ['NetworkElement=node1', 'NetworkElement=node2']
            AttributesForAttachedNodes attributes = new AttributesForAttachedNodes(cellList as List<CellInfo>, true, nodeFdns as Set<String>)

        and: 'when pmMetaDataLookupLocal is called then return 3 as attached node count'
            pmMetaDataLookupLocal.getAttachedNodeCount(_) >> 3

        when: 'request to fetch the attached node count is sent'
            def result = objectUnderTest.getAttachedNodeCount(attributes)

        then: 'the result is 3'
            ((int) result.entity) == 3
    }

    def 'getMtrAccessTypes will return the accessTypes supported by the MTR'() {
        given: 'a defined Mtr Access Type map'
            def mtrAccesTypeMap = new HashMap<>();
            mtrAccesTypeMap.put('mtrAccessTypes', MtrAccessType.values())

        and: 'when pmMetaDataLookup is called then the defined Mtr Access type map is returned'
            pmMetaDataLookupLocal.getMtrAccessTypes() >> mtrAccesTypeMap

        when: 'request to fetch the Mtr access type map is called'
            def result = objectUnderTest.getMtrAccessTypes()

        then: 'response status OK is returned and the returned entity is same as the expected Mtr Access Type Map'
            STATUS_OK == result.status
            mtrAccesTypeMap == result.entity

    }

    def 'get capabilities request returns a list of all subscription capabilities'(){
        when: 'get capabilites request is sent'
            objectUnderTest.getNodesWithSupportedROPSBySubscriptionType()

        then: 'pmCapabilityServive is called to return the correct capabilities'
            1 * pmCapabilityModelService.getSupportedNetworkElements()
    }

    def 'get resAttributes request returns the RES subscription attributes for the provided MIM version'(){
        given: 'a defined pmvq'
            def pmMimVersionQuery = new PmMimVersionQuery('mim=RNC:0166-930-179:UMTS')

        when: 'a request to fetch the RES attributes for the defined pmvq is sent'
            objectUnderTest.getResAttributes(pmMimVersionQuery)

        then: 'pmMetaDataLookup is called to return the correct RES attributes'
            1 * pmMetaDataLookupLocal.getResAttributes(pmMimVersionQuery)
    }

    @Unroll
    def 'getSupportedMoInstances will return the managed objects supported by the node and the #moClasses class for #subscriptionType subscription type'(){
        given: 'defined nodes, moClasses and pmvq'
            def nodes = 'RNC01:RNC'
            def pmMimVersionQuery = new PmMimVersionQuery('mim=RNC:0166-930-179:UMTS')
            def  moInstances = getSupportedMoInstances()

        and: 'pmMetaDataLookup is called for getting supported mo instances'
            pmMetaDataLookupLocal.getSupportedMoInstances(pmMimVersionQuery.getMimVersions(),nodes,moClasses,subscriptionType) >> moInstances

        when: 'a request to getMoInstances with the pmvq, nodes, moClasses and correct subscriptionType is sent'
            def result = objectUnderTest.getMOInstances(pmMimVersionQuery,nodes,moClasses,subscriptionType)

        then: 'an OK response with the moInstances supported by the node and moClases are returned as response entity'
            STATUS_OK == result.status
            moInstances == result.entity

        where:
            subscriptionType    |   moClasses
            'MOINSTANCE'        |   'Aal0TpVccTp'
            'CELLRELATION'      |   'UtranCell'
    }

    @Unroll
    def 'getCounters with a #subscriptionType filter should return the counters for that subscription type deprecated query'(){
        given: 'a defined mim version query and model definer'
            def pmMimVersionQuery = new PmMimVersionQuery('mim=RNC:0166-930-179:UMTS')
            def modelDefiner = 'NE'
            def result = new TreeSet<>()
            result.add(counters)

        and: 'pmMetadataLookup when called to fetch filtered counters as per the #subscriptionType'
            pmMetaDataLookupLocal.getFilteredCountersForAllVersions(pmMimVersionQuery, ['/NE-defined/'] as List<String>, subscriptionType, true) >> result

        when: 'a request to fetch counters with a valid subscriptionType is sent'
            def response = objectUnderTest.getCounters(subscriptionType, pmMimVersionQuery, modelDefiner)

        then: 'status code OK is returned with the correct counters as response entity'
            STATUS_OK == response.status
            result == response.entity

        where:
            subscriptionType    |   counters
            'MOINSTANCE'        |   new CounterTableRow('pmBwMissinsCells', 'VpcTp', 'miss inserted cells on VCC and VPC', ScannerType.USER_DEFINED)
            'CELLRELATION'      |   new CounterTableRow('pmAttLbhoSpeech', 'GsmRelation', 'Number of attempted outgoing load-based handovers to GSM', ScannerType.USER_DEFINED)
    }

    @Unroll
    def 'getCounters with a #subscriptionType filter should return the counters for that subscription type'(){
        given: 'a defined mim version query and model definer'
            def pmMimVersionQuery = new PmMimVersionQuery('mim=RNC:0166-930-179:UMTS')
            def modelDefiner = "${subscriptionType}_SubscriptionAttributes"
            def result = new TreeSet<>()
            result.add(counters)

        and: 'pmMetadataLookup when called to fetch filtered counters as per the #subscriptionType'
            pmMetaDataLookupLocal.getFilteredCountersForAllVersions(pmMimVersionQuery, ['/NE-defined/'], subscriptionType, false) >> result
            pmCapabilityReader.getSupportedModelDefinersForCounters(modelDefiner) >> ['/NE-defined/']

        when: 'a request to fetch counters with a valid subscriptionType is sent'
            def response = objectUnderTest.getCounters(subscriptionType, pmMimVersionQuery, modelDefiner)

        then: 'status code OK is returned with the correct counters as response entity'
            STATUS_OK == response.status
            result == response.entity

        where:
            subscriptionType    |   counters
            'MOINSTANCE'        |   new CounterTableRow('pmBwMissinsCells', 'VpcTp', 'miss inserted cells on VCC and VPC', ScannerType.USER_DEFINED)
            'CELLRELATION'      |   new CounterTableRow('pmAttLbhoSpeech', 'GsmRelation', 'Number of attempted outgoing load-based handovers to GSM', ScannerType.USER_DEFINED)
    }

    def getSupportedMoInstances(){
            return [
                    new MoinstanceInfo("RNC01","Test MoInstance 1"),
                    new MoinstanceInfo("RNC01","Test MoInstance 2"),
                    new MoinstanceInfo("RNC01","Test MoInstance 3"),
                    new MoinstanceInfo("RNC01","Test MoInstance 4"),
                    new MoinstanceInfo("RNC01","Test MoInstance 5"),
                    new MoinstanceInfo("RNC01","Test MoInstance 6"),
            ]
    }

    def 'get Wcdma GPEH events for node type and version'(){
        given: 'a defined pmvq and subscription type GPEH'
            def pmMimVersionQuery = new PmMimVersionQuery('mim=RNC:0166-930-179:UMTS')
            def result = new TreeSet<>()
            result.add(new EventTableRow('event Name','source object name'))
        and: 'pmMetaDataLookup returns the expected result when GPEH events is requested'
            pmMetaDataLookupLocal.getWideBandEventsForAllVersions(pmMimVersionQuery,'GPEH') >> result
        when: 'a request to get WCDMA events for GPEH subscription type is sent'
            def response = objectUnderTest.getWcdmaEvents(pmMimVersionQuery,'GPEH')
        then: 'status code OK is returned with the expected events as response entity'
            STATUS_OK == response.status
            result == response.entity
    }

    @Unroll
    def 'get nonAssociatedNodes for a MTR subscription when activated'(){
        given: 'an activating subscription id'
            def dummySubId = 1l
        and: 'pmMetaDataLookup returns the expected non associated nodeFdn without connected Msc attribute'
            subscriptionReadOperationService.findByIdWithRetry(dummySubId, true) >> subscription
            pmMetaDataLookupLocal.getNonAssociatedNodes(subscription.getNodesFdns()) >> result
        when: 'a request to get nonAssociatedNode is made'
            def response = objectUnderTest.getNonAssociatedNodes(dummySubId)
        then: 'a response #status is returned with the expected result'
            status == response.status
            if (status==STATUS_OK){
                result == response.entity
            }
        where:
            subscription                                    ||   result                     |   status
            createDummyMtrSubscriptionWithCorrectNodes()    ||  ['NetworkElement=BSC01']    |   STATUS_OK
    }

    def createErrorResponseData(){
        return new ResponseData(Response.Status.BAD_REQUEST,String.format(UNABLE_TO_ACTIVATE_SUBSCRIPTION, null))
    }

    def createDummyMtrSubscriptionWithCorrectNodes(){
        def mscDummyNodes = new Node()
        mscDummyNodes.setFdn('NetworkElement=MSC-DB09')
        def mtrDummySubscription = new MtrSubscription()
        mtrDummySubscription.setNodes([mscDummyNodes])
        return mtrDummySubscription
    }


    def getSupportedStatsCounterSubGroup() {
        return [
                'Intra-frequency' : [
                        'pmRlAddAttemptsBestCellCsConvers',
                        'pmRlAddAttemptsBestCellPacketHigh',
                        'pmRlAddAttemptsBestCellPacketLow'
                ],
                'Inter-frequency' : [
                        'pmAttLoadBasedIfho',
                        'pmAttNonBlindIfhoPsIntEul',
                        'pmAttNonBlindIfhoPsIntHs',
                        'pmAttNonBlindInterFreqHoCsConversational',
                        'pmAttNonBlindIfhoPsStrHs'
                ]
        ]
    }

    def getCounterSubGroup() {
        return [
                'CPS' : [
                        'pmDiscardedEgressCpsPackets',
                        'pmEgressCpsPackets',
                        'pmIngressCpsPackets'
                ],
                'F4/F5' : [
                        'pmBwErrBlocks',
                        'pmBwLostCells',
                        'pmBwMissinsCells',
                        'pmFwErrBlocks',
                        'pmFwLostCells',
                        'pmFwMissinsCells',
                        'pmLostBrCells',
                        'pmLostFpmCells'
                ]
        ]
    }
}

