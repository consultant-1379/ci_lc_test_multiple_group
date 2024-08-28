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

package com.ericsson.oss.services.pm.initiation.ejb;

/**
 * The enum Pmic config parameter.
 */
public enum PMICConfigParameter {

    CBSENABLED,
    MAXNOOFCBSALLOWED,
    PMICEBSMENABLED,
    PMICEBSSTREAMCLUSTERDEPLOYED,
    PMICEBSLROPINMINUTES,
    MAXNOOFMOINSTANCEALLOWED;

    /**
     * From value pmic config parameter.
     *
     * @param parameter
     *         the parameter to get enum value from
     *
     * @return the pmic config parameter
     */
    public static PMICConfigParameter fromValue(final String parameter) {
        return valueOf(parameter.toUpperCase());
    }

    /**
     * Gets the string value of the enum instance
     *
     * @return the string value of the enum instance
     */
    public String value() {
        return name();
    }
}
