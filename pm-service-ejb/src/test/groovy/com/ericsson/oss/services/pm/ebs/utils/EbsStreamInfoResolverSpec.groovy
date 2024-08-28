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

package com.ericsson.oss.services.pm.ebs.utils

import static com.ericsson.oss.pmic.dto.subscription.enums.CellTraceCategory.ASR
import static com.ericsson.oss.pmic.dto.subscription.enums.CellTraceCategory.EBSL_STREAM
import static com.ericsson.oss.pmic.dto.subscription.enums.CellTraceCategory.ESN
import static com.ericsson.oss.pmic.dto.subscription.enums.CellTraceCategory.CELLTRACE_AND_EBSL_STREAM
import static com.ericsson.oss.pmic.dto.subscription.enums.CellTraceCategory.CELLTRACE_NRAN_AND_EBSN_STREAM
import static com.ericsson.oss.pmic.dto.subscription.enums.CellTraceCategory.NRAN_EBSN_STREAM

import spock.lang.Unroll

import com.ericsson.cds.cdi.support.rule.ObjectUnderTest
import com.ericsson.cds.cdi.support.spock.CdiSpecification
import com.ericsson.oss.pmic.dto.subscription.cdts.StreamInfo


class EbsStreamInfoResolverSpec extends CdiSpecification {

    @ObjectUnderTest
    private EbsStreamInfoResolver ebsStreamInfoResolver

    def 'EbsStreamInfoResolver return empty Stream Info global properties file is not found'() {
        given: 'the system properties file path points to no file'
            System.setProperty(EbsStreamInfoResolver.GLOBAL_PROP_ABSOLUTE_FILE_PATH, 'src/test/resources/wrong_path/global.properties')

        when: 'the streaming destination is fetched'
            def streamDestination = ebsStreamInfoResolver.getStreamingDestination()

        then: 'no stream infos are returned'
            streamDestination == []
    }

    @Unroll
    def 'EbsStreamInfoResolver return correct ipv6 and ipv4 destination from the global properties file'() {
        given:
            System.setProperty(EbsStreamInfoResolver.GLOBAL_PROP_ABSOLUTE_FILE_PATH, filepath)

        when:
            def streamDestination = ebsStreamInfoResolver.getStreamingDestination()

        then:
            streamDestination == vipStreamingDestination

        where:
            filepath                                                                          || vipStreamingDestination
            'src/test/resources/str_properties/ipv4_deployement_global.properties'            || [new StreamInfo('10.43.250.4', 10101, 0)]
            'src/test/resources/str_properties/ipv6_deployement_global.properties'            || [new StreamInfo('2001:1b70:82a1:103::172', 10101, 0)]
            'src/test/resources/str_properties/ipv4_ipv6_deployement_global.properties'       || [new StreamInfo('10.43.250.4', 10101, 0),
                                                                                                  new StreamInfo('2001:1b70:82a1:103::172', 10101, 0)]
            'src/test/resources/str_properties/ipv6_deployment_with_subnet_global.properties' || [new StreamInfo('2001:1b70:6207:0000:0000:0707:5433:00A5', 10101, 0)]
    }

    @Unroll
    def 'EbsStreamInfoResolver called with CellTrace category #category return correct ipv6 and ipv4 destination from the global properties file'() {
        given: 'the system property points to valid properties file'
            System.setProperty(EbsStreamInfoResolver.GLOBAL_PROP_ABSOLUTE_FILE_PATH, filepath)

        when: 'the info resolver is called with the category #category'
            def streamDestination = ebsStreamInfoResolver.getStreamingDestination(category)

        then: 'the correct stream infos are returned'
            streamDestination == vipStreamingDestination

        where:
            category                        | filepath                                                                          || vipStreamingDestination
            EBSL_STREAM                     | 'src/test/resources/str_properties/ipv4_deployement_global.properties'            || [new StreamInfo('10.43.250.4', 10101, 0)]
            CELLTRACE_AND_EBSL_STREAM       | 'src/test/resources/str_properties/ipv4_deployement_global.properties'            || [new StreamInfo('10.43.250.4', 10101, 0)]
            ASR                             | 'src/test/resources/str_properties/ipv4_deployement_global.properties'            || [new StreamInfo('10.43.250.4', 10101, 0)]
            ESN                             | 'src/test/resources/str_properties/ipv4_deployement_global.properties'            || [new StreamInfo('10.43.250.4', 10101, 0)]
            NRAN_EBSN_STREAM                | 'src/test/resources/str_properties/ipv4_deployement_global.properties'            || [new StreamInfo('10.43.250.4', 10102, 0)]
            CELLTRACE_NRAN_AND_EBSN_STREAM  | 'src/test/resources/str_properties/ipv4_deployement_global.properties'            || [new StreamInfo('10.43.250.4', 10102, 0)]
            ESN                             | 'src/test/resources/str_properties/ipv6_deployement_global.properties'            || [new StreamInfo('2001:1b70:82a1:103::172', 10101, 0)]
    }

    @Unroll
    def 'EbsStreamInfoResolver return correct ipv4 destination from the global properties file for RPMO subscription'() {
        given: 'the system property points to a valid properties file'
            System.setProperty(EbsStreamInfoResolver.GLOBAL_PROP_ABSOLUTE_FILE_PATH, filepath)

        when: 'the stream resolver is called for an RPMO subscription'
            def streamDestination = ebsStreamInfoResolver.getStreamingDestination(true)

        then: 'the correct stream infos are returned'
            streamDestination == vipStreamingDestination

        where:
            filepath                                                               || vipStreamingDestination
            'src/test/resources/str_properties/ipv4_deployement_global.properties' || [new StreamInfo('10.43.251.5', 10101, 0)]

    }
}
