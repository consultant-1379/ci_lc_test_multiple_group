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

package com.ericsson.oss.services.pm.license;

import java.util.List;
import java.util.Map;

/**
 * Represents a license checker.
 */
public interface LicenseChecker {

    /**
     * Verify the license name.
     *
     * @param licenseName
     *         The license name.
     *
     * @return The license information.
     * @throws IllegalArgumentException
     *         if argument is null or empty.
     */
    License verify(String licenseName);

    /**
     * Verify the license name List.
     *
     * @param licenseNames
     *         List of licenseNames.
     *
     * @return The license information.
     */
    Map<String, Map<String, Object>> verify(List<String> licenseNames);
}
