/*------------------------------------------------------------------------------
 *******************************************************************************
 * COPYRIGHT Ericsson 2014
 *
 * The copyright to the computer program(s) herein is the property of
 * Ericsson Inc. The programs may be used and/or copied only with written
 * permission from Ericsson Inc. or in accordance with the terms and
 * conditions stipulated in the agreement/contract under which the
 * program(s) have been supplied.
 *******************************************************************************
 *----------------------------------------------------------------------------*/

package com.ericsson.oss.services.pm.initiation.integration;

import java.io.Serializable;
import javax.annotation.Resource;
import javax.inject.Inject;
import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.JMSException;
import javax.jms.MessageProducer;
import javax.jms.ObjectMessage;
import javax.jms.Queue;
import javax.jms.Session;

import org.slf4j.Logger;

public class SubscriptionOperationMessageSender {

    @Inject
    private Logger logger;

    @Resource(mappedName = "java:/queue/SubscriptionoperationsQueue")
    private Queue queue;

    @Resource(mappedName = "java:/ConnectionFactory")
    private ConnectionFactory cf;

    public void sendTestOperationMessageToPmService(final Serializable subscriptionOperationInfo) {
        Connection connection = null;
        MessageProducer producer = null;
        Session session = null;
        try {
            connection = cf.createConnection();
            session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
            producer = session.createProducer(queue);
            final ObjectMessage message = session.createObjectMessage();
            message.setObject(subscriptionOperationInfo);
            producer.send(message);
        } catch (final JMSException e) {
            logger.error("Error in sending message to queue: {}", e);
        } finally {
            if (session != null) {
                try {
                    session.close();
                    connection.close();
                } catch (final JMSException e) {
                    logger.error("Error closing message producer", e);
                }
            }
        }
    }

}
