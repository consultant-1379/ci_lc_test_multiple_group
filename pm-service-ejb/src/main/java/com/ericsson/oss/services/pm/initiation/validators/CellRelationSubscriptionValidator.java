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

package com.ericsson.oss.services.pm.initiation.validators;

import static com.ericsson.oss.services.pm.initiation.constants.ApplicationMessages.INVALID_SUBSCRIPTION_CELLS_TO_ACTIVATE;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import org.slf4j.Logger;

import com.ericsson.oss.pmic.dto.subscription.CellRelationSubscription;
import com.ericsson.oss.pmic.dto.subscription.cdts.CellInfo;
import com.ericsson.oss.pmic.dto.subscription.enums.SubscriptionType;
import com.ericsson.oss.services.pm.initiation.validators.annotation.SubscriptionValidatorQualifier;
import com.ericsson.oss.services.pm.services.exception.PfmDataException;
import com.ericsson.oss.services.pm.services.exception.ValidationException;

/**
 * This class validates Cell/Cell Relation Subscription
 */
@ApplicationScoped
@SubscriptionValidatorQualifier(subscriptionType = SubscriptionType.CELLRELATION)
public class CellRelationSubscriptionValidator extends StatisticalSubscriptionParentValidator<CellRelationSubscription> {
    @Inject
    private Logger logger;

    @Inject
    private MoLimitValidator moLimitValidator;


    @Override
    public void validateImport(final CellRelationSubscription subscription) throws ValidationException, PfmDataException {
        super.validateImport(subscription);

        final List<CellInfo> subscriptionCells = subscription.getCells();

        if (subscriptionCells == null || subscriptionCells.isEmpty()) {
            return;
        }

        if (subscription.getNodes().isEmpty()) {
            logger.error("No nodes found. Cells cannot exist without Nodes");
            throw new ValidationException("Cells needs nodes");
        }
    }

    @Override
    public void validateActivation(final CellRelationSubscription subscription) throws ValidationException {
        logger.debug("validating the subscription before activating {}", subscription);
        super.validateActivation(subscription);
        transformNodeCellMap(subscription);
    }

    private void transformNodeCellMap(final CellRelationSubscription subscription) throws ValidationException {
        if (subscription.getCells().isEmpty()) {
            logger.error("The cells of the subscription {} is empty.", subscription.getName());
            throw new ValidationException(String.format(INVALID_SUBSCRIPTION_CELLS_TO_ACTIVATE, subscription.getName()));
        } else {
            final Map<String, List<String>> cellMap = new HashMap<>();
            for (final CellInfo cell : subscription.getCells()) {
                List<String> cells = new ArrayList<>();
                if (cellMap.containsKey(cell.getNodeName())) {
                    cells = cellMap.get(cell.getNodeName());
                }
                cells.add(cell.getUtranCellId());
                cellMap.put(cell.getNodeName(), cells);
            }
            moLimitValidator.validateLimit(subscription, cellMap);
        }
    }

}
