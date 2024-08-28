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

package com.ericsson.oss.services.pm.scheduling.impl;

import java.util.Date;
import java.util.List;
import java.util.Map;

import javax.ejb.Asynchronous;
import javax.ejb.Stateless;
import javax.ejb.Timeout;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;
import javax.inject.Inject;

import org.slf4j.Logger;

import com.ericsson.oss.pmic.dao.availability.PmicDpsAvailabilityStatus;
import com.ericsson.oss.pmic.dto.subscription.enums.SubscriptionType;
import com.ericsson.oss.pmic.subscription.capability.SubscriptionCapabilityReader;
import com.ericsson.oss.services.pm.common.scheduling.SchedulingService;
import com.ericsson.oss.services.pm.common.systemdefined.SubscriptionAuditorLocal;
import com.ericsson.oss.services.pm.common.systemdefined.SubscriptionSystemDefinedAuditRule;
import com.ericsson.oss.services.pm.common.systemdefined.SystemDefinedPmCapabilities;
import com.ericsson.oss.services.pm.common.systemdefined.rule.SystemDefinedAuditRuleSelector;
import com.ericsson.oss.services.pm.initiation.common.RopUtil;
import com.ericsson.oss.services.pm.initiation.config.listener.ConfigurationChangeListener;
import com.ericsson.oss.services.pm.initiation.ctum.CtumSubscriptionServiceLocal;
import com.ericsson.oss.services.pm.initiation.util.constants.TimeConstants;
import com.ericsson.oss.services.pm.scheduling.cluster.MembershipListener;
import com.ericsson.oss.services.pm.services.exception.ValidationException;

/**
 * Audit scheduling of system defined subscription e.g CCTR, CTUM.
 */
@Stateless
public class SystemDefinedSubscriptionAuditScheduler extends SchedulingService {
    private static final String TIMER_NAME = "System_Defined_Subscription_Audit_Scheduler";

    @Inject
    private Logger logger;

    @Inject
    private RopUtil ropUtil;

    @Inject
    private ConfigurationChangeListener configurationChangeListener;

    @Inject
    private CtumSubscriptionServiceLocal ctumSubscriptionService;

    @Inject
    private SubscriptionAuditorLocal subscriptionAuditor;

    @Inject
    private MembershipListener membershipListener;
    @Inject
    private PmicDpsAvailabilityStatus dpsAvailabilityStatus;

    @Inject
    private SubscriptionCapabilityReader systemDefinedCapabilityReader;

    @Inject
    private SystemDefinedAuditRuleSelector systemDefinedAuditRuleSelector;


    @Override
    @Timeout
    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public void onTimeout() {
        logger.info("Scheduler triggered for system defined subscriptions");
        if (!dpsAvailabilityStatus.isAvailable()) {
            logger.warn("Failed to Auditing System Defined Subscription, Dps not available");
            return;
        }
        if (membershipListener.isMaster()) {
            ctumSubscriptionService.ctumAudit();
            logger.debug("Auditing System Defined Subscription");
            final Map<String, List<SystemDefinedPmCapabilities>> systemDefinedAttributesWithNodeTypes =
                    systemDefinedCapabilityReader.getSupportedSystemDefinedPmCapabilities();
            logger.info("System Defined Subscriptions to be audited {}", systemDefinedAttributesWithNodeTypes);
            for (final Map.Entry<String, List<SystemDefinedPmCapabilities>> capabilities : systemDefinedAttributesWithNodeTypes.entrySet()) {
                final SubscriptionType subType = SubscriptionType.fromString(capabilities.getKey());
                try {
                    final SubscriptionSystemDefinedAuditRule auditRule = systemDefinedAuditRuleSelector.getInstance(subType);
                    subscriptionAuditor.auditSystemDefinedSubscriptions(auditRule, capabilities.getValue());
                } catch (final ValidationException e) {
                    logger.error("Could not find audit rule for subscription type {}. error :: {}", subType, e.getMessage());
                    logger.info("No audit rule for subscription type {}", subType, e);
                }
            }
        }
    }

    /*
     * (non-Javadoc)
     *
     * @see com.ericsson.oss.services.pm.common.scheduling.SchedulingService#getTimerName ()
     */
    @Override
    public String getTimerName() {
        return TIMER_NAME;
    }

    /**
     * This method constructs the Scheduler post construction of SystemDefinedSubscriptionAuditScheduler.
     * <p>
     * NOTE: To avoid overall with other CBS audit and Scanner polling overlap, By Default System defined Subscription audit will start half of
     * 15min ROP.
     *  Subscription Audit time : 00:08:00, 00:23:00, 00:38:00, 00:43:00
     * </p>
     */
    @Asynchronous
    public void scheduleAudit() {
        logger.info("Initializing system defined subscription sync timer");
        final Date initialExpiration = ropUtil.getInitialExpirationTime(8);
        final long intervalInMillis = configurationChangeListener.getSysDefSubscriptionAuditInterval() * TimeConstants.ONE_MINUTE_IN_MILLISECONDS;
        try {
            createIntervalTimer(initialExpiration, intervalInMillis, false);
            logger.debug("Created timer, system defined subscription audit: {}", initialExpiration);
        } catch (final Exception exception) {
            logger.error("Failed to create system defined subscription sync scheduler", exception);
        }
    }

}
