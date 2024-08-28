/*------------------------------------------------------------------------------
 *******************************************************************************
 * COPYRIGHT Ericsson 2012
 *
 * The copyright to the computer program(s) herein is the property of
 * Ericsson Inc. The programs may be used and/or copied only with written
 * permission from Ericsson Inc. or in accordance with the terms and
 * conditions stipulated in the agreement/contract under which the
 * program(s) have been supplied.
 *******************************************************************************
 *----------------------------------------------------------------------------*/

package com.ericsson.oss.services.pm.common.utils;

import static org.testng.AssertJUnit.assertEquals;

import org.junit.Test;

public class KeyGeneratorTest {

    @Test
    public void testGenerateKey() {
        final String expectedValue = "Key1_Key2_Key3";
        final String actualValue = KeyGenerator.generateKey("Key1", "Key2", "Key3");
        assertEquals(expectedValue, actualValue);
    }
}
