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

package com.ericsson.oss.services.pm.dps.utility

import static com.ericsson.oss.pmic.dto.subscription.enums.CellTraceCategory.*

import spock.lang.Unroll

import javax.inject.Inject

import com.ericsson.cds.cdi.support.configuration.InjectionProperties
import com.ericsson.cds.cdi.support.providers.custom.model.ModelPattern
import com.ericsson.cds.cdi.support.providers.custom.model.RealModelServiceProvider
import com.ericsson.cds.cdi.support.rule.ObjectUnderTest
import com.ericsson.oss.pmic.cdi.test.util.SkeletonSpec
import com.ericsson.oss.pmic.dao.NodeDao
import com.ericsson.oss.pmic.dao.SubscriptionDao
import com.ericsson.oss.pmic.dto.subscription.CellTraceSubscription
import com.ericsson.oss.pmic.dto.subscription.StatisticalSubscription
import com.ericsson.oss.pmic.dto.subscription.enums.AdministrationState
import com.ericsson.oss.pmic.dto.subscription.enums.OperationalState
import com.ericsson.oss.pmic.dto.subscription.enums.SubscriptionType
import com.ericsson.oss.pmic.dto.subscription.enums.TaskStatus
import com.ericsson.oss.services.pm.common.constants.PmFeature
import com.ericsson.oss.services.pm.modelservice.PmCapabilityModelService
import com.ericsson.oss.services.pm.services.generic.SubscriptionWriteOperationService

class UpdateResourceSubscriptionScenarioSpec extends SkeletonSpec {

    @ObjectUnderTest
    UpdateResourceSubscription updateResourceSubscription

    @Inject
    NodeDao nodeDao

    @Inject
    private SubscriptionWriteOperationService subscriptionWriteOperationService;

    @Inject
    SubscriptionDao subscriptionDao

    @Inject
    PmCapabilityModelService capabilityAccessor

    private static requiredModels = [new ModelPattern('.*', 'pmic.*', '.*', '.*'),
                                     new ModelPattern('.*', '.*', 'SystemDefinedSubscriptionAttributes', '.*'),
                                     new ModelPattern('.*', '.*', 'PMICFunctions', '.*'),
                                     new ModelPattern('.*', '.*', 'STATISTICAL_SubscriptionAttributes', '.*'),
                                     new ModelPattern('.*', '.*', 'STATISTICAL_SystemDefinedSubscriptionAttributes', '.*'),
                                     new ModelPattern('.*', '.*', 'CELLTRACE_SubscriptionAttributes', '.*'),
                                     new ModelPattern('.*', '.*', 'CELLTRACENRAN_SubscriptionAttributes', '.*')]

    private static RealModelServiceProvider realModelServiceProvider = new RealModelServiceProvider(requiredModels)

    @Override
    def addAdditionalInjectionProperties(InjectionProperties injectionProperties) {
        injectionProperties.addInjectionProvider(realModelServiceProvider)
        autoAllocateFrom().each { injectionProperties.autoLocateFrom(it) }
    }

    @Unroll
    def 'A Criteria Based Subscription audit is executed for Statistical subscription, #neType can be added to STATISTICAL subscription'() {

        given: 'a Statistical Criteria Based Subscription'
        def criteriaBasedSubscriptionManagedObject = dps.subscription().type(SubscriptionType.STATISTICAL).name('subscription')
                .administrationState(AdministrationState.INACTIVE)
                .operationalState(OperationalState.RUNNING)
                .taskStatus(TaskStatus.OK)
                .build()
        def criteriaBasedSubscriptionDto = subscriptionDao
                .findOneById(criteriaBasedSubscriptionManagedObject.getPoId(), true) as StatisticalSubscription
        criteriaBasedSubscriptionDto.setNodeListIdentity(1)

        and: 'a node of type #neType'
        createNodeInDps(neType, 'DUMMY_TECHNOLOGY_DOMAIN')

        when: 'the subscription update is invoked'
        updateResourceSubscription.updateResourceSubscription(criteriaBasedSubscriptionDto, nodeDao.findAll())

        then: 'the node #nodeName can be added to the STATISTICAL subscription'
        criteriaBasedSubscriptionManagedObject.getAssociations('nodes').size() == 1

        where:
        neType << ['RadioNode', 'ERBS', 'MSRBS_V1', '5GRadioNode', 'VTFRadioNode', 'SGSNMME']
    }

    @Unroll
    def 'A Criteria Based Subscription audit is executed for CellTrace subscription, node that does not support CELLTRACE cannot be added to CellTrace subscription'() {

        given: 'a CellTrace Criteria Based Subscription'
        capabilityAccessor.getSupportedNodeTypesForPmFeatureCapability(PmFeature.CELLTRACE_FILE_COLLECTION) >> Collections.emptyList()
        def criteriaBasedSubscriptionManagedObject = dps.subscription().type(SubscriptionType.CELLTRACE).name('subscription')
                .administrationState(AdministrationState.INACTIVE)
                .operationalState(OperationalState.RUNNING)
                .nodeListIdentity(1)
                .taskStatus(TaskStatus.OK)
                .build()
        def criteriaBasedSubscriptionDto = subscriptionDao.findOneById(criteriaBasedSubscriptionManagedObject.getPoId(), true) as CellTraceSubscription
        criteriaBasedSubscriptionDto.setNodeListIdentity(1)

        and: 'a node of type #neType'
        createNodeInDps(neType, 'DUMMY_TECHNOLOGY_DOMAIN')

        when: 'the subscription update is invoked'
        updateResourceSubscription.updateResourceSubscription(criteriaBasedSubscriptionDto, nodeDao.findAll())

        then: 'the subscription update will not be called when node does not support CELLTRACE'
        0 * subscriptionWriteOperationService.manageSaveOrUpdate(criteriaBasedSubscriptionDto)

        then: 'the node #nodeName cannot be added to the CELLTRACE subscription'
        criteriaBasedSubscriptionManagedObject.getAssociations('nodes').size() == 0

        where:
        neType << ['RadioNode', 'ERBS', 'MSRBS_V1', '5GRadioNode', 'VTFRadioNode', 'SGSNMME']
    }

    @Unroll
    def 'A Criteria Based Subscription audit is executed for CellTrace subscription, #neType with #nodeTechnologyDomains #description to #celltraceCategory subscription'() {

        given: 'a celltrace Criteria Based Subscription of category #celltraceCategory'
        def criteriaBasedSubscriptionManagedObject = dps.subscription().
                type(SubscriptionType.CELLTRACE).
                name('CellTrace_Test_#celltraceCategory').
                administrationState(AdministrationState.ACTIVE).
                cellTraceCategory(celltraceCategory).
                taskStatus(TaskStatus.OK).
                build()
        def criteriaBasedSubscriptionDto = subscriptionDao.findOneById(criteriaBasedSubscriptionManagedObject.getPoId(), true) as CellTraceSubscription
        criteriaBasedSubscriptionDto.setNodeListIdentity(1)

        and: 'a node of type #neType with the technologyDomains #nodeTechnologyDomains'
        createNodeInDps(neType, nodeTechnologyDomains)

        when: 'subscription update is invoked'
        updateResourceSubscription.updateResourceSubscription(criteriaBasedSubscriptionDto, nodeDao.findAll())

        then: 'the node #nodeName #description to the #celltraceCategory subscription'
        criteriaBasedSubscriptionManagedObject.getAssociations('nodes').size() == (canBeAdded ? 1 : 0)

        where:
        neType         | nodeTechnologyDomains | celltraceCategory            || canBeAdded | description
        'RadioNode'    | []                    | CELLTRACE                    || false      | 'cannot be added'
        'RadioNode'    | ['EPS']               | CELLTRACE                    || true       | 'can be added'
        'RadioNode'    | ['EPS']               | CELLTRACE_AND_EBSL_FILE      || true       | 'can be added'
        'RadioNode'    | ['EPS']               | CELLTRACE_AND_EBSL_STREAM    || true       | 'can be added'
        'RadioNode'    | ['EPS']               | CELLTRACE_NRAN               || false      | 'cannot be added'
        'RadioNode'    | ['5GS']               | CELLTRACE_NRAN               || true       | 'can be added'
        'RadioNode'    | ['5GS']               | CELLTRACE_NRAN_AND_EBSN_FILE || true       | 'can be added'
        'RadioNode'    | ['5GS']               | CELLTRACE                    || false      | 'cannot be added'
        'RadioNode'    | ['5GS', 'EPS']        | CELLTRACE                    || true       | 'can be added'
        'RadioNode'    | ['5GS', 'EPS']        | CELLTRACE_NRAN               || true       | 'can be added'
        'RadioNode'    | ['UMTS']              | CELLTRACE                    || false      | 'cannot be added'
        'RadioNode'    | ['UMTS']              | CELLTRACE_NRAN               || false      | 'cannot be added'
        'RadioNode'    | ['EPS', 'UMTS']       | CELLTRACE                    || true       | 'can be added'
        'ERBS'         | ['EPS']               | CELLTRACE                    || true       | 'can be added'
        'ERBS'         | ['EPS']               | CELLTRACE_NRAN               || false      | 'cannot be added'
        'ERBS'         | ['EPS']               | CELLTRACE                    || true       | 'can be added'
        'ERBS'         | ['EPS']               | CELLTRACE_NRAN               || false      | 'cannot be added'
        'MSRBS_V1'     | ['EPS']               | CELLTRACE                    || true       | 'can be added'
        'MSRBS_V1'     | ['EPS']               | CELLTRACE_NRAN               || false      | 'cannot be added'
        'MSRBS_V1'     | ['UMTS']              | CELLTRACE                    || false      | 'cannot be added'
        'MSRBS_V1'     | ['UMTS']              | CELLTRACE_NRAN               || false      | 'cannot be added'
        'MSRBS_V1'     | ['EPS', 'UMTS']       | CELLTRACE                    || true       | 'can be added'
        'VTFRadioNode' | ['EPS']               | CELLTRACE                    || true       | 'can be added'
        'VTFRadioNode' | ['EPS']               | CELLTRACE_NRAN               || false      | 'cannot be added'
        '5GRadioNode'  | ['5GS']               | CELLTRACE_NRAN               || false      | 'cannot be added'
        'SGSNMME'      | ['EPS']               | CELLTRACE                    || false      | 'cannot be added'
    }

    def createNodeInDps(neType, technologyDomains) {
        def nodeName = "NodeFromCbsQuery_${neType}"
        def networkElementFdn = "NetworkElement=${nodeName}"
        nodeUtil.builder(nodeName).
                fdn(networkElementFdn).
                neType(neType).
                ctumEnabled(false).
                globalCtumEnabled(false).
                technologyDomain(technologyDomains as List<String>).
                build()
        return nodeName
    }
}
