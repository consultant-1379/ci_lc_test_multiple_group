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

package com.ericsson.oss.services.pm.integration.test.constants;

import java.io.File;

public interface TestConstants {

    String PMIC_SCANNER_INFO_ATTRIBUTE_ROP = "ropPeriod";
    String PMIC_SCANNER_INFO_ATTRIBUTE_SUB_POID = "subscriptionId";
    String PMIC_SCANNER_INFO_ATTRIBUTE_STATUS = "status";
    String PMIC_SCANNER_INFO_ATTRIBUTE_ID = "id";
    String PMIC_SCANNER_INFO_ATTRIBUTE_NAME = "name";
    String PMIC_SCANNER_INFO_ATTRIBUTE_PROCESS_TYPE = "processType";
    String PMIC_SCANNER_INFO_SUFFIX = ",PMICScannerInfo=";
    String PMIC_JOB_INFO_SUFFIX = ",PMICJobInfo=";
    String SCANNER_NAME_POSTFIX_CONT_BSC_RECORDINGS = ".Cont.Y.BSCRECORDINGS";

    String TEST_DIRECTORY = "src" + File.separator + "test" + File.separator + "resources" + File.separator + "files" + File.separator;

    String STATISTICAL_FILE_DIRECTORY = TEST_DIRECTORY + "XML" + File.separator;

    String CELLTRACE_FILE_DIRECTORY = TEST_DIRECTORY + "CELLTRACE" + File.separator;

    String CTUM_FILE_DIRECTORY = TEST_DIRECTORY + "CTUM" + File.separator;

    String UE_TRACE_FILE_DIRECTORY = TEST_DIRECTORY + "UETRACE" + File.separator;

    String EBM_FILE_DIRECTORY = TEST_DIRECTORY + "ebm" + File.separator + "data" + File.separator;

    String TEST_SYM_DIRECTORY = "src" + File.separator + "test" + File.separator + "resources" + File.separator + "symfiles" + File.separator;

    String STATISTICAL_FILE_SYM_DIRECTORY = TEST_SYM_DIRECTORY + "XML" + File.separator;

    String STATISTICAL_SYMLINK_DIRECTORY = TEST_SYM_DIRECTORY + "lterbs" + File.separator;

    String CELLTRACE_FILE_SYM_DIRECTORY = TEST_SYM_DIRECTORY + "CELLTRACE" + File.separator;

    String CELLTRACE_SYMLINK_DIRECTORY = TEST_SYM_DIRECTORY + "lteRbsCellTrace" + File.separator;

    String EBM_FILE_SYM_DIRECTORY = TEST_DIRECTORY + "ebm" + File.separator + "data" + File.separator;

    String EBM_SYMLINK_DIRECTORY = TEST_SYM_DIRECTORY + "sgeh" + File.separator;

}
