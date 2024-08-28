/*
 * COPYRIGHT Ericsson 2017
 * The copyright to the computer program(s) herein is the property of
 * Ericsson Inc. The programs may be used and/or copied only with written
 * permission from Ericsson Inc. or in accordance with the terms and
 * conditions stipulated in the agreement/contract under which the
 * program(s) have been supplied.
 */

package com.ericsson.oss.services.pm.initiation.task;

import java.util.Set;

import com.ericsson.oss.pmic.dto.subscription.Subscription;
import com.ericsson.oss.pmic.dto.subscription.enums.TaskStatus;

/**
 * This class validates the Task status for the Subscription
 *
 * @param <S>
 *         the subscription type parameter
 */
public interface TaskStatusValidator<S extends Subscription> {

    /**
     * Extrapolates the subscription task status based on the existence of at least one scanner/PmJob with UNKNOWN or ERROR state.
     *
     * @param subscription
     *         - The subscription we want to find the task status of.
     *
     * @return - TaskStatus.OK if no scanner/PmJob is found in ERROR/UNKNOWN state OR TaskStatus.ERROR if at least on scanner/PmJob is in
     * ERROR/UNKNOWN state.
     */
    TaskStatus getTaskStatus(final S subscription);

    /**
     * Validates the subscription's task status and admin state based on the number of nodes and scanners the subscription has The action taken
     * depends on the type of subscription
     *
     * @param subscription
     *         the subscription
     */
    void validateTaskStatusAndAdminState(final S subscription);

    /**
     * Validates the subscription's task status and admin state based on the number of nodes and scanners the subscription has The action taken
     * depends on the type of subscription
     *
     * @param subscription
     *         the subscription
     * @param nodeFdn
     *         node FDN to be verified.
     */
    void validateTaskStatusAndAdminState(final S subscription, String nodeFdn);

    /**
     * Validates the subscription's task status and admin state based on the number of nodes and scanners the subscription has The action taken
     * depends on the type of subscription
     *
     * @param subscription
     *         the subscription
     * @param nodeFdns
     *         Set of node FDNs to be verified.
     */
    void validateTaskStatusAndAdminState(final S subscription, Set<String> nodeFdns);

}
