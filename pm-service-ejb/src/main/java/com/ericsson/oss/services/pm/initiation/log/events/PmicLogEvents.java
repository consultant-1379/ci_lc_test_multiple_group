/*------------------------------------------------------------------------------
 *******************************************************************************
 * COPYRIGHT Ericsson 2012
 *
 * The copyright to the computer program(s) herein is the property of
 * Ericsson Inc. The programs may be used and/or copied only with written
 * permission from Ericsson Inc. or in accordance with the terms and
 * conditions stipulated in the agreement/contract under which the
 * program(s) have been supplied.
 *******************************************************************************
 *----------------------------------------------------------------------------*/

package com.ericsson.oss.services.pm.initiation.log.events;

import com.ericsson.oss.itpf.sdk.recording.EventLevel;

/**
 * This class contains all the PMIC LOG Events info.
 *
 * @author ekamkal
 */
public enum PmicLogEvents {

    SCANNER_CREATE_NOTIFICATION_RECEIVED("PMIC.DPS_SCANNER_CREATE_EVENT_RECEIVED", EventLevel.COARSE),
    SCANNER_UPDATE_NOTIFICATION_RECEIVED("PMIC.DPS_SCANNER_UPDATE_EVENT_RECEIVED", EventLevel.COARSE),
    SCANNER_DELETE_NOTIFICATION_RECEIVED("PMIC.DPS_SCANNER_DELETE_EVENT_RECEIVED", EventLevel.COARSE),
    BUILD_FILE_COLLECTION_TASK_LIST("PMIC.BUILDING_FILE_COLLECTION_TASK_LIST", EventLevel.COARSE),
    CREATE_FILE_COLLECTION_TASK_MNAGER_TIMER("PMIC.CREATING_FILE_COLLECTION_TASK_MANAGER_TIMER", EventLevel.COARSE),
    STOP_FILE_COLLECTION_TASK_MANAGER_TIMER("PMIC.STOPPING_FILE_COLLECTION_TASK_MANAGER_TIMER", EventLevel.COARSE),
    SEND_FILE_COLLECTION_TASK_LIST("PMIC.SEND_FILE_COLLECTION_TASK_LIST", EventLevel.COARSE),
    CREATE_FILE_COLLECTION_TASK_SENDER_TIMER("PMIC.CREATING_FILE_COLLECTION_TASK_SENDER_TIMER", EventLevel.COARSE),
    STOP_FILE_COLLECTION_TASK_SENDER("PMIC.STOPPING_FILE_COLLECTION_TASK_SENDER_TIMER", EventLevel.COARSE),
    FILE_COLLECTION_FAILURE_RECEIVED("PMIC.FILE_COLLECTION_FAILURE_RECEIVED", EventLevel.COARSE),
    FILE_COLLECTION_RECOVERY_TASK_CREATED("PMIC.FILE_COLLECTION_RECOVERY_TASK_CREATED", EventLevel.COARSE),
    FILE_COLLECTION_NOT_RECOVERABLE("PMIC.FILE_COLLECTION_NOT_RECOVERABLE", EventLevel.COARSE),
    FILE_COLLECTION_RECOVERY_SUCCESS("PMIC.FILE_COLLECTION_RECOVERY_SUCCESS", EventLevel.COARSE),
    FILE_COLLECTION_FAILED("PMIC.FILE_COLLECTION_FAILED", EventLevel.COARSE),
    FILE_COLLECTION_RECOVERY_RESULT("PMIC.FILE_COLLECTION_RECOVERY_RESULT", EventLevel.COARSE),
    FILE_COLLECTION_RESULT("PMIC.FILE_COLLECTION_RESULT", EventLevel.COARSE),
    NODE_RECONNECT_FILE_COLLECTION_RECOVERY_TASK_SENT("PMIC.NODE_RECOVERY_FILE_COLLECTION_RECOVERY_TASK_SENT", EventLevel.COARSE),
    NODE_RECONNECT_NOTIFICATION("PMIC.NODE_RECONNECT_NOTIFICATION_RECEIVED", EventLevel.COARSE),
    FILE_DELETION_INTERVAL_ATTRIBUTE_CHANGED("PMIC.FILE_DELETION_INTERVAL_ATTRIBUTE_CHANGED", EventLevel.COARSE),
    SYMBOLIC_LINK_DELETION_INTERVAL_ATTRIBUTE_CHANGED("PMIC.SYMLINK_DELETION_INTERVAL_ATTRIBUTE_CHANGED", EventLevel.COARSE),
    FILE_DELETION_FROM_NFS_SHARE("PMIC.FILE_DELETION_FROM_NFS_SHARE", EventLevel.COARSE),
    SYMBOLIC_LINK_DELETION_FROM_NFS_SHARE("PMIC.SYMBOLIC_LINK_DELETION_FROM_NFS_SHARE", EventLevel.COARSE),
    STARTUP_FILE_RECOVERY("PMIC.STARTUP_FILE_RECOVERY", EventLevel.COARSE),
    STARTUP_SCHEDULED_FILE_RECOVERY("PMIC.SCHEDULED_FILE_RECOVERY", EventLevel.COARSE);

    private final String eventKey;
    private final EventLevel eventLevel;

    /**
     * Initialises a PMICLogEvents Object
     *
     * @param eventKey
     *         - the event key
     * @param eventLevel
     *         - the event level
     */
    PmicLogEvents(final String eventKey, final EventLevel eventLevel) {
        this.eventKey = eventKey;
        this.eventLevel = eventLevel;
    }

    /**
     * Gets event key.
     *
     * @return the event key
     */
    public String getEventKey() {
        return eventKey;
    }

    /**
     * Gets event level.
     *
     * @return the event level
     */
    public EventLevel getEventLevel() {
        return eventLevel;
    }
}
