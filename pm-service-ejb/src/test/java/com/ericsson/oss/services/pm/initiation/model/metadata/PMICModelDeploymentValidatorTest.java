/*------------------------------------------------------------------------------
 *******************************************************************************
 * COPYRIGHT Ericsson 2017
 *
 * The copyright to the computer program(s) herein is the property of
 * Ericsson Inc. The programs may be used and/or copied only with written
 * permission from Ericsson Inc. or in accordance with the terms and
 * conditions stipulated in the agreement/contract under which the
 * program(s) have been supplied.
 *******************************************************************************
 *----------------------------------------------------------------------------*/
package com.ericsson.oss.services.pm.initiation.model.metadata;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertTrue;
import static org.testng.AssertJUnit.assertEquals;
import static org.testng.AssertJUnit.fail;

import static com.ericsson.oss.services.pm.common.constants.PmCapabilityModelConstants.COUNTER_EVENTS_VALIDATION_APPLICABLE;
import static com.ericsson.oss.services.pm.common.constants.PmCapabilityModelConstants.STATISTICAL_SUBSCRIPTIONATTRIBUTES;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ericsson.oss.itpf.modeling.common.info.ModelInfo;
import com.ericsson.oss.itpf.modeling.modelservice.meta.ModelMetaInformation;
import com.ericsson.oss.itpf.modeling.modelservice.typed.core.target.TargetTypeInformation;
import com.ericsson.oss.pmic.dto.NodeTypeAndVersion;
import com.ericsson.oss.services.pm.initiation.model.utils.PmMetaDataConstants;
import com.ericsson.oss.services.pm.modelservice.PmCapabilityModelService;
import com.ericsson.oss.services.pm.services.exception.ValidationException;
import com.ericsson.services.pm.initiation.restful.api.MetaDataType;

public class PMICModelDeploymentValidatorTest {

    private final Collection<String> modelUrns = new HashSet<>();
    @InjectMocks
    private PMICModelDeploymentValidator modelValidator = new PMICModelDeploymentValidator();
    @Mock
    private PmMetaDataHelper metaDataHelper;
    @Mock
    private PmCapabilityModelService capabilityModelService;
    @Mock
    private Logger logger = LoggerFactory.getLogger(this.getClass());

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);
        modelUrns.add("/pfm_event/ERBS/NE-defined/387901871689.140813204546.374877253817");
    }

    @Test
    public void itShouldNotThrowExceptionWhenNodeTypeAndVersionAreValid() throws ValidationException {
        // Given
        final Set<NodeTypeAndVersion> nodeTypeVersionSet = new HashSet<>();
        final List<String> technologyDomainList = Collections.singletonList("EPS");
        final NodeTypeAndVersion nodetype1 = new NodeTypeAndVersion("ERBS", "5.1.120", technologyDomainList);
        final NodeTypeAndVersion nodetype2 = new NodeTypeAndVersion("ERBS", "3.1.72", technologyDomainList);
        nodeTypeVersionSet.add(nodetype1);
        nodeTypeVersionSet.add(nodetype2);

        when(metaDataHelper.getMetaDataPattern(MetaDataType.COUNTERS)).thenReturn(PmMetaDataConstants.PFM_MEASUREMENT_PATTERN);
        when(metaDataHelper.getMetaDataUrnsFromModelService("ERBS", "5.1.120", PmMetaDataConstants.PFM_MEASUREMENT_PATTERN)).thenReturn(modelUrns);
        when(metaDataHelper.getMetaDataUrnsFromModelService("ERBS", "3.1.72", PmMetaDataConstants.PFM_MEASUREMENT_PATTERN)).thenReturn(modelUrns);
        when(capabilityModelService.getCapabilityValue(TargetTypeInformation.CATEGORY_NODE, "ERBS", STATISTICAL_SUBSCRIPTIONATTRIBUTES, COUNTER_EVENTS_VALIDATION_APPLICABLE, "*"))
                .thenReturn(true);
        modelValidator.modelDeploymentStatusValidator(nodeTypeVersionSet, MetaDataType.COUNTERS);

    }

    @Test
    public void itShouldThrowExceptionWhenNodeVersionAreEmpty() {
        // Given
        final String expectedOutput = "Node type & version check failed due to : Empty model version identified.";

        final Set<NodeTypeAndVersion> nodeTypeVersionSet = new HashSet<>();
        final List<String> technologyDomainList = Collections.singletonList("EPS");
        final NodeTypeAndVersion nodetype1 = new NodeTypeAndVersion("ERBS", "", technologyDomainList);
        nodeTypeVersionSet.add(nodetype1);
        when(capabilityModelService.getCapabilityValue(TargetTypeInformation.CATEGORY_NODE, "ERBS", STATISTICAL_SUBSCRIPTIONATTRIBUTES, COUNTER_EVENTS_VALIDATION_APPLICABLE, "*"))
                .thenReturn(true);

        // When
        try {
            modelValidator.modelDeploymentStatusValidator(nodeTypeVersionSet, null);
            fail();
        } catch (final ValidationException e) {
            // Then
            assertEquals(expectedOutput, e.getMessage().replaceAll("\n", ""));
        }
    }

    @Test
    public void itShouldThrowExceptionWhenNodeVersionAreNull() {
        // Given
        final String expectedOutput = "Node type & version check failed due to : Empty model version identified.";

        final Set<NodeTypeAndVersion> nodeTypeVersionSet = new HashSet<>();
        final List<String> technologyDomain = Collections.singletonList("EPS");
        final NodeTypeAndVersion nodetype1 = new NodeTypeAndVersion("ERBS", null, technologyDomain);
        nodeTypeVersionSet.add(nodetype1);
        when(capabilityModelService.getCapabilityValue(TargetTypeInformation.CATEGORY_NODE, "ERBS", STATISTICAL_SUBSCRIPTIONATTRIBUTES, COUNTER_EVENTS_VALIDATION_APPLICABLE, "*"))
                .thenReturn(true);

        // When
        try {
            modelValidator.modelDeploymentStatusValidator(nodeTypeVersionSet, null);
            fail();
        } catch (final ValidationException e) {
            // Then
            assertEquals(expectedOutput, e.getMessage().replaceAll("\n", ""));
        }
    }

    @Test
    public void itShouldThrowExceptionWhenTheMimVersionIsIncorrectlyFormatted() {
        final String expectedOutput = "Node type & version check failed due to : Invalid Version ERBS:5.S.WW.";
        // Given
        final Set<NodeTypeAndVersion> nodeTypeVersionSet = new HashSet<>();
        final List<String> technologyDomainList = Collections.singletonList("EPS");
        final NodeTypeAndVersion nodetype1 = new NodeTypeAndVersion("ERBS", "5.S.WW", technologyDomainList);
        nodeTypeVersionSet.add(nodetype1);
        when(capabilityModelService.getCapabilityValue(TargetTypeInformation.CATEGORY_NODE, "ERBS", STATISTICAL_SUBSCRIPTIONATTRIBUTES, COUNTER_EVENTS_VALIDATION_APPLICABLE, "*"))
                .thenReturn(true);

        // When
        try {
            modelValidator.modelDeploymentStatusValidator(nodeTypeVersionSet, null);
            fail();
        } catch (final ValidationException e) {
            // Then
            assertEquals(expectedOutput, e.getMessage().replaceAll("\n", ""));
        }
    }

    @Test
    public void itShouldGiveCombinedErrorMessageWhenUnsupportedVersionAndIncorrectFormatVersionAndEmptyVersionPresented() {
        // Given
        final String expectedOutputA = "Node type & version check failed due to : Invalid Version ERBS:5.S.WW, ERBS:3.1.20. Empty model version "
                + "identified.";
        final String expectedOutputB = "Node type & version check failed due to : Invalid Version ERBS:3.1.20, ERBS:5.S.WW. Empty model version "
                + "identified.";

        final Set<NodeTypeAndVersion> nodeTypeVersionSet = new HashSet<>();
        final List<String> technologyDomainList = Collections.singletonList("EPS");
        final NodeTypeAndVersion nodetype1 = new NodeTypeAndVersion("ERBS", "5.S.WW", technologyDomainList);
        final NodeTypeAndVersion nodetype2 = new NodeTypeAndVersion("ERBS", "3.1.20", technologyDomainList);
        final NodeTypeAndVersion nodetype3 = new NodeTypeAndVersion("ERBS", "", technologyDomainList);
        nodeTypeVersionSet.add(nodetype1);
        nodeTypeVersionSet.add(nodetype2);
        nodeTypeVersionSet.add(nodetype3);

        final ModelMetaInformation modelMetaInfo = mock(ModelMetaInformation.class);
        when(metaDataHelper.getMetaDataUrnsFromModelService("ERBS", "5.1.20", PmMetaDataConstants.PFM_MEASUREMENT_PATTERN)).thenReturn(modelUrns);
        when(capabilityModelService.getCapabilityValue(TargetTypeInformation.CATEGORY_NODE, "ERBS", STATISTICAL_SUBSCRIPTIONATTRIBUTES, COUNTER_EVENTS_VALIDATION_APPLICABLE, "*"))
                .thenReturn(true);
        when(modelMetaInfo.isModelDeployed(any(ModelInfo.class))).thenReturn(false);

        // When
        try {
            modelValidator.modelDeploymentStatusValidator(nodeTypeVersionSet, null);
            fail();
        } catch (final ValidationException e) {
            // Then
            final String actualOutput = e.getMessage().replaceAll("\n", "");
            assertTrue(expectedOutputA.equals(actualOutput) || expectedOutputB.equals(actualOutput));
        }
    }

    @Test
    public void itShouldGiveCombinedErrorMessageWhenIncorrectFormatAndEmptyString() {
        // Given
        final String expectedOutput = "Node type & version check failed due to : Invalid Version ERBS:5.S.WW. Empty model version identified.";

        final Set<NodeTypeAndVersion> nodeTypeVersionSet = new HashSet<>();
        final List<String> technologyDomainList = Collections.singletonList("EPS");
        final NodeTypeAndVersion nodetype1 = new NodeTypeAndVersion("ERBS", "5.S.WW", technologyDomainList);
        final NodeTypeAndVersion nodetype2 = new NodeTypeAndVersion("ERBS", "", technologyDomainList);
        nodeTypeVersionSet.add(nodetype1);
        nodeTypeVersionSet.add(nodetype2);

        final ModelMetaInformation modelMetaInfo = mock(ModelMetaInformation.class);
        when(modelMetaInfo.isModelDeployed(any(ModelInfo.class))).thenReturn(false);
        when(capabilityModelService.getCapabilityValue(TargetTypeInformation.CATEGORY_NODE, "ERBS", STATISTICAL_SUBSCRIPTIONATTRIBUTES, COUNTER_EVENTS_VALIDATION_APPLICABLE, "*"))
                .thenReturn(true);

        // When
        try {
            modelValidator.modelDeploymentStatusValidator(nodeTypeVersionSet, null);
            fail();
        } catch (final ValidationException e) {
            // Then
            assertEquals(expectedOutput, e.getMessage().replaceAll("\n", ""));
        }
    }

    @Test
    public void itShouldGiveCombinedErrorMessageWhenUnsupportedVersionAndEmptyString() {
        // Given
        final String expectedOutput = "Node type & version check failed due to : Invalid Version ERBS:3.1.20. Empty model version identified.";

        final Set<NodeTypeAndVersion> nodeTypeVersionSet = new HashSet<>();
        final List<String> technologyDomainList = Collections.singletonList("EPS");
        final NodeTypeAndVersion nodetype1 = new NodeTypeAndVersion("ERBS", "3.1.20", technologyDomainList);
        final NodeTypeAndVersion nodetype2 = new NodeTypeAndVersion("ERBS", "", technologyDomainList);
        nodeTypeVersionSet.add(nodetype1);
        nodeTypeVersionSet.add(nodetype2);

        final ModelMetaInformation modelMetaInfo = mock(ModelMetaInformation.class);
        when(metaDataHelper.getMetaDataUrnsFromModelService("ERBS", "5.1.20", PmMetaDataConstants.PFM_MEASUREMENT_PATTERN)).thenReturn(modelUrns);
        when(modelMetaInfo.isModelDeployed(any(ModelInfo.class))).thenReturn(false);
        when(capabilityModelService.getCapabilityValue(TargetTypeInformation.CATEGORY_NODE, "ERBS", STATISTICAL_SUBSCRIPTIONATTRIBUTES, COUNTER_EVENTS_VALIDATION_APPLICABLE, "*"))
                .thenReturn(true);

        // When
        try {
            modelValidator.modelDeploymentStatusValidator(nodeTypeVersionSet, null);
            fail();
        } catch (final ValidationException e) {
            // Then
            assertEquals(expectedOutput, e.getMessage().replaceAll("\n", ""));
        }
    }

    @Test
    public void itShouldGiveErrorMessageWhenUnsupportedNodeVersion() {
        // Given
        final String expectedOutput = "Node type & version check failed due to : Invalid Version ERBS:3.1.20.";

        final Set<NodeTypeAndVersion> nodeTypeVersionSet = new HashSet<>();
        final List<String> technologyDomainList = Collections.singletonList("EPS");
        final NodeTypeAndVersion nodetype1 = new NodeTypeAndVersion("ERBS", "3.1.20", technologyDomainList);
        nodeTypeVersionSet.add(nodetype1);

        final ModelMetaInformation modelMetaInfo = mock(ModelMetaInformation.class);
        when(modelMetaInfo.isModelDeployed(any(ModelInfo.class))).thenReturn(false);
        when(capabilityModelService.getCapabilityValue(TargetTypeInformation.CATEGORY_NODE, "ERBS", STATISTICAL_SUBSCRIPTIONATTRIBUTES, COUNTER_EVENTS_VALIDATION_APPLICABLE, "*"))
                .thenReturn(true);

        // When
        try {
            modelValidator.modelDeploymentStatusValidator(nodeTypeVersionSet, null);
            fail();
        } catch (final ValidationException e) {
            // Then
            assertEquals(expectedOutput, e.getMessage().replaceAll("\n", ""));

        }
    }

    @Test
    public void itShouldNotThrowExceptionWhenMetaValidationIsNotSupported() throws ValidationException {
        // Given
        final Set<NodeTypeAndVersion> nodeTypeVersionSet = new HashSet<>();
        final List<String> technologyDomainList = Collections.singletonList("EPS");
        final NodeTypeAndVersion nodetype1 = new NodeTypeAndVersion("ERBS", "5.1.120", technologyDomainList);
        nodeTypeVersionSet.add(nodetype1);

        when(metaDataHelper.getMetaDataPattern(MetaDataType.COUNTERS)).thenReturn(PmMetaDataConstants.PFM_MEASUREMENT_PATTERN);
        when(metaDataHelper.getMetaDataUrnsFromModelService("ERBS", "5.1.120", PmMetaDataConstants.PFM_MEASUREMENT_PATTERN)).thenReturn(modelUrns);
        when(metaDataHelper.getMetaDataUrnsFromModelService("ERBS", "3.1.72", PmMetaDataConstants.PFM_MEASUREMENT_PATTERN)).thenReturn(modelUrns);
        when(capabilityModelService.getCapabilityValue(TargetTypeInformation.CATEGORY_NODE, "ERBS", STATISTICAL_SUBSCRIPTIONATTRIBUTES, COUNTER_EVENTS_VALIDATION_APPLICABLE, "*"))
                .thenReturn(false);
        modelValidator.modelDeploymentStatusValidator(nodeTypeVersionSet, MetaDataType.COUNTERS);

    }

    @Test
    public void itShouldNotThrowExceptionWhenMetavalidationIsSupported() throws ValidationException {
        // Given
        final Set<NodeTypeAndVersion> nodeTypeVersionSet = new HashSet<>();
        final List<String> technologyDomainList = Collections.singletonList("EPS");
        final NodeTypeAndVersion nodetype1 = new NodeTypeAndVersion("ERBS", "5.1.120", technologyDomainList);
        nodeTypeVersionSet.add(nodetype1);

        when(metaDataHelper.getMetaDataPattern(MetaDataType.COUNTERS)).thenReturn(PmMetaDataConstants.PFM_MEASUREMENT_PATTERN);
        when(metaDataHelper.getMetaDataUrnsFromModelService("ERBS", "5.1.120", PmMetaDataConstants.PFM_MEASUREMENT_PATTERN)).thenReturn(modelUrns);
        when(metaDataHelper.getMetaDataUrnsFromModelService("ERBS", "3.1.72", PmMetaDataConstants.PFM_MEASUREMENT_PATTERN)).thenReturn(modelUrns);
        when(capabilityModelService.getCapabilityValue(TargetTypeInformation.CATEGORY_NODE, "ERBS", STATISTICAL_SUBSCRIPTIONATTRIBUTES, COUNTER_EVENTS_VALIDATION_APPLICABLE, "*"))
                .thenReturn(null);
        modelValidator.modelDeploymentStatusValidator(nodeTypeVersionSet, MetaDataType.COUNTERS);

    }

    @Test
    public void itShouldNotThrowExceptionWhenNodeVersionAreNullAndMetavalidationIsNotSupported() {
        // Given

        final Set<NodeTypeAndVersion> nodeTypeVersionSet = new HashSet<>();
        final List<String> technologyDomain = Collections.singletonList("EPS");
        final NodeTypeAndVersion nodetype1 = new NodeTypeAndVersion("ERBS", null, technologyDomain);
        nodeTypeVersionSet.add(nodetype1);
        when(capabilityModelService.getCapabilityValue(TargetTypeInformation.CATEGORY_NODE, "ERBS", STATISTICAL_SUBSCRIPTIONATTRIBUTES, COUNTER_EVENTS_VALIDATION_APPLICABLE, "*"))
                .thenReturn(false);

        try {
            modelValidator.modelDeploymentStatusValidator(nodeTypeVersionSet, null);
        } catch (final ValidationException e) {
            //received exception 
            fail();
        }

    }
}
