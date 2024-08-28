/*------------------------------------------------------------------------------
 *******************************************************************************
 * COPYRIGHT Ericsson 2016
 *
 * The copyright to the computer program(s) herein is the property of
 * Ericsson Inc. The programs may be used and/or copied only with written
 * permission from Ericsson Inc. or in accordance with the terms and
 * conditions stipulated in the agreement/contract under which the
 * program(s) have been supplied.
 *******************************************************************************
 *----------------------------------------------------------------------------*/
package com.ericsson.oss.services.pm.initiation.cache.model.value;


import static org.junit.Assert.assertEquals;

import org.junit.Test;

import com.ericsson.oss.services.pm.collection.roptime.RopTimeInfo;
import com.ericsson.oss.services.pm.collection.tasks.FileCollectionTaskRequest;
import com.ericsson.oss.services.pm.initiation.util.RopTime;

public class FileCollectionTaskWrapperTest {

    private static final long CURRENT_TIME = 1462450800000L; //2016-04-05 12:20:00
    private static final long COLLECTION_DELAY_IN_SEC = 300;
    private static final long DEFAULT_ROP_END_TIME = CURRENT_TIME - COLLECTION_DELAY_IN_SEC * 1000; //2016-04-05 12:15:00
    private static final long FIFTEEN_MIN_IN_SEC = 900;
    private static final long FIFTEEN_MIN_IN_MILLISEC = FIFTEEN_MIN_IN_SEC * 1000;

    @Test
    public void givenRopInTheFutureIsTaskExecutableFalse() {
        for (long i = DEFAULT_ROP_END_TIME + FIFTEEN_MIN_IN_MILLISEC * 10; i > DEFAULT_ROP_END_TIME; i -= FIFTEEN_MIN_IN_MILLISEC) {
            checkIsTaskExecutable(false, CURRENT_TIME, i);
        }
    }

    @Test
    public void givenRopThatEndsFiveMinsBeforeTimerExecutionIsTaskExecutableTrue() {
        checkIsTaskExecutable(true, CURRENT_TIME, DEFAULT_ROP_END_TIME);
    }

    @Test
    public void givenTaskFromTheRopBeforeLastIsTaskExecutableFalse() {
        checkIsTaskExecutable(false, CURRENT_TIME, DEFAULT_ROP_END_TIME - FIFTEEN_MIN_IN_MILLISEC);
    }

    @Test
    public void givenTaskFromEarlierThanTheRopBeforeLastIsTaskExecutableTrue() {
        for (long i = DEFAULT_ROP_END_TIME - FIFTEEN_MIN_IN_MILLISEC * 2; i > FIFTEEN_MIN_IN_MILLISEC * 10; i -= FIFTEEN_MIN_IN_MILLISEC) {
            checkIsTaskExecutable(true, CURRENT_TIME, i);
        }
    }

    @Test
    public void givenRopNewerThanRopBeforeLastIsReadyForRemovalFalse() {
        for (long i = DEFAULT_ROP_END_TIME + FIFTEEN_MIN_IN_MILLISEC * 10; i >= DEFAULT_ROP_END_TIME - FIFTEEN_MIN_IN_MILLISEC;
             i -= FIFTEEN_MIN_IN_MILLISEC) {
            checkIsTaskReadyForRemoval(false, CURRENT_TIME, i);
        }
    }

    @Test
    public void givenRopBeforeLastOrOlderIsReadyForRemovalTrue() {
        for (long i = DEFAULT_ROP_END_TIME - FIFTEEN_MIN_IN_MILLISEC * 2; i > FIFTEEN_MIN_IN_MILLISEC * 10; i -= FIFTEEN_MIN_IN_MILLISEC) {
            checkIsTaskReadyForRemoval(true, CURRENT_TIME, i);
        }
    }

    private void checkIsTaskExecutable(final boolean expectedIsExecutable, final long currentTime, final long taskTime) {
        final FileCollectionTaskRequest fileCollectionTaskRequest = new FileCollectionTaskRequest();
        final RopTime ropTime = new RopTime(taskTime, FIFTEEN_MIN_IN_SEC);
        final RopTimeInfo ropTimeInfo = new RopTimeInfo(FIFTEEN_MIN_IN_SEC, COLLECTION_DELAY_IN_SEC);
        final FileCollectionTaskWrapper fileCollectionTaskWrapper = new FileCollectionTaskWrapper(fileCollectionTaskRequest, ropTime, ropTimeInfo);
        assertEquals("Expected executable:" + expectedIsExecutable + " for current time:" + currentTime + " and taskTime:" + taskTime,
                expectedIsExecutable, fileCollectionTaskWrapper.isTaskExecutableNow(currentTime, Long.toString(FIFTEEN_MIN_IN_SEC)));
    }

    private void checkIsTaskReadyForRemoval(final boolean expectedIsReady, final long currentTime, final long taskTime) {
        final FileCollectionTaskRequest fileCollectionTaskRequest = new FileCollectionTaskRequest();
        final RopTime ropTime = new RopTime(taskTime, FIFTEEN_MIN_IN_SEC);
        final RopTimeInfo ropTimeInfo = new RopTimeInfo(FIFTEEN_MIN_IN_SEC, COLLECTION_DELAY_IN_SEC);
        final FileCollectionTaskWrapper fileCollectionTaskWrapper = new FileCollectionTaskWrapper(fileCollectionTaskRequest, ropTime, ropTimeInfo);
        assertEquals(
                "Expected ReadyForRemoval:" + expectedIsReady + " for current time:" + currentTime + " and taskTime:" + taskTime,
                expectedIsReady, fileCollectionTaskWrapper.isTaskReadyForRemoval(currentTime, Long.toString(FIFTEEN_MIN_IN_SEC)));
    }
}
