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

package com.ericsson.oss.services.pm.initiation.scanner.master;

import java.io.Serializable;
import java.util.HashSet;
import java.util.Set;

import com.ericsson.oss.pmic.dto.subscription.Subscription;
import com.ericsson.oss.pmic.dto.subscription.enums.SubscriptionType;

/**
 * <code>SubscriptionWrapper </code> acts as a wrapper for {@link Subscription} and also have information on Node Fdns associated with the
 * subscription. This object represent valid subscription , see also {@link NullSubscriptionWrapper} represent invalid subscription.
 */
public class SubscriptionWrapper implements Serializable {

    public static final String VERSION = "2.0";

    private static final long serialVersionUID = 1L;

    private String subscriptionName;
    private Long subscriptionId;
    private Set<String> nodes;
    private Subscription subscription;
    private SubscriptionType subscriptionType;

    /**
     * Constructor
     */
    public SubscriptionWrapper() {

    }

    /**
     * Constructor
     *
     * @param subscription
     *         - the subscription
     * @param nodes
     *         - the nodes list
     */
    public SubscriptionWrapper(final Subscription subscription, final Set<String> nodes) {
        this.subscription = subscription;
        subscriptionId = subscription.getId();
        subscriptionName = subscription.getName();
        subscriptionType = subscription.getType();
        this.nodes = nodes;
    }

    /**
     * Gets all node fully distinguished names.
     *
     * @return the all node fully distinguished names
     */
    public Set<String> getAllNodeFdns() {
        if (nodes == null) {
            return new HashSet<>();
        }
        return nodes;
    }

    /**
     * Gets subscription.
     *
     * @return the subscription
     */
    public Subscription getSubscription() {
        return subscription;
    }

    /**
     * Is valid Subscription.
     *
     * @return returns true if subscription is valid
     */
    public boolean isValid() {
        return true;
    }

    /**
     * Gets subscription type.
     *
     * @return the subscription type
     */
    public SubscriptionType getSubscriptionType() {
        return subscriptionType;
    }

    /**
     * Sets subscription type.
     *
     * @param subscriptionType
     *         the subscription type
     */
    public void setSubscriptionType(final SubscriptionType subscriptionType) {
        this.subscriptionType = subscriptionType;
    }

    /**
     * Gets subscription name.
     *
     * @return the subscription name
     */
    public String getName() {
        return subscriptionName;
    }

    /**
     * Gets subscription id.
     *
     * @return the subscription id
     */
    public Long getSubscriptionId() {
        return subscriptionId;
    }
}
