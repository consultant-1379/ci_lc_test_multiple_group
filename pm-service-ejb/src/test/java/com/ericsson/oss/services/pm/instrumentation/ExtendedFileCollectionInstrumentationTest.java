/*------------------------------------------------------------------------------
 *******************************************************************************
 * COPYRIGHT Ericsson 2015
 *
 * The copyright to the computer program(s) herein is the property of
 * Ericsson Inc. The programs may be used and/or copied only with written
 * permission from Ericsson Inc. or in accordance with the terms and
 * conditions stipulated in the agreement/contract under which the
 * program(s) have been supplied.
 *******************************************************************************
 *----------------------------------------------------------------------------*/
package com.ericsson.oss.services.pm.instrumentation;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;

import static com.ericsson.oss.services.pm.instrumentation.ExtendedFileCollectionInstrumentation.CELLTRACE_FILE_COLLECTION_COLLECTED_FILES;
import static com.ericsson.oss.services.pm.instrumentation.ExtendedFileCollectionInstrumentation.CELLTRACE_FILE_COLLECTION_DURATION_PER_NODE;
import static com.ericsson.oss.services.pm.instrumentation.ExtendedFileCollectionInstrumentation.CELLTRACE_FILE_COLLECTION_DURATION_PER_ROP;
import static com.ericsson.oss.services.pm.instrumentation.ExtendedFileCollectionInstrumentation.CELLTRACE_FILE_COLLECTION_MISSED_FILES;
import static com.ericsson.oss.services.pm.instrumentation.ExtendedFileCollectionInstrumentation.EBM_FILE_COLLECTION_COLLECTED_FILES;
import static com.ericsson.oss.services.pm.instrumentation.ExtendedFileCollectionInstrumentation.EBM_FILE_COLLECTION_DURATION_PER_NODE;
import static com.ericsson.oss.services.pm.instrumentation.ExtendedFileCollectionInstrumentation.EBM_FILE_COLLECTION_DURATION_PER_ROP;
import static com.ericsson.oss.services.pm.instrumentation.ExtendedFileCollectionInstrumentation.EBM_FILE_COLLECTION_MISSED_FILES;
import static com.ericsson.oss.services.pm.instrumentation.ExtendedFileCollectionInstrumentation.SCHEDULED_RECOVERY_STATISTICAL_FILE_COLLECTION_COLLECTED_FILES;
import static com.ericsson.oss.services.pm.instrumentation.ExtendedFileCollectionInstrumentation.SCHEDULED_RECOVERY_STATISTICAL_FILE_COLLECTION_DURATION_PER_NODE;
import static com.ericsson.oss.services.pm.instrumentation.ExtendedFileCollectionInstrumentation.SCHEDULED_RECOVERY_STATISTICAL_FILE_COLLECTION_DURATION_PER_ROP;
import static com.ericsson.oss.services.pm.instrumentation.ExtendedFileCollectionInstrumentation.SCHEDULED_RECOVERY_STATISTICAL_FILE_COLLECTION_MISSED_FILES;
import static com.ericsson.oss.services.pm.instrumentation.ExtendedFileCollectionInstrumentation.SINGLE_ROP_RECOVERY_CELLTRACE_FILE_COLLECTION_COLLECTED_FILES;
import static com.ericsson.oss.services.pm.instrumentation.ExtendedFileCollectionInstrumentation.SINGLE_ROP_RECOVERY_CELLTRACE_FILE_COLLECTION_DURATION_PER_NODE;
import static com.ericsson.oss.services.pm.instrumentation.ExtendedFileCollectionInstrumentation.SINGLE_ROP_RECOVERY_CELLTRACE_FILE_COLLECTION_DURATION_PER_ROP;
import static com.ericsson.oss.services.pm.instrumentation.ExtendedFileCollectionInstrumentation.SINGLE_ROP_RECOVERY_CELLTRACE_FILE_COLLECTION_MISSED_FILES;
import static com.ericsson.oss.services.pm.instrumentation.ExtendedFileCollectionInstrumentation.SINGLE_ROP_RECOVERY_EBM_FILE_COLLECTION_COLLECTED_FILES;
import static com.ericsson.oss.services.pm.instrumentation.ExtendedFileCollectionInstrumentation.SINGLE_ROP_RECOVERY_EBM_FILE_COLLECTION_DURATION_PER_NODE;
import static com.ericsson.oss.services.pm.instrumentation.ExtendedFileCollectionInstrumentation.SINGLE_ROP_RECOVERY_EBM_FILE_COLLECTION_DURATION_PER_ROP;
import static com.ericsson.oss.services.pm.instrumentation.ExtendedFileCollectionInstrumentation.SINGLE_ROP_RECOVERY_EBM_FILE_COLLECTION_MISSED_FILES;
import static com.ericsson.oss.services.pm.instrumentation.ExtendedFileCollectionInstrumentation.SINGLE_ROP_RECOVERY_STATISTICAL_FILE_COLLECTION_COLLECTED_FILES;
import static com.ericsson.oss.services.pm.instrumentation.ExtendedFileCollectionInstrumentation.SINGLE_ROP_RECOVERY_STATISTICAL_FILE_COLLECTION_DURATION_PER_NODE;
import static com.ericsson.oss.services.pm.instrumentation.ExtendedFileCollectionInstrumentation.SINGLE_ROP_RECOVERY_STATISTICAL_FILE_COLLECTION_DURATION_PER_ROP;
import static com.ericsson.oss.services.pm.instrumentation.ExtendedFileCollectionInstrumentation.SINGLE_ROP_RECOVERY_STATISTICAL_FILE_COLLECTION_MISSED_FILES;
import static com.ericsson.oss.services.pm.instrumentation.ExtendedFileCollectionInstrumentation.STARTUP_RECOVERY_STATISTICAL_FILE_COLLECTION_COLLECTED_FILES;
import static com.ericsson.oss.services.pm.instrumentation.ExtendedFileCollectionInstrumentation.STARTUP_RECOVERY_STATISTICAL_FILE_COLLECTION_DURATION;
import static com.ericsson.oss.services.pm.instrumentation.ExtendedFileCollectionInstrumentation.STARTUP_RECOVERY_STATISTICAL_FILE_COLLECTION_DURATION_PER_NODE;
import static com.ericsson.oss.services.pm.instrumentation.ExtendedFileCollectionInstrumentation.STARTUP_RECOVERY_STATISTICAL_FILE_COLLECTION_MISSED_FILES;
import static com.ericsson.oss.services.pm.instrumentation.ExtendedFileCollectionInstrumentation.STATISTICAL_FILE_COLLECTION_COLLECTED_FILES;
import static com.ericsson.oss.services.pm.instrumentation.ExtendedFileCollectionInstrumentation.STATISTICAL_FILE_COLLECTION_DURATION_PER_NODE;
import static com.ericsson.oss.services.pm.instrumentation.ExtendedFileCollectionInstrumentation.STATISTICAL_FILE_COLLECTION_DURATION_PER_ROP;
import static com.ericsson.oss.services.pm.instrumentation.ExtendedFileCollectionInstrumentation.STATISTICAL_FILE_COLLECTION_MISSED_FILES;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.internal.util.reflection.Whitebox;
import org.slf4j.Logger;

import com.codahale.metrics.MetricRegistry;
import com.ericsson.oss.services.pm.collection.api.ProcessRequestVO;
import com.ericsson.oss.services.pm.collection.constants.FileCollectionConstant;
import com.ericsson.oss.services.pm.collection.events.FileCollectionResult;
import com.ericsson.oss.services.pm.collection.events.FileCollectionSuccess;
import com.ericsson.oss.services.pm.collection.tasks.FileCollectionTaskRequest;
import com.ericsson.oss.services.pm.initiation.cache.model.value.ProcessType;
import com.ericsson.oss.services.pm.scheduling.cluster.MembershipListener;

public class ExtendedFileCollectionInstrumentationTest {
    @Mock
    Logger log;

    MetricRegistry metricRegistry;

    @Mock
    MembershipListener membershipListener;

    ExtendedFileCollectionInstrumentation extendedFileCollectionInstrumentation;

    @Before
    public void setup() {
        extendedFileCollectionInstrumentation = new ExtendedFileCollectionInstrumentation();
        metricRegistry = new MetricRegistry();
        MockitoAnnotations.initMocks(this);
        Whitebox.setInternalState(extendedFileCollectionInstrumentation, "log", log);
        Whitebox.setInternalState(extendedFileCollectionInstrumentation, "metricRegistry", metricRegistry);
        Whitebox.setInternalState(extendedFileCollectionInstrumentation, "membershipListener", membershipListener);
        when(membershipListener.isMaster()).thenReturn(true);
    }

    @Test
    public void shouldNotCollectDataIfItIsNotMasterNode() {
        when(membershipListener.isMaster()).thenReturn(false);
        final String taskId =
                "STATISTICAL_file_collection_|nodeFdn=NetworkElement=EricssonAthloneNode1|ropStartTime=1441732650419|ropPeriod=900|recoverInNextRop=true|07aa5303-5ac6-4955-b84a-48fe7e76461a";
        extendedFileCollectionInstrumentation.fileCollectionTaskStarted(taskId);
        extendedFileCollectionInstrumentation.fileCollectionTaskEnded(taskId);
        assertEquals(metricRegistry.timer(STATISTICAL_FILE_COLLECTION_DURATION_PER_ROP).getCount(), 0);
        assertEquals(metricRegistry.timer(STATISTICAL_FILE_COLLECTION_DURATION_PER_NODE).getCount(), 0);
    }

    @Test
    public void shouldMeasureStatisticalFileCollectionTaskDurationPerRopAndPerNode() {
        final String[] tasksIds =
                new String[]{
                        "STATISTICAL_file_collection_|nodeFdn=NetworkElement=EricssonAthloneNode1|ropStartTime=1441786500000|ropPeriod=900000|recoverInNextRop=true|6645e0f6-2105-4cb0-a591-051faef1c552",
                        "STATISTICAL_file_collection_|nodeFdn=NetworkElement=EricssonAthloneNode2|ropStartTime=1441786500000|ropPeriod=900000|recoverInNextRop=true|1399c058-8767-4ef7-868e-0a4caab0dfae",
                        "STATISTICAL_file_collection_|nodeFdn=NetworkElement=EricssonAthloneNode1|ropStartTime=1441798200000|ropPeriod=900000|recoverInNextRop=true|26a71f4a-d22b-4429-b877-eed6e0d21ed4",
                        "STATISTICAL_file_collection_|nodeFdn=NetworkElement=EricssonAthloneNode2|ropStartTime=1441798200000|ropPeriod=900000|recoverInNextRop=true|5a26d602-0e80-477a-a983-0d505ee63c2d"
                };
        for (final String taskId : tasksIds) {
            extendedFileCollectionInstrumentation.fileCollectionTaskStarted(taskId);
        }
        for (final String taskId : tasksIds) {
            extendedFileCollectionInstrumentation.fileCollectionTaskEnded(taskId);
        }
        assertEquals(metricRegistry.timer(STATISTICAL_FILE_COLLECTION_DURATION_PER_ROP).getCount(), 2);
        assertEquals(metricRegistry.timer(STATISTICAL_FILE_COLLECTION_DURATION_PER_NODE).getCount(), 4);
    }

    @Test
    public void shouldMeasureCellTraceFileCollectionTaskDurationPerRopAndPerNode() {
        final String[] tasksIds =
                new String[]{
                        "CELLTRACE_file_collection_|nodeFdn=NetworkElement=EricssonAthloneNode1|ropStartTime=1441786500000|ropPeriod=900000|recoverInNextRop=true|ead19932-5bb8-43dc-bbfd-cd6816110b1e",
                        "CELLTRACE_file_collection_|nodeFdn=NetworkElement=EricssonAthloneNode2|ropStartTime=1441786500000|ropPeriod=900000|recoverInNextRop=true|40a873de-5dc7-4da5-8673-db3e01a6b212",
                        "CELLTRACE_file_collection_|nodeFdn=NetworkElement=EricssonAthloneNode1|ropStartTime=1441798200000|ropPeriod=900000|recoverInNextRop=true|81c6fd4a-1602-42e4-ad23-40ac26a0294a",
                        "CELLTRACE_file_collection_|nodeFdn=NetworkElement=EricssonAthloneNode2|ropStartTime=1441798200000|ropPeriod=900000|recoverInNextRop=true|f9506ef8-e8f2-4061-b366-2bee4c71f0b1"
                };
        for (final String taskId : tasksIds) {
            extendedFileCollectionInstrumentation.fileCollectionTaskStarted(taskId);
        }
        for (final String taskId : tasksIds) {
            extendedFileCollectionInstrumentation.fileCollectionTaskEnded(taskId);
        }
        assertEquals(metricRegistry.timer(CELLTRACE_FILE_COLLECTION_DURATION_PER_ROP).getCount(), 2);
        assertEquals(metricRegistry.timer(CELLTRACE_FILE_COLLECTION_DURATION_PER_NODE).getCount(), 4);
    }

    @Test
    public void shouldMeasureEbmFileCollectionTaskDurationPerRopAndPerNode() {
        final String[] tasksIds =
                new String[]{
                        "EBM_file_collection_|nodeFdn=NetworkElement=EricssonAthloneNode1|ropStartTime=1441786500000|ropPeriod=900000|recoverInNextRop=true|ead19932-5bb8-43dc-bbfd-cd6816110b1e",
                        "EBM_file_collection_|nodeFdn=NetworkElement=EricssonAthloneNode2|ropStartTime=1441786500000|ropPeriod=900000|recoverInNextRop=true|40a873de-5dc7-4da5-8673-db3e01a6b212",
                        "EBM_file_collection_|nodeFdn=NetworkElement=EricssonAthloneNode1|ropStartTime=1441798200000|ropPeriod=900000|recoverInNextRop=true|81c6fd4a-1602-42e4-ad23-40ac26a0294a",
                        "EBM_file_collection_|nodeFdn=NetworkElement=EricssonAthloneNode2|ropStartTime=1441798200000|ropPeriod=900000|recoverInNextRop=true|f9506ef8-e8f2-4061-b366-2bee4c71f0b1"
                };
        for (final String taskId : tasksIds) {
            extendedFileCollectionInstrumentation.fileCollectionTaskStarted(taskId);
        }
        for (final String taskId : tasksIds) {
            extendedFileCollectionInstrumentation.fileCollectionTaskEnded(taskId);
        }
        assertEquals(metricRegistry.timer(EBM_FILE_COLLECTION_DURATION_PER_ROP).getCount(), 2);
        assertEquals(metricRegistry.timer(EBM_FILE_COLLECTION_DURATION_PER_NODE).getCount(), 4);
    }

    @Test
    public void shouldMeasureSingleRopRecoveryStatisticalFileCollectionTaskDurationPerRopAndPerNode() {
        final String[] tasksIds =
                new String[]{
                        "file_collection_single_rop_recovery_|taskId=STATISTICAL_file_collection_|nodeFdn=NetworkElement=EricssonAthloneNode1|ropStartTime=1441786500000|ropPeriod=900000|recoverInNextRop=true|99b378ec-abc1-48e1-b963-1fd84101c8df|141e81bb-4126-43fc-a813-4d24e74381b8",
                        "file_collection_single_rop_recovery_|taskId=STATISTICAL_file_collection_|nodeFdn=NetworkElement=EricssonAthloneNode2|ropStartTime=1441786500000|ropPeriod=900000|recoverInNextRop=true|99b378ec-abc1-48e1-b963-1fd84101c8df|c73d2355-2694-4124-a9a3-cc404799dcc6",
                        "file_collection_single_rop_recovery_|taskId=STATISTICAL_file_collection_|nodeFdn=NetworkElement=EricssonAthloneNode1|ropStartTime=1441798200000|ropPeriod=900000|recoverInNextRop=true|99b378ec-abc1-48e1-b963-1fd84101c8df|398977c2-8440-4602-bb9c-18a9305ab71a",
                        "file_collection_single_rop_recovery_|taskId=STATISTICAL_file_collection_|nodeFdn=NetworkElement=EricssonAthloneNode2|ropStartTime=1441798200000|ropPeriod=900000|recoverInNextRop=true|99b378ec-abc1-48e1-b963-1fd84101c8df|2e4111df-fa00-45f5-b12a-ac410f84e21f"
                };
        for (final String taskId : tasksIds) {
            extendedFileCollectionInstrumentation.fileCollectionTaskStarted(taskId);
        }
        for (final String taskId : tasksIds) {
            extendedFileCollectionInstrumentation.fileCollectionTaskEnded(taskId);
        }
        assertEquals(metricRegistry.timer(SINGLE_ROP_RECOVERY_STATISTICAL_FILE_COLLECTION_DURATION_PER_ROP).getCount(), 2);
        assertEquals(metricRegistry.timer(SINGLE_ROP_RECOVERY_STATISTICAL_FILE_COLLECTION_DURATION_PER_NODE).getCount(), 4);
    }

    @Test
    public void shouldMeasureSingleRopRecoveryCellTraceFileCollectionTaskDurationPerRopAndPerNode() {
        final String[] taskIds =
                new String[]{
                        "file_collection_single_rop_recovery_|taskId=CELLTRACE_file_collection_|nodeFdn=NetworkElement=EricssonAthloneNode1|ropStartTime=1441786500000|ropPeriod=900000|recoverInNextRop=true|99b378ec-abc1-48e1-b963-1fd84101c8df|2e4111df-fa00-45f5-b12a-ac410f84e21f",
                        "file_collection_single_rop_recovery_|taskId=CELLTRACE_file_collection_|nodeFdn=NetworkElement=EricssonAthloneNode2|ropStartTime=1441786500000|ropPeriod=900000|recoverInNextRop=true|99b378ec-abc1-48e1-b963-1fd84101c8df|6f6805a9-04df-47cc-a2f7-c46708a7f73f",
                        "file_collection_single_rop_recovery_|taskId=CELLTRACE_file_collection_|nodeFdn=NetworkElement=EricssonAthloneNode1|ropStartTime=1441798200000|ropPeriod=900000|recoverInNextRop=true|99b378ec-abc1-48e1-b963-1fd84101c8df|166811d7-dc8c-4e76-a0eb-f08c54a0a41d",
                        "file_collection_single_rop_recovery_|taskId=CELLTRACE_file_collection_|nodeFdn=NetworkElement=EricssonAthloneNode2|ropStartTime=1441798200000|ropPeriod=900000|recoverInNextRop=true|99b378ec-abc1-48e1-b963-1fd84101c8df|395eb830-4f08-498d-9707-70f3ab39b9ba"
                };
        for (final String taskId : taskIds) {
            extendedFileCollectionInstrumentation.fileCollectionTaskStarted(taskId);
        }
        for (final String taskId : taskIds) {
            extendedFileCollectionInstrumentation.fileCollectionTaskEnded(taskId);
        }
        assertEquals(metricRegistry.timer(SINGLE_ROP_RECOVERY_CELLTRACE_FILE_COLLECTION_DURATION_PER_ROP).getCount(), 2);
        assertEquals(metricRegistry.timer(SINGLE_ROP_RECOVERY_CELLTRACE_FILE_COLLECTION_DURATION_PER_NODE).getCount(), 4);
    }

    @Test
    public void shouldMeasureSingleRopRecoveryEbmFileCollectionTaskDurationPerRopAndPerNode() {
        final String[] taskIds =
                new String[]{
                        "file_collection_single_rop_recovery_|taskId=EBM_file_collection_|nodeFdn=NetworkElement=EricssonAthloneNode1|ropStartTime=1441786500000|ropPeriod=900000|recoverInNextRop=true|99b378ec-abc1-48e1-b963-1fd84101c8df|2e4111df-fa00-45f5-b12a-ac410f84e21f",
                        "file_collection_single_rop_recovery_|taskId=EBM_file_collection_|nodeFdn=NetworkElement=EricssonAthloneNode2|ropStartTime=1441786500000|ropPeriod=900000|recoverInNextRop=true|99b378ec-abc1-48e1-b963-1fd84101c8df|6f6805a9-04df-47cc-a2f7-c46708a7f73f",
                        "file_collection_single_rop_recovery_|taskId=EBM_file_collection_|nodeFdn=NetworkElement=EricssonAthloneNode1|ropStartTime=1441798200000|ropPeriod=900000|recoverInNextRop=true|99b378ec-abc1-48e1-b963-1fd84101c8df|166811d7-dc8c-4e76-a0eb-f08c54a0a41d",
                        "file_collection_single_rop_recovery_|taskId=EBM_file_collection_|nodeFdn=NetworkElement=EricssonAthloneNode2|ropStartTime=1441798200000|ropPeriod=900000|recoverInNextRop=true|99b378ec-abc1-48e1-b963-1fd84101c8df|395eb830-4f08-498d-9707-70f3ab39b9ba"
                };
        for (final String taskId : taskIds) {
            extendedFileCollectionInstrumentation.fileCollectionTaskStarted(taskId);
        }
        for (final String taskId : taskIds) {
            extendedFileCollectionInstrumentation.fileCollectionTaskEnded(taskId);
        }
        assertEquals(metricRegistry.timer(SINGLE_ROP_RECOVERY_EBM_FILE_COLLECTION_DURATION_PER_ROP).getCount(), 2);
        assertEquals(metricRegistry.timer(SINGLE_ROP_RECOVERY_EBM_FILE_COLLECTION_DURATION_PER_NODE).getCount(), 4);
    }

    @Test
    public void shouldMeasureFileCollectionTaskDurationPerRopAndPerNodeIncludingSingleRopRecovery() {
        final String[] tasksIds =
                new String[]{
                        "STATISTICAL_file_collection_|nodeFdn=NetworkElement=EricssonAthloneNode1|ropStartTime=1441786500000|ropPeriod=900000|recoverInNextRop=true|6645e0f6-2105-4cb0-a591-051faef1c552",
                        "STATISTICAL_file_collection_|nodeFdn=NetworkElement=EricssonAthloneNode2|ropStartTime=1441786500000|ropPeriod=900000|recoverInNextRop=true|1399c058-8767-4ef7-868e-0a4caab0dfae",
                        "STATISTICAL_file_collection_|nodeFdn=NetworkElement=EricssonAthloneNode1|ropStartTime=1441798200000|ropPeriod=900000|recoverInNextRop=true|26a71f4a-d22b-4429-b877-eed6e0d21ed4",
                        "STATISTICAL_file_collection_|nodeFdn=NetworkElement=EricssonAthloneNode2|ropStartTime=1441798200000|ropPeriod=900000|recoverInNextRop=true|5a26d602-0e80-477a-a983-0d505ee63c2d",
                        "file_collection_single_rop_recovery_|taskId=STATISTICAL_file_collection_|nodeFdn=NetworkElement=EricssonAthloneNode1|ropStartTime=1441786500000|ropPeriod=900000|recoverInNextRop=true|99b378ec-abc1-48e1-b963-1fd84101c8df|141e81bb-4126-43fc-a813-4d24e74381b8",
                        "file_collection_single_rop_recovery_|taskId=STATISTICAL_file_collection_|nodeFdn=NetworkElement=EricssonAthloneNode2|ropStartTime=1441786500000|ropPeriod=900000|recoverInNextRop=true|99b378ec-abc1-48e1-b963-1fd84101c8df|c73d2355-2694-4124-a9a3-cc404799dcc6",
                        "file_collection_single_rop_recovery_|taskId=STATISTICAL_file_collection_|nodeFdn=NetworkElement=EricssonAthloneNode1|ropStartTime=1441798200000|ropPeriod=900000|recoverInNextRop=true|99b378ec-abc1-48e1-b963-1fd84101c8df|398977c2-8440-4602-bb9c-18a9305ab71a",
                        "file_collection_single_rop_recovery_|taskId=STATISTICAL_file_collection_|nodeFdn=NetworkElement=EricssonAthloneNode2|ropStartTime=1441798200000|ropPeriod=900000|recoverInNextRop=true|99b378ec-abc1-48e1-b963-1fd84101c8df|2e4111df-fa00-45f5-b12a-ac410f84e21f",
                        "CELLTRACE_file_collection_|nodeFdn=NetworkElement=EricssonAthloneNode1|ropStartTime=1441786500000|ropPeriod=900000|recoverInNextRop=true|ead19932-5bb8-43dc-bbfd-cd6816110b1e",
                        "CELLTRACE_file_collection_|nodeFdn=NetworkElement=EricssonAthloneNode2|ropStartTime=1441786500000|ropPeriod=900000|recoverInNextRop=true|40a873de-5dc7-4da5-8673-db3e01a6b212",
                        "CELLTRACE_file_collection_|nodeFdn=NetworkElement=EricssonAthloneNode1|ropStartTime=1441798200000|ropPeriod=900000|recoverInNextRop=true|81c6fd4a-1602-42e4-ad23-40ac26a0294a",
                        "CELLTRACE_file_collection_|nodeFdn=NetworkElement=EricssonAthloneNode2|ropStartTime=1441798200000|ropPeriod=900000|recoverInNextRop=true|f9506ef8-e8f2-4061-b366-2bee4c71f0b1",
                        "file_collection_single_rop_recovery_|taskId=CELLTRACE_file_collection_|nodeFdn=NetworkElement=EricssonAthloneNode1|ropStartTime=1441786500000|ropPeriod=900000|recoverInNextRop=true|99b378ec-abc1-48e1-b963-1fd84101c8df|2e4111df-fa00-45f5-b12a-ac410f84e21f",
                        "file_collection_single_rop_recovery_|taskId=CELLTRACE_file_collection_|nodeFdn=NetworkElement=EricssonAthloneNode2|ropStartTime=1441786500000|ropPeriod=900000|recoverInNextRop=true|99b378ec-abc1-48e1-b963-1fd84101c8df|6f6805a9-04df-47cc-a2f7-c46708a7f73f",
                        "file_collection_single_rop_recovery_|taskId=CELLTRACE_file_collection_|nodeFdn=NetworkElement=EricssonAthloneNode1|ropStartTime=1441798200000|ropPeriod=900000|recoverInNextRop=true|99b378ec-abc1-48e1-b963-1fd84101c8df|166811d7-dc8c-4e76-a0eb-f08c54a0a41d",
                        "file_collection_single_rop_recovery_|taskId=CELLTRACE_file_collection_|nodeFdn=NetworkElement=EricssonAthloneNode2|ropStartTime=1441798200000|ropPeriod=900000|recoverInNextRop=true|99b378ec-abc1-48e1-b963-1fd84101c8df|395eb830-4f08-498d-9707-70f3ab39b9ba",
                        "EBM_file_collection_|nodeFdn=NetworkElement=EricssonAthloneNode1|ropStartTime=1441786500000|ropPeriod=900000|recoverInNextRop=true|ead19932-5bb8-43dc-bbfd-cd6816110b1e",
                        "EBM_file_collection_|nodeFdn=NetworkElement=EricssonAthloneNode2|ropStartTime=1441786500000|ropPeriod=900000|recoverInNextRop=true|40a873de-5dc7-4da5-8673-db3e01a6b212",
                        "EBM_file_collection_|nodeFdn=NetworkElement=EricssonAthloneNode1|ropStartTime=1441798200000|ropPeriod=900000|recoverInNextRop=true|81c6fd4a-1602-42e4-ad23-40ac26a0294a",
                        "EBM_file_collection_|nodeFdn=NetworkElement=EricssonAthloneNode2|ropStartTime=1441798200000|ropPeriod=900000|recoverInNextRop=true|f9506ef8-e8f2-4061-b366-2bee4c71f0b1",
                        "file_collection_single_rop_recovery_|taskId=EBM_file_collection_|nodeFdn=NetworkElement=EricssonAthloneNode1|ropStartTime=1441786500000|ropPeriod=900000|recoverInNextRop=true|99b378ec-abc1-48e1-b963-1fd84101c8df|2e4111df-fa00-45f5-b12a-ac410f84e21f",
                        "file_collection_single_rop_recovery_|taskId=EBM_file_collection_|nodeFdn=NetworkElement=EricssonAthloneNode2|ropStartTime=1441786500000|ropPeriod=900000|recoverInNextRop=true|99b378ec-abc1-48e1-b963-1fd84101c8df|6f6805a9-04df-47cc-a2f7-c46708a7f73f",
                        "file_collection_single_rop_recovery_|taskId=EBM_file_collection_|nodeFdn=NetworkElement=EricssonAthloneNode1|ropStartTime=1441798200000|ropPeriod=900000|recoverInNextRop=true|99b378ec-abc1-48e1-b963-1fd84101c8df|166811d7-dc8c-4e76-a0eb-f08c54a0a41d",
                        "file_collection_single_rop_recovery_|taskId=EBM_file_collection_|nodeFdn=NetworkElement=EricssonAthloneNode2|ropStartTime=1441798200000|ropPeriod=900000|recoverInNextRop=true|99b378ec-abc1-48e1-b963-1fd84101c8df|395eb830-4f08-498d-9707-70f3ab39b9ba"
                };
        for (final String taskId : tasksIds) {
            extendedFileCollectionInstrumentation.fileCollectionTaskStarted(taskId);
        }
        for (final String taskId : tasksIds) {
            extendedFileCollectionInstrumentation.fileCollectionTaskEnded(taskId);
        }
        assertEquals(metricRegistry.timer(STATISTICAL_FILE_COLLECTION_DURATION_PER_ROP).getCount(), 2);
        assertEquals(metricRegistry.timer(STATISTICAL_FILE_COLLECTION_DURATION_PER_NODE).getCount(), 4);
        assertEquals(metricRegistry.timer(CELLTRACE_FILE_COLLECTION_DURATION_PER_ROP).getCount(), 2);
        assertEquals(metricRegistry.timer(CELLTRACE_FILE_COLLECTION_DURATION_PER_NODE).getCount(), 4);
        assertEquals(metricRegistry.timer(EBM_FILE_COLLECTION_DURATION_PER_ROP).getCount(), 2);
        assertEquals(metricRegistry.timer(EBM_FILE_COLLECTION_DURATION_PER_NODE).getCount(), 4);
        assertEquals(metricRegistry.timer(SINGLE_ROP_RECOVERY_STATISTICAL_FILE_COLLECTION_DURATION_PER_ROP).getCount(), 2);
        assertEquals(metricRegistry.timer(SINGLE_ROP_RECOVERY_STATISTICAL_FILE_COLLECTION_DURATION_PER_NODE).getCount(), 4);
        assertEquals(metricRegistry.timer(SINGLE_ROP_RECOVERY_CELLTRACE_FILE_COLLECTION_DURATION_PER_ROP).getCount(), 2);
        assertEquals(metricRegistry.timer(SINGLE_ROP_RECOVERY_CELLTRACE_FILE_COLLECTION_DURATION_PER_NODE).getCount(), 4);
        assertEquals(metricRegistry.timer(SINGLE_ROP_RECOVERY_EBM_FILE_COLLECTION_DURATION_PER_ROP).getCount(), 2);
        assertEquals(metricRegistry.timer(SINGLE_ROP_RECOVERY_EBM_FILE_COLLECTION_DURATION_PER_NODE).getCount(), 4);
    }

    @Test
    public void shouldCountCollectedStatisticalFiles() {
        final String taskId =
                "STATISTICAL_file_collection_|nodeFdn=NetworkElement=EricssonAthloneNode1|ropStartTime=1441786500000|ropPeriod=900000|recoverInNextRop=true|6645e0f6-2105-4cb0-a591-051faef1c552";
        final FileCollectionResult fileCollectionResult = new FileCollectionResult();
        final List<FileCollectionSuccess> fileCollectionSuccess = new ArrayList<>();
        fileCollectionSuccess.add(new FileCollectionSuccess("destName1", 12, 34));
        fileCollectionSuccess.add(new FileCollectionSuccess("destName2", 12, 34));
        fileCollectionResult.setJobId(taskId);
        fileCollectionResult.setFileCollectionSuccess(fileCollectionSuccess);

        extendedFileCollectionInstrumentation.fileCollectionTaskEnded(fileCollectionResult);

        assertEquals(metricRegistry.counter(STATISTICAL_FILE_COLLECTION_COLLECTED_FILES).getCount(), 2);
        assertEquals(metricRegistry.counter(STATISTICAL_FILE_COLLECTION_MISSED_FILES).getCount(), 0);
    }

    @Test
    public void shouldCountMissedStatisticalFiles() {
        final String taskId =
                "STATISTICAL_file_collection_|nodeFdn=NetworkElement=EricssonAthloneNode1|ropStartTime=1441786500000|ropPeriod=900000|recoverInNextRop=true|6645e0f6-2105-4cb0-a591-051faef1c552";
        final FileCollectionResult fileCollectionResult = new FileCollectionResult();
        final List fileCollectionFailure = mock(List.class);
        fileCollectionResult.setJobId(taskId);
        fileCollectionResult.setFileCollectionFailure(fileCollectionFailure);

        when(fileCollectionFailure.size()).thenReturn(1);

        extendedFileCollectionInstrumentation.fileCollectionTaskEnded(fileCollectionResult);

        assertEquals(metricRegistry.counter(STATISTICAL_FILE_COLLECTION_COLLECTED_FILES).getCount(), 0);
        assertEquals(metricRegistry.counter(STATISTICAL_FILE_COLLECTION_MISSED_FILES).getCount(), 1);
    }

    @Test
    public void shouldCountCollectedCellTraceFiles() {
        final String taskId =
                "CELLTRACE_file_collection_|nodeFdn=NetworkElement=EricssonAthloneNode1|ropStartTime=1441786500000|ropPeriod=900000|recoverInNextRop=true|ead19932-5bb8-43dc-bbfd-cd6816110b1e";
        final FileCollectionResult fileCollectionResult = new FileCollectionResult();
        final List<FileCollectionSuccess> fileCollectionSuccess = new ArrayList<>();
        fileCollectionSuccess.add(new FileCollectionSuccess("destName1", 12, 34));
        fileCollectionSuccess.add(new FileCollectionSuccess("destName2", 12, 34));
        fileCollectionResult.setJobId(taskId);
        fileCollectionResult.setFileCollectionSuccess(fileCollectionSuccess);

        extendedFileCollectionInstrumentation.fileCollectionTaskEnded(fileCollectionResult);

        assertEquals(metricRegistry.counter(CELLTRACE_FILE_COLLECTION_COLLECTED_FILES).getCount(), 2);
        assertEquals(metricRegistry.counter(CELLTRACE_FILE_COLLECTION_MISSED_FILES).getCount(), 0);
    }

    @Test
    public void shouldCountMissedCellTraceFiles() {
        final String taskId =
                "CELLTRACE_file_collection_|nodeFdn=NetworkElement=EricssonAthloneNode1|ropStartTime=1441786500000|ropPeriod=900000|recoverInNextRop=true|ead19932-5bb8-43dc-bbfd-cd6816110b1e";
        final FileCollectionResult fileCollectionResult = new FileCollectionResult();
        final List fileCollectionFailure = mock(List.class);
        fileCollectionResult.setJobId(taskId);
        fileCollectionResult.setFileCollectionFailure(fileCollectionFailure);

        when(fileCollectionFailure.size()).thenReturn(2);

        extendedFileCollectionInstrumentation.fileCollectionTaskEnded(fileCollectionResult);

        assertEquals(metricRegistry.counter(CELLTRACE_FILE_COLLECTION_COLLECTED_FILES).getCount(), 0);
        assertEquals(metricRegistry.counter(CELLTRACE_FILE_COLLECTION_MISSED_FILES).getCount(), 2);
    }

    @Test
    public void shouldCountCollectedEbmFiles() {
        final String taskId =
                "EBM_file_collection_|nodeFdn=NetworkElement=EricssonAthloneNode1|ropStartTime=1441786500000|ropPeriod=900000|recoverInNextRop=true|ead19932-5bb8-43dc-bbfd-cd6816110b1e";
        final FileCollectionResult fileCollectionResult = new FileCollectionResult();
        final List<FileCollectionSuccess> fileCollectionSuccess = new ArrayList<>();
        fileCollectionSuccess.add(new FileCollectionSuccess("destName1", 12, 34));
        fileCollectionSuccess.add(new FileCollectionSuccess("destName2", 12, 34));
        fileCollectionResult.setJobId(taskId);
        fileCollectionResult.setFileCollectionSuccess(fileCollectionSuccess);

        extendedFileCollectionInstrumentation.fileCollectionTaskEnded(fileCollectionResult);

        assertEquals(metricRegistry.counter(EBM_FILE_COLLECTION_MISSED_FILES).getCount(), 0);
    }

    @Test
    public void shouldCountMissedEbmFiles() {
        final String taskId =
                "EBM_file_collection_|nodeFdn=NetworkElement=EricssonAthloneNode1|ropStartTime=1441786500000|ropPeriod=900000|recoverInNextRop=true|ead19932-5bb8-43dc-bbfd-cd6816110b1e";
        final FileCollectionResult fileCollectionResult = new FileCollectionResult();
        final List fileCollectionFailure = mock(List.class);
        fileCollectionResult.setJobId(taskId);
        fileCollectionResult.setFileCollectionFailure(fileCollectionFailure);

        when(fileCollectionFailure.size()).thenReturn(2);

        extendedFileCollectionInstrumentation.fileCollectionTaskEnded(fileCollectionResult);

        assertEquals(metricRegistry.counter(EBM_FILE_COLLECTION_COLLECTED_FILES).getCount(), 0);
        assertEquals(metricRegistry.counter(EBM_FILE_COLLECTION_MISSED_FILES).getCount(), 2);
    }

    @Test
    public void shouldCountCollectedSingleRopRecoveryStatisticalFiles() {
        final String taskId =
                "file_collection_single_rop_recovery_|taskId=STATISTICAL_file_collection_|nodeFdn=NetworkElement=EricssonAthloneNode1|ropStartTime=1441786500000|ropPeriod=900000|recoverInNextRop=true|99b378ec-abc1-48e1-b963-1fd84101c8df|141e81bb-4126-43fc-a813-4d24e74381b8";
        final FileCollectionResult fileCollectionResult = new FileCollectionResult();
        final List<FileCollectionSuccess> fileCollectionSuccess = new ArrayList<>();
        fileCollectionSuccess.add(new FileCollectionSuccess("destName1", 12, 34));
        fileCollectionSuccess.add(new FileCollectionSuccess("destName2", 12, 34));
        fileCollectionResult.setJobId(taskId);
        fileCollectionResult.setFileCollectionSuccess(fileCollectionSuccess);

        extendedFileCollectionInstrumentation.fileCollectionTaskEnded(fileCollectionResult);

        assertEquals(metricRegistry.counter(SINGLE_ROP_RECOVERY_STATISTICAL_FILE_COLLECTION_COLLECTED_FILES).getCount(), 2);
        assertEquals(metricRegistry.counter(SINGLE_ROP_RECOVERY_STATISTICAL_FILE_COLLECTION_MISSED_FILES).getCount(), 0);
    }

    @Test
    public void shouldCountMissedSingleRopRecoveryStatisticalFiles() {
        final String taskId =
                "file_collection_single_rop_recovery_|taskId=STATISTICAL_file_collection_|nodeFdn=NetworkElement=EricssonAthloneNode1|ropStartTime=1441786500000|ropPeriod=900000|recoverInNextRop=true|99b378ec-abc1-48e1-b963-1fd84101c8df|141e81bb-4126-43fc-a813-4d24e74381b8";
        final FileCollectionResult fileCollectionResult = new FileCollectionResult();
        final List fileCollectionFailure = mock(List.class);
        fileCollectionResult.setJobId(taskId);
        fileCollectionResult.setFileCollectionFailure(fileCollectionFailure);

        when(fileCollectionFailure.size()).thenReturn(1);

        extendedFileCollectionInstrumentation.fileCollectionTaskEnded(fileCollectionResult);

        assertEquals(metricRegistry.counter(SINGLE_ROP_RECOVERY_STATISTICAL_FILE_COLLECTION_COLLECTED_FILES).getCount(), 0);
        assertEquals(metricRegistry.counter(SINGLE_ROP_RECOVERY_STATISTICAL_FILE_COLLECTION_MISSED_FILES).getCount(), 1);
    }

    @Test
    public void shouldCountSingleRopRecoveryCollectedCelltraceFiles() {
        final String taskId =
                "file_collection_single_rop_recovery_|taskId=CELLTRACE_file_collection_|nodeFdn=NetworkElement=EricssonAthloneNode1|ropStartTime=1441786500000|ropPeriod=900000|recoverInNextRop=true|99b378ec-abc1-48e1-b963-1fd84101c8df|2e4111df-fa00-45f5-b12a-ac410f84e21f";
        final FileCollectionResult fileCollectionResult = new FileCollectionResult();
        final List<FileCollectionSuccess> fileCollectionSuccess = new ArrayList<>();
        fileCollectionSuccess.add(new FileCollectionSuccess("destName1", 12, 34));
        fileCollectionSuccess.add(new FileCollectionSuccess("destName2", 12, 34));
        fileCollectionResult.setJobId(taskId);
        fileCollectionResult.setFileCollectionSuccess(fileCollectionSuccess);

        extendedFileCollectionInstrumentation.fileCollectionTaskEnded(fileCollectionResult);

        assertEquals(metricRegistry.counter(SINGLE_ROP_RECOVERY_CELLTRACE_FILE_COLLECTION_COLLECTED_FILES).getCount(), 2);
        assertEquals(metricRegistry.counter(SINGLE_ROP_RECOVERY_CELLTRACE_FILE_COLLECTION_MISSED_FILES).getCount(), 0);
    }

    @Test
    public void shouldCountMissedSingleRopRecoveryCellTraceFiles() {
        final String taskId =
                "file_collection_single_rop_recovery_|taskId=CELLTRACE_file_collection_|nodeFdn=NetworkElement=EricssonAthloneNode1|ropStartTime=1441786500000|ropPeriod=900000|recoverInNextRop=true|99b378ec-abc1-48e1-b963-1fd84101c8df|2e4111df-fa00-45f5-b12a-ac410f84e21f";
        final FileCollectionResult fileCollectionResult = new FileCollectionResult();
        final List fileCollectionFailure = mock(List.class);
        fileCollectionResult.setJobId(taskId);
        fileCollectionResult.setFileCollectionFailure(fileCollectionFailure);

        when(fileCollectionFailure.size()).thenReturn(2);

        extendedFileCollectionInstrumentation.fileCollectionTaskEnded(fileCollectionResult);

        assertEquals(metricRegistry.counter(SINGLE_ROP_RECOVERY_CELLTRACE_FILE_COLLECTION_COLLECTED_FILES).getCount(), 0);
        assertEquals(metricRegistry.counter(SINGLE_ROP_RECOVERY_CELLTRACE_FILE_COLLECTION_MISSED_FILES).getCount(), 2);
    }

    @Test
    public void shouldCountSingleRopRecoveryCollectedEbmFiles() {
        final String taskId =
                "file_collection_single_rop_recovery_|taskId=EBM_file_collection_|nodeFdn=NetworkElement=EricssonAthloneNode1|ropStartTime=1441786500000|ropPeriod=900000|recoverInNextRop=true|99b378ec-abc1-48e1-b963-1fd84101c8df|2e4111df-fa00-45f5-b12a-ac410f84e21f";
        final FileCollectionResult fileCollectionResult = new FileCollectionResult();
        final List<FileCollectionSuccess> fileCollectionSuccess = new ArrayList<>();
        fileCollectionSuccess.add(new FileCollectionSuccess("destName1", 12, 34));
        fileCollectionSuccess.add(new FileCollectionSuccess("destName2", 12, 34));
        fileCollectionResult.setJobId(taskId);
        fileCollectionResult.setFileCollectionSuccess(fileCollectionSuccess);

        extendedFileCollectionInstrumentation.fileCollectionTaskEnded(fileCollectionResult);

        assertEquals(metricRegistry.counter(SINGLE_ROP_RECOVERY_EBM_FILE_COLLECTION_COLLECTED_FILES).getCount(), 2);
        assertEquals(metricRegistry.counter(SINGLE_ROP_RECOVERY_EBM_FILE_COLLECTION_MISSED_FILES).getCount(), 0);
    }

    @Test
    public void shouldCountMissedSingleRopRecoveryEbmFiles() {
        final String taskId =
                "file_collection_single_rop_recovery_|taskId=EBM_file_collection_|nodeFdn=NetworkElement=EricssonAthloneNode1|ropStartTime=1441786500000|ropPeriod=900000|recoverInNextRop=true|99b378ec-abc1-48e1-b963-1fd84101c8df|2e4111df-fa00-45f5-b12a-ac410f84e21f";
        final FileCollectionResult fileCollectionResult = new FileCollectionResult();
        final List fileCollectionFailure = mock(List.class);
        fileCollectionResult.setJobId(taskId);
        fileCollectionResult.setFileCollectionFailure(fileCollectionFailure);

        when(fileCollectionFailure.size()).thenReturn(2);

        extendedFileCollectionInstrumentation.fileCollectionTaskEnded(fileCollectionResult);

        assertEquals(metricRegistry.counter(SINGLE_ROP_RECOVERY_EBM_FILE_COLLECTION_COLLECTED_FILES).getCount(), 0);
        assertEquals(metricRegistry.counter(SINGLE_ROP_RECOVERY_EBM_FILE_COLLECTION_MISSED_FILES).getCount(), 2);
    }

    @Test
    public void shouldMeasureScheduledRecoveryStatisticalFileCollectionTaskDurationPerRopAndPerNode() {
        final int expectedTasksNumber = 4;
        final Set<ProcessRequestVO> processRequests = generateProcessRequests(0, expectedTasksNumber);
        final List<FileCollectionTaskRequest> fileCollectionTaskRequests =
                generateFileCollectionTasks(FileCollectionConstant.FILE_COLLECTION_DELTA_RECOVERY_TASK_ID_PREFIX, processRequests);
        final List<FileCollectionResult> fileCollectionResults = generateFileCollectionResults(fileCollectionTaskRequests);

        extendedFileCollectionInstrumentation.scheduledFileRecoveryTaskGroupStarted(processRequests);
        for (final FileCollectionTaskRequest fileCollectionTaskRequest : fileCollectionTaskRequests) {
            extendedFileCollectionInstrumentation.scheduledFileRecoveryTaskStarted(fileCollectionTaskRequest);
        }
        for (final FileCollectionResult fileCollectionResult : fileCollectionResults) {
            extendedFileCollectionInstrumentation.fileCollectionTaskEnded(fileCollectionResult);
        }

        assertEquals(metricRegistry.timer(SCHEDULED_RECOVERY_STATISTICAL_FILE_COLLECTION_DURATION_PER_ROP).getCount(), 1);
        assertEquals(metricRegistry.timer(SCHEDULED_RECOVERY_STATISTICAL_FILE_COLLECTION_DURATION_PER_NODE).getCount(), expectedTasksNumber);
    }

    @Test
    public void shouldMeasureScheduledRecoveryStatisticalFileCollectionTaskDurationPerRopAndPerNodeInCaseOfMultipleTaskGroups() {
        final int expectedTasksNumber = 4;
        final Set<ProcessRequestVO> processRequestsForTaskGroupOne = generateProcessRequests(0, expectedTasksNumber);
        final List<FileCollectionTaskRequest> fileCollectionTasksForTaskRequestGroupOne =
                generateFileCollectionTasks(FileCollectionConstant.FILE_COLLECTION_DELTA_RECOVERY_TASK_ID_PREFIX, processRequestsForTaskGroupOne);
        final List<FileCollectionResult> fileCollectionResultsForTaskGroupOne =
                generateFileCollectionResults(fileCollectionTasksForTaskRequestGroupOne);

        final Set<ProcessRequestVO> processRequestsForTaskGroupTwo = generateProcessRequests(expectedTasksNumber, expectedTasksNumber * 2);
        final List<FileCollectionTaskRequest> fileCollectionTasksForTaskRequestGroupTwo =
                generateFileCollectionTasks(FileCollectionConstant.FILE_COLLECTION_DELTA_RECOVERY_TASK_ID_PREFIX, processRequestsForTaskGroupTwo);
        final List<FileCollectionResult> fileCollectionResultsForTaskGroupTwo =
                generateFileCollectionResults(fileCollectionTasksForTaskRequestGroupTwo);

        // first task group started
        extendedFileCollectionInstrumentation.scheduledFileRecoveryTaskGroupStarted(processRequestsForTaskGroupOne);
        for (final FileCollectionTaskRequest fileCollectionTaskRequest : fileCollectionTasksForTaskRequestGroupOne) {
            extendedFileCollectionInstrumentation.scheduledFileRecoveryTaskStarted(fileCollectionTaskRequest);
        }
        for (int i = 0; i < expectedTasksNumber - 1; i++) {
            extendedFileCollectionInstrumentation.fileCollectionTaskEnded(fileCollectionResultsForTaskGroupOne.get(i));
        }

        // second task group started
        extendedFileCollectionInstrumentation.scheduledFileRecoveryTaskGroupStarted(processRequestsForTaskGroupTwo);
        for (final FileCollectionTaskRequest fileCollectionTaskRequest : fileCollectionTasksForTaskRequestGroupTwo) {
            extendedFileCollectionInstrumentation.scheduledFileRecoveryTaskStarted(fileCollectionTaskRequest);
        }

        // second task group ended
        for (final FileCollectionResult fileCollectionResult : fileCollectionResultsForTaskGroupTwo) {
            extendedFileCollectionInstrumentation.fileCollectionTaskEnded(fileCollectionResult);
        }

        assertEquals(metricRegistry.timer(SCHEDULED_RECOVERY_STATISTICAL_FILE_COLLECTION_DURATION_PER_ROP).getCount(), 1);
        assertEquals(metricRegistry.timer(SCHEDULED_RECOVERY_STATISTICAL_FILE_COLLECTION_DURATION_PER_NODE).getCount(), expectedTasksNumber * 2 - 1);

        // first task group ended
        extendedFileCollectionInstrumentation.fileCollectionTaskEnded(fileCollectionResultsForTaskGroupOne.get(expectedTasksNumber - 1));

        assertEquals(metricRegistry.timer(SCHEDULED_RECOVERY_STATISTICAL_FILE_COLLECTION_DURATION_PER_ROP).getCount(), 2);
        assertEquals(metricRegistry.timer(SCHEDULED_RECOVERY_STATISTICAL_FILE_COLLECTION_DURATION_PER_NODE).getCount(), expectedTasksNumber * 2);
    }

    private Set<ProcessRequestVO> generateProcessRequests(final int from, final int to) {
        final Set<ProcessRequestVO> processRequests = new LinkedHashSet<>();
        for (int i = from; i < to; i++) {
            final String nodeAddress = "192.168.1." + i;
            final ProcessRequestVO processRequestVO = new ProcessRequestVO.ProcessRequestVOBuilder(nodeAddress, 0, ProcessType.STATS.name()).build();
            processRequests.add(processRequestVO);
        }
        return processRequests;
    }

    private List<FileCollectionTaskRequest> generateFileCollectionTasks(final String taskIdPrefix, final Set<ProcessRequestVO> processRequests) {
        final List<FileCollectionTaskRequest> fileCollectionTaskRequests = new ArrayList<>();
        for (final ProcessRequestVO processRequest : processRequests) {
            final String taskId = taskIdPrefix + processRequest.getNodeAddress();
            final FileCollectionTaskRequest fileCollectionTaskRequest = new FileCollectionTaskRequest();
            fileCollectionTaskRequest.setJobId(taskId);
            fileCollectionTaskRequest.setNodeAddress(processRequest.getNodeAddress());
            fileCollectionTaskRequest.setRecoverInNextRop(false);
            fileCollectionTaskRequests.add(fileCollectionTaskRequest);
        }
        return fileCollectionTaskRequests;
    }

    private List<FileCollectionResult> generateFileCollectionResults(final List<FileCollectionTaskRequest> fileCollectionTaskRequests) {
        final List<FileCollectionResult> fileCollectionResults = new ArrayList<>();
        for (final FileCollectionTaskRequest fileCollectionTaskRequest : fileCollectionTaskRequests) {
            final FileCollectionResult fileCollectionResult = new FileCollectionResult();
            fileCollectionResult.setJobId(fileCollectionTaskRequest.getJobId());
            fileCollectionResult.setNodeAddress(fileCollectionTaskRequest.getNodeAddress());
            fileCollectionResult.setRecoverInNextRop(false);
            fileCollectionResults.add(fileCollectionResult);
        }
        return fileCollectionResults;
    }

    @Test
    public void shouldCountScheduledRecoveryCollectedStatisticalFiles() {
        final String taskId = FileCollectionConstant.FILE_COLLECTION_DELTA_RECOVERY_TASK_ID_PREFIX + "test";
        final FileCollectionResult fileCollectionResult = new FileCollectionResult();
        final List<FileCollectionSuccess> fileCollectionSuccess = new ArrayList<>();
        fileCollectionSuccess.add(new FileCollectionSuccess("destName1", 12, 34));
        fileCollectionSuccess.add(new FileCollectionSuccess("destName2", 12, 34));
        fileCollectionResult.setJobId(taskId);
        fileCollectionResult.setFileCollectionSuccess(fileCollectionSuccess);

        extendedFileCollectionInstrumentation.fileCollectionTaskEnded(fileCollectionResult);

        assertEquals(metricRegistry.counter(SCHEDULED_RECOVERY_STATISTICAL_FILE_COLLECTION_COLLECTED_FILES).getCount(), 2);
        assertEquals(metricRegistry.counter(SCHEDULED_RECOVERY_STATISTICAL_FILE_COLLECTION_MISSED_FILES).getCount(), 0);
    }

    @Test
    public void shouldCountScheduledRecoveryFailedStatisticalFiles() {
        final String taskId = FileCollectionConstant.FILE_COLLECTION_DELTA_RECOVERY_TASK_ID_PREFIX + "test";
        final FileCollectionResult fileCollectionResult = new FileCollectionResult();
        final List fileCollectionFailure = mock(List.class);
        fileCollectionResult.setJobId(taskId);
        fileCollectionResult.setFileCollectionFailure(fileCollectionFailure);

        when(fileCollectionFailure.size()).thenReturn(2);

        extendedFileCollectionInstrumentation.fileCollectionTaskEnded(fileCollectionResult);

        assertEquals(metricRegistry.counter(SCHEDULED_RECOVERY_STATISTICAL_FILE_COLLECTION_COLLECTED_FILES).getCount(), 0);
        assertEquals(metricRegistry.counter(SCHEDULED_RECOVERY_STATISTICAL_FILE_COLLECTION_MISSED_FILES).getCount(), 2);
    }

    @Test
    public void shouldMeasureStartupRecoveryStatisticalFileCollectionTaskDurationPerRopAndPerNode() {
        final int expectedTasksNumber = 4;
        final Set<ProcessRequestVO> processRequests = generateProcessRequests(0, expectedTasksNumber);
        final List<FileCollectionTaskRequest> fileCollectionTaskRequests =
                generateFileCollectionTasks(FileCollectionConstant.FILE_COLLECTION_RECOVERY_ON_STARTUP_TASK_ID_PREFIX, processRequests);
        final List<FileCollectionResult> fileCollectionResults = generateFileCollectionResults(fileCollectionTaskRequests);

        extendedFileCollectionInstrumentation.startupFileRecoveryTaskGroupStarted(processRequests);
        for (final FileCollectionTaskRequest fileCollectionTaskRequest : fileCollectionTaskRequests) {
            extendedFileCollectionInstrumentation.scheduledFileRecoveryTaskStarted(fileCollectionTaskRequest);
        }
        for (final FileCollectionResult fileCollectionResult : fileCollectionResults) {
            extendedFileCollectionInstrumentation.fileCollectionTaskEnded(fileCollectionResult);
        }

        assertEquals(metricRegistry.timer(STARTUP_RECOVERY_STATISTICAL_FILE_COLLECTION_DURATION).getCount(), 1);
        assertEquals(metricRegistry.timer(STARTUP_RECOVERY_STATISTICAL_FILE_COLLECTION_DURATION_PER_NODE).getCount(), expectedTasksNumber);
    }

    @Test
    public void shouldCountStartupRecoveryCollectedStatisticalFiles() {
        final String taskId = FileCollectionConstant.FILE_COLLECTION_RECOVERY_ON_STARTUP_TASK_ID_PREFIX + "test";
        final FileCollectionResult fileCollectionResult = new FileCollectionResult();
        final List<FileCollectionSuccess> fileCollectionSuccess = new ArrayList<>();
        fileCollectionSuccess.add(new FileCollectionSuccess("destName1", 12, 34));
        fileCollectionSuccess.add(new FileCollectionSuccess("destName2", 12, 34));
        fileCollectionResult.setJobId(taskId);
        fileCollectionResult.setFileCollectionSuccess(fileCollectionSuccess);

        extendedFileCollectionInstrumentation.fileCollectionTaskEnded(fileCollectionResult);

        assertEquals(metricRegistry.counter(STARTUP_RECOVERY_STATISTICAL_FILE_COLLECTION_COLLECTED_FILES).getCount(), 2);
        assertEquals(metricRegistry.counter(STARTUP_RECOVERY_STATISTICAL_FILE_COLLECTION_MISSED_FILES).getCount(), 0);
    }

    @Test
    public void shouldCountStartupRecoveryFailedStatisticalFiles() {
        final String taskId = FileCollectionConstant.FILE_COLLECTION_RECOVERY_ON_STARTUP_TASK_ID_PREFIX + "test";
        final FileCollectionResult fileCollectionResult = new FileCollectionResult();
        final List fileCollectionFailure = mock(List.class);
        fileCollectionResult.setJobId(taskId);
        fileCollectionResult.setFileCollectionFailure(fileCollectionFailure);

        when(fileCollectionFailure.size()).thenReturn(2);

        extendedFileCollectionInstrumentation.fileCollectionTaskEnded(fileCollectionResult);

        assertEquals(metricRegistry.counter(STARTUP_RECOVERY_STATISTICAL_FILE_COLLECTION_COLLECTED_FILES).getCount(), 0);
        assertEquals(metricRegistry.counter(STARTUP_RECOVERY_STATISTICAL_FILE_COLLECTION_MISSED_FILES).getCount(), 2);
    }

}
