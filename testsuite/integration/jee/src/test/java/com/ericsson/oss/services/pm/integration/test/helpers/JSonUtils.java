/*------------------------------------------------------------------------------
 *******************************************************************************
 * COPYRIGHT Ericsson 2015
 *
 * The copyright to the computer program(s) herein is the property of
 * Ericsson Inc. The programs may be used and/or copied only with written
 * permission from Ericsson Inc. or in accordance with the terms and
 * conditions stipulated in the agreement/contract under which the
 * program(s) have been supplied.
 *******************************************************************************
 *----------------------------------------------------------------------------*/

package com.ericsson.oss.services.pm.integration.test.helpers;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;

import org.codehaus.jackson.JsonNode;
import org.codehaus.jackson.JsonProcessingException;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.node.ObjectNode;
import org.codehaus.jackson.type.TypeReference;

import com.ericsson.oss.itpf.sdk.resources.Resources;
import com.ericsson.oss.pmic.dto.subscription.enums.SubscriptionType;

public class JSonUtils {

    static ObjectMapper mapper = new ObjectMapper();

    private JSonUtils() {

    }

    public static String getJsonBody(final Object jsonObject) {
        try {
            return mapper.writeValueAsString(jsonObject);
        } catch (final IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    public static List<Map<String, String>> getListContainingMapsFromJsonString(final String jsonString) {
        try {
            return mapper.readValue(jsonString, new TypeReference<List<Map<String, String>>>() {
            });
        } catch (final IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    public static <T> T getJsonMapperObjectFromJsonStream(final InputStream inputStream, final Class<T> type) {
        try {
            return mapper.readValue(inputStream, type);
        } catch (final IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    public static <T> T getJsonMapperObjectFromJsonString(final String jsonString, final Class<T> type) {
        try {
            return mapper.readValue(jsonString, type);
        } catch (final IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    public static <T> T getJsonMapperObjectFromFile(final String filePath, final Class<T> type) throws Exception {
        return mapper.readValue(Resources.getClasspathResource(filePath).getAsText(), type);
    }

    public static <T> T getJsonObjectFromSimplifiedFile(final String filePath, final SubscriptionType expectedSubscriptionType, final Class<T> type)
            throws JsonProcessingException, IOException {
        final JsonNode rootNode = mapper.readTree(Resources.getClasspathResource(filePath).getAsText());
        final JsonNode subscriptionType = rootNode.path("type");
        if (subscriptionType.asText().equals(expectedSubscriptionType.name())) {
            ((ObjectNode) rootNode).put("@class", subscriptionType.asText().toLowerCase());
        }
        return getJsonMapperObjectFromJsonString(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(rootNode), type);
    }
}
