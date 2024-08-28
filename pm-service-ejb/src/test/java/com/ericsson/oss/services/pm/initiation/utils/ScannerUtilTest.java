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

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import com.ericsson.oss.services.pm.initiation.cache.model.value.ProcessType;
import com.ericsson.oss.services.topologySearchService.exception.InvalidFdnException;

public class ScannerUtilTest {

    private ScannerUtil objectUnderTest;

    @Before
    public void setUp() {
        objectUnderTest = new ScannerUtil();
    }

    @Test
    public void extractNodeFdnFromScannerFdnShouldExtractNodeFdnFromScannerFdn() {

        // When
        final String scannerFdn = "NetworkElement=NODE1,PMICScannerInfo=1";
        final String nodeFdn = objectUnderTest.extractNodeFdnFromScannerFdn(scannerFdn);

        // Then
        Assert.assertEquals(nodeFdn, "NetworkElement=NODE1");
    }

    @Test(expected = InvalidFdnException.class)
    public void extractNodeFdnFromScannerFdnShouldThrowExceptionIfNodeFdnCannotBeExtracted() {

        // When
        final String scannerFdn = "PMICScannerInfo=1";
        objectUnderTest.extractNodeFdnFromScannerFdn(scannerFdn);
    }

    @Test
    public void getProcessSuperTypeShouldParseScannerTypeFromScannerName() {
        String result = objectUnderTest.getProcessSuperType("USERDEF-TestScanner.Cont.Y.STATS", "STATS");
        Assert.assertEquals(result, ProcessType.STATS.name());

        result = objectUnderTest.getProcessSuperType("PREDEF.STATS", "STATS");
        Assert.assertEquals(result, ProcessType.PREDEF_STATS.name());

        result = objectUnderTest.getProcessSuperType("anything", "STATS");
        Assert.assertEquals(result, ProcessType.STATS.name());

        result = objectUnderTest.getProcessSuperType("PREDEF.10001.CELLTRACE", "NORMAL_PRIORITY_CELLTRACE");
        Assert.assertEquals(result, ProcessType.NORMAL_PRIORITY_CELLTRACE.name());

        result = objectUnderTest.getProcessSuperType("PREDEF.54685428.CELLTRACE", "NORMAL_PRIORITY_CELLTRACE");
        Assert.assertEquals(result, ProcessType.NORMAL_PRIORITY_CELLTRACE.name());

        result = objectUnderTest.getProcessSuperType("PREDEF.54685428.CELLTRACE", "HIGH_PRIORITY_CELLTRACE");
        Assert.assertEquals(result, ProcessType.NORMAL_PRIORITY_CELLTRACE.name());
        result = objectUnderTest.getProcessSuperType("PREDEF.54685428.CELLTRACE", "anything");
        Assert.assertEquals(result, ProcessType.NORMAL_PRIORITY_CELLTRACE.name());

        result = objectUnderTest.getProcessSuperType("PREDEF.10004.CELLTRACE", "HIGH_PRIORITY_CELLTRACE");
        Assert.assertEquals(result, ProcessType.HIGH_PRIORITY_CELLTRACE.name());

        result = objectUnderTest.getProcessSuperType("PREDEF.10001.CELLTRACE", "anything");
        Assert.assertEquals(result, ProcessType.NORMAL_PRIORITY_CELLTRACE.name());

        result = objectUnderTest.getProcessSuperType("PREDEF.10004.CELLTRACE", "anything");
        Assert.assertEquals(result, ProcessType.HIGH_PRIORITY_CELLTRACE.name());

        result = objectUnderTest.getProcessSuperType("PREDEF.UETR", "anything");
        Assert.assertEquals(result, ProcessType.OTHER.name());

        result = objectUnderTest.getProcessSuperType(null, "anything");
        Assert.assertEquals(result, ProcessType.OTHER.name());

        result = objectUnderTest.getProcessSuperType("PREDEF.UETR", null);
        Assert.assertEquals(result, ProcessType.OTHER.name());

        result = objectUnderTest.getProcessSuperType("PREDEF.STATS", "anything");
        Assert.assertEquals(result, ProcessType.OTHER.name());

        result = objectUnderTest.getProcessSuperType("anything", "CTUM");
        Assert.assertEquals(result, ProcessType.CTUM.name());
    }

    @Test
    public void buildFdnStatsShouldReturnScannerMoFdn() {
        // When
        final String subscriptionName = "subscriptionName";
        final String nodeFdn = "nodeFdn";
        final String result = objectUnderTest.buildFdnStats(subscriptionName, nodeFdn);
        // Then
        Assert.assertEquals(result, "nodeFdn,PMICScannerInfo=USERDEF-subscriptionName.Cont.Y.STATS");
    }
}
