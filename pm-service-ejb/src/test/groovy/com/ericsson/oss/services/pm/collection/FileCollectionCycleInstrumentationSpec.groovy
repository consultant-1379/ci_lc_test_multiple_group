package com.ericsson.oss.services.pm.collection

import com.ericsson.cds.cdi.support.rule.ImplementationInstance
import com.ericsson.cds.cdi.support.rule.ObjectUnderTest
import com.ericsson.cds.cdi.support.spock.CdiSpecification
import com.ericsson.oss.itpf.sdk.recording.EventLevel
import com.ericsson.oss.itpf.sdk.recording.SystemRecorder
import com.ericsson.oss.services.pm.collection.instrumentation.FileCollectionCycleInstrumentation
import com.ericsson.oss.services.pm.initiation.constants.PmicLogCommands
import com.ericsson.oss.services.pm.initiation.util.RopTime
import com.ericsson.oss.services.pm.time.TimeGenerator
import spock.lang.Unroll

import java.text.SimpleDateFormat
import java.util.concurrent.TimeUnit

import static com.ericsson.oss.pmic.api.handler.PmMediationHandlerConstants.RecorderMessageFormat.PMIC_INPUT_EVENT_RECEIVED

class FileCollectionCycleInstrumentationSpec extends CdiSpecification {

    @ObjectUnderTest
    FileCollectionCycleInstrumentation objectUnderTest

    @ImplementationInstance
    SystemRecorder systemRecorder = mock(SystemRecorder)

    @ImplementationInstance
    TimeGenerator timeGenerator = mock(TimeGenerator)

    static SimpleDateFormat simpleDateFormat = new SimpleDateFormat('dd/MM/yyyy HH:mm:ss')
    static long currentTime = simpleDateFormat.parse('22/09/2000 12:07:30').getTime()
    static long updatedTime = simpleDateFormat.parse('22/09/2000 12:20:30').getTime()
    static int oneDayInMinutes = TimeUnit.DAYS.toMinutes(1)

    def setup() {
        simpleDateFormat.setTimeZone(TimeZone.getTimeZone('GMT'))
        objectUnderTest.setCycleId('someCycleId')
        objectUnderTest.setRopPeriodInSeconds(900)
        timeGenerator.currentTimeMillis() >> currentTime
    }

    @Unroll
    def 'Last rop data will be kept when #scenario'() {
        given:
            objectUnderTest.setRopPeriodInSeconds(ropPeriodInSeconds)
            RopTime ropTime = new RopTime(time, ropPeriodInSeconds).getLastROP(ropsAgo)
        when:
            objectUnderTest.updateDataForSuccessAndFailure(1, 0, 1024, 1024)
            timeGenerator.currentTimeMillis() >> time
            objectUnderTest.resetCurrentROP()
        then:
            1 * systemRecorder.recordEvent(PmicLogCommands.PMIC_FILE_COLLECTION_STATISTICS.getDescription(), EventLevel.DETAILED, PMIC_INPUT_EVENT_RECEIVED,
                    'COMPONENT EVENT', {
                stats ->
                    stats.contains('someCycleId') &&
                            stats.contains(',ropStartTimeIdentifier=' + ropTime.getCurrentRopStartTimeInMilliSecs()) &&
                            stats.contains(',ropPeriodInMinutes=' + TimeUnit.SECONDS.toMinutes(ropPeriodInSeconds)) &&
                            stats.contains(',numberOfFilesCollected=' + 1) &&
                            stats.contains(',numberOfFilesFailed=' + 0) &&
                            stats.contains(',numberOfBytesStored=' + 1024) &&
                            stats.contains(',numberOfBytesTransferred=' + 1024) &&
                            stats.contains(',ropStartTime=' + currentTime) &&
                            stats.contains(',ropEndTime=')
            } as String)
            assertFileCollectionDetails(0, 1)
        where:
            scenario                        | time        | ropPeriodInSeconds | ropsAgo
            'current rop data is reset'     | currentTime | 900                | 2
            'into the next rop after reset' | updatedTime | 900                | 2
            'one minute rop'                | currentTime | 60                 | 3
    }

    @Unroll
    def 'Registering #filesCollectedInCurrentRop current rops data and #filesCollectedInLastRop last rop data'() {
        given:
            objectUnderTest.setRopPeriodInSeconds(900)
        when:
            if (filesCollectedInLastRop > 0) {
                1.upto(filesCollectedInLastRop) {
                    objectUnderTest.updateDataForSuccessAndFailure(1, 0, 1024, 1024)
                }
                objectUnderTest.resetCurrentROP()
            }

            1.upto(filesCollectedInCurrentRop) {
                objectUnderTest.updateDataForSuccessAndFailure(1, 0, 1024, 1024)
            }

        then:
            expectedEventRecordings * systemRecorder.recordEvent(PmicLogCommands.PMIC_FILE_COLLECTION_STATISTICS.getDescription(), EventLevel.DETAILED, PMIC_INPUT_EVENT_RECEIVED,
                    'COMPONENT EVENT', _ as String)
            assertFileCollectionDetails(filesCollectedInCurrentRop, filesCollectedInLastRop)
        where:
            filesCollectedInCurrentRop | filesCollectedInLastRop || expectedEventRecordings
            10                         | 4                       || 1
            6                          | 0                       || 0
    }

    def 'Registering current rop data when vm is shutdown'() {
        given:
            objectUnderTest.setCycleId('fifteenMinuteRopFileCollectionCycleInstrumentation')
        when:
            1.upto(6) {
                objectUnderTest.updateDataForSuccessAndFailure(1, 0, 1024, 1024)
            }
            objectUnderTest.writeOutCurrentBufferedDate()

        then:
            1 * systemRecorder.recordEvent(PmicLogCommands.PMIC_FILE_COLLECTION_STATISTICS.getDescription(), EventLevel.DETAILED, PMIC_INPUT_EVENT_RECEIVED,
                    'COMPONENT EVENT', _ as String)
            assertFileCollectionDetails(6, 0)
    }

    @Unroll
    def 'testing ropStartTimeIdentifier when reset is called for #ropPeriodInMinutes min'() {
        given: '#ropPeriodInMinutes min instrumentation object'
            objectUnderTest.setRopPeriodInSeconds(TimeUnit.MINUTES.toSeconds(ropPeriodInMinutes))
            long time1 = simpleDateFormat.parse(timeString1).getTime()
            long time2 = simpleDateFormat.parse(timeString2).getTime()
            long expectedTime = simpleDateFormat.parse(expectedTimeString).getTime()

        when: 'current time is #time1 and reset is called'
            timeGenerator.currentTimeMillis() >> time1
            objectUnderTest.resetCurrentROP()
        then: 'recorded instrumentation will print identifier for rop #expectedTimeString'
            1 * systemRecorder.recordEvent(PmicLogCommands.PMIC_FILE_COLLECTION_STATISTICS.getDescription(), EventLevel.DETAILED, PMIC_INPUT_EVENT_RECEIVED,
                    'COMPONENT EVENT', { stats -> stats.contains(',ropStartTimeIdentifier=' + expectedTime) } as String)

        when: 'current time is #time2 and reset is called'
            timeGenerator.currentTimeMillis() >> time2
            objectUnderTest.resetCurrentROP()
        then: 'recorded instrumentation will print identifier for rop #expectedTimeString'
            1 * systemRecorder.recordEvent(PmicLogCommands.PMIC_FILE_COLLECTION_STATISTICS.getDescription(), EventLevel.DETAILED, PMIC_INPUT_EVENT_RECEIVED,
                    'COMPONENT EVENT', { stats -> stats.contains(',ropStartTimeIdentifier=' + expectedTime) } as String)
        where:
            ropPeriodInMinutes | timeString1           | timeString2           || expectedTimeString
            15                 | '22/09/2000 12:05:00' | '22/09/2000 12:05:05' || '22/09/2000 11:30:00'
            1                  | '22/09/2000 12:00:00' | '22/09/2000 12:00:05' || '22/09/2000 11:57:00'
            oneDayInMinutes    | '22/09/2000 12:00:00' | '22/09/2000 23:00:00' || '20/09/2000 00:00:00'
    }

    @Unroll
    def 'testing ropStartTimeIdentifier when writeout is called for #ropPeriodInMinutes min'() {
        given: '#ropPeriodInMinutes min instrumentation object'
            objectUnderTest.setRopPeriodInSeconds(TimeUnit.MINUTES.toSeconds(ropPeriodInMinutes))
            long time1 = simpleDateFormat.parse(timeString1).getTime()
            long time2 = simpleDateFormat.parse(timeString2).getTime()
            long expectedTime1 = simpleDateFormat.parse(expectedTimeString1).getTime()
            long expectedTime2 = simpleDateFormat.parse(expectedTimeString2).getTime()

        when: 'current time is #timeString1 and writeOut is called'
            timeGenerator.currentTimeMillis() >> time1
            objectUnderTest.writeOutCurrentBufferedDate()
        then: 'recorded instrumentation will print identifier for rop #expectedTimeString1'
            1 * systemRecorder.recordEvent(PmicLogCommands.PMIC_FILE_COLLECTION_STATISTICS.getDescription(), EventLevel.DETAILED, PMIC_INPUT_EVENT_RECEIVED,
                    'COMPONENT EVENT', { stats -> stats.contains(',ropStartTimeIdentifier=' + expectedTime1) } as String)

        when: 'current time is #timeString2 and writeOut is called'
            timeGenerator.currentTimeMillis() >> time2
            objectUnderTest.writeOutCurrentBufferedDate()
        then: 'recorded instrumentation will print identifier for #expectedTimeString2'
            1 * systemRecorder.recordEvent(PmicLogCommands.PMIC_FILE_COLLECTION_STATISTICS.getDescription(), EventLevel.DETAILED, PMIC_INPUT_EVENT_RECEIVED,
                    'COMPONENT EVENT', { stats -> stats.contains(',ropStartTimeIdentifier=' + expectedTime2) } as String)
        where:
            ropPeriodInMinutes | timeString1           | timeString2           || expectedTimeString1   |  expectedTimeString2
            15                 | '22/09/2000 12:36:21' | '22/09/2000 12:49:59' || '22/09/2000 12:15:00' | '22/09/2000 12:15:00'
            1                  | '22/09/2000 12:00:01' | '22/09/2000 12:00:59' || '22/09/2000 11:58:00' | '22/09/2000 11:58:00'
            oneDayInMinutes    | '22/09/2000 00:06:00' | '22/09/2000 00:04:00' || '21/09/2000 00:00:00' | '20/09/2000 00:00:00'

    }

    void assertFileCollectionDetails(filesCollectedInCurrentRop, filesCollectedInLastRop) {
        assert objectUnderTest.getNumberOfFilesFailedCurrentROP() == 0
        assert objectUnderTest.getNumberOfFilesCollectedCurrentROP() == filesCollectedInCurrentRop
        assert objectUnderTest.getNumberOfBytesStoredCurrentROP() == 1024 * filesCollectedInCurrentRop
        assert objectUnderTest.getNumberOfBytesTransferredCurrentROP() == 1024 * filesCollectedInCurrentRop
        assert objectUnderTest.getRopEndTimeCurrentROP() >= ((filesCollectedInCurrentRop > 0) ? currentTime : 0)
        assert objectUnderTest.getRopStartTimeCurrentROP() == ((filesCollectedInCurrentRop > 0) ? currentTime : 0)

        assert objectUnderTest.getNumberOfBytesStoredLastROP() == 1024 * filesCollectedInLastRop
        assert objectUnderTest.getNumberOfBytesTransferedLastROP() == 1024 * filesCollectedInLastRop
        assert objectUnderTest.getNumberOfFilesCollectedLastROP() == filesCollectedInLastRop
        assert objectUnderTest.getNumberOfFilesFailedLastROP() == 0

        assert objectUnderTest.getRopCollectionTime() >= 0
        assert objectUnderTest.getRopEndTimeLastROP() == ((filesCollectedInLastRop > 0) ? currentTime : 0)
        assert objectUnderTest.getRopStartTimeLastROP() >= ((filesCollectedInLastRop > 0) ? currentTime : 0)
    }

}
