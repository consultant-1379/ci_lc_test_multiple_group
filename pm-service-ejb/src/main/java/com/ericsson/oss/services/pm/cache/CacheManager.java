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

package com.ericsson.oss.services.pm.cache;

import javax.cache.Cache;

/**
 * <code>CacheManager</code> is generic wrapper for {@link Cache} common methods.
 * Sub classes should implement the {@link #getCache()} method.
 *
 * @param <K>
 *         the key parameter
 * @param <V>
 *         the value parameter
 */
public abstract class CacheManager<K, V> {

    /**
     * @param key
     *         - key value to check if it matches an entry in the cache
     *
     * @return - returns true if the key is in the cache
     */
    public boolean containsKey(final K key) {
        return getCache().containsKey(key);
    }

    /**
     * @param key
     *         - key value to get corresponding entry for
     *
     * @return - returns the corresponding entry for the given key
     */
    public V get(final K key) {
        return getCache().get(key);
    }

    /**
     * @param key
     *         - key value to enter into cache
     * @param value
     *         - entry value to enter into cache
     */
    public void put(final K key, final V value) {
        getCache().put(key, value);
    }

    /**
     * @return - returns the cache object
     */
    protected abstract Cache<K, V> getCache();
}
