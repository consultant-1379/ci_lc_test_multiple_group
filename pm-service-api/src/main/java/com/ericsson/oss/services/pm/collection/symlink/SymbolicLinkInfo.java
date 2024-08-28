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

package com.ericsson.oss.services.pm.collection.symlink;

/**
 * Symbolic link info interface for creating symbolic links.
 */
public interface SymbolicLinkInfo {

    /**
     * Forms and returns the path where the symbolic link will be stored. At the moment, this path is formed with the symbolicLinkVolume +
     * symbolicLinkBaseDir. I.e. <br/>
     * /ericsson/symvol/pmlinks1/lterbs/ <br/>
     * Where: <br/>
     * <list> <li>/ericsson/symvol/pmlinks1/ -> symbolicLinkVolume</li> <li>
     * lterbs/ -> symbolicLinkBaseDir</li> </list>
     *
     * @return - path of symbolic link
     */
    String formSymbolicLinkPath();

    /**
     * Forms and returns the target to which the symlink will point to. This target path will be formed by the target prefix + XML + nodeFDN. The
     * target prefix is a modeled configuration param. <br/>
     * The returned path will look something like this: <br/>
     * /ericsson/pmic/pm1/XML/MeContext=ERBS00001/ <br/>
     * Where: <br/>
     * <list> <li>/ericsson/pmic/ -> This is the PIB parameter symbolicLinkTargetPrefix</li> <li>pm1/ -> 3rd path level from right to left</li> <li>
     * XML/ -> 2rd path level from right to left</li> <li>
     * MeContext=ERBS00001/ -> 1st path level from right to left. It is the node FDN that is resolved dynamically</li> </list>
     * <br/>
     * This method assumes there needs to strip the last 3 levels of the path
     *
     * @param destinationDirectory
     *         - the destination directory path
     *
     * @return - path of symbolic link target
     */
    String formSymbolicLinkTargetPath(final String destinationDirectory);

    /**
     * Gets symbolic link subdir name prefix.
     *
     * @return the name prefix for the balanced subdirectories. i.e. The 'dir' part in the path below: /ericsson/symvol/pmlinks1/lterbs/dir.
     */
    String getSymbolicLinkSubdirNamePrefix();

    /**
     * Gets max symbolic links allowed.
     *
     * @return The maximum number of symlinks allows.
     */
    int getMaxSymbolicLinksAllowed();

    /**
     * Gets max symbolic link subdirs.
     *
     * @return The maximum number of symlink sub directories to be created.
     */
    int getMaxSymbolicLinkSubdirs();

    /**
     * Gets symbolic link creation enabled.
     *
     * @return Boolean value for symlink creation being enabled.
     */
    boolean isSymbolicLinkCreationEnabled();
}
