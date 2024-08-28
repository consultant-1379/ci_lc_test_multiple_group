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

package com.ericsson.oss.services.pm.adjuster.impl;

import static java.util.Collections.emptyList;

import static com.ericsson.oss.pmic.dto.subscription.enums.CellTraceCategory.CELLTRACE;
import static com.ericsson.oss.pmic.dto.subscription.enums.CellTraceCategory.CELLTRACE_AND_EBSL_FILE;
import static com.ericsson.oss.pmic.dto.subscription.enums.CellTraceCategory.CELLTRACE_AND_EBSL_STREAM;
import static com.ericsson.oss.pmic.dto.subscription.enums.CellTraceCategory.EBSL_STREAM;
import static com.ericsson.oss.services.pm.initiation.constants.ApplicationMessages.COUNTERS_NOT_ALLOWED_FOR_EBS_FILE_WITH_STREAMING_OUTPUTMODE;
import static com.ericsson.oss.services.pm.initiation.utils.CommonUtil.isNotEmptyCollection;

import java.util.ArrayList;
import java.util.List;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import org.slf4j.Logger;

import com.ericsson.oss.pmic.dto.subscription.CellTraceSubscription;
import com.ericsson.oss.pmic.dto.subscription.cdts.CounterInfo;
import com.ericsson.oss.pmic.dto.subscription.cdts.EventInfo;
import com.ericsson.oss.pmic.dto.subscription.enums.CellTraceCategory;
import com.ericsson.oss.pmic.dto.subscription.enums.OutputModeType;
import com.ericsson.oss.services.pm.adjuster.SubscriptionDataAdjusterQualifier;
import com.ericsson.oss.services.pm.ebs.utils.EbsConfigurationListener;
import com.ericsson.oss.services.pm.ebs.utils.EbsSubscriptionHelper;
import com.ericsson.oss.services.pm.ebs.utils.EbsStreamInfoResolver;
import com.ericsson.oss.services.pm.exception.DataAccessException;

/**
 * CellTraceSubscriptionDataAdjuster.
 * Corrects and updates CELLTRACE subscription data
 */
@ApplicationScoped
@SubscriptionDataAdjusterQualifier(subscriptionClass = CellTraceSubscription.class)
public class CellTraceSubscriptionDataAdjuster extends EventSubscriptionDataAdjuster<CellTraceSubscription> {

    @Inject
    private Logger logger;

    @Inject
    private EbsStreamInfoResolver ebsStreamInfoResolver;

    @Inject
    private EbsSubscriptionHelper ebsSubscriptionHelper;

    @Inject
    private EbsConfigurationListener ebsConfigurationListener;

    @Override
    public void updateImportedSubscriptionWithCorrectValues(final CellTraceSubscription subscription) throws DataAccessException {
        super.updateImportedSubscriptionWithCorrectValues(subscription);
        correctCelltraceCategory(subscription);
        if (ebsSubscriptionHelper.isEbsFileOnlyCategory(subscription) && subscription.getOutputMode().equals(OutputModeType.STREAMING)) {
            logger.warn(COUNTERS_NOT_ALLOWED_FOR_EBS_FILE_WITH_STREAMING_OUTPUTMODE, subscription.getName());
        }
    }

    @Override
    public void adjustPfmSubscriptionData(final CellTraceSubscription subscription) {
        super.adjustPfmSubscriptionData(subscription);
        correctSubscriptionData(subscription);
    }

    @Override
    public void correctSubscriptionData(final CellTraceSubscription subscription) {
        super.correctCelltraceSubscriptionData(subscription);
        subscription.setEbsCounters(getCorrectEbsCounters(subscription));
        subscription.setEbsEvents(getCorrectEbsEvents(subscription));
        subscription.setEvents(getCorrectEvents(subscription));
    }

    private List<CounterInfo> getCorrectEbsCounters(final CellTraceSubscription subscription) {
        if (ebsSubscriptionHelper.isCellTraceEbs(subscription)) {
            return subscriptionPfmData.getCorrectCounters(subscription.getName(), subscription.getEbsCounters(),
                    subscription.getNodesTypeVersion(),
                    ebsSubscriptionHelper.getEbsModelDefiners(subscription.getCellTraceCategory()),
                    ebsSubscriptionHelper.getEbsFlexModelDefiners(subscription.getCellTraceCategory()), true);
        } else {
            return emptyList();
        }
    }

    private List<EventInfo> getCorrectEbsEvents(final CellTraceSubscription subscription) {
        if (ebsSubscriptionHelper.isEbsStream(subscription)) {
            subscription.setEbsStreamInfoList(ebsStreamInfoResolver.getStreamingDestination(subscription.getCellTraceCategory()));
            return getEbsEvents(subscription);
        } else {
            return emptyList();
        }
    }

    private List<EventInfo> getEbsEvents(final CellTraceSubscription subscription) {
        if (ebsSubscriptionHelper.isEbsStreamExcludingEsnCategory(subscription)) {
            return subscriptionPfmData.getCorrectEvents(subscription.getName(), subscription.getEbsEvents(), subscription.getNodesTypeVersion());
        }
        return subscriptionPfmData.getEsnApplicableEvents(subscription.getNodesTypeVersion());
    }

    private List<EventInfo> getCorrectEvents(final CellTraceSubscription subscription) {
        if (!ebsSubscriptionHelper.isEbsStreamOnlyCategory(subscription)) {
            return subscriptionPfmData.getCorrectEvents(subscription.getName(), subscription.getEvents(), subscription.getNodesTypeVersion());
        }
        return emptyList();
    }

    private void correctCelltraceCategory(final CellTraceSubscription subscription) {
        final CellTraceCategory cellTraceCategory = subscription.getCellTraceCategory();
        if (cellTraceCategory == null || CELLTRACE.equals(cellTraceCategory)) {
            final boolean containsCounters = isNotEmptyCollection(subscription.getEbsCounters());
            if (containsCounters) {
                determineCategoryBasedOnEbsCounters(subscription);
            } else {
                subscription.setCellTraceCategory(CELLTRACE);
            }
        }
    }

    private void determineCategoryBasedOnEbsCounters(final CellTraceSubscription subscription) {
        if (ebsConfigurationListener.isEbsStreamClusterDeployed()) {
            final boolean containsEvents = isNotEmptyCollection(subscription.getEvents());
            if (containsEvents) {
                subscription.setCellTraceCategory(CELLTRACE_AND_EBSL_STREAM);
            } else {
                subscription.setCellTraceCategory(EBSL_STREAM);
                subscription.setEvents(new ArrayList<>(0));
            }
        } else {
            subscription.setCellTraceCategory(CELLTRACE_AND_EBSL_FILE);
            subscription.setEbsEvents(new ArrayList<>(0));
        }
    }
}
