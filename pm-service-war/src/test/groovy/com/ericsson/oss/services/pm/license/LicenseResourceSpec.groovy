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

import static javax.ws.rs.core.Response.Status.OK
import static javax.ws.rs.core.Response.status

import org.slf4j.Logger
import spock.lang.Unroll

import javax.ws.rs.core.Response

import com.ericsson.cds.cdi.support.rule.ImplementationClasses
import com.ericsson.oss.pmic.cdi.test.util.SkeletonSpec
import com.ericsson.oss.services.pm.exception.RetryServiceException
import com.ericsson.oss.services.pm.services.exception.ValidationException

@Unroll
class LicenseResourceSpec extends SkeletonSpec {

    final static String EMPTY_TEXT = " "
    final static String ANY_TEXT = "(any text)"

    final static String VALID_LICENSE_NAME_EBSM = "FAT1023459"
    final static String VALID_LICENSE_NAME_EBSL = "FAT1023527"
    final static String INVALID_LICENSE_NAME = "FAT0000000"
    final static String INVALID_CAPACITY_LICENSE_NAME = "FAT0000001"

    LicenseResource licenseResource

    @ImplementationClasses
            myClasses = [RetryServiceException.class]

    def setup() {
        LicenseChecker licenseChecker = Stub(LicenseChecker)
        with(licenseChecker) {
            verify(VALID_LICENSE_NAME_EBSM) >> new License(VALID_LICENSE_NAME_EBSM, true, ANY_TEXT)
            verify(INVALID_LICENSE_NAME) >> new License(INVALID_LICENSE_NAME, false, ANY_TEXT)
            verify(INVALID_CAPACITY_LICENSE_NAME) >> new License(INVALID_CAPACITY_LICENSE_NAME, false, ANY_TEXT)
            verify(EMPTY_TEXT) >> { throw new IllegalArgumentException(ANY_TEXT) }
            verify(null) >> { throw new IllegalArgumentException(ANY_TEXT) }


            verify([VALID_LICENSE_NAME_EBSM, VALID_LICENSE_NAME_EBSL]) >> [(VALID_LICENSE_NAME_EBSL): new License(VALID_LICENSE_NAME_EBSL, true, ANY_TEXT).toMap(),
                                                                           (VALID_LICENSE_NAME_EBSM): new License(VALID_LICENSE_NAME_EBSM, true, ANY_TEXT).toMap()]

            verify([VALID_LICENSE_NAME_EBSM, INVALID_CAPACITY_LICENSE_NAME]) >> [(VALID_LICENSE_NAME_EBSM)      : new License(VALID_LICENSE_NAME_EBSM, true, ANY_TEXT).toMap(),
                                                                                 (INVALID_CAPACITY_LICENSE_NAME): new License(INVALID_CAPACITY_LICENSE_NAME, false, ANY_TEXT).toMap()]

            verify([INVALID_LICENSE_NAME]) >> [(INVALID_LICENSE_NAME): new License(INVALID_LICENSE_NAME, false, ANY_TEXT).toMap()]
            verify([INVALID_CAPACITY_LICENSE_NAME]) >> [(INVALID_CAPACITY_LICENSE_NAME): new License(INVALID_CAPACITY_LICENSE_NAME, false, ANY_TEXT).toMap()]
        }
        licenseResource = new LicenseResource(licenseChecker: licenseChecker, logger: Stub(Logger))
    }

    def "Verifying license name '#licenseName' should return HTTP #expectedResponse.status and #expectedResponse.entity"() {

        when: "verifying license name"
        Response response = licenseResource.verifyLicenseName(licenseName)

        then: "the status should match"
        response.status == expectedResponse.status

        and: "the entity should match"
        response.entity.name == expectedResponse.entity.name
        response.entity.allowed == expectedResponse.entity.allowed

        where: "the license name and expected responses are"
        licenseName                   || expectedResponse
        VALID_LICENSE_NAME_EBSM       || status(OK).entity([name: VALID_LICENSE_NAME_EBSM, allowed: true]).build()
        INVALID_LICENSE_NAME          || status(OK).entity([name: INVALID_LICENSE_NAME, allowed: false]).build()
        INVALID_CAPACITY_LICENSE_NAME || status(OK).entity([name: INVALID_CAPACITY_LICENSE_NAME, allowed: false]).build()
    }

    def "Verifying license names '#licenseNames' should return HTTP #expectedResponse.status and #expectedResponse.entity"() {

        when: "verifying license names"
        Response response = licenseResource.verifyLicenseNames(licenseNames)

        then: "the status should match"
        response.status == expectedResponse.status

        and: "the entity should match"
        response.entity == expectedResponse.entity


        where: "the license name and expected responses are"
        licenseNames                                             || expectedResponse
        [VALID_LICENSE_NAME_EBSM, VALID_LICENSE_NAME_EBSL]       || status(OK).entity([(VALID_LICENSE_NAME_EBSM): [allowed: true, description: ANY_TEXT, name: VALID_LICENSE_NAME_EBSM],
                                                                                       (VALID_LICENSE_NAME_EBSL): [allowed: true, description: ANY_TEXT, name: VALID_LICENSE_NAME_EBSL]]).build()
        [VALID_LICENSE_NAME_EBSM, INVALID_CAPACITY_LICENSE_NAME] || status(OK).entity([(VALID_LICENSE_NAME_EBSM)      : [allowed: true, description: ANY_TEXT, name: VALID_LICENSE_NAME_EBSM],
                                                                                       (INVALID_CAPACITY_LICENSE_NAME): [allowed: false, description: ANY_TEXT, name: INVALID_CAPACITY_LICENSE_NAME]]).build()
        [INVALID_LICENSE_NAME]                                   || status(OK).entity([(INVALID_LICENSE_NAME): [allowed: false, name: INVALID_LICENSE_NAME, description: ANY_TEXT]]).build()
        [INVALID_CAPACITY_LICENSE_NAME]                          || status(OK).entity([(INVALID_CAPACITY_LICENSE_NAME): [allowed: false, name: INVALID_CAPACITY_LICENSE_NAME, description: ANY_TEXT]]).build()
    }

    def "Verifying invalid licence name '#licenseName' should throw RetryServiceException"() {

        when: "verifing license name"
        licenseResource.verifyLicenseName(licenseName)

        then:
        thrown(ValidationException)

        where: "the license names are"
        licenseName << [EMPTY_TEXT, null]
    }
}
