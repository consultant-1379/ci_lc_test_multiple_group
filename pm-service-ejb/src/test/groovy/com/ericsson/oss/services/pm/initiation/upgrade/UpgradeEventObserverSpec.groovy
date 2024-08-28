/*******************************************************************************
 * COPYRIGHT Ericsson 2016
 *
 * The copyright to the computer program(s) herein is the property of
 * Ericsson Inc. The programs may be used and/or copied only with written
 * permission from Ericsson Inc. or in accordance with the terms and
 * conditions stipulated in the agreement/contract under which the
 * program(s) have been supplied.
 ******************************************************************************/

package com.ericsson.oss.services.pm.initiation.upgrade

import spock.lang.Unroll

import com.ericsson.cds.cdi.support.rule.ImplementationInstance
import com.ericsson.cds.cdi.support.rule.ObjectUnderTest
import com.ericsson.cds.cdi.support.spock.CdiSpecification
import com.ericsson.oss.itpf.sdk.upgrade.UpgradeEvent
import com.ericsson.oss.itpf.sdk.upgrade.UpgradePhase

class UpgradeEventObserverSpec extends CdiSpecification {

    @ObjectUnderTest
    UpgradeEventObserver eventObserver

    @ImplementationInstance
    UpgradeEvent event = Mock(UpgradeEvent)

    @Unroll
    def 'when upgrade event #eventPhase is received, we will accept all valid UpgradeEvent phases'() {
        when:
        event.getPhase() >> eventPhase;
        eventObserver.upgradeNotificationObserver(event)
        then:
        1 * event.accept("OK")
        where:
        eventPhase << UpgradePhase.values();
    }
}
