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

package com.ericsson.oss.services.pm.initiation.schedulers;

import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map;
import javax.ejb.Stateless;
import javax.ejb.Timeout;
import javax.ejb.Timer;
import javax.ejb.TimerConfig;
import javax.ejb.TimerService;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;
import javax.inject.Inject;

import org.slf4j.Logger;

import com.ericsson.oss.pmic.dto.subscription.ResourceSubscription;
import com.ericsson.oss.pmic.dto.subscription.Subscription;
import com.ericsson.oss.pmic.dto.subscription.enums.SubscriptionType;
import com.ericsson.oss.services.pm.exception.DataAccessException;
import com.ericsson.oss.services.pm.initiation.ctum.CtumSubscriptionServiceLocal;
import com.ericsson.oss.services.pm.scheduling.cluster.MembershipListener;
import com.ericsson.oss.services.pm.services.generic.SubscriptionReadOperationService;
import com.ericsson.oss.services.pm.services.generic.SubscriptionWriteOperationService;

/**
 * This class performs the update of the attribute numberOfNodes in Resource Subscription when a node is removed from ENM. Timer processing is to
 * avoid doing the update several time when multiple nodes get removed in a bunch. Updating is done at timer expiration and if further
 * PmFunctionDelete notifications are received after the first they are ignored this handler.
 */
@Stateless
public class NodeRemovalHandler {

    private static final String TIMER_NAME = "Node_Removal_Timer";
    private static final long NODE_REMOVAL_TIMER_DURATION = 60 * 1000L;

    @Inject
    private Logger logger;
    @Inject
    private TimerService timerService;
    @Inject
    private SubscriptionReadOperationService subscriptionReadOperationService;
    @Inject
    private MembershipListener membershipListener;
    @Inject
    private CtumSubscriptionServiceLocal ctumService;
    @Inject
    private SubscriptionWriteOperationService subscriptionWriteOperationService;

    /**
     * Performs the update of the attribute numberOfNodes in Resource Subscriptions and trigger the Ctum audit to process Ctum as well.
     */
    @Timeout
    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public void updateNumberOfNodesInSubscriptions() {
        if (membershipListener.isMaster()) {
            logger.debug("Updating numberOfNodes attribute in Subscriptions due to node removal");
            try {
                final List<Subscription> subscriptions = subscriptionReadOperationService
                        .findAllBySubscriptionModelInfo(SubscriptionType.RESOURCE.getModelInfo(), true);
                for (final Subscription subscription : subscriptions) {
                    final int storedNumberOfNodes = ((ResourceSubscription) subscription).getNumberOfNodes();
                    final int actualNumberOfNodes = ((ResourceSubscription) subscription).getNodes().size();
                    if (storedNumberOfNodes != actualNumberOfNodes) {
                        ((ResourceSubscription) subscription).setNumberOfNodes(actualNumberOfNodes);
                        final Map<String, Object> map = Subscription.getMapWithPersistenceTime();
                        map.put(ResourceSubscription.ResourceSubscription120Attribute.numberOfNodes.name(), actualNumberOfNodes);
                        subscriptionWriteOperationService.updateAttributes(subscription.getId(), map);
                        subscription.setPersistenceTime((Date) map.get(Subscription.Subscription220Attribute.persistenceTime.name()));
                    }
                }
            } catch (final IllegalArgumentException | DataAccessException exception) {
                logger.error("Updating numberOfNodes attribute in Subscriptions due to node removal failed: {}", exception.getMessage());
                logger.info("Updating numberOfNodes failed:", exception);
            }
            // triggering Ctum audit to update Ctum as well.
            ctumService.ctumAudit();
        }
    }

    /**
     * creates the Timer for DPS update if it isn't already set
     */
    public void setTimer() {
        if (!checkTimerExists()) {
            logger.debug("creating timer for updating numberOfNodes due to node removal");
            final TimerConfig timerConfig = new TimerConfig(TIMER_NAME, false);
            timerService.createSingleActionTimer(NODE_REMOVAL_TIMER_DURATION, timerConfig);
        }
    }

    private boolean checkTimerExists() {
        boolean timerExists = false;
        final Collection<Timer> timers = timerService.getTimers();
        for (final Timer timer : timers) {
            if (timer.getInfo().equals(TIMER_NAME)) {
                timerExists = true;
                break;
            }
        }
        return timerExists;
    }

}
