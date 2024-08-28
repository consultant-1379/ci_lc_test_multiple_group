/*
 * ------------------------------------------------------------------------------
 *  ********************************************************************************
 *  * COPYRIGHT Ericsson  2019
 *  *
 *  * The copyright to the computer program(s) herein is the property of
 *  * Ericsson Inc. The programs may be used and/or copied only with written
 *  * permission from Ericsson Inc. or in accordance with the terms and
 *  * conditions stipulated in the agreement/contract under which the
 *  * program(s) have been supplied.
 *  *******************************************************************************
 *  *----------------------------------------------------------------------------
 */

package com.ericsson.oss.services.pm.initiation.enodeb.subscription.resource

import spock.lang.Unroll

import javax.ws.rs.core.Response

import com.ericsson.cds.cdi.support.configuration.InjectionProperties
import com.ericsson.cds.cdi.support.providers.custom.model.ModelPattern
import com.ericsson.cds.cdi.support.providers.custom.model.RealModelServiceProvider
import com.ericsson.cds.cdi.support.rule.ImplementationClasses
import com.ericsson.cds.cdi.support.rule.ObjectUnderTest
import com.ericsson.oss.pmic.cdi.test.util.SkeletonSpec
import com.ericsson.oss.services.pm.modelservice.PmCapabilities
import com.ericsson.oss.services.pm.modelservice.PmCapabilityInformation
import com.ericsson.oss.services.pm.modelservice.PmCapabilityModelServiceImpl
import com.ericsson.oss.services.pm.modelservice.PmGlobalCapabilities

class PmCapabilityResourceSpec extends SkeletonSpec {

    static def filteredModels = [new ModelPattern('oss_capability', 'global', 'STATISTICAL_SubscriptionAttributes', '.*'),
                                 new ModelPattern('oss_capability', 'global', 'MTR_SubscriptionAttributes', '.*'),
                                 new ModelPattern('oss_capability', 'global', 'PMICFunctions', '.*'),
                                 new ModelPattern('oss_capability', 'global', 'BSCRECORDINGS_SubscriptionAttributes', '.*'),
                                 new ModelPattern('oss_targettype', 'NODE', '.*', '.*'),
                                 new ModelPattern('oss_capabilitysupport', 'BSC', 'PMICFunctions', '.*'),
                                 new ModelPattern('oss_capabilitysupport', 'MSC-BC-BSP', 'PMICFunctions', '.*'),
                                 new ModelPattern('oss_capabilitysupport', 'MSC-DB', 'PMICFunctions', '.*')]

    static RealModelServiceProvider realModelServiceProvider = new RealModelServiceProvider(filteredModels)

    @Override
    def addAdditionalInjectionProperties(InjectionProperties injectionProperties) {
        injectionProperties.addInjectionProvider(realModelServiceProvider)
        injectionProperties.autoLocateFrom('com.ericsson.oss.services.pm.initiation')
    }

    @ObjectUnderTest
    PmCapabilityResource capabilityResource

    @ImplementationClasses
    def classes = [PmCapabilityModelServiceImpl.class]

    @Unroll
    def 'when valid #function targetfunction, return capability value from global capability when no capability support available'(String function, Boolean result) {

        given: 'define 3 capability name'
            final String capabilityName = 'cbs, importExportSubscriptionApplicable,ropDropDownDisabled'
        when: 'pmCapabilities request is sent'
            Response response = capabilityResource.getGlobalCapabilities(function, capabilityName)
            PmGlobalCapabilities pmCapabilities = response.entity

        then: 'pmCapabilities should be returned'
            Map<String, Object> capabilityAttributes = pmCapabilities.globalCapabilities
        and: 'validate capabilityAttributes is not empty'
            !capabilityAttributes.isEmpty()
        and: 'validate cbs targetTypes from capabilityAttributes'
            capabilityAttributes.get('cbs') == result

        where:
            function                               || result
            'STATISTICAL_SubscriptionAttributes'   || true
            'BSCRECORDINGS_SubscriptionAttributes' || false
            'MTR_SubscriptionAttributes'           || true
    }

    def 'when a valid targetFunction and null capabilities is passed to get capability value from the global capabilities, then a status NOT FOUND Response is returned'() {

        given: 'define a valid target function and null capabilities'
            final String function = 'STATISTICAL_SubscriptionAttributes'
            final String capabilities = null
            final int statusCode = Response.Status.NOT_FOUND.statusCode

        when: 'pmCapabilities request is sent'
            Response response = capabilityResource.getGlobalCapabilities(function, capabilities)

        then: 'status code is NOT FOUND'
            statusCode == response.status
    }

    @Unroll
    def 'when valid #function targetfunction, return default capability value'() {

        given: 'define 3 capability name'
            final String capabilityName = 'cbs, importExportSubscriptionApplicable,ropDropDownDisabled'
        when: 'call capabilitiesByFunction to read capability model for #function'
            Response response = capabilityResource.getGlobalCapabilities(function, capabilityName)
            PmGlobalCapabilities globalCapabilities = response.entity

        then: 'globalCapabilities should be returned'
            Map<String, Object> capabilityAttributes = globalCapabilities.globalCapabilities
        and: 'validate capabilityAttributes is not empty'
            !capabilityAttributes.isEmpty()
        and: 'validate cbs targetTypes from capabilityAttributes'
            capabilityAttributes.get('cbs') == result

        where:
            function                               || result
            'BSCRECORDINGS_SubscriptionAttributes' || false
            'MTR_SubscriptionAttributes'           || true
    }

    @Unroll
    def 'when valid #function targetfunction is passed to fetch the capabilties for all subscription supported target types it should return capabilities with supported target type'() {

        when: 'call capabilitiesByFunction to read capability model for #function'
            Response response = capabilityResource.getCapabilities(function, null, null)
            PmCapabilities capabilities = response.entity

        then: 'capabilities should be returned'
            PmCapabilityInformation capabilityAttributes = capabilities.targetTypes.get(0)
        and: 'validate capabilityAttributes is not null'
            capabilityAttributes != null
        and: 'validate importExportSubscriptionApplicable targetTypes from capabilityAttributes'
            capabilityAttributes.capabilities.get('importExportSubscriptionApplicable') == result

        where:
            function                               || result
            'BSCRECORDINGS_SubscriptionAttributes' || true
            'MTR_SubscriptionAttributes'           || true
    }

    @Unroll
    def 'when valid #function targetfunction, return capability value for given #targetType'() {

        given: 'define 3 capability name'
            final String capabilityName = 'cbs,importExportSubscriptionApplicable,ropDropDownDisabled'
        when: 'call getCapabilityForTargetTypeByFunction to read capability value from #function'
            Response response = capabilityResource.getCapabilities(function, capabilityName, targetType)
            PmCapabilities capabilities = response.entity

        then: 'capabilities should be returned'
            PmCapabilityInformation capabilityAttributes = capabilities.targetTypes.get(0)
            capabilities.targetTypes.size() == 1
        and: 'validate capabilityAttributes is not null'
            capabilityAttributes != null
        and: 'validate importExportSubscriptionApplicable targetTypes from capabilityAttributes'
            capabilityAttributes.capabilities.get('cbs') == capabilityAvailable

        where:
            function                               | targetType  || capabilityAvailable
            'BSCRECORDINGS_SubscriptionAttributes' | 'ERBS'      || false
            'MTR_SubscriptionAttributes'           | 'RadioNode' || true
    }

    @Unroll
    def 'when valid #function targetfunction, return capability value for given multiple #targetType'() {

        given: 'define 3 capability name'
            final String capabilityName = 'cbs,importExportSubscriptionApplicable,ropDropDownDisabled'
        when: 'call getCapabilityForTargetTypeByFunction to read capability value from #function'
            Response response = capabilityResource.getCapabilities(function, capabilityName, targetType)
            PmCapabilities capabilities = response.entity

        then: 'capabilities should be returned'
            PmCapabilityInformation capabilityAttributes = capabilities.targetTypes.get(0)
            capabilities.targetTypes.size() == 2
        and: 'validate capabilityAttributes is not null'
            capabilityAttributes != null
        and: 'validate importExportSubscriptionApplicable targetTypes from capabilityAttributes'
            capabilityAttributes.capabilities.get('cbs') == capabilityAvailable

        where:
            function                               | targetType       || capabilityAvailable
            'BSCRECORDINGS_SubscriptionAttributes' | 'ERBS,RadioNode' || false
            'MTR_SubscriptionAttributes'           | 'RadioNode,RNC'  || true
    }

    @Unroll
    def 'when valid #function targetfunction with #targetType called, return capability value for given #targetType'() {

        when: 'call getCapabilitiesByFunctionAndTargetType to read capability value for #targetType'
            Response response = capabilityResource.getCapabilities(function, null, targetType)
            PmCapabilities capabilities = response.entity

        then: 'capabilities should be returned'
            PmCapabilityInformation capabilityAttributes = capabilities.targetTypes.get(0)
            capabilities.targetTypes.size() == 1
        and: 'validate capabilityAttributes is not null'
            capabilityAttributes != null
        and: 'validate importExportSubscriptionApplicable targetTypes from capabilityAttributes'
            capabilityAttributes.capabilities.get('cbs') == capabilityAvailable

        where:
            function                               | targetType  || capabilityAvailable
            'BSCRECORDINGS_SubscriptionAttributes' | 'ERBS'      || false
            'STATISTICAL_SubscriptionAttributes'   | 'RadioNode' || true
    }

    def 'when valid targetFunction and valid capabilityName and null targetType as a request is sent, then global capability response is returned'(){
        when: 'call to getCapabilities with a null targetType'
            Response response = capabilityResource.getCapabilities(function,capabilityName,targetType)

        then: 'the returned response entity is a PmGlobalCapabilities type'
            PmCapabilities.name == response.entity.class.name

        and: 'returns the status code OK'
            response.status == Response.Status.OK.statusCode
        where:
            function                               | targetType  | capabilityName
            'STATISTICAL_SubscriptionAttributes'   | null        | 'cbs,importExportSubscriptionApplicable,ropDropDownDisabled'
            'BSCRECORDINGS_SubscriptionAttributes' | null        | 'cbs,importExportSubscriptionApplicable,ropDropDownDisabled'

    }
}
