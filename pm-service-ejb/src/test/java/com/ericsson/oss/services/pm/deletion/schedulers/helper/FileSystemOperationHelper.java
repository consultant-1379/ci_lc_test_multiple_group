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

package com.ericsson.oss.services.pm.deletion.schedulers.helper;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
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
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ericsson.oss.services.pm.deletion.schedulers.FileDeletionHelper;

public class FileSystemOperationHelper {
    private static final long ONE_MINUTE_IN_MS = TimeUnit.MINUTES.toMillis(1);

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    public void createTestFiles(final String directory, final int amount, final int timestampInterval) {
        createTestFiles(directory, amount, 0L, timestampInterval);
    }

    /**
     * This method is used for test and it creates as many files as specified in the amount parameter.
     * All those files will have the lastModifiedTime in the past, all before current time - deltaTimeInMillis.
     * The difference between lastModifiedTime of a file and lastModifiedTime of the previous file will be timestampInterval.
     *
     * @param directory - directory where files will be created
     * @param amount - number of files to be created
     * @param deltaTimeInMillis - time to be removed from current time
     * @param timestampInterval - time between each lastModifiedTime of consecutive files
     */
    public void createTestFiles(final String directory, final int amount, final long deltaTimeInMillis, final int timestampInterval) {
        createDirectory(directory);

        final long currentTimeMillis = DateTime.now().getMillis();
        for (int i = -amount; i < 0; i++) {
            final Path newFilePath = Paths.get(directory + "fileTest" + i + ".txt");

            if (!newFilePath.toFile().exists()) {
                createFile(newFilePath);
                final long newFileModifiedTime = currentTimeMillis - deltaTimeInMillis + timestampInterval * ONE_MINUTE_IN_MS * i;
                final FileTime fileTime = FileTime.fromMillis(newFileModifiedTime);
                setLastModifiedTime(newFilePath, fileTime);
            }
        }
    }

    public void createTestFilesAndSymlink(final String fileDirectory, final String newSymlinkDirectory, final int amount,
            final long deltaTimeInMillis, final int timestampInterval) {
        createDirectory(fileDirectory);
        createDirectory(newSymlinkDirectory);

        final long currentTimeMillis = DateTime.now().getMillis();
        for (int i = -amount; i < 0; i++) {
            final String fileName = fileDirectory + "fileTest" + i + ".txt";
            final Path newFilePath = Paths.get(fileName);

            if (!newFilePath.toFile().exists()) {
                createFile(newFilePath);
                final long newFileModifiedTime = currentTimeMillis - deltaTimeInMillis + timestampInterval * ONE_MINUTE_IN_MS * i;
                final FileTime fileTime = FileTime.fromMillis(newFileModifiedTime);
                setLastModifiedTime(newFilePath, fileTime);

                
                //create symLink
                final String symLinkName = newSymlinkDirectory + "symlink" + i;
                final Path newSymlinkPath = Paths.get(symLinkName);

                if (!newSymlinkPath.toFile().exists()) {
                    createFile(newSymlinkPath);
                    setLastModifiedTime(newSymlinkPath, fileTime);
                }
            }
        }
    }

    public void createTestSymlinks(final String targetFileDirectory, final String newSymlinkDirectory, final int timestampInterval) {
        createDirectory(newSymlinkDirectory);
        /*
         * The modified dates of symbolic links cannot be edited through java on Linux due to a bug/limitation in the jdk on Linux. Symbolic links
         * with appropriate last modified dates will be created using a shell script if this test is run on a Linux machine. They will be created
         * using code if this test is run on a Windows machine.
         */
        if (System.getProperty("os.name").toLowerCase().contains("windows")) {
            createTestSymlinksWindows(targetFileDirectory, newSymlinkDirectory, timestampInterval);
        } else {
            createTestSymlinksLinux(targetFileDirectory, newSymlinkDirectory, timestampInterval);
        }
    }

    public List<Path> getFilesInDirectory(final String directory) {
        final List<Path> filePaths = new ArrayList<>();
        try (DirectoryStream<Path> directoryStream = Files.newDirectoryStream(Paths.get(directory))) {
            for (final Path path : directoryStream) {
                filePaths.add(path.toAbsolutePath());
            }
        } catch (final IOException e) {
            logger.debug("Exception while getting files in {} : {}", directory, e.getStackTrace());
        }
        return filePaths;
    }

    public void deleteTestFiles(final String directory) {
        final FileDeletionHelper cleanupHelper = new FileDeletionHelper();
        cleanupHelper.deleteOldFiles(directory, 0, DateTime.now(), true);
    }

    public void deleteTestDirectories(final String rootDirectory) {
        final Path dir = Paths.get(rootDirectory);
        if (dir.toFile().exists()) {
            try {
                Files.walkFileTree(dir, new SimpleFileVisitor<Path>() {
                    @Override
                    public FileVisitResult postVisitDirectory(final Path directory, final IOException ioe) throws IOException {
                        Files.delete(directory);
                        return FileVisitResult.CONTINUE;
                    }
                });
            } catch (final IOException e) {
                logger.debug("Exception while deleting directory {} : {}", rootDirectory, e.getStackTrace());
            }
        }
    }

    public boolean isTestDirectoryPresent(final String directory) {
        return Paths.get(directory).toFile().exists();
    }

    public boolean isTestDirectoryEmpty(final String directory) {
        return Paths.get(directory).toFile().isDirectory() && Paths.get(directory).toFile().list().length == 0;
    }

    private void createTestSymlinksLinux(final String targetFileDirectory, final String newSymlinkDirectory, final int timestampInterval) {
        logger.debug("Create test symlinks using bash script");
        logger.debug("Date {} ", new Date(System.currentTimeMillis()));
        final String scriptPath = Paths.get(System.getProperty("user.dir")).toAbsolutePath().toString() + File.separator + "src" + File.separator
                + "test" + File.separator + "resources" + File.separator + "scripts" + File.separator + "createSymlinks.sh";
        final String targetFileDirectoryAbsolute = Paths.get(targetFileDirectory).toAbsolutePath().toString();
        final String newSymlinkDirectoryAbsolute = Paths.get(newSymlinkDirectory).toAbsolutePath().toString();

        try {
            final Process proc = new ProcessBuilder(scriptPath, targetFileDirectoryAbsolute, newSymlinkDirectoryAbsolute,
                    Integer.toString(timestampInterval)).start();
            final BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(proc.getInputStream()));
            String line;
            logger.debug("<OUTPUT>");
            while ((line = bufferedReader.readLine()) != null) {
                logger.debug(line);
            }
            logger.debug("</OUTPUT>");
            final int exitVal = proc.waitFor();
            logger.debug("Process exitValue: {}", exitVal);
        } catch (final Exception e) {
            logger.debug("IOException while running createSymlinks.sh {}", e.getMessage());
        }
    }

    private void createDirectory(final String directory) {
        final Path path = Paths.get(directory);

        if (!path.toFile().exists()) {
            try {
                Files.createDirectories(path);
            } catch (final IOException e) {
                logger.debug("Exception while creating folder {} : {}", directory, e.getStackTrace());
            }
        }
    }

    private void createFile(final Path filePath) {
        try {
            Files.createFile(filePath);
        } catch (final IOException e) {
            logger.debug("Exception while creating file {} : {}", filePath, e.getStackTrace());
        }
    }

    private void createSymlink(final Path symlinkPath, final Path targetFilePath) {
        try {
            Files.createSymbolicLink(symlinkPath, targetFilePath);
        } catch (final IOException e) {
            logger.debug("Exception while creating symbolic link {} for {} : {}", symlinkPath, targetFilePath, e.getStackTrace());
        }
    }

    private void setLastModifiedTime(final Path filePath, final FileTime fileTime) {
        try {
            Files.setAttribute(filePath, "lastModifiedTime", fileTime, LinkOption.NOFOLLOW_LINKS);
        } catch (final IOException e) {
            logger.debug("Exception while set timestamp for {} : {}", filePath, e.getStackTrace());
        }
    }

    private void createTestSymlinksWindows(final String targetFileDirectory, final String newSymlinkDirectory, final int timestampInterval) {
        final List<Path> filePaths = getFilesInDirectory(targetFileDirectory);
        final long currentTimeMillis = System.currentTimeMillis();
        for (int i = 0; i < filePaths.size(); i++) {
            final Path targetFilePath = filePaths.get(i);
            final Path symlinkPath = Paths.get(newSymlinkDirectory + "symlink" + i);
            createSymlink(symlinkPath, targetFilePath);
            final long newFileModifiedTime = currentTimeMillis - timestampInterval * ONE_MINUTE_IN_MS * i;
            final FileTime fileTime = FileTime.fromMillis(newFileModifiedTime);
            setLastModifiedTime(symlinkPath, fileTime);
        }
    }

}
