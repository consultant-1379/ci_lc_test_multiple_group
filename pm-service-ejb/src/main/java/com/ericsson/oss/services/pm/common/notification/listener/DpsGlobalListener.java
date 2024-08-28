/*------------------------------------------------------------------------------
 *******************************************************************************
 * COPYRIGHT Ericsson 2015
 *
 * The copyright to the computer program(s) herein is the property of
 * Ericsson Inc. The programs may be used and/or copied only with written
 * permission from Ericsson Inc. or in accordance with the terms and
 * conditions stipulated in the agreement/contract under which the
 * program(s) have been supplied.
 *******************************************************************************
 *----------------------------------------------------------------------------*/

package com.ericsson.oss.services.pm.common.notification.listener;

import static com.ericsson.oss.itpf.datalayer.dps.notification.DpsNotificationConfiguration.DPS_EVENT_NOTIFICATION_CHANNEL_URI;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Observes;
import javax.inject.Inject;

import com.ericsson.oss.itpf.datalayer.dps.notification.event.DpsConnectionEvent;
import com.ericsson.oss.itpf.datalayer.dps.notification.event.DpsDataBucketEvent;
import com.ericsson.oss.itpf.sdk.eventbus.annotation.Consumes;
import com.ericsson.oss.services.pm.common.notification.EventHandler;
import com.ericsson.oss.services.pm.common.notification.router.EventRouter;

/**
 * Listens for DPS events and dispatch them to the registered event handlers. The objective of this listener is to reduce the number of observers
 * using the annotation {@link Consumes}. Instead of having a lot of observers, we register only 3 observers and dispatch the event to the interested
 * event handlers.
 *
 * @see EventRouter
 * @see EventHandler
 */
@ApplicationScoped
public class DpsGlobalListener {

    private static final String CONNECTION_FILTER = "(database IS NOT NULL) AND (container IS NOT NULL)";

    private static final String SUBSCRIPTION_BUCKET_FILTER =
        "((bucketName IS NOT NULL) AND (bucketName = 'Live')) "
            + "AND ((namespace IS NOT NULL) AND (namespace = 'pmic_subscription' "
            + "OR namespace = 'pmic_stat_subscription' "
            + "OR namespace = 'pmic_ebs_subscription' "
            + "OR namespace = 'pmic_cell_subscription' "
            + "OR namespace = 'pmic_ebm_subscription' "
            + "OR namespace = 'pmic_event_subscription' "
            + "OR namespace = 'pmic_ue_subscription' "
            + "OR namespace = 'pmic_continuous_cell_subscription' "
            + "OR namespace = 'pmic_bscrecordings_subscription' "
            + "OR namespace = 'pmic_mtr_subscription' "
            + "OR namespace = 'pmic_moinstance_subscription' "
            + "OR namespace = 'pmic_cellrelation_subscription' "
            + "OR namespace = 'pmic_ctum_subscription' "
            + "OR namespace = 'pmic_celltraffic_subscription' "
            + "OR namespace = 'pmic_uetr_subscription' "
            + "OR namespace = 'pmic_gpeh_subscription' "
            + "OR namespace = 'pmic_res_subscription' "
            + "OR namespace = 'pmic_rpmo_subscription' "
            + "OR namespace = 'pmic_rtt_subscription') "
            + "AND type <> 'PMICScannerInfo' AND (attributeName LIKE '%administrationState%'))";
    private static final String RNC_FEATURE_FILTER ="((bucketName IS NOT NULL) AND (bucketName = 'Live')) AND (type ='RncFeature')";
    private static final String NODE_RECONNECT_FILTER ="((bucketName IS NOT NULL) AND (bucketName = 'Live')) AND (type ='CmFunction')";
    private static final String PM_FUNCTION_FILTER ="((bucketName IS NOT NULL) AND (bucketName = 'Live')) AND (type ='PmFunction')";
    private static final String PMIC_SUB_SCANNER_FILTER ="((bucketName IS NOT NULL) AND (bucketName = 'Live')) AND (type ='PMICSubScannerInfo')";
    private static final String PMIC_JOB_INFO_FILTER ="((bucketName IS NOT NULL) AND (bucketName = 'Live')) AND (type ='PMICJobInfo')";
    private static final String PMIC_SCANNER_FILTER = "((bucketName IS NOT NULL) AND (bucketName = 'Live')) AND (type ='PMICScannerInfo' OR type "
               + "='PMICMtrScannerInfo')";
    private static final String PMIC_UE_SCANNER_FILTER = "((bucketName IS NOT NULL) AND (bucketName = 'Live')) AND (type ='PMICUeScannerInfo')";
    private EventRouter eventRouter;

    /**
     * Init.
     *
     * @param eventRouter
     *     the event router
     */
    @Inject
    protected void init(final EventRouter eventRouter) {
        this.eventRouter = eventRouter;
    }

    /**
     * On event.
     *
     * @param event
     *     the dps connection event
     */
    public void onEvent(@Observes @Consumes(endpoint = DPS_EVENT_NOTIFICATION_CHANNEL_URI, filter = CONNECTION_FILTER)
                        final DpsConnectionEvent event) {
        eventRouter.route(event);
    }

    /**
     * Listens for an {@code DpsDataBucketEvent event on the DPS channel}</b> This event is further filtered on the Subscription Object</b>
     * <p>
     *
     * @param event
     *            {@code DpsDataBucketEvent event on the DPS channel}
     */
    public void processSubscriptionNotification(
               @Observes @Consumes(endpoint = DPS_EVENT_NOTIFICATION_CHANNEL_URI, filter = SUBSCRIPTION_BUCKET_FILTER) final DpsDataBucketEvent event) {
        eventRouter.route(event);
    }

    /**
     * Listens for an {@code DpsDataBucketEvent event on the DPS channel}</b> This event is further filtered on the CmFunction Object to get the node
     * reconnect notification</b>
     *
     * @param event
     *            {@code DpsDataBucketEvent event on the DPS channel}
     */
    public void processNodeReconnectNotification(
               @Observes @Consumes(endpoint = DPS_EVENT_NOTIFICATION_CHANNEL_URI, filter = NODE_RECONNECT_FILTER) final DpsDataBucketEvent event) {
        eventRouter.route(event);
    }

    /**
     * Listens for an {@code DpsDataBucketEvent event on the DPS channel}</b> This event is further filtered on the RncFeature Object </b>
     *
     * @param event
     *            {@code DpsDataBucketEvent event on the DPS channel}
     */
    public void processRncFeatureNotification(
               @Observes @Consumes(endpoint = DPS_EVENT_NOTIFICATION_CHANNEL_URI, filter = RNC_FEATURE_FILTER) final DpsDataBucketEvent event) {
        eventRouter.route(event);
    }

    /**
     * Listens for an {@code DpsDataBucketEvent event on the DPS channel}</b> This event is further filtered on the PmFunction Object </b>
     *
     * @param event
     *            {@code DpsDataBucketEvent event on the DPS channel}
     */
    public void processPmFunctionNotification(
               @Observes @Consumes(endpoint = DPS_EVENT_NOTIFICATION_CHANNEL_URI, filter = PM_FUNCTION_FILTER) final DpsDataBucketEvent event) {
        eventRouter.route(event);
    }

    /**
     * Listens for an {@code DpsDataBucketEvent event on the DPS channel}</b> This event is further filtered on the PMICSubScannerInfo Object </b>
     *
     * @param event
     *            {@code DpsDataBucketEvent event on the DPS channel}
     */
    public void processPmSubScannerNotification(
               @Observes @Consumes(endpoint = DPS_EVENT_NOTIFICATION_CHANNEL_URI, filter = PMIC_SUB_SCANNER_FILTER) final DpsDataBucketEvent event) {
        eventRouter.route(event);
    }

    /**
     * Listens for an {@code DpsDataBucketEvent event on the DPS channel}</b> This event is further filtered on the PMICJobInfo Object </b>
     *
     * @param event
     *            {@code DpsDataBucketEvent event on the DPS channel}
     */
    public void processPmicJobInfoNotification(
               @Observes @Consumes(endpoint = DPS_EVENT_NOTIFICATION_CHANNEL_URI, filter = PMIC_JOB_INFO_FILTER) final DpsDataBucketEvent event) {
        eventRouter.route(event);
    }

    /**
     * Listens for an {@code DpsDataBucketEvent event on the DPS channel}</b> This event is further filtered on the PMICScannerInfo and
     * PMICMtrScannerInfo Objects </b>
     *
     * @param event
     *            {@code DpsDataBucketEvent event on the DPS channel}
     */
    public void processPmScannerNotification(
               @Observes @Consumes(endpoint = DPS_EVENT_NOTIFICATION_CHANNEL_URI, filter = PMIC_SCANNER_FILTER) final DpsDataBucketEvent event) {
        eventRouter.route(event);
    }

    /**
     * Listens for an {@code DpsDataBucketEvent event on the DPS channel}</b> This event is further filtered on the PMICUeScannerInfo Object </b>
     *
     * @param event
     *            {@code DpsDataBucketEvent event on the DPS channel}
     */
    public void processPmUeScannerNotification(
               @Observes @Consumes(endpoint = DPS_EVENT_NOTIFICATION_CHANNEL_URI, filter = PMIC_UE_SCANNER_FILTER) final DpsDataBucketEvent event) {
        eventRouter.route(event);
    }
}
