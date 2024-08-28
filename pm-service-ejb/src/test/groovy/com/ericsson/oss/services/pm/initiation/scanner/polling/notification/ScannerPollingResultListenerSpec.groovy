/*
 * COPYRIGHT Ericsson 2017
 *
 * The copyright to the computer program(s) herein is the property of
 * Ericsson Inc. The programs may be used and/or copied only with written
 * permission from Ericsson Inc. or in accordance with the terms and
 * conditions stipulated in the agreement/contract under which the
 * program(s) have been supplied.
 */

package com.ericsson.oss.services.pm.initiation.scanner.polling.notification

import org.slf4j.Logger

import com.ericsson.cds.cdi.support.spock.CdiSpecification
import com.ericsson.oss.pmic.api.scanner.master.ScannerPollingResultProcessor
import com.ericsson.oss.pmic.dao.availability.PmicDpsAvailabilityStatus
import com.ericsson.oss.services.pm.instrumentation.ScannerPollingInstrumentation
import com.ericsson.cds.cdi.support.rule.MockedImplementation
import com.ericsson.cds.cdi.support.rule.ObjectUnderTest
import com.ericsson.oss.services.pm.exception.DataAccessException
import com.ericsson.oss.services.pm.initiation.events.NodeScannerInfo
import com.ericsson.oss.services.pm.initiation.events.ScannerPollingResult
import com.ericsson.oss.services.pm.services.exception.InvalidSubscriptionException

class ScannerPollingResultListenerSpec extends CdiSpecification {

    @ObjectUnderTest
    ScannerPollingResultListener objectUnderTest

    @MockedImplementation
    private Logger logger
    @MockedImplementation
    private PmicDpsAvailabilityStatus dpsAvailabilityStatus
    @MockedImplementation
    private ScannerPollingInstrumentation scannerPollingInstrumentation
    @MockedImplementation
    private ScannerPollingResultProcessor resultProcessor

    def 'Should do nothing if dps is not available'() {
        given: 'the dps is not available'
            dpsAvailabilityStatus.available >> false
            def result = new ScannerPollingResult('anFdn', [])
            result.failed = false

        when: 'a scanner polling result notification is received'
            objectUnderTest.observeScannerPollingResult(result)

        then: 'the scanner polling result listener should do nothing'
            noExceptionThrown()
            1 * logger.warn('Failed processing scanner polling result for {}, Dps not available', result.fdn)
            0 * scannerPollingInstrumentation.scannerPollingTaskEnded(_)
            0 * scannerPollingInstrumentation.scannerPollingTaskFailed(_)
            0 * resultProcessor.processScanners(_, _)
    }

    def 'Should do nothing if result is null'() {
        given: 'the dps is available'
            dpsAvailabilityStatus.available >> false

        when: 'a null scanner polling result notification is received'
            objectUnderTest.processScannerPollingResult(null)

        then: 'the scanner polling result listener should do nothing'
            noExceptionThrown()
            0 * scannerPollingInstrumentation.scannerPollingTaskEnded(_)
            0 * scannerPollingInstrumentation.scannerPollingTaskFailed(_)
            0 * resultProcessor.processScanners(_, _)
    }

    def 'Should not process result if no scanners are included and polling result failure state is true'() {
        given: 'the dps is available but result contains no scanners'
        dpsAvailabilityStatus.available >> true
        def result = new ScannerPollingResult('anFdn', [])
        result.failed = true

        when: 'a scanner polling result notification is received'
        objectUnderTest.observeScannerPollingResult(result)

        then: 'the scanner polling result processor is not called'
        noExceptionThrown()
        1 * scannerPollingInstrumentation.scannerPollingTaskEnded(result.fdn)
        1 * scannerPollingInstrumentation.scannerPollingTaskFailed(result.fdn)
        0 * resultProcessor.processScanners(_, _)
    }

    def 'Should process result if no scanners are included and polling result failure state is false'() {
        given: 'the dps is available but result contains no scanners'
        dpsAvailabilityStatus.available >> true
        def result = new ScannerPollingResult('anFdn', [])
        result.failed = false

        when: 'a scanner polling result notification is received'
        objectUnderTest.observeScannerPollingResult(result)

        then: 'the scanner polling result processor is not called'
        noExceptionThrown()
        1 * resultProcessor.processScanners(_, _)
    }

    def 'Should not throw exception if exception thrown when processing result'() {
        given: 'the dps is available and a result with scanners'
            dpsAvailabilityStatus.available >> true
            def result = new ScannerPollingResult('anFdn', [new NodeScannerInfo(1, 'scannerName', 'scannerStatus', 'processType')])
            result.failed = false

        when: 'an exception is thrown by result processor'
            resultProcessor.processScanners(_ as List, result.fdn) >> {throw exception}
            objectUnderTest.observeScannerPollingResult(result)

        then: 'no exception is thrown by the scanner polling result listener'
            noExceptionThrown()
            1 * logger.error('Error while processing scanner polling result for node [{}]. Exception : {}', result.fdn, exception.message)
            1 * logger.info('Error while processing scanner polling result for node [{}] ', result.fdn, exception)

        where:
            exception << [new DataAccessException('a message'), new InvalidSubscriptionException('a message')]
    }

    def 'Should not attempt to process result if result is failed'() {
        given: 'the dps is available and a result which is failed'
            dpsAvailabilityStatus.available >> true
            def result = new ScannerPollingResult('anFdn', [])
            result.setFailed(true)

        when: 'the scanner polling result notification is received'
            objectUnderTest.observeScannerPollingResult(result)

        then: 'result processor is not executed'
            noExceptionThrown()
            1 * scannerPollingInstrumentation.scannerPollingTaskEnded(result.fdn)
            1 * scannerPollingInstrumentation.scannerPollingTaskFailed(result.fdn)
            0 * resultProcessor.processScanners(_, _)
    }
}
