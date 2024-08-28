/*------------------------------------------------------------------------------
 *******************************************************************************
 * COPYRIGHT Ericsson 2016
 *
 * The copyright to the computer program(s) herein is the property of
 * Ericsson Inc. The programs may be used and/or copied only with written
 * permission from Ericsson Inc. or in accordance with the terms and
 * conditions stipulated in the agreement/contract under which the
 * program(s) have been supplied.
 *******************************************************************************
 *----------------------------------------------------------------------------*/
package com.ericsson.oss.service.pm.jmsreconnect;

import javax.enterprise.event.Event;
import javax.inject.Inject;
import javax.jms.ExceptionListener;
import javax.jms.JMSException;

import org.slf4j.Logger;

/**
 * The listener for JMS exception
 */
public class JMSFailoverExceptionListener implements ExceptionListener {

    @Inject
    private Logger logger;
    @Inject
    private Event<JMSFailOverEvent> event;

    /**
     * Handle fire a CDI event on receiving JMS Exception
     *
     * @param exception
     *         - the JMSException received on exception
     */
    @Override
    public void onException(final JMSException exception) {
        logger.error("JMS is up and running, Handled [{}] Exception and fire JMSFailOverEvent.", exception.getStackTrace());
        event.fire(new JMSFailOverEvent(){});
    }
}
