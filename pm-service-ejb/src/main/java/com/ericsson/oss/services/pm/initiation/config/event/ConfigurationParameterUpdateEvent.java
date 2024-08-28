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

package com.ericsson.oss.services.pm.initiation.config.event;

/**
 * Configuration Parameter Update Event.
 */
public class ConfigurationParameterUpdateEvent {
    final String configParamName;
    final Object oldValue;
    final Object newValue;

    public ConfigurationParameterUpdateEvent(final String configParamName, final Object oldValue, final Object newValue) {
        super();
        this.configParamName = configParamName;
        this.oldValue = oldValue;
        this.newValue = newValue;
    }

    public String getConfigParamName() {
        return configParamName;
    }

    public Object getOldValue() {
        return oldValue;
    }

    public Object getNewValue() {
        return newValue;
    }

    @Override
    public String toString() {
        return "ConfigChangeEvent [configParamName=" + configParamName + ", oldValue=" + oldValue + ", newValue=" + newValue + "]";
    }

}
