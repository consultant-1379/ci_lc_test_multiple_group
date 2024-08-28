/*
 * COPYRIGHT Ericsson 2017
 *
 * The copyright to the computer program(s) herein is the property of
 * Ericsson Inc. The programs may be used and/or copied only with written
 * permission from Ericsson Inc. or in accordance with the terms and
 * conditions stipulated in the agreement/contract under which the
 * program(s) have been supplied.
 */

package com.ericsson.oss.services.pm.services.generic

import com.ericsson.oss.pmic.dto.subscription.cdts.UeInfo

import static com.ericsson.oss.pmic.dto.PersistenceTrackingState.*
import static com.ericsson.oss.pmic.dto.subscription.CellTrafficSubscription.CellTrafficSubscription300Attributes.cellInfoList
import static com.ericsson.oss.pmic.dto.subscription.CellTrafficSubscription.CellTrafficSubscription300Attributes.triggerEventInfo
import static com.ericsson.oss.pmic.dto.subscription.MoinstanceSubscription.MoinstanceSubscription100Attribute.moInstances
import static com.ericsson.oss.pmic.dto.subscription.UetrSubscription.UetrSubscription100Attribute.ueInfoList
import static com.ericsson.oss.pmic.dto.subscription.cdts.CellInfo.CellInfo100Attributes.nodeName
import static com.ericsson.oss.pmic.dto.subscription.cdts.CellInfo.CellInfo100Attributes.utranCellId
import static com.ericsson.oss.pmic.dto.subscription.cdts.MoinstanceInfo.MoinstanceInfo100Attribute.moInstanceName
import static com.ericsson.oss.pmic.dto.subscription.cdts.TriggerEventInfo.TriggerEventInfo100Attributes.groupName
import static com.ericsson.oss.pmic.dto.subscription.cdts.TriggerEventInfo.TriggerEventInfo100Attributes.name
import static com.ericsson.oss.pmic.dto.subscription.enums.RopPeriod.FIFTEEN_MIN

import spock.lang.Unroll

import javax.cache.Cache
import javax.inject.Inject

import com.ericsson.cds.cdi.support.rule.ImplementationInstance
import com.ericsson.oss.itpf.datalayer.dps.persistence.ManagedObject
import com.ericsson.oss.itpf.sdk.cache.annotation.NamedCache
import com.ericsson.oss.pmic.cdi.test.util.SkeletonSpec
import com.ericsson.oss.pmic.cdi.test.util.builder.subscription.*
import com.ericsson.oss.pmic.dto.node.enums.NetworkElementType
import com.ericsson.oss.pmic.dao.SubscriptionDao
import com.ericsson.oss.pmic.dto.PersistenceTrackingState
import com.ericsson.oss.pmic.dto.PersistenceTrackingStatus
import com.ericsson.oss.pmic.dto.SubscriptionPersistenceTrackingStatus
import com.ericsson.oss.pmic.dto.subscription.*
import com.ericsson.oss.pmic.dto.subscription.cdts.CellInfo
import com.ericsson.oss.pmic.dto.subscription.cdts.CounterInfo
import com.ericsson.oss.pmic.dto.subscription.cdts.EventInfo
import com.ericsson.oss.pmic.dto.subscription.cdts.StreamInfo
import com.ericsson.oss.pmic.dto.subscription.cdts.MoinstanceInfo
import com.ericsson.oss.pmic.dto.subscription.enums.*
import com.ericsson.oss.pmic.dto.subscription.enums.UserType
import com.ericsson.oss.services.pm.exception.SubscriptionNotFoundDataAccessException
import com.ericsson.oss.services.pm.initiation.cache.constants.CacheNamingConstants
import com.ericsson.oss.services.pm.services.exception.ConcurrentSubscriptionUpdateException
import com.ericsson.oss.services.pm.services.exception.InvalidSubscriptionOperationException
import com.ericsson.oss.services.pm.services.exception.ValidationException
import com.ericsson.oss.services.pm.time.TimeGenerator

class SubscriptionWriteOperationServiceSpec extends SkeletonSpec {

    static final UeInfo UE_INFO = new UeInfo(UeType.IMSI, "1234567")
    static final Integer RECORDING_REFERENCE = 30


    @Inject
    SubscriptionWriteOperationService subscriptionWriteOperationService

    @Inject
    SubscriptionReadOperationService subscriptionReadOperationService;

    @Inject
    private SubscriptionDao subscriptionDao

    @ImplementationInstance
    private TimeGenerator timeGenerator = Mock(TimeGenerator)

    @Inject
    @NamedCache(CacheNamingConstants.PMIC_REST_SUBSCRIPTION_CACHE_V2)
    private Cache<String, Map<String, Object>> subscriptionPersistenceTrackingStatusCache

    @Unroll
    def "generateTrackingId will throw Exception if validation is incorrect"() {
        when:
        subscriptionReadOperationService.generateTrackingId(status)
        then:
        thrown(IllegalArgumentException)
        where:
        status << [null,
                   new SubscriptionPersistenceTrackingStatus(null, 1L, null, "abc"),
                   new SubscriptionPersistenceTrackingStatus(null, null, UPDATING, "abc"),
                   new SubscriptionPersistenceTrackingStatus(null, null, DELETING, "abc"),
                   new SubscriptionPersistenceTrackingStatus(null, null, ERROR, null),
                   new SubscriptionPersistenceTrackingStatus(null, 1L, ERROR, null),
                   new SubscriptionPersistenceTrackingStatus(null, null, ERROR, "")]
    }

    def "create sub will fail is sub or tracker is null"() {
        when:
        subscriptionWriteOperationService.saveOrUpdate(sub, tracker)
        then:
        thrown(IllegalArgumentException)
        where:
        sub << [null, new StatisticalSubscription()]
        tracker << ["12321", null]
    }

    @Unroll
    def "generateTrackingId will create tracking id in cache successfully for subscriptionId #subId state #state and error message #error"() {
        given: "status"
        String errorMessage = error
        Long id = subId as Long
        PersistenceTrackingState persistenceTrackingState = state as PersistenceTrackingState
        PersistenceTrackingStatus status = new SubscriptionPersistenceTrackingStatus(null, id, persistenceTrackingState, errorMessage)
        when: "generate trackingId"
        String result = subscriptionReadOperationService.generateTrackingId(status)
        then: "a not null trackingId is generated"
        result != null
        and: "caches are updated"
        subscriptionPersistenceTrackingStatusCache.get(result).get(SubscriptionPersistenceTrackingStatus.SubscriptionPersistenceTrackingAttribute.errorMessage.name()) == errorMessage
        subscriptionPersistenceTrackingStatusCache.get(result).get(SubscriptionPersistenceTrackingStatus.SubscriptionPersistenceTrackingAttribute.status.name() as String) == persistenceTrackingState.toString()
        subscriptionPersistenceTrackingStatusCache.get(result).get(SubscriptionPersistenceTrackingStatus.SubscriptionPersistenceTrackingAttribute.subscriptionId.name()) as Long == id as Long
        where:
        error << [null, "This should not be here but is allowed", null, "a", null, "a", null, "a", "a valid errror message", "a valid errror message", null, "a", null, "a"]
        subId << [null, 123L, 123L, null, 123L, 123L, 123L, 123L, null, 123L, null, null, 1L, 1L]
        state << [PERSISTING, PERSISTING, PERSISTING, PERSISTING, UPDATING, UPDATING, DELETING, DELETING, ERROR, ERROR, ABORT, ABORT, ABORT, ABORT]
    }

    @Unroll
    def "getTrackingStatus will return correct tracking status from #whichCache"() {
        given: "trackerId in cache"
        String trackerId = UUID.randomUUID().toString()
        subscriptionPersistenceTrackingStatusCache.put(trackerId, [status: ERROR.name(), errorMessage: "Some ERROR", subscriptionId: "123"])
        when: "trackerId is read from cache"
        PersistenceTrackingStatus result = subscriptionReadOperationService.getTrackingStatus(trackerId)
        then: "right status is returned"
        result.getErrorMessage() == "Some ERROR"
        result.getPersistedObjectId() == 123L
        result.getState() == ERROR
        where:
        whichCache << ["cache"]
    }

    def "getTrackingStatus will return null if no tracker exists"() {
        given:
        String trackerId = UUID.randomUUID().toString()
        when:
        PersistenceTrackingStatus result = subscriptionReadOperationService.getTrackingStatus(trackerId)
        then:
        result == null
    }

    @Unroll
    def "getTrackingStatus will return only attributes that were set in #whichCache"() {
        given: "trackerId in cache"
        String trackerId = UUID.randomUUID().toString()
        subscriptionPersistenceTrackingStatusCache.put(trackerId, [status: PERSISTING.name()])
        when: "trackerId is read from cache"
        PersistenceTrackingStatus result = subscriptionReadOperationService.getTrackingStatus(trackerId)
        then: "right attributes are returned"
        result.getErrorMessage() == null
        result.getPersistedObjectId() == null
        result.getState() == PERSISTING
        where:
        whichCache << ["cache"]
    }

    def "getTrackingStatus will throw IllegalArgumentException if tracker is null or empty"() {
        when:
        subscriptionReadOperationService.getTrackingStatus(tracker)
        then:
        thrown(IllegalArgumentException)
        where:
        tracker << ["", null]
    }

    @Unroll
    def "When update is called on an #administrationState #subscriptionType subscription it should change the subscription values"() {

        given: "A subscription exists in DPS"
        SubscriptionBuilder subscriptionBuilder = builder.newInstance(dpsUtils)
        ManagedObject subscriptionMO = subscriptionBuilder.name("subscription").administrativeState(administrationState as AdministrationState)
                .operationalState(OperationalState.RUNNING).taskStatus(TaskStatus.OK).build()
        and: "Subscription has been updated locally"
        Subscription subscription = subscriptionDao.findOneById(subscriptionMO.getPoId(), true)
        subscription.setRop(FIFTEEN_MIN)

        when: "The updated subscription is persisted"
        subscriptionWriteOperationService.saveOrUpdate(subscription)

        then: "The Subscription values in dps are updated"
        liveBucket().findPoById(1).getAttribute('rop') == FIFTEEN_MIN.toString()

        where:
        builder                              | administrationState          | subscriptionType
        CellTraceSubscriptionBuilder.class   | AdministrationState.ACTIVE   | 'CellTrace'
        CellTraceSubscriptionBuilder.class   | AdministrationState.INACTIVE | 'CellTrace'
        StatisticalSubscriptionBuilder.class | AdministrationState.ACTIVE   | 'Statistical'
        StatisticalSubscriptionBuilder.class | AdministrationState.INACTIVE | 'Statistical'
        UeTraceSubscriptionBuilder.class     | AdministrationState.ACTIVE   | 'UeTrace'
        UeTraceSubscriptionBuilder.class     | AdministrationState.INACTIVE | 'UeTrace'
        CctrSubscriptionBuilder.class        | AdministrationState.ACTIVE   | 'ContinuousCellTrace'
        CctrSubscriptionBuilder.class        | AdministrationState.INACTIVE | 'ContinuousCellTrace'
        EbmSubscriptionBuilder.class         | AdministrationState.ACTIVE   | 'Ebm'
        EbmSubscriptionBuilder.class         | AdministrationState.INACTIVE | 'Ebm'
        CtumSubscriptionBuilder.class        | AdministrationState.ACTIVE   | 'Ctum'
        CtumSubscriptionBuilder.class        | AdministrationState.INACTIVE | 'Ctum'
        UetrSubscriptionBuilder.class        | AdministrationState.ACTIVE   | 'Uetr'
        UetrSubscriptionBuilder.class        | AdministrationState.INACTIVE | 'Uetr'

    }

    @Unroll
    def "When update is called with no #subscriptionType Subscription in dps, exception should be thrown"() {

        given: "A subscription object is created"
        Subscription subscription = createSubscription(subscriptionType, null)

        when: "The subscription is persisted as an update"
        subscriptionWriteOperationService.saveOrUpdate(subscription, "")

        then: "An exception is thrown as the subscription doesn't exist in dps"
        thrown(SubscriptionNotFoundDataAccessException)

        where:
        subscriptionType << SubscriptionType.values().findAll { (it != SubscriptionType.EVENTS) }
    }

    @Unroll
    def "When update is called on an #administrationState #subscriptionType subscription, it should thrown an exception"() {

        given: "A subscription exists in DPS with a non static state"
        SubscriptionBuilder subscriptionBuilder = builder.newInstance(dpsUtils)
        ManagedObject subscriptionMO = subscriptionBuilder.name("subscription").administrativeState(administrationState as AdministrationState)
                .operationalState(OperationalState.RUNNING).taskStatus(TaskStatus.OK).build()
        Subscription subscription = subscriptionDao.findOneById(subscriptionMO.poId, true)

        when: "Update is called on subscription"
        subscriptionWriteOperationService.saveOrUpdate(subscription, "")

        then: "An Exception is thrown as you cant update an activating or deactivating subscription"
        thrown(InvalidSubscriptionOperationException)

        where:
        builder                              | administrationState              | subscriptionType
        CellTraceSubscriptionBuilder.class   | AdministrationState.ACTIVATING   | 'CellTrace'
        CellTraceSubscriptionBuilder.class   | AdministrationState.DEACTIVATING | 'CellTrace'
        StatisticalSubscriptionBuilder.class | AdministrationState.ACTIVATING   | 'Statistical'
        StatisticalSubscriptionBuilder.class | AdministrationState.DEACTIVATING | 'Statistical'
        UeTraceSubscriptionBuilder.class     | AdministrationState.ACTIVATING   | 'UeTrace'
        UeTraceSubscriptionBuilder.class     | AdministrationState.DEACTIVATING | 'UeTrace'
        CctrSubscriptionBuilder.class        | AdministrationState.ACTIVATING   | 'ContinuousCellTrace'
        CctrSubscriptionBuilder.class        | AdministrationState.DEACTIVATING | 'ContinuousCellTrace'
        EbmSubscriptionBuilder.class         | AdministrationState.ACTIVATING   | 'Ebm'
        EbmSubscriptionBuilder.class         | AdministrationState.DEACTIVATING | 'Ebm'
        CtumSubscriptionBuilder.class        | AdministrationState.ACTIVATING   | 'Ctum'
        CtumSubscriptionBuilder.class        | AdministrationState.DEACTIVATING | 'Ctum'
    }

    def "activate will fail if persistence time is not the same"() {
        given:
        def subMO = statisticalSubscriptionBuilder.build()
        def sub = subscriptionDao.findOneById(subMO.getPoId(), true)
        when:
        subscriptionWriteOperationService.activate(sub, new Date(123L))
        then:
        thrown(ConcurrentSubscriptionUpdateException)
    }

    def "deactivate will fail if persistence time is not the same"() {
        given:
        def subMO = statisticalSubscriptionBuilder.build()
        def sub = subscriptionDao.findOneById(subMO.getPoId(), true)
        when:
        subscriptionWriteOperationService.deactivate(sub, new Date(123L))
        then:
        thrown(ConcurrentSubscriptionUpdateException)
    }

    def "activate will fail if resource subscription has no nodes"() {
        given:
        def subMO = statisticalSubscriptionBuilder.persistenceTime(new Date(123L)).administrativeState(AdministrationState.INACTIVE).build()
        def sub = subscriptionDao.findOneById(subMO.getPoId(), false)
        timeGenerator.currentTimeMillis() >> 123L
        when:
        subscriptionWriteOperationService.activate(sub, new Date(123L))
        then:
        thrown(ValidationException)
    }

    @Unroll
    def "activate will fail if administrationState is not INACTIVE"() {
        given:
        def nodeMO = nodeUtil.builder("1").build()
        def subMO = statisticalSubscriptionBuilder
                .persistenceTime(new Date(123L))
                .administrativeState(state)
                .nodes(nodeMO)
                .build()
        def sub = subscriptionDao.findOneById(subMO.getPoId(), true)
        timeGenerator.currentTimeMillis() >> 123L
        when:
        subscriptionWriteOperationService.activate(sub, new Date(123L))
        then:
        thrown(ValidationException)
        where:
        state << [AdministrationState.SCHEDULED, AdministrationState.ACTIVATING, AdministrationState.DEACTIVATING, AdministrationState.UPDATING]
    }

    @Unroll
    def "deactivate will fail if administrationState is #state"() {
        given:
        def nodeMO = nodeUtil.builder("1").build()
        def subMO = statisticalSubscriptionBuilder
                .persistenceTime(new Date(123L))
                .administrativeState(state)
                .nodes(nodeMO)
                .build()
        def sub = subscriptionDao.findOneById(subMO.getPoId(), true)
        timeGenerator.currentTimeMillis() >> 123L
        when:
        subscriptionWriteOperationService.deactivate(sub, new Date(123L))
        then:
        thrown(InvalidSubscriptionOperationException)
        where:
        state << [AdministrationState.INACTIVE, AdministrationState.ACTIVATING, AdministrationState.DEACTIVATING, AdministrationState.UPDATING]
    }

    def "activate will fail if celltraffic subscription has no triggerEvents"() {
        given:
        def nodeMO = nodeUtil.builder("1").build()
        def subMO = cellTrafficSubscriptionBuilder
                .persistenceTime(new Date(123L))
                .administrativeState(AdministrationState.INACTIVE)
                .nodes(nodeMO)
                .build()
        def sub = subscriptionDao.findOneById(subMO.getPoId(), true)
        timeGenerator.currentTimeMillis() >> 123L
        when:
        subscriptionWriteOperationService.activate(sub, new Date(123L))
        then:
        thrown(ValidationException)
    }

    @Unroll
    def "activate will fail for EBM subscription if an #state ebm subscription already exists"() {
        given:
        def nodeMO = nodeUtil.builder("1").build()
        ebmSubscriptionBuilder.administrativeState(state).name("a").build()
        def subMO = ebmSubscriptionBuilder
                .persistenceTime(new Date(123L))
                .administrativeState(AdministrationState.INACTIVE)
                .nodes(nodeMO)
                .name("b")
                .build()
        def sub = subscriptionDao.findOneById(subMO.getPoId(), true)
        timeGenerator.currentTimeMillis() >> 123L
        when:
        subscriptionWriteOperationService.activate(sub, new Date(123L))
        then:
        thrown(ValidationException)
        where:
        state << [AdministrationState.ACTIVE, AdministrationState.ACTIVATING]
    }

    def "activate will fail if moInstance subscription doesn't have the correct moinstances"() {
        given:
        def nodeMO = nodeUtil.builder("1").build()
        def subMO = moinstanceSubscriptionBuilder
                .persistenceTime(new Date(123L))
                .administrativeState(AdministrationState.INACTIVE)
                .nodes(nodeMO)
                .build()
        def sub = subscriptionDao.findOneById(subMO.getPoId(), true)
        timeGenerator.currentTimeMillis() >> 123L
        when:
        subscriptionWriteOperationService.activate(sub, new Date(123L))
        then:
        thrown(ValidationException)
    }

    def "activate will fail if uetr subscription doesn't have the correct UeInfo"() {
        given:
        def nodeMO = nodeUtil.builder("1").build()
        def subMO = uetrSubscriptionBuilder
                .persistenceTime(new Date(123L))
                .administrativeState(AdministrationState.INACTIVE)
                .nodes(nodeMO)
                .build()
        def sub = subscriptionDao.findOneById(subMO.getPoId(), true)
        timeGenerator.currentTimeMillis() >> 123L
        when:
        subscriptionWriteOperationService.activate(sub, new Date(123L))
        then:
        thrown(ValidationException)
    }

    @Unroll
    def "activate will successfully activate #subType subscription when description #descriptionType"() {
        given:
        def subMO = createSub(subType, description)
        def sub = subscriptionDao.findOneById(subMO.getPoId(), true)
        timeGenerator.currentTimeMillis() >> 123L
        when:
        subscriptionWriteOperationService.activate(sub, new Date(123L))
        def sub1 = subscriptionDao.findOneById(subMO.getPoId(), true)

        then:
        sub.getAdministrationState() == AdministrationState.ACTIVATING
        sub.getTaskStatus() == TaskStatus.OK
        sub.getUserActivationDateTime().getTime() == new Date(123L).getTime()
        sub.getUserDeActivationDateTime() == null

        sub1.getAdministrationState() == AdministrationState.ACTIVATING
        sub1.getTaskStatus() == TaskStatus.OK
        sub1.getUserActivationDateTime().getTime() == new Date(123L).getTime()
        sub1.getUserDeActivationDateTime() == null
        where:
        subType                              | description  | descriptionType
        SubscriptionType.STATISTICAL         | null         | "null"
        SubscriptionType.STATISTICAL         | ""           | "empty"
        SubscriptionType.STATISTICAL         | "aaa \t\r\n" | "contains spaces, tabs, CR, new line"
        SubscriptionType.MOINSTANCE          | null         | "null"
        SubscriptionType.MOINSTANCE          | ""           | "empty"
        SubscriptionType.MOINSTANCE          | "aaa \t\r\n" | "contains spaces, tabs, CR, new line"
        SubscriptionType.CONTINUOUSCELLTRACE | null         | "null"
        SubscriptionType.CONTINUOUSCELLTRACE | ""           | "empty"
        SubscriptionType.CONTINUOUSCELLTRACE | "aaa \t\r\n" | "contains spaces, tabs, CR, new line"
        SubscriptionType.EBM                 | null         | "null"
        SubscriptionType.EBM                 | ""           | "empty"
        SubscriptionType.EBM                 | "aaa \t\r\n" | "contains spaces, tabs, CR, new line"
        SubscriptionType.UETRACE             | "not blank"  | "not blank"
        SubscriptionType.UETR                | "not blank"  | "not blank"
        SubscriptionType.CELLTRAFFIC         | "not blank"  | "not blank"
        SubscriptionType.GPEH                | "not blank"  | "not blank"
        SubscriptionType.CTUM                | "not blank"  | "not blank"
        SubscriptionType.BSCRECORDINGS       | "not blank"  | "not blank"
        SubscriptionType.MTR                 | "not blank"  | "not blank"
        SubscriptionType.CELLTRACE           | "not blank"  | "not blank"
        SubscriptionType.CONTINUOUSCELLTRACE | "not blank"  | "not blank"
    }

    @Unroll
    def "activate #subType subscription will fail when description #descriptionType"() {
        given:
        def subMO = createSub(subType, description)
        def sub = subscriptionDao.findOneById(subMO.getPoId(), true)
        timeGenerator.currentTimeMillis() >> 123L
        when:
        subscriptionWriteOperationService.activate(sub, new Date(123L))

        then:
        thrown(ValidationException)
        where:
        subType                        | description | descriptionType
        SubscriptionType.UETRACE       | null        | "null"
        SubscriptionType.UETRACE       | ""          | "empty"
        SubscriptionType.UETRACE       | " "         | "contains only spaces"
        SubscriptionType.UETRACE       | "\t"        | "contains only tabs"
        SubscriptionType.UETRACE       | "\r"        | "contains only CR"
        SubscriptionType.UETRACE       | "\n"        | "contains only new line"
        SubscriptionType.UETR          | null        | "null"
        SubscriptionType.UETR          | ""          | "empty"
        SubscriptionType.UETR          | " "         | "contains only spaces"
        SubscriptionType.UETR          | "\t"        | "contains only tabs"
        SubscriptionType.UETR          | "\r"        | "contains only CR"
        SubscriptionType.UETR          | "\n"        | "contains only new line"
        SubscriptionType.CELLTRAFFIC   | null        | "null"
        SubscriptionType.CELLTRAFFIC   | ""          | "empty"
        SubscriptionType.CELLTRAFFIC   | " "         | "contains only spaces"
        SubscriptionType.CELLTRAFFIC   | "\t"        | "contains only tabs"
        SubscriptionType.CELLTRAFFIC   | "\r"        | "contains only CR"
        SubscriptionType.CELLTRAFFIC   | "\n"        | "contains only new line"
        SubscriptionType.GPEH          | null        | "null"
        SubscriptionType.GPEH          | ""          | "empty"
        SubscriptionType.GPEH          | " "         | "contains only spaces"
        SubscriptionType.GPEH          | "\t"        | "contains only tabs"
        SubscriptionType.GPEH          | "\r"        | "contains only CR"
        SubscriptionType.GPEH          | "\n"        | "contains only new line"
        SubscriptionType.BSCRECORDINGS | null        | "null"
        SubscriptionType.BSCRECORDINGS | ""          | "empty"
        SubscriptionType.BSCRECORDINGS | " "         | "contains only spaces"
        SubscriptionType.BSCRECORDINGS | "\t"        | "contains only tabs"
        SubscriptionType.BSCRECORDINGS | "\r"        | "contains only CR"
        SubscriptionType.BSCRECORDINGS | "\n"        | "contains only new line"
        SubscriptionType.MTR           | null        | "null"
        SubscriptionType.MTR           | ""          | "empty"
        SubscriptionType.MTR           | " "         | "contains only spaces"
        SubscriptionType.MTR           | "\t"        | "contains only tabs"
        SubscriptionType.MTR           | "\r"        | "contains only CR"
        SubscriptionType.MTR           | "\n"        | "contains only new line"
        SubscriptionType.RTT           | null        | "null"
        SubscriptionType.RTT           | ""          | "empty"
        SubscriptionType.RTT           | " "         | "contains only spaces"
        SubscriptionType.RTT           | "\t"        | "contains only tabs"
        SubscriptionType.RTT           | "\r"        | "contains only CR"
        SubscriptionType.RTT           | "\n"        | "contains only new line"
        SubscriptionType.CELLTRACE     | null        | "null"
        SubscriptionType.CELLTRACE     | ""          | "empty"
        SubscriptionType.CELLTRACE     | " "         | "contains only spaces"
        SubscriptionType.CELLTRACE     | "\t"        | "contains only tabs"
        SubscriptionType.CELLTRACE     | "\r"        | "contains only CR"
        SubscriptionType.CELLTRACE     | "\n"        | "contains only new line"
    }

    @Unroll
    def "deactivate will successfully deactivate #builder"() {
        given:
        def nodeMO = nodeUtil.builder("1").build()
        def subMO = builder.newInstance(dpsUtils)
                .persistenceTime(new Date(123L))
                .administrativeState(AdministrationState.ACTIVE)
                .nodes(nodeMO)
                .build()
        def sub = subscriptionDao.findOneById(subMO.getPoId(), true)
        timeGenerator.currentTimeMillis() >> 123L
        when:
        subscriptionWriteOperationService.deactivate(sub, new Date(123L))
        def sub1 = subscriptionDao.findOneById(subMO.getPoId(), true)

        then:
        sub.getAdministrationState() == AdministrationState.DEACTIVATING
        sub.getTaskStatus() == TaskStatus.OK
        sub.getUserDeActivationDateTime().getTime() == new Date(123L).getTime()

        sub1.getAdministrationState() == AdministrationState.DEACTIVATING
        sub1.getTaskStatus() == TaskStatus.OK
        sub1.getUserDeActivationDateTime().getTime() == new Date(123L).getTime()
        where:
        builder << [UetrSubscriptionBuilder,
                    StatisticalSubscriptionBuilder,
                    MoinstanceSubscriptionBuilder,
                    CellTraceSubscriptionBuilder,
                    CctrSubscriptionBuilder,
                    CellTrafficSubscriptionBuilder,
                    GpehSubscriptionBuilder,
                    UeTraceSubscriptionBuilder,
                    EbmSubscriptionBuilder,
                    CtumSubscriptionBuilder]
    }

    def createSubscription(SubscriptionType type, Date persistenceTime) {
        Subscription subscription = typeToSubscriptionInstance.get(type)
        if (subscription == null)
            subscription = new UETraceSubscription()
        subscription.setName('Subscription')
        subscription.setType(type)
        subscription.setIdAsString("1")
        subscription.setAdministrationState(AdministrationState.ACTIVE)
        subscription.setRop(FIFTEEN_MIN)
        subscription.setPersistenceTime(persistenceTime)
        subscription.setOperationalState(OperationalState.RUNNING)
        subscription.setTaskStatus(TaskStatus.OK)
        return subscription
    }

    def createSub(SubscriptionType type, String description) {
        def nodeMO = nodeUtil.builder("1").build()
        def subBuilder = dps.subscription().type(type)
                .persistenceTime(new Date(123L))
                .administrationState(AdministrationState.INACTIVE)
                .nodes(nodeMO)
                .counters(new CounterInfo("a", "b"))
                .events(new EventInfo("a", "b"))
                .ueInfo(UE_INFO)
                .ueInfoList(UE_INFO)
                .attributes([
                (cellInfoList.name())    : [[(nodeName.name())   : "1",
                                             (utranCellId.name()): "123"]],
                (triggerEventInfo.name()): [(groupName.name()): "abc",
                                            (name.name())     : "abc"],
                (moInstances.name())     : [[(MoinstanceInfo.MoinstanceInfo100Attribute.nodeName.name()): "RNS06",
                                             (moInstanceName.name())                                    : "Aal2PathVccTp=b1a3"]],
                "mtrAccessTypes"         : [MtrAccessType.LCS.name()],
                "recordingReference"     : RECORDING_REFERENCE
        ])
        if(isSystemDefSub(type)) {
            subBuilder = subBuilder.userType(UserType.SYSTEM_DEF)
        }
        if (description != null) {
            subBuilder = subBuilder.description(description)
        }
        return subBuilder.build()
    }

    def isSystemDefSub(SubscriptionType type) {
        return type == SubscriptionType.CTUM || type == SubscriptionType.CONTINUOUSCELLTRACE
    }
    @Unroll
    def "activate will successfully activate #subsriptionType subscription with #modelName"() {
        given:
        def nodeMO1 = nodeUtil.builder("GSM02BSC01").neType(NetworkElementType.BSC).build()
        def nodeMO2 = nodeUtil.builder("GSM02BSC02").neType(NetworkElementType.BSC).build()
        def cells = [new CellInfo("GSM02BSC01", "1730270")];
        def events = [new EventInfo("ALL", "ALL")]
        def streamingData = [ new StreamInfo("10.101.101.10",22)]
        def ueInfoList = [new UeInfo(UeType.IMEI, "12345678901234"), new UeInfo(UeType.IMSI, "123456789012345") ]
        def subMo = dps.subscription().type(SubscriptionType.valueOf(subsriptionType)).name("subNameNe").administrationState(AdministrationState.INACTIVE).nodeListIdentity(2).nodes     (nodeMO1, nodeMO2).build()
        def sub;
        if(SubscriptionType.RPMO == SubscriptionType.valueOf(subsriptionType)){
            sub = subscriptionDao.findOneById(subMo.getPoId(), true) as RpmoSubscription
            sub.setCells(cells)

        }else{
            sub = subscriptionDao.findOneById(subMo.getPoId(), true) as RttSubscription
            sub.setUeInfoList(ueInfoList)
        }
        sub.setDescription(description)
        sub.setEvents(events)
        sub.setStreamInfoList(streamingData)
        sub.setPersistenceTime(new Date(123L))


        timeGenerator.currentTimeMillis() >> 123L
        when:
        subscriptionWriteOperationService.activate(sub, new Date(123L))
        then:
        sub.getAdministrationState() == AdministrationState.ACTIVATING
        sub.getTaskStatus() == TaskStatus.OK
        sub.getUserActivationDateTime().getTime() == new Date(123L).getTime()
        sub.getUserDeActivationDateTime() == null
        where :
        subsriptionType | description
        "RPMO"          | "null"
        "RTT"           | "trace description"
    }
    static def typeToSubscriptionInstance = [(SubscriptionType.STATISTICAL)        : new StatisticalSubscription(),
                                             (SubscriptionType.CELLTRACE)          : new CellTraceSubscription(),
                                             (SubscriptionType.EBM)                : new EbmSubscription(),
                                             (SubscriptionType.EBS)                : new EbmSubscription(),
                                             (SubscriptionType.CTUM)               : new CtumSubscription(),
                                             (SubscriptionType.CONTINUOUSCELLTRACE): new ContinuousCellTraceSubscription(),
                                             (SubscriptionType.UETR)               : new UetrSubscription(),
                                             (SubscriptionType.UETRACE)            : new UETraceSubscription()]
}
