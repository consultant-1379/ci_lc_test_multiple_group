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

package com.ericsson.oss.services.pm.common.notification;

/**
 * Represents an event handler.
 *
 * @param <E>
 *         The type of event this event handler is capable of handling.
 */
public interface EventHandler<E> {

    /**
     * The event handler method.
     *
     * @param event
     *         The event to be handled.
     */
    void onEvent(E event);

    /**
     * Returns the event class of the event handler. The event handler will only process events for this class or any subclasses of it.
     *
     * @return The event class.
     */
    Class<E> getEventClass();

    /**
     * Check if the event handler is interested in the event, based on the event properties. This method is useful for routing purposes. Clients
     * could check if the event handler is interested in the event prior delivering it.
     *
     * @param event
     *         The event of interest.
     *
     * @return true/false
     */
    boolean isInterested(E event);
}
