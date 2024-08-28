/*******************************************************************************
 * COPYRIGHT Ericsson 2016
 *
 * The copyright to the computer program(s) herein is the property of
 * Ericsson Inc. The programs may be used and/or copied only with written
 * permission from Ericsson Inc. or in accordance with the terms and
 * conditions stipulated in the agreement/contract under which the
 * program(s) have been supplied.
 ******************************************************************************/

package com.ericsson.oss.services.pm.deletion.schedulers

import javax.ejb.TimerService
import javax.inject.Inject

import com.ericsson.cds.cdi.support.rule.ImplementationClasses
import com.ericsson.cds.cdi.support.rule.ImplementationInstance
import com.ericsson.cds.cdi.support.spock.CdiSpecification
import com.ericsson.oss.itpf.sdk.cluster.MembershipChangeEvent
import com.ericsson.oss.services.pm.collection.mountpoints.DestinationMountPointConfigSourceFromModeledConfiguration
import com.ericsson.oss.services.pm.deletion.schedulers.helper.FileSystemOperationHelper
import com.ericsson.oss.services.pm.initiation.config.listener.processors.PmicNfsShareListValueProcessorImpl
import com.ericsson.oss.services.pm.initiation.config.listener.validators.PathValidatorImpl
import com.ericsson.oss.services.pm.scheduling.cluster.MembershipListener

class AbstractFileDeletionSchedulerBeanSpec extends CdiSpecification {

    final static int DAYS_5 = 5
    final static int HOURS_3 = 3
    final static int HOURS_24 = 24
    final static int FILES_PER_HR = 4
    final static int MINUTES_PER_ROP = 15

    @Inject
    FileSystemOperationHelper fileSystemHelper

    def timersCreated = []

    @ImplementationInstance
    TimerService timerService = [
            getTimers          : { [] },
            createIntervalTimer: { a, b, c -> timersCreated += c; null },
            createTimer        : { a, b -> timersCreated += b; null }
    ] as TimerService

    @ImplementationInstance
    protected MembershipListener listener = new MembershipListener() {
        private boolean isMaster = true

        @Override
        public void listenForMembershipChange(MembershipChangeEvent event) {
            isMaster = event.isMaster()
        }

        @Override
        public boolean isMaster() {
            return isMaster
        }
    }
    @ImplementationClasses
    def myClasses = [DestinationMountPointConfigSourceFromModeledConfiguration.class, PmicNfsShareListValueProcessorImpl.class, PathValidatorImpl.class]
}
