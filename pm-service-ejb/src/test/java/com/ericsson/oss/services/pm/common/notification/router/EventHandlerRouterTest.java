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

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;

import javax.naming.Binding;
import javax.naming.Context;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;

import org.junit.Before;
import org.junit.Test;
import org.mockito.internal.util.reflection.Whitebox;

import com.ericsson.oss.services.pm.common.notification.EventHandler;

public final class EventHandlerRouterTest {
    private static final String BASE_JNDI_CONTEXT = "java:module";

    private Context context;

    private EventHandlerRouter eventRouter;

    private EventHandler<String> eventHandlerA;

    private EventHandler<Integer> eventHandlerB1;

    private EventHandler<Integer> eventHandlerB2;

    private String eventA;

    private Integer eventB1;

    private Integer eventB2;

    @Before
    public void before() throws NamingException {
        eventRouter = new EventHandlerRouter();
        eventA = "Some event";
        eventB1 = 1;
        eventB2 = 2;
        mockDependencies();
        final EventHandlerRetriever eventHandlerRetriever = new EventHandlerRetriever();
        eventHandlerRetriever.init(context);
        Whitebox.setInternalState(eventRouter, "eventHandlerRetriever", eventHandlerRetriever);
    }

    @SuppressWarnings("unchecked")
    private void mockDependencies() throws NamingException {
        context = mock(RouterContext.class);
        eventHandlerA = mock(EventHandler.class);
        eventHandlerB1 = mock(EventHandler.class);
        eventHandlerB2 = mock(EventHandler.class);
        when(eventHandlerA.getEventClass()).thenReturn(String.class);
        when(eventHandlerB1.getEventClass()).thenReturn(Integer.class);
        when(eventHandlerB2.getEventClass()).thenReturn(Integer.class);
        when(eventHandlerA.isInterested(eventA)).thenReturn(true);
        when(eventHandlerB1.isInterested(eventB1)).thenReturn(true);
        when(eventHandlerB1.isInterested(eventB2)).thenReturn(false);
        when(eventHandlerB2.isInterested(eventB1)).thenReturn(false);
        when(eventHandlerB2.isInterested(eventB2)).thenReturn(true);
        mockNamingEnumeration(eventHandlerA, eventHandlerB1, eventHandlerB2);
    }

    @SuppressWarnings("unchecked")
    private void mockNamingEnumeration(final EventHandler<?>... eventHandlers) throws NamingException {
        final NamingEnumeration<Binding> namingEnumeration = mock(NamingEnumeration.class);
        final List<Binding> bindings = new ArrayList<>();
        final List<Boolean> hasMoreElements = new ArrayList<>();
        for (final EventHandler<?> eventHandler : eventHandlers) {
            final Binding binding = mock(Binding.class);
            when(binding.getObject()).thenReturn(eventHandler);
            bindings.add(binding);
            hasMoreElements.add(true);
        }
        hasMoreElements.add(false);
        final Boolean hasMoreElementsFirstCall = hasMoreElements.remove(0);
        final Boolean[] hasMoreElementsSubCalls = hasMoreElements.toArray(new Boolean[hasMoreElements.size()]);
        when(namingEnumeration.hasMoreElements()).thenReturn(hasMoreElementsFirstCall, hasMoreElementsSubCalls);
        final Binding nextFirstCall = bindings.remove(0);
        final Binding[] nextSubCalls = bindings.toArray(new Binding[bindings.size()]);
        when(namingEnumeration.next()).thenReturn(nextFirstCall, nextSubCalls);
        when(context.listBindings(BASE_JNDI_CONTEXT)).thenReturn(namingEnumeration);
    }

    @Test
    public void eventRouterShouldRouteOnlyIfEventHandlerIsInterestedInEvent() throws NamingException {
        eventRouter.route(eventA);
        eventRouter.route(eventB1);
        eventRouter.route(eventB2);
        verify(eventHandlerA, times(1)).onEvent(eventA);
        verify(eventHandlerB1, times(1)).onEvent(eventB1);
        verify(eventHandlerB1, never()).onEvent(eventB2);
        verify(eventHandlerB2, times(1)).onEvent(eventB2);
        verify(eventHandlerB2, never()).onEvent(eventB1);
    }

    @Test
    public void eventRouterShouldDiscardMultipleRegistrationOfSameEventHandler() throws NamingException {
        mockNamingEnumeration(eventHandlerA, eventHandlerA, eventHandlerA);
        eventRouter.route(eventA);
        verify(eventHandlerA, times(1)).onEvent(eventA);
    }

    @Test
    public void eventRouterShouldNotStopRoutingIfAnyEventHandlerThrowsException() throws NamingException {
        doThrow(Exception.class).when(eventHandlerA).onEvent(eventA);
        eventRouter.route(eventA);
        eventRouter.route(eventB1);
        eventRouter.route(eventB2);
        verify(eventHandlerA, times(1)).onEvent(eventA);
        verify(eventHandlerB1, times(1)).onEvent(eventB1);
        verify(eventHandlerB2, times(1)).onEvent(eventB2);
    }
}
