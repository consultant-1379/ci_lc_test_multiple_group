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

package com.ericsson.oss.services.pm.initiation.utils;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.internal.util.reflection.Whitebox;
import org.slf4j.Logger;
import org.testng.Assert;

public class FdnUtilTest {

    private FdnUtil objectUnderTest;

    @Mock
    private Logger logger;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        objectUnderTest = new FdnUtil();
        Whitebox.setInternalState(objectUnderTest, "logger", logger);
    }

    @Test
    public void shouldReturnTheRootParentFromFdn() {
        final String result = objectUnderTest.getRootParentFdnFromChild("NetworkElement=Test,CppConnectivityInformation=1,AnotherChild=1");
        Assert.assertEquals(result, "NetworkElement=Test");
    }

    @Test
    public void shouldReturnTheImmediateParentFdnFromChildFdn() {
        final String result = objectUnderTest.getDirectParentFdnFromChild("NetworkElement=Test,CppConnectivityInformation=1,AnotherChild=1");
        Assert.assertEquals(result, "NetworkElement=Test,CppConnectivityInformation=1");
    }

    @Test
    public void shouldGetParentMoFdnSpecified() {
        final String complexChildFdn = "NetworkElement=Test,CppConnectivityInformation=1,Grandparent=1,Parent=1,Child=1";
        final String result = objectUnderTest.getParentFdnFromChild("CppConnectivityInformation", complexChildFdn);
        Assert.assertEquals(result, "NetworkElement=Test,CppConnectivityInformation=1");
    }

}
