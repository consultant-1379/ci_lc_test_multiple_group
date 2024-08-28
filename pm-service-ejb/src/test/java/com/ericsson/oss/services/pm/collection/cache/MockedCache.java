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

package com.ericsson.oss.services.pm.collection.cache;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.cache.Cache;
import javax.cache.CacheManager;
import javax.cache.configuration.CacheEntryListenerConfiguration;
import javax.cache.configuration.Configuration;
import javax.cache.integration.CompletionListener;
import javax.cache.processor.EntryProcessor;
import javax.cache.processor.EntryProcessorException;
import javax.cache.processor.EntryProcessorResult;

public class MockedCache<K, V> implements Cache<K, V> {

    private final Map<K, V> cacheContents;

    public MockedCache() {
        this.cacheContents = new HashMap<>();
    }

    /*
     * (non-Javadoc)
     * @see javax.cache.Cache#get(java.lang.Object)
     */
    @Override
    public V get(final K key) {
        return cacheContents.get(key);
    }

    /*
     * (non-Javadoc)
     * @see javax.cache.Cache#getAll(java.util.Set)
     */
    @Override
    public Map<K, V> getAll(final Set<? extends K> keys) {
        return cacheContents;
    }

    public int getSize() {
        return cacheContents.size();
    }

    /*
     * (non-Javadoc)
     * @see javax.cache.Cache#containsKey(java.lang.Object)
     */
    @Override
    public boolean containsKey(final K key) {
        return cacheContents.containsKey(key);
    }

    /*
     * (non-Javadoc)
     * @see javax.cache.Cache#loadAll(java.util.Set, boolean, javax.cache.integration.CompletionListener)
     */
    @Override
    public void loadAll(final Set<? extends K> keys, final boolean replaceExistingValues, final CompletionListener completionListener) {
        //no implementation needed
    }

    /*
     * (non-Javadoc)
     * @see javax.cache.Cache#put(java.lang.Object, java.lang.Object)
     */
    @Override
    public void put(final K key, final V value) {
        cacheContents.put(key, value);
    }

    /*
     * (non-Javadoc)
     * @see javax.cache.Cache#getAndPut(java.lang.Object, java.lang.Object)
     */
    @Override
    public V getAndPut(final K key, final V value) {
        return null;
    }

    /*
     * (non-Javadoc)
     * @see javax.cache.Cache#putAll(java.util.Map)
     */
    @Override
    public void putAll(final Map<? extends K, ? extends V> map) {
        //no implementation needed
    }

    /*
     * (non-Javadoc)
     * @see javax.cache.Cache#putIfAbsent(java.lang.Object, java.lang.Object)
     */
    @Override
    public boolean putIfAbsent(final K key, final V value) {
        return false;
    }

    /*
     * (non-Javadoc)
     * @see javax.cache.Cache#remove(java.lang.Object)
     */
    @Override
    public boolean remove(final K key) {
        return cacheContents.remove(key) != null;
    }

    /*
     * (non-Javadoc)
     * @see javax.cache.Cache#remove(java.lang.Object, java.lang.Object)
     */
    @Override
    public boolean remove(final K key, final V oldValue) {
        return false;
    }

    /*
     * (non-Javadoc)
     * @see javax.cache.Cache#getAndRemove(java.lang.Object)
     */
    @Override
    public V getAndRemove(final K key) {
        return null;
    }

    /*
     * (non-Javadoc)
     * @see javax.cache.Cache#replace(java.lang.Object, java.lang.Object, java.lang.Object)
     */
    @Override
    public boolean replace(final K key, final V oldValue, final V newValue) {
        return false;
    }

    /*
     * (non-Javadoc)
     * @see javax.cache.Cache#replace(java.lang.Object, java.lang.Object)
     */
    @Override
    public boolean replace(final K key, final V value) {
        if (containsKey(key)) {
            cacheContents.put(key, value);
            return true;
        }
        return false;
    }

    /*
     * (non-Javadoc)
     * @see javax.cache.Cache#getAndReplace(java.lang.Object, java.lang.Object)
     */
    @Override
    public V getAndReplace(final K key, final V value) {
        return null;
    }

    /*
     * (non-Javadoc)
     * @see javax.cache.Cache#removeAll(java.util.Set)
     */
    @Override
    public void removeAll(final Set<? extends K> keys) {
        //no implementation needed
    }

    /*
     * (non-Javadoc)
     * @see javax.cache.Cache#removeAll()
     */
    @Override
    public void removeAll() {
        //no implementation needed
    }

    /*
     * (non-Javadoc)
     * @see javax.cache.Cache#clear()
     */
    @Override
    public void clear() {
        //no implementation needed
    }

    /*
     * (non-Javadoc)
     * @see javax.cache.Cache#getConfiguration(java.lang.Class)
     */
    @Override
    public <C extends Configuration<K, V>> C getConfiguration(final Class<C> clazz) {
        return null;
    }

    /*
     * (non-Javadoc)
     * @see javax.cache.Cache#invoke(java.lang.Object, javax.cache.processor.EntryProcessor, java.lang.Object[])
     */
    @Override
    public <T> T invoke(final K key, final EntryProcessor<K, V, T> entryProcessor, final Object... arguments) throws EntryProcessorException {
        return null;
    }

    /*
     * (non-Javadoc)
     * @see javax.cache.Cache#invokeAll(java.util.Set, javax.cache.processor.EntryProcessor, java.lang.Object[])
     */
    @Override
    public <T> Map<K, EntryProcessorResult<T>> invokeAll(final Set<? extends K> keys, final EntryProcessor<K, V, T> entryProcessor,
                                                         final Object... arguments) {
        return null;
    }

    /*
     * (non-Javadoc)
     * @see javax.cache.Cache#getName()
     */
    @Override
    public String getName() {
        return null;
    }

    /*
     * (non-Javadoc)
     * @see javax.cache.Cache#getCacheManager()
     */
    @Override
    public CacheManager getCacheManager() {
        return null;
    }

    /*
     * (non-Javadoc)
     * @see javax.cache.Cache#close()
     */
    @Override
    public void close() {
        //no implementation needed
    }

    /*
     * (non-Javadoc)
     * @see javax.cache.Cache#isClosed()
     */
    @Override
    public boolean isClosed() {
        return false;
    }

    /*
     * (non-Javadoc)
     * @see javax.cache.Cache#unwrap(java.lang.Class)
     */
    @Override
    public <T> T unwrap(final Class<T> clazz) {
        return null;
    }

    /*
     * (non-Javadoc)
     * @see javax.cache.Cache#registerCacheEntryListener(javax.cache.configuration.CacheEntryListenerConfiguration)
     */
    @Override
    public void registerCacheEntryListener(final CacheEntryListenerConfiguration<K, V> cacheEntryListenerConfiguration) {
        //no implementation needed.
    }

    /*
     * (non-Javadoc)
     * @see javax.cache.Cache#deregisterCacheEntryListener(javax.cache.configuration.CacheEntryListenerConfiguration)
     */
    @Override
    public void deregisterCacheEntryListener(final CacheEntryListenerConfiguration<K, V> cacheEntryListenerConfiguration) {
        //no implementation needed.
    }

    /*
     * (non-Javadoc)
     * @see javax.cache.Cache#iterator()
     */
    @Override
    public Iterator<javax.cache.Cache.Entry<K, V>> iterator() {
        final List<javax.cache.Cache.Entry<K, V>> entries = new ArrayList<>(cacheContents.values().size());
        for (final Map.Entry<K, V> entry : cacheContents.entrySet()) {
            entries.add(new Entry<K, V>() {
                @Override
                public K getKey() {
                    return entry.getKey();
                }

                @Override
                public V getValue() {
                    return entry.getValue();
                }

                @Override
                public <T> T unwrap(final Class<T> clazz) {
                    return null;
                }
            });
        }
        return entries.iterator();
    }

}
