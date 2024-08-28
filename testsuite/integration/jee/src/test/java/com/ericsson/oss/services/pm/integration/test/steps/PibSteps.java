/*------------------------------------------------------------------------------
 *******************************************************************************
 * COPYRIGHT Ericsson 2018
 *
 * The copyright to the computer program(s) herein is the property of
 * Ericsson Inc. The programs may be used and/or copied only with written
 * permission from Ericsson Inc. or in accordance with the terms and
 * conditions stipulated in the agreement/contract under which the
 * program(s) have been supplied.
 *******************************************************************************
 *----------------------------------------------------------------------------*/

package com.ericsson.oss.services.pm.integration.test.steps;

import javax.inject.Inject;

import com.ericsson.oss.services.pm.integration.test.helpers.PibHelper;


public class PibSteps {

    @Inject
    private PibHelper pibHelper;

    public void updateConfigParam(final String paramName, final String paramValue, final Class<?> propertyType) throws Exception {
        pibHelper.updateParameter(paramName, paramValue, propertyType);
    }
}
