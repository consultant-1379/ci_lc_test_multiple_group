/*******************************************************************************
 * COPYRIGHT Ericsson 2016
 *
 * The copyright to the computer program(s) herein is the property of
 * Ericsson Inc. The programs may be used and/or copied only with written
 * permission from Ericsson Inc. or in accordance with the terms and
 * conditions stipulated in the agreement/contract under which the
 * program(s) have been supplied.
 ******************************************************************************/

package com.ericsson.oss.services.pm.initiation.enodeb.subscription.resource

import com.ericsson.oss.pmic.api.counters.PmCountersLifeCycleResolver

import static com.ericsson.oss.services.pm.common.constants.PmCapabilityModelConstants.CELLTRACE_LRAN_SUBSCRIPTION_ATTRIBUTES
import static com.ericsson.oss.services.pm.common.constants.PmCapabilityModelConstants.CELLTRACE_NRAN_SUBSCRIPTION_ATTRIBUTES
import static com.ericsson.oss.services.pm.common.constants.PmCapabilityModelConstants.SUPPORTED_MODEL_DEFINERS_FOR_COUNTERS

import spock.lang.Shared
import spock.lang.Unroll

import javax.cache.Cache
import javax.inject.Inject
import javax.ws.rs.core.Response

import com.ericsson.cds.cdi.support.rule.ImplementationClasses
import com.ericsson.cds.cdi.support.rule.MockedImplementation
import com.ericsson.cds.cdi.support.rule.ObjectUnderTest
import com.ericsson.oss.itpf.datalayer.dps.persistence.ManagedObject
import com.ericsson.oss.itpf.datalayer.dps.stub.RuntimeConfigurableDps
import com.ericsson.oss.itpf.sdk.cache.annotation.NamedCache
import com.ericsson.oss.itpf.sdk.security.accesscontrol.EAccessControl
import com.ericsson.oss.itpf.sdk.security.accesscontrol.SecurityViolationException
import com.ericsson.oss.pmic.cdi.test.util.PmBaseSpec
import com.ericsson.oss.pmic.dao.NodeDao
import com.ericsson.oss.pmic.dao.SubscriptionDao
import com.ericsson.oss.pmic.dto.PersistenceTrackingState
import com.ericsson.oss.pmic.dto.subscription.AccessType
import com.ericsson.oss.pmic.dto.subscription.BscRecordingsSubscription
import com.ericsson.oss.pmic.dto.subscription.CellRelationSubscription
import com.ericsson.oss.pmic.dto.subscription.CellTraceSubscription
import com.ericsson.oss.pmic.dto.subscription.CellTrafficSubscription
import com.ericsson.oss.pmic.dto.subscription.ContinuousCellTraceSubscription
import com.ericsson.oss.pmic.dto.subscription.CtumSubscription
import com.ericsson.oss.pmic.dto.subscription.GpehSubscription
import com.ericsson.oss.pmic.dto.subscription.MoinstanceSubscription
import com.ericsson.oss.pmic.dto.subscription.MtrSubscription
import com.ericsson.oss.pmic.dto.subscription.ResSubscription
import com.ericsson.oss.pmic.dto.subscription.RpmoSubscription
import com.ericsson.oss.pmic.dto.subscription.RttSubscription
import com.ericsson.oss.pmic.dto.subscription.StatisticalSubscription
import com.ericsson.oss.pmic.dto.subscription.Subscription
import com.ericsson.oss.pmic.dto.subscription.UETraceSubscription
import com.ericsson.oss.pmic.dto.subscription.UetrSubscription
import com.ericsson.oss.pmic.dto.subscription.cdts.CellInfo
import com.ericsson.oss.pmic.dto.subscription.cdts.CounterInfo
import com.ericsson.oss.pmic.dto.subscription.cdts.EventInfo
import com.ericsson.oss.pmic.dto.subscription.cdts.ResInfo
import com.ericsson.oss.pmic.dto.subscription.cdts.ScheduleInfo
import com.ericsson.oss.pmic.dto.subscription.cdts.StreamInfo
import com.ericsson.oss.pmic.dto.subscription.cdts.TriggerEventInfo
import com.ericsson.oss.pmic.dto.subscription.cdts.UeInfo
import com.ericsson.oss.pmic.dto.subscription.enums.AdministrationState
import com.ericsson.oss.pmic.dto.subscription.enums.CellTraceCategory
import com.ericsson.oss.pmic.dto.subscription.enums.MtrAccessType
import com.ericsson.oss.pmic.dto.subscription.enums.OperationalState
import com.ericsson.oss.pmic.dto.subscription.enums.OutputModeType
import com.ericsson.oss.pmic.dto.subscription.enums.ResServiceCategory
import com.ericsson.oss.pmic.dto.subscription.enums.RopPeriod
import com.ericsson.oss.pmic.dto.subscription.enums.SubscriptionType
import com.ericsson.oss.pmic.dto.subscription.enums.TaskStatus
import com.ericsson.oss.pmic.dto.subscription.enums.UeType
import com.ericsson.oss.pmic.dto.subscription.enums.UserType
import com.ericsson.oss.pmic.impl.modelservice.PmCapabilityReaderImpl
import com.ericsson.oss.services.pm.cache.PmFunctionEnabledWrapper
import com.ericsson.oss.services.pm.ebs.utils.EbsStreamInfoResolver
import com.ericsson.oss.services.pm.exception.RetryServiceException
import com.ericsson.oss.services.pm.initiation.cache.constants.CacheNamingConstants
import com.ericsson.oss.services.pm.initiation.model.metadata.PMICModelDeploymentValidator
import com.ericsson.oss.services.pm.modelservice.PmCapabilityModelService
import com.ericsson.oss.services.pm.modelservice.PmGlobalCapabilities
import com.ericsson.oss.services.pm.services.exception.ValidationException

import static com.ericsson.oss.services.pm.common.constants.PmCapabilityModelConstants.SUPPORTED_SUBSCRIPTION_TYPES
import static com.ericsson.oss.services.pm.common.constants.PmCapabilityModelConstants.SUPPORTED_TECHNOLOGY_DOMAINS

class SubscriptionResourceCreateSpec extends PmBaseSpec {

    static final NODE_NAME_1 = 'LTE01ERBS0001'
    static final NODE_NAME_2 = 'LTE01ERBS0002'
    static final NODE_NAME_3 = 'LTE01ERBS0003'
    static final NODE_NAME_4 = 'RNC03'
    static final NODE_NAME_5 = 'Router6672-1'
    static final NODE_NAME_ERBS_FAKE_CUSTOM_NODE = 'Node_NoOssModelIdentity_NoCounters'
    static final NODE_NAME_SGSN = 'SGSN-16A-CP02-V301'
    static final MANAGED_ELEMENT = 'ManagedElement='
    static final SGSN_MME_1 = 'SgsnMme=1'
    static final COMMA = ','
    static final SGSN_MME_MO_FDN = MANAGED_ELEMENT + NODE_NAME_SGSN + COMMA + SGSN_MME_1
    static final SGSN_MME_NS = 'Sgsn_Mme'
    static final EQ = '='
    static final PLMN_MO_FDN = SGSN_MME_MO_FDN + COMMA + PLMN_TYPE + EQ + PLMN_TYPE
    static final PLMN_TYPE = 'PLMN'
    static final SGSN_MME_TYPE = 'SgsnMme'

    @ObjectUnderTest
    SubscriptionResource objectUnderTest

    @Inject
    @NamedCache(CacheNamingConstants.PMIC_REST_SUBSCRIPTION_CACHE_V2)
    Cache<String, Map<String, Object>> cache

    @MockedImplementation
    EAccessControl eAccessControl

    @MockedImplementation
    PMICModelDeploymentValidator mockedPmicModelDeploymentValidator

    @Inject
    SubscriptionDao subscriptionDao

    @MockedImplementation
    EbsStreamInfoResolver ebsStreamInfoResolver

    @MockedImplementation
    PmCapabilityModelService pmCapabilityModelService

    @MockedImplementation
    PmCountersLifeCycleResolver pmCountersLifeCycleResolver

    @Inject
    NodeDao nodeDao

    @Shared
    def nodeDtos = []
    def nodes

    def fakeNodeDtos = []
    def fakeNodes

    def sgsnNodeDtos = []
    def sgsnNodes

    @MockedImplementation
    PmFunctionEnabledWrapper mockedPmFunctionEnabledWrapper

    @ImplementationClasses
    def classes = [PmCapabilityReaderImpl]

    def setup() {
        eAccessControl.isAuthorized(_, _) >> true
        mockedPmFunctionEnabledWrapper.isPmFunctionEnabled(_) >> true
        def suppCounterWithRop = 'twamp:[15]'
        pmCapabilityModelService.getNodeSupportedRopsForCounters(_, _) >> suppCounterWithRop
        def nranCapabilities = new PmGlobalCapabilities()
        nranCapabilities.updateAttributes([(SUPPORTED_MODEL_DEFINERS_FOR_COUNTERS): ['/NE-defined-CUCP-EBS/', '/NE-defined-CUUP-EBS/', '/NE-defined-DU-EBS/']])
        def lranCapabilities = new PmGlobalCapabilities()
        lranCapabilities.updateAttributes([(SUPPORTED_MODEL_DEFINERS_FOR_COUNTERS): ['/NE-defined-EBS/']])
        pmCapabilityModelService.getGlobalCapabilitiesByFunction(CELLTRACE_NRAN_SUBSCRIPTION_ATTRIBUTES, SUPPORTED_MODEL_DEFINERS_FOR_COUNTERS) >> nranCapabilities
        pmCapabilityModelService.getGlobalCapabilitiesByFunction(CELLTRACE_LRAN_SUBSCRIPTION_ATTRIBUTES, SUPPORTED_MODEL_DEFINERS_FOR_COUNTERS) >> lranCapabilities
        nodes = [
            nodeUtil.builder(NODE_NAME_1).ossModelIdentity('16B-G.1.281').attributes('technologyDomain': ['EPS']).build(),
            nodeUtil.builder(NODE_NAME_2).ossModelIdentity('16B-G.1.281').attributes('technologyDomain': ['EPS']).build(),
            nodeUtil.builder(NODE_NAME_3).ossModelIdentity('16B-G.1.281').attributes('technologyDomain': ['EPS']).build(),
            nodeUtil.builder(NODE_NAME_4).ossModelIdentity('16B-G.1.281').attributes('technologyDomain': ['EPS']).build(),
            nodeUtil.builder(NODE_NAME_5).neType('Router6672').ossModelIdentity('17B').attributes('technologyDomain': ['EPS']).build()
        ]

        if (nodeDtos.isEmpty()) {
            def nodeFdns = []
            nodes.each { nodeFdns.add(it.getFdn()) }
            nodeDtos = nodeDao.findAllByFdn(nodeFdns)
        }

        fakeNodes = [nodeUtil.builder(NODE_NAME_ERBS_FAKE_CUSTOM_NODE).ossModelIdentity(null).attributes('technologyDomain': ['EPS']).build()]
        if (fakeNodeDtos.isEmpty()) {
            def nodeFdns = []
            fakeNodes.each { nodeFdns.add(it.getFdn()) }
            fakeNodeDtos = nodeDao.findAllByFdn(nodeFdns)
        }

        sgsnNodes = [
            nodeUtil.builder(NODE_NAME_SGSN).neType('SGSN-MME').ossModelIdentity('16A-CP02')
                .pmEnabled(true).build()
        ]

        if (sgsnNodeDtos.isEmpty()) {
            def nodeSgsnFdns = []
            sgsnNodes.each { nodeSgsnFdns.add(it.getFdn()) }
            sgsnNodeDtos = nodeDao.findAllByFdn(nodeSgsnFdns)
        }

        ebsStreamInfoResolver.getStreamingDestination() >> [new StreamInfo('128.2.2.2', 22), new StreamInfo('255.255.255.255', 22)]
        def sgsnAttributes = ['mobileCountryCode': '262', 'mobileNetworkCode': '012'] as Map
        createMoInRuntimeDps(SGSN_MME_NS, SGSN_MME_TYPE, SGSN_MME_MO_FDN, new HashMap<>())
        createMoInRuntimeDps(SGSN_MME_NS, PLMN_TYPE, PLMN_MO_FDN, sgsnAttributes)
    }

    @Unroll
    def 'create #type subscription will create subscription and return light sub from dps '() {
        when: '#type subscription is given from UI'
            Response response = objectUnderTest.createSingleSubscription(subscription)

        then: 'Status 201 response with light subscription will be returned'
            noExceptionThrown()
            response.getStatus() == 201
            (response.getEntity() as Subscription).getId() > 0
        and: '#type Subscription created in dps'
            subscriptionDao.findOneById((response.getEntity() as Subscription).getId()).getPersistenceTime() != null

        where: '#type Subscription with 3 nodes and 1 counter/event'
            subscription                          | type
            createStatisticalSubscription()       | 'Statistical'
            createCelltraceSubscription()         | 'Celltrace'
            createEbsLCelltraceSubscription(false) | 'Celltrace'
            createEbsLCelltraceSubscription(true)  | 'Celltrace'
            createMoInstanceSubscription()        | 'Moinstance'
            createCelltrafficSubscription()       | 'Celltraffic'
            createGpehSubscription()              | 'Gpeh'
            createResSubscription()               | 'Res'
            createCellRelationSubscription()      | 'CellRelation'
            createBscRecordingsSubscription()     | 'BscRecordings'
            createMtrSubscription()               | 'Mtr'
            createRpmoSubscription()              | 'Rpmo'
            createRttSubscription()               | 'Rtt'
            createUETraceSubscription()           | 'UEtrace'
    }

    @Unroll
    def 'create subscription will fail if subscription is imported '() {
        given: 'create stub method for capability service'
            def subscription = new StatisticalSubscription()
            subscription.name = 'TestStatSub'
            subscription.nodes = [nodeDtos.get(4)]
            subscription.administrationState = AdministrationState.INACTIVE
            subscription.description = 'Description'
            subscription.criteriaSpecification = []
            subscription.operationalState = OperationalState.NA
            subscription.owner = 'Administrator'
            subscription.rop = RopPeriod.ONE_DAY
            subscription.taskStatus = TaskStatus.OK
            subscription.type = SubscriptionType.STATISTICAL
            subscription.userType = UserType.USER_DEF
            subscription.counters = [new CounterInfo('delayAvg', 'twamp')]
            subscription.scheduleInfo = new ScheduleInfo(null, null)
            subscription.isImported = true
            pmCapabilityModelService.getSubscriptionAttributesGlobalCapabilities(subscription, _) >> [:]

        when: 'import subscription is given from UI'
            objectUnderTest.createSingleSubscription(subscription)

        then: 'validate exception is thrown'
            thrown(ValidationException)
    }

    def 'create subscription will fail if subscription is null'() {
        when: 'null subscription is given from UI'
            def response = objectUnderTest.createSingleSubscription(null)

        then: 'Status 400 response will be returned'
            response.status == 400
    }

    def 'create subscription will fail if subscription name is equal to ContinuousCellTraceSubscription'() {
        when: 'subscription is given from UI'
            def subscription = new ContinuousCellTraceSubscription()
            subscription.name = 'ContinuousCellTraceSubscription'
            subscription.type = SubscriptionType.CONTINUOUSCELLTRACE
            objectUnderTest.createSingleSubscription(subscription)

        then: 'exception is thrown'
            thrown(RetryServiceException)
    }

    def 'create subscription will fail if subscription type is ContinuousCellTraceSubscription'() {
        when: 'subscription is given from UI'
            def subscription = new ContinuousCellTraceSubscription()
            subscription.type = SubscriptionType.CONTINUOUSCELLTRACE
            objectUnderTest.createSingleSubscription(subscription)

        then: 'exception is thrown'
            thrown(RetryServiceException)
    }

    def 'When create subscription is called by unauthorized user, a SecurityViolationException is thrown'() {
        when: 'subscription is given from UI'
            def subscription = createStatisticalSubscription()
            subscription.type = SubscriptionType.CONTINUOUSCELLTRACE
            objectUnderTest.createSingleSubscription(subscription)

        then: 'exception is thrown'
            eAccessControl.isAuthorized(_, _) >> false
            thrown(SecurityViolationException)
    }

    def 'will save subscription successfully with nodes and all attributes and update tracker'() {
        given: 'statistical subscription'
            def sub = createStatisticalSubscription()

        when: 'the subscription is created'
            def response = objectUnderTest.createSingleSubscription(sub)

        then:
            def statSub = subscriptionDao.findOneById((response.entity as Subscription).id) as StatisticalSubscription
            statSub.persistenceTime != null
            statSub.counters == sub.counters

        and: 'tracker will change to DONE'
            cache.iterator().next().value.get('status') == PersistenceTrackingState.DONE.name()
            cache.iterator().next().value.get('subscriptionId') == sub.idAsString
    }

    def 'will save subscription successfully with nodes without ossModelIdentity and not supporting counters'() {
        given: 'statistical subscription'
            mockedPmicModelDeploymentValidator.isCounterValidationSupportedForGivenTargetType(_) >> false

            StatisticalSubscription subscription = createStatisticalSubscription(fakeNodeDtos)

        when: 'the subscription is created'
            def response = objectUnderTest.createSingleSubscription(subscription)
        then:
            def statSub = subscriptionDao.findOneById((response.entity as Subscription).id) as StatisticalSubscription
            statSub.persistenceTime != null
            statSub.counters == subscription.counters

        and: 'tracker will change to DONE'
            cache.iterator().next().value.get('status') == PersistenceTrackingState.DONE.name()
            cache.iterator().next().value.get('subscriptionId') == subscription.idAsString
    }

    def 'will save celltrace subscription successfully with nodes and all attributes and update tracker'() {
        given: 'celltrace subscription'
            def sub = createEbsLCelltraceSubscription(true);

        when: 'the subscription is created'
            def response = objectUnderTest.createSingleSubscription(sub)

        then:
            def statSub = subscriptionDao.findOneById((response.entity as Subscription).id) as CellTraceSubscription
            statSub.persistenceTime != null
            statSub.ebsCounters == sub.ebsCounters

        and: 'tracker will change to DONE'
            cache.iterator().next().value.get('status') == PersistenceTrackingState.DONE.name()
            cache.iterator().next().value.get('subscriptionId') == sub.idAsString
    }

    @Unroll
    def 'createSingleSubscription will return status 201 when #desc'() {
        given: 'UETR subscription object created'
            def subscription = createUetrSubscription(streamInfo)

        when: 'Create UETR subscription'
            def response = objectUnderTest.createSingleSubscription(subscription)

        then: 'Status 201 response will be returned'
            response.status == 201

        where:
            streamInfo                                                             | desc
            [new StreamInfo('', 0, 0)]                                             | 'Blank IP targetTypes sent from UI'
            [new StreamInfo('1.1.1.1', 0, 0)]                                      | 'Valid IP targetTypes sent from UI'
            [(new StreamInfo('1.1.1.1', 0, 0)), (new StreamInfo('1.1.1.2', 0, 0))] | 'Multiple IP values sent from UI'
    }

    def 'createSingleSubscription will throw exception when an invalid ip is passed '() {
        given: 'UETR subscription object created'
            def subscription = createUetrSubscription([new StreamInfo('123', 0, 0)])

        when: 'Create UETR subscription with invalid ip format'
            objectUnderTest.createSingleSubscription(subscription)

        then: 'Exception thrown as the ip address is not in proper format'
            thrown(ValidationException)
    }

    def 'manual creation of subscription of type CTUM must fail'() {

        when: 'Creating CTUM subscription'
            Subscription subscription = createCtumSubscription()
            objectUnderTest.createSingleSubscription(subscription)

        then: 'Exception will be thrown'
            thrown(RetryServiceException)
    }

    def 'Import cell trace ebs-n subscription should have event producer id set for all events'() {
        given: 'stubs for capability service'
            pmCapabilityModelService.getSubscriptionAttributesGlobalCapabilities(_, SUPPORTED_TECHNOLOGY_DOMAINS) >> [:]
            pmCapabilityModelService.getSupportedCapabilityValues('RadioNode', SUPPORTED_SUBSCRIPTION_TYPES) >> ['CELLTRACE']
            pmCapabilityModelService.getNodeAndSubscriptionTypeSupportedRops('RadioNode', SubscriptionType.CELLTRACE) >> [900]
            pmCountersLifeCycleResolver.getSupportedCounterLifeCyclesForTechnologyDomains(_, _) >> ['CURRENT', 'DEPRECATED', 'PRELIMINARY']

        and: 'a subscription to be saved with ebs-n counters all for the CUCP event producer'
            def subscription = createEbsNCellTraceSubscription()
            subscription.isImported = true

        when: 'I try to import the subscription'
            objectUnderTest.createSingleSubscription(subscription)
            def sub = subscriptionDao.findOneByExactName('TestEbsNCellTraceSub', false) as CellTraceSubscription

        then: 'the subscription is saved and all the events for the counters in the subscription have the correct event producer id set'
            sub != null
            sub.events.size() == 1
            sub.events.every({event -> event.eventProducerId == 'CUCP'})
    }

    def createStatisticalSubscription(nodes = nodeDtos) {
        Subscription subscription = new StatisticalSubscription()
        subscription.name = 'TestStatSub'
        subscription.nodes = nodes
        subscription.administrationState = AdministrationState.INACTIVE
        subscription.description = 'Description'
        subscription.criteriaSpecification = []
        subscription.operationalState = OperationalState.NA
        subscription.owner = 'Administrator'
        subscription.rop = RopPeriod.FIFTEEN_MIN
        subscription.taskStatus = TaskStatus.OK
        subscription.type = SubscriptionType.STATISTICAL
        subscription.userType = UserType.USER_DEF
        subscription.counters = [new CounterInfo('pmBbmDlBbCapacityUtilization', 'BbProcessingResource')]
        subscription.scheduleInfo = new ScheduleInfo(null, null)
        return subscription
    }

    def createMoInstanceSubscription() {
        def subscription = new MoinstanceSubscription()
        subscription.name = 'TestMoInstanceStatSub'
        subscription.nodes = nodeDtos
        subscription.administrationState = AdministrationState.INACTIVE
        subscription.description = 'Description'
        subscription.criteriaSpecification = []
        subscription.operationalState = OperationalState.NA
        subscription.owner = 'Administrator'
        subscription.rop = RopPeriod.FIFTEEN_MIN
        subscription.taskStatus =TaskStatus.OK
        subscription.type = SubscriptionType.MOINSTANCE
        subscription.userType = UserType.USER_DEF
        subscription.counters = [new CounterInfo('pmBbmDlBbCapacityUtilization', 'BbProcessingResource')]
        subscription.scheduleInfo = new ScheduleInfo(null, null)
        return subscription
    }

    def createCelltraceSubscription() {
        def subscription = new CellTraceSubscription()
        subscription.name = 'TestCellTraceSub'
        subscription.nodes = nodeDtos
        subscription.administrationState = AdministrationState.INACTIVE
        subscription.description = 'Description'
        subscription.criteriaSpecification = []
        subscription.outputMode = OutputModeType.FILE
        subscription.operationalState = OperationalState.NA
        subscription.owner = 'Administrator'
        subscription.rop = RopPeriod.FIFTEEN_MIN
        subscription.taskStatus = TaskStatus.OK
        subscription.type = SubscriptionType.CELLTRACE
        subscription.userType = UserType.USER_DEF
        subscription.events = [new EventInfo('INTERNAL_EVENT_ADMISSION_BLOCKING_STARTED', 'SESSION_ESTABLISHMENT_EVALUATION')]
        subscription.scheduleInfo = new ScheduleInfo(null, null)
        subscription.cellTraceCategory = CellTraceCategory.CELLTRACE
        return subscription
    }

    def createEbsLCelltraceSubscription(final boolean withNormalEvents) {
        def subscription = (CellTraceSubscription) createCelltraceSubscription()
        subscription.name = 'TestEbsCellTraceSub'
        subscription.ebsCounters = [new CounterInfo('EBS_Counter', 'EBS_Counter_Class')]
        subscription.ebsEvents = [new EventInfo('EBS_Event', 'EBS_Event_Group')]
        subscription.cellTraceCategory = CellTraceCategory.CELLTRACE_AND_EBSL_STREAM
        if (!withNormalEvents) {
            subscription.events = []
            subscription.cellTraceCategory = CellTraceCategory.EBSL_STREAM
        }
        return subscription
    }

    def createEbsNCellTraceSubscription() {
        def subscription = (CellTraceSubscription) createCelltraceSubscription()
        subscription.name = 'TestEbsNCellTraceSub'
        subscription.ebsCounters = [new CounterInfo('pmEbsRwrFailNoEutranTarget', 'NRCellCU'),
                                    new CounterInfo('pmEbsRwrFailPlmnNotAllowed', 'NRCellCU'),
                                    new CounterInfo('pmEbsRwrFailUeCap', 'NRCellCU')]
        subscription.events = []
        subscription.cellTraceCategory = CellTraceCategory.CELLTRACE_NRAN_AND_EBSN_FILE
        def fdn = nodeUtil.builder('NR01gNodeBRadio00001').neType('RadioNode').ossModelIdentity('19.Q3-R41A26').attributes('technologyDomain': ['5GS']).build().fdn
        subscription.nodes = nodeDao.findAllByFdn([fdn])
        return subscription
    }

    def createCelltrafficSubscription() {
        def subscription = new CellTrafficSubscription()
        subscription.name = 'TestCellTrafficSub'
        subscription.nodes = nodeDtos
        subscription.administrationState = AdministrationState.INACTIVE
        subscription.description = 'Description'
        subscription.criteriaSpecification = []
        subscription.outputMode = OutputModeType.FILE
        subscription.operationalState = OperationalState.NA
        subscription.owner = 'Administrator'
        subscription.rop = RopPeriod.FIFTEEN_MIN
        subscription.taskStatus =TaskStatus.OK
        subscription.type = SubscriptionType.CELLTRAFFIC
        subscription.userType = UserType.USER_DEF
        subscription.cellInfoList = [new CellInfo('RNC03', 'RNC03-1-1')]
        subscription.events = [new EventInfo('INTERNAL_EVENT_ADMISSION_BLOCKING_STARTED', 'SESSION_ESTABLISHMENT_EVALUATION')]
        subscription.triggerEventInfo = new TriggerEventInfo('RRC_ACTIVE_SET_UPDATE', 'EVENT_PARAM_RNC_ID_1')
        subscription.scheduleInfo = new ScheduleInfo(null, null)
        return subscription
    }

    def createGpehSubscription() {
        def subscription = new GpehSubscription()
        subscription.name = 'TestGpehSub'
        subscription.nodes = nodeDtos
        subscription.administrationState = AdministrationState.INACTIVE
        subscription.description = 'Description'
        subscription.criteriaSpecification = []
        subscription.outputMode = OutputModeType.FILE
        subscription.operationalState = OperationalState.NA
        subscription.owner = 'Administrator'
        subscription.rop = RopPeriod.FIFTEEN_MIN
        subscription.taskStatus =TaskStatus.OK
        subscription.type = SubscriptionType.GPEH
        subscription.userType = UserType.USER_DEF
        subscription.cells = [new CellInfo('RNC03', 'RNC03-1-1')]
        subscription.events = [new EventInfo('INTERNAL_EVENT_ADMISSION_BLOCKING_STARTED', 'SESSION_ESTABLISHMENT_EVALUATION')]
        subscription.scheduleInfo = new ScheduleInfo(null, null)
        return subscription
    }

    def createUetrSubscription(List<StreamInfo> streamInfoList) {
        def subscription = new UetrSubscription()
        subscription.type = SubscriptionType.UETR
        subscription.name = 'UetrSub'
        subscription.outputMode = OutputModeType.STREAMING
        subscription.userType = UserType.USER_DEF
        subscription.streamInfoList = streamInfoList
        return subscription
    }

    def createRttSubscription() {
        def subscription = new RttSubscription()
        subscription.name = 'TestRttSub'
        subscription.type = SubscriptionType.RTT
        subscription.userType = UserType.USER_DEF
        subscription.administrationState = AdministrationState.INACTIVE
        subscription.description = 'Description'
        subscription.operationalState = OperationalState.NA
        subscription.criteriaSpecification = []
        subscription.owner = 'Administrator'
        subscription.scheduleInfo = new ScheduleInfo(null, null)
        subscription.setUserDeActivationDateTime(null)
        subscription.rop = RopPeriod.FIFTEEN_MIN
        subscription.traceReference = 'Trace Reference'
        subscription.events = [new EventInfo('INTERNAL_EVENT_ADMISSION_BLOCKING_STARTED', 'SESSION_ESTABLISHMENT_EVALUATION')]
        subscription.ueInfoList = [new UeInfo(UeType.IMSI, '123456787654321')]
        return subscription
    }

    def createCellRelationSubscription() {
        def subscription = new CellRelationSubscription()
        subscription.name = 'TestCellRelationSub'
        subscription.nodes = nodeDtos
        subscription.administrationState = AdministrationState.INACTIVE
        subscription.description = 'Description'
        subscription.criteriaSpecification = []
        subscription.operationalState = OperationalState.NA
        subscription.owner = 'Administrator'
        subscription.rop = RopPeriod.FIFTEEN_MIN
        subscription.taskStatus =TaskStatus.OK
        subscription.type = SubscriptionType.CELLRELATION
        subscription.userType = UserType.USER_DEF
        subscription.cellsSupported = true
        subscription.cells = [new CellInfo('RNC03', 'RNC03-1-1')]
        subscription.counters = [new CounterInfo('pmBbmDlBbCapacityUtilization', 'BbProcessingResource')]
        subscription.scheduleInfo = new ScheduleInfo(null, null)
        return subscription
    }

    def createBscRecordingsSubscription() {
        def subscription = new BscRecordingsSubscription()
        subscription.name = 'TestBscRecordingsSub'
        subscription.nodes = nodeDtos
        subscription.administrationState = AdministrationState.INACTIVE
        subscription.description = 'Description'
        subscription.criteriaSpecification = []
        subscription.operationalState = OperationalState.NA
        subscription.owner = 'Administrator'
        subscription.rop = RopPeriod.FIFTEEN_MIN
        subscription.taskStatus =TaskStatus.OK
        subscription.type = SubscriptionType.BSCRECORDINGS
        subscription.userType = UserType.USER_DEF
        subscription.scheduleInfo = new ScheduleInfo(null, null)
        return subscription
    }

    def createMtrSubscription() {
        def subscription = new MtrSubscription()
        subscription.name = 'TestMtrSub'
        subscription.type = SubscriptionType.MTR
        subscription.userType = UserType.USER_DEF
        subscription.administrationState = AdministrationState.INACTIVE
        subscription.description = 'Description'
        subscription.operationalState = OperationalState.NA
        subscription.criteriaSpecification = []
        subscription.owner = 'Administrator'
        subscription.scheduleInfo = new ScheduleInfo(null, null)
        subscription.ueInfo = new UeInfo(UeType.IMSI, '123456787654321')
        subscription.recordingReference = 12
        subscription.traceReference = 'Trace Reference'
        subscription.mtrAccessTypes = [MtrAccessType.TCN]
        subscription.attachedNodes = nodeDtos
        return subscription
    }

    def createRpmoSubscription() {
        def subscription = new RpmoSubscription()
        subscription.name = 'TestRpmoSub'
        subscription.nodes = nodeDtos
        subscription.administrationState = AdministrationState.INACTIVE
        subscription.description = 'Description'
        subscription.criteriaSpecification = []
        subscription.outputMode = OutputModeType.FILE
        subscription.operationalState = OperationalState.NA
        subscription.owner = 'Administrator'
        subscription.rop = RopPeriod.FIFTEEN_MIN
        subscription.taskStatus =TaskStatus.OK
        subscription.type = SubscriptionType.RPMO
        subscription.userType = UserType.USER_DEF
        subscription.cells = [new CellInfo('RNC03', 'RNC03-1-1')]
        subscription.events = [new EventInfo('INTERNAL_EVENT_ADMISSION_BLOCKING_STARTED', 'SESSION_ESTABLISHMENT_EVALUATION')]
        subscription.scheduleInfo = new ScheduleInfo(null, null)
        return subscription
    }

    def createResSubscription() {
        def subscription = new ResSubscription()
        subscription.name = 'TestResInstanceStatSub'
        subscription.nodes = nodeDtos
        subscription.administrationState = AdministrationState.INACTIVE
        subscription.description = 'Description'
        subscription.criteriaSpecification = []
        subscription.operationalState = OperationalState.NA
        subscription.owner = 'Administrator'
        subscription.rop = RopPeriod.FIFTEEN_MIN
        subscription.taskStatus =TaskStatus.OK
        subscription.type = SubscriptionType.RES
        subscription.userType = UserType.USER_DEF
        subscription.counters = [new CounterInfo('UtranCell', 'PmRes1'), new CounterInfo('UtranCell', 'PmRes2'), new CounterInfo('UtranCell', 'PmRes3')]
        subscription.scheduleInfo = new ScheduleInfo(null, null)
        subscription.cells = [new CellInfo('RNC03', 'RNC03-1-1')]
        subscription.setResUeFraction(1)
        subscription.setResMeasPeriods(getResMeasPeriods())
        subscription.setResMeasDef(getResMeasDef())
        return subscription
    }

    def createUETraceSubscription() {
        def subscription = new UETraceSubscription()
        subscription.name = 'TestUETraceSub'
        subscription.description = 'Description'
        subscription.owner = 'Administrator'
        subscription.userType = UserType.USER_DEF
        subscription.type = SubscriptionType.UETRACE
        subscription.scheduleInfo = new ScheduleInfo(null, null)
        subscription.administrationState = AdministrationState.INACTIVE
        subscription.operationalState = OperationalState.NA
        subscription.taskStatus =TaskStatus.OK
        subscription.rop = RopPeriod.FIFTEEN_MIN
        subscription.accessType = AccessType.FULL
        subscription.outputMode = OutputModeType.FILE
        subscription.ueInfo = new UeInfo(UeType.IMSI, '12456')
        return subscription
    }

    def createCtumSubscription() {
        def subscription = new CtumSubscription()
        subscription.name = 'TestCtumSub'
        subscription.owner = 'Administrator'
        subscription.description = 'Description'
        subscription.userType = UserType.USER_DEF
        subscription.administrationState = AdministrationState.INACTIVE
        subscription.operationalState = OperationalState.NA
        subscription.taskStatus =TaskStatus.OK
        subscription.rop = RopPeriod.FIFTEEN_MIN
        subscription.accessType = AccessType.FULL
        subscription.nodes = sgsnNodeDtos
        subscription.outputMode = OutputModeType.FILE
        return subscription
    }
    
    def getResMeasPeriods() {
        return [
                (ResServiceCategory.SPEECH) : 16,
                (ResServiceCategory.INTERACTIVE) : 16,
                (ResServiceCategory.VIDEO) : 16,
                (ResServiceCategory.STREAMING) : 16
        ]
    }

    def getResMeasDef() {
        return [
                'pmRes1': new ResInfo('service1', 'rmq1'),
                'pmRes2': new ResInfo('service2', 'rmq2'),
                'pmRes3': new ResInfo('service3', 'rmq3')
        ]
    }

    private ManagedObject createMoInRuntimeDps(final String ns, final String type, final String fdn, final Map<String, Object> attributes) {
        final ManagedObject managedObject = getCdiInjectorRule().getService(RuntimeConfigurableDps.class).addManagedObject().namespace(ns).type(type)
            .withFdn(fdn).addAttributes(attributes).build()
        return managedObject
    }

}