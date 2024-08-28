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

package com.ericsson.oss.services.pm.initiation.integration;

import static org.awaitility.Awaitility.await;
import static org.junit.Assert.assertEquals;

import static com.ericsson.oss.services.pm.initiation.subscription.data.SubscriptionAttributes.ADMIN_STATE;
import static com.ericsson.oss.services.pm.initiation.subscription.data.SubscriptionAttributes.ID;
import static com.ericsson.oss.services.pm.initiation.subscription.data.SubscriptionAttributes.TASK_STATUS;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.nio.file.DirectoryStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import javax.annotation.Resource;
import javax.inject.Inject;
import javax.jms.Queue;

import org.apache.commons.io.FileUtils;
import org.awaitility.Duration;
import org.jboss.arquillian.container.test.api.OperateOnDeployment;
import org.jboss.arquillian.junit.InSequence;
import org.junit.Rule;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ericsson.oss.pmic.dto.subscription.enums.AdministrationState;
import com.ericsson.oss.pmic.dto.subscription.enums.SubscriptionType;
import com.ericsson.oss.pmic.dto.subscription.enums.TaskStatus;
import com.ericsson.oss.services.pm.deletion.schedulers.FileDeletionHelper;
import com.ericsson.oss.services.pm.initiation.config.listener.DeploymentReadyConfigurationListener;
import com.ericsson.oss.services.pm.initiation.model.resource.PMICScannerInfo;
import com.ericsson.oss.services.pm.initiation.model.resource.PMICScannerStatus;
import com.ericsson.oss.services.pm.initiation.scanner.data.ScannerCreationHelperBean;
import com.ericsson.oss.services.pm.initiation.subscription.data.SubscriptionAttributes;
import com.ericsson.oss.services.pm.initiation.util.constants.TimeConstants;
import com.ericsson.oss.services.pm.integration.test.constants.TestConstants;
import com.ericsson.oss.services.pm.listeners.DpsNotificationListener;
import com.ericsson.oss.services.pm.listeners.IntegrationRequestResponseHolder;
import com.ericsson.oss.services.pm.listeners.MediationTaskRequestListener;
import com.ericsson.oss.services.pm.test.requests.PmServiceRequestsTypes;
import com.ericsson.oss.services.pm.test.requests.SubscriptionOperationRequest;

/**
 * This is base class for all it has common tests for DPS deployments and DPS operations. All test cases must extend this class so that DPS is
 * deployed before test case starts.
 * <p/>
 * In Test class priority of test cases must start from 3 as 1 & 2 are reserved for mock data access delegate and dps deployment.
 *
 * @author fenrir/Dynamo
 */
public class InputBaseArquillian implements TestConstants {

    public static final String PM_SERVICE_TEST = "pm_service_deployment";
    public static final long ONE_MINUTE_IN_MS = TimeUnit.MINUTES.toMillis(1);
    public final static int DAYS_5 = 5;
    public final static int HOURS_24 = 24;
    public final static int HOURS_6 = 6;
    public final static int MINUTES_60 = 60;
    public final static int MINUTES_30 = 30;
    public final static int MINUTES_15 = 15;
    public final static int FILES_PER_HR = 4;
    public final static int HOURS_3 = 3;
    public final static int ROP = 15;
    protected final static String isStartupSetUpDone = "isStartupSetUpDone";
    private static final Logger LOGGER = LoggerFactory.getLogger(InputBaseArquillian.class);
    @Rule
    public ArquillianSuiteTestWatcher testWatcher = new ArquillianSuiteTestWatcher();
    @Inject
    protected IntegrationRequestResponseHolder testResponseHolder;
    @Inject
    protected MediationTaskRequestListener tasksListener;
    @Inject
    protected DpsNotificationListener dpsNotificationListener;
    @Inject
    protected ScannerCreationHelperBean scannerCreationHelperBean;
    @Resource(mappedName = "java:/queue/SubscriptionoperationsQueue")
    private Queue queue;
    @Inject
    private SubscriptionOperationMessageSender operationSender;
    @Inject
    private DeploymentReadyConfigurationListener deploymentListener;

    @Inject
    private SubscriptionTestUtils testUtils;

    @Test
    @InSequence(1)
    @OperateOnDeployment(PM_SERVICE_TEST)
    public void setupEnvironment() throws Exception {
        if (!Boolean.getBoolean(isStartupSetUpDone)) {
            deleteDiskResource(Paths.get(TEST_DIRECTORY), Paths.get(TEST_SYM_DIRECTORY));
            createExecutableFile();
            createTestFiles(STATISTICAL_FILE_DIRECTORY, DAYS_5 * HOURS_24 * FILES_PER_HR, ROP);
            createTestFiles(STATISTICAL_FILE_SYM_DIRECTORY, DAYS_5 * HOURS_24 * FILES_PER_HR, ROP);
            createTestFiles(CELLTRACE_FILE_DIRECTORY, DAYS_5 * HOURS_24 * FILES_PER_HR, ROP);
            createTestSymlinks(STATISTICAL_FILE_SYM_DIRECTORY, STATISTICAL_SYMLINK_DIRECTORY, 24);
            createTestFiles(CELLTRACE_FILE_SYM_DIRECTORY, DAYS_5 * HOURS_24 * FILES_PER_HR, ROP);
            createTestSymlinks(CELLTRACE_FILE_SYM_DIRECTORY, CELLTRACE_SYMLINK_DIRECTORY, 3);
            createTestFiles(EBM_FILE_DIRECTORY, DAYS_5 * HOURS_24 * FILES_PER_HR, ROP);
            createTestFiles(EBM_FILE_SYM_DIRECTORY, DAYS_5 * HOURS_24 * FILES_PER_HR, ROP);
            createTestSymlinks(EBM_FILE_SYM_DIRECTORY, EBM_SYMLINK_DIRECTORY, 3);
            createTestFiles(CTUM_FILE_DIRECTORY, DAYS_5 * HOURS_24 * FILES_PER_HR, ROP);
            createTestFiles(UE_TRACE_FILE_DIRECTORY, DAYS_5 * HOURS_24 * FILES_PER_HR, ROP);
            System.setProperty(isStartupSetUpDone, "true");
        }
        testResponseHolder.clear();
        tasksListener.setUseStubbedMediation(false);
        deploymentListener.listenForCbsEnabledChanges("true");
    }

    public Map<String, Object> getScheduledInfo(final Date date) {
        final Map<String, Object> attr = new HashMap<String, Object>();
        attr.put(SubscriptionAttributes.START_TIME.name(), date);
        return attr;
    }

    public Map<String, Object> getAdminState(final AdministrationState adminState) {
        final Map<String, Object> attr = new HashMap<String, Object>();
        attr.put(SubscriptionAttributes.ADMIN_STATE.name(), AdministrationState.ACTIVE.name());
        return attr;
    }

    public Map<String, Object> getActivationTime(final Date date) {
        final Map<String, Object> attr = new HashMap<String, Object>();
        attr.put(SubscriptionAttributes.ACTIVATION_TIME.name(), date);
        return attr;
    }

    public Map<String, Object> createScannerAttributesMap(final int ropPeriod, final String subscrptionId, final PMICScannerStatus scannerStatus,
                                                          final String scannerID, final String scannerName, final String processType) {

        final Map<String, Object> scannerAttributes = new HashMap<String, Object>();
        scannerAttributes.put(PMIC_SCANNER_INFO_ATTRIBUTE_NAME, scannerName);
        scannerAttributes.put(PMIC_SCANNER_INFO_ATTRIBUTE_ROP, ropPeriod);
        scannerAttributes.put(PMIC_SCANNER_INFO_ATTRIBUTE_SUB_POID, subscrptionId);
        scannerAttributes.put(PMIC_SCANNER_INFO_ATTRIBUTE_STATUS, scannerStatus.name());
        scannerAttributes.put(PMIC_SCANNER_INFO_ATTRIBUTE_ID, scannerID);
        scannerAttributes.put(PMIC_SCANNER_INFO_ATTRIBUTE_PROCESS_TYPE, processType);
        return scannerAttributes;
    }

    public Map<String, Object> createScannerAttributesMap(final int ropPeriod, final String subscriptionId, final PMICScannerInfo pmicScannerInfo) {
        return createScannerAttributesMap(ropPeriod, subscriptionId, pmicScannerInfo.status, pmicScannerInfo.id, pmicScannerInfo.name,
                pmicScannerInfo.processType);
    }

    public void createTestFiles(final String directory, final int amount, final int timestampInterval) {
        createDirectory(directory);

        final long currentTimeMillis = System.currentTimeMillis();
        for (int i = -amount; i < 0; i++) {
            final Path newFilePath = Paths.get(directory + "fileTest" + i + ".txt");

            if (!Files.exists(newFilePath)) {
                LOGGER.debug("Creating file {}", newFilePath);
                createFile(newFilePath);
                final long newFileModifiedTime = currentTimeMillis + timestampInterval * ONE_MINUTE_IN_MS * i;
                final FileTime fileTime = FileTime.fromMillis(newFileModifiedTime);
                LOGGER.debug("Setting last modified time of file {}, to {}", newFilePath, fileTime);
                setLastModifiedTime(newFilePath, fileTime);
            }
        }
    }

    private void createDirectory(final String directory) {
        final Path path = Paths.get(directory);
        if (!Files.exists(path)) {
            try {
                LOGGER.debug("Creating directory {}", path);
                Files.createDirectories(path);
            } catch (final IOException e) {
                LOGGER.debug("IOException while creating directory {}", e);
            }
        }
    }

    private void createFile(final Path filePath) {
        try {
            Files.createFile(filePath);
        } catch (final IOException e) {
            LOGGER.debug("IOException while creating file {}", e);
        }
    }

    private void setLastModifiedTime(final Path filePath, final FileTime fileTime) {
        try {
            Files.setAttribute(filePath, "lastModifiedTime", fileTime, LinkOption.NOFOLLOW_LINKS);
        } catch (final IOException e) {
            LOGGER.debug("IOException setting last modified time {}", e);
        }
    }

    public void createExecutableFile() {
        final String scriptPath = Paths.get(System.getProperty("user.dir")).toAbsolutePath().toString() + File.separator + "src" + File.separator
                + "test" + File.separator + "resources" + File.separator + "scripts" + File.separator + "createSymlinks.sh";

        final String executableScriptPath = Paths.get(System.getProperty("user.dir")).toAbsolutePath().toString() + File.separator + "src"
                + File.separator + "test" + File.separator + "resources" + File.separator + "scripts" + File.separator + "createSymlinksEx.sh";

        List<String> lines = new ArrayList<String>();

        try {
            lines = readFile(scriptPath);
            final File file = new File(executableScriptPath);
            final PrintWriter out = new PrintWriter(new FileWriter(file));
            LOGGER.debug("Creating file {}", executableScriptPath);
            for (final String line : lines) {
                out.println(line);
            }
            out.close();
            file.setExecutable(true);
            file.setReadable(true);
            file.setWritable(true);
        } catch (final IOException e) {
            e.printStackTrace();
        }
    }

    private List<String> readFile(final String fileName) throws IOException {
        final Scanner textScanner = new Scanner(new File(fileName));
        final List<String> lines = new ArrayList<String>();
        while (textScanner.hasNextLine()) {
            lines.add(textScanner.nextLine());
        }
        textScanner.close();
        return lines;
    }

    public void createTestSymlinks(final String targetFileDirectory, final String newSymlinkDirectory, final int intervalTimeStamp) {
        createDirectory(newSymlinkDirectory);
        /*
         * The modified dates of symbolic links cannot be edited through java on Linux due to a bug/limitation in the jdk on Linux. Symbolic links
         * with appropriate last modified dates will be created using a shell script if this test is run on a Linux machine. They will be created
         * using code if this test is run on a Windows machine.
         */
        if (System.getProperty("os.name").toLowerCase().contains("windows")) {
            createTestSymlinksWindows(targetFileDirectory, newSymlinkDirectory, intervalTimeStamp);
        } else {
            createTestSymlinksLinux(targetFileDirectory, newSymlinkDirectory, intervalTimeStamp);
        }
    }

    private void createTestSymlinksWindows(final String targetFileDirectory, final String newSymlinkDirectory, final int timestampInterval) {
        final List<Path> filePaths = getFilesInDirectory(targetFileDirectory);
        for (int i = 0; i < filePaths.size(); i++) {
            final Path targetFilePath = filePaths.get(i);
            final Path symlinkPath = Paths.get(newSymlinkDirectory + "symlink" + i);
            createSymlink(symlinkPath, targetFilePath);
            final long newFileModifiedTime = System.currentTimeMillis() - timestampInterval * TimeConstants.ONE_HOUR_IN_MILLISECONDS * i;
            final FileTime fileTime = FileTime.fromMillis(newFileModifiedTime);
            setLastModifiedTime(symlinkPath, fileTime);
        }
    }

    private void createSymlink(final Path symlinkPath, final Path targetFilePath) {
        try {
            Files.createSymbolicLink(symlinkPath, targetFilePath);
        } catch (final IOException e) {
            LOGGER.debug("IOException while creating symlink {}", e);
        }
    }

    private void createTestSymlinksLinux(final String targetFileDirectory, final String newSymlinkDirectory, final int timestampInterval) {
        LOGGER.debug("Create test symlinks using bash script");
        final String scriptPath = Paths.get(System.getProperty("user.dir")).toAbsolutePath().toString() + File.separator + "src" + File.separator
                + "test" + File.separator + "resources" + File.separator + "scripts" + File.separator + "createSymlinksEx.sh";
        final String targetFileDirectoryAbsolute = Paths.get(targetFileDirectory).toAbsolutePath().toString();
        final String newSymlinkDirectoryAbsolute = Paths.get(newSymlinkDirectory).toAbsolutePath().toString();

        try {
            final Process proc = new ProcessBuilder(scriptPath, targetFileDirectoryAbsolute, newSymlinkDirectoryAbsolute,
                    Integer.toString(timestampInterval)).start();
            final BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(proc.getInputStream()));
            String line = null;
            LOGGER.debug("<OUTPUT>");
            while ((line = bufferedReader.readLine()) != null) {
                LOGGER.debug(line);
            }
            LOGGER.debug("</OUTPUT>");
            final int exitVal = proc.waitFor();
            LOGGER.debug("Process exitValue: {}", exitVal);
        } catch (final InterruptedException exception) {
            LOGGER.error("InterruptedException while runninG createSymlinks.sh", exception);
        } catch (final IOException exception) {
            LOGGER.error("IOException while runnin createSymlinks.sh", exception);
        }
    }

    public List<Path> getFilesInDirectory(final String directory) {
        final List<Path> filePaths = new ArrayList<Path>();
        try (DirectoryStream<Path> directoryStream = Files.newDirectoryStream(Paths.get(directory))) {
            for (final Path path : directoryStream) {
                filePaths.add(path.toAbsolutePath());
            }
        } catch (final IOException exception) {
            LOGGER.error("IOException while getting files in directory {}", exception);
        }
        return filePaths;
    }

    /**
     * Delete File(s) or Directory(ies) with their contents.
     *
     * @param paths
     *         - Array of paths to delete.
     */
    public void deleteDiskResource(final Path... paths) {
        try {
            for (final Path path : paths) {
                LOGGER.debug("Deleting Directory: {}", path.toAbsolutePath());
                final File file = path.toFile();
                if (file.exists()) {
                    FileUtils.forceDelete(path.toFile());
                } else {
                    LOGGER.error("Cannot delete {} as it does not exist", path.toAbsolutePath());
                }
            }
        } catch (final IOException exception) {
            LOGGER.error("IOException while deleting test files {}", paths, exception);
        }
    }

    public String getSubscriptionIdMatchingSubscriptionName(final String subscriptionName, final SubscriptionType subscriptionType)
            throws InterruptedException {
        String subscriptionID = "100000";
        testResponseHolder.clear();
        final CountDownLatch cl = new CountDownLatch(1);
        testResponseHolder.setCountDownLatch(cl);
        final SubscriptionOperationRequest ssi = new SubscriptionOperationRequest(PmServiceRequestsTypes.LIST_ACTIVE, null, null, null);
        operationSender.sendTestOperationMessageToPmService(ssi);
        cl.await(60, TimeUnit.SECONDS);
        final Map<String, Map<String, Object>> activeSubscriptions = testResponseHolder.getAllSubs();
        final Set<String> subscriptionNames = activeSubscriptions.keySet();
        for (final String name : subscriptionNames) {
            if (name.equals(subscriptionName) && activeSubscriptions.get(name).get("TYPE").equals(subscriptionType.name())) {
                final Map<String, Object> subscriptionAttr = activeSubscriptions.get(name);
                subscriptionID = (String) subscriptionAttr.get("ID");
                break;
            }
        }
        testResponseHolder.clear();
        return subscriptionID;
    }

    public void waitForScannerToBeCreated(final int count) throws InterruptedException {
        final CountDownLatch cl = new CountDownLatch(count);
        dpsNotificationListener.setObjCreatedCountDownLatch(cl);
        cl.await(60, TimeUnit.SECONDS);
    }

    public void waitForScannerToBeCreated() throws InterruptedException {
        waitForScannerToBeCreated(2);
    }

    public void deleteTestFiles(final String directoryPathString) {
        final FileDeletionHelper cleanupHelper = new FileDeletionHelper();
        cleanupHelper.deleteOldFiles(directoryPathString, 0, true);
    }

    public void deleteTestDirectories() {
        try {
            final Path dir = Paths.get(TEST_DIRECTORY);
            Files.walkFileTree(dir, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult postVisitDirectory(final Path directory, final IOException ioe) throws IOException {
                    LOGGER.debug("Deleting Directory: {}", directory.getFileName());
                    Files.delete(directory);
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (final IOException e) {
            LOGGER.debug("IOException while deleting test files {}", e);
        }
    }

    public void deleteTestSymLinkDirectories() {
        try {
            final Path dir = Paths.get(TEST_SYM_DIRECTORY);
            Files.walkFileTree(dir, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult postVisitDirectory(final Path directory, final IOException ioe) throws IOException {
                    LOGGER.debug("Deleting Directory: {}", directory.getFileName());

                    Files.delete(directory);

                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (final IOException e) {
            LOGGER.debug("IOException while deleting test files {}", e);
        }
    }

    public void deleteExecutableFile() {
        final String executableScriptPath = Paths.get(System.getProperty("user.dir")).toAbsolutePath().toString() + File.separator + "src"
                + File.separator + "test" + File.separator + "resources" + File.separator + "scripts" + File.separator + "createSymlinksEx.sh";
        try {
            Files.delete(Paths.get(executableScriptPath));
            LOGGER.info("File " + executableScriptPath + " deleted");
        } catch (final IOException e) {
            LOGGER.error("File " + executableScriptPath + " deletion Failed", e);
        }
    }

    /**
     * Asserts that all subscriptions in DPS have this {@link AdministrationState}
     *
     * @param administrationState
     *         - The {@link AdministrationState} subscriptions must have
     *
     * @throws InterruptedException
     *         - if thread is interrupted
     */
    public void assertThatSubscriptionsHave(final AdministrationState administrationState) throws InterruptedException {
        final Map<String, Map<String, Object>> subscriptions = testUtils.findSubscriptions(getClass().getSimpleName());
        for (final String name : subscriptions.keySet()) {
            final Map<String, Object> subscriptionAttr = subscriptions.get(name);
            final String id = (String) subscriptionAttr.get(ID.name());
            testUtils.assertAdministrationState(id, name, subscriptionAttr, administrationState);
        }
    }

    /**
     * Asserts that all subscriptions in DPS have this {@link AdministrationState}
     *
     * @param administrationState
     *         - The {@link AdministrationState} subscriptions must have
     *
     * @throws InterruptedException
     *         - if thread is interrupted
     */
    public void assertThatSubscriptionsHaveTaskStatus(final TaskStatus taskStatus, final String fileName) throws InterruptedException {
        final Map<String, Map<String, Object>> subscriptions = testUtils.findSubscriptions(fileName);
        for (final String name : subscriptions.keySet()) {
            final Map<String, Object> subscriptionAttr = subscriptions.get(name);
            final String id = (String) subscriptionAttr.get(ID.name());
            assertTaskStatus(id, name, subscriptionAttr, taskStatus);
        }
    }

    public void assertTaskStatus(final String id, final String name, final Map<String, Object> initiationResult, final TaskStatus state) {
        assertEquals(id, initiationResult.get(ID.name()));
        assertEquals(String.format("Subscription %s should be %s. result: %s.", name, state.name(), initiationResult), state,
                initiationResult.get(TASK_STATUS.name()));
    }

    /**
     * Wait up to the specified duration and assert that subscriptions have the correct state
     *
     * @param administrationState
     *         - {@link AdministrationState} subscriptions must have
     * @param maxDuration
     *         - The maximum duration to wait. Check is performed every 5 seconds
     *
     * @throws InterruptedException
     *         - if thread is interrupted
     */
    public void waitUntil(final AdministrationState administrationState, final Duration maxDuration) throws InterruptedException {
        await().pollDelay(Duration.FIVE_SECONDS).pollInterval(Duration.FIVE_SECONDS).atMost(maxDuration).until(new Callable<Boolean>() {
            @Override
            public Boolean call() throws InterruptedException {
                return areAllSubscriptionsWithAdminState(administrationState);
            }
        });
    }

    /**
     * Get all subscriptions from DPS and verify if they all have this admin state
     *
     * @param administrationState
     *         - {@link AdministrationState} subscriptions must have
     *
     * @return - true if all subscriptions have this admin state
     * @throws InterruptedException
     *         - if thread is interrupted
     */
    public Boolean areAllSubscriptionsWithAdminState(final AdministrationState administrationState) throws InterruptedException {
        final Map<String, Map<String, Object>> testSubscriptions = testUtils.findSubscriptions(getClass().getSimpleName());
        boolean isFinished = true;
        for (final Map<String, Object> subscriptionAttributes : testSubscriptions.values()) {
            if (!administrationState.name().equals(subscriptionAttributes.get(ADMIN_STATE.name()))) {
                isFinished = false;
            }
        }
        return isFinished;
    }

    protected void waitUntilTaskStatus(final TaskStatus taskStatus, final Duration maxDuration) {
        await().pollDelay(Duration.FIVE_SECONDS).pollInterval(Duration.FIVE_SECONDS).atMost(maxDuration).until(new Callable<Boolean>() {
            @Override
            public Boolean call() throws InterruptedException {
                return areAllSubscriptionsWithTaskStatus(taskStatus, getClass().getSimpleName());
            }
        });
    }

    protected void waitUntilTaskStatus(final TaskStatus taskStatus, final Duration maxDuration, final String DataFile) {
        await().pollDelay(Duration.FIVE_SECONDS).pollInterval(Duration.FIVE_SECONDS).atMost(maxDuration).until(new Callable<Boolean>() {
            @Override
            public Boolean call() throws InterruptedException {
                return areAllSubscriptionsWithTaskStatus(taskStatus, DataFile);
            }
        });
    }

    private Boolean areAllSubscriptionsWithTaskStatus(final TaskStatus taskStatus, String dataFile) throws InterruptedException {
        final Map<String, Map<String, Object>> testSubscriptions = testUtils.findSubscriptions(dataFile);
        boolean isFinished = true;
        for (final Map.Entry<String, Map<String, Object>> subscriptionAttributes : testSubscriptions.entrySet()) {
            LOGGER.info("Subscription {} has task Status {} Expected {}", subscriptionAttributes.getKey(),
                    subscriptionAttributes.getValue().get(TASK_STATUS.name()), taskStatus);
            if (!taskStatus.equals(subscriptionAttributes.getValue().get(TASK_STATUS.name()))) {
                isFinished = false;
            }
        }
        return isFinished;
    }
}
