package com.ericsson.oss.services.pm.integration.test;

import javax.ejb.Stateless;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;
import javax.inject.Inject;

import com.ericsson.oss.services.pm.initiation.node.data.NodeCreationHelperBean;

@Stateless
@TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
public class SubscriptionHelper {

    @Inject
    private NodeCreationHelperBean nodeCreationHelperBean;

    public void deleteAllSubscription() {
        nodeCreationHelperBean.deleteAllSubscription();
    }
}
