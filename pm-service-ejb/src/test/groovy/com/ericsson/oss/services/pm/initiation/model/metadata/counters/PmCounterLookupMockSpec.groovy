/*------------------------------------------------------------------------------
 *******************************************************************************
 * COPYRIGHT Ericsson 2020
 *
 * The copyright to the computer program(s) herein is the property of
 * Ericsson Inc. The programs may be used and/or copied only with written
 * permission from Ericsson Inc. or in accordance with the terms and
 * conditions stipulated in the agreement/contract under which the
 * program(s) have been supplied.
 *******************************************************************************
 *----------------------------------------------------------------------------*/
package com.ericsson.oss.services.pm.initiation.model.metadata.counters

import spock.lang.Unroll

import com.ericsson.cds.cdi.support.rule.ImplementationClasses
import com.ericsson.cds.cdi.support.rule.MockedImplementation
import com.ericsson.cds.cdi.support.rule.SpyImplementation
import com.ericsson.oss.pmic.cdi.test.util.SkeletonSpec
import com.ericsson.oss.pmic.dto.NodeTypeAndVersion
import com.ericsson.oss.pmic.impl.counters.PmCountersLifeCycleResolverImpl
import com.ericsson.oss.pmic.impl.modelservice.PmCapabilityReaderImpl
import com.ericsson.oss.services.pm.initiation.model.metadata.PMICModelDeploymentValidator
import com.ericsson.oss.services.pm.modeling.schema.gen.pfm_measurement.ScannerType
import com.ericsson.services.pm.initiation.restful.api.CounterTableRow

class PmCounterLookupMockSpec extends SkeletonSpec {

    @SpyImplementation
    PmCountersLookUp pmCounterLookUp

    @MockedImplementation
    PMICModelDeploymentValidator pmicModelDeploymentValidator

    @ImplementationClasses
    def classes = [PmCapabilityReaderImpl.class, PmCountersLifeCycleResolverImpl.class]

    def nodeVersionA
    def nodeVersionB
    def nodeVersionC

    def setup() {
        pmicModelDeploymentValidator.isCounterValidationSupportedForGivenTargetType(_ as String) >> false
        nodeVersionA = new NodeTypeAndVersion('RadioNode', 'VALID_OSS_MA', ['EPS'])
        nodeVersionB = new NodeTypeAndVersion('RadioNode', 'VALID_OSS_MB', ['EPS'])
        nodeVersionC = new NodeTypeAndVersion('RadioNode', 'VALID_OSS_MC', ['EPS'])
    }

    @Unroll
    def 'Querying same counters for different node mim versions where the scanner types have not changed between versions' () {
        given: 'the expected counters are deployed in model service'
            pmCounterLookUp.getAllPMCounters(nodeVersionA, '/pfm_measurement/', '/NE-defined/', false) >> { counters(scannerOneType, scannerTwoType) }
            pmCounterLookUp.getAllPMCounters(nodeVersionB, '/pfm_measurement/', '/NE-defined/', false) >> { counters(scannerOneType, scannerTwoType) }

        when: 'a query is performed to fetch counters for the supplied node versions'
            def actualresult = pmCounterLookUp.getCountersForAllVersions([nodeVersionA, nodeVersionB, nodeVersionC] as Set, ['/NE-defined/'], false)

        then: 'the scanner type has not been modified for any counter'
            def expectedResult = counters(scannerOneType, scannerTwoType) // different counters objects so the test doesn't modify our expected result
            expectedResult.size() == actualresult.size()
            //must explicitly compare the scannerType, counterRow1 == counterRow2 ignores scannerType for comparison
            actualresult.every {
                (expectedResult[0].counterName == it.counterName && expectedResult[0].scannerType == it.scannerType) ||
                (expectedResult[1].counterName == it.counterName && expectedResult[1].scannerType == it.scannerType)
            }

        where:
            scannerOneType           | scannerTwoType
            ScannerType.PRIMARY      | ScannerType.USER_DEFINED
            ScannerType.PRIMARY      | ScannerType.SECONDARY
            ScannerType.USER_DEFINED | ScannerType.PRIMARY
            ScannerType.USER_DEFINED | ScannerType.SECONDARY
            ScannerType.SECONDARY    | ScannerType.USER_DEFINED
            ScannerType.SECONDARY    | ScannerType.PRIMARY
    }

    @Unroll
    def 'Querying same counters for different node versions where the scanner type for a counter has changed between 3 node versions such as (#scannerVersionA, #scannerVersionB, #scannerVersionC)' () {
        given: 'the expected counters are deployed in model service'
            pmCounterLookUp.getAllPMCounters(nodeVersionA, '/pfm_measurement/', '/NE-defined/', false) >> { counters(scannerVersionA) }
            pmCounterLookUp.getAllPMCounters(nodeVersionB, '/pfm_measurement/', '/NE-defined/', false) >> { counters(scannerVersionB) }
            pmCounterLookUp.getAllPMCounters(nodeVersionC, '/pfm_measurement/', '/NE-defined/', false) >> { counters(scannerVersionC) }

        when: 'a query is performed to fetch counters for the supplied node versions'
            def actualresult = pmCounterLookUp.getCountersForAllVersions([nodeVersionA, nodeVersionB] as Set, ['/NE-defined/'], false)

        then: "the scanner type for the first counter has been modified to one of the #expectedScannerTypeIsOneOf expected types"
            def expectedResult = counters() // different counters objects so the test doesn't modify our expected result
            expectedResult.size() == actualresult.size()
            //must explicitly compare the scannerType, counterRow1 == counterRow2 ignores scannerType for comparison
            actualresult.every {
                (expectedResult[0].counterName == it.counterName && expectedScannerTypeIsOneOf.contains(it.scannerType)) ||
                (expectedResult[1].counterName == it.counterName && expectedResult[1].scannerType == it.scannerType)
            }

        where:
            scannerVersionA             | scannerVersionB           | scannerVersionC           || expectedScannerTypeIsOneOf
            ScannerType.PRIMARY         | ScannerType.USER_DEFINED  | ScannerType.USER_DEFINED  || [ScannerType.PRIMARY]
            ScannerType.USER_DEFINED    | ScannerType.PRIMARY       | ScannerType.SECONDARY     || [ScannerType.PRIMARY, ScannerType.SECONDARY]
            ScannerType.USER_DEFINED    | ScannerType.SECONDARY     | ScannerType.PRIMARY       || [ScannerType.PRIMARY, ScannerType.SECONDARY]
            ScannerType.SECONDARY       | ScannerType.PRIMARY       | ScannerType.SECONDARY     || [ScannerType.PRIMARY, ScannerType.SECONDARY]
    }

    def counters(c1Type = ScannerType.PRIMARY, c2Type = ScannerType.USER_DEFINED) {
        [
                new CounterTableRow('c1', 'so1', 'desc', c1Type),
                new CounterTableRow('c2', 'so2', 'desc', c2Type)
        ] as Set
    }
}
