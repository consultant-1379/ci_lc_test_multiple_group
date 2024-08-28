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
package com.ericsson.oss.services.pm.initiation.notification

import com.ericsson.oss.pmic.dao.versant.PmSubScannerDaoImpl
import com.ericsson.oss.services.pm.generic.ScannerServiceImpl

import static com.ericsson.oss.pmic.cdi.test.util.Constants.NETWORK_ELEMENT_1

import javax.ejb.TimerService
import javax.inject.Inject

import com.ericsson.cds.cdi.support.rule.ImplementationClasses
import com.ericsson.cds.cdi.support.rule.ImplementationInstance
import com.ericsson.cds.cdi.support.rule.MockedImplementation
import com.ericsson.cds.cdi.support.rule.ObjectUnderTest
import com.ericsson.oss.itpf.datalayer.dps.notification.event.AttributeChangeData
import com.ericsson.oss.itpf.datalayer.dps.notification.event.DpsAttributeChangedEvent
import com.ericsson.oss.pmic.api.modelservice.PmCapabilitiesLookupLocal
import com.ericsson.oss.pmic.dao.PmSubScannerDao
import com.ericsson.oss.pmic.cdi.test.util.SkeletonSpec
import com.ericsson.oss.pmic.dto.scanner.enums.ProcessType
import com.ericsson.oss.services.pm.generic.NodeServiceImpl
import com.ericsson.oss.services.pm.generic.PmSubScannerServiceImpl
import com.ericsson.oss.services.pm.initiation.schedulers.DelayedPmFunctionUpdateProcessor
import com.ericsson.oss.services.pm.scheduling.cluster.MembershipListener
import org.mockito.Mockito

/***
 * This class will test for the pmEnabled attribute change DPS notifications in PmFunction MO update.
 */
class EbsDelayedPmFunctionUpdateProcessorSpec extends SkeletonSpec {

    @ObjectUnderTest
    DelayedPmFunctionUpdateProcessor delayedPmFunctionUpdateProcessor

    @Inject
    DpsPmEnabledUpdateNotificationListener dpsPmEnabledUpdateNotificationListener

    @Inject
    PmSubScannerDao subScannerDao

    @MockedImplementation
    TimerService timerService

    @MockedImplementation
    PmCapabilitiesLookupLocal pmCapabilitiesLookupLocal

    @ImplementationInstance
    MembershipListener membershipListener = Mockito.mock(MembershipListener)

    @ImplementationClasses
    def classes = [PmSubScannerServiceImpl.class, NodeServiceImpl.class, ScannerServiceImpl.class, PmSubScannerDaoImpl.class]

    def setup() {
        timerService.getTimers() >> []
        pmCapabilitiesLookupLocal.getDefaultCapabilityValue(_, _) >> []
    }

    def 'when pmFunction disabled for node then any sub scanners object for EBS Streaming should be deleted'() {
        given: 'pmEnabled Attribute change event is created'
            def attributeChangedEvent = createAttributeChangeEvent(false, NETWORK_ELEMENT_1)
            Mockito.when(membershipListener.isMaster()).thenReturn(true)

        and: 'there are PMICSubScannerInfo object in dps for this network node'
            def node = dps.node().name('LTE01ERBS0001').build()
            def lteScanner = dps.scanner().nodeName(node.name).name('PREDEF.10004.CELLTRACE').processType(ProcessType.HIGH_PRIORITY_CELLTRACE).status('ACTIVE').build()
            dps.subScanner().subscriptionId(123L).fdn(lteScanner.fdn + ',PMICSubScannerInfo=SOME_NAME_1').build()
            dps.subScanner().subscriptionId(124L).fdn(lteScanner.fdn + ',PMICSubScannerInfo=SOME_NAME_2').build()
            def nrScanner = dps.scanner().nodeName(node.name).name('PREDEF.DU.10004.CELLTRACE').processType(ProcessType.HIGH_PRIORITY_CELLTRACE).status('ACTIVE').build()
            dps.subScanner().subscriptionId(125L).fdn(nrScanner.fdn + ',PMICSubScannrInfo=SOME_NAME_3').build()

        when: 'Update listener is called and pmFunction timeout occurs'
            dpsPmEnabledUpdateNotificationListener.onEvent(attributeChangedEvent)
            delayedPmFunctionUpdateProcessor.pmFunctionDataAndDelay.get(NETWORK_ELEMENT_1).delay =
                    delayedPmFunctionUpdateProcessor.timeGenerator.currentTimeMillis() - 1000L
            delayedPmFunctionUpdateProcessor.processPmFunctionData()

        then: 'all sub scanners for the node are deleted'
            subScannerDao.findAllByParentScannerFdn(lteScanner.fdn).size() == 0
            subScannerDao.findAllByParentScannerFdn(nrScanner.fdn).size() == 0
    }

    private static DpsAttributeChangedEvent createAttributeChangeEvent(final boolean newValue, final String nodeFdn) {
        final Collection<AttributeChangeData> attributeChangeData = [
            new AttributeChangeData('pmEnabled', !newValue, newValue, null, null)
        ]
        return new DpsAttributeChangedEvent(fdn: "$nodeFdn,PmFunction=1", changedAttributes: attributeChangeData)
    }

}
