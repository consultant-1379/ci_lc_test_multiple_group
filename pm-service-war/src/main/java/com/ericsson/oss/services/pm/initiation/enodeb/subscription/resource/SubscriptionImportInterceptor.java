/*------------------------------------------------------------------------------
 *******************************************************************************
 * COPYRIGHT Ericsson 2017
 *
 * The copyright to the computer program(s) herein is the property of
 * Ericsson Inc. The programs may be used and/or copied only with written
 * permission from Ericsson Inc. or in accordance with the terms and
 * conditions stipulated in the agreement/contract under which the
 * program(s) have been supplied.
 *******************************************************************************
 *----------------------------------------------------------------------------*/
package com.ericsson.oss.services.pm.initiation.enodeb.subscription.resource;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.lang.reflect.Method;
import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.ext.Provider;

import org.codehaus.jackson.JsonNode;
import org.codehaus.jackson.JsonProcessingException;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.node.ObjectNode;
import org.jboss.resteasy.annotations.interception.ServerInterceptor;
import org.jboss.resteasy.core.ResourceMethod;
import org.jboss.resteasy.core.ServerResponse;
import org.jboss.resteasy.spi.Failure;
import org.jboss.resteasy.spi.HttpRequest;
import org.jboss.resteasy.spi.interception.AcceptedByMethod;
import org.jboss.resteasy.spi.interception.PreProcessInterceptor;
import org.slf4j.Logger;

/**
 * REST easy PreProccessInterceptor extension class for modifying HttpRequest Json message body adding the "@class" filed to Json
 */
@Provider
@ServerInterceptor
public class SubscriptionImportInterceptor implements PreProcessInterceptor, AcceptedByMethod {

    /**
     * Header information for imported subscription
     */
    private static final String X_IS_IMPORTED = "X-isImported";

    @Inject
    private Logger logger;

    @Context
    private HttpServletRequest httpServletRequest;

    @Override
    public ServerResponse preProcess(final HttpRequest request, final ResourceMethod method) throws Failure, WebApplicationException {
        logger.debug("preProcess interceptor called {}", httpServletRequest.getContentType());

        if (httpServletRequest.getContentType() != null && httpServletRequest.getContentType().startsWith(MediaType.APPLICATION_JSON)
                && isImported()) {
            try {
                addToJsonClassInfo(request);
            } catch (final IOException e) {
                throw new Failure(e);
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    @Override
    public boolean accept(final Class declaring, final Method method) {
        if (declaring.isAnnotationPresent(ImportPreProcess.class)) {
            logger.debug("@ImportPreProcess annotation present on class");
            return true;
        }
        if (method.isAnnotationPresent(ImportPreProcess.class)) {
            logger.debug("@ImportPreProcess annotation present on method");
            return true;
        }
        logger.debug("no @ImportPreProcess annotation present");
        return false;
    }

    /**
     * @param request
     *
     * @throws IOException
     * @throws JsonProcessingException
     */
    private void addToJsonClassInfo(final HttpRequest request) throws JsonProcessingException, IOException {

        final BufferedInputStream input = new BufferedInputStream(request.getInputStream());
        final ObjectMapper objectMapper = new ObjectMapper();

        final JsonNode rootNode = objectMapper.readTree(input);
        final JsonNode subscriptionType = rootNode.path("type");
        final String subscriptionClass = subscriptionType.asText();

        if (logger.isDebugEnabled()) {
            logger.debug("SubscriptionType: {} ", objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(subscriptionType));
        }

        switch (subscriptionClass) {
            case "STATISTICAL":
            case "MOINSTANCE":
            case "CELLTRACE":
            case "EBM":
            case "UETRACE":
            case "RES":
            case "CELLRELATION":
            case "BSCRECORDINGS":
            case "MTR":
            case "RPMO":
            case "RTT":
                ((ObjectNode) rootNode).put("@class", subscriptionClass.toLowerCase());
                break;
            default:
                throw new IOException(" import of subscription type" + subscriptionClass + " is not supported in reduced format ");
        }

        request.setInputStream(new ByteArrayInputStream(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(rootNode)));
    }

    private Boolean isImported() {
        final String isImported = httpServletRequest.getHeader(X_IS_IMPORTED);
        logger.debug("isImported={}", isImported);
        return Boolean.parseBoolean(isImported);
    }

}
