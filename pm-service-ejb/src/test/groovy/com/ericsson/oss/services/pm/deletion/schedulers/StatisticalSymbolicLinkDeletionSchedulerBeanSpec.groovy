/*******************************************************************************
 * COPYRIGHT Ericsson 2016
 *
 * The copyright to the computer program(s) herein is the property of
 * Ericsson Inc. The programs may be used and/or copied only with written
 * permission from Ericsson Inc. or in accordance with the terms and
 * conditions stipulated in the agreement/contract under which the
 * program(s) have been supplied.
 ******************************************************************************/

package com.ericsson.oss.services.pm.deletion.schedulers

import static com.ericsson.oss.services.pm.initiation.utils.TestConstants.DATATYPE_STATISTICAL
import static com.ericsson.oss.services.pm.initiation.utils.TestConstants.DATATYPE_STATISTICAL_12HOUR
import static com.ericsson.oss.services.pm.initiation.utils.TestConstants.DATATYPE_STATISTICAL_1HOUR
import static com.ericsson.oss.services.pm.initiation.utils.TestConstants.DATATYPE_STATISTICAL_1MIN
import static com.ericsson.oss.services.pm.initiation.utils.TestConstants.DATATYPE_STATISTICAL_24HOUR
import static com.ericsson.oss.services.pm.initiation.utils.TestConstants.DATATYPE_STATISTICAL_30MIN
import static com.ericsson.oss.services.pm.initiation.utils.TestConstants.DATATYPE_STATISTICAL_5MIN
import static com.ericsson.oss.services.pm.initiation.utils.TestConstants.STATS_12HOUR_ROP
import static com.ericsson.oss.services.pm.initiation.utils.TestConstants.STATS_15MIN_ROP
import static com.ericsson.oss.services.pm.initiation.utils.TestConstants.STATS_1HOUR_ROP
import static com.ericsson.oss.services.pm.initiation.utils.TestConstants.STATS_1MIN_ROP
import static com.ericsson.oss.services.pm.initiation.utils.TestConstants.STATS_24HOUR_ROP
import static com.ericsson.oss.services.pm.initiation.utils.TestConstants.STATS_30MIN_ROP
import static com.ericsson.oss.services.pm.initiation.utils.TestConstants.STATS_5MIN_ROP
import static com.ericsson.oss.services.pm.initiation.utils.TestConstants.STATS_FILE_COLLECTION_APG_DIR
import static com.ericsson.oss.services.pm.initiation.utils.TestConstants.STATS_FILE_COLLECTION_DIR_12HOUR
import static com.ericsson.oss.services.pm.initiation.utils.TestConstants.STATS_FILE_COLLECTION_DIR_15MIN
import static com.ericsson.oss.services.pm.initiation.utils.TestConstants.STATS_FILE_COLLECTION_DIR_1HOUR
import static com.ericsson.oss.services.pm.initiation.utils.TestConstants.STATS_FILE_COLLECTION_DIR_1MIN
import static com.ericsson.oss.services.pm.initiation.utils.TestConstants.STATS_FILE_COLLECTION_DIR_24HOUR
import static com.ericsson.oss.services.pm.initiation.utils.TestConstants.STATS_FILE_COLLECTION_DIR_30MIN
import static com.ericsson.oss.services.pm.initiation.utils.TestConstants.STATS_FILE_COLLECTION_DIR_5MIN
import static com.ericsson.oss.services.pm.initiation.utils.TestConstants.STATS_SYMLINK_DIR_12HOUR
import static com.ericsson.oss.services.pm.initiation.utils.TestConstants.STATS_SYMLINK_DIR_15MIN
import static com.ericsson.oss.services.pm.initiation.utils.TestConstants.STATS_SYMLINK_DIR_1HOUR
import static com.ericsson.oss.services.pm.initiation.utils.TestConstants.STATS_SYMLINK_DIR_1MIN
import static com.ericsson.oss.services.pm.initiation.utils.TestConstants.STATS_SYMLINK_DIR_24HOUR
import static com.ericsson.oss.services.pm.initiation.utils.TestConstants.STATS_SYMLINK_DIR_30MIN
import static com.ericsson.oss.services.pm.initiation.utils.TestConstants.STATS_SYMLINK_DIR_5MIN
import static com.ericsson.oss.services.pm.initiation.utils.TestConstants.SYMLINK_FIVE_MINUTE_AND_ABOVE_TIMER
import static com.ericsson.oss.services.pm.initiation.utils.TestConstants.SYMLINK_ONE_MINUTE_TIMER

import javax.ejb.Timer
import javax.ejb.TimerConfig

import com.ericsson.cds.cdi.support.rule.ImplementationInstance
import com.ericsson.cds.cdi.support.rule.ObjectUnderTest

import spock.lang.Shared
import spock.lang.Unroll

class StatisticalSymbolicLinkDeletionSchedulerBeanSpec extends AbstractFileDeletionSchedulerBeanSpec {

    @Shared def timers
    @Shared def retentionsPeriodInMin
    @Shared def dataTypes = [:]
    @Shared def flsDeleteRequestDataTypeParameter = []
    @Shared def flsDeleteRequestRetentionPeriodInMinutesParameter = []
    @Shared def getDeletedRecordInvokationCounter = 0
    @Shared def FILE_IN_ROP = "FILE_IN_ROP"
    @Shared def FILE_IN_ROP_AND_A_HALF = "FILE_IN_ROP_AND_A_HALF"
    @Shared def FIXED_FILE_NUMBER = "FIXED_FILE_NUMBER"
    @Shared def NUM_OF_FILES_FOR_FIXED_FILE_NUMBER = 1500 // it has to be greater than 1296

    @Shared def filesInRetPeriod = [
        (STATS_1MIN_ROP):180, // 60 files/h  *  3h(default retention period 3h) = 180 files
        (STATS_5MIN_ROP):288, // 12 files/h  * 24h(default retention period 1dd) = 288 files
        (STATS_15MIN_ROP):96, //  4 files/h  * 24h(default retention period 1dd) = 96 files
        (STATS_30MIN_ROP):48, //  2 files/h  * 24h(default retention period 1dd) = 48 files
        (STATS_1HOUR_ROP):24, //  1 file/h   * 24h(default retention period 1dd) = 24 files
        (STATS_12HOUR_ROP):2, //  2 file/day * 1dd(default retention period 1dd) = 2 files
        (STATS_24HOUR_ROP):1, //  1 file/day * 1dd(default retention period 1dd) = 1 files
    ]
    @Shared def fileDirs = [(STATS_1MIN_ROP):STATS_FILE_COLLECTION_DIR_1MIN,
        (STATS_5MIN_ROP):STATS_FILE_COLLECTION_DIR_5MIN,
        (STATS_15MIN_ROP):STATS_FILE_COLLECTION_DIR_15MIN,
        (STATS_30MIN_ROP):STATS_FILE_COLLECTION_DIR_30MIN,
        (STATS_1HOUR_ROP):STATS_FILE_COLLECTION_DIR_1HOUR,
        (STATS_12HOUR_ROP):STATS_FILE_COLLECTION_DIR_12HOUR,
        (STATS_24HOUR_ROP):STATS_FILE_COLLECTION_DIR_24HOUR]
    @Shared def symlinkDirs = [(STATS_1MIN_ROP):STATS_SYMLINK_DIR_1MIN,
        (STATS_5MIN_ROP):STATS_SYMLINK_DIR_5MIN,
        (STATS_15MIN_ROP):STATS_SYMLINK_DIR_15MIN,
        (STATS_30MIN_ROP):STATS_SYMLINK_DIR_30MIN,
        (STATS_1HOUR_ROP):STATS_SYMLINK_DIR_1HOUR,
        (STATS_12HOUR_ROP):STATS_SYMLINK_DIR_12HOUR,
        (STATS_24HOUR_ROP):STATS_SYMLINK_DIR_24HOUR]
    @Shared def ropPeriodDurationInSeconds = [STATS_1MIN_ROP, STATS_5MIN_ROP, STATS_30MIN_ROP,
        STATS_1HOUR_ROP, STATS_12HOUR_ROP, STATS_24HOUR_ROP, STATS_15MIN_ROP]

    @ImplementationInstance
    Timer timer = Mock()

    @ImplementationInstance
    ResourceRetryManager resourceRetryManager = [
        getDeletedRecord : { request ->
            flsDeleteRequestDataTypeParameter <<  request.getDataType()
            flsDeleteRequestRetentionPeriodInMinutesParameter <<  request.getRetentionPeriodInMinutes()
            getDeletedRecordInvokationCounter++
            null
        }
    ] as ResourceRetryManager

    @ObjectUnderTest
    StatisticalSymlinkDeletionSchedulerBean deletionScheduler

    def setup() {
        timers = [:]
        retentionsPeriodInMin = [:]
        dataTypes = [:]
        flsDeleteRequestDataTypeParameter = []
        flsDeleteRequestRetentionPeriodInMinutesParameter = []
        getDeletedRecordInvokationCounter = 0
        findTimerForStatistical()
        prepareDataTypes()
    }

    @Unroll
    def "On symlink deletion interval expiration for #ropDescription, all relevant symlinks (and no files) should be deleted ()"() {
        given: "files collected and symlink created during a retention period and a half"
        def int retentionPeriodInMin = retentionsPeriodInMin[timerName]
        def int numOfDaysInOneRetentionPeriod = retentionPeriodInMin / (24 * 60)
        def int numOfMinutesRemainderInOneRetentionPeriodExceedingTheDays = retentionPeriodInMin % (24 * 60)
        def int filesCollectedInADay = (24 * 60 * 60) / ropInSec
        def int filesCollectedInAnHour = (60 * 60) / ropInSec
        def int numOfFileInOneRetentionPeriod = numOfDaysInOneRetentionPeriod * filesCollectedInADay
        + numOfMinutesRemainderInOneRetentionPeriodExceedingTheDays * filesCollectedInAnHour

        def int numOfFileInHalfRetentionPeriod = numOfFileInOneRetentionPeriod/2
        def int filesToBeCreated = numOfFileInOneRetentionPeriod + numOfFileInHalfRetentionPeriod

        def int minutesPerRop = ropInSec / 60L

        // create files and symlinks in file system
        fileSystemHelper.createTestFilesAndSymlink(fileCollectionDir, symLinkDir, filesToBeCreated,
            deltaRetentionPeriod * retentionPeriodInMin * 60000L, minutesPerRop)
        assert fileSystemHelper.getFilesInDirectory(symLinkDir).size() == filesToBeCreated

        // the mocked timer should pretend to be the timer with TimerConfig equals to timerName
        timer.getInfo() >> timers[timerName].info

        when: "symlink deletion interval expiration for the relevant timer"
        deletionScheduler.performCleanup(timer)

        then: "all symlinks that are passed their retention period shold be deleted from the file system, files no"
        fileSystemHelper.getFilesInDirectory(fileCollectionDir).size() == filesToBeCreated
        def int expectedfile = (expectedfiles ? numOfFileInOneRetentionPeriod : 0)
        assert fileSystemHelper.getFilesInDirectory(symLinkDir).size() == expectedfile
        assert fileSystemHelper.isTestDirectoryEmpty(symLinkDir) == !expectedfiles

        and: "no FlsRequests have been invoked"
        assert getDeletedRecordInvokationCounter == 0
        assert flsDeleteRequestRetentionPeriodInMinutesParameter.isEmpty()
        assert flsDeleteRequestDataTypeParameter.isEmpty()

        where:
        ropInSec         | deltaRetentionPeriod |ropStr    |timerName                           | fileCollectionDir                | symLinkDir               | expectedfiles
        STATS_15MIN_ROP  | 0L                   |"15 min"  |SYMLINK_FIVE_MINUTE_AND_ABOVE_TIMER | STATS_FILE_COLLECTION_DIR_15MIN  | STATS_SYMLINK_DIR_15MIN  | true
        STATS_30MIN_ROP  | 0L                   |"30 min"  |SYMLINK_FIVE_MINUTE_AND_ABOVE_TIMER | STATS_FILE_COLLECTION_DIR_30MIN  | STATS_SYMLINK_DIR_30MIN  | true
        STATS_1HOUR_ROP  | 0L                   |"1 hour"  |SYMLINK_FIVE_MINUTE_AND_ABOVE_TIMER | STATS_FILE_COLLECTION_DIR_1HOUR  | STATS_SYMLINK_DIR_1HOUR  | true
        STATS_12HOUR_ROP | 0L                   |"12 hour" |SYMLINK_FIVE_MINUTE_AND_ABOVE_TIMER | STATS_FILE_COLLECTION_DIR_12HOUR | STATS_SYMLINK_DIR_12HOUR | true
        STATS_24HOUR_ROP | 0L                   |"24 hour" |SYMLINK_FIVE_MINUTE_AND_ABOVE_TIMER | STATS_FILE_COLLECTION_DIR_24HOUR | STATS_SYMLINK_DIR_24HOUR | true
        STATS_5MIN_ROP   | 0L                   |"5 min"   |SYMLINK_FIVE_MINUTE_AND_ABOVE_TIMER | STATS_FILE_COLLECTION_DIR_5MIN   | STATS_SYMLINK_DIR_5MIN   | true
        ropDescription = "$ropStr rop $deltaRetentionPeriod"
    }

    @Unroll
    def "On symlink deletion interval expiration for 1MIN rop, all relevant symlinks (and no files) should be deleted ()"() {
        given: "files collected and symlink created during a retention period and a half"
        def int retentionPeriodInMin = retentionsPeriodInMin[timerName]
        def int filesCollectedInAnHour = 60
        def int numOfFileInOneRetentionPeriod = 180

        def int numOfFileInHalfRetentionPeriod = numOfFileInOneRetentionPeriod/2
        def int filesToBeCreated = numOfFileInOneRetentionPeriod + numOfFileInHalfRetentionPeriod

        def int minutesPerRop = 1

        // create files and symlinks in file system
        fileSystemHelper.createTestFilesAndSymlink(fileCollectionDir, symLinkDir, filesToBeCreated,
            deltaRetentionPeriod * retentionPeriodInMin * 60000L, minutesPerRop)
        assert fileSystemHelper.getFilesInDirectory(symLinkDir).size() == filesToBeCreated

        // the mocked timer should pretend to be the timer with TimerConfig equals to timerName
        timer.getInfo() >> timers[timerName].info

        when: "symlink deletion interval expiration for the relevant timer"
        deletionScheduler.performCleanup(timer)

        then: "all symlinks that are passed their retention period shold be deleted from the file system, files no"
        fileSystemHelper.getFilesInDirectory(fileCollectionDir).size() == filesToBeCreated
        def int expectedfile = (expectedfiles ? numOfFileInOneRetentionPeriod : 0)
        assert fileSystemHelper.getFilesInDirectory(symLinkDir).size() == expectedfile
        assert fileSystemHelper.isTestDirectoryEmpty(symLinkDir) == !expectedfiles

        and: "no FlsRequests have been invoked"
        assert getDeletedRecordInvokationCounter == 0
        assert flsDeleteRequestRetentionPeriodInMinutesParameter.isEmpty()
        assert flsDeleteRequestDataTypeParameter.isEmpty()

        where:
        ropInSec         | deltaRetentionPeriod |ropStr    |timerName                           | fileCollectionDir                | symLinkDir               | expectedfiles
        STATS_1MIN_ROP   | 0L                   |"1 min"   |SYMLINK_ONE_MINUTE_TIMER            | STATS_FILE_COLLECTION_DIR_1MIN   | STATS_SYMLINK_DIR_1MIN   | true
        ropDescription = "$ropStr rop $deltaRetentionPeriod"
    }

    @Unroll
    def "On symlink deletion interval expiration for #ropDescription, all old symlinks (and no files) should be deleted ()"() {
        given: "files collected and symlink created during a retention period and a half"
        def retentionPeriodInMin = retentionsPeriodInMin[timerName]
        def numOfDaysInOneRetentionPeriod = retentionPeriodInMin / (24 * 60)
        def numOfMinutesRemainderInOneRetentionPeriodExceedingTheDays = retentionPeriodInMin % (24 * 60)
        def filesCollectedInADay = (24 * 60 * 60) / ropInSec
        def filesCollectedInAnHour = (60 * 60) / ropInSec
        def numOfFileInOneRetentionPeriod = numOfDaysInOneRetentionPeriod * filesCollectedInADay
        + numOfMinutesRemainderInOneRetentionPeriodExceedingTheDays * filesCollectedInAnHour

        def numOfFileInHalfRetentionPeriod = numOfFileInOneRetentionPeriod/2
        def int filesToBeCreated = numOfFileInOneRetentionPeriod + numOfFileInHalfRetentionPeriod

        def int minutesPerRop = ropInSec / 60L

        // create files and symlinks in file symte
        fileSystemHelper.createTestFilesAndSymlink(fileCollectionDir, symLinkDir, filesToBeCreated,
            deltaRetentionPeriod * retentionPeriodInMin * 60000L, minutesPerRop)
        assert fileSystemHelper.getFilesInDirectory(symLinkDir).size() == filesToBeCreated

        // the mocked timer should pretend to be the timer with TimerConfig equals to timerName
        timer.getInfo() >> timers[timerName].info

        when: "symlink deletion interval expiration for the relevant timer"
        deletionScheduler.performCleanup(timer)

        then: "all symlinks that are passed their retention period shold be deleted from the file system, files not deleted"
        fileSystemHelper.getFilesInDirectory(fileCollectionDir).size() == filesToBeCreated
        fileSystemHelper.getFilesInDirectory(symLinkDir).size() == (expectedfiles ? numOfFileInOneRetentionPeriod : 0)
        fileSystemHelper.isTestDirectoryEmpty(symLinkDir) == !expectedfiles

        and: "FlsRequest not invoked"
        assert getDeletedRecordInvokationCounter == 0
        assert flsDeleteRequestRetentionPeriodInMinutesParameter.isEmpty()
        assert flsDeleteRequestDataTypeParameter.isEmpty()

        where:
        ropInSec         | deltaRetentionPeriod |ropStr    |timerName                           | fileCollectionDir                | symLinkDir               | expectedfiles
        STATS_15MIN_ROP  | 2L                   |"15 min"  |SYMLINK_FIVE_MINUTE_AND_ABOVE_TIMER | STATS_FILE_COLLECTION_DIR_15MIN  | STATS_SYMLINK_DIR_15MIN  | false
        STATS_30MIN_ROP  | 1L                   |"30 min"  |SYMLINK_FIVE_MINUTE_AND_ABOVE_TIMER | STATS_FILE_COLLECTION_DIR_30MIN  | STATS_SYMLINK_DIR_30MIN  | false
        STATS_1HOUR_ROP  | 1L                   |"1 hour"  |SYMLINK_FIVE_MINUTE_AND_ABOVE_TIMER | STATS_FILE_COLLECTION_DIR_1HOUR  | STATS_SYMLINK_DIR_1HOUR  | false
        STATS_12HOUR_ROP | 3L                   |"12 hour" |SYMLINK_FIVE_MINUTE_AND_ABOVE_TIMER | STATS_FILE_COLLECTION_DIR_12HOUR | STATS_SYMLINK_DIR_12HOUR | false
        STATS_24HOUR_ROP | 2L                   |"24 hour" |SYMLINK_FIVE_MINUTE_AND_ABOVE_TIMER | STATS_FILE_COLLECTION_DIR_24HOUR | STATS_SYMLINK_DIR_24HOUR | false
        STATS_5MIN_ROP   | 1L                   |"5 min"   |SYMLINK_FIVE_MINUTE_AND_ABOVE_TIMER | STATS_FILE_COLLECTION_DIR_5MIN   | STATS_SYMLINK_DIR_5MIN   | false
        STATS_1MIN_ROP   | 1L                   |"1 min"   |SYMLINK_ONE_MINUTE_TIMER            | STATS_FILE_COLLECTION_DIR_1MIN   | STATS_SYMLINK_DIR_1MIN   | false
        ropDescription = "$ropStr rop $deltaRetentionPeriod"
    }

    @Unroll
    def "On symlink deletion interval expiration for #timerName, when #filesDescr are not present in any dir, files dir are not deleted"() {
        given: "files collected and symlink created during a retention period"
            createOnlyOldFilesAndSymlinksForAllRops(FILE_IN_ROP);

            // the mocked timer should pretend to be the timer with TimerConfig equals to timerName
            timer.getInfo() >> timers[timerName].info

        when: "symlink deletion interval expiration for the relevant timer"
            deletionScheduler.performCleanup(timer)

        then: "all relevant symlinks deleted, others not deleted"
            assert fileSystemHelper.isTestDirectoryPresent(fileDirs[STATS_1MIN_ROP]) == true
            assert fileSystemHelper.isTestDirectoryEmpty(symlinkDirs[STATS_1MIN_ROP]) == true
        assert true

        where:
            timerName << [SYMLINK_ONE_MINUTE_TIMER]

         timerDescr = "$timerName"
         filesDescr = "symlink created during a ret perdiod"
    }

    @Unroll
    def "When create timer is called should create timers with correct info"() {
        given: "StatisticalSymlinkDeletionSchedulerBean started"
        when: "create timer is called"
        deletionScheduler.createTimerOnStartup()
        then: "the relevant timers will be created"
        assert timersCreated.contains({SYMLINK_FIVE_MINUTE_AND_ABOVE_TIMER} as TimerConfig)
        assert timersCreated.contains({SYMLINK_ONE_MINUTE_TIMER} as TimerConfig)
    }

    def cleanup() {
        ropPeriodDurationInSeconds.each {
            fileSystemHelper.deleteTestFiles(symlinkDirs[it])
            fileSystemHelper.deleteTestDirectories(symlinkDirs[it])
            fileSystemHelper.deleteTestFiles(fileDirs[it])
            fileSystemHelper.deleteTestDirectories(fileDirs[it])
        }
        fileSystemHelper.deleteTestFiles(STATS_FILE_COLLECTION_APG_DIR)
        fileSystemHelper.deleteTestDirectories(STATS_FILE_COLLECTION_APG_DIR)
    }

    def findTimerForStatistical() {
        def index = 0
        timersCreated.each {
            def timerInfo = it.info
            if (SYMLINK_ONE_MINUTE_TIMER.equals(timerInfo) || SYMLINK_FIVE_MINUTE_AND_ABOVE_TIMER.equals(timerInfo)) {
                timers[timerInfo] = it
                retentionsPeriodInMin[timerInfo] = deletionScheduler.getRetentionPeriod(timerInfo)
            }
            index++
        }
    }

    def prepareDataTypes() {
        dataTypes.clear()
        dataTypes[SYMLINK_ONE_MINUTE_TIMER] = [DATATYPE_STATISTICAL_1MIN]
        dataTypes[SYMLINK_FIVE_MINUTE_AND_ABOVE_TIMER] =[
            DATATYPE_STATISTICAL_5MIN,
            DATATYPE_STATISTICAL,
            DATATYPE_STATISTICAL_30MIN,
            DATATYPE_STATISTICAL_1HOUR,
            DATATYPE_STATISTICAL_12HOUR,
            DATATYPE_STATISTICAL_24HOUR
        ]
    }

    def createFilesAndSymlinksForAllRops(howManyFiles) {
        ropPeriodDurationInSeconds.each {
            fileSystemHelper.createTestFilesAndSymlink(fileDirs[it], symlinkDirs[it], calculateFilesNum(it, howManyFiles),
                0L, (int)(it/60L))
        }
    }

    def createOnlyOldFilesAndSymlinksForAllRops(howManyFiles) {
        ropPeriodDurationInSeconds.each {
            fileSystemHelper.createTestFilesAndSymlink(fileDirs[it], symlinkDirs[it], calculateFilesNum(it, howManyFiles),
                0L, (int)(it/60L) + 43200)
        }
    }

    def int calculateFilesNum(rop, howManyFiles) {
        if (howManyFiles == FIXED_FILE_NUMBER) {
            return NUM_OF_FILES_FOR_FIXED_FILE_NUMBER
        }
        if (howManyFiles == FILE_IN_ROP) {
            return (int) filesInRetPeriod.get(rop)
        }
        if (howManyFiles == FILE_IN_ROP_AND_A_HALF) {
            return (int) filesInRetPeriod.get(rop) + filesInRetPeriod.get(rop)/2
        }
        return (int) NUM_OF_FILES_FOR_FIXED_FILE_NUMBER
    }
}