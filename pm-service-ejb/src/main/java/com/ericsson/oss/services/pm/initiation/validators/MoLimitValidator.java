/*------------------------------------------------------------------------------
 *******************************************************************************
 * COPYRIGHT Ericsson 2019
 *
 * The copyright to the computer program(s) herein is the property of
 * Ericsson Inc. The programs may be used and/or copied only with written
 * permission from Ericsson Inc. or in accordance with the terms and
 * conditions stipulated in the agreement/contract under which the
 * program(s) have been supplied.
 *******************************************************************************
 *----------------------------------------------------------------------------*/

package com.ericsson.oss.services.pm.initiation.validators;

import com.ericsson.oss.pmic.dto.subscription.StatisticalSubscription;
import com.ericsson.oss.services.pm.initiation.api.ReadPMICConfigurationLocal;
import com.ericsson.oss.services.pm.initiation.ejb.PMICConfigParameter;
import com.ericsson.oss.services.pm.services.exception.ConfigurationParameterException;
import com.ericsson.oss.services.pm.services.exception.ValidationException;
import org.slf4j.Logger;

import javax.inject.Inject;
import java.util.List;
import java.util.Map;

import static com.ericsson.oss.services.pm.initiation.constants.ApplicationMessages.COUNTER_CELLS_ALLOWED_EXCEEDED;

/**
 * Validate that the number of mos in subscription does not exceed max allowed.
 */
public class MoLimitValidator {
    @Inject
    private Logger logger;
    @Inject
    private ReadPMICConfigurationLocal readConfiguration;

    void validateLimit(final StatisticalSubscription subscription, final Map<String, List<String>> moMap)
            throws ValidationException {
        final StringBuilder noExceedsNodes = new StringBuilder();
        final String maxAllowed;
        try {
            maxAllowed = readConfiguration.getConfigParamValue(PMICConfigParameter.MAXNOOFMOINSTANCEALLOWED.toString());
        } catch (final ConfigurationParameterException e) {
            throw new ValidationException(e.getMessage(), e);
        }
        logger.debug("PIB value for maxNoOfMoinstancesAllowed :{}.", maxAllowed);
        for (final Map.Entry<String, List<String>> entry : moMap.entrySet()) {
            final long noOfCellsAndCounters = subscription.getCounters().size() * (long) entry.getValue().size();
            if (noOfCellsAndCounters > Integer.parseInt(maxAllowed)) {
                logger.info("Number of counter * instances :{} selected per RNC for {} exceeds, the supported maximum total limit of {}.",
                        noOfCellsAndCounters, entry.getKey(), maxAllowed);
                if (noExceedsNodes.length() > 0) {
                    noExceedsNodes.append(", ");
                }
                noExceedsNodes.append(entry.getKey());
            }
        }
        if (noExceedsNodes.length() > 0) {
            throw new ValidationException(String.format(COUNTER_CELLS_ALLOWED_EXCEEDED, noExceedsNodes, maxAllowed));
        }
    }
}
