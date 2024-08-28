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

import static com.ericsson.oss.pmic.dto.subscription.enums.AdministrationState.ACTIVATING
import static com.ericsson.oss.pmic.dto.subscription.enums.AdministrationState.ACTIVE
import static com.ericsson.oss.pmic.dto.subscription.enums.AdministrationState.DEACTIVATING
import static com.ericsson.oss.pmic.dto.subscription.enums.AdministrationState.INACTIVE
import static com.ericsson.oss.pmic.dto.subscription.enums.AdministrationState.SCHEDULED
import static com.ericsson.oss.pmic.dto.subscription.enums.AdministrationState.UPDATING
import static com.ericsson.oss.pmic.dto.subscription.enums.TaskStatus.OK
import static com.ericsson.oss.pmic.dto.subscription.enums.UserType.SYSTEM_DEF
import static com.ericsson.oss.pmic.dto.subscription.enums.UserType.USER_DEF
import static com.ericsson.oss.services.pm.common.constants.PmCapabilityModelConstants.CELLTRACE_LRAN_SUBSCRIPTION_ATTRIBUTES
import static com.ericsson.oss.services.pm.common.constants.PmCapabilityModelConstants.CELLTRACE_NRAN_SUBSCRIPTION_ATTRIBUTES
import static com.ericsson.oss.services.pm.common.constants.PmCapabilityModelConstants.COUNTER_EVENTS_VALIDATION_APPLICABLE
import static com.ericsson.oss.services.pm.common.constants.PmCapabilityModelConstants.STATISTICAL_SUBSCRIPTIONATTRIBUTES
import static com.ericsson.oss.services.pm.common.constants.PmCapabilityModelConstants.SUBSCRIPTION_ATTRIBUTES_SUFFIX
import static com.ericsson.oss.services.pm.common.constants.PmCapabilityModelConstants.SUPPORTED_MODEL_DEFINERS_FOR_COUNTERS
import static com.ericsson.oss.services.pm.initiation.common.Constants.PMIC_CONTINUOUSCELLTRACE_SUBSCRIPTION_NAME
import static com.ericsson.oss.services.pm.initiation.constants.ApplicationMessages.ENDTIME_EQUAL_OR_BEFORE_CURRENTTIME_PLUS_ROP
import static com.ericsson.oss.services.pm.initiation.constants.ApplicationMessages.ENDTIME_GREATER_CURRENT_TIME
import static com.ericsson.oss.services.pm.initiation.constants.ApplicationMessages.ENDTIME_GREATER_START_TIME
import static com.ericsson.oss.services.pm.initiation.constants.ApplicationMessages.INVALID_SUBSCRITPION_TO_ACTIVATE
import static com.ericsson.oss.services.pm.initiation.constants.ApplicationMessages.UNABLE_TO_ACTIVATE_EBM
import static com.ericsson.oss.services.pm.initiation.constants.ApplicationMessages.UNABLE_TO_ACTIVATE_ESN
import static org.testng.Assert.assertEquals

import spock.lang.Unroll

import javax.inject.Inject
import javax.ws.rs.core.Response.Status

import com.ericsson.cds.cdi.support.rule.ImplementationClasses
import com.ericsson.cds.cdi.support.rule.MockedImplementation
import com.ericsson.cds.cdi.support.rule.ObjectUnderTest
import com.ericsson.oss.itpf.datalayer.dps.persistence.ManagedObject
import com.ericsson.oss.itpf.modeling.modelservice.typed.core.target.TargetTypeInformation
import com.ericsson.oss.itpf.sdk.security.accesscontrol.EAccessControl
import com.ericsson.oss.itpf.sdk.security.accesscontrol.SecurityViolationException
import com.ericsson.oss.pmic.cdi.test.util.SkeletonSpec
import com.ericsson.oss.pmic.dto.node.enums.NetworkElementType
import com.ericsson.oss.pmic.dto.subscription.ContinuousCellTraceSubscription
import com.ericsson.oss.pmic.dto.subscription.EventSubscription
import com.ericsson.oss.pmic.dto.subscription.StatisticalSubscription
import com.ericsson.oss.pmic.dto.subscription.Subscription
import com.ericsson.oss.pmic.dto.subscription.cdts.CellInfo
import com.ericsson.oss.pmic.dto.subscription.cdts.CounterInfo
import com.ericsson.oss.pmic.dto.subscription.cdts.EventInfo
import com.ericsson.oss.pmic.dto.subscription.cdts.MoinstanceInfo
import com.ericsson.oss.pmic.dto.subscription.cdts.ScheduleInfo
import com.ericsson.oss.pmic.dto.subscription.cdts.StreamInfo
import com.ericsson.oss.pmic.dto.subscription.cdts.TriggerEventInfo
import com.ericsson.oss.pmic.dto.subscription.cdts.UeInfo
import com.ericsson.oss.pmic.dto.subscription.enums.AdministrationState
import com.ericsson.oss.pmic.dto.subscription.enums.CellTraceCategory
import com.ericsson.oss.pmic.dto.subscription.enums.MtrAccessType
import com.ericsson.oss.pmic.dto.subscription.enums.OperationalState
import com.ericsson.oss.pmic.dto.subscription.enums.RopPeriod
import com.ericsson.oss.pmic.dto.subscription.enums.SubscriptionType
import com.ericsson.oss.pmic.dto.subscription.enums.TaskStatus
import com.ericsson.oss.pmic.dto.subscription.enums.UeType
import com.ericsson.oss.pmic.impl.counters.PmCountersLifeCycleResolverImpl
import com.ericsson.oss.pmic.impl.modelservice.PmCapabilityReaderImpl
import com.ericsson.oss.services.pm.adjuster.impl.SubscriptionMetaDataService
import com.ericsson.oss.services.pm.cache.PmFunctionEnabledWrapper
import com.ericsson.oss.services.pm.ebs.utils.EbsStreamInfoResolver
import com.ericsson.oss.services.pm.initiation.api.ReadPMICConfigurationLocal
import com.ericsson.oss.services.pm.initiation.common.ResponseData
import com.ericsson.oss.services.pm.initiation.ejb.PMICConfigParameter
import com.ericsson.oss.services.pm.initiation.enodeb.subscription.resource.dto.InitiationRequest
import com.ericsson.oss.services.pm.initiation.enodeb.subscription.resource.dto.InitiationResponse
import com.ericsson.oss.services.pm.modelservice.PmCapabilityModelService
import com.ericsson.oss.services.pm.modelservice.PmGlobalCapabilities
import com.ericsson.oss.services.pm.services.exception.ConcurrentSubscriptionUpdateException
import com.ericsson.oss.services.pm.services.exception.ValidationException
import com.ericsson.oss.services.pm.services.generic.SubscriptionReadOperationService

class SubscriptionResourceActivateSpec extends SkeletonSpec {

    static final EventInfo VALID_EVENT = new EventInfo('INTERNAL_EVENT_UE_CAPABILITY', 'CCTR')
    static final EventInfo INVALID_EVENT = new EventInfo('a', 'b')
    static final CounterInfo VALID_COUNTER = new CounterInfo('pmAdmNrRrcUnknownArpRatio', 'AdmissionControl')
    static final CounterInfo INVALID_COUNTER = new CounterInfo('a', 'b')
    static final UeInfo UE_INFO = new UeInfo(UeType.IMSI, '1234567')
    static final MoinstanceInfo MO_INSTANCE_INFO = new MoinstanceInfo('TEST_NODE', 'b')
    static final TriggerEventInfo TRIGGER_EVENT_INFO = new TriggerEventInfo('a', 'b')
    static final Integer RECORDING_REFERENCE = 30

    @ObjectUnderTest
    private SubscriptionResource subscriptionResource

    private final Date persistenceTime = new Date()
    private final InitiationRequest initiationRequest = new InitiationRequest(persistenceTime)
    private final ManagedObject node = createErbsNodeMo('TEST_NODE')

    @MockedImplementation
    EAccessControl eAccessControl

    @MockedImplementation
    SubscriptionMetaDataService subscriptionMetaDataService

    @Inject
    private SubscriptionReadOperationService subscriptionReadOperationService

    @MockedImplementation
    ReadPMICConfigurationLocal configurationLocal

    @MockedImplementation
    EbsStreamInfoResolver ebsStreamInfoResolver

    @MockedImplementation
    PmFunctionEnabledWrapper mockedPmFunctionEnabledWrapper

    @MockedImplementation
    PmCapabilityModelService mockedPmCapabilityModelService

    @ImplementationClasses
    def classes = [PmCapabilityReaderImpl.class, PmCountersLifeCycleResolverImpl.class]

    final Map<String, Object> pmGlobalCapabilities = new HashMap<String, Object>()

    def setup() {
        eAccessControl.isAuthorized(_, _) >> true
        configurationLocal.getConfigParamValue(PMICConfigParameter.MAXNOOFMOINSTANCEALLOWED.name()) >> '999'
        configurationLocal.getConfigParamValue(PMICConfigParameter.MAXNOOFCBSALLOWED.name()) >> '2'
        ebsStreamInfoResolver.getStreamingDestination(_) >> [new StreamInfo('3.3.3.3', 10101)]
        mockedPmFunctionEnabledWrapper.isPmFunctionEnabled(_) >> true
        mockedPmCapabilityModelService.getSupportedCounterLifeCycles(_) >> ['CURRENT', 'DEPRECATED']
        pmGlobalCapabilities.put("subscriptionDescriptionRequired", false)
        mockedPmCapabilityModelService.getGlobalCapabilitiesByFunction(_, "subscriptionDescriptionRequired") >> pmGlobalCapabilities
        def nranCapabilities = new PmGlobalCapabilities()
        nranCapabilities.updateAttributes([(SUPPORTED_MODEL_DEFINERS_FOR_COUNTERS): ['/NE-defined-CUCP-EBS/', '/NE-defined-CUUP-EBS/', '/NE-defined-DU-EBS/']])
        def lranCapabilities = new PmGlobalCapabilities()
        lranCapabilities.updateAttributes([(SUPPORTED_MODEL_DEFINERS_FOR_COUNTERS): ['/NE-defined-EBS/']])
        mockedPmCapabilityModelService.getGlobalCapabilitiesByFunction(CELLTRACE_NRAN_SUBSCRIPTION_ATTRIBUTES, SUPPORTED_MODEL_DEFINERS_FOR_COUNTERS) >> nranCapabilities
        mockedPmCapabilityModelService.getGlobalCapabilitiesByFunction(CELLTRACE_LRAN_SUBSCRIPTION_ATTRIBUTES, SUPPORTED_MODEL_DEFINERS_FOR_COUNTERS) >> lranCapabilities
        subscriptionMetaDataService.getCorrectEvents(_, _, _) >> [VALID_EVENT]
        subscriptionMetaDataService.getEsnApplicableEvents(_) >> [VALID_EVENT]
        subscriptionMetaDataService.getCorrectCounters(_, _, _, _, _, _) >> [VALID_COUNTER]
    }

    @Unroll
    def 'When cbSubscriptionAllowed with less CBS #subscriptionType subscriptions in dps than is allowed, should not throw and exception'() {
        given: 'Two CBS Subscriptions exist in DPS'
            def sub = createSubscriptionMoBuilderWithNameAndState(type, ACTIVE).operationalState(OperationalState.RUNNING)
                .taskStatus(TaskStatus.OK)
                .cbs(true)
                .build()

        when: 'cbSubscriptionAllowed is called on dps'
            subscriptionResource.get(sub.poId + '_duplicate')

        then: 'No Exception should be thrown'
            noExceptionThrown()

        where:
            type << [SubscriptionType.STATISTICAL, SubscriptionType.CELLTRACE]
    }

    @Unroll
    def 'When cbSubscriptionAllowed with MAX number CBS #subscriptionType subscriptions in dps then, it should return InternalServerError'() {
        given: 'Two CBS Subscriptions exist in DPS'
            def sub = createSubscriptionMoBuilderWithNameAndState(type, ACTIVE).operationalState(OperationalState.RUNNING)
                .taskStatus(TaskStatus.OK)
                .cbs(true)
                .build()

            createSubscriptionMoBuilderWithNameAndState(type, ACTIVE, 'sub2').operationalState(OperationalState.RUNNING)
                .taskStatus(TaskStatus.OK)
                .cbs(true)
                .build()

        when: 'cbSubscriptionAllowed is called on dps'
            def resp = subscriptionResource.get(sub.poId + '_duplicate')

        then:
            assertEquals(resp.status, 500)

        where:
            type << [SubscriptionType.STATISTICAL, SubscriptionType.CELLTRACE]
    }

    @Unroll
    def 'When activate is called with a valid #type subscription, the state is changed to ACTIVATING in DPS'() {
        given: 'An inactive subscription in DPS'
            pmGlobalCapabilities.put("subscriptionDescriptionRequired", descriptionRequired)
            mockedPmCapabilityModelService.getSubscriptionAttributesGlobalCapabilities(_, "subscriptionDescriptionRequired") >> pmGlobalCapabilities
            def subMo = createSubscriptionMoBuilder(type, [VALID_EVENT] as EventInfo[], [VALID_COUNTER] as CounterInfo[]).build()

        when: 'The subscription is activated'
            def response = subscriptionResource.activate(subMo.poId, initiationRequest)
            def initiationResponse = (InitiationResponse) response.entity
            def updatedSubscription = subscriptionReadOperationService.findOneById(subMo.poId)

        then: 'It goes to ACTIVATING in DPS'
            ACTIVATING == initiationResponse.administrationState
            response.status == Status.OK.statusCode
            ACTIVATING == updatedSubscription.administrationState
            updatedSubscription.userActivationDateTime.after(persistenceTime)

        where:
            type                                 | descriptionRequired
            SubscriptionType.MOINSTANCE          | false
            SubscriptionType.STATISTICAL         | false
            SubscriptionType.UETR                | true
            SubscriptionType.EBM                 | false
            SubscriptionType.CONTINUOUSCELLTRACE | false
            SubscriptionType.CELLTRAFFIC         | true
            SubscriptionType.CELLTRACE           | true
            SubscriptionType.GPEH                | true
            SubscriptionType.CTUM                | true
            SubscriptionType.UETRACE             | true
            SubscriptionType.RES                 | false
    }

    @Unroll
    def 'When activate is called with a valid #type subscription but some invalid counters or events, thy will be removed'() {
        given: 'An inactive subscription in DPS'
            pmGlobalCapabilities.put("subscriptionDescriptionRequired", descriptionRequired)
            mockedPmCapabilityModelService.getSubscriptionAttributesGlobalCapabilities(_, "subscriptionDescriptionRequired") >> pmGlobalCapabilities
            def subMo = createSubscriptionMoBuilder(type, [VALID_EVENT, INVALID_EVENT] as EventInfo[],
                [VALID_COUNTER, INVALID_COUNTER] as CounterInfo[]).build()

        when: 'The subscription is activated'
            subscriptionResource.activate(subMo.poId, initiationRequest)
            final Subscription updatedSubscription = subscriptionReadOperationService.findOneById(subMo.poId, true)

        then:
            if (updatedSubscription instanceof EventSubscription) {
                updatedSubscription.events.size() == 1
            }
            if (updatedSubscription instanceof StatisticalSubscription) {
                updatedSubscription.counters.size() == 1
            }

        where:
            type                                 | descriptionRequired
            SubscriptionType.MOINSTANCE          | false
            SubscriptionType.STATISTICAL         | false
            SubscriptionType.UETR                | true
            SubscriptionType.EBM                 | false
            SubscriptionType.CONTINUOUSCELLTRACE | false
            SubscriptionType.CELLTRAFFIC         | true
            SubscriptionType.CELLTRACE           | true
            SubscriptionType.GPEH                | true
    }

    def 'When activating a RES subscription with some node conflicts an exception is thrown and available nodes are provided'() {
        given: 'An active and inactive Res subscription in DPS with one node in common'
            def node1 = createErbsNodeMo('node1')
            def node2 = createErbsNodeMo('node2')
            createResSubscriptionMoBuilder('activeResSub', ACTIVE, [node1] as ManagedObject[]).build()
            def inactiveSubMo = createResSubscriptionMoBuilder('inactiveResSub', INACTIVE, [node1, node2] as ManagedObject[]).build()

        when: 'The subscription is activated'
            subscriptionResource.activate(inactiveSubMo.poId, initiationRequest)

        then: 'An exception is thrown'
            thrown ValidationException
    }

    @Unroll
    def 'when hasNodesAndCellsConflict is called for a RES subscription correct response is returned'() {
        given: 'Nodes on DPS'
            def node1 = dps.node()
                .name('RNC01')
                .ossModelIdentity('18.Q2-J.1.280')
                .neType(NetworkElementType.RNC.neTypeString)
                .pmEnabled(true)
                .build()
            def node2 = dps.node()
                .name('RNC02')
                .ossModelIdentity('18.Q2-J.1.280')
                .neType(NetworkElementType.RNC.neTypeString)
                .pmEnabled(true)
                .build()

        and: 'subscription created in DPS'
            def subscriptionMo = createResSubscriptionMoBuilder('activeResSub', ACTIVE, [node1, node2] as ManagedObject[]).cells(cellInfoList).applyOnAllCells(applyAllCells).build()

        when: 'hasNodesAndCellsConflict is called'
            def response = subscriptionResource.hasNodesAndCellsConflict(String.valueOf(subscriptionMo.poId))

        then: 'correct response is returned'
            response.entity == shouldShowWarning
        where:
            applyAllCells | cellInfoList                                                             || shouldShowWarning
            false         | [new CellInfo('RNC01', 'RNC01-1-1'), new CellInfo('RNC02', 'RNC02-2-1')] || false
            false         | [new CellInfo('RNC01', 'RNC01-1-1'), new CellInfo('RNC01', 'RNC01-2-1')] || true
            false         | []                                                                       || true
            true          | []                                                                       || false
    }

    def 'When activate is called on an EBM subscription and one is already active, an exception is thrown'() {
        given: 'An active and an inactive EBM subscription in DPS'
            def subMo = createSubscriptionMoBuilder(SubscriptionType.EBM, [VALID_EVENT] as EventInfo[],
                [VALID_COUNTER] as CounterInfo[]).build()
            def subMo1 = createSubscriptionMoBuilder(SubscriptionType.EBM, [VALID_EVENT] as EventInfo[],
                [VALID_COUNTER] as CounterInfo[]).name('sub2')
                .administrationState(currentSubscriptionState)
                .build()

        when: 'The subscription is activated'
            subscriptionResource.activate(subMo.poId, initiationRequest)

        then: 'An exception is thrown'
            def e = thrown(ValidationException)
            e.message == String.format(UNABLE_TO_ACTIVATE_EBM, subMo1.name)

        where:
            currentSubscriptionState << [ACTIVE, ACTIVATING, UPDATING]
    }

    def 'When activating ESN subscription, should succeed if no other ESN subscription is "Active"'() {
        given: 'An ESN subscription, a node and MOs for each'
            def subscriptionMO = createCellTraceSubscriptionMo(CellTraceCategory.ESN.name())
            dpsUtils.addAssociation(subscriptionMO, 'nodes', node)

        when: 'Subscription activation attempted on first ESN subscription,  no active ESN subscription exists'
            subscriptionResource.activate(subscriptionMO.poId, initiationRequest)
            def subsRetrieved = subscriptionReadOperationService.findOneByExactName(subscriptionMO.name, false)

        then: 'No validation exception is thrown'
            noExceptionThrown()

        and: 'Subscription is activating'
            subsRetrieved.administrationState == ACTIVATING
    }

    @Unroll
    def 'When activating an ESN subscription, should throw exception if other ESN subcription admin state is #subscriptionState'() {
        given: 'An ESN subscription, a node and MOs for each'
            def subscriptionMO = createCellTraceSubscriptionMo(CellTraceCategory.ESN.name())
            dpsUtils.addAssociation(subscriptionMO, 'nodes', node)
            def subscriptionMO2 = createCellTraceSubscriptionMo(CellTraceCategory.ESN.name(), 'sub2', subscriptionState)
            dpsUtils.addAssociation(subscriptionMO2, 'nodes', node)

        when: 'Subscription activation attempted on first ESN subscription'
            subscriptionResource.activate(subscriptionMO.poId, initiationRequest)

        then: 'Validation exception is thrown'
            final ValidationException exception = thrown()

        and: 'Validation exception message is correct'
            String.format(UNABLE_TO_ACTIVATE_ESN, subscriptionMO2.name) == exception.message

        where:
            subscriptionState << [ACTIVE, ACTIVATING, UPDATING]

    }

    def 'When activate is called with an invalid subscription ID error response is returned'() {
        given: 'No subscription in DPS with the given ID'

        when: 'The subscription is activated'
            def result = subscriptionResource.activate(1234L, initiationRequest)

        then:
            (result.entity as ResponseData).error == String.format('Subscription %d not found.', 1234L)
    }

    def 'When activate is called on a subscription with no nodes, an exception is thrown'() {
        given: 'An inactive subscription in DPS'
            def subscriptionMo = createSubscriptionMoBuilderWithNameAndState(SubscriptionType.STATISTICAL).counters(VALID_COUNTER)
                .build()

        when: 'The subscription is activated'
            subscriptionResource.activate(subscriptionMo.poId, initiationRequest)

        then: 'An exception is thrown'
            ValidationException e = thrown(ValidationException)
            e.message == String.format(INVALID_SUBSCRITPION_TO_ACTIVATE, subscriptionMo.name)

        and: 'the subscription state doesnt change'
            INACTIVE == subscriptionReadOperationService.findOneById(subscriptionMo.poId).administrationState
    }

    def 'when activating a resource subscription (#type) with no nodes, an exception will be thrown'() {
        given: 'An inactive subscription in DPS'
            def subMo = createSubscriptionMoBuilder(type, [VALID_EVENT] as EventInfo[],
                [VALID_COUNTER] as CounterInfo[],).nodes([] as ManagedObject[])
                .build()

        when: 'The subscription is activated'
            subscriptionResource.activate(subMo.poId, initiationRequest)

        then:
            def e = thrown(ValidationException)

            e.message.contains('cannot activate unless Nodes and Counters/Events are attached')
        where:
            type << [SubscriptionType.MOINSTANCE,
                     SubscriptionType.CELLRELATION,
                     SubscriptionType.STATISTICAL,
                     SubscriptionType.UETR,
                     SubscriptionType.EBM,
                     SubscriptionType.CONTINUOUSCELLTRACE,
                     SubscriptionType.CELLTRAFFIC,
                     SubscriptionType.CELLTRACE,
                     SubscriptionType.GPEH,
                     SubscriptionType.RES]
    }

    @Unroll
    def 'When activate is called for a scheduled #type subscription with the endDate 30 minutes after the start, it goes to SCHEDULED'() {
        given: 'An inactive subscription where the scheduled time endDate is 30 minutes after the startDate'
            pmGlobalCapabilities.put("subscriptionDescriptionRequired", descriptionRequired)
            mockedPmCapabilityModelService.getSubscriptionAttributesGlobalCapabilities(_, "subscriptionDescriptionRequired") >> pmGlobalCapabilities
            def subMo = createSubscriptionMoBuilderWIthSchedule(type, [VALID_EVENT, INVALID_EVENT] as EventInfo[],
                [VALID_COUNTER, INVALID_COUNTER] as CounterInfo[],
                createSchedule(1, 30)).build()

        when:
            def response = subscriptionResource.activate(subMo.poId, initiationRequest)
            def initiationResponse = (InitiationResponse) response.entity
            def updatedSubscription = subscriptionReadOperationService.findOneById(subMo.poId)

        then:
            AdministrationState.SCHEDULED == initiationResponse.administrationState
            response.status == Status.OK.statusCode
            AdministrationState.SCHEDULED == updatedSubscription.administrationState
            updatedSubscription.userActivationDateTime.after(persistenceTime)

        where:
            type                         | descriptionRequired
            SubscriptionType.MOINSTANCE  | false
            SubscriptionType.STATISTICAL | false
            SubscriptionType.UETR        | true
            SubscriptionType.EBM         | false
            SubscriptionType.CELLTRAFFIC | true
            SubscriptionType.CELLTRACE   | false
            SubscriptionType.GPEH        | true
            SubscriptionType.CTUM        | true
            SubscriptionType.UETRACE     | true
            SubscriptionType.RES         | false
    }

    def createSchedule(final Integer offsetRopsFromCurrentTime, final Integer scheduledEndDateOffset) {

        def cal = Calendar.getInstance()
        cal.add(Calendar.MINUTE, (15 * offsetRopsFromCurrentTime) + 15 - (cal.get(Calendar.MINUTE) % 15))
        def startTime = cal.time
        cal.add(Calendar.MINUTE, scheduledEndDateOffset)
        def endTime = cal.time
        return new ScheduleInfo(startTime, endTime)
    }

    @Unroll
    def 'When activate is called for a scheduled subscription with the endDate -1 minutes after the start, an exception is thrown'() {
        given: 'An inactive subscription where the scheduled time endDate is -1 minutes after the startDate'
            def subMo = createSubscriptionMoBuilderWIthSchedule(type, [VALID_EVENT, INVALID_EVENT] as EventInfo[],
                [VALID_COUNTER, INVALID_COUNTER] as CounterInfo[],
                createSchedule(1, -1)).build()

        when:
            subscriptionResource.activate(subMo.poId, initiationRequest)

        then:
            def e = thrown(ValidationException)
            e.message == ENDTIME_GREATER_START_TIME

        where:
            type << [SubscriptionType.MOINSTANCE,
                     SubscriptionType.CELLRELATION,
                     SubscriptionType.STATISTICAL,
                     SubscriptionType.UETR,
                     SubscriptionType.EBM,
                     SubscriptionType.CONTINUOUSCELLTRACE,
                     SubscriptionType.CELLTRAFFIC,
                     SubscriptionType.CELLTRACE,
                     SubscriptionType.GPEH,
                     SubscriptionType.CTUM,
                     SubscriptionType.UETRACE,
                     SubscriptionType.RES]
    }

    @Unroll
    def 'When activate is called for a scheduled subscription with the endDate 29 minutes after the start, an exception is thrown'() {
        given: 'An inactive subscription where the scheduled time endDate is 29 minutes after the startDate'
            def subMo = createSubscriptionMoBuilderWIthSchedule(type, [VALID_EVENT, INVALID_EVENT] as EventInfo[],
                [VALID_COUNTER, INVALID_COUNTER] as CounterInfo[],
                createSchedule(1, 29)).build()

        when:
            subscriptionResource.activate(subMo.poId, initiationRequest)

        then:
            def e = thrown(ValidationException)
            e.message == ENDTIME_EQUAL_OR_BEFORE_CURRENTTIME_PLUS_ROP

        where:
            type << [SubscriptionType.MOINSTANCE,
                     SubscriptionType.CELLRELATION,
                     SubscriptionType.STATISTICAL,
                     SubscriptionType.UETR,
                     SubscriptionType.EBM,
                     SubscriptionType.CONTINUOUSCELLTRACE,
                     SubscriptionType.CELLTRAFFIC,
                     SubscriptionType.CELLTRACE,
                     SubscriptionType.GPEH,
                     SubscriptionType.CTUM,
                     SubscriptionType.UETRACE,
                     SubscriptionType.RES]
    }

    @Unroll
    def 'When activate is called for a scheduled subscription with the endDate -1 minutes after the start and end time before the current time, an exception is thrown'() {
        given: 'An inactive subscription where the scheduled time endDate is 29 minutes after the startDate'
            def subMo = createSubscriptionMoBuilderWIthSchedule(type, [VALID_EVENT, INVALID_EVENT] as EventInfo[],
                [VALID_COUNTER, INVALID_COUNTER] as CounterInfo[],
                createSchedule(-2, -1)).build()

        when:
            subscriptionResource.activate(subMo.poId, initiationRequest)

        then:
            def e = thrown(ValidationException)
            e.message == ENDTIME_GREATER_CURRENT_TIME

        where:
            type << [SubscriptionType.MOINSTANCE,
                     SubscriptionType.CELLRELATION,
                     SubscriptionType.STATISTICAL,
                     SubscriptionType.UETR,
                     SubscriptionType.EBM,
                     SubscriptionType.CONTINUOUSCELLTRACE,
                     SubscriptionType.CELLTRAFFIC,
                     SubscriptionType.CELLTRACE,
                     SubscriptionType.GPEH,
                     SubscriptionType.CTUM,
                     SubscriptionType.UETRACE,
                     SubscriptionType.RES]
    }

    @Unroll
    def 'When activate is called with a #state subscription a InvalidSubscriptionException is thrown'() {
        given:
            def subMo = createSubscriptionMoBuilder(type, [VALID_EVENT, INVALID_EVENT] as EventInfo[],
                [VALID_COUNTER, INVALID_COUNTER] as CounterInfo[]).administrationState(state)
                .build()
        when:
            subscriptionResource.activate(subMo.poId, initiationRequest)

        then:
            def e = thrown(ValidationException)
            e.message.contains('can\'t be Activated with state')

        where:
            [type, state] << [[SubscriptionType.MOINSTANCE,
                               SubscriptionType.STATISTICAL,
                               SubscriptionType.CELLRELATION,
                               SubscriptionType.UETR,
                               SubscriptionType.EBM,
                               SubscriptionType.CONTINUOUSCELLTRACE,
                               SubscriptionType.CELLTRAFFIC,
                               SubscriptionType.CELLTRACE,
                               SubscriptionType.GPEH,
                               SubscriptionType.CTUM,
                               SubscriptionType.UETRACE,
                               SubscriptionType.RES],
                              [ACTIVATING,
                               ACTIVE,
                               DEACTIVATING,
                               SCHEDULED,
                               UPDATING]].combinations()
    }

    @Unroll
    def 'when activating a subscription with different persistence time than the existing subscription in database, exception will be thrown'() {
        given: 'An inactive subscription where the scheduled time endDate is 29 minutes after the startDate'
            def subMo = createSubscriptionMoBuilder(type, [VALID_EVENT, INVALID_EVENT] as EventInfo[],
                [VALID_COUNTER, INVALID_COUNTER] as CounterInfo[]).build()

        when:
            subscriptionResource.activate(subMo.poId, new InitiationRequest(new Date(1L)))

        then:
            def e = thrown(ConcurrentSubscriptionUpdateException)
            e.message.contains('has changed by another user')

        where:
            type << [SubscriptionType.MOINSTANCE,
                     SubscriptionType.CELLRELATION,
                     SubscriptionType.STATISTICAL,
                     SubscriptionType.UETR,
                     SubscriptionType.EBM,
                     SubscriptionType.CONTINUOUSCELLTRACE,
                     SubscriptionType.CELLTRAFFIC,
                     SubscriptionType.CELLTRACE,
                     SubscriptionType.GPEH,
                     SubscriptionType.UETRACE,
                     SubscriptionType.CTUM,
                     SubscriptionType.RES]
    }

    def 'When activate is called by unauthorized user, a SecurityViolationException is thrown'() {
        given: 'An inactive subscription in DPS'
            def subscription = createSubscriptionMoBuilderWithNameAndState(SubscriptionType.STATISTICAL).build()

        when: 'The subscription is activated'
            subscriptionResource.activate(subscription.poId, initiationRequest)

        then: 'An exception is thrown'
            eAccessControl.isAuthorized(_, _) >> false
            thrown(SecurityViolationException)
    }

    @Unroll
    def 'when activating an event subscription with no Events, an exception will be thrown'() {
        given:
            mockedPmCapabilityModelService.getCapabilityValue(TargetTypeInformation.CATEGORY_NODE, 'ERBS', type.name()+SUBSCRIPTION_ATTRIBUTES_SUFFIX,
                COUNTER_EVENTS_VALIDATION_APPLICABLE,'*') >> true
            def sub = createSubscriptionMoBuilderWithNameAndState(type).nodes(node).build()

        when:
            subscriptionResource.activate(sub.poId, initiationRequest)

        then:
            def e = thrown(ValidationException)
            e.message.contains('cannot activate unless Nodes and Counters/Events are attached')

        where:
            type << [SubscriptionType.CELLTRAFFIC,
                     SubscriptionType.EBM,
                     SubscriptionType.UETR,
                     SubscriptionType.GPEH]
    }

    @Unroll
    def 'when activating an event EBS Stream subscription without resolving ebsStreamInfo, an exception will be thrown'() {
        given:
            def node = dps.node().name('LTEERBS060001').ossModelIdentity('18.Q2-J.1.280').neType('ERBS').build()
            def sub = dps.subscription().
                type(SubscriptionType.CELLTRACE).
                nodes(node).
                administrationState(INACTIVE).
                name('EbsCellTraceTest').
                cellTraceCategory(cellTrcaeCategory).
                persistenceTime(persistenceTime).
                ebsEvents(new EventInfo('INTERNAL_PROC_UE_CTXT_MODIFY', 'INTERNAL')).
                build()

        when:
            subscriptionResource.activate(sub.poId, initiationRequest)

        then:
            ebsStreamInfoResolver.getStreamingDestination(_) >> []
            def exception = thrown(ValidationException)
            exception.message.contains('Subscription EbsCellTraceTest cannot be activated without Stream Cluster Deployment, Please contact ' +
                'System Administrator')

        where:
            cellTrcaeCategory << [
                CellTraceCategory.ASR,
                CellTraceCategory.EBSL_STREAM,
                CellTraceCategory.CELLTRACE_AND_EBSL_STREAM,
                CellTraceCategory.NRAN_EBSN_STREAM
            ]
    }

    def 'When activate is called on a celltrace subscription with ebs events without normal events, Ebs Stream Cluster is deployed'() {
        given: 'An inactive subscription in DPS'
            def subscription = createCellTraceSubscriptionMo(CellTraceCategory.EBSL_STREAM.name())
            dpsUtils.addAssociation(subscription, 'nodes', node)

        when: 'The subscription is activated'
            subscriptionResource.activate(subscription.poId, initiationRequest)

        then: 'subscription is activated'
            ACTIVATING == subscriptionReadOperationService.findOneById(subscription.poId).administrationState
    }

    def 'When activate is called on a CCTR subscription with events and CellTraceCategory as null, during activation subscription object update with CellTraceCategory attribute as Celltrace'() {
        given: 'An inactive subscription in DPS'
            def subscription = createCCTRSubscriptionMo(CellTraceCategory.CELLTRACE)
            dpsUtils.addAssociation(subscription, 'nodes', node)

        when: 'The subscription is activated'
            subscriptionResource.activate(subscription.poId, initiationRequest)

        then: 'subscription is activated'
            def sub = (ContinuousCellTraceSubscription) subscriptionReadOperationService.findOneById(subscription.poId);
            ACTIVATING == sub.administrationState

        and: 'Subscription object updated with CellTraceCategory attribute targetTypes as "CELLTRACE"'
            sub.cellTraceCategory == CellTraceCategory.CELLTRACE
    }

    @Unroll
    def 'when activating a statistical subscription with no Counters, an exception will be thrown'() {
        given:
            mockedPmCapabilityModelService.getCapabilityValue(TargetTypeInformation.CATEGORY_NODE, 'ERBS', STATISTICAL_SUBSCRIPTIONATTRIBUTES,
                COUNTER_EVENTS_VALIDATION_APPLICABLE, '*') >> true
            def sub = createSubscriptionMoBuilderWithNameAndState(type).nodes(node)
                .moInstances(MO_INSTANCE_INFO)
                .build()

        when:
            subscriptionResource.activate(sub.poId, initiationRequest)

        then:
            def e = thrown(ValidationException)
            e.message.contains('cannot activate unless Nodes and Counters/Events are attached')

        where:
            type << [SubscriptionType.STATISTICAL,
                     SubscriptionType.MOINSTANCE,
                     SubscriptionType.CELLRELATION,
                     SubscriptionType.RES]
    }

    def 'when activating an UETR subscription with empty ueInfoList, exception will be thrown'() {
        given:
            def subMo = createSubscriptionMoBuilder(SubscriptionType.UETR, [VALID_EVENT] as EventInfo[],
                [] as CounterInfo[]).ueInfo(null)
                .ueInfoList([] as UeInfo[])
                .build()

        when:
            subscriptionResource.activate(subMo.poId, initiationRequest)

        then:
            def e = thrown(ValidationException)
            e.message.contains('cannot activate unless one or more IMSIs are specified')
    }

    def 'when activating a MoInstance subscription with missing moInstances, exceptions will be thrown'() {
        given:
            def subMo = createSubscriptionMoBuilder(SubscriptionType.MOINSTANCE, [] as EventInfo[],
                VALID_COUNTER as CounterInfo[]).moInstances([] as MoinstanceInfo[]).build()

        when:
            subscriptionResource.activate(subMo.poId, initiationRequest)

        then:
            def e = thrown(ValidationException)
            e.message.contains('cannot activate unless MO Instances are attached')
    }

    def 'when activating a CellRelation subscription with missing Cells, exceptions will be thrown'() {
        given:
            def subMo = createSubscriptionMoBuilder(SubscriptionType.CELLRELATION, [] as EventInfo[],
                VALID_COUNTER as CounterInfo[]).cells([] as CellInfo[]).build()

        when:
            subscriptionResource.activate(subMo.poId, initiationRequest)

        then:
            def e = thrown(ValidationException)
            e.message == "Subscription ${subMo.name} cannot activate unless Cells are attached."
    }

    def 'when activating a CellTraffic subscription with missing trigered events, exceptions will be thrown'() {
        given:
            def subMo = createSubscriptionMoBuilder(SubscriptionType.CELLTRAFFIC, [VALID_EVENT] as EventInfo[],
                [] as CounterInfo[]).triggerEventInfo(null)
                .build()

        when:
            subscriptionResource.activate(subMo.poId, initiationRequest)

        then:
            def e = thrown(ValidationException)
            e.message.contains("Subscription ${subMo.name} cannot activate unless trigger event is attached.")
    }

    @Unroll
    def 'When activate is called on a statistical subscription, with node not supporting counters, the state is changed to ACTIVATING in DPS'() {
        given: 'An inactive subscription in DPS'
            pmGlobalCapabilities.put("subscriptionDescriptionRequired", descriptionRequired)
            mockedPmCapabilityModelService.getSubscriptionAttributesGlobalCapabilities(_, "subscriptionDescriptionRequired") >> pmGlobalCapabilities
            mockedPmCapabilityModelService.getCapabilityValue(TargetTypeInformation.CATEGORY_NODE, 'ERBS', STATISTICAL_SUBSCRIPTIONATTRIBUTES,
                COUNTER_EVENTS_VALIDATION_APPLICABLE, '*') >> false
            def subMo = createSubscriptionMoBuilderWithNameAndState(type).nodes(node)
                .userType(USER_DEF)
                .counters([VALID_COUNTER] as CounterInfo[])
                .build()

        when: 'The subscription is activated'
            def response = subscriptionResource.activate(subMo.poId, initiationRequest)
            def initiationResponse = (InitiationResponse) response.entity
            def updatedSubscription = subscriptionReadOperationService.findOneById(subMo.poId)

        then: 'It goes to ACTIVATING in DPS'
            ACTIVATING == initiationResponse.administrationState
            response.status == Status.OK.statusCode
            ACTIVATING == updatedSubscription.administrationState
            updatedSubscription.userActivationDateTime.after(persistenceTime)

        where:
            type                         | descriptionRequired
            SubscriptionType.STATISTICAL | false
    }

    @Unroll
    def 'when activating a statistical subscription with node not supporting counters and scheduleInfo provided, the state is changed to SCHEDULED in DPS'() {
        given:
            pmGlobalCapabilities.put("subscriptionDescriptionRequired", descriptionRequired)
            mockedPmCapabilityModelService.getSubscriptionAttributesGlobalCapabilities(_, "subscriptionDescriptionRequired") >> pmGlobalCapabilities
            mockedPmCapabilityModelService.getCapabilityValue(TargetTypeInformation.CATEGORY_NODE, _, STATISTICAL_SUBSCRIPTIONATTRIBUTES,
                COUNTER_EVENTS_VALIDATION_APPLICABLE, '*') >> false
            def sub = createSubscriptionMoBuilderWithNameAndState(type).nodes(node)
                .scheduleInfo(createSchedule(1, 30))
                .counters([VALID_COUNTER] as CounterInfo[])
                .build()

        when: 'The subscription is activated'
            def response = subscriptionResource.activate(sub.poId, initiationRequest)
            def initiationResponse = (InitiationResponse) response.entity
            def updatedSubscription = subscriptionReadOperationService.findOneById(sub.poId)

        then: 'It goes to ACTIVATING in DPS'
            SCHEDULED == initiationResponse.administrationState
            response.status == Status.OK.statusCode
            SCHEDULED == updatedSubscription.administrationState
            updatedSubscription.userActivationDateTime.after(persistenceTime)

        where:
            type                         | descriptionRequired
            SubscriptionType.STATISTICAL | false
    }

    @Unroll
    def 'When activate is called on a statistical subscription without counter selected, with node not supporting counters, and COUNTER_EVENTS_VALIDATION_APPLICABLE = true, exception shoul be thrown'() {
        given: 'An inactive subscription in DPS'
            mockedPmCapabilityModelService.getCapabilityValue(TargetTypeInformation.CATEGORY_NODE, 'ERBS', STATISTICAL_SUBSCRIPTIONATTRIBUTES,
                COUNTER_EVENTS_VALIDATION_APPLICABLE, '*') >> true
            def subMo = createSubscriptionMoBuilderWithNameAndState(type).nodes(node)
                .userType(USER_DEF)
                .build()

        when: 'The subscription is activated'
            subscriptionResource.activate(subMo.poId, initiationRequest)

        then: 'It thrown exception'
            def e = thrown(ValidationException)
            e.message.contains(String.format('cannot activate unless Nodes and Counters/Events are attached.'))

        where:
            type << [SubscriptionType.STATISTICAL]
    }

    @Unroll
    def 'when activating a statistical subscription with node not supporting counters and scheduleInfo provided, and COUNTER_EVENTS_VALIDATION_APPLICABLE = true, exception should be thrown'() {
        given:
            pmGlobalCapabilities.put("subscriptionDescriptionRequired", false)
            mockedPmCapabilityModelService.getSubscriptionAttributesGlobalCapabilities(_, "subscriptionDescriptionRequired") >> pmGlobalCapabilities
            mockedPmCapabilityModelService.getCapabilityValue(TargetTypeInformation.CATEGORY_NODE, _, STATISTICAL_SUBSCRIPTIONATTRIBUTES,
                COUNTER_EVENTS_VALIDATION_APPLICABLE, '*') >> true
            def sub = createSubscriptionMoBuilderWithNameAndState(type).nodes(node)
                .scheduleInfo(createSchedule(1, 30))
                .build()

        when: 'The subscription is activated'
            subscriptionResource.activate(sub.poId, initiationRequest)

        then: 'It thrown exception'
            def e = thrown(ValidationException)
            e.message.contains(String.format('cannot activate unless Nodes and Counters/Events are attached.'))

        where:
            type << [SubscriptionType.STATISTICAL]
    }

    ManagedObject createCellTraceSubscriptionMo(final String cellTraceCategory,
                                                final String name = 'sub1',
                                                final AdministrationState state = INACTIVE) {
        return cellTraceSubscriptionBuilder.name(name)
            .administrativeState(state)
            .persistenceTime(persistenceTime)
            .addNode(node)
            .addEbsEvent('INTERNAL', 'INTERNAL_EVENT_ANR_CONFIG_MISSING')
            .userType(SYSTEM_DEF)
            .taskStatus(OK)
            .cellTraceCategory(cellTraceCategory)
            .build()
    }

    @Unroll
    def 'when activating a #type subscription when description #descriptionType, an exception will be thrown'() {
        given: 'An inactive subscription in DPS'
            pmGlobalCapabilities.put("subscriptionDescriptionRequired", true)
            mockedPmCapabilityModelService.getSubscriptionAttributesGlobalCapabilities(_, "subscriptionDescriptionRequired") >> pmGlobalCapabilities
            def subMo = createSubscriptionMoBuilder(type, [VALID_EVENT] as EventInfo[], [VALID_COUNTER] as CounterInfo[], description).build()

        when: 'The subscription is activated'
            subscriptionResource.activate(subMo.poId, initiationRequest)

        then: 'an exception is thrown'
            def e = thrown(ValidationException)
            e.message.contains('It is required to provide a justification in the description before activating a trace subscription.')

        where:
            type                           | description | descriptionType
            SubscriptionType.UETRACE       | null        | 'null'
            SubscriptionType.UETRACE       | ''          | 'empty'
            SubscriptionType.UETRACE       | ' '         | 'contains only spaces'
            SubscriptionType.UETRACE       | '\t'        | 'contains only tabs'
            SubscriptionType.UETRACE       | '\r'        | 'contains only CR'
            SubscriptionType.UETRACE       | '\n'        | 'contains only new line'
            SubscriptionType.UETR          | null        | 'null'
            SubscriptionType.UETR          | ''          | 'empty'
            SubscriptionType.UETR          | ' '         | 'contains only spaces'
            SubscriptionType.UETR          | '\t'        | 'contains only tabs'
            SubscriptionType.UETR          | '\r'        | 'contains only CR'
            SubscriptionType.UETR          | '\n'        | 'contains only new line'
            SubscriptionType.CELLTRAFFIC   | null        | 'null'
            SubscriptionType.CELLTRAFFIC   | ''          | 'empty'
            SubscriptionType.CELLTRAFFIC   | ' '         | 'contains only spaces'
            SubscriptionType.CELLTRAFFIC   | '\t'        | 'contains only tabs'
            SubscriptionType.CELLTRAFFIC   | '\r'        | 'contains only CR'
            SubscriptionType.CELLTRAFFIC   | '\n'        | 'contains only new line'
            SubscriptionType.GPEH          | null        | 'null'
            SubscriptionType.GPEH          | ''          | 'empty'
            SubscriptionType.GPEH          | ' '         | 'contains only spaces'
            SubscriptionType.GPEH          | '\t'        | 'contains only tabs'
            SubscriptionType.GPEH          | '\r'        | 'contains only CR'
            SubscriptionType.GPEH          | '\n'        | 'contains only new line'
            SubscriptionType.BSCRECORDINGS | null        | 'null'
            SubscriptionType.BSCRECORDINGS | ''          | 'empty'
            SubscriptionType.BSCRECORDINGS | ' '         | 'contains only spaces'
            SubscriptionType.BSCRECORDINGS | '\t'        | 'contains only tabs'
            SubscriptionType.BSCRECORDINGS | '\r'        | 'contains only CR'
            SubscriptionType.BSCRECORDINGS | '\n'        | 'contains only new line'
            SubscriptionType.MTR           | null        | 'null'
            SubscriptionType.MTR           | ''          | 'empty'
            SubscriptionType.MTR           | ' '         | 'contains only spaces'
            SubscriptionType.MTR           | '\t'        | 'contains only tabs'
            SubscriptionType.MTR           | '\r'        | 'contains only CR'
            SubscriptionType.MTR           | '\n'        | 'contains only new line'
    }

    @Unroll
    def 'when activating first #subscriptionType subscription with rop=#ropPeriod and #numOfNodes nodes, expectedException=#exception'() {
        given: 'Some nodes involved with the subscription to activate'
            def nodes = new ManagedObject[numOfNodes]
            for (int i = 0; i < numOfNodes; ++i) {
                nodes[i] = dps.node()
                    .name('TestNode00' + i)
                    .neType(NetworkElementType.RADIONODE.getNeTypeString())
                    .pmEnabled(true)
                    .build()
            }

        and: 'An inactive subscription in DPS'
            pmGlobalCapabilities.put("subscriptionDescriptionRequired", true)
            mockedPmCapabilityModelService.getSubscriptionAttributesGlobalCapabilities(_, "subscriptionDescriptionRequired") >> pmGlobalCapabilities
            def subMo = createSubscriptionMoBuilderWithNameAndState(SubscriptionType.STATISTICAL).nodes(nodes)
                .counters(VALID_COUNTER as CounterInfo[])
                .rop(ropPeriod)
                .description('not blank')
                .build()

        when: 'The subscription is activated'
            try {
                subscriptionResource.activate(subMo.poId, initiationRequest)
            } catch (final ValidationException e) {
                assert exception: "Unexpected ValidationException"
            }
            def updatedSubscription = subscriptionReadOperationService.findOneById(subMo.poId)

        then: 'subscription is activated'
            (exception ? INACTIVE : ACTIVATING) == updatedSubscription.administrationState

        where:
            ropPeriod             | numOfNodes || exception
            RopPeriod.ONE_MIN     | 2          || false
            RopPeriod.FIVE_MIN    | 2          || false
            RopPeriod.FIFTEEN_MIN | 2          || false
            RopPeriod.THIRTY_MIN  | 2          || false
            RopPeriod.ONE_HOUR    | 2          || false
            RopPeriod.TWELVE_HOUR | 2          || false
            RopPeriod.ONE_DAY     | 2          || false
            RopPeriod.ONE_MIN     | 3          || true
            RopPeriod.FIVE_MIN    | 3          || true
            RopPeriod.FIFTEEN_MIN | 3          || false
            RopPeriod.THIRTY_MIN  | 3          || true
            RopPeriod.ONE_HOUR    | 3          || true
            RopPeriod.TWELVE_HOUR | 3          || true
            RopPeriod.ONE_DAY     | 3          || false
    }

    @Unroll
    def 'when activating another #subscriptionType subscription with rop=#ropPeriod and #numOfNodes nodes, expectedException=#exception'() {
        given: 'Some nodes and subscriptions already configured'
            def neType = NetworkElementType.RADIONODE
            pmGlobalCapabilities.put("subscriptionDescriptionRequired", true)
            mockedPmCapabilityModelService.getSubscriptionAttributesGlobalCapabilities(_, "subscriptionDescriptionRequired") >> pmGlobalCapabilities
            alreadyConfiguredSubscription.eachWithIndex { rop, adminState, index ->
                def node = dps.node()
                    .name('TestNode00' + index)
                    .neType(neType.getNeTypeString())
                    .pmEnabled(true)
                    .build()
                createSubscriptionMoBuilderWithNameAndState(SubscriptionType.STATISTICAL, adminState, 'sub00' + index).nodes(node)
                    .counters(VALID_COUNTER as CounterInfo[])
                    .rop(rop)
                    .description('not blank')
                    .build()
            }

        and: 'Some nodes involved with the subscription to activate'
            def node = dps.node()
                .name('TestNode00')
                .neType(neType.getNeTypeString())
                .pmEnabled(true)
                .build()

        and: 'An inactive subscription in DPS'
            def subMo = createSubscriptionMoBuilderWithNameAndState(SubscriptionType.STATISTICAL).nodes(node)
                .counters(VALID_COUNTER as CounterInfo[])
                .rop(ropPeriod)
                .description('not blank')
                .build()

        when: 'The subscription is activated'
            try {
                subscriptionResource.activate(subMo.poId, initiationRequest)
            } catch (final ValidationException e) {
                assert exception: "Unexpected ValidationException"
            }
            def updatedSubscription = subscriptionReadOperationService.findOneById(subMo.poId)

        then: 'subscription is activated'
            (exception ? INACTIVE : ACTIVATING) == updatedSubscription.administrationState

        where:
            alreadyConfiguredSubscription                                       | ropPeriod             || exception
            [(RopPeriod.ONE_MIN): (ACTIVE), (RopPeriod.FIVE_MIN): (INACTIVE)]   | RopPeriod.ONE_MIN     || false
            [(RopPeriod.ONE_MIN): (ACTIVE), (RopPeriod.FIVE_MIN): (ACTIVE)]     | RopPeriod.ONE_MIN     || true
            [(RopPeriod.FIFTEEN_MIN): (ACTIVE), (RopPeriod.FIVE_MIN): (ACTIVE)] | RopPeriod.ONE_MIN     || false
            [(RopPeriod.ONE_MIN): (ACTIVE), (RopPeriod.FIVE_MIN): (ACTIVE)]     | RopPeriod.FIFTEEN_MIN || false
    }

    def createResSubscriptionMoBuilder(final String name,
                                       final AdministrationState state,
                                       final ManagedObject[] nodes) {
        dps.subscription()
            .type(SubscriptionType.RES)
            .name(name)
            .administrationState(state)
            .persistenceTime(persistenceTime)
            .userType(USER_DEF)
            .counters([VALID_COUNTER] as CounterInfo[])
            .nodes(nodes)
    }

    def createResSubscriptionMo(final String name,
                                final AdministrationState state,
                                final ManagedObject[] nodes) {
        dps.subscription()
            .type(SubscriptionType.RES)
            .name(name)
            .administrationState(state)
            .persistenceTime(persistenceTime)
            .userType(USER_DEF)
            .counters([VALID_COUNTER] as CounterInfo[])
            .nodes(nodes)
            .build()
    }

    def createCCTRSubscriptionMo(final CellTraceCategory cellTraceCategory) {
        dps.subscription()
            .type(SubscriptionType.CONTINUOUSCELLTRACE)
            .name(PMIC_CONTINUOUSCELLTRACE_SUBSCRIPTION_NAME)
            .administrationState(INACTIVE)
            .persistenceTime(persistenceTime)
            .events([VALID_EVENT] as EventInfo[])
            .userType(SYSTEM_DEF)
            .taskStatus(OK)
            .cellTraceCategory(cellTraceCategory)
            .build()
    }

    def createSubscriptionMoBuilderWithNameAndState(final SubscriptionType type,
                                                    final AdministrationState state = INACTIVE,
                                                    final String name = 'sub1') {
        dps.subscription()
            .type(type)
            .name(name)
            .administrationState(state)
            .persistenceTime(persistenceTime)
            .userType(USER_DEF)
    }

    def createSubscriptionMoBuilderWIthSchedule(final SubscriptionType type,
                                                final EventInfo[] events,
                                                final CounterInfo[] counters,
                                                final ScheduleInfo scheduleInfo) {
        createSubscriptionMoBuilder(type, events, counters).scheduleInfo(scheduleInfo)
    }

    def createSubscriptionMoBuilder(final SubscriptionType type,
                                    final EventInfo[] events,
                                    final CounterInfo[] counters,
                                    final String description = 'not blank') {
        createSubscriptionMoBuilderWithNameAndState(type, INACTIVE).ueInfo(UE_INFO)
            .ueInfoList(UE_INFO)
            .events(events)
            .counters(counters)
            .triggerEventInfo(TRIGGER_EVENT_INFO)
            .moInstances(MO_INSTANCE_INFO)
            .nodes(node)
            .description(description)
            .attributes([
            "mtrAccessTypes"    : [MtrAccessType.LCS.name()],
            "recordingReference": RECORDING_REFERENCE
        ])

    }

    def createErbsNodeMo(final String name) {
        dps.node()
            .name(name)
            .ossModelIdentity('16B-G.1.281')
            .neType(NetworkElementType.ERBS.getNeTypeString())
            .pmEnabled(true)
            .technologyDomain(Arrays.asList('EPS'))
            .build()
    }
}
