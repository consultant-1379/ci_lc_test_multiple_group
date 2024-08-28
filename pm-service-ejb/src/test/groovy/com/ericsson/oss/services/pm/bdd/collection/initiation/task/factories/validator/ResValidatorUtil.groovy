/*------------------------------------------------------------------------------
 *******************************************************************************
 * COPYRIGHT Ericsson 2018
 *
 * The copyright to the computer program(s) herein is the property of
 * Ericsson Inc. The programs may be used and/or copied only with written
 * permission from Ericsson Inc. or in accordance with the terms and
 * conditions stipulated in the agreement/contract under which the
 * program(s) have been supplied.
 *******************************************************************************
 *----------------------------------------------------------------------------*/

package com.ericsson.oss.services.pm.bdd.collection.initiation.task.factories.validator

import com.ericsson.cds.cdi.support.rule.MockedImplementation
import com.ericsson.oss.pmic.cdi.test.util.SkeletonSpec
import com.ericsson.oss.pmic.dto.scanner.enums.ProcessType
import com.ericsson.oss.pmic.dto.scanner.enums.ScannerStatus
import com.ericsson.oss.pmic.dto.subscription.enums.AdministrationState
import com.ericsson.oss.pmic.dto.subscription.enums.SubscriptionType
import com.ericsson.oss.pmic.dto.subscription.enums.TaskStatus
import com.ericsson.oss.services.pm.initiation.utils.PmFunctionConfig

class ResValidatorUtil extends SkeletonSpec {

    @MockedImplementation
    PmFunctionConfig pmFunctionConfig

    def SCANNER_NAME = 'USERDEF-Test_RES.Cont.Y.STATS'

    def createSubscription(state = AdministrationState.ACTIVE, status = TaskStatus.OK) {
        dps.subscription()
                .type(SubscriptionType.RES)
                .taskStatus(status)
                .administrationState(state)
                .name('testName')
                .build()
    }

    def createNodes(startAt, numNodes, subscriptionMo, pmFunction, associationEndpoint, controllingNode = null) {
        def nodes = []
        (startAt..numNodes).each {
            def attributes = controllingNode != null ? ['controllingRnc' : controllingNode.fdn] : [:]
            def node = dps.node()
                    .fdn("NetworkElement=node_${it}")
                    .neType('RNC')
                    .pmFunction(pmFunction)
                    .attributes(attributes)
                    .build()
            dpsUtils.addAssociation(subscriptionMo, associationEndpoint, node)
            nodes.add(node)
        }
        return nodes
    }

    def createScanners(nodes, subId) {
        def scanners = []
        nodes.each {
            def scanner = dps.scanner()
                    .nodeName(it)
                    .name(SCANNER_NAME)
                    .processType(ProcessType.STATS)
                    .status(ScannerStatus.ACTIVE)
                    .subscriptionId(subId)
                    .build()
            scanners.add(scanner)
        }
        return scanners
    }
}
