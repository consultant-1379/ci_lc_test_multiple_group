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

package com.ericsson.oss.services.pm.initiation.scanner.master;

import java.util.List;
import java.util.Set;
import javax.inject.Inject;

import com.ericsson.oss.pmic.api.scanner.master.dependency.ExternalDependencies;
import com.ericsson.oss.pmic.api.scanner.master.duplicated.exceptions.InvalidSubscriptionException;
import com.ericsson.oss.pmic.dto.node.Node;
import com.ericsson.oss.pmic.dto.scanner.Scanner;
import com.ericsson.oss.pmic.dto.subscanner.PmSubScanner;
import com.ericsson.oss.pmic.dto.subscription.Subscription;
import com.ericsson.oss.pmic.dto.subscription.enums.SubscriptionType;
import com.ericsson.oss.pmic.subscription.capability.SubscriptionCapabilityReader;
import com.ericsson.oss.services.pm.cache.PmFunctionEnabledWrapper;
import com.ericsson.oss.services.pm.ebs.utils.EbsConfiguration;
import com.ericsson.oss.services.pm.exception.DataAccessException;
import com.ericsson.oss.services.pm.exception.RetryServiceException;
import com.ericsson.oss.services.pm.generic.NodeService;
import com.ericsson.oss.services.pm.generic.PmSubScannerService;
import com.ericsson.oss.services.pm.generic.ScannerService;
import com.ericsson.oss.services.pm.initiation.config.listener.ConfigurationChangeListener;
import com.ericsson.oss.services.pm.initiation.notification.events.InitiationEventType;
import com.ericsson.oss.services.pm.initiation.processor.activation.ActivationProcessor;
import com.ericsson.oss.services.pm.initiation.scanner.operation.ScannerOperation;
import com.ericsson.oss.services.pm.services.generic.SubscriptionReadOperationService;

/**
 * Implementation of ExternalDependencies interface.
 */
public class ExternalDependenciesImpl implements ExternalDependencies {

    @Inject
    private NodeService nodeService;
    @Inject
    private ScannerService scannerService;
    @Inject
    private ScannerOperation scannerOperation;
    @Inject
    private ActivationProcessor activationProcessor;
    @Inject
    private SubscriptionReadOperationService subscriptionReadOperationService;
    @Inject
    private PmFunctionEnabledWrapper pmFunctionCache;
    @Inject
    private EbsConfiguration ebsConfiguration;
    @Inject
    private PmSubScannerService pmSubScannerService;
    @Inject
    private ConfigurationChangeListener configurationChangeListener;
    @Inject
    private SubscriptionCapabilityReader pmCapabilityReader;
    @Inject
    private SubscriptionManager subscriptionManager;

    @Override
    public boolean isPmFunctionEnabled(final String nodeFdn) {
        return pmFunctionCache.isPmFunctionEnabled(nodeFdn);
    }

    @Override
    public Node findOneByFdn(final String fdn) throws DataAccessException {
        return nodeService.findOneByFdn(fdn);
    }

    @Override
    public void activate(final List<Node> nodes, final Subscription subscription)
            throws DataAccessException {
        activationProcessor.activate(nodes, subscription, InitiationEventType.SCANNER_MASTER_ACTIVATE_NODE);
    }

    @Override
    public List<PmSubScanner> findAllByParentScannerFdn(final String scannerFdn) throws DataAccessException {
        return pmSubScannerService.findAllByParentScannerFdn(scannerFdn);
    }

    @Override
    public List<Scanner> findAllByNodeFdn(final Iterable<String> nodeFdns) throws DataAccessException {
        return scannerService.findAllByNodeFdn(nodeFdns);
    }

    @Override
    public void deleteById(final Long poId) throws DataAccessException {
        pmSubScannerService.deleteById(poId);
    }

    @Override
    public boolean isEbsStreamClusterDeployed() {
        return ebsConfiguration.isEbsOrAsrStreamClusterDeployed();
    }

    @Override
    public Boolean getPmMigrationEnabled() {
        return configurationChangeListener.getPmMigrationEnabled();
    }

    @Override
    public String getSystemDefinedSubscriptionNameFromCapability(final String subscriptionName) {
        return pmCapabilityReader.getSystemDefinedSubscriptionNameFromCapability(subscriptionName);
    }

    @Override
    public void saveOrUpdate(final Scanner object) throws DataAccessException {
        scannerService.saveOrUpdate(object);
    }

    @Override
    public void deleteScannerFromTheNode(final String scannerFdn, final String scannerId) {
        scannerOperation.deleteScannerFromTheNode(scannerFdn, scannerId);
    }

    @Override
    public void resumeScannerOnTheNode(final String nodeFdn, final Long scannerPoId) {
        scannerOperation.resumeScannerOnTheNode(nodeFdn, scannerPoId);
    }

    @Override
    public void suspendScannerOnTheNode(final String scannerFdn, final Long scannerPoId) {
        scannerOperation.suspendScannerOnTheNode(scannerFdn, scannerPoId);
    }

    @Override
    public void suspendScannerOnTheNode(final String scannerFdn) {
        scannerOperation.suspendScannerOnTheNode(scannerFdn);
    }

    @Override
    public void saveOrUpdateWithRetry(final Scanner scanner) throws DataAccessException {
        try {
            scannerService.saveOrUpdateWithRetry(scanner);
        } catch (final RetryServiceException e) {
            throw new DataAccessException(e);
        }
    }

    @Override
    public void deleteWithRetry(final String scannerFdn) throws DataAccessException {
        try {
            scannerService.deleteWithRetry(scannerFdn);
        } catch (final RetryServiceException e) {
            throw new DataAccessException(e);
        }
    }

    @Override
    public Subscription findOneById(final Long subscriptionId, final boolean loadAssociations) throws DataAccessException {
        return subscriptionReadOperationService.findOneById(subscriptionId, loadAssociations);
    }

    @Override
    public Subscription getSubscription(final String subscriptionName, final SubscriptionType subscriptionType)
            throws InvalidSubscriptionException, DataAccessException {
        try {
            return subscriptionManager.getSubscriptionWrapper(subscriptionName, subscriptionType).getSubscription();
        } catch (final com.ericsson.oss.services.pm.services.exception.InvalidSubscriptionException e) {
            throw new InvalidSubscriptionException(e.getMessage(), e);
        }
    }

    @Override
    public Subscription getSubscription(final Long subscriptionId) throws InvalidSubscriptionException, DataAccessException {
        try {
            return subscriptionManager.getSubscriptionWrapperById(subscriptionId).getSubscription();
        } catch (final com.ericsson.oss.services.pm.services.exception.InvalidSubscriptionException e) {
            throw new InvalidSubscriptionException(e.getMessage(), e);
        } catch (final RetryServiceException e) {
            throw new DataAccessException(e);
        }
    }

    @Override
    public Set<String> getSubscriptionNodes(final Long subscriptionId) throws InvalidSubscriptionException, DataAccessException {
        try {
            return subscriptionManager.getSubscriptionWrapperById(subscriptionId).getAllNodeFdns();
        } catch (final com.ericsson.oss.services.pm.services.exception.InvalidSubscriptionException e) {
            throw new InvalidSubscriptionException(e.getMessage(), e);
        } catch (final RetryServiceException e) {
            throw new DataAccessException(e);
        }
    }
}
