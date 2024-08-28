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

package com.ericsson.oss.services.pm.common.notification.router;

import com.ericsson.oss.services.pm.common.notification.EventHandler;

/**
 * Represents an event router. The objective is to route an event to the interested event handlers, based on the event class and/or event properties.
 *
 * @see EventHandler
 */
public interface EventRouter {
    /**
     * Routes the event to the interested event handlers.
     *
     * @param event
     *         The event to be routed.
     */
    void route(Object event);
}
