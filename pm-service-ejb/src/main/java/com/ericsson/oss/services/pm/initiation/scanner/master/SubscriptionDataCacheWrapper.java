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

import javax.cache.Cache;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import com.ericsson.oss.itpf.sdk.cache.annotation.NamedCache;
import com.ericsson.oss.itpf.sdk.cache.data.ValueHolder;
import com.ericsson.oss.services.pm.cache.CacheManager;
import com.ericsson.oss.services.pm.initiation.cache.constants.CacheNamingConstants;
import com.ericsson.oss.services.pm.initiation.cache.model.SubscriptionDataCacheV2;

/**
 * <code>SubscriptionDataCacheWrapper</code> acts as a wrapper for cache {@link SubscriptionDataCacheV2}. This class is responsible for accessing cache
 * directly , see also {@link CacheManager}
 */
@ApplicationScoped
public class SubscriptionDataCacheWrapper extends CacheManager<String, ValueHolder> {

    @Inject
    @NamedCache(CacheNamingConstants.PMIC_SUBSCRIPTION_DATA_CACHE_V2)
    private Cache<String, ValueHolder> cache;

    @Override
    public Cache<String, ValueHolder> getCache() {
        return cache;
    }
}
