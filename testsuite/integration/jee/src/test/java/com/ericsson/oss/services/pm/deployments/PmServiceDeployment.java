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

package com.ericsson.oss.services.pm.deployments;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;

import org.jboss.arquillian.container.test.api.Testable;
import org.jboss.arquillian.protocol.servlet.arq514hack.descriptors.api.application.ApplicationDescriptor;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.Node;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.Asset;
import org.jboss.shrinkwrap.api.asset.EmptyAsset;
import org.jboss.shrinkwrap.api.container.ResourceContainer;
import org.jboss.shrinkwrap.api.spec.EnterpriseArchive;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.jboss.shrinkwrap.descriptor.api.Descriptors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ericsson.oss.services.pm.collection.instrumentation.FileCollectionInstrumentationIntTest;
import com.ericsson.oss.services.pm.collection.recovery.ScheduledRecoveryInTest;
import com.ericsson.oss.services.pm.collection.scheduling.FileCollectionEventForwardingInTest;
import com.ericsson.oss.services.pm.collection.scheduling.FileCollectionSchedulingInTest;
import com.ericsson.oss.services.pm.dao.SubscriptionDaoIt;
import com.ericsson.oss.services.pm.dao.SubscriptionDaoPerformanceIt;
import com.ericsson.oss.services.pm.deletion.schedulers.FileDeletionHelper;
import com.ericsson.oss.services.pm.initiation.integration.ArquillianSuiteTestWatcher;
import com.ericsson.oss.services.pm.initiation.integration.InputBaseArquillian;
import com.ericsson.oss.services.pm.initiation.integration.SubscriptionOperationMessageSender;
import com.ericsson.oss.services.pm.initiation.integration.SubscriptionTestUtils;
import com.ericsson.oss.services.pm.initiation.integration.subscription.*;
import com.ericsson.oss.services.pm.initiation.node.data.NodeCreationHelperBean;
import com.ericsson.oss.services.pm.initiation.node.data.NodeData;
import com.ericsson.oss.services.pm.initiation.node.data.NodeDataReader;
import com.ericsson.oss.services.pm.initiation.scanner.data.PMICScannerData;
import com.ericsson.oss.services.pm.initiation.scanner.data.ScannerCreationHelperBean;
import com.ericsson.oss.services.pm.initiation.scanner.migration.ScannerMigrationTest;
import com.ericsson.oss.services.pm.initiation.scanner.polling.ScannerMasterTest;
import com.ericsson.oss.services.pm.initiation.scanner.polling.ScannerPollingSchedulerITInTest;
import com.ericsson.oss.services.pm.initiation.subscription.data.SubscriptionAttributes;
import com.ericsson.oss.services.pm.initiation.subscription.data.SubscriptionDataReader;
import com.ericsson.oss.services.pm.initiation.subscription.data.SubscriptionInfo;
import com.ericsson.oss.services.pm.initiation.timer.FrequencyGeneratorForTests;
import com.ericsson.oss.services.pm.initiation.timer.TimeGeneratorForTests;
import com.ericsson.oss.services.pm.initiation.timer.delay.AddOrRemoveNodesFromActiveSubscriptionITTest;
import com.ericsson.oss.services.pm.initiation.timer.delay.PMICScannerUpdateNotificationITTest;
import com.ericsson.oss.services.pm.integration.RestBaseArquillian;
import com.ericsson.oss.services.pm.integration.test.constants.TestConstants;
import com.ericsson.oss.services.pm.listeners.DpsNotificationListener;
import com.ericsson.oss.services.pm.listeners.FileCollectionEventForwardingListener;
import com.ericsson.oss.services.pm.listeners.IntegrationRequestResponseHolder;
import com.ericsson.oss.services.pm.listeners.IntegrationRequestResponseListener;
import com.ericsson.oss.services.pm.listeners.MediationTaskRequestListener;
import com.ericsson.oss.services.pm.test.requests.PmServiceRequestsTypes;
import com.ericsson.oss.services.pm.test.requests.SubscriptionOperationRequest;

/**
 * This class uses the pm-service ear and just adds extra classes for helping tests.
 *
 * @author ekamkal
 */
public class PmServiceDeployment {

    private static final Logger logger = LoggerFactory.getLogger(PmServiceDeployment.class);

    public static final Archive<?> createPmServiceDeploymentWithTestBeans(final String testName, final Class<?>... classes) {
        logger.debug("******Creating pm-service ear for test******");

        final File ear = Artifact.resolveArtifactWithoutDependencies(Artifact.COM_ERICSSON_OSS_PM_SERVICE_EAR);
        final EnterpriseArchive pmServiceearForTest = ShrinkWrap.createFromZipFile(EnterpriseArchive.class, ear);

        pmServiceearForTest.addAsLibraries(Artifact.resolveArtifactWithDependencies(Artifact.ORG_APACHE_POI));
        pmServiceearForTest.addAsLibraries(Artifact.resolveArtifactWithDependencies(Artifact.REST_ASSURED_API));
        pmServiceearForTest.addAsLibraries(Artifact.resolveArtifactWithDependencies(Artifact.AWAITILITY));
        pmServiceearForTest.addAsLibraries(Artifact.resolveArtifactWithDependencies(Artifact.SDK_RESOURCE_API));
        pmServiceearForTest.addAsLibraries(Artifact.resolveArtifactWithDependencies(Artifact.GSON));
        pmServiceearForTest.addAsLibraries(Artifact.resolveArtifactWithDependencies(Artifact.SHARED_SERVICE_API));

        final WebArchive testWar = createCustomizedTestArchive();
        testWar.addClasses(classes);

        pmServiceearForTest.addAsModule(testWar);
        createCustomApplicationXmlFile(pmServiceearForTest, "PmicIntegrationTest");

        logger.debug("******Created from maven artifact with coordinates {} ******", Artifact.COM_ERICSSON_OSS_PM_SERVICE_EAR);
        return pmServiceearForTest;
    }

    private static WebArchive createCustomizedTestArchive() {
        final WebArchive testApp = Testable.archiveToTest(ShrinkWrap.create(WebArchive.class, "PmicIntegrationTest.war"));
        testApp.addAsManifestResource(EmptyAsset.INSTANCE, "beans.xml");

        testApp.addAsLibraries(Artifact.resolveArtifactWithDependencies(Artifact.REST_ASSURED_API));
        testApp.addClass(CBSubscriptionITTest.class);
        testApp.addClass(SubscriptionOperationMessageSender.class);
        testApp.addClass(SubscriptionTestUtils.class);
        testApp.addClass(NodeDataReader.class);
        testApp.addClass(FileDeletionHelper.class);
        testApp.addClass(NodeData.class);
        testApp.addClass(NodeCreationHelperBean.class);
        testApp.addClass(IntegrationRequestResponseListener.class);
        testApp.addClass(SubscriptionOperationRequest.class);
        testApp.addClass(IntegrationRequestResponseHolder.class);
        testApp.addClass(SubscriptionAttributes.class);
        testApp.addClass(ScannerCreationHelperBean.class);
        testApp.addClass(SubscriptionDataReader.class);
        testApp.addClass(SubscriptionInfo.class);
        testApp.addClass(PmServiceRequestsTypes.class);
        testApp.addClass(PMICScannerData.class);
        testApp.addClass(FileCollectionInstrumentationIntTest.class);
        testApp.addClass(FileCollectionSchedulingInTest.class);
        testApp.addClass(MediationTaskRequestListener.class);
        testApp.addClass(ScheduledRecoveryInTest.class);
        testApp.addClass(PMCountersFromModelServiceITinTest.class);
        testApp.addClass(ScannerPollingSchedulerITInTest.class);
        testApp.addClass(ScannerMasterTest.class);
        testApp.addClass(DpsNotificationListener.class);
        testApp.addClass(TestConstants.class);
        testApp.addClass(SubscriptionInitiatorITTest.class);
        testApp.addClass(TimeGeneratorForTests.class);
        testApp.addClass(FrequencyGeneratorForTests.class);
        testApp.addClass(UpdateDeleteSubscriptionFailureCaseITTest.class);
        testApp.addClass(PMICScannerUpdateNotificationITTest.class);
        testApp.addClass(CellTraceSubscriptionITTest.class);
        testApp.addClass(CellTraceSubscriptionNegativeITTest.class);
        testApp.addClass(FileCollectionEventForwardingListener.class);
        testApp.addClass(FileCollectionEventForwardingInTest.class);
        testApp.addClass(ScannerMigrationTest.class);
        testApp.addClass(EventsFromModelServiceITTest.class);
        testApp.addClass(ArquillianSuiteTestWatcher.class);
        testApp.addClass(UeTraceSubscriptionITTest.class);
        testApp.addClass(UetrSubscriptionITTest.class);
        testApp.addClass(AddOrRemoveNodesFromActiveCCTRSubscriptionITTest.class);
        testApp.addClass(ContinuousCelltraceSubscription5GSITTest.class);
         testApp.addClass(AddOrRemoveNodesFromActiveSubscriptionITTest.class);
        testApp.addClass(DeleteNodeFromEnmITTest.class);
        testApp.addClass(SubscriptionDaoPerformanceIt.class);
        testApp.addClass(SubscriptionDaoIt.class);
        testApp.addPackage(InputBaseArquillian.class.getPackage());
        testApp.addPackages(true, RestBaseArquillian.class.getPackage());

        testApp.addAsDirectories("/subscription_data/");
        testApp.addAsDirectories("/data/subscription/");
        for (final File file : Artifact.SUBSCRIPTION_DATA) {
            logger.info("Adding resource {} to archive.", file);
            testApp.addAsResource(file, "/subscription_data/" + file.getName());
        }

        for (final File file : new File("src/test/resources/subscription/data/").listFiles()) {
            logger.info("Adding resource {} to archive.", file);
            testApp.addAsResource(file, "/data/subscription/" + file.getName());
        }
        addFilesAsResources(testApp, Artifact.SUBSCRIPTION_JSON_FILES);
        addFilesAsResources(testApp, Artifact.NODE_JSON_FILES);
        return testApp;
    }

    private static void createCustomApplicationXmlFile(final EnterpriseArchive serviceEar, final String webModuleName) {

        final Node node = serviceEar.get("META-INF/application.xml");
        ApplicationDescriptor desc = Descriptors.importAs(ApplicationDescriptor.class).fromStream(node.getAsset().openStream());

        desc.webModule(webModuleName + ".war", webModuleName);
        final String descriptorAsString = desc.exportAsString();

        serviceEar.delete(node.getPath());
        desc = Descriptors.importAs(ApplicationDescriptor.class).fromString(descriptorAsString);

        final Asset asset = new Asset() {
            @Override
            public InputStream openStream() {
                final ByteArrayInputStream bi = new ByteArrayInputStream(descriptorAsString.getBytes());
                return bi;
            }
        };
        serviceEar.addAsManifestResource(asset, "application.xml");
    }

    public static void addFilesAsResources(final ResourceContainer<?> rc, final String folder) {
        addFilesAsResources(rc, "", folder);
    }

    public static void addFilesAsResources(final ResourceContainer<?> rc, final String prefix, final String folder) {
        try {
            final File dir = new File(folder);
            if (dir != null) {

                final File[] files = dir.listFiles();
                if (files != null) {
                    for (final File file : dir.listFiles()) {
                        if (file.isFile()) {
                            logger.info("Adding file \"{}\" as resource: \"{}\".", file.getAbsolutePath(), prefix + file.getName());
                            rc.addAsResource(file, prefix + file.getName());
                        } else if (file.isDirectory()) {
                            addFilesAsResources(rc, file.getName() + "/", file.getAbsolutePath());
                        }
                    }
                }
            }
        } catch (final Exception e) {
            logger.warn(e.getMessage());
        }
    }
}
