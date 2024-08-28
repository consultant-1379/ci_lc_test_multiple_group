/*******************************************************************************
 * COPYRIGHT Ericsson 2016
 *
 * The copyright to the computer program(s) herein is the property of
 * Ericsson Inc. The programs may be used and/or copied only with written
 * permission from Ericsson Inc. or in accordance with the terms and
 * conditions stipulated in the agreement/contract under which the
 * program(s) have been supplied.
 ******************************************************************************/

package com.ericsson.oss.services.pm.initiation.upgrade;

import static com.ericsson.oss.services.pm.initiation.constants.PmicLogCommands.PMIC_UNEXPECTED_UPGRADE_NOTIFICATION;
import static com.ericsson.oss.services.pm.initiation.constants.PmicLogCommands.PMIC_UPGRADE_NOTIFICATION;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Observes;
import javax.inject.Inject;

import com.ericsson.oss.itpf.sdk.recording.EventLevel;
import com.ericsson.oss.itpf.sdk.recording.SystemRecorder;
import com.ericsson.oss.itpf.sdk.upgrade.UpgradeEvent;
import com.ericsson.oss.itpf.sdk.upgrade.UpgradePhase;

/**
 * UpgradeEventObserver handles upgrade notification
 *
 * @author unknown
 * Listens for upgrade notifications
 */
@ApplicationScoped
public class UpgradeEventObserver {

    private static final String PMIC_SERVICE = "PMICService";

    @Inject
    private SystemRecorder systemRecorder;

    /**
     * Listens for upgrade notifications. Please do not rely on as it will be discontinued in the future
     *
     * @param event
     *         - the {@link UpgradeEvent}
     */
    public void upgradeNotificationObserver(@Observes final UpgradeEvent event) {
        final UpgradePhase phase = event.getPhase();
        switch (phase) {
            case SERVICE_INSTANCE_UPGRADE_PREPARE:
            case SERVICE_CLUSTER_UPGRADE_PREPARE:
            case SERVICE_CLUSTER_UPGRADE_FAILED:
            case SERVICE_CLUSTER_UPGRADE_FINISHED_SUCCESSFULLY:
            case SERVICE_INSTANCE_UPGRADE_FAILED:
            case SERVICE_INSTANCE_UPGRADE_FINISHED_SUCCESSFULLY:
            case DB_SCHEMA_UPGRADE_PREPARE:
                event.accept("OK");

                final String additionalInfo = String.format("PMIC, Received upgrade notification : %s", phase.toString());
                systemRecorder.recordEvent(PMIC_UPGRADE_NOTIFICATION.getDescription(), EventLevel.COARSE, PMIC_SERVICE, PMIC_SERVICE,
                        additionalInfo);
                break;
            default:
                event.reject("Unexpected UpgradePhase");
                final String Info = String.format("PMIC, Received unexpected upgradePhase in upgrade notification: %s", phase.toString());
                systemRecorder
                        .recordEvent(PMIC_UNEXPECTED_UPGRADE_NOTIFICATION.getDescription(), EventLevel.COARSE, PMIC_SERVICE, PMIC_SERVICE, Info);

                break;
        }
    }
}
