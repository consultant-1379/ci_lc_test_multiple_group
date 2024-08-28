/*------------------------------------------------------------------------------
 *******************************************************************************
 * COPYRIGHT Ericsson 2016
 *
 * The copyright to the computer program(s) herein is the property of
 * Ericsson Inc. The programs may be used and/or copied only with written
 * permission from Ericsson Inc. or in accordance with the terms and
 * conditions stipulated in the agreement/contract under which the
 * program(s) have been supplied.
 *******************************************************************************
 *----------------------------------------------------------------------------*/
package com.ericsson.oss.services.pm.testsuite.steps.rest;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;

import org.junit.Assert;

import com.ericsson.oss.cucumber.arquillian.api.CucumberGlues;
import com.ericsson.oss.services.test.rest.step.SharedRestSteps;

import cucumber.api.java.en.Then;

@CucumberGlues
public class PmServiceRestStep extends SharedRestSteps {

    public static final String OID_REGEX = "(\\.?\\d+)*";

    @Then("^Response contains (\\d+) \"([^\"]*)\"$")
    public void verifyEventCount(final int numOfEvents, final String pathQuery) {
        final List<String> events = response.path(pathQuery);
        Assert.assertEquals(numOfEvents, events.size());
    }

    @Then("^Response contains (\\d+) Unique \"([^\"]*)\"$")
    public void verifyUniqueEventGroupCount(final int numOfEventGroups, final String pathQuery) {
        final List<String> groupNames = response.path(pathQuery);
        final Collection<String> uniqueGroupNames = new HashSet<>(groupNames);
        Assert.assertEquals(numOfEventGroups, uniqueGroupNames.size());
    }

    @Then("^Response contains (\\d+) \"([^\"]*)\" different from OID$")
    public void verifyEventCountAndDifferenceFromOid(final int numOfEvents, final String pathQuery) {
        final List<String> events = response.path(pathQuery);
        Assert.assertEquals(numOfEvents, events.size());
        for (final String event : events) {
            Assert.assertFalse(event.matches(OID_REGEX));
        }
    }

    @Then("^Response contains (\\d+) Unique \"([^\"]*)\" different from OID$")
    public void verifyUniqueEventGroupCountAndDifferenceFromOid(final int numOfEventGroups, final String pathQuery) {
        final List<String> groupNames = response.path(pathQuery);
        final Collection<String> uniqueGroupNames = new HashSet<>(groupNames);
        Assert.assertEquals(numOfEventGroups, uniqueGroupNames.size());
        for (final String uniqueGroupName : uniqueGroupNames) {
            Assert.assertFalse(uniqueGroupName.matches(OID_REGEX));
        }
    }
}
