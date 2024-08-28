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
package com.ericsson.oss.services.pm.initiation.model.metadata.counters;

import java.util.HashSet;
import java.util.Set;
import javax.inject.Inject;

import org.slf4j.Logger;

import com.ericsson.oss.itpf.modeling.modelservice.typed.TypedModelAccess;
import com.ericsson.oss.itpf.modeling.modelservice.typed.core.target.TargetTypeInformation;

/**
 * HelperClass to fetch the technologyDomain of a neType
 */
public class PmicTechnologyDomainHelper {

    @Inject
    private Logger log;

    @Inject
    private TypedModelAccess typedModelAccess;

    /**
     * This method returns Set of String specifying technology domain the neType supports
     *
     * @param neType
     *         - NetworkElement Type
     *
     * @return Set of String specifying technologyDomain the neType supports
     */
    public Set<String> getTechnologyDomain(final String neType) {
        Set<String> technologyDomainSet = new HashSet<>();
        try {
            final TargetTypeInformation targetTypeInformation = typedModelAccess
                    .getModelInformation(TargetTypeInformation.class);
            technologyDomainSet = targetTypeInformation.getTechnologyDomains(
                    TargetTypeInformation.CATEGORY_NODE, neType);
            log.debug("Technology Domain for neType {} is {} ", neType, technologyDomainSet);
        } catch (final IllegalArgumentException ex) {
            log.debug("No technologyDomain information is available for neType {}.", neType);
        }
        return technologyDomainSet;
    }

}
