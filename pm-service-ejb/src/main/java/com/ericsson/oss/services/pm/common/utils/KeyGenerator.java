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

package com.ericsson.oss.services.pm.common.utils;

/**
 * This class provides the functionality of generating key based on the given input.
 */
public final class KeyGenerator {

    private KeyGenerator() {
        //utility class should not be instantiated.
    }

    /**
     * This method will generate underscore appended key from the input string array. e.g generatorKeyFromStringArray[] =
     * "Java","is","platform","independant". ouput = "Java_is_platform_independant"
     *
     * @param generatorKeyFromStringArray
     *         - input string to generate key from
     *
     * @return - the generated key
     */
    public static String generateKey(final String... generatorKeyFromStringArray) {
        final StringBuilder key = new StringBuilder();
        final int lastElementIndex = generatorKeyFromStringArray.length - 1;
        for (int index = 0; index < lastElementIndex; index++) {
            key.append(generatorKeyFromStringArray[index]);
            key.append("_");
        }
        key.append(generatorKeyFromStringArray[lastElementIndex]);
        return key.toString();
    }
}
