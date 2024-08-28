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

package com.ericsson.oss.service.pm.jmsreconnect;

import static com.ericsson.oss.pmic.api.constants.ModelledConfigurationConstants.Ebsl.PROP_PMIC_EBSL_ROP_IN_MINUTES;
import static com.ericsson.oss.services.pm.common.logging.PMICLog.Event.JMS_FAILOVER;

import java.util.Collection;
import java.util.List;
import javax.ejb.Asynchronous;
import javax.ejb.Stateless;
import javax.ejb.Timeout;
import javax.ejb.Timer;
import javax.ejb.TimerService;
import javax.enterprise.event.Observes;
import javax.inject.Inject;

import org.slf4j.Logger;

import com.ericsson.oss.pmic.dto.subscription.enums.RopPeriod;
import com.ericsson.oss.services.pm.collection.roptime.SupportedRopTimes;
import com.ericsson.oss.services.pm.collection.schedulers.FileCollectionTaskSenderBeanHelper;
import com.ericsson.oss.services.pm.common.logging.SystemRecorderWrapperLocal;
import com.ericsson.oss.services.pm.ebs.utils.EbsConfigurationListener;
import com.ericsson.oss.services.pm.initiation.cache.model.value.FileCollectionTaskWrapper;
import com.ericsson.oss.services.pm.initiation.notification.senders.PmicSubscriptionUpdateMassageSender;
import com.ericsson.oss.services.pm.initiation.util.RopTime;
import com.ericsson.oss.services.pm.scheduling.cluster.MembershipListener;
import com.ericsson.oss.services.pm.time.TimeGenerator;

/**
 * Stateless session bean JMSFailoverHandler is to handler JMS Exception on JMS failover
 * Due to cloud single instance behavior, File collection tasks resend after 5min delay
 */
@Stateless
public class JMSFailoverHandler {

    private static final Long JMS_FAILOVER_TIMEOUT = 300000L;

    private static final Long SAFE_DELAY = 60000L;

    @Inject
    private Logger logger;

    @Inject
    private FileCollectionTaskSenderBeanHelper helper;

    @Inject
    private MembershipListener membershipChangeListener;

    @Inject
    private SystemRecorderWrapperLocal systemRecorder;

    @Inject
    private JMSLostFileCollectionTasksHandler lostTasksHandler;

    @Inject
    private TimerService timerService;

    @Inject
    private TimeGenerator timeGenerator;

    @Inject
    private PmicSubscriptionUpdateMassageSender pmicSubscriptionUpdateMassageSender;

    @Inject
    private EbsConfigurationListener ebsConfigurationListener;

    @Inject
    private SupportedRopTimes supportedRopTimes;

    /**
     * On JMS failover, the method will create the timer with 5 minute delay
     *
     * @param jmsFailOverEvent
     *            - JMS fail over event
     */
    @Asynchronous
    public void handleFailOver(@Observes final JMSFailOverEvent jmsFailOverEvent) {
        lostTasksHandler.fetchOneMinuteFileCollectionTasksFromCache();
        cancelTimers();
        timerService.createTimer(getTimeOutDuration(), "JMSFailoverHandlerTimer");
        systemRecorder.eventCoarse(JMS_FAILOVER, this.getClass().getSimpleName(),
                String.format("JMS Fail over will be handled by master instance. isMaster: %b . Timer is created to resend File Collection tasks",
                        membershipChangeListener.isMaster()));
        if (membershipChangeListener.isMaster()) {
            pmicSubscriptionUpdateMassageSender.sendNotificationOnSwitchOverToExternalConsumer(PROP_PMIC_EBSL_ROP_IN_MINUTES,
                    ebsConfigurationListener.getPmicEbslRopInMinutes());
        }
    }

    /**
     * On Timeout, the method will resend all unresponded messages
     */
    @Timeout
    public void sendLostFileCollectionTasks() {
        if (membershipChangeListener.isMaster()) {
            final List<FileCollectionTaskWrapper> lostTasks = lostTasksHandler.getAllSentFileCollectionTasks();
            logger.info("There are {} FileCollection Tasks to be Resend ", lostTasks.size());
            final int numberOfTasksSent = helper.sendUnresponsedFileCollectionTasks(lostTasks);
            systemRecorder.eventCoarse(JMS_FAILOVER, this.getClass().getSimpleName(),
                    String.format("Tasks Queue size: %d . Sent %d file collection tasks for %s.", lostTasks.size(), numberOfTasksSent, "JMS Failover"));
        }
    }

    /**
     * Calculate the timeout duration based on the JMS failover time. If JMS
     * failover happens before 3 minutes or after 1 minute the
     * FileCollectionTaskSender, then timeout is 3 minutes, other wise timeout
     * is calculated so that the difference between FileCollectionTaskSender
     * timer and jmshandler timer for sendLostFileCollectionTasks() sender is 4
     * minutes
     *
     * @return timeout duration
     */
    private long getTimeOutDuration() {
        final RopTime currentRop = new RopTime(timeGenerator.currentTimeMillis(), RopPeriod.FIFTEEN_MIN.getDurationInSeconds());
        final long jmsHandlingTime = currentRop.getTime() + JMS_FAILOVER_TIMEOUT;
        final long fileCollectionTimeOut = currentRop.getCurrentRopStartTimeInMilliSecs() +
                supportedRopTimes.getRopTime(RopPeriod.FIFTEEN_MIN.getDurationInSeconds()).getCollectionDelayInMilliSecond();

        long timeout = JMS_FAILOVER_TIMEOUT;

        if (jmsHandlingTime >= fileCollectionTimeOut && jmsHandlingTime - fileCollectionTimeOut <= JMS_FAILOVER_TIMEOUT + SAFE_DELAY) {
            timeout = JMS_FAILOVER_TIMEOUT + SAFE_DELAY + fileCollectionTimeOut - currentRop.getTime();
        }

        logger.debug("Timeout for JMS Failover Handling is:{}.", timeout);
        return timeout;
    }

    private void cancelTimers() {
        int cancelledTimers = 0;
        final Collection<Timer> timers = timerService.getTimers();
        if (timers != null) {
            for (final Timer timer : timers) {
                timer.cancel();
                logger.debug("Existing timer {} cancelled ", timer);
                cancelledTimers++;
            }
        }
        logger.info("Cancelled {} JMSFailoverHandler timers.", cancelledTimers);
    }

}
