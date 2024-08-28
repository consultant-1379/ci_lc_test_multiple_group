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

package com.ericsson.oss.services.pm.initiation.schedulers

import org.slf4j.Logger
import spock.lang.Unroll

import java.util.regex.Pattern

import com.ericsson.cds.cdi.support.rule.MockedImplementation
import com.ericsson.cds.cdi.support.rule.ObjectUnderTest
import com.ericsson.oss.pmic.dto.node.Node
import com.ericsson.oss.pmic.dto.scanner.Scanner
import com.ericsson.oss.pmic.dto.scanner.enums.ProcessType
import com.ericsson.oss.pmic.dto.subscription.CellTraceSubscription
import com.ericsson.oss.pmic.dto.subscription.cdts.CounterInfo
import com.ericsson.oss.pmic.dto.subscription.cdts.EventInfo
import com.ericsson.oss.pmic.dto.subscription.cdts.StreamInfo
import com.ericsson.oss.pmic.dto.subscription.enums.CellTraceCategory
import com.ericsson.oss.services.pm.exception.DataAccessException
import com.ericsson.oss.services.pm.generic.ScannerService
import com.ericsson.oss.services.pm.initiation.task.factories.auditor.CellTraceErrorNodeProcessor
import com.ericsson.oss.services.pm.initiation.task.factories.auditor.CellTraceSubscriptionHelper
import com.ericsson.oss.services.pm.initiation.task.factories.auditor.ResourceSubscriptionAuditorCriteria

class CellTraceAuditFunctionExceptionSpec extends AuditorParentSpec {

    @ObjectUnderTest
    CellTraceErrorNodeProcessor cellTraceErrorNodeProcessor

    @ObjectUnderTest
    CellTraceSubscriptionHelper cellTraceSubscriptionHelper

    @MockedImplementation
    ScannerService scannerService

    @MockedImplementation
    Logger logger

    def 'Should return true if DataAccessException thrown while trying to fetch scanners'() {
        given: 'a Cell Trace NRAN subscription and a node'
            def subscription = new CellTraceSubscription([] as List<CounterInfo>, 1000, false, false, [] as List<EventInfo>,
                                                         [] as List<StreamInfo>, CellTraceCategory.CELLTRACE_NRAN)
            def node = new Node()
            node.fdn = 'myFdn'
            def exception = new DataAccessException('a message')
            scannerService.findAllByNodeFdnAndProcessTypeInReadTx([node.fdn], ProcessType.NORMAL_PRIORITY_CELLTRACE, ProcessType.HIGH_PRIORITY_CELLTRACE) >> {throw exception}

        when: 'the error node proceessor is called'
            def value = cellTraceErrorNodeProcessor.shouldAddNodeToMissingScannerNodesBasedOnDpsScanners(node, [] as List<Pattern>, scannerService,
                    cellTraceSubscriptionHelper.isCellTraceNran(subscription.getCellTraceCategory()))

        then: 'the error node processor returns true and correct error is logged'
            value
            noExceptionThrown()
            1 * logger.error('Error {} accessing database to fecth scanners for node {}', exception.message, node.fdn, exception)


    }

    @Unroll
    def 'Should not throw exception if DataAccessException thrown while trying to un-assign CCTR scanner'() {
        given: 'a Cell Trace NRAN subscription and a CCTR scanner'
            def subscription = new CellTraceSubscription([] as List<CounterInfo>, 1000, false, false, [] as List<EventInfo>,
                                                         [] as List<StreamInfo>, CellTraceCategory.CELLTRACE_NRAN)
            subscription.setNodes([new Node()])
            def scanner = new Scanner()
            scanner.setProcessType(ProcessType.HIGH_PRIORITY_CELLTRACE)
            scanner.setName(name)
            def scannerList = [scanner]
            def dummyCriteria = new ResourceSubscriptionAuditorCriteria() {
            }
            def exception = new DataAccessException('a message')
            scannerService.saveOrUpdateWithRetry(_ as Scanner) >> {throw exception}

        when: 'helper is executed to get nodes with missing and duplicate scanners'
            cellTraceSubscriptionHelper.getNodesWithMissingAndDuplicateScanners(scannerList, dummyCriteria, subscription)

        then: 'no exception thrown'
            noExceptionThrown()
            1 * logger.info('Error while removing Cell Trace Subscription id from scanner {} : {}', scanner, exception.message)
            1 * logger.debug('Exception from update scanner', exception)

        where:
            name << ['PREDEF.10005.CELLTRACE', 'PREDEF.DU.10005.CELLTRACE', 'PREDEF.CUCP.10005.CELLTRACE', 'PREDEF.SOME_OTHER_EP.10005.CELLTRACE']
    }
}
