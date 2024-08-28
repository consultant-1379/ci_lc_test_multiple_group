/*------------------------------------------------------------------------------
 *******************************************************************************
 * COPYRIGHT Ericsson 2016
 *
 * The copyright to the computer program(s) herein is the property of
 * Ericsson Inc. The programs may be used and/or copied only with written
 * permission from Ericsson Inc. or in accordance with the terms and
 * conditions stipulated in the agreement/contract under which the
 * program(s) have been supplied.
 *******************************************************************************
 *----------------------------------------------------------------------------*/

package com.ericsson.oss.services.pm.license;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Represents the relevant information about a license.
 */
public class License {

    private final String name;

    private final boolean allowed;

    private final String description;

    /**
     * Creates a new instance of license with all mandatory fields.
     *
     * @param name
     *         The license name.
     * @param allowed
     *         The flag, indicating if the license name is allowed or not.
     * @param description
     *         The description about the license.
     */
    public License(final String name, final boolean allowed, final String description) {
        this.name = name;
        this.allowed = allowed;
        this.description = description;
    }

    public String getName() {
        return name;
    }

    public boolean isAllowed() {
        return allowed;
    }

    public String getDescription() {
        return description;
    }

    @Override
    public boolean equals(final Object other) {
        if (this == other) {
            return true;
        }
        if (other == null || getClass() != other.getClass()) {
            return false;
        }
        final License that = (License) other;
        return Objects.equals(name, that.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name);
    }

    /**
     * Generate a map of this license properties.
     *
     * @return The license properties map.
     */
    public Map<String, Object> toMap() {
        final Map<String, Object> licenseMap = new HashMap<>();
        licenseMap.put("name", getName());
        licenseMap.put("allowed", isAllowed());
        licenseMap.put("description", getDescription());
        return licenseMap;
    }

    @Override
    public String toString() {
        return "License{" +
                "name='" + name + '\'' +
                ", allowed=" + allowed +
                ", description='" + description + '\'' +
                '}';
    }
}
