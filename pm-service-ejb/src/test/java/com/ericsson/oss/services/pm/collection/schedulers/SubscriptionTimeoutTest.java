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

package com.ericsson.oss.services.pm.collection.schedulers;

import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;

import static com.ericsson.oss.pmic.api.constants.ModelConstants.PmCapabilityConstants.CAPABILITY_MODEL_NAME_STATS;

import java.util.HashMap;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.internal.util.reflection.Whitebox;
import org.slf4j.Logger;

import com.ericsson.oss.pmic.api.modelservice.PmCapabilitiesLookupLocal;
import com.ericsson.oss.services.pm.initiation.config.listener.ConfigurationChangeListener;
import com.ericsson.oss.services.pm.initiation.schedulers.SubscriptionTimeout;

public class SubscriptionTimeoutTest {

    private static final int WORST_CASE_INITIATION_TIMEOUT = 5_000;
    private static final int WORST_CASE_REPLY_WORST_DELAY = 180_000;
    private static final String NE_INITIATION_TIMEOUT = "neInitiationTimeout";
    private static final String NE_WORST_REPLY_DELAY = "neReplyWorstDelay";
    private static final String RADIO_NODE = "RadioNode";
    private static final String SGSN_MME = "SGSN-MME";
    private static final int WORST_CASE_NUMBER_OF_RADIO_NODES = 100;
    private static final int WORST_CASE_NUMBER_OF_SGSN_MME = 10;
    @InjectMocks
    private final SubscriptionTimeout subscriptionTimeout = spy(new SubscriptionTimeout());
    private final Map<String, String> neFdnsAndTypes = new HashMap<>();
    @Mock
    private ConfigurationChangeListener configListener;
    @Mock
    private PmCapabilitiesLookupLocal pmCapabilitiesLookup;
    @Mock
    private Logger logger;

    @Before
    public void before() {
        Whitebox.setInternalState(subscriptionTimeout, "logger", logger);
        Whitebox.setInternalState(subscriptionTimeout, "configListener", configListener);
        MockitoAnnotations.initMocks(this);
        for (int i = 0; i < WORST_CASE_NUMBER_OF_RADIO_NODES; i++) {
            neFdnsAndTypes.put("NetworkElement=1,ManagedElement=" + i, RADIO_NODE);
        }
        for (int i = 0; i < WORST_CASE_NUMBER_OF_SGSN_MME; i++) {
            neFdnsAndTypes.put("NetworkElement=2,ManagedElement=" + i, SGSN_MME);
        }
    }

    @Test
    public void givenDefaultPmCapabilityDelayValuesGetInitiationTimeoutSumsDefaultsAndUsesWorstCaseForLargeNumberOfNodes() {
        final int timeout = WORST_CASE_REPLY_WORST_DELAY + WORST_CASE_INITIATION_TIMEOUT * WORST_CASE_NUMBER_OF_SGSN_MME;
        final long initiationTimeout = subscriptionTimeout.getTotalTimeoutForAllSubscriptionNodes(neFdnsAndTypes);
        assertEquals(initiationTimeout, timeout);
    }

    @Test
    public void givenNonDefaultPmCapabilityDelayValuesGetInitiationTimeoutSumsDefaultsAndUsesWorstCaseForLargeNumberOfNodes() {
        final int radioNodeInitialTimeout = 6000;
        final int radioNodeWorstDelay = 7000;
        final int sgsnInitialTimeout = 8000;
        final int sgsnWorstDelay = 9000;
        when(pmCapabilitiesLookup.getCapabilityValue(RADIO_NODE, CAPABILITY_MODEL_NAME_STATS, NE_INITIATION_TIMEOUT)).thenReturn(
                radioNodeInitialTimeout);
        when(pmCapabilitiesLookup.getCapabilityValue(RADIO_NODE, CAPABILITY_MODEL_NAME_STATS, NE_WORST_REPLY_DELAY)).thenReturn(radioNodeWorstDelay);
        when(pmCapabilitiesLookup.getCapabilityValue(SGSN_MME, CAPABILITY_MODEL_NAME_STATS, NE_INITIATION_TIMEOUT)).thenReturn(sgsnInitialTimeout);
        when(pmCapabilitiesLookup.getCapabilityValue(SGSN_MME, CAPABILITY_MODEL_NAME_STATS, NE_WORST_REPLY_DELAY)).thenReturn(sgsnWorstDelay);
        final int timeout = sgsnWorstDelay + radioNodeWorstDelay;
        final long initiationTimeout = subscriptionTimeout.getTotalTimeoutForAllSubscriptionNodes(neFdnsAndTypes);
        assertEquals(initiationTimeout, timeout);
    }

    @Test
    public void givenTotalNodeTimeoutIsLessThanWorstCaseWorstCaseIsNotAddedToTimeout() {
        final Map<String, String> customNeFdnsAndTypes = new HashMap<>();
        final int numberOfNodes = WORST_CASE_REPLY_WORST_DELAY / WORST_CASE_INITIATION_TIMEOUT - 1;
        for (int i = 0; i < numberOfNodes; i++) {
            customNeFdnsAndTypes.put("NetworkElement=1,ManagedElement=" + i, RADIO_NODE);
        }
        final int timeout = WORST_CASE_INITIATION_TIMEOUT * numberOfNodes;
        final long initiationTimeout = subscriptionTimeout.getTotalTimeoutForAllSubscriptionNodes(customNeFdnsAndTypes);
        assertEquals(initiationTimeout, timeout);
    }

    @Test
    public void givenTotalNodeTimeoutIsGreaterThanWorstCaseWorstCaseIsReturned() {
        final Map<String, String> customNeFdnsAndTypes = new HashMap<>();
        final int numberOfNodes = WORST_CASE_REPLY_WORST_DELAY / WORST_CASE_INITIATION_TIMEOUT + 1;
        for (int i = 0; i < numberOfNodes; i++) {
            customNeFdnsAndTypes.put("NetworkElement=1,ManagedElement=" + i, RADIO_NODE);
        }
        final int timeout = WORST_CASE_REPLY_WORST_DELAY;
        final long initiationTimeout = subscriptionTimeout.getTotalTimeoutForAllSubscriptionNodes(customNeFdnsAndTypes);
        assertEquals(initiationTimeout, timeout);
    }
}
