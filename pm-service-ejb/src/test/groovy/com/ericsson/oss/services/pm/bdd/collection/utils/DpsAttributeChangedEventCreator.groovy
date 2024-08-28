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

package com.ericsson.oss.services.pm.bdd.collection.utils

import com.ericsson.oss.itpf.datalayer.dps.notification.event.AttributeChangeData
import com.ericsson.oss.itpf.datalayer.dps.notification.event.DpsAttributeChangedEvent

trait DpsAttributeChangedEventCreator {

    private static final String EVENT_NAMESPACE = "pmic_ctum_subscription"
    private static final String EVENT_BUCKET_NAME = "Live"
    private static final String EVENT_VERSION = "1.0.0"

    DpsAttributeChangedEvent createDpsAttributeChangedEvent(final String fdn, final String eventType, final Long poId, final String attrName, final Object oldValue, final Object newValue) {
        AttributeChangeData attributeChangeData = createAttributeChangeData(attrName, oldValue, newValue)
        return new DpsAttributeChangedEvent(EVENT_NAMESPACE, eventType, EVENT_VERSION, poId, fdn, EVENT_BUCKET_NAME, [attributeChangeData] as Set)
    }

    AttributeChangeData createAttributeChangeData(final String attrName, final String oldValue, final String newValue) {
        return new AttributeChangeData(attrName, oldValue, newValue, null, null)
    }
}
