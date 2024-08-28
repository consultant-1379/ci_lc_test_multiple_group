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
import static com.ericsson.oss.services.pm.initiation.utils.TestConstants.FIFTEEN_MINUTE_AND_ABOVE_TIMER
import static com.ericsson.oss.services.pm.initiation.utils.TestConstants.FIVE_MIN_TIMER
import static com.ericsson.oss.services.pm.initiation.utils.TestConstants.ONE_MINUTE_TIMER
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

import javax.ejb.Timer
import javax.ejb.TimerConfig

import com.ericsson.cds.cdi.support.rule.ImplementationInstance
import com.ericsson.cds.cdi.support.rule.ObjectUnderTest

import spock.lang.Shared
import spock.lang.Unroll

class StatisticalFileDeletionSchedulerBeanSpec extends AbstractFileDeletionSchedulerBeanSpec {

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
        (STATS_1MIN_ROP):180, // 60 files/h * 3h(default retention period 3h) = 180 files
        (STATS_5MIN_ROP):864, // 12 files/h * 72h(default retention period 3 dd) = 864 files
        (STATS_15MIN_ROP):288, // 4 files/h * 72h(default retention period 3 dd) = 288 files
        (STATS_30MIN_ROP):144, // 2 files/h * 72h(default retention period 3 dd) = 144 files
        (STATS_1HOUR_ROP):72, // 1 file/h * 72h(default retention period 3 dd) = 72 files
        (STATS_12HOUR_ROP):6, // 2 file/day * 3dd(default retention period 3 dd) = 6 files
        (STATS_24HOUR_ROP):3 // 1 file/day * 3dd(default retention period 3 dd) = 3 files
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
    @Shared def ropPeriodDurationInSeconds = [STATS_1MIN_ROP, STATS_5MIN_ROP, STATS_15MIN_ROP, STATS_30MIN_ROP,
        STATS_1HOUR_ROP, STATS_12HOUR_ROP, STATS_24HOUR_ROP]

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
    StatisticalFileDeletionSchedulerBean deletionScheduler

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
    def "On file deletion interval expiration for #ropDescription, all relevant files (and not symlinks) shold be deleted"() {
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

        // creates files in file system
        fileSystemHelper.createTestFiles(fileCollectionDir, filesToBeCreated, deltaRetentionPeriod * retentionPeriodInMin * 60000L, minutesPerRop)

        // creates symLinks
        fileSystemHelper.createTestSymlinks(fileCollectionDir, symLinkDir, minutesPerRop)

        // the mocked timer should pretend to be the timer with TimerConfig equals to timerName
        timer.getInfo() >> timers[timerName].info

        when: "file deletion interval expiration for the relevant timer"
        deletionScheduler.performCleanup(timer)

        then: "all files that are passed their retention period shold be deleted from the file system, all related symlinks deleted"
        fileSystemHelper.isTestDirectoryPresent(fileCollectionDir) == expectedfiles
        fileSystemHelper.getFilesInDirectory(fileCollectionDir).size() == (expectedfiles ? numOfFileInOneRetentionPeriod : 0)
        fileSystemHelper.getFilesInDirectory(symLinkDir).size() == filesToBeCreated

        and: "the correct FlsRequest has been invoked"
        assert getDeletedRecordInvokationCounter == dataTypes[timerName].size()
        flsDeleteRequestRetentionPeriodInMinutesParameter.each { assert it == retentionPeriodInMin }
        flsDeleteRequestDataTypeParameter.each {
            assert dataTypes[timerName].contains(it)
        }

        where:
        ropInSec         | deltaRetentionPeriod |ropStr    |timerName                      | fileCollectionDir                | symLinkDir               | expectedfiles
        STATS_15MIN_ROP  | 0L                   |"15 min"  |FIFTEEN_MINUTE_AND_ABOVE_TIMER | STATS_FILE_COLLECTION_DIR_15MIN  | STATS_SYMLINK_DIR_15MIN  | true
        STATS_30MIN_ROP  | 0L                   |"30 min"  |FIFTEEN_MINUTE_AND_ABOVE_TIMER | STATS_FILE_COLLECTION_DIR_30MIN  | STATS_SYMLINK_DIR_30MIN  | true
        STATS_1HOUR_ROP  | 0L                   |"1 hour"  |FIFTEEN_MINUTE_AND_ABOVE_TIMER | STATS_FILE_COLLECTION_DIR_1HOUR  | STATS_SYMLINK_DIR_1HOUR  | true
        STATS_12HOUR_ROP | 0L                   |"12 hour" |FIFTEEN_MINUTE_AND_ABOVE_TIMER | STATS_FILE_COLLECTION_DIR_12HOUR | STATS_SYMLINK_DIR_12HOUR | true
        STATS_24HOUR_ROP | 0L                   |"24 hour" |FIFTEEN_MINUTE_AND_ABOVE_TIMER | STATS_FILE_COLLECTION_DIR_24HOUR | STATS_SYMLINK_DIR_24HOUR | true
        STATS_5MIN_ROP   | 0L                   |"5 min"   |FIVE_MIN_TIMER                 | STATS_FILE_COLLECTION_DIR_5MIN   | STATS_SYMLINK_DIR_5MIN   | true
        STATS_1MIN_ROP   | 0L                   |"1 min"   |ONE_MINUTE_TIMER               | STATS_FILE_COLLECTION_DIR_1MIN   | STATS_SYMLINK_DIR_1MIN   | true
        STATS_15MIN_ROP  | 2L                   |"15 min"  |FIFTEEN_MINUTE_AND_ABOVE_TIMER | STATS_FILE_COLLECTION_DIR_15MIN  | STATS_SYMLINK_DIR_15MIN  | false
        STATS_30MIN_ROP  | 1L                   |"30 min"  |FIFTEEN_MINUTE_AND_ABOVE_TIMER | STATS_FILE_COLLECTION_DIR_30MIN  | STATS_SYMLINK_DIR_30MIN  | false
        STATS_1HOUR_ROP  | 1L                   |"1 hour"  |FIFTEEN_MINUTE_AND_ABOVE_TIMER | STATS_FILE_COLLECTION_DIR_1HOUR  | STATS_SYMLINK_DIR_1HOUR  | false
        STATS_12HOUR_ROP | 3L                   |"12 hour" |FIFTEEN_MINUTE_AND_ABOVE_TIMER | STATS_FILE_COLLECTION_DIR_12HOUR | STATS_SYMLINK_DIR_12HOUR | false
        STATS_24HOUR_ROP | 2L                   |"24 hour" |FIFTEEN_MINUTE_AND_ABOVE_TIMER | STATS_FILE_COLLECTION_DIR_24HOUR | STATS_SYMLINK_DIR_24HOUR | false
        STATS_5MIN_ROP   | 1L                   |"5 min"   |FIVE_MIN_TIMER                 | STATS_FILE_COLLECTION_DIR_5MIN   | STATS_SYMLINK_DIR_5MIN   | false
        STATS_1MIN_ROP   | 1L                   |"1 min"   |ONE_MINUTE_TIMER               | STATS_FILE_COLLECTION_DIR_1MIN   | STATS_SYMLINK_DIR_1MIN   | false

        ropDescription = "$ropStr rop $deltaRetentionPeriod"
    }

    @Unroll
    def "On file deletion interval expiration for #ropDescription, other directory are not deleted"() {
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

        // creates files in file system
        def otherRops = [
            (STATS_1MIN_ROP):STATS_FILE_COLLECTION_DIR_1MIN,
            (STATS_5MIN_ROP):STATS_FILE_COLLECTION_DIR_5MIN,
        ]

        fileSystemHelper.createTestFiles(fileCollectionDir, filesToBeCreated, minutesPerRop)
        for (def otherRop: otherRops.keySet()) {
            fileSystemHelper.createTestFiles(otherRops.get(otherRop), 10, (int)(otherRop/2))
        }

        // creates symLinks
        fileSystemHelper.createTestSymlinks(fileCollectionDir, symLinkDir, minutesPerRop)

        // the mocked timer should pretend to be the timer with TimerConfig equals to timerName
        timer.getInfo() >> timers[timerName].info

        when: "file deletion interval expiration for the relevant timer"
        deletionScheduler.performCleanup(timer)

        then: "all files that are passed their retention period shold be deleted from the file system, all related symlinks unchanged"
        fileSystemHelper.getFilesInDirectory(fileCollectionDir).size() == numOfFileInOneRetentionPeriod + (subDirRemaining ? otherRops.size() : 0)
        fileSystemHelper.getFilesInDirectory(symLinkDir).size() == filesToBeCreated

        and: "other files should not be deleted"
        fileSystemHelper.getFilesInDirectory(STATS_FILE_COLLECTION_DIR_5MIN).size() == 10

        and: "the correct FlsRequest has been invoked"
        assert getDeletedRecordInvokationCounter == dataTypes[timerName].size()
        flsDeleteRequestRetentionPeriodInMinutesParameter.each { assert it == retentionPeriodInMin }
        flsDeleteRequestDataTypeParameter.each {
            assert dataTypes[timerName].contains(it)
        }

        where:
        ropInSec         | ropStr    |timerName                      | fileCollectionDir                | symLinkDir               | subDirRemaining
        STATS_15MIN_ROP  | "15 min"  |FIFTEEN_MINUTE_AND_ABOVE_TIMER | STATS_FILE_COLLECTION_DIR_15MIN  | STATS_SYMLINK_DIR_15MIN  | true
        STATS_30MIN_ROP  | "30 min"  |FIFTEEN_MINUTE_AND_ABOVE_TIMER | STATS_FILE_COLLECTION_DIR_30MIN  | STATS_SYMLINK_DIR_30MIN  | false
        STATS_1HOUR_ROP  | "1 hour"  |FIFTEEN_MINUTE_AND_ABOVE_TIMER | STATS_FILE_COLLECTION_DIR_1HOUR  | STATS_SYMLINK_DIR_1HOUR  | false
        STATS_12HOUR_ROP | "12 hour" |FIFTEEN_MINUTE_AND_ABOVE_TIMER | STATS_FILE_COLLECTION_DIR_12HOUR | STATS_SYMLINK_DIR_12HOUR | false
        STATS_24HOUR_ROP | "24 hour" |FIFTEEN_MINUTE_AND_ABOVE_TIMER | STATS_FILE_COLLECTION_DIR_24HOUR | STATS_SYMLINK_DIR_24HOUR | false

        ropDescription = "$ropStr rop "
    }

    @Unroll
    def "On file deletion interval expiration for #timerName, when #filesDescr are present in each dir, only relevant files are deleted (symlinks unchanged)"() {
        given: "files collected and symlink created during a retention period and a half"
            createFilesAndSymlinksForAllRops(FILE_IN_ROP_AND_A_HALF);

            // the mocked timer should pretend to be the timer with TimerConfig equals to timerName
            timer.getInfo() >> timers[timerName].info

        when: "file deletion interval expiration for the relevant timer"
            deletionScheduler.performCleanup(timer)

        then: "all relevant files and symlinks deleted, others not deleted"
            assert checkFilesAndSymlinksFor(timerName, FILE_IN_ROP_AND_A_HALF)

        where:
            timerName << [FIVE_MIN_TIMER, FIFTEEN_MINUTE_AND_ABOVE_TIMER]

         timerDescr = "$timerName"
         filesDescr = "file created during a ret period"
    }

    @Unroll
    def "On file deletion interval expiration for #timerName, when #filesDescr are not present in any dir, symlinks dir are not deleted"() {
        given: "files collected and symlink created during a retention period"
            createOnlyOldFilesAndSymlinksForAllRops(FILE_IN_ROP);

            // the mocked timer should pretend to be the timer with TimerConfig equals to timerName
            timer.getInfo() >> timers[timerName].info

        when: "file deletion interval expiration for the relevant timer"
            deletionScheduler.performCleanup(timer)

        then: "all relevant files and symlinks deleted, others not deleted"
            assert fileSystemHelper.isTestDirectoryPresent(fileDirs[STATS_5MIN_ROP]) == false
            assert fileSystemHelper.isTestDirectoryPresent(symlinkDirs[STATS_5MIN_ROP]) == true
        assert true

        where:
            timerName << [FIVE_MIN_TIMER]

         timerDescr = "$timerName"
         filesDescr = "file created during a ret perdiod"
    }

    @Unroll
    def "On Cleanup should delete all APG files that are passed their retention period"() {
        given: "Files Are created "
        fileSystemHelper.createTestFiles(STATS_FILE_COLLECTION_APG_DIR, DAYS_5 * HOURS_24 * FILES_PER_HR, MINUTES_PER_ROP)
        timer.getInfo() >> timers[FIFTEEN_MINUTE_AND_ABOVE_TIMER].info
        when:
        deletionScheduler.performCleanup(timer)
        then:
        fileSystemHelper.getFilesInDirectory(STATS_FILE_COLLECTION_APG_DIR).size() == 3 * HOURS_24 * FILES_PER_HR
    }

    @Unroll
    def "When create timer is called should create timers with correct info"() {
        given: "StatisticalFileDeletionSchedulerBean started"
        when: "create timer is called"
        deletionScheduler.createTimerOnStartup()
        then: "the relevant timers will be created"
        assert timersCreated.contains({FIFTEEN_MINUTE_AND_ABOVE_TIMER} as TimerConfig)
        assert timersCreated.contains({ONE_MINUTE_TIMER} as TimerConfig)
        assert timersCreated.contains({FIVE_MIN_TIMER} as TimerConfig)
    }

    def cleanup() {
        for (entry in fileDirs) {
            fileSystemHelper.deleteTestFiles(entry.value)
            fileSystemHelper.deleteTestDirectories(entry.value)
        }
        for (entry in symlinkDirs) {
            fileSystemHelper.deleteTestFiles(entry.value)
            fileSystemHelper.deleteTestDirectories(entry.value)
        }

        fileSystemHelper.deleteTestFiles(STATS_FILE_COLLECTION_APG_DIR)
        fileSystemHelper.deleteTestDirectories(STATS_FILE_COLLECTION_APG_DIR)
    }

    def findTimerForStatistical() {
        def index = 0
        timersCreated.each {
            def timerInfo = it.info
            if (FIVE_MIN_TIMER.equals(timerInfo) || ONE_MINUTE_TIMER.equals(timerInfo) || FIFTEEN_MINUTE_AND_ABOVE_TIMER.equals(timerInfo)) {
                timers[timerInfo] = it
                retentionsPeriodInMin[timerInfo] = deletionScheduler.getRetentionPeriod(timerInfo)
            }
            index++
        }
    }

    def prepareDataTypes() {
        dataTypes.clear()
        dataTypes[ONE_MINUTE_TIMER] = [DATATYPE_STATISTICAL_1MIN]
        dataTypes[FIVE_MIN_TIMER] = [DATATYPE_STATISTICAL_5MIN]
        dataTypes[FIFTEEN_MINUTE_AND_ABOVE_TIMER] =[
            DATATYPE_STATISTICAL,
            DATATYPE_STATISTICAL_30MIN,
            DATATYPE_STATISTICAL_1HOUR,
            DATATYPE_STATISTICAL_12HOUR,
            DATATYPE_STATISTICAL_24HOUR
        ]
    }

    def createFilesAndSymlinksForAllRops(howManyFiles) {
        ropPeriodDurationInSeconds.each {
            fileSystemHelper.createTestFiles(fileDirs[it], calculateFilesNum(it, howManyFiles), (int)(it/60L))
            fileSystemHelper.createTestSymlinks(fileDirs[it], symlinkDirs[it], (int)(it/60L))
        }
    }

    def createOnlyOldFilesAndSymlinksForAllRops(howManyFiles) {
        ropPeriodDurationInSeconds.each {
            fileSystemHelper.createTestFiles(fileDirs[it], calculateFilesNum(it, howManyFiles), (int)(it/60L) + 43200)
            fileSystemHelper.createTestSymlinks(fileDirs[it], symlinkDirs[it], (int)(it/60L) + 43200)
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

    def checkFilesAndSymlinksFor(timerName, howManyFiles) {
        def howManyFilesNotImpactedDir = howManyFiles
        def howManyFilesImpactedDir = FILE_IN_ROP
        def subDirOf15MinDir = ropPeriodDurationInSeconds.size() - 1
        def result = true
        def impactedRopDirSize = [:]
        def notImpactedRopDirSize = [:]
        ropPeriodDurationInSeconds.each {
            impactedRopDirSize[it] = calculateFilesNum(it, howManyFilesImpactedDir)
        }
        ropPeriodDurationInSeconds.each {
            notImpactedRopDirSize[it] = calculateFilesNum(it, howManyFilesNotImpactedDir)
        }
        impactedRopDirSize[STATS_15MIN_ROP] = impactedRopDirSize[STATS_15MIN_ROP] + subDirOf15MinDir
        notImpactedRopDirSize[STATS_15MIN_ROP] = notImpactedRopDirSize[STATS_15MIN_ROP] + subDirOf15MinDir
        def impactedRop = []
        def notImpactedRop = []

        switch (timerName) {
            case ONE_MINUTE_TIMER:
                impactedRop = [STATS_1MIN_ROP]
                notImpactedRop = [STATS_5MIN_ROP, STATS_15MIN_ROP, STATS_30MIN_ROP, STATS_1HOUR_ROP, STATS_12HOUR_ROP, STATS_24HOUR_ROP]
                break
            case FIVE_MIN_TIMER:
                impactedRop = [STATS_5MIN_ROP]
                notImpactedRop = [STATS_1MIN_ROP, STATS_15MIN_ROP, STATS_30MIN_ROP, STATS_1HOUR_ROP, STATS_12HOUR_ROP, STATS_24HOUR_ROP]
                break
            default :
                impactedRop = [STATS_15MIN_ROP, STATS_30MIN_ROP, STATS_1HOUR_ROP, STATS_12HOUR_ROP, STATS_24HOUR_ROP]
                notImpactedRop = [STATS_1MIN_ROP, STATS_5MIN_ROP]
                break
        }

        impactedRop.each {
            def numSymLink = calculateFilesNum(it, howManyFiles)
            if (it == STATS_15MIN_ROP) {
                numSymLink = numSymLink + subDirOf15MinDir
            }
            result = result && (fileSystemHelper.getFilesInDirectory(fileDirs[it]).size() == impactedRopDirSize[it])
            result = result && (fileSystemHelper.getFilesInDirectory(symlinkDirs[it]).size() == numSymLink)
        }
        notImpactedRop.each {
            result = result && (fileSystemHelper.getFilesInDirectory(fileDirs[it]).size() == notImpactedRopDirSize[it])
            result = result && (fileSystemHelper.getFilesInDirectory(symlinkDirs[it]).size() == notImpactedRopDirSize[it])
        }
        return result
    }
}