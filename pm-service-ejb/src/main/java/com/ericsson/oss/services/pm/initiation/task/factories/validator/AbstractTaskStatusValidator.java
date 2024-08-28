/*
 * COPYRIGHT Ericsson 2017
 *
 * The copyright to the computer program(s) herein is the property of
 * Ericsson Inc. The programs may be used and/or copied only with written
 * permission from Ericsson Inc. or in accordance with the terms and
 * conditions stipulated in the agreement/contract under which the
 * program(s) have been supplied.
 */

package com.ericsson.oss.services.pm.initiation.task.factories.validator;

import javax.inject.Inject;

import org.slf4j.Logger;

import com.ericsson.oss.pmic.dto.subscription.Subscription;
import com.ericsson.oss.pmic.dto.subscription.enums.TaskStatus;
import com.ericsson.oss.services.pm.exception.DataAccessException;
import com.ericsson.oss.services.pm.exception.RuntimeDataAccessException;

/**
 * Abstract class containing common methods irrespective of subscription type
 */
public abstract class AbstractTaskStatusValidator {

    @Inject
    private Logger logger;

    /**
     * Provides the task status for the given subscription
     *
     * @param subscription
     *          subscription
     *
     * @return TaskStatus
     *          The {@link TaskStatus} to update to
     */
    public TaskStatus getTaskStatus(final Subscription subscription) {
        try {
            if (isTaskStatusError(subscription)) {
                return TaskStatus.ERROR;
            }
        } catch (final DataAccessException | RuntimeDataAccessException e) {
            logger.error("Database exception thrown while trying to extract scanners/Pmjob for subscription {}. Exception message: {}",
                    subscription.getId(), e.getMessage());
            logger.info("Database exception thrown while trying to extract scanners/Pmjob for subscription {}. Exception : {}",
                    subscription.getId(), e);
            return TaskStatus.ERROR;
        }
        return TaskStatus.OK;
    }

    /**
     * Checks if task status is Error in case number of active scanners does not equal number of nodes in subscription or number of active scanners
     * does not equal number of imsi associated with node in UETR subscription. Specific behavior needed on Baseband RadioNode where multiple scanners
     * can be associated to a System Defined Statistical Subscription.
     *
     * @param subscription
     *         Subscription
     *
     * @return true is Error else false
     * @throws DataAccessException
     *         will be thrown when an exception from database is thrown
     */
    protected abstract boolean isTaskStatusError(final Subscription subscription)
            throws DataAccessException;

}
