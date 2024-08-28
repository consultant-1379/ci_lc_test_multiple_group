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

import static com.ericsson.oss.services.pm.initiation.utils.TestConstants.CELLTRACE_FILE_COLLECTION_DIR
import static com.ericsson.oss.services.pm.initiation.utils.TestConstants.TARGET_DIR

import javax.ejb.TimerConfig

import com.ericsson.cds.cdi.support.rule.ObjectUnderTest

class CellTraceSymbolicLinkDeletionSchedulerBeanSpec extends AbstractFileDeletionSchedulerBeanSpec {

    private final static int SYMLINK_DELETION_INTERVAL_IN_MINUTES = 15;
    private final static String SYMLINK_DIRECTORY = TARGET_DIR + "symlinks" + File.separator

    @ObjectUnderTest
    CellTraceSymlinkDeletionSchedulerBean deletionScheduler

    def "When delete symlinks is called should delete symlinks"() {
        when:
        fileSystemHelper.createTestFiles(CELLTRACE_FILE_COLLECTION_DIR, DAYS_5 * HOURS_24 * FILES_PER_HR, MINUTES_PER_ROP)
        fileSystemHelper.createTestSymlinks(CELLTRACE_FILE_COLLECTION_DIR, SYMLINK_DIRECTORY, MINUTES_PER_ROP)

        then:
        fileSystemHelper.getFilesInDirectory(SYMLINK_DIRECTORY).size() == DAYS_5 * HOURS_24 * FILES_PER_HR

        when:
        deletionScheduler.performCleanup()

        then:
        fileSystemHelper.getFilesInDirectory(SYMLINK_DIRECTORY).size() == HOURS_3 * FILES_PER_HR
    }

    def "When create timer is called should create timer with correct info"() {
        given:
        TimerConfig timerConfig = new TimerConfig(SYMLINK_DELETION_INTERVAL_IN_MINUTES, false);
        when: "create timer is called"
        deletionScheduler.createTimerOnStartup();
        then:
        timersCreated.contains({ timer -> timer.getInfo() == timerConfig.getInfo() } as TimerConfig)
    }

    def cleanup() {
        fileSystemHelper.deleteTestFiles(CELLTRACE_FILE_COLLECTION_DIR)
        fileSystemHelper.deleteTestDirectories(CELLTRACE_FILE_COLLECTION_DIR)
        fileSystemHelper.deleteTestFiles(SYMLINK_DIRECTORY)
        fileSystemHelper.deleteTestDirectories(SYMLINK_DIRECTORY)
    }
}
