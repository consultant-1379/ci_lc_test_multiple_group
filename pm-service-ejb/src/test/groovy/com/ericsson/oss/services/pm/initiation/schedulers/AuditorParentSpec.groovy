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

package com.ericsson.oss.services.pm.initiation.schedulers

import static com.ericsson.oss.pmic.dto.scanner.enums.ProcessType.HIGH_PRIORITY_CELLTRACE

import com.ericsson.oss.itpf.datalayer.dps.persistence.ManagedObject
import com.ericsson.oss.pmic.dto.scanner.Scanner
import com.ericsson.oss.pmic.dto.scanner.enums.ProcessType
import com.ericsson.oss.pmic.dto.scanner.enums.ScannerStatus
import com.ericsson.oss.pmic.dto.subscription.Subscription
import com.ericsson.oss.services.pm.PmServiceEjbSkeletonSpec

class AuditorParentSpec extends PmServiceEjbSkeletonSpec {

    public static final String DUMMNY_OSS_MO = 'XXX'

    void buildScanner(final String nodeName, final String name, final ManagedObject subscription,
                      final boolean isActive, final ProcessType processType = HIGH_PRIORITY_CELLTRACE,
                      final Scanner.PmicScannerType scannerType = Scanner.PmicScannerType.PMICScannerInfo) {
        if (isActive) {
            def subId = processType == HIGH_PRIORITY_CELLTRACE ? Subscription.UNKNOWN_SUBSCRIPTION_ID : subscription.poId
            buildScanner(nodeName, name, ScannerStatus.ACTIVE, subId, processType, scannerType)
        } else {
            buildScanner(nodeName, name, ScannerStatus.INACTIVE, Subscription.UNKNOWN_SUBSCRIPTION_ID, processType, scannerType)
        }
    }

    void buildScanner(final String nodeName, final String scannerName, final ScannerStatus status,
                      final Long subscriptionId = Subscription.UNKNOWN_SUBSCRIPTION_ID,
                      final ProcessType processType = HIGH_PRIORITY_CELLTRACE,
                      final Scanner.PmicScannerType scannerType = Scanner.PmicScannerType.PMICScannerInfo) {
        dps.scanner()
           .scannerType(scannerType)
           .nodeName(nodeName)
           .name(scannerName)
           .processType(processType)
           .status(status)
           .subscriptionId(subscriptionId)
           .build()
    }

    ManagedObject buildNode(final String nodeName, final String neType, final List<String> technologyDomain = ['EPS'], final boolean pmEnabled = true) {
        buildNodeBuilder(nodeName, neType, technologyDomain, pmEnabled).build()
    }

    def buildNodeBuilder(final String nodeName, final String neType, final List<String> technologyDomain = ['EPS'], final boolean pmEnabled = true) {
        dps.node()
           .name(nodeName)
           .neType(neType)
           .ossModelIdentity(DUMMNY_OSS_MO)
           .technologyDomain(technologyDomain)
           .pmEnabled(pmEnabled)
    }
}
