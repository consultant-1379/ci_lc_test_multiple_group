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

package com.ericsson.oss.services.pm.bdd.collection.utils

import com.ericsson.oss.mediation.ftp.jca.api.error.FtpErrors
import com.ericsson.oss.pmic.dto.subscription.enums.SubscriptionType
import com.ericsson.oss.services.pm.collection.events.FileCollectionFailure
import com.ericsson.oss.services.pm.collection.events.FileCollectionResult
import com.ericsson.oss.services.pm.collection.events.FileCollectionSuccess


trait FileCollectionResultCreator {

    FileCollectionResult createFileCollectionResult(final long ropStartTime, final boolean recoverInNextRop,
                                                    final boolean hasSuccessFiles, final boolean hasFailedFiles,
                                                    final SubscriptionType subscriptionType, final FtpErrors error) {
        FileCollectionResult fileCollectionResult = new FileCollectionResult()
        fileCollectionResult.setRecoverInNextRop(recoverInNextRop)
        fileCollectionResult.setRopStartTime(ropStartTime)
        fileCollectionResult.setDestinationDirectory("/")
        fileCollectionResult.setSourceDirectory("target/tests/UETRACE/ERBS/")
        fileCollectionResult.setNodeAddress("NetworkElement=101010")
        fileCollectionResult.setNeType("ERBS")
        fileCollectionResult.setTaskStartTime(0L)
        fileCollectionResult.setTaskEndTime(0L)
        fileCollectionResult.setJobId("XXX")
        fileCollectionResult.setSubscriptionType(subscriptionType.name())
        fileCollectionResult.setRopPeriod(900L)

        final Map<String, String> destSrcFileNames = new HashMap<>()
        List<FileCollectionSuccess> fileCollectionSuccess = new ArrayList<>()
        List<FileCollectionFailure> fileCollectionFailure = new ArrayList<>()

        if (hasSuccessFiles) {
            final String successSourceFileName = "UNKNOWN/A20160311.1615+0000-1630+0000_MeContext=101010_3_UNKNOWN_uetracefile.bin.gz"
            final String successDestinationFileName = "target/tests/A20160311.1615-1630_uetrace_file_3.bin.gz"
            destSrcFileNames.put(successDestinationFileName, successSourceFileName)
            fileCollectionSuccess.add(new FileCollectionSuccess(successDestinationFileName, 10, 10))
        }
        if (hasFailedFiles) {
            final String failedSourceFileName = "UNKNOWN/A20160311.1615+0000-1630+0000_MeContext=101010_2_UNKNOWN_uetracefile.bin.gz"
            final String failedDestinationFileName = "target/tests/A20160311.1615-1630_uetrace_file_2.bin.gz"
            destSrcFileNames.put(failedSourceFileName, failedDestinationFileName)
            fileCollectionFailure.add(new FileCollectionFailure(failedDestinationFileName, error.getErrorMessage(), error.getErrorCode()))
        }

        fileCollectionResult.setFileCollectionSuccess(fileCollectionSuccess)
        fileCollectionResult.setFileCollectionFailure(fileCollectionFailure)
        fileCollectionResult.setDestAndSourceFileNames(destSrcFileNames)
        fileCollectionResult.setTaskStatusCode(error ? error.getErrorCode() : 0)
        return fileCollectionResult
    }
}
