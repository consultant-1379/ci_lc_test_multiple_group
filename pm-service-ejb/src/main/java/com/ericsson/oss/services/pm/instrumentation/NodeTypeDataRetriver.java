/*
 * COPYRIGHT Ericsson 2018
 *
 * The copyright to the computer program(s) herein is the property of
 * Ericsson Inc. The programs may be used and/or copied only with written
 * permission from Ericsson Inc. or in accordance with the terms and
 * conditions stipulated in the agreement/contract under which the
 * program(s) have been supplied.
 */

package com.ericsson.oss.services.pm.instrumentation;

import java.util.Collection;
import java.util.Iterator;
import javax.inject.Inject;

import org.slf4j.Logger;

import com.ericsson.oss.itpf.modeling.modelservice.ModelService;
import com.ericsson.oss.itpf.modeling.modelservice.typed.TypedModelAccess;
import com.ericsson.oss.itpf.modeling.modelservice.typed.capabilities.Capability;
import com.ericsson.oss.itpf.modeling.modelservice.typed.capabilities.CapabilityInformation;
import com.ericsson.oss.itpf.sdk.resources.Resource;
import com.ericsson.oss.itpf.sdk.resources.Resources;

/**
 * NodeData Type File.
 */
public class NodeTypeDataRetriver {

    private static final String ABSOLUTE_FILE_URI = "/ericsson/tor/data/pm/fileCollectionCategories.txt";
    private static final String DIRECTORY_PATH = "/ericsson/tor/data/pm";

    @Inject
    private ModelService modelService;

    @Inject
    private Logger logger;

    /**
     * method to write the fileCollectionCategory type into the file.
     */
    public void nodeTypeFileCreation() {
        final Resource directoryResource = Resources.getFileSystemResource(DIRECTORY_PATH);
        final Resource fileResource = Resources.getFileSystemResource(ABSOLUTE_FILE_URI);
        final StringBuilder stringBuffer = new StringBuilder("NodeType:Category");
        final TypedModelAccess typedModelAccess = modelService.getTypedAccess();
        final CapabilityInformation capabilityInformation = typedModelAccess.getModelInformation(CapabilityInformation.class);
        if (directoryResource.isDirectoryExists()) {
            final Collection<Capability> capabilities = capabilityInformation.getCapabilities("PMICFunctions", "fileCollectionCategory");
            final Iterator<Capability> iterator = capabilities.iterator();
            while (iterator.hasNext()) {
                final Capability capability = iterator.next();
                logger.info("Target type {} value {}", capability.getTargetType(), capability.getValue());
                stringBuffer.append(System.getProperty("line.separator"));
                stringBuffer.append(capability.getTargetType());
                stringBuffer.append(":");
                stringBuffer.append(capability.getValue());
            }
            writeToFileSystem(String.valueOf(stringBuffer).getBytes(), false, fileResource);
        } else {
            logger.info("directory path {} doesn't exists", DIRECTORY_PATH);
        }
    }

    /**
     * to create Resource and write content into the file.
     *
     * @param bytes
     *         - to write the data into the file
     * @param append
     *         - to append the data to the file
     * @param resource
     *         to create a resource
     */
    private void writeToFileSystem(final byte[] bytes, final boolean append, final Resource resource) {
        if (resource.supportsWriteOperations()) {
            // resource is created (if it does not exist) and content is written (or appended)
            resource.write(bytes, append);
        }
    }
}
