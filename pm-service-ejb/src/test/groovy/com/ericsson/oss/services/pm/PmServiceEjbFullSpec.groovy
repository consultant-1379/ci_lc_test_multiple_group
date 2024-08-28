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

class PmServiceEjbFullSpec extends BaseSkeletonSpec {

    /*
     * This filtered models set is the master set of all model patterns needed for all tests currently written. Previously all models
     * were being loaded and this was time consuming as it took almost a minute and a half to build the model repo. Most tests need
     * the commented out set below but as of time of writing SubscriptionValidationSpec and PmMoinstancesLookUpSpec need the extended
     * filtered set and the decision was made to have one set of filters for all tests. If you are running your test in your IDE and
     * you do not need to use the master set and wish to save time building the model repo you can use the sub set which is commented
     * out below instead. This Filtering of models should be refactored when a better solution is available as not all tests need the
     * master set and this filtering could be more efficient if the logic was in the framework to have a base set of filters and we
     * could add to it in each test class.
     */
    static def filteredModels = [new ModelPattern("pfm_event", ".*", ".*", ".*"), //All events for all node types (these are not big)
                                 //COUNTERS ------ THESE ARE HUGE FILES SO PLEASE USE ONLY ONE PARTICULAR VERSION PER NODE TYPE ------ COUNTERS//
                                 new ModelPattern("pfm_measurement", "ERBS", "NE-defined.*", ".*"),
                                 new ModelPattern("pfm_measurement", "RNC", "NE-defined", "302844197299.570973145071.360909781222"),
                                 new ModelPattern("pfm_measurement", "RBS", "NE-defined", "865846184775.724661915212.334504283828"),
                                 new ModelPattern("pfm_measurement", "SGSN-MME", "NE-defined", "17393998269.548742343269.672773014295"),
                                 new ModelPattern("pfm_measurement", "SGSN-MME", "NE-defined-EBS", "728640688790.31103334842.230414419262"),
                                 new ModelPattern("pfm_measurement", "RadioNode", "NE-defined", "601868161194.915374994175.690942869240"),
                                 new ModelPattern("pfm_measurement", "RadioNode", "NE-defined", "645478802444.406187419126.62490048196"),
                                 new ModelPattern("pfm_measurement", "RadioNode", "NE-defined", "775594457684.780051587212.834770756403"),
                                 new ModelPattern("pfm_measurement", "RadioNode", "NE-defined-EBS", "811009674287.1012148498324.81992437622"),
                                 new ModelPattern("pfm_measurement", "EPG", "NE-defined", "935014410894.686938588409.1021277924711"),
                                 new ModelPattern("pfm_measurement", "MSRBS_V1", "NE-defined", "610357586020.838987120130.307445516514"),
                                 new ModelPattern("pfm_measurement", "MSRBS_V1", "NE-defined-EBS", "871849819133.118010947113.451923166203"),
                                 new ModelPattern("pfm_measurement", "5GRadioNode", "NE-defined", "287327310131.240135665436.784434076582"),
                                 new ModelPattern("ext_integrationpointlibrary", ".*", ".*", ".*"),
                                 //COUNTERS ------ THESE ARE HUGE FILES SO PLEASE USE ONLY ONE PARTICULAR VERSION PER NODE TYPE ------ COUNTERS//
                                 new ModelPattern("cfm_miminfo", ".*", ".*", ".*"),
                                 new ModelPattern('.*', 'NODE.*', '.*', '.*'),
                                 new ModelPattern('.*', 'pmic.*', '.*', '.*'),
                                 new ModelPattern(".*", ".*", "PMICFunctions", ".*"),
                                 new ModelPattern('.*', '.*', 'STATISTICAL_SystemDefinedSubscriptionAttributes', '.*'),
                                 new ModelPattern('.*', '.*', 'CELLTRACE_SubscriptionAttributes', '.*'),
                                 new ModelPattern('.*', '.*', 'CELLTRACENRAN_SubscriptionAttributes', '.*'),
                                 new ModelPattern('.*', '.*', 'CONTINUOUSCELLTRACE_SystemDefinedSubscriptionAttributes', '.*'),
                                 new ModelPattern('.*', '.*', 'CONTINUOUSCELLTRACENRAN_SystemDefinedSubscriptionAttributes', '.*'),
                                 new ModelPattern(".*", ".*", "STATISTICAL_SubscriptionAttributes", ".*"),
                                 new ModelPattern(".*", ".*", "RTT_SubscriptionAttributes", ".*"),
                                 new ModelPattern(".*", ".*", "CELLRELATION_SubscriptionAttributes", ".*"),
                                 new ModelPattern(".*", ".*", "EBM_SubscriptionAttributes", ".*"),
                                 new ModelPattern(".*", ".*", "EBS_SubscriptionAttributes", ".*"),
                                 new ModelPattern(".*", ".*", "MOINSTANCE_SubscriptionAttributes", ".*"),
                                 new ModelPattern(".*", ".*", "RES_SubscriptionAttributes", ".*"),
                                 new ModelPattern(".*", ".*", "STATISTICAL_SubscriptionAttributes", ".*"),
                                 new ModelPattern("dps_primarytype", "RNC_NODE_MODEL", "ResMeasControl", ".*"),
                                 new ModelPattern("oss_edt", "RNC_NODE_MODEL", "SupportedResServices", ".*"),
                                 new ModelPattern("oss_edt", "RNC_NODE_MODEL", "SupportedResMeasQuantities", ".*")]

    static RealModelServiceProvider realModelServiceProvider = new RealModelServiceProvider(filteredModels)

    def getRealModelServiceProvider() {
        return realModelServiceProvider;
    }
}
