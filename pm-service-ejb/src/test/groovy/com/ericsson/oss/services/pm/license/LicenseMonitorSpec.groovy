/*******************************************************************************
 * COPYRIGHT Ericsson 2016
 *
 * The copyright to the computer program(s) herein is the property of
 * Ericsson Inc. The programs may be used and/or copied only with written
 * permission from Ericsson Inc. or in accordance with the terms and
 * conditions stipulated in the agreement/contract under which the
 * program(s) have been supplied.
 ******************************************************************************/

package com.ericsson.oss.services.pm.license

import static com.ericsson.oss.itpf.sdk.licensing.Permission.*

import org.slf4j.Logger
import spock.lang.Unroll

import com.ericsson.cds.cdi.support.spock.CdiSpecification
import com.ericsson.oss.itpf.sdk.licensing.LicensingService

class LicenseMonitorSpec extends CdiSpecification {

    final static String EMPTY_TEXT = ""
    final static String ANY_TEXT = "(any text)"

    final static String VALID_LICENSE_EBSM = "FAT1023459"
    final static String VALID_LICENSE_BSCEVENTS = "FAT1024150"
    final static String INVALID_LICENSE = "FAT0000000"
    final static String INVALID_CAPACITY_LICENSE = "FAT0000001"

    LicenseMonitor licenseMonitor

    LicensingService licensingService = Stub(LicensingService)

    def setup() {
        licenseMonitor = new LicenseMonitor(licensingService: licensingService, log: Stub(Logger))
    }

    @Unroll
    def "Verifying #scenario should #result"() {

        given: "that license service behaves according to current scenario"
        mockLicensingService(throwException)

        when: "initializing license monitor"
        licenseMonitor.init()

        and: "scheduler times out"
        if (isScheduled) {
            licenseMonitor.onSchedulerTimeout()
        }

        and: "verifying the license"
        def license = licenseMonitor.verify(licenseName as String)

        then: "the license information should match"
        license.name == licenseName
        license.allowed == licenseAllowed
        license.description == description

        where: "the scenarios are"
        licenseStatus      | licenseName                   | throwException | licenseAllowed | isScheduled | description
        "valid"            | VALID_LICENSE_EBSM            | false          | true           | true        | "Verified with success."
        "valid"            | VALID_LICENSE_BSCEVENTS       | false          | true           | true        | "Verified with success."
        "invalid"          | INVALID_LICENSE               | false          | false          | true        | "Unknown license."
        "invalid capacity" | INVALID_CAPACITY_LICENSE      | false          | false          | true        | "Unknown license."
        "valid"            | VALID_LICENSE_EBSM            | true           | false          | true        | "Failed to verify license. Assuming not allowed."
        "valid"            | VALID_LICENSE_BSCEVENTS       | true           | false          | true        | "Failed to verify license. Assuming not allowed."
        "invalid"          | INVALID_LICENSE               | true           | false          | true        | "Unknown license."
        "invalid capacity" | INVALID_CAPACITY_LICENSE      | true           | false          | true        | "Unknown license."
        "valid"            | VALID_LICENSE_EBSM            | false          | true           | false       | "Verified with success."
        "valid"            | VALID_LICENSE_BSCEVENTS       | false          | true           | false       | "Verified with success."
        "invalid"          | INVALID_LICENSE               | false          | false          | false       | "Unknown license."
        "invalid capacity" | INVALID_CAPACITY_LICENSE      | false          | false          | false       | "Unknown license."
        "valid"            | VALID_LICENSE_EBSM            | true           | false          | false       | "Failed to verify license. Assuming not allowed."
        "valid"            | VALID_LICENSE_BSCEVENTS       | true           | false          | false       | "Failed to verify license. Assuming not allowed."
        "invalid"          | INVALID_LICENSE               | true           | false          | false       | "Unknown license."
        "invalid capacity" | INVALID_CAPACITY_LICENSE      | true           | false          | false       | "Unknown license."

        exceptionDesc = throwException ? "throwing exception" : "not throwing exception"
        scenario = "$licenseStatus license name '$licenseName' $exceptionDesc"
        result = licenseAllowed ? "be allowed" : "not be allowed"
    }

    def "Verifying invalid licence name '#licenseName' should throw IllegalArgumentException"() {

        when: "verifing license name"
        licenseMonitor.verify(licenseName as String)

        then:
        thrown(IllegalArgumentException)

        where: "the license names are"
        licenseName << [EMPTY_TEXT, null]
    }

    def "Verifying when new LicenseMonitor get created, licensingService validatePermission not called."() {
        when:
        cdiInjectorRule.createObject(LicenseMonitor.class)
        then:
        0 * licensingService.validatePermission(_ as String)
    }

    def mockLicensingService(boolean throwException) {
        with(licensingService) {
            if (throwException) {
                validatePermission(_ as String) >> { throw new IllegalArgumentException(ANY_TEXT) }
            } else {
                validatePermission(VALID_LICENSE_EBSM) >> ALLOWED
                validatePermission(VALID_LICENSE_BSCEVENTS) >> ALLOWED
                validatePermission(INVALID_LICENSE) >> DENIED_NO_VALID_LICENSE
                validatePermission(INVALID_CAPACITY_LICENSE) >> DENIED_INSUFFICIENT_CAPACITY
                validatePermission(EMPTY_TEXT) >> { throw new IllegalArgumentException(ANY_TEXT) }
                validatePermission(null) >> { throw new IllegalArgumentException(ANY_TEXT) }
            }
        }
    }
}
