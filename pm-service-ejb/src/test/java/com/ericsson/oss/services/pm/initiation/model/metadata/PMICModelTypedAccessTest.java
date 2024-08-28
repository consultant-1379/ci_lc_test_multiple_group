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
import static org.testng.Assert.assertEquals;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.internal.util.reflection.Whitebox;
import org.slf4j.Logger;

import com.ericsson.oss.itpf.datalayer.dps.modeling.modelservice.typed.persistence.primarytype.HierarchicalPrimaryTypeSpecification;
import com.ericsson.oss.itpf.datalayer.dps.modeling.modelservice.typed.persistence.primarytype.PrimaryTypeAttributeSpecification;
import com.ericsson.oss.itpf.datalayer.dps.modeling.modelservice.typed.persistence.primarytype.PrimaryTypeSpecification;
import com.ericsson.oss.itpf.modeling.common.info.ModelInfo;
import com.ericsson.oss.itpf.modeling.modelservice.typed.TypedModelAccess;
import com.ericsson.oss.itpf.modeling.modelservice.typed.core.DataType;
import com.ericsson.oss.itpf.modeling.modelservice.typed.core.DataTypeSpecification;
import com.ericsson.oss.itpf.modeling.schema.util.SchemaConstants;
import com.ericsson.oss.pmic.dto.subscription.StatisticalSubscription;


public class PMICModelTypedAccessTest {

    private static final String DEFAULT_VERSION = "1.0.0";
    private static final String DEFAULT_NAMESPACE = "pmic_subscription";


    PMICModelTypedAccess pmicTypedAccess;

    @Mock
    TypedModelAccess modelAccess;

    @Mock
    Logger log;

    @Before
    public void setUp()  {
        pmicTypedAccess = spy(new PMICModelTypedAccess());
        MockitoAnnotations.initMocks(this);
        Whitebox.setInternalState(pmicTypedAccess, "typedModelAccess", modelAccess);
        Whitebox.setInternalState(pmicTypedAccess, "log", log);
    }

    @Test
    public void getDefinedChildrenForSpecificationShouldReturnOneChildrenNameForGivenSpecification() {

        // mock
        final ModelInfo primaryType = new ModelInfo(SchemaConstants.DPS_PRIMARYTYPE, DEFAULT_NAMESPACE, StatisticalSubscription.class.getSimpleName(),
                DEFAULT_VERSION);
        final HierarchicalPrimaryTypeSpecification specification = mock(HierarchicalPrimaryTypeSpecification.class);
        final ModelInfo modelInfo = mock(ModelInfo.class);
        final List<HierarchicalPrimaryTypeSpecification> specList = new ArrayList<>();
        specList.add(specification);

        // when
        when(modelAccess.getEModelSpecification(primaryType, HierarchicalPrimaryTypeSpecification.class)).thenReturn(specification);
        when(specification.getAllChildTypes()).thenReturn(specList);
        when(specification.getModelInfo()).thenReturn(modelInfo);
        when(modelInfo.getName()).thenReturn("scheduleInfo");

        // Calling Testable method
        final List<String> actualChildNames = pmicTypedAccess.getDefinedChildrenForSpecification(DEFAULT_NAMESPACE,
                StatisticalSubscription.class.getSimpleName(), DEFAULT_VERSION);

        // Given
        final List<String> expectedChildNames = new ArrayList<>();
        expectedChildNames.add("scheduleInfo");
        // Assert
        assertEquals(actualChildNames, expectedChildNames);
    }

    @Test
    public void getDefinedChildrenForSpecificationShouldReturnTwoChildrenNamesForGivenSpecification() {

        // mock
        final ModelInfo primaryType = new ModelInfo(SchemaConstants.DPS_PRIMARYTYPE, DEFAULT_NAMESPACE, StatisticalSubscription.class.getSimpleName(),
                DEFAULT_VERSION);
        final HierarchicalPrimaryTypeSpecification specification = mock(HierarchicalPrimaryTypeSpecification.class);
        final HierarchicalPrimaryTypeSpecification specification1 = mock(HierarchicalPrimaryTypeSpecification.class);
        final ModelInfo modelInfo = mock(ModelInfo.class);
        final ModelInfo modelInfo1 = mock(ModelInfo.class);
        final List<HierarchicalPrimaryTypeSpecification> specList = new ArrayList<>();
        specList.add(specification);
        specList.add(specification1);

        // when
        when(modelAccess.getEModelSpecification(primaryType, HierarchicalPrimaryTypeSpecification.class)).thenReturn(specification);
        when(specification.getAllChildTypes()).thenReturn(specList);
        when(specification.getModelInfo()).thenReturn(modelInfo);
        when(specification1.getModelInfo()).thenReturn(modelInfo1);
        when(modelInfo.getName()).thenReturn("ScheduleInfo");
        when(modelInfo1.getName()).thenReturn("CounterEventGroupInfo");

        // Calling Testable method
        final List<String> actualChildNames = pmicTypedAccess.getDefinedChildrenForSpecification(DEFAULT_NAMESPACE,
                StatisticalSubscription.class.getSimpleName(), DEFAULT_VERSION);

        // Given
        final List<String> expectedChildNames = new ArrayList<>();
        expectedChildNames.add("ScheduleInfo");
        expectedChildNames.add("CounterEventGroupInfo");
        // Assert
        assertEquals(actualChildNames, expectedChildNames);
    }

    @Test
    public void getAttributesFromSpecificationNameShouldReturnOneAttributeInAMap() {
        // mock
        final ModelInfo primaryType = new ModelInfo(SchemaConstants.DPS_PRIMARYTYPE, DEFAULT_NAMESPACE, StatisticalSubscription.class.getSimpleName(),
                DEFAULT_VERSION);
        final PrimaryTypeSpecification specification = mock(PrimaryTypeSpecification.class);
        final PrimaryTypeAttributeSpecification attributeSpec = mock(PrimaryTypeAttributeSpecification.class);
        final List<PrimaryTypeAttributeSpecification> attriSpecList = new ArrayList<>();
        attriSpecList.add(attributeSpec);
        final DataTypeSpecification dataTypeSpec = mock(DataTypeSpecification.class);

        // when
        when(modelAccess.getEModelSpecification(primaryType, PrimaryTypeSpecification.class)).thenReturn(specification);
        when(specification.getAttributeSpecifications()).thenReturn(attriSpecList);
        when(attributeSpec.getName()).thenReturn("name");
        when(attributeSpec.getDataTypeSpecification()).thenReturn(dataTypeSpec);
        when(dataTypeSpec.getDataType()).thenReturn(DataType.STRING);

        // Calling Testable method
        final Map<String, DataType> actualMap = pmicTypedAccess.getAttributesFromSpecificationName(DEFAULT_NAMESPACE,
                StatisticalSubscription.class.getSimpleName(), DEFAULT_VERSION);

        // Given
        final Map<String, DataType> expectedMap = new HashMap<>();
        expectedMap.put("name", DataType.STRING);

        // Assert
        assertEquals(actualMap, expectedMap);
    }

    @Test
    public void getAttributesFromSpecificationNameShouldReturnTwoAttributesInAMap() {
        // mock
        final ModelInfo primaryType = new ModelInfo(SchemaConstants.DPS_PRIMARYTYPE, DEFAULT_NAMESPACE, StatisticalSubscription.class.getSimpleName(),
                DEFAULT_VERSION);
        final PrimaryTypeSpecification specification = mock(PrimaryTypeSpecification.class);
        final PrimaryTypeAttributeSpecification attributeSpec = mock(PrimaryTypeAttributeSpecification.class);
        final PrimaryTypeAttributeSpecification attributeSpec1 = mock(PrimaryTypeAttributeSpecification.class);
        final List<PrimaryTypeAttributeSpecification> attriSpecList = new ArrayList<>();
        attriSpecList.add(attributeSpec);
        attriSpecList.add(attributeSpec1);
        final DataTypeSpecification dataTypeSpec = mock(DataTypeSpecification.class);

        // when
        when(modelAccess.getEModelSpecification(primaryType, PrimaryTypeSpecification.class)).thenReturn(specification);
        when(specification.getAttributeSpecifications()).thenReturn(attriSpecList);
        when(attributeSpec.getName()).thenReturn("name");
        when(attributeSpec1.getName()).thenReturn("type");
        when(attributeSpec.getDataTypeSpecification()).thenReturn(dataTypeSpec);
        when(attributeSpec1.getDataTypeSpecification()).thenReturn(dataTypeSpec);
        when(dataTypeSpec.getDataType()).thenReturn(DataType.STRING);

        // Calling Testable method
        final Map<String, DataType> actualMap = pmicTypedAccess.getAttributesFromSpecificationName(DEFAULT_NAMESPACE,
                StatisticalSubscription.class.getSimpleName(), DEFAULT_VERSION);

        // Given
        final Map<String, DataType> expectedMap = new HashMap<>();
        expectedMap.put("name", DataType.STRING);
        expectedMap.put("type", DataType.STRING);

        // Assert
        assertEquals(actualMap, expectedMap);
    }
}
