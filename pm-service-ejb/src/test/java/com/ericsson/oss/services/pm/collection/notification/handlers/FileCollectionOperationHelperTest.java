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
package com.ericsson.oss.services.pm.collection.notification.handlers;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.verify;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.internal.util.reflection.Whitebox;
import org.slf4j.Logger;

import com.ericsson.oss.services.pm.collection.api.ProcessRequestVO;
import com.ericsson.oss.services.pm.collection.schedulers.FileCollectionTaskManagerBean;
import com.ericsson.oss.services.pm.initiation.cache.model.value.ProcessType;

public class FileCollectionOperationHelperTest {

    private FileCollectionOperationHelper objectUnderTest;

    @Mock
    private FileCollectionTaskManagerBean fileCollectionTaskManager;

    @Mock
    private Logger logger;

    @Before
    public void setUp() {
        objectUnderTest = new FileCollectionOperationHelper();
        MockitoAnnotations.initMocks(this);
        Whitebox.setInternalState(objectUnderTest, "logger", logger);
        Whitebox.setInternalState(objectUnderTest, "fileCollectionTaskManager", fileCollectionTaskManager);
    }

    @Test
    public void startFileCollectionShouldCallTaskManagerToStartFileCollection() {
        // When
        objectUnderTest.startFileCollection(60, "NetworkElement=NODE1", ProcessType.STATS.name());
        // Then
        verify(fileCollectionTaskManager, Mockito.times(1)).startFileCollection(any(ProcessRequestVO.class));
    }

    @Test
    public void stopFileCollectionShouldCallTaskManagerToStopFileCollection() {
        // When
        objectUnderTest.stopFileCollection(60, "NetworkElement=NODE1", ProcessType.NORMAL_PRIORITY_CELLTRACE.name());
        // Then
        verify(fileCollectionTaskManager, Mockito.times(1)).stopFileCollection(any(ProcessRequestVO.class));
    }
}