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
package com.ericsson.oss.services.pm.initiation.task.factories.activation;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.List;
import javax.enterprise.inject.Instance;
import javax.enterprise.util.AnnotationLiteral;

import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.internal.util.reflection.Whitebox;
import org.slf4j.Logger;

import com.ericsson.oss.pmic.dto.node.Node;
import com.ericsson.oss.pmic.dto.subscription.StatisticalSubscription;
import com.ericsson.oss.pmic.dto.subscription.Subscription;
import com.ericsson.oss.services.pm.initiation.task.factories.MediationTaskRequestFactory;

public class SubscriptionActivationTaskRequestFactoryTest {
    @Mock
    private Logger logger;
    @Mock
    private Instance<MediationTaskRequestFactory<Subscription>> mediationTaskRequestFactories;
    @Mock
    private MediationTaskRequestFactory<Subscription> mediationTaskRequestFactory;
    @InjectMocks
    private SubscriptionActivationTaskRequestFactory underTest;

    @Before
    public void setMocks() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void createMediationTaskRequestsForStatisticalSubscription() {
        when(mediationTaskRequestFactories.select(any(AnnotationLiteral.class))).thenReturn(mediationTaskRequestFactories);
        when(mediationTaskRequestFactories.isUnsatisfied()).thenReturn(false);
        when(mediationTaskRequestFactories.get()).thenReturn(mediationTaskRequestFactory);
        Whitebox.setInternalState(underTest, "logger", logger);

        final Node pmicNodeInfo = new Node();
        pmicNodeInfo.setId(1L);
        final List<Node> nodes = Collections.singletonList(pmicNodeInfo);
        final StatisticalSubscription subscription = new StatisticalSubscription();
        subscription.setId(1L);
        underTest.createMediationTaskRequests(nodes, subscription, true);

        verify(mediationTaskRequestFactory).createMediationTaskRequests(nodes, subscription, true);
    }

    @Test(expected = UnsupportedOperationException.class)
    public void createMediationTaskRequestsFailure() {
        when(mediationTaskRequestFactories.select(any(AnnotationLiteral.class))).thenReturn(mediationTaskRequestFactories);
        when(mediationTaskRequestFactories.isUnsatisfied()).thenReturn(true);

        final Node pmicNodeInfo = new Node();
        pmicNodeInfo.setId(1L);
        final List<Node> nodes = Collections.singletonList(pmicNodeInfo);
        final StatisticalSubscription subscription = new StatisticalSubscription();
        subscription.setId(1L);
        underTest.createMediationTaskRequests(nodes, subscription, true);
    }

}
