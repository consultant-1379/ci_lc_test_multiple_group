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

package com.ericsson.oss.services.pm.collection.cache.utils;

import java.util.Map;

/**
 * Data type utility class for retrieving given type values from a map.
 */
public final class DataTypeUtil {

    private DataTypeUtil() {
        //utility class, should not be instantiated.
    }

    /**
     * Retrieves a particular values from the map as a long.
     *
     * @param key
     *         The key in the map
     * @param map
     *         The maps that contains the key.
     *
     * @return long
     */
    public static long getLongValue(final String key, final Map<String, Object> map) {
        final Object value = map.get(key);
        if (value instanceof Long) {
            return (Long) value;
        } else {
            return 0;
        }
    }

    /**
     * Retrieves a particular values from the map as a int.
     *
     * @param key
     *         The key in the map
     * @param map
     *         The maps that contains the key.
     *
     * @return int
     */
    public static int getIntValue(final String key, final Map<String, Object> map) {
        final Object value = map.get(key);
        if (value instanceof Integer) {
            return (Integer) value;
        } else {
            return 0;
        }
    }

    /**
     * Retrieves a particular values from the map as a String.
     *
     * @param key
     *         The key in the map
     * @param map
     *         The maps that contains the key.
     *
     * @return String
     */
    public static String getStringValue(final String key, final Map<String, Object> map) {
        final Object value = map.get(key);
        if (value != null) {
            return (String) value;
        } else {
            return null;
        }
    }

}
