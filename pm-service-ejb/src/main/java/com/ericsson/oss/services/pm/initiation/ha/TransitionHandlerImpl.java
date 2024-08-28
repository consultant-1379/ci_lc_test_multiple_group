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

package com.ericsson.oss.services.pm.initiation.ha;

import static com.ericsson.oss.pmic.api.constants.ModelledConfigurationConstants.Ebsl.PROP_PMIC_EBSL_ROP_IN_MINUTES;

import java.util.concurrent.Future;
import javax.annotation.Resource;
import javax.ejb.AsyncResult;
import javax.ejb.Asynchronous;
import javax.ejb.Stateless;
import javax.ejb.Timeout;
import javax.ejb.TimerConfig;
import javax.ejb.TimerService;
import javax.inject.Inject;

import org.slf4j.Logger;

import com.ericsson.oss.services.pm.ebs.utils.EbsConfigurationListener;
import com.ericsson.oss.services.pm.initiation.ha.operations.InitiationHaManager;
import com.ericsson.oss.services.pm.initiation.notification.senders.PmicSubscriptionUpdateMassageSender;
import com.ericsson.oss.services.pm.scheduling.cluster.MembershipListener;

/**
 * Transition handler.
 */
@Stateless
public class TransitionHandlerImpl {

    private static final String PM_SERVICE_INITIATION_HA_TIMER_NAME = "PM_SERVICE_INITIATION_HA_TIMER";

    private static final Long FREQUENCY = 15 * 1000L;

    private boolean isUnfinishedHandled;

    @Inject
    private Logger log;

    @Inject
    private InitiationHaManager activationManager;

    @Inject
    private MembershipListener membershipListener;

    @Inject
    private PmicSubscriptionUpdateMassageSender pmicSubscriptionUpdateMassageSender;

    @Inject
    private EbsConfigurationListener ebsConfigurationListener;

    @Resource
    private TimerService timerService;

    /**
     * Create timer for membership check.
     *
     * @return returns true if timer was successfully created
     */
    @Asynchronous
    public Future<Boolean> createTimerForMembershipCheck() {
        log.info("Creating timer to keep checking membership status and handle unfinished initiations if membership changes");
        final TimerConfig timerConfig = new TimerConfig();
        timerConfig.setInfo(PM_SERVICE_INITIATION_HA_TIMER_NAME);
        timerConfig.setPersistent(false);
        log.info("Setting a programmatic timeout for each {} milliseconds from now.", FREQUENCY);
        timerService.createIntervalTimer(0, FREQUENCY, timerConfig);
        return new AsyncResult<>(true);
    }

    /**
     * Checks membership.
     */
    @Timeout
    public void checkMembership() {
        log.debug("ha timer for initiation operations running, checking if membership has changed.");
        if (membershipListener.isMaster() && !isUnfinishedHandled()) {
            log.info("Switched to Master node, handling ha for initiation operations.");
            isUnfinishedHandled = activationManager.handleUnfinishedTasks();
            pmicSubscriptionUpdateMassageSender.sendNotificationOnSwitchOverToExternalConsumer(PROP_PMIC_EBSL_ROP_IN_MINUTES,
                    ebsConfigurationListener.getPmicEbslRopInMinutes());
        } else if (!membershipListener.isMaster() && isUnfinishedHandled()) {
            log.info("Switched to Slave node, will not handle ha for initiation operations.");
            isUnfinishedHandled = false;
        }
    }

    /**
     * Is unfinished taks handled checks.
     *
     * @return returns true if unfinished tasks are being handled
     */
    public boolean isUnfinishedHandled() {
        return isUnfinishedHandled;
    }

    /**
     * Sets unfinished handled.
     *
     * @param isUnfinishedHandled
     *         boolean value for if unfinished tasks are being handled
     */
    protected void setUnfinishedHandled(final boolean isUnfinishedHandled) {
        this.isUnfinishedHandled = isUnfinishedHandled;
    }
}
