/*
 * COPYRIGHT Ericsson 2017
 * The copyright to the computer program(s) herein is the property of
 * Ericsson Inc. The programs may be used and/or copied only with written
 * permission from Ericsson Inc. or in accordance with the terms and
 * conditions stipulated in the agreement/contract under which the
 * program(s) have been supplied.
 */

package com.ericsson.oss.services.pm.adjuster.impl;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import javax.enterprise.context.ApplicationScoped;

import com.ericsson.oss.pmic.dto.node.Node;
import com.ericsson.oss.pmic.dto.subscription.MoinstanceSubscription;
import com.ericsson.oss.pmic.dto.subscription.cdts.MoinstanceInfo;
import com.ericsson.oss.services.pm.adjuster.SubscriptionDataAdjusterQualifier;
import com.ericsson.oss.services.pm.exception.DataAccessException;

/**
 * MoInstanceSubscriptionPfmDataAdjuster.
 */
@ApplicationScoped
@SubscriptionDataAdjusterQualifier(subscriptionClass = MoinstanceSubscription.class)
public class MoInstanceSubscriptionDataAdjuster extends StatisticalSubscriptionParentDataAdjuster<MoinstanceSubscription> {

    @Override
    public void updateImportedSubscriptionWithCorrectValues(final MoinstanceSubscription subscription) throws DataAccessException {
        super.updateImportedSubscriptionWithCorrectValues(subscription);
        filterInvalidMoInstances(subscription);
    }

    private void filterInvalidMoInstances(final MoinstanceSubscription subscription) {
        final List<MoinstanceInfo> subscriptionMoInstances = subscription.getMoInstances();
        final List<Node> nodes = subscription.getNodes();
        if (subscriptionMoInstances == null || nodes == null || nodes.isEmpty()) {
            return;
        }

        final List<String> nodeNames = new ArrayList<>();
        for (final Node node : nodes) {
            if (node != null) {
                nodeNames.add(node.getName());
            }
        }

        final Iterator<MoinstanceInfo> moInstancesIterator = subscriptionMoInstances.iterator();
        while (moInstancesIterator.hasNext()) {
            final MoinstanceInfo moInstance = moInstancesIterator.next();
            if (moInstance != null && !nodeNames.contains(moInstance.getNodeName())) {
                moInstancesIterator.remove();
            }
        }
    }
}
