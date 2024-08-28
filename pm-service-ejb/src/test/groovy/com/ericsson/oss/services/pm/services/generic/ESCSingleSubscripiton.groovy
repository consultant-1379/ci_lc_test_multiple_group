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
package com.ericsson.oss.services.pm.services.generic

import static com.ericsson.oss.pmic.dto.subscription.enums.RopPeriod.FIFTEEN_MIN

import javax.inject.Inject

import com.ericsson.cds.cdi.support.configuration.InjectionProperties
import com.ericsson.cds.cdi.support.providers.custom.model.ModelPattern
import com.ericsson.cds.cdi.support.providers.custom.model.RealModelServiceProvider
import com.ericsson.cds.cdi.support.rule.ImplementationClasses
import com.ericsson.oss.pmic.cdi.test.util.SkeletonSpec
import com.ericsson.oss.pmic.cdi.test.util.builder.subscription.StatisticalSubscriptionBuilder
import com.ericsson.oss.pmic.cdi.test.util.builder.subscription.SubscriptionBuilder
import com.ericsson.oss.pmic.dto.node.Node
import com.ericsson.oss.pmic.dto.subscription.StatisticalSubscription
import com.ericsson.oss.pmic.dto.subscription.Subscription
import com.ericsson.oss.pmic.dto.subscription.enums.*
import com.ericsson.oss.services.pm.initiation.validators.StatisticalSubscriptionParentValidator
import com.ericsson.oss.services.pm.modelservice.PmCapabilityModelServiceImpl
import com.ericsson.oss.services.pm.services.exception.ValidationException

class ESCSingleSubscripiton extends SkeletonSpec {
    static def filteredModels = [new ModelPattern('oss_capability', 'global', 'STATISTICAL_SubscriptionAttributes', '.*'),
                                 new ModelPattern('oss_capability', 'global', 'MTR_SubscriptionAttributes', '.*'),
                                 new ModelPattern('oss_capability', 'global', 'PMICFunctions', '.*'),
                                 new ModelPattern('oss_capability', 'global', 'BSCRECORDINGS_SubscriptionAttributes', '.*'),
                                 new ModelPattern('oss_targettype', 'NODE', '.*', '.*'),
                                 new ModelPattern('oss_capabilitysupport', 'BSC', 'PMICFunctions', '.*'),
                                 new ModelPattern('oss_capabilitysupport', 'MSC-BC-BSP', 'PMICFunctions', '.*'),
                                 new ModelPattern('oss_capabilitysupport', 'MSC-DB', 'PMICFunctions', '.*')]

    static RealModelServiceProvider realModelServiceProvider = new RealModelServiceProvider(filteredModels)

    @Override
    def addAdditionalInjectionProperties(InjectionProperties injectionProperties) {
        injectionProperties.addInjectionProvider(realModelServiceProvider)
        injectionProperties.autoLocateFrom('com.ericsson.oss.services.pm.initiation')
    }

    @ImplementationClasses
    def classes = [PmCapabilityModelServiceImpl.class]
    @Inject
    StatisticalSubscriptionParentValidator parentValidator

    def "Allowing only single subscription for ESC nodes "() {
        given: "Subscription has been updated locally"

        def nodeMO = nodeUtil.builder("CORE102ERS-SN-ESC01").build()
        SubscriptionBuilder subscriptionBuilder = StatisticalSubscriptionBuilder.class.newInstance(dpsUtils)
        subscriptionBuilder.name("subscription").administrativeState(administrationState as AdministrationState)
                .operationalState(OperationalState.RUNNING).taskStatus(TaskStatus.OK).nodes(nodeMO).build()
        Subscription subscription1 = createSubscription(SubscriptionType.STATISTICAL, new Date(123L))
        subscription1.setUserType(UserType.USER_DEF)
        def node = getESCNode('CORE102ERS-SN-ESC01')
        subscription1.setNodes([node])

        when:
        parentValidator.validateActivation(subscription1)

        then:
        def exception = thrown(ValidationException)
        exception.message.contains(message)

        where:

        builder                              | administrationState        | subscriptionType | message
        StatisticalSubscriptionBuilder.class | AdministrationState.ACTIVE | 'Statistical'    | "There can only be 1 active STATISTICAL Subscription in the network"
    }

    def createSubscription(SubscriptionType type, Date persistenceTime) {
        Subscription subscription = new StatisticalSubscription()
        subscription.setName('Subscription1')
        subscription.setType(type)
        subscription.setIdAsString("1")
        subscription.setAdministrationState(AdministrationState.INACTIVE)
        subscription.setRop(FIFTEEN_MIN)
        subscription.setPersistenceTime(persistenceTime)
        subscription.setOperationalState(OperationalState.RUNNING)
        subscription.setTaskStatus(TaskStatus.OK)
        return subscription
    }

    Node getESCNode(String nodeName) {
        def node = new Node()
        node.neType = 'ESC'
        node.fdn = 'NetworkElement=' + nodeName
        node.ossModelIdentity = '13A'
        node.name = nodeName
        node.id = 281474977608869L
        node.technologyDomain = ['EPS']
        node.pmFunction = true
        return node
    }
}