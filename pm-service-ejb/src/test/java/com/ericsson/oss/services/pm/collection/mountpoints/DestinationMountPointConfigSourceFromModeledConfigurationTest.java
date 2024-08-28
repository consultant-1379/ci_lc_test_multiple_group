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

package com.ericsson.oss.services.pm.collection.mountpoints;

import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.internal.util.reflection.Whitebox;
import org.slf4j.Logger;

import com.ericsson.oss.services.pm.initiation.config.listener.ConfigurationChangeListener;
import com.ericsson.oss.services.pm.initiation.config.listener.processors.PmicNfsShareListValueProcessor;
import com.ericsson.oss.services.pm.initiation.config.listener.processors.PmicNfsShareListValueProcessorImpl;

public class DestinationMountPointConfigSourceFromModeledConfigurationTest {

    private static final List<String> MULTIPLE_PMIC_NFS_SHARE_ENTRIES = Collections.unmodifiableList(Arrays.asList("/ericsson/pmic1/", "/ericsson/pmic2/", "/ericsson/pmic3/",
            "/ericsson/pmic4/", "/ericsson/pmic5/"));

    private static final List<String> FOR_TRANSITION_FOR_MULTIPLE_PMIC_NFS_SHARE_ENTRIES_STATE_A = Collections.unmodifiableList(Arrays.asList("/ericsson/pmic1/changed/stateA/",
            "/ericsson/pmic2/changed/stateA/", "/ericsson/pmic3/changed/stateA/"));

    private static final List<String> FOR_TRANSITION_FOR_MULTIPLE_PMIC_NFS_SHARE_ENTRIES_STATE_B = Collections.unmodifiableList(Arrays.asList("/ericsson/pmic1/changed/stateB/",
            "/ericsson/pmic2/changed/stateB/"));

    @Mock
    private Logger logger;

    @Mock
    private ConfigurationChangeListener configurationChangeListener;

    private DestinationMountPointConfigSourceFromModeledConfiguration objectUnderTest;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        final PmicNfsShareListValueProcessor pmicNfsShareListValueProcessor = new PmicNfsShareListValueProcessorImpl();
        objectUnderTest = new DestinationMountPointConfigSourceFromModeledConfiguration();
        Whitebox.setInternalState(objectUnderTest, "logger", logger);
        Whitebox.setInternalState(objectUnderTest, "pmicNfsShareListValueProcessor", pmicNfsShareListValueProcessor);
        Whitebox.setInternalState(objectUnderTest, "configurationChangeListener", configurationChangeListener);
    }

    @Test(expected = IllegalArgumentException.class)
    public void getAvailableFileCollectionMountPointsWhenPmicNfsShareListIsSetToNull() {
        when(configurationChangeListener.getPmicNfsShareList()).thenReturn(null);
        objectUnderTest.getAvailableFileCollectionMountPoints();
    }

    @Test(expected = IllegalArgumentException.class)
    public void getAvailableFileCollectionMountPointsWhenPmicNfsShareListIsSetToEmptyString() {
        when(configurationChangeListener.getPmicNfsShareList()).thenReturn("");
        objectUnderTest.getAvailableFileCollectionMountPoints();

    }

    @Test
    public void getAvailableFileCollectionMountPointsWhenPmicNfsShareListIsSetWithSingleEntry() {
        when(configurationChangeListener.getPmicNfsShareList()).thenReturn(MULTIPLE_PMIC_NFS_SHARE_ENTRIES.get(0));
        final List<String> mountPointList = objectUnderTest.getAvailableFileCollectionMountPoints();

        assertEquals(mountPointList.size(), 1);
        assertEquals(mountPointList.get(0), MULTIPLE_PMIC_NFS_SHARE_ENTRIES.get(0));
    }

    @Test
    public void getAvailableFileCollectionMountPointsWhenPmicNfsShareListIsSetWithMultipleEntries() {
        when(configurationChangeListener.getPmicNfsShareList()).thenReturn(concatMultipleEntries(MULTIPLE_PMIC_NFS_SHARE_ENTRIES));
        final List<String> mountPointList = objectUnderTest.getAvailableFileCollectionMountPoints();

        // Logic has been changed to provide all the mount points
        Assert.assertArrayEquals(MULTIPLE_PMIC_NFS_SHARE_ENTRIES.toArray(), mountPointList.toArray());
    }

    @Test
    public void reloadAndGetAvailableFileCollectionMountPointsWithMultipleTransitionsInPmicNfsShareList() {
        when(configurationChangeListener.getPmicNfsShareList()).thenReturn(concatMultipleEntries(MULTIPLE_PMIC_NFS_SHARE_ENTRIES));

        // First time, all entries will be provided
        List<String> mountPointList = objectUnderTest.getAvailableFileCollectionMountPoints();
        Assert.assertArrayEquals(MULTIPLE_PMIC_NFS_SHARE_ENTRIES.toArray(), mountPointList.toArray());

        // First change, reset values and trigger change from listener
        when(configurationChangeListener.getPmicNfsShareList()).thenReturn(
                concatMultipleEntries(FOR_TRANSITION_FOR_MULTIPLE_PMIC_NFS_SHARE_ENTRIES_STATE_A));
        objectUnderTest.reload();

        mountPointList = objectUnderTest.getAvailableFileCollectionMountPoints();

        Assert.assertArrayEquals(FOR_TRANSITION_FOR_MULTIPLE_PMIC_NFS_SHARE_ENTRIES_STATE_A.toArray(), mountPointList.toArray());
        // Second change, reset values and trigger change from listener
        when(configurationChangeListener.getPmicNfsShareList()).thenReturn(
                concatMultipleEntries(FOR_TRANSITION_FOR_MULTIPLE_PMIC_NFS_SHARE_ENTRIES_STATE_B));
        objectUnderTest.reload();

        mountPointList = objectUnderTest.getAvailableFileCollectionMountPoints();
        Assert.assertArrayEquals(FOR_TRANSITION_FOR_MULTIPLE_PMIC_NFS_SHARE_ENTRIES_STATE_B.toArray(), mountPointList.toArray());
    }

    private String concatMultipleEntries(final Collection<String> entries) {
        final StringBuilder strBuilder = new StringBuilder();
        String separator = "";
        for (final String entry : entries) {
            strBuilder.append(separator);
            strBuilder.append(entry);
            separator = ",";
        }
        return strBuilder.toString();
    }

}
