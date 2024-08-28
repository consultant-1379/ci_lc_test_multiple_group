/*
 * ------------------------------------------------------------------------------
 *  ********************************************************************************
 *  * COPYRIGHT Ericsson  2016
 *  *
 *  * The copyright to the computer program(s) herein is the property of
 *  * Ericsson Inc. The programs may be used and/or copied only with written
 *  * permission from Ericsson Inc. or in accordance with the terms and
 *  * conditions stipulated in the agreement/contract under which the
 *  * program(s) have been supplied.
 *  *******************************************************************************
 *  *----------------------------------------------------------------------------
 */
package com.ericsson.oss.services.pm.initiation.notification

import spock.lang.Unroll

import javax.ejb.TimerService
import javax.inject.Inject

import com.ericsson.cds.cdi.support.rule.ImplementationInstance
import com.ericsson.cds.cdi.support.rule.ObjectUnderTest
import com.ericsson.oss.itpf.datalayer.dps.notification.event.AttributeChangeData
import com.ericsson.oss.itpf.datalayer.dps.notification.event.DpsAttributeChangedEvent
import com.ericsson.oss.pmic.cdi.test.util.SkeletonSpec
import com.ericsson.oss.pmic.dao.ScannerDao
import com.ericsson.oss.pmic.dto.scanner.enums.ProcessType
import com.ericsson.oss.pmic.dto.scanner.enums.ScannerStatus
import com.ericsson.oss.pmic.dto.subscription.cdts.EventInfo
import com.ericsson.oss.pmic.dto.subscription.enums.AdministrationState
import com.ericsson.oss.pmic.dto.subscription.enums.RopPeriod
import com.ericsson.oss.pmic.dto.subscription.enums.SubscriptionType
import com.ericsson.oss.pmic.dto.subscription.enums.TaskStatus
import com.ericsson.oss.services.pm.collection.notification.DpsNotificationListenerForOneMinuteLicenseUpdate

class DpsNotificationListenerForOneMinuteLicenseUpdateSpec extends SkeletonSpec {

    private static final long PO_ID = 1l

    @ImplementationInstance
    protected TimerService timerService = Mock(TimerService)

    @ObjectUnderTest
    DpsNotificationListenerForOneMinuteLicenseUpdate dpsNotificationListenerForOneMinuteLicenseUpdate

    @Inject
    ScannerDao scannerDao

    def setup() {
        timerService.getTimers() >> []
    }

    @Unroll
    def "When feature state is updated to #newFeatureState, scanner ROP period should be updated to #expectedRop "() {
        given: "GPEH subscription,nodes and scanners exists in dps"
        def nodeMo = dps.node().name("RNC01").ossModelIdentity('16B-V.7.1659').neType('RNC').pmEnabled(true).build()
        def gpehSubscriptionMO = dps.subscription().
                type(SubscriptionType.GPEH).
                name("Test").
                administrationState(AdministrationState.ACTIVE).
                events(new EventInfo("a", "b")).
                rop(RopPeriod.FIFTEEN_MIN).
                taskStatus(TaskStatus.OK).
                build()
        dps.scanner().
                nodeName(nodeMo).
                name("PREDEF.30000.GPEH").
                processType(ProcessType.REGULAR_GPEH).
                subscriptionId(gpehSubscriptionMO).
                status(ScannerStatus.ACTIVE).
                ropPeriod(currentRop).
                build()
        and: "featureState Attribute Change Event is created"
        Collection<AttributeChangeData> attributeChangeData = [new AttributeChangeData("featureState", currentFeatureState, newFeatureState, null,
                null)]
        DpsAttributeChangedEvent attributeChangedEvent = new DpsAttributeChangedEvent(
                fdn: fdn,
                changedAttributes: attributeChangeData)
        attributeChangedEvent.setPoId(PO_ID)
        when: "Listener receives Attribute Change Event"
        dpsNotificationListenerForOneMinuteLicenseUpdate.onEvent(attributeChangedEvent)
        then: "active scanners for the node are updated with the expected rop period #expectedRop"
        def scanners = scannerDao.findAllByNodeFdnAndSubscriptionIdAndScannerStatus([nodeMo.getFdn()], null, ScannerStatus.ACTIVE)
        scanners.each {
            if (it.getStatus() == ScannerStatus.ACTIVE) {
                assert it.getRopPeriod() == expectedRop
            }
        }
        where:
        currentFeatureState << ['INACTIVE', 'ACTIVATED', 'INACTIVE', 'ACTIVATED', 'INACTIVE', 'ACTIVATED']
        newFeatureState << ['ACTIVATED', 'INACTIVE', 'ACTIVATED', 'INACTIVE', 'ACTIVATED', 'INACTIVE']
        currentRop << [900, 60, 900, 60, 900, 60]
        expectedRop << [60, 900, 60, 900, 60, 900]
        fdn << ["SubNetwork=RNC01,MeContext=RNC01,ManagedElement=1,SystemFunctions=1,Licensing=1,RncFeature=GpehCapIncrRedRopPer",
                "SubNetwork=RNC01,MeContext=RNC01,ManagedElement=1,SystemFunctions=1,Licensing=1,RncFeature=GpehCapIncrRedRopPer",
                "MeContext=RNC01,ManagedElement=1,SystemFunctions=1,Licensing=1,RncFeature=GpehCapIncrRedRopPer",
                "MeContext=RNC01,ManagedElement=1,SystemFunctions=1,Licensing=1,RncFeature=GpehCapIncrRedRopPer",
                "NetworkElement=RNC01,ManagedElement=1,SystemFunctions=1,Licensing=1,RncFeature=GpehCapIncrRedRopPer",
                "NetworkElement=RNC01,ManagedElement=1,SystemFunctions=1,Licensing=1,RncFeature=GpehCapIncrRedRopPer"]
    }
}
