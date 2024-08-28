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

package com.ericsson.oss.services.pm.initiation.notification.handlers;

import javax.ejb.Stateless;
import javax.inject.Inject;

import org.slf4j.Logger;

import com.ericsson.oss.pmic.api.utils.FdnUtil;
import com.ericsson.oss.pmic.dto.subscription.Subscription;
import com.ericsson.oss.services.pm.collection.notification.handlers.initiationresponsecache.handlers.InitiationResponseCacheHelper;
import com.ericsson.oss.services.pm.scheduling.cluster.MembershipListener;
import com.ericsson.oss.services.pm.scheduling.impl.DelayedTaskStatusValidator;

/**
 * This class can handle behavior related to PmSubScanner deleted event
 */
@Stateless
public class PmSubScannerDeleteOperationHandler {

    @Inject
    private Logger logger;

    @Inject
    private MembershipListener membershipListener;

    @Inject
    private InitiationResponseCacheHelper initiationResponseCacheHelper;

    @Inject
    private DelayedTaskStatusValidator delayedTaskStatusValidator;

    /**
     * On Scanner create notification this method will update initiation cache and starts file collection
     *
     * @param subScannerFdn
     *         Sub Scanner FDN
     * @param subscriptionId
     *         subscriptionId
     */
    public void execute(final String subScannerFdn, final String subscriptionId) {
        if (membershipListener.isMaster()) {
            if (Subscription.isValidSubscriptionId(subscriptionId)) {
                final String nodeFdn = FdnUtil.getRootParentFdnFromChild(subScannerFdn);
                logger.debug("processing notification for {} : {}", subScannerFdn, nodeFdn);
                initiationResponseCacheHelper.processInitiationResponseCacheForDeactivation(subscriptionId, nodeFdn);
                logger.debug("Performing task status validation for subscription {} after timeout", subscriptionId);
                delayedTaskStatusValidator.scheduleDelayedTaskStatusValidation(Long.valueOf(subscriptionId));
            } else {
                logger.debug("Subscription ID is not valid for SubScanner {}", subScannerFdn);
            }
        }
    }
}
