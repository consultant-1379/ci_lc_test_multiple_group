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

package com.ericsson.oss.services.pm.initiation.model.metadata.moinstances;

import static com.ericsson.oss.pmic.api.utils.SubscriptionTypeMoTypeParentMapper.SUBSCRIPTION_TYPE_MO_PARENT_MAP;
import static com.ericsson.oss.pmic.api.utils.SubscriptionTypeMoTypeParentMapper.SUPPORTED_MO_CLASSES;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.inject.Inject;

import org.slf4j.Logger;

import com.ericsson.oss.itpf.modeling.modelservice.ModelService;
import com.ericsson.oss.itpf.modeling.modelservice.typed.TypedModelAccess;
import com.ericsson.oss.itpf.modeling.modelservice.typed.core.target.MimMappedTo;
import com.ericsson.oss.itpf.modeling.modelservice.typed.core.target.TargetTypeInformation;
import com.ericsson.oss.itpf.modeling.modelservice.typed.core.target.TargetTypeVersionInformation;
import com.ericsson.oss.pmic.dao.availability.PmicDpsAvailabilityStatus;
import com.ericsson.oss.pmic.dto.NodeTypeAndVersion;
import com.ericsson.oss.pmic.dto.node.Node;
import com.ericsson.oss.pmic.dto.subscription.cdts.CellInfo;
import com.ericsson.oss.pmic.dto.subscription.cdts.MoinstanceInfo;
import com.ericsson.oss.pmic.dto.subscription.enums.SubscriptionType;
import com.ericsson.oss.services.pm.exception.DataAccessException;
import com.ericsson.oss.services.pm.exception.RuntimeDataAccessException;
import com.ericsson.oss.services.pm.generic.NodeService;
import com.ericsson.oss.services.pm.generic.SystemPropertiesService;
import com.ericsson.oss.services.pm.modelservice.PmCapabilityModelService;

/**
 * This class is responsible to fetch moInstances based on MoClassType and Nodes
 */
public class PmMoinstancesLookUp {

    public static final String COMMA_CHAR = ",";
    public static final String EQUAL_CHAR = "=";
    public static final String UTRAN_CELL_GROUP = "UtranCell";
    public static final String BSC_NAMESPACE = "BscM";
    @Inject
    private Logger log;
    @Inject
    private NodeService nodeService;
    @Inject
    private ModelService modelService;
    @Inject
    private PmicDpsAvailabilityStatus dpsAvailabilityStatus;
    @Inject
    private PmCapabilityModelService pmCapabilitiesLookup;
    @Inject
    private SystemPropertiesService systemPropertiesService;

    /**
     * Gets moinstances.
     *
     * @param nodeAndVersionSet
     *         the node type and version set
     * @param nodes
     *         the nodes
     * @param moClasses
     *         the mo classes
     * @param subscriptionType
     *         the subscription type
     *
     * @return the moinstance objects
     * @throws DataAccessException
     *         thrown if the database cannot be reached
     * @throws RuntimeDataAccessException
     *         - if a retriable data access exception is thrown from DPS
     */
    public List<MoinstanceInfo> getMoinstances(final Set<NodeTypeAndVersion> nodeAndVersionSet, final String nodes, final String moClasses,
                                               final String subscriptionType)
            throws DataAccessException, RuntimeDataAccessException {
        final List<String> listMOCs = new ArrayList<>(Arrays.asList(moClasses.split(COMMA_CHAR)));
        final Map<String, String> nodeNamesToTypeMap = new HashMap<>();
        for (final String nodeData : nodes.split(COMMA_CHAR)) {
            final String[] nodeNameToType = nodeData.split(":");
            if (nodeNameToType.length != 2) {
                log.warn("Node Data {} is not populated with 'networkElementName: networkElementType'", nodeData);
                return Collections.emptyList();
            }
            nodeNamesToTypeMap.put(nodeNameToType[0], nodeNameToType[1]);
        }
        return getMoinstanceInfos(nodeAndVersionSet, SubscriptionType.fromString(subscriptionType), nodeNamesToTypeMap, listMOCs);
    }

    /**
     * Gets moinstances.
     *
     * @param nodeTypeAndVersions
     *         node type and version set
     * @param subscriptionType
     *         subscription type
     * @param nodeNamesToTypeMap
     *         nodes
     * @param moClasses
     *         mo classes
     *
     * @return moinstance objects
     * @throws DataAccessException
     *         thrown if the database cannot be reached
     * @throws RuntimeDataAccessException
     *         - if a retriable data access exception is thrown from DPS
     */
    public List<MoinstanceInfo> getMoinstanceInfos(final Iterable<NodeTypeAndVersion> nodeTypeAndVersions, final SubscriptionType subscriptionType,
                                                   final Map<String, String> nodeNamesToTypeMap, final List<String> moClasses)
            throws DataAccessException {
        return getMoinstanceInfos(nodeTypeAndVersions, subscriptionType, nodeNamesToTypeMap, moClasses, true);
    }

    private <T> List<T> getMoinstanceInfos(final Iterable<NodeTypeAndVersion> nodeTypeAndVersions, final SubscriptionType subscriptionType,
                                           final Map<String, String> nodeNamesToTypeMap, final List<String> moClasses, final boolean isMoInstance)
            throws DataAccessException {
        if (!dpsAvailabilityStatus.isAvailable()) {
            log.warn("Failed to find Moinstances, Dps not available");
            return Collections.emptyList();
        }

        final List<T> moInstances = new ArrayList<>();

        final Map<String, String> nodeTypeToNameSpaceMap = new HashMap<>();
        for (final NodeTypeAndVersion nodetypeAndVersion : nodeTypeAndVersions) {
            final String nodeType = nodetypeAndVersion.getNodeType();
            final String ossModelIdentity = nodetypeAndVersion.getOssModelIdentity();
            log.debug("ossModelIdentity: {}", ossModelIdentity);
            final String namespace = (SubscriptionType.RPMO == subscriptionType) ? BSC_NAMESPACE : (getNodeNamespace(nodeType, ossModelIdentity));
            log.debug("Namespace {} returned for Nodetype: {}", namespace, nodeType);
            if (namespace == null) {
                return Collections.emptyList();
            }
            nodeTypeToNameSpaceMap.put(nodeType, namespace);
        }

        final String moParentType = SUBSCRIPTION_TYPE_MO_PARENT_MAP.get(subscriptionType.name());
        for (final Map.Entry<String, String> entry : nodeNamesToTypeMap.entrySet()) {
            final String networkElementName = entry.getKey();
            final String networkElementType = entry.getValue();
            if (SubscriptionType.MOINSTANCE == subscriptionType) {
                final List<String> supportedMOClasses = pmCapabilitiesLookup.getSupportedMOCsCapabilityValues(networkElementType,
                        SUPPORTED_MO_CLASSES, SubscriptionType.MOINSTANCE.toString());
                log.info("Capability values for SUPPORTED_MO_CLASSES : {}", supportedMOClasses);
                moClasses.retainAll(supportedMOClasses);
            }
            final String managedElementFdn = nodeService.findManagedElementFdnFromNodeFdn(Node.NETWORK_ELEMENT_FDN_KEY + networkElementName);
            if (managedElementFdn == null) {
                log.warn("Couldn't find managedElementFDN from nodeName {}", networkElementName);
                return Collections.emptyList();
            }
            final String moInstanceParentFdn = managedElementFdn + COMMA_CHAR + moParentType;
            final String namespace = nodeTypeToNameSpaceMap.get(networkElementType);
            if (namespace == null) {
                log.warn("Cannot get namespace for network element type {}", networkElementType);
                return Collections.emptyList();
            }
            log.debug("getSupportedMoInstances() networkElementFdn : {} moInstanceParentFdn :{}, Namespace : {}", managedElementFdn,
                    moInstanceParentFdn, namespace);
            for (final String moClassType : moClasses) {
                moInstances.addAll((List<T>) populateMoTypeInstances(networkElementName, managedElementFdn, moInstanceParentFdn, namespace,
                        moClassType, isMoInstance));
            }
        }

        return moInstances;
    }

    /**
     * Gets cells.
     *
     * @param nodeTypeAndVersions
     *         node type and version set
     * @param subscriptionType
     *         subscription type
     * @param nodeNamesToTypeMap
     *         nodes
     *
     * @return cellInfo objects
     * @throws DataAccessException
     *         thrown if the database cannot be reached
     * @throws RuntimeDataAccessException
     *         - if a retriable data access exception is thrown from DPS
     */
    public List<CellInfo> getCellInfos(final Iterable<NodeTypeAndVersion> nodeTypeAndVersions, final SubscriptionType subscriptionType,
                                       final Map<String, String> nodeNamesToTypeMap)
            throws DataAccessException {
        return getMoinstanceInfos(nodeTypeAndVersions, subscriptionType, nodeNamesToTypeMap, Arrays.asList(UTRAN_CELL_GROUP), false);
    }

    private <T> List<T> populateMoTypeInstances(final String networkElementName, final String managedElementFdn, final String moInstanceParentFdn,
                                                final String namespace, final String moClassType, final boolean isMoInstance)
            throws DataAccessException {

        final List<T> moInstances = new ArrayList<>();
        final List<String> moInstanceFdns = systemPropertiesService.findAllMoInstancesOnManagedElementWithNamespaceAndType(moInstanceParentFdn,
                namespace, moClassType, networkElementName);
        for (final String moInstanceFdn : moInstanceFdns) {

            final String moInstanceName = moInstanceFdn.split(moInstanceParentFdn + COMMA_CHAR)[1];
            log.debug("DPS fdn [{}] with managedElement[{}]: moInstanceName :[{}]", moInstanceFdn, managedElementFdn, moInstanceName);
            moInstances.add((T) (isMoInstance ? new MoinstanceInfo(networkElementName, moInstanceName)
                    : new CellInfo(networkElementName, moInstanceName.split(EQUAL_CHAR)[1])));
        }
        return moInstances;
    }

    private String getNodeNamespace(final String nodeType, final String ossModelIdentity) {
        final TypedModelAccess typedModelAccess = modelService.getTypedAccess();
        final TargetTypeInformation targetTypeInformation = typedModelAccess.getModelInformation(TargetTypeInformation.class);
        final TargetTypeVersionInformation targetTypeVersionInformation = targetTypeInformation
                .getTargetTypeVersionInformation(TargetTypeInformation.CATEGORY_NODE, nodeType);
        final Collection<MimMappedTo> mimsMappedTo = targetTypeVersionInformation.getMimsMappedTo(ossModelIdentity);
        if (!mimsMappedTo.isEmpty()) {
            return mimsMappedTo.iterator().next().getNamespace();
        } else {
            return null;
        }
    }
}
