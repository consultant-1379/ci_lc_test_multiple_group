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

package com.ericsson.oss.services.pm.scheduling.api;

import java.io.Serializable;
import javax.ejb.ScheduleExpression;
import javax.ejb.Timer;

import com.ericsson.oss.pmic.dto.subscription.enums.AdministrationState;

/**
 * Scheduler service initiation interface.
 */
public interface SchedulerServiceInitiationLocal {

    /**
     * Create timer timer.
     *
     * @param info
     *         the timer info
     * @param expression
     *         the schedule expression
     * @param persistant
     *         boolean value if the timer is persistant
     *
     * @return the timer
     */
    Timer createTimer(Serializable info, ScheduleExpression expression, boolean persistant);

    /**
     * Gets timer.
     *
     * @param subscriptionId
     *         - id of the subscription  to get timer for
     * @param eventType
     *         - current subscription state
     *
     * @return - returns a Timer Object
     */
    Timer getTimer(final long subscriptionId, final AdministrationState eventType);

}
