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

package com.ericsson.oss.services.pm.initiation.config.listener.processors;

import java.util.List;

import com.ericsson.oss.services.pm.initiation.config.listener.ConfigurationChangeListener;

/**
 * Given an input parameter for the modeled configuration parameter: pmicNfsShareList, this class processes a change event
 */
public interface PmicNfsShareListValueProcessor {

    /**
     * Applies appropriate logic to reformat, filter, complete, parse, etc the input parameter to produce a suitable list of available mount
     * points available for the application.
     *
     * @param newValueForPmicNfsShareList
     *         The input parameter that represents a value changed and observed by {@link ConfigurationChangeListener}
     *
     * @return A list of Strings that represent the available mount points for the application.
     */
    List<String> process(final String newValueForPmicNfsShareList);
}
