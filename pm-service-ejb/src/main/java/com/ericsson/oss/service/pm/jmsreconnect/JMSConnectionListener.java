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

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.annotation.Resource;
import javax.ejb.Singleton;
import javax.ejb.Startup;
import javax.inject.Inject;
import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.JMSException;

import org.slf4j.Logger;

/**
 * Singleton been created on container startup and build a jms connection to jms server. The connection will be registered with an exception listener
 */
@Singleton
@Startup
public class JMSConnectionListener {

    @Resource(lookup = "java:/ConnectionFactory")
    private ConnectionFactory connectionFactory;

    @Inject
    private JMSFailoverExceptionListener pmExceptionListener;

    @Inject
    private Logger log;

    private Connection connection;

    /**
     * During init, a connection is established and exception listener is registered.
     *
     * @throws JMSException
     *         - JMS Exception on connection start
     */
    @PostConstruct
    public void init() throws JMSException {
        connection = connectionFactory.createConnection();

        connection.setExceptionListener(pmExceptionListener);

        connection.start();
        log.info("JMS exception listener registered");
    }

    /**
     * Connection is closed on destory.
     *
     * @throws JMSException
     *         - JMS Exception on connection close
     */
    @PreDestroy
    public void destory() throws JMSException {
        connection.close();
    }
}
