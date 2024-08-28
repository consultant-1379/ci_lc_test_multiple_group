/*------------------------------------------------------------------------------
 *******************************************************************************
 * COPYRIGHT Ericsson 2019
 *
 * The copyright to the computer program(s) herein is the property of
 * Ericsson Inc. The programs may be used and/or copied only with written
 * permission from Ericsson Inc. or in accordance with the terms and
 * conditions stipulated in the agreement/contract under which the
 * program(s) have been supplied.
 *******************************************************************************
 *----------------------------------------------------------------------------*/

package com.ericsson.oss.services.pm.common.notification.router;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import javax.annotation.PostConstruct;
import javax.ejb.Lock;
import javax.ejb.LockType;
import javax.ejb.Singleton;
import javax.ejb.Startup;
import javax.inject.Inject;
import javax.naming.Binding;
import javax.naming.Context;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ericsson.oss.services.pm.common.notification.EventHandler;

/**
 * Class to fetch all the handlers that Extends {@link EventHandler} and store in a local hash map depending on the eventClass type. The collection of
 * EventHandlers will be returned n request.
 */
@Startup
@Singleton
@Lock(LockType.READ)
public class EventHandlerRetriever {

    private static final Logger LOG = LoggerFactory.getLogger(EventHandlerRetriever.class);

    private static final String BASE_JNDI_CONTEXT = "java:module";

    private final Map<Class, Set<EventHandler>> eventHandlersByEventClass;

    private int numberOfEventHandlers;

    /**
     * Instantiates a new Event handler router.
     */
    public EventHandlerRetriever() {
        eventHandlersByEventClass = new HashMap<>();
    }

    /**
     * Retrieves all the available beans extending EventHandler and store that information in the hashmap.
     *
     * @param context
     *            the context
     * @throws NamingException
     *            the naming exception
     */
    @Inject
    @PostConstruct
    protected void init(final Context context) throws NamingException {
        final NamingEnumeration<Binding> bindings = context.listBindings(BASE_JNDI_CONTEXT);
        while (bindings.hasMoreElements()) {
            final Object object = bindings.next().getObject();
            if (object instanceof EventHandler) {
                addEventHandler((EventHandler) object);
            }
        }
        LOG.info("Number of event handlers: {}; Number of event classes: {}", getNumberOfEventHandlers(), getNumberOfEventClasses());
    }

    private void addEventHandler(final EventHandler<?> eventHandler) {
        final Class<?> eventClass = eventHandler.getEventClass();
        if (!eventHandlersByEventClass.containsKey(eventClass)) {
            eventHandlersByEventClass.put(eventClass, new HashSet<>());
        }
        if (getEventHandlers(eventClass).add(eventHandler)) {
            numberOfEventHandlers++;
        }
    }

    /**
     * Gets number of event handlers.
     *
     * @return the number of event handlers
     */
    protected int getNumberOfEventHandlers() {
        return numberOfEventHandlers;
    }

    /**
     * Gets number of event classes.
     *
     * @return the number of event classes
     */
    protected int getNumberOfEventClasses() {
        return eventHandlersByEventClass.size();
    }

    /**
     * @param eventClass
     *            eventClass for which the Handlers has to be returned
     * @return Set of handler that extends EventHandler with the type as passed eventClass.
     */
    public Set<EventHandler> getEventHandlers(final Class eventClass) {
        final Set<EventHandler> eventHandlers = eventHandlersByEventClass.get(eventClass);
        return eventHandlers != null ? eventHandlers : Collections.emptySet();
    }
}
