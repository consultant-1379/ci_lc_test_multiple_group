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

package com.ericsson.oss.services.pm.upgrade;

import static com.ericsson.oss.pmic.dto.subscription.enums.CellTraceCategory.CELLTRACE;
import static com.ericsson.oss.pmic.dto.subscription.enums.CellTraceCategory.CELLTRACE_AND_EBSL_FILE;
import static com.ericsson.oss.pmic.dto.subscription.enums.CellTraceCategory.CELLTRACE_AND_EBSL_STREAM;
import static com.ericsson.oss.pmic.dto.subscription.enums.CellTraceCategory.EBSL_STREAM;
import static com.ericsson.oss.services.pm.initiation.utils.CommonUtil.isNotEmptyCollection;

import java.util.List;
import javax.ejb.Asynchronous;
import javax.ejb.Stateless;
import javax.inject.Inject;

import org.slf4j.Logger;

import com.ericsson.oss.pmic.dto.ModelInfo;
import com.ericsson.oss.pmic.dto.subscription.AccessType;
import com.ericsson.oss.pmic.dto.subscription.CellTraceSubscription;
import com.ericsson.oss.pmic.dto.subscription.EbmSubscription;
import com.ericsson.oss.pmic.dto.subscription.ResourceSubscription;
import com.ericsson.oss.pmic.dto.subscription.Subscription;
import com.ericsson.oss.pmic.dto.subscription.cdts.CounterInfo;
import com.ericsson.oss.pmic.dto.subscription.enums.CellTraceCategory;
import com.ericsson.oss.pmic.dto.subscription.enums.SubscriptionType;
import com.ericsson.oss.services.pm.exception.DataAccessException;
import com.ericsson.oss.services.pm.services.generic.SubscriptionReadOperationService;
import com.ericsson.oss.services.pm.services.generic.SubscriptionWriteOperationService;

/**
 * Utility class for post upgrade Handler.
 */
@Stateless
public class PostUpgradeHandler {

    @Inject
    private Logger logger;
    @Inject
    private SubscriptionReadOperationService subscriptionReadOperationService;
    @Inject
    private SubscriptionWriteOperationService subscriptionWriteOperationService;

    /**
     * Update subscription attributes during upgrade.
     */
    @Asynchronous
    public void updateAttributeForUpgrade() {
        logger.info("Updating subscription attributes on startup");
        updateCelltraceCategory();
        updateAccessType();
    }

    /**
     * Update if accessType field with default value for all existing subscriptions of model version 2.2.0
     */
    private void updateAccessType() {
        try {
            final List<Subscription> subscriptions = subscriptionReadOperationService
                    .findAllBySubscriptionModelInfo(getSubscriptionModelInfo(), false);
            logger.info("Found {} subscriptions to update accessType", subscriptions.size());
            for (final Subscription subscription : subscriptions) {
                if (subscription.getAccessType() == null) {
                    subscription.setAccessType(AccessType.FULL);
                    subscriptionWriteOperationService.saveOrUpdate(subscription);
                }
            }
        } catch (final DataAccessException exception) {
            logger.error("Error while updating Subscription accessType field: {}", exception.getMessage());
        }
    }

    /**
     * will set the attribute number of nodes for all resources subscription and "ebsEnabled=true" for EBM
     * subscriptions containing ebsCounters.
     */
    private void updateCelltraceCategory() {
        try {
            final List<Subscription> subscriptions = subscriptionReadOperationService
                    .findAllBySubscriptionModelInfo(SubscriptionType.RESOURCE.getModelInfo(), true);
            for (final Subscription subscription : subscriptions) {
                boolean updateOnDps = shallUpdateNumberOfNodes((ResourceSubscription) subscription);
                if (subscription instanceof EbmSubscription && "1.2.0".equals(subscription.getModelVersion())) {
                    updateOnDps |= shallUpdateEbsEnabled((EbmSubscription) subscription);
                }
                if (isCelltraceSubscriptionInstanceWithSpecifiedVersion(subscription)) {
                    updateOnDps |= shallUpdateCelltraceCategory((CellTraceSubscription) subscription);
                }
                if (updateOnDps) {
                    subscriptionWriteOperationService.saveOrUpdate(subscription);
                }
            }
        } catch (final IllegalArgumentException | DataAccessException e) {
            logger.error("Error while updating Subscription: {}", e.getMessage());
            logger.info("Error while updating Subscription", e);
        }

    }

    private boolean shallUpdateCelltraceCategory(final CellTraceSubscription subscription) {

        final CellTraceCategory cellTraceCategory = subscription.getCellTraceCategory();

        if (cellTraceCategory == null) {
            final boolean containsCounters = isNotEmptyCollection(subscription.getEbsCounters());
            final boolean containsEbsEvents = isNotEmptyCollection(subscription.getEbsEvents());
            final boolean containsEvents = isNotEmptyCollection(subscription.getEvents());

            if (containsCounters && containsEbsEvents && containsEvents) {
                subscription.setCellTraceCategory(CELLTRACE_AND_EBSL_STREAM);
            } else if (containsCounters && containsEbsEvents) {
                subscription.setCellTraceCategory(EBSL_STREAM);
            } else if (containsCounters) {
                subscription.setCellTraceCategory(CELLTRACE_AND_EBSL_FILE);
            } else {
                subscription.setCellTraceCategory(CELLTRACE);
            }
            return true;
        }
        return false;
    }

    private boolean isCelltraceSubscriptionInstanceWithSpecifiedVersion(final Subscription subscription) {
        return (SubscriptionType.CELLTRACE.equals(subscription.getType()) && "2.1.0".equals(subscription.getModelVersion())) || (
                SubscriptionType.CONTINUOUSCELLTRACE.equals(subscription.getType()) && "1.1.0".equals(subscription.getModelVersion()));
    }

    private boolean shallUpdateEbsEnabled(final EbmSubscription subscription) {
        final List<CounterInfo> ebsCounters = subscription.getEbsCounters();
        final Boolean ebsEnabled = subscription.isEbsEnabled();
        if (ebsCounters != null && !ebsCounters.isEmpty() && (ebsEnabled == null || !ebsEnabled)) {
            subscription.setEbsEnabled(true);
            return true;
        }
        return false;
    }

    private boolean shallUpdateNumberOfNodes(final ResourceSubscription subscription) {
        final int nodeSize = subscription.getNodes().size();
        if (subscription.getNumberOfNodes() != nodeSize) {
            subscription.setNumberOfNodes(nodeSize);
            return true;
        }
        return false;
    }

    private ModelInfo getSubscriptionModelInfo() {
        return new ModelInfo() {
            @Override
            public String getModelType() {
                return Subscription.SUBSCRIPTION_MODEL_TYPE;
            }

            @Override
            public String getModelNamespace() {
                return Subscription.SUBSCRIPTION_MODEL_NAMESPACE;
            }

            @Override
            public String getModelVersion() {
                return Subscription.SUBSCRIPTION_MODEL_VERSION;
            }

            @Override
            public boolean hasAssociations() {
                return false;
            }
        };
    }

}