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

package com.ericsson.oss.services.pm.initiation.config.listener.validators;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Class for validating file paths
 */
public class PathValidatorImpl implements PathValidator {
    /**
     * {@inheritDoc}
     */
    @Override
    public String formAndValidatePath(final String defaultIfNonConvertible, final String basePath, final String... morePathElements) {
        // If no valid path can be formed, an exception is thrown and we just use the default set above
        try {
            final Path path = Paths.get(basePath, morePathElements);
            return path.toFile().getPath();
        } catch (final InvalidPathException ipe) {
            return defaultIfNonConvertible;
        }

    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String formSymbolicLinkPath(final String symbolicLinkVolume, final String symbolicLinkBaseDir) {
        return formAndValidatePath(symbolicLinkVolume, symbolicLinkVolume, symbolicLinkBaseDir);
    }
}
