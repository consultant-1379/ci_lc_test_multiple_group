/*------------------------------------------------------------------------------
 *******************************************************************************
 * COPYRIGHT Ericsson 2019
 *
 * The copyright to the computer program(s) herein is the property of
 * Ericsson Inc. The programs may be used and/or copied only with written
 * permission from Ericsson Inc. or in accordance with the terms and
 * conditions stipulated in the agreement/contract under which the
 * program(s) have been supplied.
 *******************************************************************************
 *----------------------------------------------------------------------------*/

package com.ericsson.oss.services.pm.initiation.schedulers

import org.slf4j.Logger

import com.ericsson.cds.cdi.support.rule.MockedImplementation
import com.ericsson.cds.cdi.support.rule.ObjectUnderTest
import com.ericsson.cds.cdi.support.spock.CdiSpecification
import com.ericsson.oss.pmic.dto.scanner.Scanner
import com.ericsson.oss.services.pm.exception.DataAccessException
import com.ericsson.oss.services.pm.exception.RetryServiceException
import com.ericsson.oss.services.pm.generic.ScannerService
import com.ericsson.oss.services.pm.initiation.task.factories.auditor.ResourceSubscriptionHelper
import com.ericsson.oss.services.pm.services.generic.SubscriptionReadOperationService

class ResourceSubscriptionAuditExceptionSpec extends CdiSpecification {

    @ObjectUnderTest
    ResourceSubscriptionHelper resourceSubscriptionHelper

    @MockedImplementation
    SubscriptionReadOperationService subscriptionReadOperationService

    @MockedImplementation
    ScannerService scannerService

    @MockedImplementation
    Logger logger

    def 'Should not throw exception if an exception is thrown while trying to update a scanner in dps'() {
        given: 'a scanner to update'
            def scanner = new Scanner()
            scanner.fdn = 'anFdn'
            scannerService.saveOrUpdateWithRetry(scanner) >> {throw exception}

        when: 'the resource subscription helper is executed to update scanner in dps'
            resourceSubscriptionHelper.updateSubscriptionIdToZeroAndSetStatusToUnknown(scanner)

        then: 'no exception is thrown'
            noExceptionThrown()
            1 * logger.error('Cannot update Scanner Status to UNKNOWN and SubscriptionID to 0 for scanner {}. Exception message: {}', scanner.fdn,
                    exception.getMessage())
            1 * logger.info('Cannot update Scanner Status to UNKNOWN and SubscriptionID to 0 for scanner [{}].', scanner.fdn, exception)

        where:
            exception << [new DataAccessException('a message'), new RetryServiceException('a message')]

    }
}
