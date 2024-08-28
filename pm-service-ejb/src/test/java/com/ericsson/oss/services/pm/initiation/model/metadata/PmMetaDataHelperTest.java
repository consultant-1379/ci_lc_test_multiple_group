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

package com.ericsson.oss.services.pm.initiation.model.metadata;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import java.util.Collection;
import java.util.HashSet;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.internal.util.reflection.Whitebox;
import org.slf4j.Logger;

import com.ericsson.oss.itpf.modeling.modelservice.typed.TypedModelAccess;
import com.ericsson.oss.itpf.modeling.modelservice.typed.core.target.TargetTypeInformation;
import com.ericsson.oss.itpf.modeling.modelservice.typed.core.target.TargetTypeVersionInformation;
import com.ericsson.oss.services.pm.initiation.model.utils.PmMetaDataConstants;

public class PmMetaDataHelperTest {

    private static final String NE_TYPE_ERBS = "ERBS";
    private static final String OSS_MODEL_IDENTITY_ERBS = "4140-311-231";
    private static final String NE_TYPE_SGSNMME = "SGSN-MME";
    private static final String OSS_MODEL_IDENTITY_SGSNMME = "2085-555-613";
    private final Collection<String> modelUrns = new HashSet<>();
    private final Collection<String> modelurnsSgsnmme = new HashSet<>();
    private PmMetaDataHelper metaDataHelper;
    @Mock
    private Logger logger;
    @Mock
    private TypedModelAccess typedModelAccess;

    TargetTypeInformation targetTypeInformation;

    TargetTypeVersionInformation targetTypeVersionInformation;

    @Before
    public void setUp() {
        metaDataHelper = spy(new PmMetaDataHelper());
        MockitoAnnotations.initMocks(this);
        Whitebox.setInternalState(metaDataHelper, "log", logger);
        Whitebox.setInternalState(metaDataHelper, "typedModelAccess", typedModelAccess);

        modelUrns.add("/pfm_measurement/ERBS/NE-defined/387901871689.140813204546.374877253817");
        modelUrns.add("/pfm_measurement/CPP/NE-defined/387901871689.140813204546.374877253817");
        modelUrns.add("/pfm_event/ERBS/NE-defined/387901871689.140813204546.374877253817");
        modelUrns.add("/cfm_miminfo/ERBS/NE-defined/387901871689.140813204546.374877253817");

        targetTypeInformation = mock(TargetTypeInformation.class);
        targetTypeVersionInformation = mock(TargetTypeVersionInformation.class);
        when(typedModelAccess.getModelInformation(TargetTypeInformation.class)).thenReturn(targetTypeInformation);
        when(targetTypeInformation.getTargetTypeVersionInformation(TargetTypeInformation.CATEGORY_NODE, NE_TYPE_ERBS))
                .thenReturn(targetTypeVersionInformation);
        when(targetTypeVersionInformation.getMostAppropriateTmiForTarget(OSS_MODEL_IDENTITY_ERBS, "/pfm_measurement/*/*/*"))
                .thenReturn(OSS_MODEL_IDENTITY_ERBS);
        when(targetTypeVersionInformation.getSupportedModels(OSS_MODEL_IDENTITY_ERBS)).thenReturn(modelUrns);

        modelurnsSgsnmme.add("/pfm_measurement/SGSN-MME/NE-defined/86747392859.533278520398.208765005057");
        modelurnsSgsnmme.add("/pfm_measurement/SGSN-MME/NE-defined-EBS/461546506116.270735945586.235228827690");
        when(targetTypeInformation.getTargetTypeVersionInformation(TargetTypeInformation.CATEGORY_NODE, NE_TYPE_SGSNMME))
                .thenReturn(targetTypeVersionInformation);
        when(targetTypeVersionInformation.getMostAppropriateTmiForTarget(OSS_MODEL_IDENTITY_SGSNMME, "/pfm_measurement/*/*/*"))
                .thenReturn(OSS_MODEL_IDENTITY_SGSNMME);
        when(targetTypeVersionInformation.getSupportedModels(OSS_MODEL_IDENTITY_SGSNMME)).thenReturn(modelurnsSgsnmme);
    }

    @Test
    public void shouldGetMetaDataUrnsForPfmMeasurementFromModelServiceForTheGivenOssModelIdentity() {

        final Collection<String> expectedModelUrns = new HashSet<>();
        expectedModelUrns.add("/pfm_measurement/ERBS/NE-defined/387901871689.140813204546.374877253817");
        expectedModelUrns.add("/pfm_measurement/CPP/NE-defined/387901871689.140813204546.374877253817");

        Collection<String> actualModelUrns = metaDataHelper.getMetaDataUrnsFromModelService(NE_TYPE_ERBS, OSS_MODEL_IDENTITY_ERBS,
                PmMetaDataConstants.PFM_MEASUREMENT_PATTERN);

        Assert.assertEquals(actualModelUrns, expectedModelUrns);

        actualModelUrns = metaDataHelper.getMetaDataUrnsFromModelService(NE_TYPE_ERBS, OSS_MODEL_IDENTITY_ERBS,
                PmMetaDataConstants.PFM_MEASUREMENT_PATTERN + "(.*)");

        Assert.assertEquals(actualModelUrns, expectedModelUrns);
    }

    @Test
    public void shouldGetNEDefinedOnlyMetaDdataUrnsForPfmMeasurementFromModelServiceForTheGivenOssModelIdentity() {

        final Collection<String> expectedModelUrns = new HashSet<>();
        expectedModelUrns.add("/pfm_measurement/SGSN-MME/NE-defined/86747392859.533278520398.208765005057");
        when(targetTypeVersionInformation.getMostAppropriateTmiForTarget(OSS_MODEL_IDENTITY_SGSNMME, "/pfm_measurement/*/NE-defined/*"))
                .thenReturn(OSS_MODEL_IDENTITY_SGSNMME);

        final Collection<String> actualModelUrns = metaDataHelper.getMetaDataUrnsFromModelService(NE_TYPE_SGSNMME, OSS_MODEL_IDENTITY_SGSNMME,
                PmMetaDataConstants.PFM_MEASUREMENT_PATTERN + "(.*)" + PmMetaDataConstants.NE_DEFINED_PATTERN);

        Assert.assertEquals(actualModelUrns, expectedModelUrns);
    }

    @Test
    public void shouldGetNEDefinedEBSOnlyMetaDataUrnsForPfmMeasurementFromModelServiceForTheGivenOssModelIdentity() {

        final Collection<String> expectedModelUrns = new HashSet<>();
        expectedModelUrns.add("/pfm_measurement/SGSN-MME/NE-defined-EBS/461546506116.270735945586.235228827690");
        when(targetTypeVersionInformation.getMostAppropriateTmiForTarget(OSS_MODEL_IDENTITY_SGSNMME, "/pfm_measurement/*/NE-defined-EBS/*"))
                .thenReturn(OSS_MODEL_IDENTITY_SGSNMME);

        final Collection<String> actualModelUrns = metaDataHelper.getMetaDataUrnsFromModelService(NE_TYPE_SGSNMME, OSS_MODEL_IDENTITY_SGSNMME,
                PmMetaDataConstants.PFM_MEASUREMENT_PATTERN + "(.*)" + PmMetaDataConstants.OSS_DEFINED_PATTERN);

        Assert.assertEquals(actualModelUrns, expectedModelUrns);
    }

    @Test
    public void shouldGetMetaDataUrnsForPfmEventFromModelServiceForTheGivenOssModelIdentity() {

        final Collection<String> expectedModelUrns = new HashSet<>();
        expectedModelUrns.add("/pfm_event/ERBS/NE-defined/387901871689.140813204546.374877253817");
        when(targetTypeVersionInformation.getMostAppropriateTmiForTarget(OSS_MODEL_IDENTITY_ERBS, "/pfm_event/*/*/*"))
                .thenReturn(OSS_MODEL_IDENTITY_ERBS);
        final Collection<String> actualModelUrns = metaDataHelper.getMetaDataUrnsFromModelService(NE_TYPE_ERBS, OSS_MODEL_IDENTITY_ERBS,
                PmMetaDataConstants.PFM_EVENT_PATTERN);

        Assert.assertEquals(actualModelUrns, expectedModelUrns);
    }

    @Test
    public void shouldGetMetaDataUrnsForCfmMimInfoFromModelServiceForTheGivenOssModelIdentity() {

        final Collection<String> expectedModelUrns = new HashSet<>();
        expectedModelUrns.add("/cfm_miminfo/ERBS/NE-defined/387901871689.140813204546.374877253817");

        final Collection<String> actualModelUrns = metaDataHelper.getMetaDataUrnsFromModelService(NE_TYPE_ERBS, OSS_MODEL_IDENTITY_ERBS,
                PmMetaDataConstants.CFM_MIMINFO_PATTERN);

        Assert.assertEquals(actualModelUrns, expectedModelUrns);
    }

    @Test
    public void shouldGetEmptyUrnsWhenOssModelIdentityDoesNotValidate() {
        when(targetTypeVersionInformation.getSupportedModels(OSS_MODEL_IDENTITY_ERBS)).thenThrow(new IllegalArgumentException());
        final Collection<String> actualModelUrns = metaDataHelper.getMetaDataUrnsFromModelService(NE_TYPE_ERBS, OSS_MODEL_IDENTITY_ERBS,
                PmMetaDataConstants.PFM_EVENT_PATTERN);
        Assert.assertEquals(actualModelUrns.size(), 0);
    }
}
