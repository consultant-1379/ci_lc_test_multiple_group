/*
 * ------------------------------------------------------------------------------
 *  ********************************************************************************
 *  * COPYRIGHT Ericsson  2016
 *  *
 *  * The copyright to the computer program(s) herein is the property of
 *  * Ericsson Inc. The programs may be used and/or copied only with written
 *  * permission from Ericsson Inc. or in accordance with the terms and
 *  * conditions stipulated in the agreement/contract under which the
 *  * program(s) have been supplied.
 *  *******************************************************************************
 *  *----------------------------------------------------------------------------
 */

package com.ericsson.oss.services.pm.bdd.collection.recovery

import javax.inject.Inject
import java.time.LocalDate

import spock.lang.Unroll

import com.ericsson.oss.pmic.cdi.test.util.SkeletonSpec
import com.ericsson.oss.services.pm.collection.recovery.util.DstUtil

class DstUtilSpec extends SkeletonSpec {

    @Inject
    private DstUtil dstUtil

    @Unroll
    def "Checks if the given date #date is switching to daylight saving time"() {
        given:
        LocalDate localDate = LocalDate.now()

        when: "Recovery is started"
        boolean isDSTChange = dstUtil.observesDSTChange(localDate)

        then: "Old Timer should cancel and create new Timer"
        isDSTChange == false
        where:
        date                                                            || result
        LocalDate.of(2019, 03, 30)             || false
        LocalDate.of(2019, 10, 27)             || true
        LocalDate.of(2019, 03, 31)             || true
        LocalDate.of(2017, 10, 29)             || true
        LocalDate.of(2020, 10, 25)             || true
        LocalDate.of(2018, 10, 28)             || true
        LocalDate.of(2018, 03, 25)             || false

    }

}
