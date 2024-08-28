/*------------------------------------------------------------------------------
 *******************************************************************************
 * COPYRIGHT Ericsson 2016
 *
 * The copyright to the computer program(s) herein is the property of
 * Ericsson Inc. The programs may be used and/or copied only with written
 * permission from Ericsson Inc. or in accordance with the terms and
 * conditions stipulated in the agreement/contract under which the
 * program(s) have been supplied.
 *******************************************************************************
 *----------------------------------------------------------------------------*/

package com.ericsson.oss.services.pm.initiation.cache.model.value;

import static org.testng.Assert.assertEquals;

import org.junit.Test;

public class ProcessTypeTest {
    @Test
    public void shouldReturnEVENTJOBprocessType() {
        assertEquals(ProcessType.getProcessType(ProcessType.EVENTJOB.name(), "PREDEF.EBMLOG.EBM"), ProcessType.EVENTJOB);
    }

    @Test
    public void shouldReturnNORMALPRIORITYCELLTRACEprocessType() {
        assertEquals(ProcessType.getProcessType(ProcessType.NORMAL_PRIORITY_CELLTRACE.name(), "PREDEF.10002.CELLTRACE"), ProcessType.CELLTRACE);
    }

    @Test
    public void shouldReturnHIGHPRIORITYCELLTRACEprocessType() {
        assertEquals(ProcessType.getProcessType(ProcessType.HIGH_PRIORITY_CELLTRACE.name(), "PREDEF.10005.CELLTRACE"), ProcessType.CELLTRACE);
    }

    @Test
    public void shouldReturnCTUMprocessType() {
        assertEquals(ProcessType.getProcessType(ProcessType.CTUM.name(), "USERDEF-CTUM-SGSN16AV301"), ProcessType.CTUM);
    }

    @Test
    public void shouldReturnPREDEFSTATSprocessType() {
        assertEquals(ProcessType.getProcessType(ProcessType.PREDEF_STATS.name(), "PREDEF.PREDEF_Rtn.STATS"), ProcessType.PREDEF_STATS);
    }

    @Test
    public void shouldReturnSTATSprocessType() {
        assertEquals(ProcessType.getProcessType(ProcessType.STATS.name(), "USERDEF.PREDEF_Rtn.STATS"), ProcessType.STATS);
    }

    @Test
    public void shouldReturnSTATSwithOnlyProcessType() {
        assertEquals(ProcessType.getProcessType(ProcessType.STATS.name()), ProcessType.STATS);
    }

    @Test
    public void shouldReturnPREDEFSTATSwithOnlyProcessType() {
        assertEquals(ProcessType.getProcessType(ProcessType.PREDEF_STATS.name()), ProcessType.PREDEF_STATS);
    }

    @Test
    public void shouldReturnCTUMwithOnlyProcessType() {
        assertEquals(ProcessType.getProcessType(ProcessType.CTUM.name()), ProcessType.CTUM);
    }

    @Test
    public void shouldReturnEVENTJOBwithOnlyProcessType() {
        assertEquals(ProcessType.getProcessType(ProcessType.EVENTJOB.name()), ProcessType.EVENTJOB);
    }

    @Test
    public void shouldReturnCELLTRACEwithOnlyProcessTypeForNORMALPRIORITYCELLTRACE() {
        assertEquals(ProcessType.getProcessType(ProcessType.NORMAL_PRIORITY_CELLTRACE.name()), ProcessType.CELLTRACE);
    }

    @Test
    public void shouldReturnCELLTRACEwithOnlyProcessTypeForHIGHPRIORITYCELLTRACE() {
        assertEquals(ProcessType.getProcessType(ProcessType.HIGH_PRIORITY_CELLTRACE.name()), ProcessType.CELLTRACE);
    }
}
