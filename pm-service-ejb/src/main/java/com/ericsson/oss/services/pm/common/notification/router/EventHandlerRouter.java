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

import javax.ejb.Asynchronous;
import javax.ejb.Singleton;
import javax.ejb.Stateless;
import javax.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ericsson.oss.services.pm.common.notification.EventHandler;

/**
 * Routes an event based on the event class and the event properties. This implementation discovers all the event handlers registered in the
 * application during startup. The event handlers must implement the interface {@link EventHandler} and <b>must</b> be annotated with
 * {@link Singleton} or {@link Stateless}. An event handler could process the event in another thread if the method
 * {@link EventHandler#onEvent(Object)} is
 * annotated with {@link Asynchronous}.
 */
public class EventHandlerRouter implements EventRouter {

    private static final Logger LOG = LoggerFactory.getLogger(EventHandlerRouter.class);

    @Inject
    private EventHandlerRetriever eventHandlerRetriever;

    /**
     * Routes the event to the registered event handlers. The routing logic is based on event class and event properties.
     * This method does not throw exceptions.
     *
     * @param event
     *         The event to be routed.
     */
    @Override
    public void route(final Object event) {
        LOG.debug("Routing the event: '{}'...", event);
        final Class eventClass = event.getClass();
        for (final EventHandler eventHandler : eventHandlerRetriever.getEventHandlers(eventClass)) {
            try {
                if (eventHandler.isInterested(event)) {
                    eventHandler.onEvent(event);
                    break;
                }
            } catch (final Exception e) {
                LOG.error("Unexpected error when routing the event '{}' to the event handler '{}'.", event, eventHandler, e);
            }
        }
    }
}
