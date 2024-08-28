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

package com.ericsson.oss.services.pm.initiation;

import java.util.HashSet;
import java.util.Set;
import javax.ws.rs.core.Application;

import com.ericsson.oss.services.pm.initiation.common.exceptionmapper.GlobalExceptionMapper;
import com.ericsson.oss.services.pm.initiation.common.exceptionmapper.SecurityViolationExceptionMapper;
import com.ericsson.oss.services.pm.initiation.enodeb.pmprocess.resource.PmProcessReportResource;
import com.ericsson.oss.services.pm.initiation.enodeb.servertime.resource.ServerTimeResource;
import com.ericsson.oss.services.pm.initiation.enodeb.statistics.resource.PMChartUIResource;
import com.ericsson.oss.services.pm.initiation.enodeb.subscription.resource.PmCapabilityResource;
import com.ericsson.oss.services.pm.initiation.enodeb.subscription.resource.SubscriptionComponentUIResource;
import com.ericsson.oss.services.pm.initiation.enodeb.subscription.resource.SubscriptionConfigResource;
import com.ericsson.oss.services.pm.initiation.enodeb.subscription.resource.SubscriptionResource;
import com.ericsson.oss.services.pm.license.LicenseResource;

/**
 * Pmi application.
 */
public class PMIApplication extends Application {
    private final Set<Object> resourceObjects = new HashSet<>();
    private final Set<Class<?>> resourceClasses = new HashSet<>();

    /**
     * Instantiates a new Pmi application.
     */
    public PMIApplication() {
        resourceClasses.add(SubscriptionResource.class);
        resourceClasses.add(SubscriptionComponentUIResource.class);
        resourceClasses.add(PMChartUIResource.class);
        resourceClasses.add(PmProcessReportResource.class);
        resourceClasses.add(ServerTimeResource.class);
        resourceClasses.add(SubscriptionConfigResource.class);
        resourceClasses.add(LicenseResource.class);
        resourceClasses.add(PmCapabilityResource.class);
    }

    @Override
    public Set<Class<?>> getClasses() {
        final Set<Class<?>> classes = new HashSet<>(resourceClasses);
        classes.add(SecurityViolationExceptionMapper.class);
        classes.add(GlobalExceptionMapper.class);
        return classes;
    }

    @Override
    public Set<Object> getSingletons() {
        return resourceObjects;
    }
}
