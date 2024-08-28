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

import static com.ericsson.oss.pmic.cdi.test.util.Constants.NETWORK_ELEMENT_1
import static com.ericsson.oss.pmic.cdi.test.util.Constants.NETWORK_ELEMENT_2
import static com.ericsson.oss.pmic.cdi.test.util.Constants.NETWORK_ELEMENT_3

import org.mockito.Mockito

import javax.ejb.TimerService
import javax.inject.Inject

import com.ericsson.cds.cdi.support.rule.ImplementationInstance
import com.ericsson.cds.cdi.support.rule.ObjectUnderTest
import com.ericsson.cds.cdi.support.rule.MockedImplementation
import com.ericsson.oss.pmic.cdi.test.util.SkeletonSpec
import com.ericsson.oss.itpf.datalayer.dps.notification.event.AttributeChangeData
import com.ericsson.oss.itpf.datalayer.dps.notification.event.DpsAttributeChangedEvent
import com.ericsson.oss.services.model.ned.pm.function.FileCollectionState
import com.ericsson.oss.services.pm.common.utils.PmFunctionConstants
import com.ericsson.oss.services.pm.initiation.notification.handlers.ResPmFunctionHelper
import com.ericsson.oss.services.pm.initiation.schedulers.DelayedPmFunctionUpdateProcessor
import com.ericsson.oss.services.pm.initiation.schedulers.DelayedPmFunctionUpdateProcessor.ScheduleData
import com.ericsson.oss.services.pm.scheduling.cluster.MembershipListener

/***
 * This class will test for the pmEnabled attribute change DPS notifications in PmFunction MO update.
 */
class DelayedPmFunctionUpdateProcessorSpec extends SkeletonSpec {

    @ObjectUnderTest
    DelayedPmFunctionUpdateProcessor delayedPmFunctionUpdateProcessor

    @Inject
    DpsPmEnabledUpdateNotificationListener dpsPmEnabledUpdateNotificationListener

    @Inject
    DpsPmFunctionCreatedNotificationListener dpsPmFunctionCreatedNotificationListener

    @Inject
    DpsPmFunctionDeleteNotificationListener dpsPmFunctionDeleteNotificationListener

    @ImplementationInstance
    DpsPmEnabledUpdateNotificationProcessor processor = [
        processPmFunctionChange: { a, b, c -> if (a == NETWORK_ELEMENT_2) throw new IllegalArgumentException() }
    ] as DpsPmEnabledUpdateNotificationProcessor

    @MockedImplementation
    TimerService timerService

    @MockedImplementation
    ResPmFunctionHelper resPmFunctionHelper

    @ImplementationInstance
    MembershipListener membershipListener = Mockito.mock(MembershipListener)

    def setup() {
        timerService.getTimers() >> []
    }

    def 'when pmFunction is created at default, and single changes occur pmFunctionDataAndDelay entry is correctly updated'() {
        given:
            'instance is ' + mastership
            Mockito.when(membershipListener.isMaster()).thenReturn(mastership)

        when: 'update listener is called to set fileCollectionState to DISABLED for NETWORK_ELEMENT_1'
            dpsPmEnabledUpdateNotificationListener.onEvent(createAttributeChangeEvent(NETWORK_ELEMENT_1,
                [(PmFunctionConstants.FILE_COLLECTION_STATE):
                     ['OLD_VALUE': FileCollectionState.ENABLED.name(),
                      'NEW_VALUE': FileCollectionState.DISABLED.name()]
                ]));

        then: 'An entry is inserted in pmFunctionDataAndDelay'
            delayedPmFunctionUpdateProcessor.pmFunctionDataAndDelay.size() == (mastership ? 1 : 0)

        when: 'timeout expires before delay time is elapsed'
            delayedPmFunctionUpdateProcessor.processPmFunctionData()

        then: 'An entry is still present in pmFunctionDataAndDelay'
            delayedPmFunctionUpdateProcessor.pmFunctionDataAndDelay.size() == (mastership ? 1 : 0)

        when: 'timeout expires after delay time is elapsed'
            if (mastership) {
                delayedPmFunctionUpdateProcessor.pmFunctionDataAndDelay.get(NETWORK_ELEMENT_1).delay =
                    delayedPmFunctionUpdateProcessor.timeGenerator.currentTimeMillis() - 1000L
            }
            delayedPmFunctionUpdateProcessor.processPmFunctionData()

        then: 'Wntry has been proecssed and pmFunctionDataAndDelay is empty'
            delayedPmFunctionUpdateProcessor.pmFunctionDataAndDelay.size() == 0

        where:
            mastership << [true, false]
    }

    def 'when pmFunction is created at default, and changes occur pmFunctionDataAndDelay entry is correctly managed'() {
        given:
            'instance is ' + mastership
            Mockito.when(membershipListener.isMaster()).thenReturn(mastership)

        when: 'update listener is called to set fileCollectionState to DISABLED for NETWORK_ELEMENT_1'
            dpsPmEnabledUpdateNotificationListener.onEvent(createAttributeChangeEvent(NETWORK_ELEMENT_1,
                [(PmFunctionConstants.FILE_COLLECTION_STATE):
                     ['OLD_VALUE': FileCollectionState.ENABLED.name(),
                      'NEW_VALUE': FileCollectionState.DISABLED.name()]
                ]))

        and: 'update listener is called to set fileCollectionState to DISABLED for NETWORK_ELEMENT_2'
            dpsPmEnabledUpdateNotificationListener.onEvent(createAttributeChangeEvent(NETWORK_ELEMENT_2,
                [(PmFunctionConstants.FILE_COLLECTION_STATE):
                     ['OLD_VALUE': FileCollectionState.ENABLED.name(),
                      'NEW_VALUE': FileCollectionState.DISABLED.name()]
                ]))

        then: 'Two entries are inserted in pmFunctionDataAndDelay'
            delayedPmFunctionUpdateProcessor.pmFunctionDataAndDelay.size() == (mastership ? 2 : 0)

        when: 'timeout expires before delay time is elapsed'
            delayedPmFunctionUpdateProcessor.processPmFunctionData()

        then: 'Two entries are still present in pmFunctionDataAndDelay'
            delayedPmFunctionUpdateProcessor.pmFunctionDataAndDelay.size() == (mastership ? 2 : 0)

        when: 'timeout expires after delay time is elapsed'
            if (mastership) {
                delayedPmFunctionUpdateProcessor.pmFunctionDataAndDelay.get(NETWORK_ELEMENT_1).delay =
                    delayedPmFunctionUpdateProcessor.timeGenerator.currentTimeMillis() - 1000L
                delayedPmFunctionUpdateProcessor.pmFunctionDataAndDelay.get(NETWORK_ELEMENT_2).delay =
                    delayedPmFunctionUpdateProcessor.timeGenerator.currentTimeMillis() - 1000L

            }
            delayedPmFunctionUpdateProcessor.processPmFunctionData()

        then: 'Entries have been processed and pmFunctionDataAndDelay is empty'
            delayedPmFunctionUpdateProcessor.pmFunctionDataAndDelay.size() == 0

        where:
            mastership << [true, false]
    }

    def 'when pmFunction is created at default, and more changes than a single batch occur, pmFunctionDataAndDelay entry is correctly managed'() {
        given:
            'instance is ' + mastership
            Mockito.when(membershipListener.isMaster()).thenReturn(mastership)
            def iterations = 60

        when: 'update listener is called to set fileCollectionState to DISABLED for NETWORK_ELEMENT_1'
            for (int i = 0; i < iterations; i++) {
                dpsPmEnabledUpdateNotificationListener.onEvent(createAttributeChangeEvent(NETWORK_ELEMENT_1,
                    [(PmFunctionConstants.FILE_COLLECTION_STATE):
                         ['OLD_VALUE': FileCollectionState.ENABLED.name(),
                          'NEW_VALUE': FileCollectionState.DISABLED.name()]
                    ]))
            }

        then: 'Entries are inserted in pmFunctionDataAndDelay'
            delayedPmFunctionUpdateProcessor.pmFunctionDataAndDelay.size() == (mastership ? 1 : 0)

        when: 'timeout expires before delay time is elapsed'
            delayedPmFunctionUpdateProcessor.processPmFunctionData()

        then: 'Entries are still present in pmFunctionDataAndDelay'
            delayedPmFunctionUpdateProcessor.pmFunctionDataAndDelay.size() == (mastership ? 1 : 0)

        when: 'timeout expires after delay time is elapsed'
            if (mastership) {
                delayedPmFunctionUpdateProcessor.pmFunctionDataAndDelay.get(NETWORK_ELEMENT_1).delay =
                    delayedPmFunctionUpdateProcessor.timeGenerator.currentTimeMillis() - 1000L

            }
            delayedPmFunctionUpdateProcessor.processPmFunctionData()

        then: 'Max number of entries have been proecssed and pmFunctionDataAndDelay contains remaining entries'
            delayedPmFunctionUpdateProcessor.pmFunctionDataAndDelay.size() == (mastership ? 1 : 0)
            if (mastership) {
                assert getNumberOfEntries() == iterations - delayedPmFunctionUpdateProcessor.MAX_SIZE_FOR_PROCESSING_BATCH
            }

        where:
            mastership << [true, false]
    }

    def 'when pmFunction is created at default, and more changes than a single batch occur on different nodes, pmFunctionDataAndDelay entry is correctly managed'() {
        given:
            'instance is ' + mastership
            Mockito.when(membershipListener.isMaster()).thenReturn(mastership)
            def iterations = 60

        when: 'update listener is called to set fileCollectionState to DISABLED for NETWORK_ELEMENT_1'
            for (int i = 0; i < iterations; i++) {
                dpsPmEnabledUpdateNotificationListener.onEvent(createAttributeChangeEvent(NETWORK_ELEMENT_1,
                    [(PmFunctionConstants.FILE_COLLECTION_STATE):
                         ['OLD_VALUE': FileCollectionState.ENABLED.name(),
                          'NEW_VALUE': FileCollectionState.DISABLED.name()]
                    ]))
                dpsPmEnabledUpdateNotificationListener.onEvent(createAttributeChangeEvent(NETWORK_ELEMENT_3,
                    [(PmFunctionConstants.FILE_COLLECTION_STATE):
                         ['OLD_VALUE': FileCollectionState.ENABLED.name(),
                          'NEW_VALUE': FileCollectionState.DISABLED.name()]
                    ]))
            }

        then: 'Entries are inserted in pmFunctionDataAndDelay'
            delayedPmFunctionUpdateProcessor.pmFunctionDataAndDelay.size() == (mastership ? 2 : 0)

        when: 'timeout expires before delay time is elapsed'
            delayedPmFunctionUpdateProcessor.processPmFunctionData()

        then: 'Entries are still present in pmFunctionDataAndDelay'
            delayedPmFunctionUpdateProcessor.pmFunctionDataAndDelay.size() == (mastership ? 2 : 0)

        when: 'timeout expires after delay time is elapsed'
            if (mastership) {
                delayedPmFunctionUpdateProcessor.pmFunctionDataAndDelay.get(NETWORK_ELEMENT_1).delay =
                    delayedPmFunctionUpdateProcessor.timeGenerator.currentTimeMillis() - 1000L
            }
            delayedPmFunctionUpdateProcessor.processPmFunctionData()

        then: 'Max number of entries have been processed and pmFunctionDataAndDelay contains remaining entries'
            delayedPmFunctionUpdateProcessor.pmFunctionDataAndDelay.size() == (mastership ? 2 : 0)
            if (mastership) {
                assert getNumberOfEntries() == iterations * 2 - delayedPmFunctionUpdateProcessor.MAX_SIZE_FOR_PROCESSING_BATCH
            }

        where:
            mastership << [true, false]
    }

    private static DpsAttributeChangedEvent createAttributeChangeEvent(final boolean newValue, final String nodeFdn) {
        final Collection<AttributeChangeData> attributeChangeData = [
            new AttributeChangeData('pmEnabled', !newValue, newValue, null, null)
        ]
        return new DpsAttributeChangedEvent(fdn: "$nodeFdn,PmFunction=1", changedAttributes: attributeChangeData)
    }

    private static DpsAttributeChangedEvent createAttributeChangeEvent(final String nodeFdn, final Map<String, Map<String, Object>> attributes) {
        final Collection<AttributeChangeData> attributeChangeData = new ArrayList<AttributeChangeData>()
        attributes.each { key, value ->
            attributeChangeData.add(new AttributeChangeData(key, value.get('OLD_VALUE'), value.get('NEW_VALUE'), null, null))
        }
        return new DpsAttributeChangedEvent(fdn: "$nodeFdn,PmFunction=1", changedAttributes: attributeChangeData)
    }

    private int getNumberOfEntries() {
        final Iterator<Map.Entry<String, ScheduleData>> entryIterator = delayedPmFunctionUpdateProcessor.pmFunctionDataAndDelay.entrySet().iterator();
        int numOfEntries = 0;
        while (entryIterator.hasNext()) {
            numOfEntries += entryIterator.next().getValue().scheduledEntries.size();
        }
        return numOfEntries;
    }
}
