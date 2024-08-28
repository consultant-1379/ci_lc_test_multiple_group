/*------------------------------------------------------------------------------
 *******************************************************************************
 * COPYRIGHT Ericsson 2018
 *
 * The copyright to the computer program(s) herein is the property of
 * Ericsson Inc. The programs may be used and/or copied only with written
 * permission from Ericsson Inc. or in accordance with the terms and
 * conditions stipulated in the agreement/contract under which the
 * program(s) have been supplied.
 *******************************************************************************
 *----------------------------------------------------------------------------*/

package com.ericsson.oss.services.pm.initiation.task.factories.deactivation;

import javax.enterprise.context.ApplicationScoped;

import com.ericsson.oss.pmic.dto.subscription.CellRelationSubscription;
import com.ericsson.oss.pmic.dto.subscription.StatisticalSubscription;
import com.ericsson.oss.services.pm.initiation.task.factories.MediationTaskRequestFactory;
import com.ericsson.oss.services.pm.initiation.task.factories.deactivation.qualifier.DeactivationTaskRequest;

/**
 * The CellRelation subscription deactivation task request factory.
 */
@DeactivationTaskRequest(subscriptionType = CellRelationSubscription.class)
@ApplicationScoped
public class CellRelationSubscriptionDeactivationTaskRequestFactory extends StatisticalSubscriptionDeactivationTaskRequestFactory
        implements MediationTaskRequestFactory<StatisticalSubscription> {

}
