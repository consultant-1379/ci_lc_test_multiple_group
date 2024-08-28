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

package com.ericsson.oss.services.pm.cbs.notification;

import static com.ericsson.oss.services.pm.common.logging.PMICLog.Event.CBS_AUDIT_EXECUTION;
import static com.ericsson.oss.services.pm.initiation.common.Constants.CBS_AUDIT_CRITERIA_DOES_NOT_EXIST;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Observes;
import javax.inject.Inject;

import org.slf4j.Logger;

import com.ericsson.oss.itpf.sdk.eventbus.model.annotation.Modeled;
import com.ericsson.oss.services.pm.cbs.core.CBSCriteriaExecutor;
import com.ericsson.oss.services.pm.cbs.events.CBSExecutionPlanEvent200;
import com.ericsson.oss.services.pm.common.logging.SystemRecorderWrapperLocal;

/**
 * Modeled Listener for CBSExecutionPlanEvent events
 */
@ApplicationScoped
public class CBSExecutionPlanEventListener {

    @Inject
    private Logger logger;

    @Inject
    private SystemRecorderWrapperLocal systemRecorder;

    @Inject
    private CBSCriteriaExecutor cbsCriteriaExecutor;

    /**
     * Receives the CBSExecutionPlanEvent and initiates cbsCriteriaExecutor to act on the event
     *
     * @param cbsExecutionPlanEvent200
     *         - Criteria Based Subscription Event to update CBSResourceSubscrptions after CBS Audit.
     */

    public void receiveCbsExecutionPlanEvent(@Observes @Modeled final CBSExecutionPlanEvent200 cbsExecutionPlanEvent200) {
        try {
            final String query = cbsExecutionPlanEvent200.getCbsQuery();
            systemRecorder.eventCoarse(CBS_AUDIT_EXECUTION, query, "CBS Audit Execution Started");
            if (null == query) {
                systemRecorder.eventCoarse(CBS_AUDIT_EXECUTION, query, CBS_AUDIT_CRITERIA_DOES_NOT_EXIST);
            } else {
                cbsCriteriaExecutor.executeCriteriaAndUpdateNodeList(cbsExecutionPlanEvent200);
            }
        } catch (final Exception exception) {
            logger.error("CBSExecutionPlanEvent {} Exception {} while executing CBSExecutionPlanEventListener", cbsExecutionPlanEvent200,
                    exception.getMessage());
            logger.info("CBSExecutionPlanEvent {} Exception while executing CBSExecutionPlanEventListener", cbsExecutionPlanEvent200,
                    exception);
        }
    }
}
