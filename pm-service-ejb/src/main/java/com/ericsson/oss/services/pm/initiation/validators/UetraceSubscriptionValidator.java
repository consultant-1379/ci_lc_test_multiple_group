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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import org.slf4j.Logger;

import com.ericsson.oss.pmic.dto.subscription.UETraceSubscription;
import com.ericsson.oss.pmic.dto.subscription.cdts.NodeInfo;
import com.ericsson.oss.pmic.dto.subscription.cdts.StreamInfo;
import com.ericsson.oss.pmic.dto.subscription.cdts.UeInfo;
import com.ericsson.oss.pmic.dto.subscription.enums.OutputModeType;
import com.ericsson.oss.pmic.dto.subscription.enums.SubscriptionType;
import com.ericsson.oss.services.pm.initiation.model.subscription.NodeGrouping;
import com.ericsson.oss.services.pm.initiation.validators.annotation.SubscriptionValidatorQualifier;
import com.ericsson.oss.services.pm.services.exception.PfmDataException;
import com.ericsson.oss.services.pm.services.exception.ValidationException;

/**
 * This class validates UETrace Subscription
 */
@ApplicationScoped
@SubscriptionValidatorQualifier(subscriptionType = SubscriptionType.UETRACE)
public class UetraceSubscriptionValidator extends SubscriptionValidator<UETraceSubscription> {

    @Inject
    private Logger logger;

    @Override
    public void validate(final UETraceSubscription subscription) throws ValidationException {
        super.validate(subscription);

        final List<UeInfo> ueInfos = subscription.getUeInfo() != null ? Collections.singletonList(subscription.getUeInfo()) : new ArrayList<>();
        subscriptionCommonValidator.validateUeInfos(ueInfos);

        final List<StreamInfo> streamInfos = subscription.getStreamInfo() != null ? Collections.singletonList(subscription.getStreamInfo()) : null;

        final OutputModeType outputModeType = subscription.getOutputMode();
        if (subscriptionCommonValidator.isStreamInfoApplicable(outputModeType, streamInfos)) {
            subscriptionCommonValidator.validateIpAddresses(streamInfos);
        }
    }

    @Override
    public void validateImport(final UETraceSubscription subscription) throws ValidationException, PfmDataException {
        final List<NodeInfo> invalidNodeInfos = new ArrayList<>();
        final List<NodeInfo> nodeInfos = subscription.getNodeInfoList();

        super.validateImport(subscription);
        for (final NodeInfo nodeInfo : nodeInfos) {
            final String validInterfaceTypes = getValidInterfaceTypes(nodeInfo);
            final List<String> interfaceTypes = nodeInfo.getInterfaceTypes();
            for (final String interfaceType : interfaceTypes) {
                logger.debug("Checking Interface Type  : {} against valid one = {}", interfaceType, validInterfaceTypes);
                if (!interfaceType.matches(validInterfaceTypes)) {
                    invalidNodeInfos.add(nodeInfo);
                }
            }
        }
        if (!invalidNodeInfos.isEmpty()) {
            final StringBuilder exceptionMessage = new StringBuilder("Invalid Interface types ");
            for (final NodeInfo invalidNodeInfo : invalidNodeInfos) {
                exceptionMessage
                        .append(String.format("[%s %s],", invalidNodeInfo.getNodeGrouping().name(), invalidNodeInfo.getInterfaceTypes().toString()));
            }
            exceptionMessage.append(" for subscription " + subscription.getType().name());
            throw new ValidationException(exceptionMessage.toString());
        }
    }

    /**
     * @param nodeInfo
     *
     * @return the valid values for InterfaceType field according to the specified nodeGrouping
     */
    private String getValidInterfaceTypes(final NodeInfo nodeInfo) {
        String validInterfaceTypes = "";
        // use "(?i)val1|val2|val3" if case sensitivity

        logger.debug("Node grouping of the IMPORT file is : {}", nodeInfo.getNodeGrouping());
        if (NodeGrouping.ENODEB.name().equals(nodeInfo.getNodeGrouping().name())) {
            validInterfaceTypes = "s1|uu|x2";
        } else if (NodeGrouping.MME.name().equals(nodeInfo.getNodeGrouping().name())) {
            validInterfaceTypes = "s1_mme|s3_s16|s6a|s11|sv|iu|gb|slg|sls|gr|gn_gp|sgs|s6d|s4|s3_s10";
        }
        logger.debug("Returning valid interface types : {}", validInterfaceTypes);
        return validInterfaceTypes;
    }
}
