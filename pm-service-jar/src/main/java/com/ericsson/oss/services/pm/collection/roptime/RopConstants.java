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

package com.ericsson.oss.services.pm.collection.roptime;

/**
 * Rop constants class, is used to find the collection task creation point in the ROP.
 */
public final class RopConstants {

    private RopConstants() {
        //utility class should not be instantiated.
    }

    /*
     * This constant is used to find the collection task creation point in the ROP
     * 2 means mid of the ROP, e.g. for 60 seconds ROP collection task creation point is 60/2=30 seconds Mid of the ROP
     */
    public static final int COLLECTION_POINT = 2;

}
