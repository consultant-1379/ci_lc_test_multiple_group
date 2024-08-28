/*------------------------------------------------------------------------------
 *******************************************************************************
 * COPYRIGHT Ericsson 2012
 *
 * The copyright to the computer program(s) herein is the property of
 * Ericsson Inc. The programs may be used and/or copied only with written
 * permission from Ericsson Inc. or in accordance with the terms and
 * conditions stipulated in the agreement/contract under which the
 * program(s) have been supplied.
 *******************************************************************************
 *----------------------------------------------------------------------------*/

package com.ericsson.oss.services.pm.listeners;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Observes;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ericsson.oss.itpf.datalayer.dps.notification.DpsNotificationConfiguration;
import com.ericsson.oss.itpf.datalayer.dps.notification.event.DpsAssociationRemovedEvent;
import com.ericsson.oss.itpf.datalayer.dps.notification.event.DpsAttributeChangedEvent;
import com.ericsson.oss.itpf.datalayer.dps.notification.event.DpsObjectCreatedEvent;
import com.ericsson.oss.itpf.datalayer.dps.notification.event.DpsObjectDeletedEvent;
import com.ericsson.oss.itpf.sdk.eventbus.annotation.Consumes;

@ApplicationScoped
public class DpsNotificationListener {

    private final static Logger LOGGER = LoggerFactory.getLogger(DpsNotificationListener.class);
    final Map<String, DpsAttributeChangedEvent> scannerAttrChangedEvent = new HashMap<>();
    final Map<String, DpsObjectDeletedEvent> scannerDeletedEvent = new HashMap<>();
    final Map<String, DpsObjectCreatedEvent> scannerCreatedEvent = new HashMap<>();
    final Map<String, DpsAssociationRemovedEvent> nodeRemovedEvent = new HashMap<>();
    final Map<String, DpsAttributeChangedEvent> subAttributeChangeEvent = new HashMap<>();
    private CountDownLatch scannerAttrChangedCl;
    private CountDownLatch scannerDeletedCl;
    private CountDownLatch scannerCreatedCl;
    private CountDownLatch nodeRemovedCl;
    private CountDownLatch subAttributeChangeCl;

    public void processScannerUpdateNotification(@Observes @Consumes(endpoint = DpsNotificationConfiguration.DPS_EVENT_NOTIFICATION_CHANNEL_URI, filter = "type ='PMICScannerInfo'") final DpsAttributeChangedEvent dpsAttributeChangeEvent) {
        LOGGER.debug("Received DPS notification for Scanner Update object {} ", dpsAttributeChangeEvent.getFdn());
        scannerAttrChangedEvent.put(dpsAttributeChangeEvent.getFdn(), dpsAttributeChangeEvent);
        if (scannerAttrChangedCl != null) {
            scannerAttrChangedCl.countDown();
        }
    }

    public void processScannerDeleteNotification(@Observes @Consumes(endpoint = DpsNotificationConfiguration.DPS_EVENT_NOTIFICATION_CHANNEL_URI, filter = "type ='PMICScannerInfo'") final DpsObjectDeletedEvent dpsObjDeletedEvent) {
        LOGGER.debug("Received DPS notification for Scanner Delete object {} ", dpsObjDeletedEvent.getFdn());
        scannerDeletedEvent.put(dpsObjDeletedEvent.getFdn(), dpsObjDeletedEvent);
        if (scannerDeletedCl != null) {
            scannerDeletedCl.countDown();
        }
    }

    public void processScannerCreateNotification(@Observes @Consumes(endpoint = DpsNotificationConfiguration.DPS_EVENT_NOTIFICATION_CHANNEL_URI, filter = "type ='PMICScannerInfo'") final DpsObjectCreatedEvent dpsObjCreatedEvent) {
        LOGGER.debug("Received DPS notification for Scanner Create object {} ", dpsObjCreatedEvent.getFdn());
        scannerCreatedEvent.put(dpsObjCreatedEvent.getFdn(), dpsObjCreatedEvent);
        if (scannerCreatedCl != null) {
            scannerCreatedCl.countDown();
        }
    }

    public void processRemoveAssociationNotification(@Observes @Consumes(endpoint = DpsNotificationConfiguration.DPS_EVENT_NOTIFICATION_CHANNEL_URI, filter = "type ='StatisticalSubscription'") final DpsAssociationRemovedEvent dpsAssociationRemovedEvent) {
        LOGGER.debug("Received DPS notification for Remove Node Association object {} ", dpsAssociationRemovedEvent.getFdn());
        nodeRemovedEvent.put(dpsAssociationRemovedEvent.getFdn(), dpsAssociationRemovedEvent);
        if (nodeRemovedCl != null) {
            nodeRemovedCl.countDown();
        }
    }

    public void processSubscriptionAdminStateUpdatedNotification(@Observes @Consumes(endpoint = DpsNotificationConfiguration.DPS_EVENT_NOTIFICATION_CHANNEL_URI, filter = "(namespace = 'pmic_subscription' OR namespace = 'pmic_stat_subscription' OR namespace = 'pmic_ebs_subscription' OR namespace = 'pmic_cell_subscription' OR namespace = 'pmic_continuous_cell_subscription' OR namespace = 'pmic_ebm_subscription' OR namespace = 'pmic_event_subscription') AND type <> 'PMICScannerInfo'") final DpsAttributeChangedEvent dpsNotificationEvent) {
        LOGGER.debug("Received DPS notification for Subscription Attribute Changed on object {}. Attributes changed is {}",
                dpsNotificationEvent.getFdn(), dpsNotificationEvent.getChangedAttributes());
        subAttributeChangeEvent.put(dpsNotificationEvent.getFdn(), dpsNotificationEvent);
        if (subAttributeChangeCl != null) {
            subAttributeChangeCl.countDown();
        }
    }

    public DpsAttributeChangedEvent getDpsAttributeChangedEventForNode(final String nodeFdn) {
        LOGGER.debug("Finding DPS changed attribute event notification for fdn {} in current map {} ", nodeFdn, scannerAttrChangedEvent);
        return scannerAttrChangedEvent.remove(nodeFdn);
    }

    public DpsObjectDeletedEvent getDpsObjectDeletedEventForNode(final String nodeFdn) {
        LOGGER.debug("Finding DPS object deleted event notification for fdn {} in current map {} ", nodeFdn, scannerDeletedEvent);
        return scannerDeletedEvent.remove(nodeFdn);
    }

    public DpsObjectCreatedEvent getDpsObjectCreatedEventForNode(final String nodeFdn) {
        LOGGER.debug("Finding DPS object created event notification for fdn {} in current map {} ", nodeFdn, scannerCreatedEvent);
        return scannerCreatedEvent.remove(nodeFdn);
    }

    public DpsAttributeChangedEvent getDpsAttributeChangeEventForSubscription(final String subFdn) {
        LOGGER.debug("Finding DPS object created event notification for fdn {} in current map {} ", subFdn, subAttributeChangeEvent);
        return subAttributeChangeEvent.remove(subFdn);
    }

    public void setAttChangedCountDownLatch(final CountDownLatch cl) {
        scannerAttrChangedCl = cl;
    }

    public void setObjDeletedCountDownLatch(final CountDownLatch cl) {
        scannerDeletedCl = cl;
    }

    public void setObjCreatedCountDownLatch(final CountDownLatch cl) {
        scannerCreatedCl = cl;
    }

    public void setNodeRemovedCountDownLatch(final CountDownLatch cl) {
        nodeRemovedCl = cl;
    }

    public void setSubAttributeChangeCountDownLatch(final CountDownLatch cl) {
        subAttributeChangeCl = cl;
    }

    public void clear() {
        scannerAttrChangedEvent.clear();
        scannerDeletedEvent.clear();
        scannerCreatedEvent.clear();
        nodeRemovedEvent.clear();
        subAttributeChangeEvent.clear();
    }
}
