/*------------------------------------------------------------------------------
 *******************************************************************************
 * COPYRIGHT Ericsson 2012
 *
 * The copyright to the computer program(s) herein is the property of
 * Ericsson Inc. The programs may be used and/or copied only with written
 * permission from Ericsson Inc. or in accordance with the terms and
 * conditions stipulated in the agreement/contract under which the
 * program(s) have been supplied.
 *******************************************************************************
 *----------------------------------------------------------------------------*/

package com.ericsson.oss.services.pm.collection.recovery;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.HashSet;
import java.util.Set;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Matchers;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.internal.util.reflection.Whitebox;
import org.slf4j.Logger;
import org.testng.Assert;

import com.ericsson.oss.pmic.dto.subscription.enums.RopPeriod;
import com.ericsson.oss.services.pm.collection.api.ProcessRequestVO;
import com.ericsson.oss.services.pm.collection.cache.FileCollectionActiveTaskCacheWrapper;
import com.ericsson.oss.services.pm.collection.cache.FileCollectionScheduledRecoveryCacheWrapper;
import com.ericsson.oss.services.pm.collection.task.factories.StatisticalRecoveryTaskRequestFactory;
import com.ericsson.oss.services.pm.collection.tasks.FileCollectionTaskRequest;
import com.ericsson.oss.services.pm.eventSender.PmEventSender;
import com.ericsson.oss.services.pm.initiation.cache.model.value.ProcessType;
import com.ericsson.oss.services.pm.instrumentation.ExtendedFileCollectionInstrumentation;

public class RecoveryHandlerTest {

    RecoveryHandler objectUnderTest;

    @Mock
    private FileCollectionActiveTaskCacheWrapper fileCollectionActiveTaskCache;

    @Mock
    private FileCollectionScheduledRecoveryCacheWrapper fileCollectionScheduledRecoveryCache;

    @Mock
    private Logger logger;

    @Mock
    private StatisticalRecoveryTaskRequestFactory statisticalRecoveryTaskRequestFactory;

    @Mock
    private PmEventSender sender;

    @Mock
    private ExtendedFileCollectionInstrumentation extendedFileCollectionInstrumentation;

    @Before
    public void setUp() {
        objectUnderTest = new RecoveryHandler();
        MockitoAnnotations.initMocks(this);
        Whitebox.setInternalState(objectUnderTest, "fileCollectionActiveTaskCache", fileCollectionActiveTaskCache);
        Whitebox.setInternalState(objectUnderTest, "fileCollectionScheduledRecoveryCache", fileCollectionScheduledRecoveryCache);
        Whitebox.setInternalState(objectUnderTest, "statisticalRecoveryTaskRequestFactory", statisticalRecoveryTaskRequestFactory);
        Whitebox.setInternalState(objectUnderTest, "sender", sender);
        Whitebox.setInternalState(objectUnderTest, "logger", logger);
        Whitebox.setInternalState(objectUnderTest, "extendedFileCollectionInstrumentation", extendedFileCollectionInstrumentation);
    }

    @Test
    public void shouldRecoverFilesForNodeWhenProcessTypeIsStats() {
        final ProcessRequestVO recoveryRequest = mock(ProcessRequestVO.class);
        when(recoveryRequest.getProcessType()).thenReturn(ProcessType.STATS.name());
        when(recoveryRequest.getNodeAddress()).thenReturn("NetworkElement=Node1");
        when(recoveryRequest.getRopPeriod()).thenReturn(RopPeriod.FIFTEEN_MIN.getDurationInSeconds());
        when(sender.sendPmEvent(any(FileCollectionTaskRequest.class))).thenReturn(true);
        final FileCollectionTaskRequest task = mock(FileCollectionTaskRequest.class);
        when(task.getNodeAddress()).thenReturn("NetworkElement=Node1");
        when(statisticalRecoveryTaskRequestFactory.createFileCollectionRecoveryOnStartupTaskRequest(
                any(String.class), any(long.class), any(long.class), any(long.class))).thenReturn(task);

        objectUnderTest.recoverFilesForNode(recoveryRequest, 5, false);

        verify(sender, times(1)).sendPmEvent(any(FileCollectionTaskRequest.class));
    }

    @Test
    public void shouldGetFileCollectionRequestForAllActiveProcess() {
        final long startTime = 1434277359989l;
        final Set<ProcessRequestVO> processRequests = new HashSet<>();
        processRequests.add(new ProcessRequestVO.ProcessRequestVOBuilder("Node1", RopPeriod.ONE_MIN.getDurationInSeconds(),
                ProcessType.STATS.name()).startTime(startTime).build());
        processRequests.add(new ProcessRequestVO.ProcessRequestVOBuilder("Node2", RopPeriod.ONE_MIN.getDurationInSeconds(),
                ProcessType.STATS.name()).startTime(startTime).build());
        processRequests.add(new ProcessRequestVO.ProcessRequestVOBuilder("Node3", RopPeriod.ONE_MIN.getDurationInSeconds(),
                ProcessType.STATS.name()).startTime(startTime).build());
        processRequests.add(new ProcessRequestVO.ProcessRequestVOBuilder("Node1",
                RopPeriod.FIFTEEN_MIN.getDurationInSeconds(), ProcessType.STATS.name()).startTime(startTime).build());
        processRequests
                .add(new ProcessRequestVO.ProcessRequestVOBuilder("Node2", RopPeriod.FIFTEEN_MIN.getDurationInSeconds(),
                        ProcessType.HIGH_PRIORITY_CELLTRACE.name()).startTime(startTime).build());
        processRequests
                .add(new ProcessRequestVO.ProcessRequestVOBuilder("Node4", RopPeriod.FIFTEEN_MIN.getDurationInSeconds(),
                        ProcessType.NORMAL_PRIORITY_CELLTRACE.name()).startTime(startTime).build());
        processRequests.add(new ProcessRequestVO.ProcessRequestVOBuilder("Node4",
                RopPeriod.FIFTEEN_MIN.getDurationInSeconds(), ProcessType.STATS.name()).startTime(startTime).build());
        when(fileCollectionActiveTaskCache.getProcessRequestForRop(Matchers.<Integer>anyVararg()))
                .thenReturn(processRequests);

        final Set<ProcessRequestVO> allRequest = objectUnderTest.buildProcessRequestForAllActiveProcess();

        Assert.assertEquals(7, allRequest.size());
    }

    @Test
    public void shouldGetFileCollectionRequestForAllRemovedProcessesWithinRecoveryPeriod() {

        final ProcessRequestVO request900Node1Stats = new ProcessRequestVO.ProcessRequestVOBuilder("Node1",
                RopPeriod.FIFTEEN_MIN.getDurationInSeconds(), ProcessType.STATS.name())
                        .startTime(System.currentTimeMillis() - 1000).endTime(System.currentTimeMillis()).build();
        final ProcessRequestVO request900Node1CellTrace = new ProcessRequestVO.ProcessRequestVOBuilder("Node1",
                RopPeriod.FIFTEEN_MIN.getDurationInSeconds(), ProcessType.HIGH_PRIORITY_CELLTRACE.name())
                        .startTime(System.currentTimeMillis() - 1000 - 24 * 60 * 60 * 1000)
                        .endTime(System.currentTimeMillis() - 500 - 24 * 60 * 60 * 1000).build();

        final ProcessRequestVO request900Node2Stats = new ProcessRequestVO.ProcessRequestVOBuilder("Node2",
                RopPeriod.FIFTEEN_MIN.getDurationInSeconds(), ProcessType.STATS.name())
                        .startTime(System.currentTimeMillis() - 1000).endTime(System.currentTimeMillis() - 500).build();

        final ProcessRequestVO request900Node3Stats = new ProcessRequestVO.ProcessRequestVOBuilder("Node3",
                RopPeriod.FIFTEEN_MIN.getDurationInSeconds(), ProcessType.STATS.name())
                        .startTime(System.currentTimeMillis() - 1000 - 24 * 60 * 60 * 1000)
                        .endTime(System.currentTimeMillis() - 500 - 24 * 60 * 60 * 1000).build();

        final Set<ProcessRequestVO> processRequests = new HashSet<>();
        processRequests.add(request900Node1Stats);
        processRequests.add(request900Node1CellTrace);
        processRequests.add(request900Node2Stats);
        processRequests.add(request900Node3Stats);

        when(fileCollectionScheduledRecoveryCache.getProcessRequestForRop(Matchers.<Integer>anyVararg())).thenReturn(processRequests);
        final Set<ProcessRequestVO> allRequest = new HashSet<>();
        objectUnderTest.appendProcessRequestForAllRemovedProcesses(allRequest, 24);

        Assert.assertEquals(2, allRequest.size());
    }
}
