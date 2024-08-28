/*------------------------------------------------------------------------------
 *******************************************************************************
 * COPYRIGHT Ericsson 2012
 *
 * The copyright to the computer program(s) herein is the property of
 * Ericsson Inc. The programs may be used and/or copied only with written
 * permission from Ericsson Inc. or in accordance with the terms and
 * conditions stipulated in the agreement/contract under which the
 * program(s) have been supplied.
 *******************************************************************************
 *----------------------------------------------------------------------------*/

package com.ericsson.oss.services.pm.listeners;

import java.util.Map;
import javax.ejb.ActivationConfigProperty;
import javax.ejb.MessageDriven;
import javax.inject.Inject;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageListener;
import javax.jms.ObjectMessage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ericsson.oss.services.pm.test.requests.SubscriptionOperationRequest;

@MessageDriven(activationConfig = {
        @ActivationConfigProperty(propertyName = "destinationType", propertyValue = "javax.jms.Queue"),
        @ActivationConfigProperty(propertyName = "destination", propertyValue = "queue/SubscriptionUpdatesQueue")})
public class IntegrationRequestResponseListener implements MessageListener {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    @Inject
    private IntegrationRequestResponseHolder sdl;

    @Override
    public void onMessage(final Message message) {
        try {
            logger.debug("Test-War: Received response from pm-service");
            final Object object = ((ObjectMessage) message).getObject();
            if (object instanceof SubscriptionOperationRequest) {
                final SubscriptionOperationRequest subscriptionInfo = (SubscriptionOperationRequest) object;
                logger.debug("Received response from pm-service: {}", subscriptionInfo.getSubscriptionOperation());
                final Map<String, Map<String, Object>> subscriptionAttr = subscriptionInfo.getReturnedSubscriptionAttributes();

                // subscriptionData.put((String)subscriptionAttr.get("name"),subscriptionAttr);
                sdl.setSubscriptionData(subscriptionAttr);
            }
        } catch (final JMSException e) {
            logger.error("Error while receiving message", e);
        }

    }

}
