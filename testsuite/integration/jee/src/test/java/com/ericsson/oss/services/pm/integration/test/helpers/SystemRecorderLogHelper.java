/*------------------------------------------------------------------------------
 *******************************************************************************
 * COPYRIGHT Ericsson 2019
 *
 * The copyright to the computer program(s) herein is the property of
 * Ericsson Inc. The programs may be used and/or copied only with written
 * permission from Ericsson Inc. or in accordance with the terms and
 * conditions stipulated in the agreement/contract under which the
 * program(s) have been supplied.
 *******************************************************************************
 *----------------------------------------------------------------------------*/

package com.ericsson.oss.services.pm.integration.test.helpers;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintWriter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SystemRecorderLogHelper {
    private static final Logger logger = LoggerFactory.getLogger(SystemRecorderLogHelper.class);

    public static final String SYSTEM_RECORDER_TEST_LOG_PATH = "/ericsson/3pp/jboss/standalone/log/system_recorder_test.log";
    public static final String PRIVACY_ERROR_MESSAGE = "SystemRecorder API not allowed for privacy logging have been invoked on trace subscription";

    public void cleanup() {
        final File systemRecorderTestLog = new File(SYSTEM_RECORDER_TEST_LOG_PATH);
        try (final PrintWriter writer = new PrintWriter(systemRecorderTestLog)){
            writer.print("");
        } catch (final FileNotFoundException e) {
            logger.error("File {} not found", SYSTEM_RECORDER_TEST_LOG_PATH);
        }
    }

    private boolean isSystemRecorderTestLogEmpty() {
        final File systemRecorderTestLog = new File(SYSTEM_RECORDER_TEST_LOG_PATH);
        return true ? systemRecorderTestLog.length() == 0 : false;
    }

    public boolean isCompliantWithPrivacyLogging() {
        return isSystemRecorderTestLogEmpty();
    }
}
