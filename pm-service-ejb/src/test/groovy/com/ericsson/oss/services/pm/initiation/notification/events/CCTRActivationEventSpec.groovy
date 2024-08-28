/*
 * ------------------------------------------------------------------------------
 *  ********************************************************************************
 *  * COPYRIGHT Ericsson  2017
 *  *
 *  * The copyright to the computer program(s) herein is the property of
 *  * Ericsson Inc. The programs may be used and/or copied only with written
 *  * permission from Ericsson Inc. or in accordance with the terms and
 *  * conditions stipulated in the agreement/contract under which the
 *  * program(s) have been supplied.
 *  *******************************************************************************
 *  *----------------------------------------------------------------------------
 */
package com.ericsson.oss.services.pm.initiation.notification.events

import com.ericsson.oss.pmic.dto.subscription.enums.CellTraceCategory

import static com.ericsson.oss.services.pm.initiation.util.constants.SubscriptionDPSConstant.PMIC_ASSOCIATION_SUBSCRIPTION_NODES

import javax.inject.Inject

import com.ericsson.cds.cdi.support.rule.ObjectUnderTest
import com.ericsson.oss.itpf.datalayer.dps.persistence.ManagedObject
import com.ericsson.oss.pmic.cdi.test.util.SkeletonSpec
import com.ericsson.oss.pmic.dao.ScannerDao
import com.ericsson.oss.pmic.dao.SubscriptionDao
import com.ericsson.oss.pmic.dto.scanner.enums.ProcessType
import com.ericsson.oss.pmic.dto.subscription.enums.SubscriptionType
import com.ericsson.oss.services.model.ned.pm.function.NeConfigurationManagerState

class CCTRActivationEventSpec extends SkeletonSpec {

    @ObjectUnderTest
    private ActivationEvent objectUnderTest

    @Inject
    SubscriptionDao subscriptionDao

    @Inject
    ScannerDao scannerDao

    private String CCTR_SUBSCRIPTION = 'ContinuousCellTraceSubscription'
    def defaultScannerAttributes = [status: 'ACTIVE', ropPeriod: 900, fileCollectionEnabled: true, errorCode: -1 as short] as Map<String, String>
    def cctrSubscriptionAttributes = [type: SubscriptionType.CONTINUOUSCELLTRACE.name(), name: CCTR_SUBSCRIPTION, events: events,
                                      cellTraceCategory: CellTraceCategory.CELLTRACE.name()]

    def "Verify PREDEF.10005.CELLTRACE scanner has CCTR subscriptionId attached before sending the task request"() {
        given: "One Node with PREDEF.10005.CELLTRACE scanner in DPS"
        ManagedObject subscriptionMO = dpsUtils.createMoInDPSWithAttributes(CCTR_SUBSCRIPTION + '=' + CCTR_SUBSCRIPTION,
                "pmic_continuous_cell_subscription", "1.1.0",
                CCTR_SUBSCRIPTION, cctrSubscriptionAttributes)
        ManagedObject erbsNode = nodeUtil.builder('LTE01ERBS0001').neConfigurationManagerState(NeConfigurationManagerState.DISABLED).build()
        dpsUtils.addAssociation(subscriptionMO, PMIC_ASSOCIATION_SUBSCRIPTION_NODES, erbsNode)
        scannerUtil.builder('PREDEF.10005.CELLTRACE', 'LTE01ERBS0001').attributes(defaultScannerAttributes).processType(ProcessType.HIGH_PRIORITY_CELLTRACE).subscriptionId(subscriptionMO.poId).errorCode(-1 as Short).build()

        when: "Activation request sent to mediation"
        objectUnderTest.execute(subscriptionMO.poId)

        then: "subscriptionId should be attached to available HIGH_PRIORITY_CELLTRACE scanner"
        !scannerDao.findAllByNodeFdnAndSubscriptionIdAndProcessType([erbsNode.fdn], [subscriptionMO.poId]).empty
    }

    def getEvents() {
        return [["groupName": "CCTR", "name": "INTERNAL_PER_UE_TRAFFIC_REP"],
                ["groupName": "CCTR", "name": "INTERNAL_PROC_RRC_CONN_SETUP"]]
    }
}
