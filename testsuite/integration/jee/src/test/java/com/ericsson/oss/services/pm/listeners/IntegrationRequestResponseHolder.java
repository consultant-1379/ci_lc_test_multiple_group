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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import javax.ejb.Singleton;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class IntegrationRequestResponseHolder {

    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final Map<String, Map<String, Object>> subscriptionData = new ConcurrentHashMap<>();
    private CountDownLatch cl;

    public void setSubscriptionData(final Map<String, Map<String, Object>> subsData) {
        logger.debug("Adding subsciption data {} ", subsData);
        if (subsData != null && !subsData.isEmpty()) {
            subscriptionData.putAll(subsData);
        }

        cl.countDown();
    }

    public void setCountDownLatch(final CountDownLatch cl) {
        this.cl = cl;
    }

    public Map<String, Map<String, Object>> getAllSubs() {
        return subscriptionData;
    }

    public void clear() {
        subscriptionData.clear();
    }
}
