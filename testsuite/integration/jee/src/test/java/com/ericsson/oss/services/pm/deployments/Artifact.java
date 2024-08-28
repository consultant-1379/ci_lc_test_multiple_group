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

import java.io.File;

import org.jboss.shrinkwrap.resolver.api.maven.Maven;

/**
 * Constants defined for Maven
 */

public class Artifact {

    public static final String COM_ERICSSON_OSS_ITPF_SDK___DIST_JAR = "com.ericsson.oss.itpf.sdk:service-framework-dist:jar";

    // public static final String COM_ERICSSON_OSS_MEDIATION_SDK_MODELS_JAR = "com.ericsson.nms.mediation:core-mediation-models-api:jar";
    public static final String COM_ERICSSON_OSS_MEDIATION_SDK_MODELS_JAR = "com.ericsson.oss.mediation:mediation-sdk-event-models-jar:jar";

    // Mediation models
    public static final String COM_ERICSSON_NMS_OP_TYPE = "com.ericsson.nms.mediation:core-mediation-models-api:jar";
    public static final String COM_ERICSSON_NMS_MEDIATION_SDK_EVENT_MODEL = "com.ericsson.nms.mediation:core-mediation-api:jar";

    // PMIC models
    public static final String COM_ERICSSON_NMS_PM_SERVICE_EVENT_MODEL = "com.ericsson.oss.services.pmic:pmic-service-common-module-model-event:jar";

    public static final String PMIC_API_JAR = "com.ericsson.oss.pmic:pm-service-api:jar";
    public static final String PMIC_JAR_JAR = "com.ericsson.oss.pmic:pm-service-jar:jar";
    public static final String PMIC_EJB_JAR = "com.ericsson.oss.pmic:pm-service-ejb:jar";
    public static final String PMIC_EVENT_MODELS_JAR = "com.ericsson.oss.services.pmic:pmic-service-common-module-model-event:jar";
    public static final String PMIC_MODELS_JAR = "com.ericsson.oss.pmic:pm-service-jar:jar";

    public static final String COM_ERICSSON_OSS_PM_SERVICE_EAR = "com.ericsson.oss.pmic:pm-service-ear:ear:?";

    public static final String COM_ERICSSON_OSS_ITPF_MODELING__MODEL_SERVICE_API_JAR = "com.ericsson.oss.itpf.modeling:model-service-api-jar:jar";
    public static final String COM_ERICSSON_OSS_ITPF_MODELING__MODEL_COMMON_JAR = "com.ericsson.oss.itpf.modeling:modeling-common-jar:jar";

    public static final String COM_ERICSSON_OSS_DPS_API_JAR = "com.ericsson.oss.itpf.datalayer.dps:dps-api:jar";

    public static final String ORG_APACHE_POI = "org.apache.poi:poi-ooxml";

    public static final String REST_ASSURED_API = "io.rest-assured:rest-assured";

    public static final String AWAITILITY = "org.awaitility:awaitility";

    public static final String SDK_RESOURCE_API = "com.ericsson.oss.itpf.sdk:sdk-resources-api";

    public static final String JODA_TIME = "joda-time:joda-time:jar";

    public static final String APACHE_HTTP_CLIENT = "org.apache.httpcomponents:httpclient:jar";

    public static final String GSON = "com.google.code.gson:gson";

    public static final String SHARED_SERVICE_API = "com.ericsson.oss.services.test:shared-services-helper-api";

    public static final String PATH_TO_SEARCH_SUBSCRIPTIONS_AND_NODES = "src/test/resources/subscription/CellTraceSubscription";

    // Mockito
    public static final String MOCKITO_ALL = "org.mockito:mockito-all";

    public static final File BEANS_XML_FILE = new File("src/test/resources/META-INF/beans.xml");

    public static final File MANIFEST_MF_FILE = new File("src/test/resources/META-INF/MANIFEST.MF");

    public static final File DEPLOYMENT_STRUCTURE = new File("src/test/resources/META-INF/jboss-deployment-structure.xml");
    public static final File[] SUBSCRIPTION_DATA = new File("src/test/resources/subscription_data/").listFiles();
    public static final String SUBSCRIPTION_JSON_FILES = "src/test/resources/subscription/";
    public static final String NODE_JSON_FILES = "src/test/resources/node_data/";

    private static final String POM_XML = "pom.xml";

    public static File resolveArtifactWithoutDependencies(final String artifactCoordinates) {
        return Maven.resolver().loadPomFromFile(POM_XML).resolve(artifactCoordinates).withoutTransitivity().asSingleFile();
    }

    public static File[] resolveArtifactWithDependencies(final String artifactCoordinates) {
        return Maven.resolver().loadPomFromFile(POM_XML).resolve(artifactCoordinates).withTransitivity().asFile();
    }
}
