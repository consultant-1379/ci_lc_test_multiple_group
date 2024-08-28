/*******************************************************************************
 * COPYRIGHT Ericsson 2016
 * <p>
 * The copyright to the computer program(s) herein is the property of
 * Ericsson Inc. The programs may be used and/or copied only with written
 * permission from Ericsson Inc. or in accordance with the terms and
 * conditions stipulated in the agreement/contract under which the
 * program(s) have been supplied.
 ******************************************************************************/

package com.ericsson.oss.services.pm.instrumentation;

import static com.ericsson.oss.services.pm.initiation.util.constants.SubscriptionDPSConstant.PMIC_ATT_SUBSCRIPTION_ADMINSTATE;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Observes;
import javax.inject.Inject;

import org.slf4j.Logger;

import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.Timer;
import com.ericsson.oss.itpf.datalayer.dps.notification.DpsNotificationConfiguration;
import com.ericsson.oss.itpf.datalayer.dps.notification.event.AttributeChangeData;
import com.ericsson.oss.itpf.datalayer.dps.notification.event.DpsAttributeChangedEvent;
import com.ericsson.oss.itpf.sdk.eventbus.annotation.Consumes;
import com.ericsson.oss.itpf.sdk.instrument.MetricsUtil;
import com.ericsson.oss.itpf.sdk.instrument.annotation.InstrumentedBean;
import com.ericsson.oss.itpf.sdk.instrument.annotation.MonitoredAttribute;
import com.ericsson.oss.pmic.dto.subscription.Subscription;
import com.ericsson.oss.pmic.dto.subscription.enums.AdministrationState;
import com.ericsson.oss.pmic.dto.subscription.enums.SubscriptionType;
import com.ericsson.oss.services.pm.exception.DataAccessException;
import com.ericsson.oss.services.pm.exception.RetryServiceException;
import com.ericsson.oss.services.pm.scheduling.cluster.MembershipListener;
import com.ericsson.oss.services.pm.services.generic.SubscriptionReadOperationService;

/**
 * The type Subscription instrumentation.
 */
@ApplicationScoped
@InstrumentedBean(displayName = "Subscription Instrumentation")
@SuppressWarnings("PMD.ExcessivePublicCount")
public class SubscriptionInstrumentation {

    public static final String SUBSCRIPTION_ACTIVATION_DURATION_FOR_STATUS_TO_GO_TO_ACTIVE =
            "subscription_instrumentation.common.duration_for_status_to_go_to_active";

    public static final String STATISTICAL_SUBSCRIPTION_ACTIVATION_DURATION_FOR_STATUS_TO_GO_TO_ACTIVE =
            "subscription_instrumentation.statistical_subscription.duration_for_status_to_go_to_active";

    public static final String CELLTRACE_SUBSCRIPTION_ACTIVATION_DURATION_FOR_STATUS_TO_GO_TO_ACTIVE =
            "subscription_instrumentation.celltrace_subscription.duration_for_status_to_go_to_active";

    public static final String SUBSCRIPTION_DEACTIVATION_DURATION_FOR_STATUS_TO_GO_TO_INACTIVE =
            "subscription_instrumentation.duration_for_status_to_go_to_inactive";

    public static final String STATISTICAL_SUBSCRIPTION_DEACTIVATION_DURATION_FOR_STATUS_TO_GO_TO_INACTIVE =
            "subscription_instrumentation.statistical_subscription.duration_for_status_to_go_to_inactive";

    public static final String CELLTRACE_SUBSCRIPTION_DEACTIVATION_DURATION_FOR_STATUS_TO_GO_TO_INACTIVE =
            "subscription_instrumentation.celltrace_subscription.duration_for_status_to_go_to_inactive";

    public static final String EBM_SUBSCRIPTION_ACTIVATION_DURATION_FOR_STATUS_TO_GO_TO_ACTIVE =
            "subscription_instrumentation.ebm_subscription.duration_for_status_to_go_to_active";

    public static final String EBM_SUBSCRIPTION_DEACTIVATION_DURATION_FOR_STATUS_TO_GO_TO_INACTIVE =
            "subscription_instrumentation.ebm_subscription.duration_for_status_to_go_to_inactive";

    public static final String CCTR_SUBSCRIPTION_ACTIVATION_DURATION_FOR_STATUS_TO_GO_TO_ACTIVE =
            "subscription_instrumentation.continuous_celltrace_subscription.duration_for_status_to_go_to_active";

    public static final String CCTR_SUBSCRIPTION_DEACTIVATION_DURATION_FOR_STATUS_TO_GO_TO_INACTIVE =
            "subscription_instrumentation.continuous_celltrace_subscription.duration_for_status_to_go_to_inactive";

    private static final String SEPARATOR = "|";
    private final Map<String, Timer.Context> timerContexts = new ConcurrentHashMap<>();

    @Inject
    private Logger log;
    @Inject
    private MembershipListener membershipListener;
    @Inject
    private SubscriptionReadOperationService subscriptionReadOperationService;

    private MetricRegistry metricRegistry;

    /**
     * Post Construct method to initialise metric registry
     */
    @PostConstruct
    public void onInit() {
        metricRegistry = MetricsUtil.getRegistry();
    }

    /**
     * Pre Destroy method to clear timer context map
     */
    @PreDestroy
    public void onDestroy() {
        timerContexts.clear();
    }

    /**
     * On subscription change event.
     *
     * @param event
     *         - DpsAttributeChangedEvent object with old and new values for changed attributes
     */
    public void onSubscriptionChangeEvent(@Observes @Consumes(endpoint = DpsNotificationConfiguration.DPS_EVENT_NOTIFICATION_CHANNEL_URI,
            filter = "(namespace = 'pmic_subscription' OR namespace = 'pmic_event_subscription' "
                    + "OR namespace = 'pmic_stat_subscription' OR namespace = 'pmic_cell_subscription' "
                    + "OR namespace = 'pmic_continuous_cell_subscription' OR namespace = 'pmic_ebm_subscription' "
                    + "OR namespace = 'pmic_moinstance_subscription' OR namespace = 'pmic_ctum_subscription' "
                    + "OR namespace = 'pmic_celltraffic_subscription' OR namespace = 'pmic_uetr_subscription' "
                    + "OR namespace = 'pmic_gpeh_subscription' OR namespace = 'pmic_ebs_subscription') "
                    + "AND type <> 'PMICScannerInfo'") final DpsAttributeChangedEvent event) {

        if (!isTimerMetricEnabled() || !membershipListener.isMaster()) {
            return;
        }
        final Set<AttributeChangeData> changedAttributes = event.getChangedAttributes();
        for (final AttributeChangeData changedData : changedAttributes) {
            if (log.isTraceEnabled()) {
                log.trace("Name of the changed attribute : {}", changedData.getName());
                log.trace("Old Value of the changed attribute : {}", changedData.getOldValue());
                log.trace("Updated Value of the changed attribute : {}", changedData.getNewValue());
            }
            final Subscription subscription;
            try {
                subscription = subscriptionReadOperationService.findByIdWithRetry(event.getPoId(), false);
            } catch (final DataAccessException | RetryServiceException e) {
                log.error("DPS exception when tried to find a subscription with poId = {}: {}", event.getPoId(), e.getMessage());
                log.info("DPS exception when tried to find a subscription with poId = {}", event.getPoId(), e);
                continue;
            }

            if (subscription == null) {
                log.error("Subscription with id {} does not exist!", event.getPoId());
            } else {
                processSubscriptionInitiation(event.getPoId(), changedData, subscription);
            }
        }
    }

    private void processSubscriptionInitiation(final long poid, final AttributeChangeData changedData, final Subscription subscription) {
        if (isSubscriptionActivating(changedData)) {
            log.debug("Subscription is activating - SubscriptionId {}", poid);
            subscriptionActivationStarted(subscription);
        } else if (isSubscriptionActive(changedData)) {
            log.debug("Subscription is active - SubscriptionId {}", poid);
            subscriptionActivationEnded(subscription);
        } else if (isSubscriptionDeactivating(changedData)) {
            log.debug("Subscription is deactivating - SubscriptionId {}", poid);
            subscriptionDeactivationStarted(subscription);
        } else if (isSubscriptionInactive(changedData)) {
            log.debug("Subscription is inactive - SubscriptionId {}", poid);
            subscriptionDeactivationEnded(subscription);
        }
    }

    /**
     * Is timer metric enabled boolean.
     *
     * @return returns true if metric registry is not null
     */
    protected boolean isTimerMetricEnabled() {
        return metricRegistry != null; // see MetricsUtil.getRegistry() for details
    }

    /**
     * Start timer.
     *
     * @param name
     *         - name of the timer to be started
     * @param subscriptionId
     *         - id of the subscription
     */
    protected void startTimer(final String name, final String subscriptionId) {
        if (!isTimerMetricEnabled()) {
            return;
        }
        final Timer timer = metricRegistry.timer(name);
        final Timer.Context timerContext = timer.time();
        timerContexts.put(name + SEPARATOR + "subscriptionId = " + subscriptionId, timerContext);
    }

    /**
     * Stop timer.
     *
     * @param name
     *         - name of the timer to be stopped
     * @param subscriptionId
     *         - id of the subscription
     */
    protected void stopTimer(final String name, final String subscriptionId) {
        if (!isTimerMetricEnabled()) {
            return;
        }
        final Timer.Context timerContext = timerContexts.remove(name + SEPARATOR + "subscriptionId = " + subscriptionId);
        if (timerContext != null) {
            timerContext.stop();
        }
    }

    /**
     * Subscription activation started.
     *
     * @param subscription
     *         - subscription object to start activation started timer for
     */
    public void subscriptionActivationStarted(final Subscription subscription) {
        startTimer(SUBSCRIPTION_ACTIVATION_DURATION_FOR_STATUS_TO_GO_TO_ACTIVE, subscription.getIdAsString());
        if (subscription.getType() == SubscriptionType.STATISTICAL) {
            startTimer(STATISTICAL_SUBSCRIPTION_ACTIVATION_DURATION_FOR_STATUS_TO_GO_TO_ACTIVE, subscription.getIdAsString());
        } else if (subscription.getType() == SubscriptionType.CELLTRACE) {
            startTimer(CELLTRACE_SUBSCRIPTION_ACTIVATION_DURATION_FOR_STATUS_TO_GO_TO_ACTIVE, subscription.getIdAsString());
        } else if (subscription.getType() == SubscriptionType.EBM) {
            startTimer(EBM_SUBSCRIPTION_ACTIVATION_DURATION_FOR_STATUS_TO_GO_TO_ACTIVE, subscription.getIdAsString());
        } else if (subscription.getType() == SubscriptionType.CONTINUOUSCELLTRACE) {
            startTimer(CCTR_SUBSCRIPTION_ACTIVATION_DURATION_FOR_STATUS_TO_GO_TO_ACTIVE, subscription.getIdAsString());
        }
    }

    /**
     * Subscription activation ended.
     *
     * @param subscription
     *         - subscription object to stop activation started timer for
     */
    public void subscriptionActivationEnded(final Subscription subscription) {
        stopTimer(SUBSCRIPTION_ACTIVATION_DURATION_FOR_STATUS_TO_GO_TO_ACTIVE, subscription.getIdAsString());
        if (subscription.getType() == SubscriptionType.STATISTICAL) {
            stopTimer(STATISTICAL_SUBSCRIPTION_ACTIVATION_DURATION_FOR_STATUS_TO_GO_TO_ACTIVE, subscription.getIdAsString());
        } else if (subscription.getType() == SubscriptionType.CELLTRACE) {
            stopTimer(CELLTRACE_SUBSCRIPTION_ACTIVATION_DURATION_FOR_STATUS_TO_GO_TO_ACTIVE, subscription.getIdAsString());
        } else if (subscription.getType() == SubscriptionType.EBM) {
            stopTimer(EBM_SUBSCRIPTION_ACTIVATION_DURATION_FOR_STATUS_TO_GO_TO_ACTIVE, subscription.getIdAsString());
        } else if (subscription.getType() == SubscriptionType.CONTINUOUSCELLTRACE) {
            stopTimer(CCTR_SUBSCRIPTION_ACTIVATION_DURATION_FOR_STATUS_TO_GO_TO_ACTIVE, subscription.getIdAsString());
        }
    }

    /**
     * Subscription deactivation started.
     *
     * @param subscription
     *         - subscription object to start deactivation started timer for
     */
    public void subscriptionDeactivationStarted(final Subscription subscription) {
        startTimer(SUBSCRIPTION_DEACTIVATION_DURATION_FOR_STATUS_TO_GO_TO_INACTIVE, subscription.getIdAsString());
        if (subscription.getType() == SubscriptionType.STATISTICAL) {
            startTimer(STATISTICAL_SUBSCRIPTION_DEACTIVATION_DURATION_FOR_STATUS_TO_GO_TO_INACTIVE, subscription.getIdAsString());
        } else if (subscription.getType() == SubscriptionType.CELLTRACE) {
            startTimer(CELLTRACE_SUBSCRIPTION_DEACTIVATION_DURATION_FOR_STATUS_TO_GO_TO_INACTIVE, subscription.getIdAsString());
        } else if (subscription.getType() == SubscriptionType.EBM) {
            startTimer(EBM_SUBSCRIPTION_DEACTIVATION_DURATION_FOR_STATUS_TO_GO_TO_INACTIVE, subscription.getIdAsString());
        } else if (subscription.getType() == SubscriptionType.CONTINUOUSCELLTRACE) {
            startTimer(CCTR_SUBSCRIPTION_DEACTIVATION_DURATION_FOR_STATUS_TO_GO_TO_INACTIVE, subscription.getIdAsString());
        }
    }

    /**
     * Subscription deactivation ended.
     *
     * @param subscription
     *         - subscription object to stop deactivation started timer for
     */
    public void subscriptionDeactivationEnded(final Subscription subscription) {
        stopTimer(SUBSCRIPTION_DEACTIVATION_DURATION_FOR_STATUS_TO_GO_TO_INACTIVE, subscription.getIdAsString());
        if (subscription.getType() == SubscriptionType.STATISTICAL) {
            stopTimer(STATISTICAL_SUBSCRIPTION_DEACTIVATION_DURATION_FOR_STATUS_TO_GO_TO_INACTIVE, subscription.getIdAsString());
        } else if (subscription.getType() == SubscriptionType.CELLTRACE) {
            stopTimer(CELLTRACE_SUBSCRIPTION_DEACTIVATION_DURATION_FOR_STATUS_TO_GO_TO_INACTIVE, subscription.getIdAsString());
        } else if (subscription.getType() == SubscriptionType.EBM) {
            stopTimer(EBM_SUBSCRIPTION_DEACTIVATION_DURATION_FOR_STATUS_TO_GO_TO_INACTIVE, subscription.getIdAsString());
        } else if (subscription.getType() == SubscriptionType.CONTINUOUSCELLTRACE) {
            stopTimer(CCTR_SUBSCRIPTION_DEACTIVATION_DURATION_FOR_STATUS_TO_GO_TO_INACTIVE, subscription.getIdAsString());
        }
    }

    private boolean isSubscriptionActivating(final AttributeChangeData changedData) {
        return changedData.getName().equals(PMIC_ATT_SUBSCRIPTION_ADMINSTATE)
                && changedData.getNewValue().equals(AdministrationState.ACTIVATING.name());
    }

    private boolean isSubscriptionActive(final AttributeChangeData changedData) {
        return changedData.getName().equals(PMIC_ATT_SUBSCRIPTION_ADMINSTATE) && changedData.getNewValue().equals(AdministrationState.ACTIVE.name());
    }

    private boolean isSubscriptionDeactivating(final AttributeChangeData changedData) {
        return changedData.getName().equals(PMIC_ATT_SUBSCRIPTION_ADMINSTATE)
                && changedData.getNewValue().equals(AdministrationState.DEACTIVATING.name());
    }

    private boolean isSubscriptionInactive(final AttributeChangeData changedData) {
        return changedData.getName().equals(PMIC_ATT_SUBSCRIPTION_ADMINSTATE)
                && changedData.getNewValue().equals(AdministrationState.INACTIVE.name());
    }

    /**
     * Gets count of subscription activation duration for status to go to active.
     *
     * @return the count of subscription activation duration for status to go to active
     */
    @MonitoredAttribute(displayName = SUBSCRIPTION_ACTIVATION_DURATION_FOR_STATUS_TO_GO_TO_ACTIVE
            + ".count", visibility = MonitoredAttribute.Visibility.ALL, interval = MonitoredAttribute.Interval.ONE_MIN)
    public long getCountSubscriptionActivationDurationForStatusToGoToActive() {
        if (!isTimerMetricEnabled()) {
            return 0;
        }
        return metricRegistry.timer(SUBSCRIPTION_ACTIVATION_DURATION_FOR_STATUS_TO_GO_TO_ACTIVE).getCount();
    }

    /**
     * Gets max subscription activation duration for status to go to active.
     *
     * @return the max subscription activation duration for status to go to active
     */
    @MonitoredAttribute(displayName = SUBSCRIPTION_ACTIVATION_DURATION_FOR_STATUS_TO_GO_TO_ACTIVE
            + ".max", visibility = MonitoredAttribute.Visibility.ALL, interval = MonitoredAttribute.Interval.ONE_MIN,
            units = MonitoredAttribute.Units.NANO_SECONDS)
    public long getMaxSubscriptionActivationDurationForStatusToGoToActive() {
        if (!isTimerMetricEnabled()) {
            return 0;
        }
        return metricRegistry.timer(SUBSCRIPTION_ACTIVATION_DURATION_FOR_STATUS_TO_GO_TO_ACTIVE).getSnapshot().getMax();
    }

    /**
     * Gets median subscription activation duration for status to go to active.
     *
     * @return the median subscription activation duration for status to go to active
     */
    @MonitoredAttribute(displayName = SUBSCRIPTION_ACTIVATION_DURATION_FOR_STATUS_TO_GO_TO_ACTIVE
            + ".median", visibility = MonitoredAttribute.Visibility.ALL, interval = MonitoredAttribute.Interval.ONE_MIN,
            units = MonitoredAttribute.Units.NANO_SECONDS)
    public double getMedianSubscriptionActivationDurationForStatusToGoToActive() {
        if (!isTimerMetricEnabled()) {
            return 0;
        }
        return metricRegistry.timer(SUBSCRIPTION_ACTIVATION_DURATION_FOR_STATUS_TO_GO_TO_ACTIVE).getSnapshot().getMedian();
    }

    /**
     * Gets min subscription activation duration for status to go to active.
     *
     * @return the min subscription activation duration for status to go to active
     */
    @MonitoredAttribute(displayName = SUBSCRIPTION_ACTIVATION_DURATION_FOR_STATUS_TO_GO_TO_ACTIVE
            + ".min", visibility = MonitoredAttribute.Visibility.ALL, interval = MonitoredAttribute.Interval.ONE_MIN,
            units = MonitoredAttribute.Units.NANO_SECONDS)
    public long getMinSubscriptionActivationDurationForStatusToGoToActive() {
        if (!isTimerMetricEnabled()) {
            return 0;
        }
        return metricRegistry.timer(SUBSCRIPTION_ACTIVATION_DURATION_FOR_STATUS_TO_GO_TO_ACTIVE).getSnapshot().getMin();
    }

    /**
     * Gets count of statistical subscription activation duration for status to go to active.
     *
     * @return the count of statistical subscription activation duration for status to go to active
     */
    @MonitoredAttribute(displayName = STATISTICAL_SUBSCRIPTION_ACTIVATION_DURATION_FOR_STATUS_TO_GO_TO_ACTIVE
            + ".count", visibility = MonitoredAttribute.Visibility.ALL, interval = MonitoredAttribute.Interval.ONE_MIN)
    public long getCountStatisticalSubscriptionActivationDurationForStatusToGoToActive() {
        if (!isTimerMetricEnabled()) {
            return 0;
        }
        return metricRegistry.timer(STATISTICAL_SUBSCRIPTION_ACTIVATION_DURATION_FOR_STATUS_TO_GO_TO_ACTIVE).getCount();
    }

    /**
     * Gets max statistical subscription activation duration for status to go to active.
     *
     * @return the max statistical subscription activation duration for status to go to active
     */
    @MonitoredAttribute(displayName = STATISTICAL_SUBSCRIPTION_ACTIVATION_DURATION_FOR_STATUS_TO_GO_TO_ACTIVE
            + ".max", visibility = MonitoredAttribute.Visibility.ALL, interval = MonitoredAttribute.Interval.ONE_MIN,
            units = MonitoredAttribute.Units.NANO_SECONDS)
    public long getMaxStatisticalSubscriptionActivationDurationForStatusToGoToActive() {
        if (!isTimerMetricEnabled()) {
            return 0;
        }
        return metricRegistry.timer(STATISTICAL_SUBSCRIPTION_ACTIVATION_DURATION_FOR_STATUS_TO_GO_TO_ACTIVE).getSnapshot().getMax();
    }

    /**
     * Gets median statistical subscription activation duration for status to go to active.
     *
     * @return the median statistical subscription activation duration for status to go to active
     */
    @MonitoredAttribute(displayName = STATISTICAL_SUBSCRIPTION_ACTIVATION_DURATION_FOR_STATUS_TO_GO_TO_ACTIVE
            + ".median", visibility = MonitoredAttribute.Visibility.ALL, interval = MonitoredAttribute.Interval.ONE_MIN,
            units = MonitoredAttribute.Units.NANO_SECONDS)
    public double getMedianStatisticalSubscriptionActivationDurationForStatusToGoToActive() {
        if (!isTimerMetricEnabled()) {
            return 0;
        }
        return metricRegistry.timer(STATISTICAL_SUBSCRIPTION_ACTIVATION_DURATION_FOR_STATUS_TO_GO_TO_ACTIVE).getSnapshot().getMedian();
    }

    /**
     * Gets min statistical subscription activation duration for status to go to active.
     *
     * @return the min statistical subscription activation duration for status to go to active
     */
    @MonitoredAttribute(displayName = STATISTICAL_SUBSCRIPTION_ACTIVATION_DURATION_FOR_STATUS_TO_GO_TO_ACTIVE
            + ".min", visibility = MonitoredAttribute.Visibility.ALL, interval = MonitoredAttribute.Interval.ONE_MIN,
            units = MonitoredAttribute.Units.NANO_SECONDS)
    public long getMinStatisticalSubscriptionActivationDurationForStatusToGoToActive() {
        if (!isTimerMetricEnabled()) {
            return 0;
        }
        return metricRegistry.timer(STATISTICAL_SUBSCRIPTION_ACTIVATION_DURATION_FOR_STATUS_TO_GO_TO_ACTIVE).getSnapshot().getMin();
    }

    /**
     * Gets count of celltrace subscription activation duration for status to go to active.
     *
     * @return the count of celltrace subscription activation duration for status to go to active
     */
    @MonitoredAttribute(displayName = CELLTRACE_SUBSCRIPTION_ACTIVATION_DURATION_FOR_STATUS_TO_GO_TO_ACTIVE
            + ".count", visibility = MonitoredAttribute.Visibility.ALL, interval = MonitoredAttribute.Interval.ONE_MIN)
    public long getCountCelltraceSubscriptionActivationDurationForStatusToGoToActive() {
        if (!isTimerMetricEnabled()) {
            return 0;
        }
        return metricRegistry.timer(CELLTRACE_SUBSCRIPTION_ACTIVATION_DURATION_FOR_STATUS_TO_GO_TO_ACTIVE).getCount();
    }

    /**
     * Gets max celltrace subscription activation duration for status to go to active.
     *
     * @return the max celltrace subscription activation duration for status to go to active
     */
    @MonitoredAttribute(displayName = CELLTRACE_SUBSCRIPTION_ACTIVATION_DURATION_FOR_STATUS_TO_GO_TO_ACTIVE
            + ".max", visibility = MonitoredAttribute.Visibility.ALL, interval = MonitoredAttribute.Interval.ONE_MIN,
            units = MonitoredAttribute.Units.NANO_SECONDS)
    public long getMaxCelltraceSubscriptionActivationDurationForStatusToGoToActive() {
        if (!isTimerMetricEnabled()) {
            return 0;
        }
        return metricRegistry.timer(CELLTRACE_SUBSCRIPTION_ACTIVATION_DURATION_FOR_STATUS_TO_GO_TO_ACTIVE).getSnapshot().getMax();
    }

    /**
     * Gets median celltrace subscription activation duration for status to go to active.
     *
     * @return the median celltrace subscription activation duration for status to go to active
     */
    @MonitoredAttribute(displayName = CELLTRACE_SUBSCRIPTION_ACTIVATION_DURATION_FOR_STATUS_TO_GO_TO_ACTIVE
            + ".median", visibility = MonitoredAttribute.Visibility.ALL, interval = MonitoredAttribute.Interval.ONE_MIN,
            units = MonitoredAttribute.Units.NANO_SECONDS)
    public double getMedianCelltraceSubscriptionActivationDurationForStatusToGoToActive() {
        if (!isTimerMetricEnabled()) {
            return 0;
        }
        return metricRegistry.timer(CELLTRACE_SUBSCRIPTION_ACTIVATION_DURATION_FOR_STATUS_TO_GO_TO_ACTIVE).getSnapshot().getMedian();
    }

    /**
     * Gets min celltrace subscription activation duration for status to go to active.
     *
     * @return the min celltrace subscription activation duration for status to go to active
     */
    @MonitoredAttribute(displayName = CELLTRACE_SUBSCRIPTION_ACTIVATION_DURATION_FOR_STATUS_TO_GO_TO_ACTIVE
            + ".min", visibility = MonitoredAttribute.Visibility.ALL, interval = MonitoredAttribute.Interval.ONE_MIN,
            units = MonitoredAttribute.Units.NANO_SECONDS)
    public long getMinCelltraceSubscriptionActivationDurationForStatusToGoToActive() {
        if (!isTimerMetricEnabled()) {
            return 0;
        }
        return metricRegistry.timer(CELLTRACE_SUBSCRIPTION_ACTIVATION_DURATION_FOR_STATUS_TO_GO_TO_ACTIVE).getSnapshot().getMin();
    }

    /**
     * Gets count of cctr subscription activation duration for status to go to active.
     *
     * @return the count of cctr subscription activation duration for status to go to active
     */
    @MonitoredAttribute(displayName = CCTR_SUBSCRIPTION_ACTIVATION_DURATION_FOR_STATUS_TO_GO_TO_ACTIVE
            + ".count", visibility = MonitoredAttribute.Visibility.ALL, interval = MonitoredAttribute.Interval.ONE_MIN)
    public long getCountCCTRSubscriptionActivationDurationForStatusToGoToActive() {
        if (!isTimerMetricEnabled()) {
            return 0;
        }
        return metricRegistry.timer(CCTR_SUBSCRIPTION_ACTIVATION_DURATION_FOR_STATUS_TO_GO_TO_ACTIVE).getCount();
    }

    /**
     * Gets max cctr subscription activation duration for status to go to active.
     *
     * @return the max cctr subscription activation duration for status to go to active
     */
    @MonitoredAttribute(displayName = CCTR_SUBSCRIPTION_ACTIVATION_DURATION_FOR_STATUS_TO_GO_TO_ACTIVE
            + ".max", visibility = MonitoredAttribute.Visibility.ALL, interval = MonitoredAttribute.Interval.ONE_MIN,
            units = MonitoredAttribute.Units.NANO_SECONDS)
    public long getMaxCCTRSubscriptionActivationDurationForStatusToGoToActive() {
        if (!isTimerMetricEnabled()) {
            return 0;
        }
        return metricRegistry.timer(CCTR_SUBSCRIPTION_ACTIVATION_DURATION_FOR_STATUS_TO_GO_TO_ACTIVE).getSnapshot().getMax();
    }

    /**
     * Gets median cctr subscription activation duration for status to go to active.
     *
     * @return the median cctr subscription activation duration for status to go to active
     */
    @MonitoredAttribute(displayName = CCTR_SUBSCRIPTION_ACTIVATION_DURATION_FOR_STATUS_TO_GO_TO_ACTIVE
            + ".median", visibility = MonitoredAttribute.Visibility.ALL, interval = MonitoredAttribute.Interval.ONE_MIN,
            units = MonitoredAttribute.Units.NANO_SECONDS)
    public double getMedianCCTRSubscriptionActivationDurationForStatusToGoToActive() {
        if (!isTimerMetricEnabled()) {
            return 0;
        }
        return metricRegistry.timer(CCTR_SUBSCRIPTION_ACTIVATION_DURATION_FOR_STATUS_TO_GO_TO_ACTIVE).getSnapshot().getMedian();
    }

    /**
     * Gets min cctr subscription activation duration for status to go to active.
     *
     * @return the min cctr subscription activation duration for status to go to active
     */
    @MonitoredAttribute(displayName = CCTR_SUBSCRIPTION_ACTIVATION_DURATION_FOR_STATUS_TO_GO_TO_ACTIVE
            + ".min", visibility = MonitoredAttribute.Visibility.ALL, interval = MonitoredAttribute.Interval.ONE_MIN,
            units = MonitoredAttribute.Units.NANO_SECONDS)
    public long getMinCCTRSubscriptionActivationDurationForStatusToGoToActive() {
        if (!isTimerMetricEnabled()) {
            return 0;
        }
        return metricRegistry.timer(CCTR_SUBSCRIPTION_ACTIVATION_DURATION_FOR_STATUS_TO_GO_TO_ACTIVE).getSnapshot().getMin();
    }

    /**
     * Gets count of subscription deactivation duration for status to go to inactive.
     *
     * @return the count of subscription deactivation duration for status to go to inactive
     */
    @MonitoredAttribute(displayName = SUBSCRIPTION_DEACTIVATION_DURATION_FOR_STATUS_TO_GO_TO_INACTIVE
            + ".count", visibility = MonitoredAttribute.Visibility.ALL, interval = MonitoredAttribute.Interval.ONE_MIN)
    public long getCountSubscriptionDeactivationDurationForStatusToGoToInactive() {
        if (!isTimerMetricEnabled()) {
            return 0;
        }
        return metricRegistry.timer(SUBSCRIPTION_DEACTIVATION_DURATION_FOR_STATUS_TO_GO_TO_INACTIVE).getCount();
    }

    /**
     * Gets max subscription deactivation duration for status to go to inactive.
     *
     * @return the max subscription deactivation duration for status to go to inactive
     */
    @MonitoredAttribute(displayName = SUBSCRIPTION_DEACTIVATION_DURATION_FOR_STATUS_TO_GO_TO_INACTIVE
            + ".max", visibility = MonitoredAttribute.Visibility.ALL, interval = MonitoredAttribute.Interval.ONE_MIN,
            units = MonitoredAttribute.Units.NANO_SECONDS)
    public long getMaxSubscriptionDeactivationDurationForStatusToGoToInactive() {
        if (!isTimerMetricEnabled()) {
            return 0;
        }
        return metricRegistry.timer(SUBSCRIPTION_DEACTIVATION_DURATION_FOR_STATUS_TO_GO_TO_INACTIVE).getSnapshot().getMax();
    }

    /**
     * Gets median subscription deactivation duration for status to go to inactive.
     *
     * @return the median subscription deactivation duration for status to go to inactive
     */
    @MonitoredAttribute(displayName = SUBSCRIPTION_DEACTIVATION_DURATION_FOR_STATUS_TO_GO_TO_INACTIVE
            + ".median", visibility = MonitoredAttribute.Visibility.ALL, interval = MonitoredAttribute.Interval.ONE_MIN,
            units = MonitoredAttribute.Units.NANO_SECONDS)
    public double getMedianSubscriptionDeactivationDurationForStatusToGoToInactive() {
        if (!isTimerMetricEnabled()) {
            return 0;
        }
        return metricRegistry.timer(SUBSCRIPTION_DEACTIVATION_DURATION_FOR_STATUS_TO_GO_TO_INACTIVE).getSnapshot().getMedian();
    }

    /**
     * Gets min subscription deactivation duration for status to go to inactive.
     *
     * @return the min subscription deactivation duration for status to go to inactive
     */
    @MonitoredAttribute(displayName = SUBSCRIPTION_DEACTIVATION_DURATION_FOR_STATUS_TO_GO_TO_INACTIVE
            + ".min", visibility = MonitoredAttribute.Visibility.ALL, interval = MonitoredAttribute.Interval.ONE_MIN,
            units = MonitoredAttribute.Units.NANO_SECONDS)
    public long getMinSubscriptionDeactivationDurationForStatusToGoToInactive() {
        if (!isTimerMetricEnabled()) {
            return 0;
        }
        return metricRegistry.timer(SUBSCRIPTION_DEACTIVATION_DURATION_FOR_STATUS_TO_GO_TO_INACTIVE).getSnapshot().getMin();
    }

    /**
     * Gets count of statistical subscription deactivation duration for status to go to inactive.
     *
     * @return the count of statistical subscription deactivation duration for status to go to inactive
     */
    @MonitoredAttribute(displayName = STATISTICAL_SUBSCRIPTION_DEACTIVATION_DURATION_FOR_STATUS_TO_GO_TO_INACTIVE
            + ".count", visibility = MonitoredAttribute.Visibility.ALL, interval = MonitoredAttribute.Interval.ONE_MIN)
    public long getCountStatisticalSubscriptionDeactivationDurationForStatusToGoToInactive() {
        if (!isTimerMetricEnabled()) {
            return 0;
        }
        return metricRegistry.timer(STATISTICAL_SUBSCRIPTION_DEACTIVATION_DURATION_FOR_STATUS_TO_GO_TO_INACTIVE).getCount();
    }

    /**
     * Gets max statistical subscription deactivation duration for status to go to inactive.
     *
     * @return the max statistical subscription deactivation duration for status to go to inactive
     */
    @MonitoredAttribute(displayName = STATISTICAL_SUBSCRIPTION_DEACTIVATION_DURATION_FOR_STATUS_TO_GO_TO_INACTIVE
            + ".max", visibility = MonitoredAttribute.Visibility.ALL, interval = MonitoredAttribute.Interval.ONE_MIN,
            units = MonitoredAttribute.Units.NANO_SECONDS)
    public long getMaxStatisticalSubscriptionDeactivationDurationForStatusToGoToInactive() {
        if (!isTimerMetricEnabled()) {
            return 0;
        }
        return metricRegistry.timer(STATISTICAL_SUBSCRIPTION_DEACTIVATION_DURATION_FOR_STATUS_TO_GO_TO_INACTIVE).getSnapshot().getMax();
    }

    /**
     * Gets median statistical subscription deactivation duration for status to go to inactive.
     *
     * @return the median statistical subscription deactivation duration for status to go to inactive
     */
    @MonitoredAttribute(displayName = STATISTICAL_SUBSCRIPTION_DEACTIVATION_DURATION_FOR_STATUS_TO_GO_TO_INACTIVE
            + ".median", visibility = MonitoredAttribute.Visibility.ALL, interval = MonitoredAttribute.Interval.ONE_MIN,
            units = MonitoredAttribute.Units.NANO_SECONDS)
    public double getMedianStatisticalSubscriptionDeactivationDurationForStatusToGoToInactive() {
        if (!isTimerMetricEnabled()) {
            return 0;
        }
        return metricRegistry.timer(STATISTICAL_SUBSCRIPTION_DEACTIVATION_DURATION_FOR_STATUS_TO_GO_TO_INACTIVE).getSnapshot().getMedian();
    }

    /**
     * Gets min statistical subscription deactivation duration for status to go to inactive.
     *
     * @return the min statistical subscription deactivation duration for status to go to inactive
     */
    @MonitoredAttribute(displayName = STATISTICAL_SUBSCRIPTION_DEACTIVATION_DURATION_FOR_STATUS_TO_GO_TO_INACTIVE
            + ".min", visibility = MonitoredAttribute.Visibility.ALL, interval = MonitoredAttribute.Interval.ONE_MIN,
            units = MonitoredAttribute.Units.NANO_SECONDS)
    public long getMinStatisticalSubscriptionDeactivationDurationForStatusToGoToInactive() {
        if (!isTimerMetricEnabled()) {
            return 0;
        }
        return metricRegistry.timer(STATISTICAL_SUBSCRIPTION_DEACTIVATION_DURATION_FOR_STATUS_TO_GO_TO_INACTIVE).getSnapshot().getMin();
    }

    /**
     * Gets count of celltrace subscription deactivation duration for status to go to inactive.
     *
     * @return the count of celltrace subscription deactivation duration for status to go to inactive
     */
    @MonitoredAttribute(displayName = CELLTRACE_SUBSCRIPTION_DEACTIVATION_DURATION_FOR_STATUS_TO_GO_TO_INACTIVE
            + ".count", visibility = MonitoredAttribute.Visibility.ALL, interval = MonitoredAttribute.Interval.ONE_MIN)
    public long getCountCelltraceSubscriptionDeactivationDurationForStatusToGoToInactive() {
        if (!isTimerMetricEnabled()) {
            return 0;
        }
        return metricRegistry.timer(CELLTRACE_SUBSCRIPTION_DEACTIVATION_DURATION_FOR_STATUS_TO_GO_TO_INACTIVE).getCount();
    }

    /**
     * Gets max celltrace subscription deactivation duration for status to go to inactive.
     *
     * @return the max celltrace subscription deactivation duration for status to go to inactive
     */
    @MonitoredAttribute(displayName = CELLTRACE_SUBSCRIPTION_DEACTIVATION_DURATION_FOR_STATUS_TO_GO_TO_INACTIVE
            + ".max", visibility = MonitoredAttribute.Visibility.ALL, interval = MonitoredAttribute.Interval.ONE_MIN,
            units = MonitoredAttribute.Units.NANO_SECONDS)
    public long getMaxCelltraceSubscriptionDeactivationDurationForStatusToGoToInactive() {
        if (!isTimerMetricEnabled()) {
            return 0;
        }
        return metricRegistry.timer(CELLTRACE_SUBSCRIPTION_DEACTIVATION_DURATION_FOR_STATUS_TO_GO_TO_INACTIVE).getSnapshot().getMax();
    }

    /**
     * Gets median celltrace subscription deactivation duration for status to go to inactive.
     *
     * @return the median celltrace subscription deactivation duration for status to go to inactive
     */
    @MonitoredAttribute(displayName = CELLTRACE_SUBSCRIPTION_DEACTIVATION_DURATION_FOR_STATUS_TO_GO_TO_INACTIVE
            + ".median", visibility = MonitoredAttribute.Visibility.ALL, interval = MonitoredAttribute.Interval.ONE_MIN,
            units = MonitoredAttribute.Units.NANO_SECONDS)
    public double getMedianCelltraceSubscriptionDeactivationDurationForStatusToGoToInactive() {
        if (!isTimerMetricEnabled()) {
            return 0;
        }
        return metricRegistry.timer(CELLTRACE_SUBSCRIPTION_DEACTIVATION_DURATION_FOR_STATUS_TO_GO_TO_INACTIVE).getSnapshot().getMedian();
    }

    /**
     * Gets min celltrace subscription deactivation duration for status to go to inactive.
     *
     * @return the min celltrace subscription deactivation duration for status to go to inactive
     */
    @MonitoredAttribute(displayName = CELLTRACE_SUBSCRIPTION_DEACTIVATION_DURATION_FOR_STATUS_TO_GO_TO_INACTIVE
            + ".min", visibility = MonitoredAttribute.Visibility.ALL, interval = MonitoredAttribute.Interval.ONE_MIN,
            units = MonitoredAttribute.Units.NANO_SECONDS)
    public long getMinCelltraceSubscriptionDeactivationDurationForStatusToGoToInactive() {
        if (!isTimerMetricEnabled()) {
            return 0;
        }
        return metricRegistry.timer(CELLTRACE_SUBSCRIPTION_DEACTIVATION_DURATION_FOR_STATUS_TO_GO_TO_INACTIVE).getSnapshot().getMin();
    }

    /**
     * Gets the count of cctr subscription deactivation duration for status to go to inactive.
     *
     * @return the count of cctr subscription deactivation duration for status to go to inactive
     */
    @MonitoredAttribute(displayName = CCTR_SUBSCRIPTION_DEACTIVATION_DURATION_FOR_STATUS_TO_GO_TO_INACTIVE
            + ".count", visibility = MonitoredAttribute.Visibility.ALL, interval = MonitoredAttribute.Interval.ONE_MIN)
    public long getCCTRSubscriptionDeactivationDurationForStatusToGoToInactive() {
        if (!isTimerMetricEnabled()) {
            return 0;
        }
        return metricRegistry.timer(CCTR_SUBSCRIPTION_DEACTIVATION_DURATION_FOR_STATUS_TO_GO_TO_INACTIVE).getCount();
    }

    /**
     * Gets max cctr subscription deactivation duration for status to go to inactive.
     *
     * @return the max cctr subscription deactivation duration for status to go to inactive
     */
    @MonitoredAttribute(displayName = CCTR_SUBSCRIPTION_DEACTIVATION_DURATION_FOR_STATUS_TO_GO_TO_INACTIVE
            + ".max", visibility = MonitoredAttribute.Visibility.ALL, interval = MonitoredAttribute.Interval.ONE_MIN,
            units = MonitoredAttribute.Units.NANO_SECONDS)
    public long getMaxCCTRSubscriptionDeactivationDurationForStatusToGoToInactive() {
        if (!isTimerMetricEnabled()) {
            return 0;
        }
        return metricRegistry.timer(CCTR_SUBSCRIPTION_DEACTIVATION_DURATION_FOR_STATUS_TO_GO_TO_INACTIVE).getSnapshot().getMax();
    }

    /**
     * Gets median cctr subscription deactivation duration for status to go to inactive.
     *
     * @return the median cctr subscription deactivation duration for status to go to inactive
     */
    @MonitoredAttribute(displayName = CCTR_SUBSCRIPTION_DEACTIVATION_DURATION_FOR_STATUS_TO_GO_TO_INACTIVE
            + ".median", visibility = MonitoredAttribute.Visibility.ALL, interval = MonitoredAttribute.Interval.ONE_MIN,
            units = MonitoredAttribute.Units.NANO_SECONDS)
    public double getMedianCCTRSubscriptionDeactivationDurationForStatusToGoToInactive() {
        if (!isTimerMetricEnabled()) {
            return 0;
        }
        return metricRegistry.timer(CCTR_SUBSCRIPTION_DEACTIVATION_DURATION_FOR_STATUS_TO_GO_TO_INACTIVE).getSnapshot().getMedian();
    }

    /**
     * Gets min cctr subscription deactivation duration for status to go to inactive.
     *
     * @return the min cctr subscription deactivation duration for status to go to inactive
     */
    @MonitoredAttribute(displayName = CCTR_SUBSCRIPTION_DEACTIVATION_DURATION_FOR_STATUS_TO_GO_TO_INACTIVE
            + ".min", visibility = MonitoredAttribute.Visibility.ALL, interval = MonitoredAttribute.Interval.ONE_MIN,
            units = MonitoredAttribute.Units.NANO_SECONDS)
    public long getMinCCTRSubscriptionDeactivationDurationForStatusToGoToInactive() {
        if (!isTimerMetricEnabled()) {
            return 0;
        }
        return metricRegistry.timer(CCTR_SUBSCRIPTION_DEACTIVATION_DURATION_FOR_STATUS_TO_GO_TO_INACTIVE).getSnapshot().getMin();
    }

    /**
     * Gets count of ebm subscription activation duration for status to go to active.
     *
     * @return the count of ebm subscription activation duration for status to go to active
     */
    @MonitoredAttribute(displayName = EBM_SUBSCRIPTION_ACTIVATION_DURATION_FOR_STATUS_TO_GO_TO_ACTIVE
            + ".count", visibility = MonitoredAttribute.Visibility.ALL, interval = MonitoredAttribute.Interval.ONE_MIN)
    public long getCountEbmSubscriptionActivationDurationForStatusToGoToActive() {
        if (!isTimerMetricEnabled()) {
            return 0;
        }
        return metricRegistry.timer(EBM_SUBSCRIPTION_ACTIVATION_DURATION_FOR_STATUS_TO_GO_TO_ACTIVE).getCount();
    }

    /**
     * Gets max ebm subscription activation duration for status to go to active.
     *
     * @return the max ebm subscription activation duration for status to go to active
     */
    @MonitoredAttribute(displayName = EBM_SUBSCRIPTION_ACTIVATION_DURATION_FOR_STATUS_TO_GO_TO_ACTIVE
            + ".max", visibility = MonitoredAttribute.Visibility.ALL, interval = MonitoredAttribute.Interval.ONE_MIN,
            units = MonitoredAttribute.Units.NANO_SECONDS)
    public long getMaxEbmSubscriptionActivationDurationForStatusToGoToActive() {
        if (!isTimerMetricEnabled()) {
            return 0;
        }
        return metricRegistry.timer(EBM_SUBSCRIPTION_ACTIVATION_DURATION_FOR_STATUS_TO_GO_TO_ACTIVE).getSnapshot().getMax();
    }

    /**
     * Gets median ebm subscription activation duration for status to go to active.
     *
     * @return the median ebm subscription activation duration for status to go to active
     */
    @MonitoredAttribute(displayName = EBM_SUBSCRIPTION_ACTIVATION_DURATION_FOR_STATUS_TO_GO_TO_ACTIVE
            + ".median", visibility = MonitoredAttribute.Visibility.ALL, interval = MonitoredAttribute.Interval.ONE_MIN,
            units = MonitoredAttribute.Units.NANO_SECONDS)
    public double getMedianEbmSubscriptionActivationDurationForStatusToGoToActive() {
        if (!isTimerMetricEnabled()) {
            return 0;
        }
        return metricRegistry.timer(EBM_SUBSCRIPTION_ACTIVATION_DURATION_FOR_STATUS_TO_GO_TO_ACTIVE).getSnapshot().getMedian();
    }

    /**
     * Gets min ebm subscription activation duration for status to go to active.
     *
     * @return the min ebm subscription activation duration for status to go to active
     */
    @MonitoredAttribute(displayName = EBM_SUBSCRIPTION_ACTIVATION_DURATION_FOR_STATUS_TO_GO_TO_ACTIVE
            + ".min", visibility = MonitoredAttribute.Visibility.ALL, interval = MonitoredAttribute.Interval.ONE_MIN,
            units = MonitoredAttribute.Units.NANO_SECONDS)
    public long getMinEbmSubscriptionActivationDurationForStatusToGoToActive() {
        if (!isTimerMetricEnabled()) {
            return 0;
        }
        return metricRegistry.timer(EBM_SUBSCRIPTION_ACTIVATION_DURATION_FOR_STATUS_TO_GO_TO_ACTIVE).getSnapshot().getMin();
    }

    /**
     * Gets count of ebm subscription deactivation duration for status to go to inactive.
     *
     * @return the count of ebm subscription deactivation duration for status to go to inactive
     */
    @MonitoredAttribute(displayName = EBM_SUBSCRIPTION_DEACTIVATION_DURATION_FOR_STATUS_TO_GO_TO_INACTIVE
            + ".count", visibility = MonitoredAttribute.Visibility.ALL, interval = MonitoredAttribute.Interval.ONE_MIN)
    public long getCountEbmSubscriptionDeactivationDurationForStatusToGoToInactive() {
        if (!isTimerMetricEnabled()) {
            return 0;
        }
        return metricRegistry.timer(EBM_SUBSCRIPTION_DEACTIVATION_DURATION_FOR_STATUS_TO_GO_TO_INACTIVE).getCount();
    }

    /**
     * Gets max ebm subscription deactivation duration for status to go to inactive.
     *
     * @return the max ebm subscription deactivation duration for status to go to inactive
     */
    @MonitoredAttribute(displayName = EBM_SUBSCRIPTION_DEACTIVATION_DURATION_FOR_STATUS_TO_GO_TO_INACTIVE
            + ".max", visibility = MonitoredAttribute.Visibility.ALL, interval = MonitoredAttribute.Interval.ONE_MIN,
            units = MonitoredAttribute.Units.NANO_SECONDS)
    public long getMaxEbmSubscriptionDeactivationDurationForStatusToGoToInactive() {
        if (!isTimerMetricEnabled()) {
            return 0;
        }
        return metricRegistry.timer(EBM_SUBSCRIPTION_DEACTIVATION_DURATION_FOR_STATUS_TO_GO_TO_INACTIVE).getSnapshot().getMax();
    }

    /**
     * Gets median ebm subscription deactivation duration for status to go to inactive.
     *
     * @return the median ebm subscription deactivation duration for status to go to inactive
     */
    @MonitoredAttribute(displayName = EBM_SUBSCRIPTION_DEACTIVATION_DURATION_FOR_STATUS_TO_GO_TO_INACTIVE
            + ".median", visibility = MonitoredAttribute.Visibility.ALL, interval = MonitoredAttribute.Interval.ONE_MIN,
            units = MonitoredAttribute.Units.NANO_SECONDS)
    public double getMedianEbmSubscriptionDeactivationDurationForStatusToGoToInactive() {
        if (!isTimerMetricEnabled()) {
            return 0;
        }
        return metricRegistry.timer(EBM_SUBSCRIPTION_DEACTIVATION_DURATION_FOR_STATUS_TO_GO_TO_INACTIVE).getSnapshot().getMedian();
    }

    /**
     * Gets min ebm subscription deactivation duration for status to go to inactive.
     *
     * @return the min ebm subscription deactivation duration for status to go to inactive
     */
    @MonitoredAttribute(displayName = EBM_SUBSCRIPTION_DEACTIVATION_DURATION_FOR_STATUS_TO_GO_TO_INACTIVE
            + ".min", visibility = MonitoredAttribute.Visibility.ALL, interval = MonitoredAttribute.Interval.ONE_MIN,
            units = MonitoredAttribute.Units.NANO_SECONDS)
    public long getMinEbmSubscriptionDeactivationDurationForStatusToGoToInactive() {
        if (!isTimerMetricEnabled()) {
            return 0;
        }
        return metricRegistry.timer(EBM_SUBSCRIPTION_DEACTIVATION_DURATION_FOR_STATUS_TO_GO_TO_INACTIVE).getSnapshot().getMin();
    }

}
