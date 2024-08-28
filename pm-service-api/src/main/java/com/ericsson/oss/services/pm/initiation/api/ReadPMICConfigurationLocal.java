/*------------------------------------------------------------------------------
 *******************************************************************************
 * COPYRIGHT Ericsson 2015
 *
 * The copyright to the computer program(s) herein is the property of
 * Ericsson Inc. The programs may be used and/or copied only with written
 * permission from Ericsson Inc. or in accordance with the terms and
 * conditions stipulated in the agreement/contract under which the
 * program(s) have been supplied.
 *******************************************************************************
 *----------------------------------------------------------------------------*/

package com.ericsson.oss.services.pm.initiation.api;

import javax.ejb.Local;

import com.ericsson.oss.services.pm.services.exception.ConfigurationParameterException;

/**
 * Class for Reading pmic configuration.
 */
@Local
public interface ReadPMICConfigurationLocal {

    /**
     * This method checks whether the parameter exist or not and if it finds the value then return the corresponding value else, it will throw the
     * Exception.
     * <p>
     *
     * @param pibParam
     *         - Platform Integration Bridge parameter used to get corresponding config parameter
     *
     * @return String - return the configured value for corresponding parameter
     * @throws ConfigurationParameterException
     *         - will throw ConfigurationParameterException if pibParam value isnt handled
     */
    String getConfigParamValue(String pibParam) throws ConfigurationParameterException;
}
