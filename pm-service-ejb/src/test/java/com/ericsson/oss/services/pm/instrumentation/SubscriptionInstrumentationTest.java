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
package com.ericsson.oss.services.pm.instrumentation;

import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;
import static org.testng.Assert.assertEquals;

import static com.ericsson.oss.services.pm.initiation.util.constants.SubscriptionDPSConstant.PMIC_ATT_SUBSCRIPTION_ADMINSTATE;
import static com.ericsson.oss.services.pm.instrumentation.SubscriptionInstrumentation.CELLTRACE_SUBSCRIPTION_ACTIVATION_DURATION_FOR_STATUS_TO_GO_TO_ACTIVE;
import static com.ericsson.oss.services.pm.instrumentation.SubscriptionInstrumentation.CELLTRACE_SUBSCRIPTION_DEACTIVATION_DURATION_FOR_STATUS_TO_GO_TO_INACTIVE;
import static com.ericsson.oss.services.pm.instrumentation.SubscriptionInstrumentation.EBM_SUBSCRIPTION_ACTIVATION_DURATION_FOR_STATUS_TO_GO_TO_ACTIVE;
import static com.ericsson.oss.services.pm.instrumentation.SubscriptionInstrumentation.EBM_SUBSCRIPTION_DEACTIVATION_DURATION_FOR_STATUS_TO_GO_TO_INACTIVE;
import static com.ericsson.oss.services.pm.instrumentation.SubscriptionInstrumentation.STATISTICAL_SUBSCRIPTION_ACTIVATION_DURATION_FOR_STATUS_TO_GO_TO_ACTIVE;
import static com.ericsson.oss.services.pm.instrumentation.SubscriptionInstrumentation.STATISTICAL_SUBSCRIPTION_DEACTIVATION_DURATION_FOR_STATUS_TO_GO_TO_INACTIVE;
import static com.ericsson.oss.services.pm.instrumentation.SubscriptionInstrumentation.SUBSCRIPTION_ACTIVATION_DURATION_FOR_STATUS_TO_GO_TO_ACTIVE;
import static com.ericsson.oss.services.pm.instrumentation.SubscriptionInstrumentation.SUBSCRIPTION_DEACTIVATION_DURATION_FOR_STATUS_TO_GO_TO_INACTIVE;

import java.util.HashSet;
import java.util.Set;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.internal.util.reflection.Whitebox;
import org.slf4j.Logger;

import com.codahale.metrics.MetricRegistry;
import com.ericsson.oss.itpf.datalayer.dps.notification.event.AttributeChangeData;
import com.ericsson.oss.itpf.datalayer.dps.notification.event.DpsAttributeChangedEvent;
import com.ericsson.oss.pmic.dto.subscription.CellTraceSubscription;
import com.ericsson.oss.pmic.dto.subscription.EbmSubscription;
import com.ericsson.oss.pmic.dto.subscription.StatisticalSubscription;
import com.ericsson.oss.pmic.dto.subscription.Subscription;
import com.ericsson.oss.pmic.dto.subscription.enums.AdministrationState;
import com.ericsson.oss.pmic.dto.subscription.enums.SubscriptionType;
import com.ericsson.oss.services.pm.exception.DataAccessException;
import com.ericsson.oss.services.pm.exception.RetryServiceException;
import com.ericsson.oss.services.pm.scheduling.cluster.MembershipListener;
import com.ericsson.oss.services.pm.services.generic.SubscriptionReadOperationService;

public class SubscriptionInstrumentationTest {

    SubscriptionInstrumentation subscriptionInstrumentation;

    @Mock
    Logger log;
    @Mock
    MembershipListener membershipListener;
    @Mock
    SubscriptionReadOperationService subscriptionReadOperationService;

    MetricRegistry metricRegistry;

    @Before
    public void setUp() {
        subscriptionInstrumentation = new SubscriptionInstrumentation();
        metricRegistry = new MetricRegistry();
        initMocks(this);
        Whitebox.setInternalState(subscriptionInstrumentation, "log", log);
        Whitebox.setInternalState(subscriptionInstrumentation, "membershipListener", membershipListener);
        Whitebox.setInternalState(subscriptionInstrumentation, "subscriptionReadOperationService", subscriptionReadOperationService);
        Whitebox.setInternalState(subscriptionInstrumentation, "metricRegistry", metricRegistry);
        when(membershipListener.isMaster()).thenReturn(true);
    }

    @Test
    public void statisticalSubscriptionActivationTestCreateTwoTimers() {
        final Subscription sub = new StatisticalSubscription();
        sub.setId(123L);
        sub.setType(SubscriptionType.STATISTICAL);
        subscriptionInstrumentation.subscriptionActivationStarted(sub);
        subscriptionInstrumentation.subscriptionActivationEnded(sub);
        assertEquals(metricRegistry.timer(SUBSCRIPTION_ACTIVATION_DURATION_FOR_STATUS_TO_GO_TO_ACTIVE).getCount(), 1);
        assertEquals(metricRegistry.timer(STATISTICAL_SUBSCRIPTION_ACTIVATION_DURATION_FOR_STATUS_TO_GO_TO_ACTIVE).getCount(), 1);
        assertEquals(metricRegistry.timer(CELLTRACE_SUBSCRIPTION_ACTIVATION_DURATION_FOR_STATUS_TO_GO_TO_ACTIVE).getCount(), 0);
        assertEquals(metricRegistry.timer(EBM_SUBSCRIPTION_ACTIVATION_DURATION_FOR_STATUS_TO_GO_TO_ACTIVE).getCount(), 0);
    }

    @Test
    public void celltraceSubscriptionActivationTestCreateTwoTimers() {
        final Subscription sub = new CellTraceSubscription();
        sub.setId(123L);
        sub.setType(SubscriptionType.CELLTRACE);
        subscriptionInstrumentation.subscriptionActivationStarted(sub);
        subscriptionInstrumentation.subscriptionActivationEnded(sub);
        assertEquals(metricRegistry.timer(SUBSCRIPTION_ACTIVATION_DURATION_FOR_STATUS_TO_GO_TO_ACTIVE).getCount(), 1);
        assertEquals(metricRegistry.timer(CELLTRACE_SUBSCRIPTION_ACTIVATION_DURATION_FOR_STATUS_TO_GO_TO_ACTIVE).getCount(), 1);
        assertEquals(metricRegistry.timer(STATISTICAL_SUBSCRIPTION_ACTIVATION_DURATION_FOR_STATUS_TO_GO_TO_ACTIVE).getCount(), 0);
        assertEquals(metricRegistry.timer(EBM_SUBSCRIPTION_ACTIVATION_DURATION_FOR_STATUS_TO_GO_TO_ACTIVE).getCount(), 0);
    }

    @Test
    public void ebmSubscriptionActivationTestCreateTwoTimers() {
        final Subscription sub = new EbmSubscription();
        sub.setId(123L);
        sub.setType(SubscriptionType.EBM);
        subscriptionInstrumentation.subscriptionActivationStarted(sub);
        subscriptionInstrumentation.subscriptionActivationEnded(sub);
        assertEquals(metricRegistry.timer(SUBSCRIPTION_ACTIVATION_DURATION_FOR_STATUS_TO_GO_TO_ACTIVE).getCount(), 1);
        assertEquals(metricRegistry.timer(CELLTRACE_SUBSCRIPTION_ACTIVATION_DURATION_FOR_STATUS_TO_GO_TO_ACTIVE).getCount(), 0);
        assertEquals(metricRegistry.timer(STATISTICAL_SUBSCRIPTION_ACTIVATION_DURATION_FOR_STATUS_TO_GO_TO_ACTIVE).getCount(), 0);
        assertEquals(metricRegistry.timer(EBM_SUBSCRIPTION_ACTIVATION_DURATION_FOR_STATUS_TO_GO_TO_ACTIVE).getCount(), 1);
    }

    @Test
    public void statisticalSubscriptionDeactivationTestCreateTwoTimers() {
        final Subscription sub = new StatisticalSubscription();
        sub.setId(123L);
        sub.setType(SubscriptionType.STATISTICAL);
        subscriptionInstrumentation.subscriptionDeactivationStarted(sub);
        subscriptionInstrumentation.subscriptionDeactivationEnded(sub);
        assertEquals(metricRegistry.timer(SUBSCRIPTION_DEACTIVATION_DURATION_FOR_STATUS_TO_GO_TO_INACTIVE).getCount(), 1);
        assertEquals(metricRegistry.timer(STATISTICAL_SUBSCRIPTION_DEACTIVATION_DURATION_FOR_STATUS_TO_GO_TO_INACTIVE).getCount(), 1);
        assertEquals(metricRegistry.timer(CELLTRACE_SUBSCRIPTION_DEACTIVATION_DURATION_FOR_STATUS_TO_GO_TO_INACTIVE).getCount(), 0);
        assertEquals(metricRegistry.timer(EBM_SUBSCRIPTION_DEACTIVATION_DURATION_FOR_STATUS_TO_GO_TO_INACTIVE).getCount(), 0);
    }

    @Test
    public void celltraceSubscriptionDeactivationTestCreateTwoTimers() {
        final Subscription sub = new CellTraceSubscription();
        sub.setId(123L);
        sub.setType(SubscriptionType.CELLTRACE);
        subscriptionInstrumentation.subscriptionDeactivationStarted(sub);
        subscriptionInstrumentation.subscriptionDeactivationEnded(sub);
        assertEquals(metricRegistry.timer(SUBSCRIPTION_DEACTIVATION_DURATION_FOR_STATUS_TO_GO_TO_INACTIVE).getCount(), 1);
        assertEquals(metricRegistry.timer(STATISTICAL_SUBSCRIPTION_DEACTIVATION_DURATION_FOR_STATUS_TO_GO_TO_INACTIVE).getCount(), 0);
        assertEquals(metricRegistry.timer(CELLTRACE_SUBSCRIPTION_DEACTIVATION_DURATION_FOR_STATUS_TO_GO_TO_INACTIVE).getCount(), 1);
        assertEquals(metricRegistry.timer(EBM_SUBSCRIPTION_DEACTIVATION_DURATION_FOR_STATUS_TO_GO_TO_INACTIVE).getCount(), 0);
    }

    @Test
    public void ebmSubscriptionDeactivationTestCreateTwoTimers() {
        final Subscription sub = new EbmSubscription();
        sub.setId(123L);
        sub.setType(SubscriptionType.EBM);
        subscriptionInstrumentation.subscriptionDeactivationStarted(sub);
        subscriptionInstrumentation.subscriptionDeactivationEnded(sub);
        assertEquals(metricRegistry.timer(SUBSCRIPTION_DEACTIVATION_DURATION_FOR_STATUS_TO_GO_TO_INACTIVE).getCount(), 1);
        assertEquals(metricRegistry.timer(STATISTICAL_SUBSCRIPTION_DEACTIVATION_DURATION_FOR_STATUS_TO_GO_TO_INACTIVE).getCount(), 0);
        assertEquals(metricRegistry.timer(CELLTRACE_SUBSCRIPTION_DEACTIVATION_DURATION_FOR_STATUS_TO_GO_TO_INACTIVE).getCount(), 0);
        assertEquals(metricRegistry.timer(EBM_SUBSCRIPTION_DEACTIVATION_DURATION_FOR_STATUS_TO_GO_TO_INACTIVE).getCount(), 1);
    }

    @Test
    public void onSubscriptionChangeEventTestSubscriptionChangeFromInactiveToActive()
            throws DataAccessException, RetryServiceException {
        final Set<AttributeChangeData> attributeChangeData = new HashSet<>();
        attributeChangeData.add(getAttributeChangeData(PMIC_ATT_SUBSCRIPTION_ADMINSTATE, AdministrationState.INACTIVE.name(),
                AdministrationState.ACTIVATING.name()));

        final Subscription subscription = new StatisticalSubscription();
        subscription.setType(SubscriptionType.STATISTICAL);
        subscription.setId(123L);

        when(subscriptionReadOperationService.findByIdWithRetry(anyLong(), anyBoolean())).thenReturn(subscription);
        when(log.isTraceEnabled()).thenReturn(true);

        subscriptionInstrumentation.onSubscriptionChangeEvent(getDpsAttributeChangedEvent(attributeChangeData));

        attributeChangeData.clear();
        attributeChangeData.add(
                getAttributeChangeData(PMIC_ATT_SUBSCRIPTION_ADMINSTATE, AdministrationState.ACTIVATING.name(),
                        AdministrationState.ACTIVE.name()));
        subscriptionInstrumentation.onSubscriptionChangeEvent(getDpsAttributeChangedEvent(attributeChangeData));

        assertEquals(metricRegistry.timer(SUBSCRIPTION_ACTIVATION_DURATION_FOR_STATUS_TO_GO_TO_ACTIVE).getCount(), 1);
        assertEquals(metricRegistry.timer(STATISTICAL_SUBSCRIPTION_ACTIVATION_DURATION_FOR_STATUS_TO_GO_TO_ACTIVE).getCount(), 1);
    }

    @Test
    public void onSubscriptionChangeEventTestEBMSubscriptionChangeFromInactiveToActive()
            throws DataAccessException, RetryServiceException {
        final Set<AttributeChangeData> attributeChangeData = new HashSet<>();
        attributeChangeData.add(getAttributeChangeData(PMIC_ATT_SUBSCRIPTION_ADMINSTATE, AdministrationState.INACTIVE.name(),
                AdministrationState.ACTIVATING.name()));

        final Subscription subscription = new EbmSubscription();
        subscription.setType(SubscriptionType.EBM);
        subscription.setId(123L);

        when(subscriptionReadOperationService.findByIdWithRetry(anyLong(), anyBoolean())).thenReturn(subscription);
        when(log.isTraceEnabled()).thenReturn(true);

        subscriptionInstrumentation.onSubscriptionChangeEvent(getDpsAttributeChangedEvent(attributeChangeData));

        attributeChangeData.clear();
        attributeChangeData.add(
                getAttributeChangeData(PMIC_ATT_SUBSCRIPTION_ADMINSTATE, AdministrationState.ACTIVATING.name(),
                        AdministrationState.ACTIVE.name()));
        subscriptionInstrumentation.onSubscriptionChangeEvent(getDpsAttributeChangedEvent(attributeChangeData));

        assertEquals(metricRegistry.timer(SUBSCRIPTION_ACTIVATION_DURATION_FOR_STATUS_TO_GO_TO_ACTIVE).getCount(), 1);
        assertEquals(metricRegistry.timer(STATISTICAL_SUBSCRIPTION_ACTIVATION_DURATION_FOR_STATUS_TO_GO_TO_ACTIVE).getCount(), 0);
        assertEquals(metricRegistry.timer(CELLTRACE_SUBSCRIPTION_ACTIVATION_DURATION_FOR_STATUS_TO_GO_TO_ACTIVE).getCount(), 0);
        assertEquals(metricRegistry.timer(EBM_SUBSCRIPTION_ACTIVATION_DURATION_FOR_STATUS_TO_GO_TO_ACTIVE).getCount(), 1);

        assertEquals(subscriptionInstrumentation.getCountEbmSubscriptionActivationDurationForStatusToGoToActive(), 1);
    }

    @Test
    public void onSubscriptionChangeEventTestSubscriptionChangeFromActiveToInactive()
            throws DataAccessException, RetryServiceException {
        final Set<AttributeChangeData> attributeChangeData = new HashSet<>();
        attributeChangeData.add(getAttributeChangeData(PMIC_ATT_SUBSCRIPTION_ADMINSTATE, AdministrationState.ACTIVE.name(),
                AdministrationState.DEACTIVATING.name()));

        final Subscription subscription = new StatisticalSubscription();
        subscription.setType(SubscriptionType.STATISTICAL);
        subscription.setId(123L);

        when(subscriptionReadOperationService.findByIdWithRetry(anyLong(), anyBoolean())).thenReturn(subscription);
        when(log.isTraceEnabled()).thenReturn(true);

        subscriptionInstrumentation.onSubscriptionChangeEvent(getDpsAttributeChangedEvent(attributeChangeData));

        attributeChangeData.clear();
        attributeChangeData.add(getAttributeChangeData(PMIC_ATT_SUBSCRIPTION_ADMINSTATE, AdministrationState.DEACTIVATING.name(),
                AdministrationState.INACTIVE.name()));
        subscriptionInstrumentation.onSubscriptionChangeEvent(getDpsAttributeChangedEvent(attributeChangeData));

        assertEquals(metricRegistry.timer(SUBSCRIPTION_DEACTIVATION_DURATION_FOR_STATUS_TO_GO_TO_INACTIVE).getCount(), 1);
        assertEquals(metricRegistry.timer(STATISTICAL_SUBSCRIPTION_DEACTIVATION_DURATION_FOR_STATUS_TO_GO_TO_INACTIVE).getCount(), 1);
    }

    @Test
    public void onSubscriptionChangeEventTestEbmSubscriptionChangeFromActiveToInactive()
            throws DataAccessException, RetryServiceException {
        final Set<AttributeChangeData> attributeChangeData = new HashSet<>();
        attributeChangeData.add(getAttributeChangeData(PMIC_ATT_SUBSCRIPTION_ADMINSTATE, AdministrationState.ACTIVE.name(),
                AdministrationState.DEACTIVATING.name()));

        final Subscription subscription = new EbmSubscription();
        subscription.setType(SubscriptionType.EBM);
        subscription.setId(123L);

        when(subscriptionReadOperationService.findByIdWithRetry(anyLong(), anyBoolean())).thenReturn(subscription);

        subscriptionInstrumentation.onSubscriptionChangeEvent(getDpsAttributeChangedEvent(attributeChangeData));

        attributeChangeData.clear();
        attributeChangeData.add(getAttributeChangeData(PMIC_ATT_SUBSCRIPTION_ADMINSTATE, AdministrationState.DEACTIVATING.name(),
                AdministrationState.INACTIVE.name()));
        subscriptionInstrumentation.onSubscriptionChangeEvent(getDpsAttributeChangedEvent(attributeChangeData));

        assertEquals(metricRegistry.timer(SUBSCRIPTION_DEACTIVATION_DURATION_FOR_STATUS_TO_GO_TO_INACTIVE).getCount(), 1);
        assertEquals(metricRegistry.timer(STATISTICAL_SUBSCRIPTION_DEACTIVATION_DURATION_FOR_STATUS_TO_GO_TO_INACTIVE).getCount(), 0);
        assertEquals(metricRegistry.timer(CELLTRACE_SUBSCRIPTION_DEACTIVATION_DURATION_FOR_STATUS_TO_GO_TO_INACTIVE).getCount(), 0);
        assertEquals(metricRegistry.timer(EBM_SUBSCRIPTION_DEACTIVATION_DURATION_FOR_STATUS_TO_GO_TO_INACTIVE).getCount(), 1);

        assertEquals(subscriptionInstrumentation.getCountEbmSubscriptionDeactivationDurationForStatusToGoToInactive(), 1);
    }

    private AttributeChangeData getAttributeChangeData(final String name, final String oldValue, final String newValue) {
        final AttributeChangeData attributeChangeData = new AttributeChangeData();
        attributeChangeData.setName(name);
        attributeChangeData.setOldValue(oldValue);
        attributeChangeData.setNewValue(newValue);
        return attributeChangeData;
    }

    private DpsAttributeChangedEvent getDpsAttributeChangedEvent(final Set<AttributeChangeData> attributeChangeData) {
        final DpsAttributeChangedEvent changeEvent = new DpsAttributeChangedEvent();
        changeEvent.setChangedAttributes(attributeChangeData);
        changeEvent.setNamespace("pmic_subscription");
        changeEvent.setType("PMICScannerInfo");
        changeEvent.setFdn("NetworkElement=LTEERBS002");
        changeEvent.setPoId(99l);
        return changeEvent;
    }

}
