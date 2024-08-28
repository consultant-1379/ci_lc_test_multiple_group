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

package com.ericsson.oss.services.pm.initiation.pmjobs.helper.subscriptions;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Any;
import javax.enterprise.inject.Instance;
import javax.enterprise.util.AnnotationLiteral;
import javax.inject.Inject;

import org.slf4j.Logger;

import com.ericsson.oss.pmic.dto.scanner.enums.ProcessType;
import com.ericsson.oss.services.pm.initiation.pmjobs.helper.subscriptions.qualifier.ProcessTypeValidation;

/**
 * The Subscription info provider factory.
 */
@ApplicationScoped
public class SubscriptionInfoProviderFactory {

    private static final ProcessType[] SUPPORTED_SUBSCRIPTIONS_FOR_SYNCING = {ProcessType.UETRACE, ProcessType.CTUM};

    @Inject
    private Logger logger;

    @Inject
    @Any
    private Instance<SubscriptionInfo> subscriptionInfoValidator;

    /**
     * Gets subscriptionInfo based on processType
     *
     * @param processType
     *         process type CTUM/UETRACE
     *
     * @return subscriptionInfo subscription info
     */
    public SubscriptionInfo getSubscriptionInfo(final ProcessType processType) {
        final SubscriptionInfoAnnotationLiteral selector = new SubscriptionInfoAnnotationLiteral(processType);

        final Instance<SubscriptionInfo> selectedInstance = subscriptionInfoValidator.select(selector);

        if (selectedInstance.isUnsatisfied()) {
            logger.error("Process Type: {} is not currently supported ", processType);
            throw new UnsupportedOperationException("Process Type: " + processType + " is not currently supported");
        }
        return selectedInstance.get();
    }

    /**
     * Gets all the process type which supports PmJob instead of PmicScanner
     *
     * @return Array of process types supported
     */
    public ProcessType[] getSupportedProcessTypesForPmJob() {
        return SUPPORTED_SUBSCRIPTIONS_FOR_SYNCING;
    }

    /**
     * The Subscription info annotation.
     */
    @SuppressWarnings("all")
    class SubscriptionInfoAnnotationLiteral extends AnnotationLiteral<ProcessTypeValidation>
            implements ProcessTypeValidation {
        private final ProcessType processType;

        /**
         * Instantiates a new Subscription info annotation literal.
         *
         * @param processType
         *         the process type
         */
        SubscriptionInfoAnnotationLiteral(final ProcessType processType) {
            this.processType = processType;
        }

        @Override
        public ProcessType processType() {
            return processType;
        }
    }
}
