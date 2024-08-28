
/*
 * COPYRIGHT Ericsson 2017
 *
 * The copyright to the computer program(s) herein is the property of
 * Ericsson Inc. The programs may be used and/or copied only with written
 * permission from Ericsson Inc. or in accordance with the terms and
 * conditions stipulated in the agreement/contract under which the
 * program(s) have been supplied.
 */

package com.ericsson.oss.services.pm.initiation.config.listener.processors;

import static com.ericsson.oss.services.pm.initiation.config.listener.processors.PmicNfsShareListEntrySeparator.ENTRY_SEPARATOR;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.enterprise.context.ApplicationScoped;

/**
 * Implementation of the interface {@link PmicNfsShareListValueProcessor} to process changes for the modeled configuration parameter
 * 'pmicNfsShareList'
 */
@ApplicationScoped
public class PmicNfsShareListValueProcessorImpl implements PmicNfsShareListValueProcessor {

    /**
     * {@inheritDoc}
     */
    @Override
    public List<String> process(final String newValueForPmicNfsShareList) {
        final List<String> processedPmicNfsShareList = new ArrayList<>();

        if (newValueForPmicNfsShareList == null) {
            throw new IllegalArgumentException("New value cannot be null");
        }
        if ("".equals(newValueForPmicNfsShareList)) {
            return processedPmicNfsShareList;
        }

        final String[] entriesArray = newValueForPmicNfsShareList.split(ENTRY_SEPARATOR.getSeparator());

        return Arrays.asList(entriesArray);
    }
}
