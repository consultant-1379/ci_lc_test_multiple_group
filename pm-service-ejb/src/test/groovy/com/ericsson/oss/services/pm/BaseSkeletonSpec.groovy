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

package com.ericsson.oss.services.pm

import org.mockito.Mockito

import javax.enterprise.context.spi.CreationalContext
import javax.enterprise.inject.spi.BeanManager
import javax.inject.Inject

import com.ericsson.cds.cdi.support.configuration.InjectionProperties
import com.ericsson.cds.cdi.support.rule.ImplementationClasses
import com.ericsson.cds.cdi.support.rule.ImplementationInstance
import com.ericsson.cds.cdi.support.spock.CdiSpecification
import com.ericsson.oss.itpf.datalayer.dps.stub.RuntimeConfigurableDps
import com.ericsson.oss.pmic.cdi.test.util.CommonBaseSpec
import com.ericsson.oss.pmic.cdi.test.util.builder.DpsObjectBuilder
import com.ericsson.oss.pmic.cdi.test.util.builder.TestDpsUtils
import com.ericsson.oss.pmic.cdi.test.util.builder.node.TestNetworkElementDpsUtils
import com.ericsson.oss.pmic.cdi.test.util.builder.scanner.TestPmJobBuilder
import com.ericsson.oss.pmic.cdi.test.util.builder.scanner.TestScannerDpsUtils
import com.ericsson.oss.pmic.cdi.test.util.builder.subscription.*
import com.ericsson.oss.pmic.dao.*
import com.ericsson.oss.pmic.subscription.capability.SubscriptionCapabilityReaderImpl

abstract class BaseSkeletonSpec extends CdiSpecification implements CommonBaseSpec {

    static abstract filteredModels

    RuntimeConfigurableDps configurableDps = cdiInjectorRule.getService(RuntimeConfigurableDps);

    @Deprecated
    TestDpsUtils dpsUtils = new TestDpsUtils(configurableDps)

    @Deprecated
    // Use DpsObjectBuilder
    TestScannerDpsUtils scannerUtil = new TestScannerDpsUtils(configurableDps)
    @Deprecated
    // Use DpsObjectBuilder
    TestNetworkElementDpsUtils nodeUtil = new TestNetworkElementDpsUtils(configurableDps)
    @Deprecated
    // Use DpsObjectBuilder
    TestPmJobBuilder pmJobBuilder = new TestPmJobBuilder(configurableDps)
    @Deprecated
    // Use DpsObjectBuilder
    StatisticalSubscriptionBuilder statisticalSubscriptionBuilder = new StatisticalSubscriptionBuilder(dpsUtils)
    @Deprecated
    // Use DpsObjectBuilder
    CellTraceSubscriptionBuilder cellTraceSubscriptionBuilder = new CellTraceSubscriptionBuilder(dpsUtils)
    @Deprecated
    // Use DpsObjectBuilder
    CctrSubscriptionBuilder cctrSubscriptionBuilder = new CctrSubscriptionBuilder(dpsUtils)
    @Deprecated
    // Use DpsObjectBuilder
    EbmSubscriptionBuilder ebmSubscriptionBuilder = new EbmSubscriptionBuilder(dpsUtils)
    @Deprecated
    // Use DpsObjectBuilder
    CtumSubscriptionBuilder ctumSubscriptionBuilder = new CtumSubscriptionBuilder(dpsUtils)
    @Deprecated
    // Use DpsObjectBuilder
    UeTraceSubscriptionBuilder ueTraceSubscriptionBuilder = new UeTraceSubscriptionBuilder(dpsUtils)
    @Deprecated
    // Use DpsObjectBuilder
    UetrSubscriptionBuilder uetrSubscriptionBuilder = new UetrSubscriptionBuilder(dpsUtils)
    @Deprecated
    // Use DpsObjectBuilder
    CellTrafficSubscriptionBuilder cellTrafficSubscriptionBuilder = new CellTrafficSubscriptionBuilder(dpsUtils)
    @Deprecated
    // Use DpsObjectBuilder
    GpehSubscriptionBuilder gpehSubscriptionBuilder = new GpehSubscriptionBuilder(dpsUtils)
    @Deprecated
    // Use DpsObjectBuilder
    MoinstanceSubscriptionBuilder moinstanceSubscriptionBuilder = new MoinstanceSubscriptionBuilder(dpsUtils)

    final DpsObjectBuilder dps = new DpsObjectBuilder(configurableDps)

    def autoAllocateFrom() {
        return ['com.ericsson.oss.services.pm',
                'com.ericsson.oss.pmic.api.system.logging',
                'com.ericsson.oss.pmic.api.scanner.master',
                'com.ericsson.oss.pmic.dao',
                'com.ericsson.oss.pmic.dto']
    }

    abstract getRealModelServiceProvider()

    @Override
    def addAdditionalInjectionProperties(InjectionProperties injectionProperties) {
        injectionProperties.addInjectionProvider(getRealModelServiceProvider())
        autoAllocateFrom().each { injectionProperties.autoLocateFrom(it) }
    }

    def liveBucket() {
        if (liveDataBucket == null) {
            liveDataBucket = configurableDps.build().getLiveBucket();
        }
        return liveDataBucket
    }

    @ImplementationInstance
    BeanManager beanManager = Mockito.mock(BeanManager)

    @ImplementationInstance
    CreationalContext creationalContext = Mockito.mock(CreationalContext)

    @ImplementationClasses
    def implementationClasses = [SubscriptionCapabilityReaderImpl.class]

    @Inject
    NodeDao nodeDao
    @Inject
    ScannerDao scannerDao
    @Inject
    SubscriptionDao subscriptionDao
    @Inject
    PmJobDao pmJobDao
    @Inject
    PmSubScannerDao pmSubScannerDao
    @Inject
    SystemPropertiesDao systemPropertiesDao

    def setup() {

        def subscriptionHierarchy = configurableDps.defineClassHierarchy('pmic_subscription', 'Subscription')
        def resourceHierarchy = subscriptionHierarchy.withSubTypeInSameNamespace('ResourceSubscription')
        resourceHierarchy.withSubType('pmic_mtr_subscription', 'MtrSubscription')
        resourceHierarchy.withSubType('pmic_cellrelation_subscription', 'CellRelationSubscription')
        def statsHierarchy = resourceHierarchy.withSubType('pmic_stat_subscription', 'StatisticalSubscription')
        statsHierarchy.withSubType('pmic_moinstance_subscription', 'MoinstanceSubscription')
        statsHierarchy.withSubType('pmic_res_subscription', 'ResSubscription')
        def eventHierarchy = resourceHierarchy.withSubType('pmic_event_subscription', 'EventSubscription')
        eventHierarchy.withSubType('pmic_uetr_subscription', 'UetrSubscription')
        eventHierarchy.withSubType('pmic_ebm_subscription', 'EbmSubscription')
        eventHierarchy.withSubType('pmic_celltraffic_subscription', 'CellTrafficSubscription')
        eventHierarchy.withSubType('pmic_gpeh_subscription', 'GpehSubscription')
        def cellTraceHierarchy = eventHierarchy.withSubType('pmic_cell_subscription', 'CellTraceSubscription')
        cellTraceHierarchy.withSubType('pmic_continuous_cell_subscription', 'ContinuousCellTraceSubscription')
        subscriptionHierarchy.withSubType('pmic_ue_subscription', 'UETraceSubscription')
        subscriptionHierarchy.withSubType('pmic_ctum_subscription', 'CtumSubscription')

        def scannerHierarchy = configurableDps.defineClassHierarchy('pmic_subscription', 'PMICScannerInfo')
        scannerHierarchy.withSubType('pmic_subscription', 'PMICUeScannerInfo')

        def beans = [null]
        Mockito.when(beanManager.getBeans(NodeDao.class)).thenReturn(beans as Set)
        Mockito.when(beanManager.getBeans(ScannerDao.class)).thenReturn(beans as Set)
        Mockito.when(beanManager.getBeans(SubscriptionDao.class)).thenReturn(beans as Set)
        Mockito.when(beanManager.getBeans(PmJobDao.class)).thenReturn(beans as Set)
        Mockito.when(beanManager.getBeans(PmSubScannerDao.class)).thenReturn(beans as Set)
        Mockito.when(beanManager.getBeans(SystemPropertiesDao.class)).thenReturn(beans as Set)
        Mockito.when(beanManager.createCreationalContext(null)).thenReturn(creationalContext)
        Mockito.when(beanManager.getReference(null, NodeDao.class, creationalContext)).thenReturn(nodeDao)
        Mockito.when(beanManager.getReference(null, ScannerDao.class, creationalContext)).thenReturn(scannerDao)
        Mockito.when(beanManager.getReference(null, SubscriptionDao.class, creationalContext)).thenReturn(subscriptionDao)
        Mockito.when(beanManager.getReference(null, PmJobDao.class, creationalContext)).thenReturn(pmJobDao)
        Mockito.when(beanManager.getReference(null, PmSubScannerDao.class, creationalContext)).thenReturn(pmSubScannerDao)
        Mockito.when(beanManager.getReference(null, SystemPropertiesDao.class, creationalContext)).thenReturn(systemPropertiesDao)
    }
}

