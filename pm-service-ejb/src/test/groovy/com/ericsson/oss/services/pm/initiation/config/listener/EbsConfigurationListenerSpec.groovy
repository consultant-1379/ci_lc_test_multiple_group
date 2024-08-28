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

package com.ericsson.oss.services.pm.initiation.config.listener

import spock.lang.Unroll

import javax.inject.Inject

import com.ericsson.cds.cdi.support.configuration.InjectionProperties
import com.ericsson.cds.cdi.support.providers.custom.sfwk.PropertiesForTest
import com.ericsson.cds.cdi.support.providers.custom.sfwk.SuppliedProperty
import com.ericsson.cds.cdi.support.rule.ObjectUnderTest
import com.ericsson.oss.itpf.sdk.eventbus.model.EventSender
import com.ericsson.oss.itpf.sdk.eventbus.model.annotation.Modeled
import com.ericsson.oss.pmic.cdi.test.util.SkeletonSpec
import com.ericsson.oss.services.pm.ebs.utils.EbsConfigurationListener
import com.ericsson.oss.services.pm.initiation.events.PmicSubscriptionUpdate

class EbsConfigurationListenerSpec extends SkeletonSpec {
    private static final String pmicEbslRopInMinutes = "pmicEbslRopInMinutes"

    @Inject
    @Modeled
    EventSender<PmicSubscriptionUpdate> eventSender

    @ObjectUnderTest
    private EbsConfigurationListener ebsConfigurationListener

    @Override
    def addAdditionalInjectionProperties(InjectionProperties injectionProperties) {
        super.addAdditionalInjectionProperties(injectionProperties)
        injectionProperties.autoLocateFrom('com.ericsson.oss.services.pm.initiation');
    }

    @Unroll
    @PropertiesForTest(properties = [@SuppliedProperty(name = "pmicEbslRopInMinutes", value = "15")])
    def "when valid #pibParam pibParameter is passed to config resource with initial value 15 should return correct file interval value"() {

        given:
        ebsConfigurationListener.onPmicEbslRopInMinutesChanged(newValue)

        when: "pibConfigParam request is sent"
        String configParamterValue = ebsConfigurationListener.getPmicEbslRopInMinutes()

        then: "returned value should be as set in configured.properties"
        configParamterValue == value

        where:
        pibParam             | newValue || value
        pmicEbslRopInMinutes | "5"      || "5"
        pmicEbslRopInMinutes | "15"     || "15"
    }

    @Unroll
    @PropertiesForTest(properties = [@SuppliedProperty(name = "pmicEbslRopInMinutes", value = "5")])
    def "when valid #pibParam pibParameter is passed to config resource with initial value 5 then it should return correct file interval value"() {

        given:
        ebsConfigurationListener.onPmicEbslRopInMinutesChanged(newValue)

        when: "pibConfigParam request is sent"
        String configParamterValue = ebsConfigurationListener.getPmicEbslRopInMinutes()

        then: "returned value should be as set in configured.properties"
        configParamterValue == value

        where:
        pibParam             | newValue || value
        pmicEbslRopInMinutes | "5"      || "5"
        pmicEbslRopInMinutes | "15"     || "15"
    }

    @Unroll
    @PropertiesForTest(properties = [@SuppliedProperty(name = "pmicEbslRopInMinutes", value = "5")])
    def "When config resource pmicEbslRopInMinutes is updated, then should notify external consumer the old and new value of PIB parameter "() {

        when: "pibConfigParam request is sent"
        ebsConfigurationListener.onPmicEbslRopInMinutesChanged(newValue)

        then: "PmicSubscriptionUpdate Notification is sent to Jms"
        1 * eventSender.send({ PmicSubscriptionUpdate pmicSubscriptionUpdate ->
            pmicSubscriptionUpdate.getPmicSubscriptionChangedAttributeList()
                    .size() == 1
        })
        where:
        pibParam             | newValue || value
        pmicEbslRopInMinutes | "5"      || "5"
        pmicEbslRopInMinutes | "15"     || "15"

    }

    @Unroll
    def "When config resource pmicEbsStreamClusterDeployed #ebsClusterDeployed pmicAsrStreamClusterDeployed #asrClusterDeployed is updated, then it should return new value of PIB parameter "() {
        given:
        ebsConfigurationListener.onEbsStreamClusterDeployedChanged(ebsClusterDeployed)
        ebsConfigurationListener.onAsrStreamClusterDeployedChanged(asrClusterDeployed)
        when:
        boolean configParamValue = ebsConfigurationListener.isEbsOrAsrStreamClusterDeployed()
        then:
        configParamValue == value
        where:
        ebsClusterDeployed | asrClusterDeployed || value
        true               | true               || true
        true               | false              || true
        false              | true               || true
        false              | false              || false
    }
}
