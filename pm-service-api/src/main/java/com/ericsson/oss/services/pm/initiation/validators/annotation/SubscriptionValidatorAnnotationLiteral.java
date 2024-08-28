/*
 * ------------------------------------------------------------------------------
 *  ********************************************************************************
 *  * COPYRIGHT Ericsson  2017
 *  *
 *  * The copyright to the computer program(s) herein is the property of
 *  * Ericsson Inc. The programs may be used and/or copied only with written
 *  * permission from Ericsson Inc. or in accordance with the terms and
 *  * conditions stipulated in the agreement/contract under which the
 *  * program(s) have been supplied.
 *  *******************************************************************************
 *  *----------------------------------------------------------------------------
 */
package com.ericsson.oss.services.pm.initiation.validators.annotation;

import javax.enterprise.util.AnnotationLiteral;

import com.ericsson.oss.pmic.dto.subscription.enums.SubscriptionType;

/**
 * The Class SubscriptionValidatorAnnotationLiteralDTO.
 */
public class SubscriptionValidatorAnnotationLiteral extends AnnotationLiteral<SubscriptionValidatorQualifier> implements
        SubscriptionValidatorQualifier {
    private static final long serialVersionUID = 3104827123L;

    private final SubscriptionType subscriptionType;

    /**
     * Instantiates a new subscription validator annotation literal.
     *
     * @param subscriptionType
     *         the subscription type
     */
    public SubscriptionValidatorAnnotationLiteral(final SubscriptionType subscriptionType) {
        this.subscriptionType = subscriptionType;
    }

    @Override
    public SubscriptionType subscriptionType() {
        return subscriptionType;
    }
}
