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

package com.ericsson.oss.services.pm

import com.ericsson.cds.cdi.support.providers.custom.model.ModelPattern
import com.ericsson.cds.cdi.support.providers.custom.model.RealModelServiceProvider

class PmServiceEjbSkeletonSpec extends BaseSkeletonSpec {

    //DO NOT ADD pfm_measurements HERE. USE PmBaseSpec IF YOU NEED MORE MODELS.
    // The SkeletonSpec is supposed to be used as super-class to all tests that do not require models for pfm_measurements, pfm_events and various nodes.
    // Using SkeletonSpek allows for much faster execution/debugging for developers so please keep this in mind if you want to add more models.
    static filteredModels = [new ModelPattern('.*', 'pmic.*', '.*', '.*'),
                             new ModelPattern(".*", ".*", "PMICFunctions", ".*"),
                             new ModelPattern(".*", ".*", "STATISTICAL_SystemDefinedSubscriptionAttributes", ".*"),
                             new ModelPattern(".*", ".*", "CONTINUOUSCELLTRACE_SystemDefinedSubscriptionAttributes", ".*")]
    static RealModelServiceProvider realModelServiceProvider = new RealModelServiceProvider(filteredModels)

    def getRealModelServiceProvider() {
        return realModelServiceProvider
    }
}

