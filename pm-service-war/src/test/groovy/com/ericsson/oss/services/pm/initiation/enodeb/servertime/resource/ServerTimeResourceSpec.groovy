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

package com.ericsson.oss.services.pm.initiation.enodeb.servertime.resource

import org.slf4j.Logger

import javax.ws.rs.core.Response

import com.ericsson.cds.cdi.support.spock.CdiSpecification
import com.ericsson.oss.services.pm.initiation.enodeb.servertime.resource.dto.DateWithTimeZone

class ServerTimeResourceSpec extends CdiSpecification {

    ServerTimeResource serverTimeResourceSpy = Spy(ServerTimeResource)

    Logger logger = Mock(Logger)

    def setup() {
        serverTimeResourceSpy.@log = logger
    }

    def "getTimeOffset should return a date with timezone and HTTP 200"() {
        given:
        final Calendar expectedCurrentDateAndTime = Calendar.getInstance()
        expectedCurrentDateAndTime.set(Calendar.YEAR, 2015)
        expectedCurrentDateAndTime.set(Calendar.MONTH, 6)
        expectedCurrentDateAndTime.set(Calendar.DAY_OF_MONTH, 24)
        expectedCurrentDateAndTime.set(Calendar.HOUR_OF_DAY, 11)
        expectedCurrentDateAndTime.set(Calendar.MINUTE, 14)
        expectedCurrentDateAndTime.set(Calendar.SECOND, 0)
        expectedCurrentDateAndTime.set(Calendar.MILLISECOND, 0)

        serverTimeResourceSpy.getCurrentDateAndTime() >> expectedCurrentDateAndTime
        final String expectedServerLocationTimezone = "America/Los_Angeles"
        serverTimeResourceSpy.getServerLocationTimezone() >> expectedServerLocationTimezone
        when:
        final Response response = serverTimeResourceSpy.getTimeOffset()

        final int expectedOffset = -25200000
        final DateWithTimeZone expectedEntity = new DateWithTimeZone(expectedCurrentDateAndTime.getTime(), expectedOffset,
                expectedServerLocationTimezone)
        then:
        response.getEntity() == expectedEntity
        response.getStatus() == 200
    }


    def "getTimeOffset should return HTTP 500 when a runtime exception"() {
        given:
        serverTimeResourceSpy.getServerLocationTimezone() >> { throw new UnsupportedOperationException() }
        when:
        final Response response = serverTimeResourceSpy.getTimeOffset()
        then:
        response.getStatus() == 500
    }
}
