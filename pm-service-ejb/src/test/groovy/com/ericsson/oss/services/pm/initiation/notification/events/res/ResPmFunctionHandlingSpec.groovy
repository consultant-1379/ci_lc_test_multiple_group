/*
 * ------------------------------------------------------------------------------
 *  ********************************************************************************
 *  * COPYRIGHT Ericsson  2018
 *  *
 *  * The copyright to the computer program(s) herein is the property of
 *  * Ericsson Inc. The programs may be used and/or copied only with written
 *  * permission from Ericsson Inc. or in accordance with the terms and
 *  * conditions stipulated in the agreement/contract under which the
 *  * program(s) have been supplied.
 *  *******************************************************************************
 *  *----------------------------------------------------------------------------
 */
package com.ericsson.oss.services.pm.initiation.notification.events.res

import static com.ericsson.oss.pmic.cdi.test.util.Constants.LIVE
import static com.ericsson.oss.pmic.cdi.test.util.constant.SubscriptionOperationConstant.*

import javax.inject.Inject

import com.ericsson.cds.cdi.support.configuration.InjectionProperties
import com.ericsson.cds.cdi.support.providers.custom.model.ModelPattern
import com.ericsson.cds.cdi.support.providers.custom.model.RealModelServiceProvider
import com.ericsson.cds.cdi.support.rule.ImplementationInstance
import com.ericsson.oss.itpf.datalayer.dps.notification.event.AttributeChangeData
import com.ericsson.oss.itpf.datalayer.dps.notification.event.DpsAttributeChangedEvent
import com.ericsson.oss.itpf.datalayer.dps.notification.event.DpsObjectDeletedEvent
import com.ericsson.oss.itpf.datalayer.dps.persistence.ManagedObject
import com.ericsson.oss.itpf.sdk.eventbus.model.EventSender
import com.ericsson.oss.itpf.sdk.eventbus.model.annotation.Modeled
import com.ericsson.oss.mediation.sdk.event.MediationTaskRequest
import com.ericsson.oss.pmic.cdi.test.util.SkeletonSpec
import com.ericsson.oss.pmic.dto.scanner.enums.ProcessType
import com.ericsson.oss.pmic.dto.scanner.enums.ScannerStatus
import com.ericsson.oss.pmic.dto.subscription.ResSubscription
import com.ericsson.oss.pmic.dto.subscription.enums.AdministrationState
import com.ericsson.oss.pmic.dto.subscription.enums.SubscriptionType
import com.ericsson.oss.pmic.dto.subscription.enums.TaskStatus
import com.ericsson.oss.pmic.util.TimeGenerator
import com.ericsson.oss.services.pm.cache.PmFunctionEnabledWrapper
import com.ericsson.oss.services.pm.collection.notification.DpsScannerDeleteNotificationListener
import com.ericsson.oss.services.pm.collection.notification.DpsScannerUpdateNotificationListener
import com.ericsson.oss.services.pm.generic.ScannerService
import com.ericsson.oss.services.pm.initiation.notification.DpsPmEnabledUpdateNotificationListener
import com.ericsson.oss.services.pm.initiation.notification.DpsPmEnabledUpdateNotificationProcessor
import com.ericsson.oss.services.pm.initiation.schedulers.DelayedPmFunctionUpdateProcessor
import com.ericsson.oss.services.pm.initiation.tasks.SubscriptionActivationTaskRequest
import com.ericsson.oss.services.pm.initiation.tasks.SubscriptionDeactivationTaskRequest
import com.ericsson.oss.services.pm.initiation.utils.PmFunctionUtil
import com.ericsson.oss.services.pm.scheduling.cluster.MembershipListener
import com.ericsson.oss.services.pm.scheduling.impl.DelayedTaskStatusValidator
import com.ericsson.oss.services.pm.services.generic.SubscriptionReadOperationService

class ResPmFunctionHandlingSpec extends SkeletonSpec {

    static def filteredModels = [new ModelPattern("oss_capability", "global", "RES_SubscriptionAttributes", ".*"),
                                 new ModelPattern("oss_capabilitysupport", "RNC", "PMICFunctions", ".*"),
                                 new ModelPattern("oss_capability", "global", "PMICFunctions", ".*")]

    static RealModelServiceProvider realModelServiceProvider = new RealModelServiceProvider(filteredModels)

    def autoAllocateFrom() {
        return ['com.ericsson.oss.services.pm.common.logging',
                'com.ericsson.oss.services.pm.initiation',
                'com.ericsson.oss.pmic.impl.modelservice',
                'com.ericsson.oss.services.pm',
                'com.ericsson.oss.pmic.dao',
                'com.ericsson.oss.pmic.dto']
    }

    @Override
    def addAdditionalInjectionProperties(InjectionProperties injectionProperties) {
        injectionProperties.addInjectionProvider(realModelServiceProvider)
        autoAllocateFrom().each { injectionProperties.autoLocateFrom(it) }
    }

    @Inject
    DpsScannerUpdateNotificationListener dpsScannerUpdateNotificationListener

    @Inject
    DpsScannerDeleteNotificationListener dpsScannerDeleteNotificationListener

    @Inject
    DpsPmEnabledUpdateNotificationListener dpsPmEnabledUpdateNotificationListener

    @Inject
    DelayedTaskStatusValidator delayedTaskStatusValidator

    @Inject
    PmFunctionEnabledWrapper pmFunctionCache

    @Inject
    ScannerService scannerService

    @Inject
    SubscriptionReadOperationService subscriptionReadOperationService

    @Inject
    private DpsPmEnabledUpdateNotificationProcessor dpsPmEnabledUpdateNotificationProcessor

    @ImplementationInstance
    TimeGenerator timeGenerator = Mock()

    @Inject
    @Modeled
    private EventSender<MediationTaskRequest> eventSender

    @ImplementationInstance
    MembershipListener membershipListener = Mock()

    @ImplementationInstance
    DelayedPmFunctionUpdateProcessor delayedUpdateProcessor = [
            scheduleDelayedPmFunctionUpdateProcessor: { a, b, c -> dpsPmEnabledUpdateNotificationProcessor.processPmFunctionChange(a, b, c) }
    ] as DelayedPmFunctionUpdateProcessor


    def rnc1NodeAttributes = [
            name             : "RNC01",
            networkElementId : "RNC01",
            fdn              : "NetworkElement=RNC01",
            platformType     : "CPP",
            neType           : "RNC",
            nodeModelIdentity: "16B-V.7.1659",
            ossModelIdentity : "16B-V.7.1659",
            ossPrefix        : "MeContext=RNC01",
            pmFunction       : true]

    def rnc2NodeAttributes = [
            name             : "RNC02",
            networkElementId : "RNC02",
            fdn              : "NetworkElement=RNC02",
            platformType     : "CPP",
            neType           : "RNC",
            nodeModelIdentity: "16B-V.7.1659",
            ossModelIdentity : "16B-V.7.1659",
            ossPrefix        : "MeContext=RNC02",
            pmFunction       : true]

    def rbs1NodeAttributes = [
            name             : "RBS1",
            fdn              : "NetworkElement=RBS1",
            platformType     : "CPP",
            neType           : "RBS",
            nodeModelIdentity: "16A-U.4.210",
            ossModelIdentity : "16A-U.4.210",
            controllingRnc   : "NetworkElement=RNC01",
            pmFunction       : true]

    def rbs2NodeAttributes = [
            name             : "RBS2",
            fdn              : "NetworkElement=RBS2",
            platformType     : "CPP",
            neType           : "RBS",
            nodeModelIdentity: "16A-U.4.210",
            ossModelIdentity : "16A-U.4.210",
            controllingRnc   : "NetworkElement=RNC01",
            pmFunction       : true]

    def rbs3NodeAttributes = [
            name             : "RBS3",
            fdn              : "NetworkElement=RBS3",
            platformType     : "CPP",
            neType           : "RBS",
            nodeModelIdentity: "16A-U.4.210",
            ossModelIdentity : "16A-U.4.210",
            controllingRnc   : "NetworkElement=RNC02",
            pmFunction       : false]

    ManagedObject RNC01
    ManagedObject RNC02
    ManagedObject RNC03

    ManagedObject nodeMO1, nodeMO2

    private static final RES_SUBSCRIPTION_NAME = "Test_RES"
    private static final SCANNER_NAME = "USERDEF-" + RES_SUBSCRIPTION_NAME + ".Cont.Y.STATS"
    private static final SCANNER_RDN = "PMICScannerInfo=" + SCANNER_NAME

    ManagedObject attachedNode1Mo
    ManagedObject attachedNode2Mo
    ManagedObject attachedNode3Mo

    def subscriptionId

    /*
     * Creates nodes RNC01, RNC02, RBS1, RBS2 with pmEnabled=true, RBS3 with pmEnabled=false. First four added to ACTIVE RES sub, creates scanners on dps.
     * RBS3 not included in sub no scanner in dps.
     * Populates PmFunction cache properly.
     */

    def setup() {
        membershipListener.isMaster() >> true
        buildNodes()
        pmFunctionCache.addEntry(rnc1NodeAttributes.fdn, true)
        pmFunctionCache.addEntry(rnc2NodeAttributes.fdn, true)
        pmFunctionCache.addEntry(rbs1NodeAttributes.fdn, true)
        pmFunctionCache.addEntry(rbs2NodeAttributes.fdn, true)
        pmFunctionCache.addEntry(rbs3NodeAttributes.fdn, false)

        subscriptionId = buildActiveResSubscription()
        String[] nodeNames = [rnc1NodeAttributes.name, rnc2NodeAttributes.name, rbs1NodeAttributes.name, rbs2NodeAttributes.name]
        buildScanners(nodeNames, subscriptionId)
    }

    def "when pm function goes OFF on an attached Node, node is removed from Subscription, scanner is deleted on dps, Subscription status is updated"() {
        given: "PmFunctionValue retrieval"
        PmFunctionUtil.PmFunctionPropertyValue pmFunctionPropValue = PmFunctionUtil.getPmFunctionConfig()
        when: "PMFunction goes OFF on attached node"
        attachedNode1Mo.getChild("PmFunction=1").setAttribute("pmEnabled", false)
        and: "notification is received, validator invoked"
        dpsPmEnabledUpdateNotificationListener.onEvent(createPmFunctionAttributeChangeEvent(true, false, attachedNode1Mo))
        simulateScannerAndNotification(attachedNode1Mo, pmFunctionPropValue)
        and: 'Call delay Task Validator'
        timeGenerator.currentTimeMillis() >> System.currentTimeMillis() + 10 * 60 * 1000
        delayedTaskStatusValidator.validateTaskStatusAdminState()
        then: "scanner is deleted on dps"
        assert scannerService.findAllByNodeFdn(Collections.singleton(rbs1NodeAttributes.fdn)).isEmpty()
        and: "node is removed from subscription"
        ResSubscription resSub = ((ResSubscription) subscriptionReadOperationService.findOneById(subscriptionId as java.lang.Long, true))
        Set<String> attachedNodes = resSub.getAttachedNodesFdn()
        !attachedNodes.contains(rbs1NodeAttributes.fdn)
        and: "subscription status is OK"
        resSub.getTaskStatus() == TaskStatus.OK
    }

    def "when pm function goes ON on a RBS/RadioNode node, node is included in subscription "() {
        when: "PMFunction goes ON on RBS node"
        attachedNode3Mo.getChild("PmFunction=1").setAttribute("pmEnabled", true)
        and: "PmFunctionUpdate listener is invoked"
        dpsPmEnabledUpdateNotificationListener.onEvent(createPmFunctionAttributeChangeEvent(false, true, attachedNode3Mo))
        then: "node is added to Subscription"
        ResSubscription resSub = ((ResSubscription) subscriptionReadOperationService.findOneById(subscriptionId as java.lang.Long, true))
        Set<String> attachedNodes = resSub.getAttachedNodesFdn()
        attachedNodes.size() == 3
        attachedNodes.contains(attachedNode3Mo.fdn)
        and: "Activation is sent to node"
        1 * eventSender.send({ activation -> activation.getNodeAddress() == rbs3NodeAttributes.fdn } as SubscriptionActivationTaskRequest)
    }

    def "when pm function goes OFF on a RNC node, attached nodes are removed from subscription "() {
        given: "PmFunctionValue retrieval"
        PmFunctionUtil.PmFunctionPropertyValue pmFunctionPropValue = PmFunctionUtil.getPmFunctionConfig()
        when: "PMFunction goes ON on RNC node"
        RNC01.getChild("PmFunction=1").setAttribute("pmEnabled", false)
        and: "notification is received, validator invoked"
        dpsPmEnabledUpdateNotificationListener.onEvent(createPmFunctionAttributeChangeEvent(true, false, RNC01))
        simulateScannerAndNotification(RNC01, pmFunctionPropValue)
        and: 'Call delay Task Validator'
        timeGenerator.currentTimeMillis() >> System.currentTimeMillis() + 10 * 60 * 1000
        delayedTaskStatusValidator.validateTaskStatusAdminState()
        then: "deactivation and deletion tasks are sent for attached nodes"
        if (PmFunctionUtil.PmFunctionPropertyValue.PM_FUNCTION_LEGACY == pmFunctionPropValue) {
            2 * eventSender.send(_ as SubscriptionDeactivationTaskRequest)
        }
        and: "attached nodes are removed from subscription"
        ResSubscription resSub = ((ResSubscription) subscriptionReadOperationService.findOneById(subscriptionId as java.lang.Long, true))
        resSub.getAttachedNodesFdn().size() == 0
        and: "RNC node is left or removed depending on PmFunctionProperty"
        if (PmFunctionUtil.PmFunctionPropertyValue.PM_FUNCTION_LEGACY == pmFunctionPropValue) {
            assert resSub.getNodesFdns().size() == 2
        } else {
            assert resSub.getNodesFdns().size() == 1
        }
    }

    def buildNodes() {
        attachedNode1Mo = dps.node().name(rbs1NodeAttributes.name).attributes(rbs1NodeAttributes).build()
        attachedNode2Mo = dps.node().name(rbs2NodeAttributes.name).attributes(rbs2NodeAttributes).build()
        attachedNode3Mo = dps.node().name(rbs3NodeAttributes.name).attributes(rbs3NodeAttributes).build()

        RNC01 = dps.node().name(rnc1NodeAttributes.name).attributes(rnc1NodeAttributes).build()
        RNC02 = dps.node().name(rnc2NodeAttributes.name).attributes(rnc2NodeAttributes).build()
    }

    def buildScanners(String[] nodes, long subscrId) {
        for (String node : nodes) {
            scannerUtil.builder(SCANNER_NAME, node)
                    .subscriptionId(subscrId)
                    .status(ScannerStatus.ACTIVE)
                    .processType(ProcessType.STATS)
                    .build()
        }
    }

    long buildActiveResSubscription() {
        def subAttributes = [resSpreadingFactor: ["SF_32"],
                             applyOnAllCells   : true]
        ManagedObject resSubscriptionMo = dps.subscription().type(SubscriptionType.RES).name(RES_SUBSCRIPTION_NAME).nodes(RNC01, RNC02).attributes(subAttributes).attachedNodes(attachedNode1Mo, attachedNode2Mo).administrationState(AdministrationState.ACTIVE).build()
        return resSubscriptionMo.getPoId()
    }

    def simulateScannerAndNotification(ManagedObject nodeMo, PmFunctionUtil.PmFunctionPropertyValue pmFunctionValue) {
        if (PmFunctionUtil.PmFunctionPropertyValue.PM_FUNCTION_LEGACY == pmFunctionValue) {
            nodeMo.getChild(SCANNER_RDN).setAttribute("status", ScannerStatus.UNKNOWN.name())
            dpsScannerUpdateNotificationListener.onEvent(createScannerAttributeChangeEvent("status", "ACTIVE", "UNKNOWN", nodeMo.getChild(SCANNER_RDN)))
        } else {
            ManagedObject scanner = nodeMo.getChild(SCANNER_RDN)
            DpsObjectDeletedEvent scannerDeleteEvent = getDeleteScannerObjectEvent(scanner)
            scannerService.deleteById(scanner.getPoId())
            dpsScannerDeleteNotificationListener.onEvent(scannerDeleteEvent)
        }
    }

    def DpsAttributeChangedEvent createScannerAttributeChangeEvent(String attribute, Object oldValue, Object newValue, ManagedObject scanner) {
        return new DpsAttributeChangedEvent(fdn: scanner.fdn, changedAttributes: [new AttributeChangeData(attribute, oldValue, newValue, null, null)])
    }

    def DpsAttributeChangedEvent createPmFunctionAttributeChangeEvent(Object oldValue, Object newValue, ManagedObject node) {
        return new DpsAttributeChangedEvent(fdn: node.getFdn() + ",PmFunction=1", changedAttributes: [new AttributeChangeData("pmEnabled", oldValue, newValue, null, null)])
    }

    def DpsObjectDeletedEvent getDeleteScannerObjectEvent(ManagedObject scanner) {
        String bucketName = LIVE
        boolean mibRoot = false
        Map<String, Object> scannerAttr = new HashMap<String, Object>()
        scannerAttr.put(SCANNER_ROP_PERIOD_ATTRIBUTE, scanner.getAttribute(SCANNER_ROP_PERIOD_ATTRIBUTE))
        scannerAttr.put(SCANNER_STATUS_ATTRIBUTE, scanner.getAttribute(SCANNER_STATUS_ATTRIBUTE))
        scannerAttr.put(SCANNER_SUBSCRIPTION_PO_ID_ATTRIBUTE, scanner.getAttribute(SCANNER_SUBSCRIPTION_PO_ID_ATTRIBUTE))
        scannerAttr.put(PROCESS_TYPE_ATTRIBUTE, scanner.getAttribute(PROCESS_TYPE_ATTRIBUTE))
        return new DpsObjectDeletedEvent(SCANNER_MODEL_NAME_SPACE, SCANNER_MODEL_NAME, SCANNER_MODEL_VERSION, scanner.getPoId(), scanner.getFdn(), bucketName, mibRoot, scannerAttr)
    }
}
