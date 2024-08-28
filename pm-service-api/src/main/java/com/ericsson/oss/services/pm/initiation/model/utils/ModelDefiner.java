/*
 * COPYRIGHT Ericsson 2017
 *
 * The copyright to the computer program(s) herein is the property of
 * Ericsson Inc. The programs may be used and/or copied only with written
 * permission from Ericsson Inc. or in accordance with the terms and
 * conditions stipulated in the agreement/contract under which the
 * program(s) have been supplied.
 */

package com.ericsson.oss.services.pm.initiation.model.utils;

import com.ericsson.oss.services.pm.services.exception.PfmDataException;

/**
 * Enum class for list of entities that can define models. For example Network Element (NE) or OSS.
 */
public enum ModelDefiner {
    NE(PmMetaDataConstants.NE_DEFINED_PATTERN),
    OSS(PmMetaDataConstants.OSS_DEFINED_PATTERN);
    private static final String EMPTY_STRING = "";
    private static final String INVALID = "INVALID";
    private String urnPattern;

    /**
     * Instantiates a new model definer.
     *
     * @param urnPattern
     *         the urn pattern
     */
    ModelDefiner(final String urnPattern) {
        this.urnPattern = urnPattern;
    }

    /**
     * Gets the urn pattern.
     *
     * @param value
     *         the value
     *
     * @return the urn pattern
     */
    public static String getUrnPattern(final String value) {
        if (value == null || EMPTY_STRING.equals(value.trim())) {
            return EMPTY_STRING;
        }
        try {
            return valueOf(value).getUrnPattern();
        } catch (final IllegalArgumentException exception) {
            return INVALID;
        }
    }

    /**
     * Gets the model definer.
     *
     * @param modelDefiner
     *         the model definer
     *
     * @return the model definer
     * @throws PfmDataException
     *         PfmDataException
     */
    public static ModelDefiner getModelDefiner(final String modelDefiner) throws PfmDataException {
        if (modelDefiner == null || modelDefiner.trim().isEmpty()) {
            return null;
        } else {
            try {
                return valueOf(modelDefiner);
            } catch (final IllegalArgumentException exception) {
                final String message = String.format("Invalid definer %s", modelDefiner);
                throw new PfmDataException(message);
            }
        }
    }

    public String getUrnPattern() {
        return urnPattern;
    }
}
