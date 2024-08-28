/*------------------------------------------------------------------------------
 *******************************************************************************
 * COPYRIGHT Ericsson 2017
 *
 * The copyright to the computer program(s) herein is the property of
 * Ericsson Inc. The programs may be used and/or copied only with written
 * permission from Ericsson Inc. or in accordance with the terms and
 * conditions stipulated in the agreement/contract under which the
 * program(s) have been supplied.
 *******************************************************************************
 *----------------------------------------------------------------------------*/
package com.ericsson.oss.services.pm.initiation.common.accesscontrol;

import java.util.EnumMap;
import java.util.Map;

import com.ericsson.oss.pmic.dto.subscription.enums.SubscriptionType;

/**
 * Maps from @SubscriptionType to the resourceId defined in PMIC access policy
 */
public final class SubscriptionTypeToResourceIdMapper {

    private static final Map<SubscriptionType, String> resourceIdMap = new EnumMap<>(SubscriptionType.class);

    static {
        resourceIdMap.put(SubscriptionType.STATISTICAL, AccessControlResources.STATISTICAL);
        resourceIdMap.put(SubscriptionType.MOINSTANCE, AccessControlResources.STATISTICAL);
        resourceIdMap.put(SubscriptionType.CELLTRACE, AccessControlResources.CELLTRACE_EBS_L);
        resourceIdMap.put(SubscriptionType.CONTINUOUSCELLTRACE, AccessControlResources.CELLTRACE_EBS_L);
        resourceIdMap.put(SubscriptionType.UETRACE, AccessControlResources.UETRACE);
        resourceIdMap.put(SubscriptionType.EBM, AccessControlResources.EBM_EBS_M);
        resourceIdMap.put(SubscriptionType.CTUM, AccessControlResources.CTUM);
        resourceIdMap.put(SubscriptionType.UETR, AccessControlResources.UETR);
        resourceIdMap.put(SubscriptionType.CELLTRAFFIC, AccessControlResources.CTR);
        resourceIdMap.put(SubscriptionType.GPEH, AccessControlResources.GPEH);
        resourceIdMap.put(SubscriptionType.CELLRELATION, AccessControlResources.STATISTICAL);
        resourceIdMap.put(SubscriptionType.RES, AccessControlResources.RES);
        resourceIdMap.put(SubscriptionType.BSCRECORDINGS, AccessControlResources.BSCRECORDINGS);
        resourceIdMap.put(SubscriptionType.MTR, AccessControlResources.MTR);
        resourceIdMap.put(SubscriptionType.RPMO, AccessControlResources.BSCPERFORMANCEEVENTS);
        resourceIdMap.put(SubscriptionType.RTT, AccessControlResources.RTT);
    }

    private SubscriptionTypeToResourceIdMapper() {
        //utility class should not be instantiated.
    }

    /**
     * Get access control resourceID for given subscription type
     *
     * @param subscriptionType
     *         the subscriptionType
     *
     * @return the access control resourceID
     */
    public static String getResourceId(final SubscriptionType subscriptionType) {
        if (!resourceIdMap.containsKey(subscriptionType)) {
            throw new IllegalArgumentException("Subscription Type can't be recognized for role based authorization");
        }
        return resourceIdMap.get(subscriptionType);
    }

}
