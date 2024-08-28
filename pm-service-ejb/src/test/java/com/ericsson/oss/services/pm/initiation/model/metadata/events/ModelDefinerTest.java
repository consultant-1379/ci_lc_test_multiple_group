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
package com.ericsson.oss.services.pm.initiation.model.metadata.events;

import static org.testng.Assert.assertTrue;


import org.junit.Test;

import com.ericsson.oss.services.pm.initiation.model.utils.ModelDefiner;
import com.ericsson.oss.services.pm.initiation.model.utils.PmMetaDataConstants;

public class ModelDefinerTest {

    @Test
    public void testGetUrnPattern() {
        assertTrue(PmMetaDataConstants.NE_DEFINED_PATTERN.equals(ModelDefiner.getUrnPattern("NE")));
        assertTrue(PmMetaDataConstants.OSS_DEFINED_PATTERN.equals(ModelDefiner.getUrnPattern("OSS")));
        assertTrue("".equals(ModelDefiner.getUrnPattern("")));
        assertTrue("".equals(ModelDefiner.getUrnPattern(null)));
        assertTrue("INVALID".equals(ModelDefiner.getUrnPattern("BOGUS")));
    }
}
