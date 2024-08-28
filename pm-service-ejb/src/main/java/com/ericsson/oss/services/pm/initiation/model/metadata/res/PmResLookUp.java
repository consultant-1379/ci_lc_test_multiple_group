/*------------------------------------------------------------------------------
 *******************************************************************************
 * COPYRIGHT Ericsson 2018
 *
 * The copyright to the computer program(s) herein is the property of
 * Ericsson Inc. The programs may be used and/or copied only with written
 * permission from Ericsson Inc. or in accordance with the terms and
 * conditions stipulated in the agreement/contract under which the
 * program(s) have been supplied.
 *******************************************************************************
 *----------------------------------------------------------------------------*/

package com.ericsson.oss.services.pm.initiation.model.metadata.res;

import static com.ericsson.oss.itpf.modeling.schema.util.SchemaConstants.DPS_PRIMARYTYPE;
import static com.ericsson.oss.itpf.modeling.schema.util.SchemaConstants.OSS_EDT;
import static com.ericsson.oss.services.pm.common.constants.PmCapabilityModelConstants.PMFUNCTION;
import static com.ericsson.oss.services.pm.common.constants.PmCapabilityModelConstants.RES_SUBSCRIPTION_ATTRIBUTES;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import javax.inject.Inject;

import org.slf4j.Logger;

import com.ericsson.oss.itpf.datalayer.dps.modeling.modelservice.typed.persistence.primarytype.PrimaryTypeAttributeSpecification;
import com.ericsson.oss.itpf.datalayer.dps.modeling.modelservice.typed.persistence.primarytype.PrimaryTypeSpecification;
import com.ericsson.oss.itpf.modeling.common.info.ModelInfo;
import com.ericsson.oss.itpf.modeling.modelservice.exception.UnknownModelException;
import com.ericsson.oss.itpf.modeling.modelservice.typed.TypedModelAccess;
import com.ericsson.oss.itpf.modeling.modelservice.typed.core.constraints.Constraint;
import com.ericsson.oss.itpf.modeling.modelservice.typed.core.constraints.MinMaxRange;
import com.ericsson.oss.itpf.modeling.modelservice.typed.core.constraints.MinMaxValue;
import com.ericsson.oss.itpf.modeling.modelservice.typed.core.constraints.ValueRangeConstraint;
import com.ericsson.oss.itpf.modeling.modelservice.typed.core.edt.EnumDataTypeSpecification;
import com.ericsson.oss.itpf.modeling.modelservice.typed.core.target.MimMappedTo;
import com.ericsson.oss.itpf.modeling.modelservice.typed.core.target.TargetTypeInformation;
import com.ericsson.oss.itpf.modeling.modelservice.typed.core.target.TargetTypeVersionInformation;
import com.ericsson.oss.pmic.api.modelservice.PmCapabilitiesLookupLocal;
import com.ericsson.oss.pmic.dto.NodeTypeAndVersion;
import com.ericsson.oss.pmic.dto.node.Node;
import com.ericsson.oss.pmic.dto.subscription.cdts.CellInfo;
import com.ericsson.oss.pmic.dto.subscription.enums.ResServiceCategory;
import com.ericsson.oss.pmic.dto.subscription.enums.ResSpreadingFactor;
import com.ericsson.oss.pmic.profiler.logging.LogProfiler;
import com.ericsson.oss.services.pm.exception.DataAccessException;
import com.ericsson.oss.services.pm.generic.NodeService;
import com.ericsson.oss.services.pm.initiation.restful.ResSubscriptionAttributes;
import com.ericsson.oss.services.pm.initiation.utils.PmFunctionUtil;
import com.ericsson.oss.services.pm.services.exception.PfmDataException;

/**
 * This class is responsible to fetch all attributes and enums related to Res subscription
 */
public class PmResLookUp {

    static final String RES_MEAS_CONTROL = "resMeasureControlMoName";
    static final String RES_UE_FRACTION = "resUeFractionAttributeName";
    static final String SUPPORTED_RES_SERVICES = "supportedResServicesEdtName";
    static final String SUPPORTED_RES_MEASURE_QUANTITIES = "supportedResMeasureQuantitiesEdtName";
    static final String RES_MEAS_CAPABILITY = "resSubscriptionAttributes";
    static final String ATTACHED_NODE_TYPES = "multipleNeTypesList";

    @Inject
    private Logger logger;

    @Inject
    private NodeService nodeService;

    @Inject
    private TypedModelAccess typedModelAccess;

    @Inject
    private PmFunctionUtil pmFunctionUtil;

    @Inject
    private PmCapabilitiesLookupLocal pmCapabilitiesLookupLocal;

    /**
     * Gets RES supported attributes to be shown in the UI.
     *
     * @param nodeAndVersionSet
     *         the node type and version set
     *
     * @return the RES attributes objects
     * @throws PfmDataException
     *         - - thrown if PmMimVersionQuery is invalid
     */
    public ResSubscriptionAttributes getResAttributes(final Set<NodeTypeAndVersion> nodeAndVersionSet) throws PfmDataException {

        final TargetTypeInformation targetTypeInformation = typedModelAccess.getModelInformation(TargetTypeInformation.class);

        try {
            final Map<String, Set<Integer>> supportedSamplingRates = new HashMap<>();
            final Set<Integer> supportedResUeFraction = new TreeSet<>();
            final Set<String> supportedResRmq = new TreeSet<>();
            final Set<String> supportedResServices = new TreeSet<>();
            final Set<Integer> supportedResSpreadingFactor = new TreeSet<>();

            for (final NodeTypeAndVersion nodeTypeAndVersion : nodeAndVersionSet) {
                final TargetTypeVersionInformation targetTypeVersionInformation = targetTypeInformation
                        .getTargetTypeVersionInformation(TargetTypeInformation.CATEGORY_NODE, nodeTypeAndVersion.getNodeType());
                final Collection<MimMappedTo> mimsMappedTo = targetTypeVersionInformation.getMimsMappedTo(nodeTypeAndVersion.getOssModelIdentity());
                if (!mimsMappedTo.isEmpty()) {
                    final MimMappedTo mim = mimsMappedTo.iterator().next();
                    final String nodeType = nodeTypeAndVersion.getNodeType();
                    getSupportedSamplingRatesforMim(supportedSamplingRates, mim, nodeType);
                    supportedResUeFraction.addAll(getSupportedResUeFractionForMim(mim, nodeType));
                    supportedResRmq.addAll(getSupporteResRmqAndServicesForMim(mim, nodeType, SUPPORTED_RES_MEASURE_QUANTITIES));
                    supportedResServices.addAll(getSupporteResRmqAndServicesForMim(mim, nodeType, SUPPORTED_RES_SERVICES));
                }
            }

            supportedResSpreadingFactor.addAll(ResSpreadingFactor.getIdentifiers());

            return new ResSubscriptionAttributes(supportedSamplingRates, supportedResUeFraction, supportedResRmq, supportedResServices,
                    supportedResSpreadingFactor);

        } catch (final IllegalArgumentException | UnknownModelException e) {
            logger.error(e.getMessage(), e);
            throw new PfmDataException(e.getMessage(), e);
        }
    }

    /**
     * Fetch list of attached nodes with PmFunction enabled
     *
     * @param cells
     *         list of Cells
     * @param isApplyOnAllCells
     *         isApplyOnAllCells flag
     * @param nodeFdns
     *         the RES nodeFdns
     * @param isActivationEvent
     *         isActivationEvent flag
     *
     * @return the list of attached nodes or empty list
     * @throws DataAccessException
     *         - if any other data access exception is thrown.
     * @throws IllegalArgumentException
     *         - if node Fdn is not a valid network element fdn.
     */
    @LogProfiler(name = "Fetch attached nodes")
    public List<Node> fetchAttachedNodes(final List<CellInfo> cells, final boolean isApplyOnAllCells, final Set<String> nodeFdns,
                                         final boolean isActivationEvent)
            throws IllegalArgumentException, DataAccessException {
        final List<String> resSubscriptionAttachedNodeTypes = (List<String>) pmCapabilitiesLookupLocal
                .getDefaultCapabilityValue(RES_SUBSCRIPTION_ATTRIBUTES, ATTACHED_NODE_TYPES);
        if (isActivationEvent) {
            pmFunctionUtil.filterNodeFdnsByPmFunctionOn(nodeFdns);
        }
        final List<Node> fetchedNodes = nodeService.fetchWranAttached(resSubscriptionAttachedNodeTypes, nodeFdns, cells,
                   !isActivationEvent || isApplyOnAllCells);
        pmFunctionUtil.filterNodesByPmFunctionOn(fetchedNodes);
        logger.debug("Found {} Attached Nodes", fetchedNodes.size());
        return fetchedNodes;
    }

    private void getSupportedSamplingRatesforMim(final Map<String, Set<Integer>> supportedSamplingRates, final MimMappedTo mim,
                                                 final String nodeType) {
        final ModelInfo modelInfo = new ModelInfo(DPS_PRIMARYTYPE, mim.getNamespace(),
                getResSubscriptionAttributesCapability(nodeType, RES_MEAS_CONTROL), mim.getVersion());
        final PrimaryTypeSpecification typeSpec = typedModelAccess.getEModelSpecification(modelInfo, PrimaryTypeSpecification.class);
        for (final ResServiceCategory category : ResServiceCategory.values()) {
            final Set<Integer> rangeValues = getRangeConstraintsValues(
                    typeSpec.getAttributeSpecification(getResSubscriptionAttributesCapability(nodeType, category.getIdentifier())));
            if (supportedSamplingRates.containsKey(category.name())) {
                supportedSamplingRates.get(category.name()).addAll(rangeValues);
            } else {
                supportedSamplingRates.put(category.name(), rangeValues);
            }
        }
    }

    private Collection<String> getSupporteResRmqAndServicesForMim(final MimMappedTo mim, final String nodeType, final String key) {
        final String supportedValues = getResSubscriptionAttributesCapability(nodeType, key);
        return typedModelAccess.getEModelSpecification(new ModelInfo(OSS_EDT, mim.getNamespace(), supportedValues, mim.getVersion()),
                EnumDataTypeSpecification.class).getMemberNames();
    }

    private Collection<Integer> getSupportedResUeFractionForMim(final MimMappedTo mim, final String nodeType) {
        final PrimaryTypeSpecification typeSpec = typedModelAccess.getEModelSpecification(new ModelInfo(DPS_PRIMARYTYPE, mim.getNamespace(),
                getResSubscriptionAttributesCapability(nodeType, RES_MEAS_CONTROL), mim.getVersion()), PrimaryTypeSpecification.class);
        return getRangeConstraintsValues(typeSpec.getAttributeSpecification(getResSubscriptionAttributesCapability(nodeType, RES_UE_FRACTION)));
    }

    /**
     * @param attributeSpec
     *
     * @return
     */
    private Set<Integer> getRangeConstraintsValues(final PrimaryTypeAttributeSpecification attributeSpec) {
        final Collection<Constraint> constraints = attributeSpec.getDataTypeSpecification().getConstraints();
        final Set<Integer> allowedValues = new TreeSet<>();
        for (final Constraint constraint : constraints) {
            if (constraint instanceof ValueRangeConstraint) {
                final Collection<MinMaxValue> rangeValues = ((ValueRangeConstraint) constraint).getAllowedValues();
                for (final MinMaxValue minMaxValue : rangeValues) {
                    allowedValues.addAll(getRangeValues(((MinMaxRange) minMaxValue).getMinValue(), ((MinMaxRange) minMaxValue).getMaxValue()));
                }
            }
        }
        return allowedValues;
    }

    private Set<Integer> getRangeValues(final Long minValue, final Long maxValue) {
        final Integer minIntValue = minValue.intValue();
        final Integer maxIntValue = maxValue.intValue();
        final Set<Integer> values = new HashSet<>(maxIntValue - minIntValue);
        for (int i = minIntValue; i <= maxIntValue; i++) {
            values.add(i);
        }
        return values;
    }

    private String getResSubscriptionAttributesCapability(final String nodeType, final String resCapabilityKey) {
        final Map<String, String> resSubscriptionAttributes = (Map<String, String>) pmCapabilitiesLookupLocal.getCapabilityValue(nodeType, PMFUNCTION,
                RES_MEAS_CAPABILITY);
        return resSubscriptionAttributes.get(resCapabilityKey);
    }
}