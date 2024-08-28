
/*
 * COPYRIGHT Ericsson 2017
 *
 * The copyright to the computer program(s) herein is the property of
 * Ericsson Inc. The programs may be used and/or copied only with written
 * permission from Ericsson Inc. or in accordance with the terms and
 * conditions stipulated in the agreement/contract under which the
 * program(s) have been supplied.
 */

package com.ericsson.oss.services.pm.initiation.model.metadata;

import static com.ericsson.oss.services.pm.common.constants.PmCapabilityModelConstants.COUNTER_EVENTS_VALIDATION_APPLICABLE;

import static com.ericsson.oss.services.pm.common.constants.PmCapabilityModelConstants.STATISTICAL_SUBSCRIPTIONATTRIBUTES;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import javax.inject.Inject;

import org.slf4j.Logger;

import com.ericsson.oss.itpf.modeling.modelservice.typed.core.target.TargetTypeInformation;
import com.ericsson.oss.pmic.dto.NodeTypeAndVersion;
import com.ericsson.oss.services.pm.modelservice.PmCapabilityModelService;
import com.ericsson.oss.services.pm.services.exception.ValidationException;
import com.ericsson.services.pm.initiation.restful.api.MetaDataType;
import com.ericsson.services.pm.initiation.restful.api.PmMimVersionQuery;

/**
 * This class is used to validate a PmMimVersionQuery or set of NodeTypeAndVersion. It validates that counter models exist in Model Service for the
 * given Set of node's and version's. It also validates that the query is correct format (i.e. contains information on both node name and version)
 *
 * @author enichyl
 */
public class PMICModelDeploymentValidator {

    @Inject
    private Logger log;
    @Inject
    private PmMetaDataHelper metaDataHelper;
    @Inject
    private PmCapabilityModelService capabilityModelService;

    /**
     * Model deployment status validator.
     *
     * @param nodeTypeVersionSet
     *         the node type version set
     * @param metaDataType
     *         the meta data type
     *
     * @throws ValidationException
     *         thrown if nodeTypeAndVersionSet/metaDataType is invalid
     */
    //TODO Method to be removed and use ModelServiceDao method to access ModelService
    public void modelDeploymentStatusValidator(final Set<NodeTypeAndVersion> nodeTypeVersionSet, final MetaDataType metaDataType)
            throws ValidationException {
        log.debug("start model deployment validation for counters {} ", nodeTypeVersionSet);
        final Set<NodeTypeAndVersion> errorModelSet = new HashSet<>();

        final String metaDataPattern = metaDataHelper.getMetaDataPattern(metaDataType);
        // scanning all models
        final boolean isValidModelIdentity = isValidModelIdentity(nodeTypeVersionSet, errorModelSet, metaDataPattern);
        if (!errorModelSet.isEmpty() || isValidModelIdentity) {
            validateNodeTypeVersions(errorModelSet, isValidModelIdentity);
        }
    }

    /**
     * Model deployment status validator.
     *
     * @param pmvq
     *         - the PmMimVersionQuery to be executed
     * @param metaDataType
     *         - the type of meta Data to be passed on to the validator
     *
     * @throws ValidationException
     *         - thrown if PmMimVersionQuery / MetaDataType is invalid
     */
    public void modelDeploymentStatusValidator(final PmMimVersionQuery pmvq, final MetaDataType metaDataType) throws ValidationException {
        modelDeploymentStatusValidator(pmvq.getMimVersions(), metaDataType);
    }

    private void validateNodeTypeVersions(final Set<NodeTypeAndVersion> errorModelSet, final boolean isValidModelIdentity)
            throws ValidationException {
        final StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("Node type & version check failed due to : ");
        if (!errorModelSet.isEmpty()) {
            stringBuilder.append("Invalid Version");
            for (final NodeTypeAndVersion ntav : errorModelSet) {
                stringBuilder.append(String.format(" %s:%s,", ntav.getNodeType(), ntav.getOssModelIdentity()));
            }
            stringBuilder.deleteCharAt(stringBuilder.length() - 1);
            stringBuilder.append(".");
        }

        if (isValidModelIdentity) {
            if (stringBuilder.charAt(stringBuilder.length() - 1) == '.') {
                stringBuilder.append(" ");
            }
            stringBuilder.append("Empty model version identified.");
        }
        log.debug("Exception message generated {}", stringBuilder);
        throw new ValidationException(stringBuilder.toString());
    }

    private boolean isValidModelIdentity(final Set<NodeTypeAndVersion> nodeTypeVersionSet, final Set<NodeTypeAndVersion> errorModelSet,
                                         final String metaDataPattern) {
        boolean isValidModelIdentity = false;
        for (final NodeTypeAndVersion nodeTypeAndVersion : nodeTypeVersionSet) {
            final String neType = nodeTypeAndVersion.getNodeType();
            final boolean isEventCounterVerificationNeeded = isCounterValidationSupportedForGivenTargetType(nodeTypeAndVersion.getNodeType());
            final String ossModelIdentity = nodeTypeAndVersion.getOssModelIdentity();
            if (isEventCounterVerificationNeeded) {
                log.debug("Validating counters for node neType: {}, version: {}", neType, ossModelIdentity);
                if (ossModelIdentity == null || ossModelIdentity.isEmpty()) {
                    isValidModelIdentity = true;
                    continue;
                }

                final Collection<String> pfmMeasurementModelUrns = metaDataHelper.getMetaDataUrnsFromModelService(neType, ossModelIdentity,
                        metaDataPattern);
                if (pfmMeasurementModelUrns.isEmpty()) {
                    errorModelSet.add(nodeTypeAndVersion);
                }
            }
        }
        return isValidModelIdentity;
    }

    /**
     * Validates PmMimVersionQuery
     *
     * @param pmvq
     *         - the PmMimVersionQuery to be executed
     *
     * @throws ValidationException
     *         - thrown if PmMimVersionQuery / MetaDataType is invalid
     */
    public void validateQuery(final PmMimVersionQuery pmvq) throws ValidationException {
        if (pmvq == null || pmvq.getMimVersions() == null || pmvq.getMimVersions().isEmpty()) {
            throw new ValidationException("Invalid mim version query for counter list");
        }
    }

    /**
     * Check counter verification capability for given targetType
     *
     * @param targetType
     *         - target type of the node
     *
     * @return - returns whether capability is enabled or disabled for given target type
     */
    public boolean isCounterValidationSupportedForGivenTargetType(final String targetType) {

        Boolean isEventCounterVerificationNeeded = true;

        try {

            isEventCounterVerificationNeeded = (Boolean) capabilityModelService.getCapabilityValue(TargetTypeInformation.CATEGORY_NODE, targetType, STATISTICAL_SUBSCRIPTIONATTRIBUTES , COUNTER_EVENTS_VALIDATION_APPLICABLE, "*");
            // setting default to true if model is not found and null value is returned from model service.
            return (isEventCounterVerificationNeeded == null) || isEventCounterVerificationNeeded;
        } catch (final Exception ex) {
            log.warn("Failed to get meta validation capability for given target type {} due to {}", targetType, ex);

        }

        return true;

    }
}
