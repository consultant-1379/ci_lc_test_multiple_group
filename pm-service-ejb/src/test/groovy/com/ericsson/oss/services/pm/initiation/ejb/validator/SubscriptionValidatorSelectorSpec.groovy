/*******************************************************************************
 * COPYRIGHT Ericsson 2017
 *
 * The copyright to the computer program(s) herein is the property of
 * Ericsson Inc. The programs may be used and/or copied only with written
 * permission from Ericsson Inc. or in accordance with the terms and
 * conditions stipulated in the agreement/contract under which the
 * program(s) have been supplied.
 ******************************************************************************/
package com.ericsson.oss.services.pm.initiation.ejb.validator

import static com.ericsson.oss.pmic.dto.subscription.enums.SubscriptionType.*

import spock.lang.Unroll

import com.ericsson.cds.cdi.support.rule.ObjectUnderTest
import com.ericsson.oss.pmic.cdi.test.util.SkeletonSpec
import com.ericsson.oss.pmic.dto.subscription.Subscription
import com.ericsson.oss.services.pm.initiation.constants.ApplicationMessages
import com.ericsson.oss.services.pm.initiation.validator.ValidateSubscription
import com.ericsson.oss.services.pm.initiation.validators.*
import com.ericsson.oss.services.pm.services.exception.ValidationException

class SubscriptionValidatorSelectorSpec extends SkeletonSpec {

    @ObjectUnderTest
    private SubscriptionValidatorSelector subscriptionValidatorSelector;

    private ValidateSubscription<Subscription> subscriptionValidator

    @Unroll
    def "When selecting a validator for SubscriptionType= #subscriptionType then it should return #validatorClass.getSimpleName() and it should NOT throw Exception"() {
        when: "subscription validator is requested"

        subscriptionValidator = subscriptionValidatorSelector.getInstance(subscriptionType)

        then: "it should return the correct validator"
        noExceptionThrown()
        subscriptionValidator.class == validatorClass

        where:

        subscriptionType    | validatorClass
        STATISTICAL         | StatisticalSubscriptionValidator
        CELLTRACE           | CellTraceSubscriptionValidator
        UETRACE             | UetraceSubscriptionValidator
        EBM                 | EbmSubscriptionValidator
        CONTINUOUSCELLTRACE | ContinuousCellTraceSubscriptionValidator
        CTUM                | CtumSubscriptionValidator
        UETR                | UetrSubscriptionValidator
        GPEH                | GpehSubscriptionValidator
        MOINSTANCE          | MoinstanceSubscriptionValidator
        CELLTRAFFIC         | CellTrafficSubscriptionValidator
        CELLRELATION        | CellRelationSubscriptionValidator
        BSCRECORDINGS       | BscRecordingsSubscriptionValidator
        MTR                 | MtrSubscriptionValidator
        RPMO                | RpmoSubscriptionValidator
        RTT                 | RttSubscriptionValidator
    }

    @Unroll
    def "When selecting a validator for SubscriptionType=#subscriptionType and it validator don't exist then it should  throw InvalidSubscriptionException"() {
        when: "subscription without validator is requested"
        subscriptionValidator = subscriptionValidatorSelector.getInstance(subscriptionType)

        then: "exception should be thrown"
        def exception = thrown(ValidationException)
        exception.message == ApplicationMessages.NOTVALID_SUBSCRIPTION_TYPE

        where:
        subscriptionType << [EBS, EVENTS]
    }
}
