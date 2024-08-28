package com.ericsson.oss.services.pm.common.notification.router;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;

import javax.naming.Binding;
import javax.naming.Context;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;

import org.junit.Before;
import org.junit.Test;

import com.ericsson.oss.services.pm.common.notification.EventHandler;

public class EventHandlerRetrieverTest {
    private static final String BASE_JNDI_CONTEXT = "java:module";

    private Context context;

    private EventHandlerRetriever eventHandlerRetriever = new EventHandlerRetriever();

    @Before
    public void before() throws NamingException {
        mockDependencies();
    }

    @SuppressWarnings("unchecked")
    private void mockDependencies() throws NamingException {
        context = mock(RouterContext.class);
        final EventHandler<String> eventHandlerA = mock(EventHandler.class);
        final EventHandler<Integer> eventHandlerB1 = mock(EventHandler.class);
        final EventHandler<Integer> eventHandlerB2 = mock(EventHandler.class);
        when(eventHandlerA.getEventClass()).thenReturn(String.class);
        when(eventHandlerB1.getEventClass()).thenReturn(Integer.class);
        when(eventHandlerB2.getEventClass()).thenReturn(Integer.class);
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
        final Boolean[] hasMoreElementsSubCalls = hasMoreElements.toArray(new Boolean[0]);
        when(namingEnumeration.hasMoreElements()).thenReturn(hasMoreElementsFirstCall, hasMoreElementsSubCalls);
        final Binding nextFirstCall = bindings.remove(0);
        final Binding[] nextSubCalls = bindings.toArray(new Binding[0]);
        when(namingEnumeration.next()).thenReturn(nextFirstCall, nextSubCalls);
        when(context.listBindings(BASE_JNDI_CONTEXT)).thenReturn(namingEnumeration);
    }

    @Test
    public void eventRouterInitMethodShouldLocateAllEventHandlers() throws NamingException {
        eventHandlerRetriever.init(context);
        assertEquals(3, eventHandlerRetriever.getNumberOfEventHandlers());
        assertEquals(2, eventHandlerRetriever.getNumberOfEventClasses());
    }
}