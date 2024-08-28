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

package com.ericsson.oss.services.pm.initiation.model.metadata.events;

import java.util.Collection;

import com.ericsson.oss.itpf.modeling.common.info.ModelInfo;
import com.ericsson.oss.pmic.subscription.enums.EventTypeFilter;
import com.ericsson.oss.services.pm.initiation.model.utils.PmMetaDataConstants;
import com.ericsson.services.pm.initiation.restful.api.EventTableRow;

/**
 * The type Pm events look up for UETR events.
 */
public class PmUetrEventsLookUp extends AbstractPmEventsLookUp {

    /**
     * {@inheritDoc}
     */
    @Override
    protected Collection<EventTableRow> fetchEventsAndGroups(final ModelInfo modelInfo) {
        final Collection<EventTableRow> result =
                getProfileEvents(modelInfo, PmMetaDataConstants.UETR_PROFILE_PATTERN, EventTypeFilter.TRIGGERED);
        result.addAll(getProfileEvents(modelInfo, PmMetaDataConstants.UETR_PROFILE_PATTERN, EventTypeFilter.NON_TRIGGERED));
        return result;
    }

}
