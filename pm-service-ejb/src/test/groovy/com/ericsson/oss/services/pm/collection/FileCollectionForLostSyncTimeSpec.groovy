package com.ericsson.oss.services.pm.collection

import java.util.concurrent.TimeUnit

import com.ericsson.cds.cdi.support.rule.MockedImplementation
import com.ericsson.cds.cdi.support.rule.ObjectUnderTest
import com.ericsson.cds.cdi.support.spock.CdiSpecification
import com.ericsson.oss.pmic.util.TimeGenerator

class FileCollectionForLostSyncTimeSpec extends CdiSpecification {

    @ObjectUnderTest
    FileCollectionForLostSyncTime collectionForLostSyncTime

    @MockedImplementation
    TimeGenerator timeGenerator

    Calendar pivotTimeCalendar

    def setup() {
        pivotTimeCalendar = Calendar.getInstance(TimeZone.getTimeZone("Europe/Dublin"))
        pivotTimeCalendar.set(2019, 1, 9, 2, 58, 11)
        timeGenerator.currentTimeMillis() >> pivotTimeCalendar.getTimeInMillis()
    }

    def "return 0 rop, when there is no loss of rop"() {
        given:
        def fromTime = new Date(pivotTimeCalendar.getTimeInMillis() - TimeUnit.MINUTES.toMillis(2))
        def ropSizeInSeconds = 900
        def ropRecoveryPeriodInHours = 24
        when:
        def totalRopForCollection = collectionForLostSyncTime.getTotalRopsToCollect(fromTime, ropSizeInSeconds, ropRecoveryPeriodInHours)
        then:
        totalRopForCollection == 0
    }

    def "return 0 rop, when sync loss time is null"() {
        given:
        def fromTime = null
        def ropSizeInSeconds = 900
        def ropRecoveryPeriodInHours = 24
        when:
        def totalRopForCollection = collectionForLostSyncTime.getTotalRopsToCollect(fromTime, ropSizeInSeconds, ropRecoveryPeriodInHours)
        then:
        totalRopForCollection == 0
    }

    def "return total number of rop when sync was lost time more than 15 min but less than 30 min for 15 min rop"() {
        given:
        def fromTime = new Date(pivotTimeCalendar.getTimeInMillis() - TimeUnit.MINUTES.toMillis(20))
        def ropSizeInSeconds = 900
        def ropRecoveryPeriodInHours = 24
        when:
        def totalRopForCollection = collectionForLostSyncTime.getTotalRopsToCollect(fromTime, ropSizeInSeconds, ropRecoveryPeriodInHours)
        then:
        totalRopForCollection >= 1
    }

    def "return 0 rop, when there is invalid rop period"() {
        given:
        def fromTime = new Date(pivotTimeCalendar.getTimeInMillis() - TimeUnit.MINUTES.toMillis(2))
        def ropSizeInSeconds = 0
        def ropRecoveryPeriodInHours = 24
        when:
        def totalRopForCollection = collectionForLostSyncTime.getTotalRopsToCollect(fromTime, ropSizeInSeconds, ropRecoveryPeriodInHours)
        then:
        totalRopForCollection == 0
    }
}
