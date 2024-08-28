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

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;

import java.util.Hashtable;

import javax.naming.Context;
import javax.naming.NamingException;
import javax.naming.spi.InitialContextFactory;

import org.junit.Before;
import org.junit.Test;

public class RouterContextTest {

    private static final String JNDI_OBJECT_NAME = "any-name";
    private static final Object OBJECT_INSTANCE = new Object();
    private static final String INITIAL_CONTEXT_FACTORY_NAME = RouterInitialFactory.class.getName();

    @Before
    public void before() {
        System.setProperty(Context.INITIAL_CONTEXT_FACTORY, INITIAL_CONTEXT_FACTORY_NAME);
    }

    @Test
    public void routerContextDdefaultConstructorShouldInstantiateViaInitialFactory() throws NamingException {
        final RouterContext context = new RouterContext();
        assertEquals(context.lookup(JNDI_OBJECT_NAME), OBJECT_INSTANCE);
    }

    public static class RouterInitialFactory implements InitialContextFactory {

        @Override
        public Context getInitialContext(final Hashtable<?, ?> environment) throws NamingException {
            final Context context = mock(Context.class);
            when(context.lookup(JNDI_OBJECT_NAME)).thenReturn(OBJECT_INSTANCE);
            return context;
        }
    }
}
