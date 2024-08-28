/*
 * ------------------------------------------------------------------------------
 *  ********************************************************************************
 *  * COPYRIGHT Ericsson  2017
 *  *
 *  * The copyright to the computer program(s) herein is the property of
 *  * Ericsson Inc. The programs may be used and/or copied only with written
 *  * permission from Ericsson Inc. or in accordance with the terms and
 *  * conditions stipulated in the agreement/contract under which the
 *  * program(s) have been supplied.
 *  *******************************************************************************
 *  *----------------------------------------------------------------------------
 */

package com.ericsson.oss.services.pm.initiation.enodeb.subscription.resource

import spock.lang.Unroll

import javax.ws.rs.core.Response

import com.ericsson.cds.cdi.support.configuration.InjectionProperties
import com.ericsson.cds.cdi.support.rule.ObjectUnderTest
import com.ericsson.oss.pmic.cdi.test.util.SkeletonSpec
import com.ericsson.oss.services.pm.initiation.common.ResponseData

class SubscriptionConfigResourceSpec extends SkeletonSpec {

    @ObjectUnderTest
    SubscriptionConfigResource configResource
    static final String cbsEnabled = 'cbsEnabled'
    static final String maxNoOfCbsAllowed = 'maxNoOfCbsAllowed'
    static final String pmicEbsmEnabled = 'pmicEbsmEnabled'
    static final String pmicEbsStreamClusterDeployed = 'pmicEbsStreamClusterDeployed'
    static final String pmicEbslRopInMinutes = 'pmicEbslRopInMinutes'

    @Override
    def addAdditionalInjectionProperties(InjectionProperties injectionProperties) {
        super.addAdditionalInjectionProperties(injectionProperties)
        injectionProperties.autoLocateFrom('com.ericsson.oss.services.pm.initiation')
    }

    @Unroll
    def "when valid #pibParam pibParameter is passed to config resource should return correct value"() {

        when: "pibConfigParam request is sent"
        Response response = configResource.getPIBConfigParamInfo(pibParam)
        String configParamterValue = response.getEntity()

        then: "returned targetTypes should be as set in configured.properties"
        configParamterValue == value.toString()

        where:
        pibParam                     | value
        cbsEnabled                   | true
        maxNoOfCbsAllowed            | 2
        pmicEbsmEnabled              | true
        pmicEbsStreamClusterDeployed | false
        pmicEbslRopInMinutes         | 15
    }

    def "when invalid pibParameter name is passed to config resource, should throw exception"() {
        when: "pibConfigParam request is sent with invalid parameter name"
        Response response = configResource.getPIBConfigParamInfo('test')
        ResponseData data = response.getEntity()

        then: "error response is returned"
        data.getError() == "PMIC Configuration parameter with name 'test' does not  exists.".toString()
        data.getCode() == Response.Status.BAD_REQUEST
    }

}
