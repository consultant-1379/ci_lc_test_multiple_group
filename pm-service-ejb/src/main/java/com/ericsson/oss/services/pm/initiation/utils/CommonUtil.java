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

package com.ericsson.oss.services.pm.initiation.utils;

import java.util.Collection;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ericsson.oss.pmic.dto.subscription.enums.RopPeriod;

/**
 * This class consists of static method that can be used across application. Any method which is not specific to a module is part of this common
 * utility class.
 */
public class CommonUtil {
    private static final Logger logger = LoggerFactory.getLogger(CommonUtil.class);

    private static final String COMMA = ",";
    private static final String COLUMN = ":";

    private CommonUtil() {}

    /**
     * Returns true if passed input string is null or has only spaces.
     *
     * @param str
     *         -input string to check.
     *
     * @return - returns true if input string is null or empty
     */
    public static boolean isStringNullOrEmpty(final String str) {
        return str == null || str.trim().isEmpty();
    }

    /**
     * Gets node name from Node Fdn.
     *
     * @param nodeFdn
     *         Node FDN
     *
     * @return Node Name
     */
    public static String getNodeNameFromFdn(final String nodeFdn) {
        return nodeFdn.substring(nodeFdn.indexOf('=') + 1);
    }

    /**
     * Check if the collection is not empty.
     *
     * @param collection
     *         Collection
     *
     * @return true if not empty otherwise false
     */
    public static boolean isNotEmptyCollection(final Collection<?> collection) {
        return collection != null && !collection.isEmpty();
    }

    /**
     * Check if the collection is empty.
     *
     * @param collection
     *         Collection.
     *
     * @return true if collection is empty, else false
     */
    public static boolean isNullOrEmptyCollection(final Collection<?> collection) {
        return !isNotEmptyCollection(collection);
    }

    /**
     * parse and update the file collection priority for the ROP.
     *
     *@param ropToPriorityMap
     *  - rop to priority Map
     * @param defaultPriority
     *  - Default priority value
     * @param fileCollectionPriorityForRop
     *  - file collection priority for Rop.
     * @param fileCollectionProperty
     *  - file collection.
     */
    public static void parseAndUpdateFileCollectionPriorityForRop(final Map<Integer, Integer> ropToPriorityMap, final Integer defaultPriority,
                                                                  final String fileCollectionPriorityForRop, final String fileCollectionProperty) {

        for (final RopPeriod ropPeriod : RopPeriod.values()) {
            ropToPriorityMap.put(ropPeriod.getDurationInSeconds(), defaultPriority);
        }
        if (StringUtils.isNotEmpty(fileCollectionPriorityForRop)) {
            final String[] params = fileCollectionPriorityForRop.split(COMMA);
            for (int idx = 0; idx < params.length; idx++) {
                final String[] ropData = params[idx].split(COLUMN);
                if (ropData.length > 1) {
                    try {
                        final Integer ropValue = RopPeriod.valueOf(ropData[0]).getDurationInSeconds();
                        ropToPriorityMap.put(ropValue, Integer.parseInt(ropData[1]));
                        logger.debug("ropToPrioritMap({}): {}", ropValue, ropToPriorityMap.get(ropValue));
                    } catch (final IllegalArgumentException e) {
                        logger.error("Invalid configuration for {} parameter: {}", fileCollectionProperty, e.getMessage());
                        logger.debug("Invalid configuration for {} parameter: ", fileCollectionProperty, e);
                    }
                } else {
                    logger.error("Invalid configuration for {} parameter: invalid formatting", fileCollectionProperty);
                }
            }
        } else {
            logger.error("Invalid configuration for {} parameter: empty string", fileCollectionProperty);
        }
    }

}
