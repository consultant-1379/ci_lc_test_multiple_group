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
package com.ericsson.oss.services.pm.collection.api

import com.ericsson.cds.cdi.support.spock.CdiSpecification

class ProcessRequestVoSpec extends CdiSpecification {

    def "Verify ProcessRequestVo object for NORMAL_PRIORITY_CELLTRACE and HIGH_PRIORITY_CELLTRACE are same"() {
        given:
        ProcessRequestVO processRequestVO1 = new ProcessRequestVO.ProcessRequestVOBuilder("1.1.1.1", 2, "NORMAL_PRIORITY_CELLTRACE").build()
        ProcessRequestVO processRequestVO2 = new ProcessRequestVO.ProcessRequestVOBuilder("1.1.1.1", 2, "HIGH_PRIORITY_CELLTRACE").build()
        expect: "compare correct object"
        processRequestVO1 == processRequestVO2
        processRequestVO1.hashCode() == processRequestVO2.hashCode()
    }

    def "Verify ProcessRequestVo subscription type object for BSCRECORDINGS"() {
        given:
        ProcessRequestVO processRequestVO1 = new ProcessRequestVO.ProcessRequestVOBuilder("GSM02BSC01", 15, "BSCRECORDINGS").build()
        expect: "check processtype value"
        processRequestVO1.getProcessType() == "BSCRECORDINGS"
    }
}
