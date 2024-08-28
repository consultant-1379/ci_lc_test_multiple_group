/*******************************************************************************
 * COPYRIGHT Ericsson 2019
 *
 * The copyright to the computer program(s) herein is the property of
 * Ericsson Inc. The programs may be used and/or copied only with written
 * permission from Ericsson Inc. or in accordance with the terms and
 * conditions stipulated in the agreement/contract under which the
 * program(s) have been supplied.
 ******************************************************************************/

package com.ericsson.oss.services.pm.common.capability

import static com.ericsson.oss.services.pm.common.constants.PmCapabilityModelConstants.SUPPORTED_TECHNOLOGY_DOMAINS

import com.ericsson.cds.cdi.support.rule.ObjectUnderTest
import com.ericsson.oss.services.pm.PmServiceEjbFullSpec
import com.ericsson.oss.services.pm.modelservice.PmCapabilityModelServiceImpl

import spock.lang.Unroll

class CellTraceSubscriptionsCapabilitiesSpec extends PmServiceEjbFullSpec {

    @ObjectUnderTest
    PmCapabilityModelServiceImpl pmCapabilityModelService

    @Unroll
    def 'Read supportedTechnologyDomains from #function function'() {
        when: 'the supported technology domains capability is read from #function'
            def pmGlobalCapabilities = pmCapabilityModelService
                    .getGlobalCapabilitiesByFunction(function, SUPPORTED_TECHNOLOGY_DOMAINS)

        then: 'the technology domains #expectedTechnologyDomains are returned for the #function capability model'
            def capabilitySupportedTechnologyDomains = pmGlobalCapabilities.globalCapabilities.get(SUPPORTED_TECHNOLOGY_DOMAINS)
            assert capabilitySupportedTechnologyDomains.containsAll(expectedTechnologyDomains)

        where: 'the subscription capability models are as follows'
            function                                                      || expectedTechnologyDomains
            'CELLTRACE_SubscriptionAttributes'                            || ['EPS']
            'CELLTRACENRAN_SubscriptionAttributes'                        || ['5GS']
            'CONTINUOUSCELLTRACE_SystemDefinedSubscriptionAttributes'     || ['EPS']
            'CONTINUOUSCELLTRACENRAN_SystemDefinedSubscriptionAttributes' || ['5GS']
    }
}
