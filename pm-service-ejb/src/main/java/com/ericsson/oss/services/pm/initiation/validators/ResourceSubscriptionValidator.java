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

package com.ericsson.oss.services.pm.initiation.validators;

import static com.ericsson.oss.services.pm.common.constants.PmCapabilityModelConstants.SUPPORTED_TECHNOLOGY_DOMAINS;
import static com.ericsson.oss.services.pm.initiation.constants.ApplicationMessages.INVALID_SUBSCRITPION_TO_ACTIVATE;

import java.util.List;
import java.util.Map;

import javax.inject.Inject;

import org.slf4j.Logger;

import com.ericsson.oss.pmic.dto.node.Node;
import com.ericsson.oss.pmic.dto.subscription.ResourceSubscription;
import com.ericsson.oss.pmic.dto.subscription.enums.UserType;
import com.ericsson.oss.services.pm.initiation.model.metadata.PMICModelDeploymentValidator;
import com.ericsson.oss.services.pm.initiation.model.metadata.PmMetaDataHelper;
import com.ericsson.oss.services.pm.initiation.utils.PmFunctionUtil;
import com.ericsson.oss.services.pm.modelservice.PmCapabilityModelService;
import com.ericsson.oss.services.pm.services.exception.PfmDataException;
import com.ericsson.oss.services.pm.services.exception.ValidationException;
import com.ericsson.services.pm.initiation.restful.api.MetaDataType;

/**
 * This class validates Subscription Resource (Network Nodes)
 *
 * @param <S> the type of subscription to validate
 */
public class ResourceSubscriptionValidator<S extends ResourceSubscription> extends SubscriptionValidator<S> {

    @Inject
    private Logger logger;

    @Inject
    protected PmMetaDataHelper metaDataHelper;

    @Inject
    private PmCapabilityModelService pmCapabilityModelService;

    @Inject
    protected PMICModelDeploymentValidator pmicModelDeploymentValidator;

    @Override
    public void validate(final S subscription) throws ValidationException {
        super.validate(subscription);
        if (PmFunctionUtil.PmFunctionPropertyValue.PM_FUNCTION_LEGACY != PmFunctionUtil.getPmFunctionConfig()) {
            subscriptionCommonValidator.validatePmFunction(subscription.getNodes());
        }
        final MetaDataType metaData = metaDataHelper.getMetaDataType(subscription.getType());
        logger.debug("Validating pfm measurement for given subscription");
        if (isPfmMeasurementValidationRequired(subscription)) {
            checkIsNodeNeTypeAndVersionSupported(subscription, metaData);
        }
        if (subscription.getIsImported()) {
            subscriptionCommonValidator.checkCapabilitySupportedNodesBySubscriptionType(subscription);
            subscriptionCommonValidator.checkCapabilitySupportedRopPeriod(subscription);
        }
    }

    private void checkIsNodeNeTypeAndVersionSupported(final ResourceSubscription sub, final MetaDataType metaData) throws ValidationException {
        pmicModelDeploymentValidator.modelDeploymentStatusValidator(sub.getNodesTypeVersion(), metaData);
    }

    /**
     * Get boolean value of whether Validation needed or not. Usually this is meant for system defined or user defined subscription with nodes having
     * no pfm measurements.
     *
     * @param subscription - a Subscription object
     * @return value of countersEventsValidationApplicable : TRUE/FALSE
     */
    private boolean isPfmMeasurementValidationRequired(final ResourceSubscription subscription) {
        try {
            if (UserType.SYSTEM_DEF == subscription.getUserType()) {
                logger.debug("Validating pfm measurement for given System defined subscription");
                return systemDefinedPmCapabilityReader.isEventCounterVerificationNeeded(subscription.getName());
            } else if (UserType.USER_DEF == subscription.getUserType()) {
                logger.debug("Validating pfm measurement for given user defined subscription");
                return subscriptionCommonValidator.isEventCounterVerificationNeeded(subscription);
            }
        } catch (final Exception exception) {
            logger.error("Failed to check pfm measurement validation for system defined subscription due to {}", exception.getMessage());
        }
        return true;
    }

    @Override
    public void validateActivation(final S subscription) throws ValidationException {
        super.validateActivation(subscription);
        if (subscription.getNodes() == null || subscription.getNodes().isEmpty()) {
            throw new ValidationException(String.format(INVALID_SUBSCRITPION_TO_ACTIVATE, subscription.getName()));
        }
    }

    @Override
    public void validateImport(final S subscription) throws ValidationException, PfmDataException {
        final List<Node> nodes = subscription.getNodes();
        Object capabilityValue = null;
        if (nodes != null) {
            final Map<String, Object> globalCapabilities = pmCapabilityModelService.getSubscriptionAttributesGlobalCapabilities(subscription, SUPPORTED_TECHNOLOGY_DOMAINS);
            capabilityValue = globalCapabilities.get(SUPPORTED_TECHNOLOGY_DOMAINS);
        }
        if (nodes == null || capabilityValue == null) {
            subscriptionCommonValidator.validatePmFunction(nodes);
        } else {
            subscriptionCommonValidator.validateApplicableNodesForSubscription(nodes, (List<String>) capabilityValue);
        }
    }
}
