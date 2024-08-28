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

package com.ericsson.oss.services.pm.initiation.model.metadata;

import static com.ericsson.oss.services.pm.initiation.model.utils.PmMetaDataConstants.OSS_DEFINED_REG_EXP;
import static com.ericsson.oss.services.pm.initiation.model.utils.PmMetaDataConstants.STAR_CHAR_APPENDER;
import static com.ericsson.oss.services.pm.initiation.model.utils.PmMetaDataConstants.NE_DEFINED_PREFIX;

import java.util.Collection;
import java.util.HashSet;
import java.util.regex.Pattern;
import javax.inject.Inject;

import org.slf4j.Logger;

import com.ericsson.oss.itpf.modeling.modelservice.typed.TypedModelAccess;
import com.ericsson.oss.itpf.modeling.modelservice.typed.core.target.TargetTypeInformation;
import com.ericsson.oss.itpf.modeling.modelservice.typed.core.target.TargetTypeVersionInformation;
import com.ericsson.oss.pmic.dto.subscription.enums.SubscriptionType;
import com.ericsson.oss.services.pm.initiation.model.utils.PmMetaDataConstants;
import com.ericsson.services.pm.initiation.restful.api.MetaDataType;

/**
 * The type Pm meta data helper.
 */
public class PmMetaDataHelper {

    private static final String CIRCUMFLEX = "^";
    private static final String WILD = "(.*)";

    @Inject
    private Logger log;

    @Inject
    private TypedModelAccess typedModelAccess;

    /**
     * This method would return model URNs for the given pfmMetaDataPattern.
     *
     * @param neType
     *         - Network Element Type
     * @param ossModelIdentity
     *         - Operations Support Systems model identity of node
     * @param pfmMetaDataPattern
     *         - the pfm meta data pattern
     *
     * @return - returns a collection of pfmModelUrns
     * @see PmMetaDataConstants
     */
    public Collection<String> getMetaDataUrnsFromModelService(final String neType, final String ossModelIdentity, final String pfmMetaDataPattern) {
        log.debug("Starting searching modelUrns for node {} with ossModelIdentity {}, pfmMetaDataPattern{}", neType, ossModelIdentity,
                pfmMetaDataPattern);
        Collection<String> pfmModelUrns = new HashSet<>();
        try {
            final TargetTypeInformation targetTypeInformation = typedModelAccess.getModelInformation(TargetTypeInformation.class);
            final TargetTypeVersionInformation targetTypeVersionInformation = targetTypeInformation
                    .getTargetTypeVersionInformation(TargetTypeInformation.CATEGORY_NODE, neType);

            String modelUrn = pfmMetaDataPattern.contains(PmMetaDataConstants.PFM_EVENT_PATTERN)
                    ? PmMetaDataConstants.PFM_EVENT_PATTERN + STAR_CHAR_APPENDER : PmMetaDataConstants.PFM_MEASUREMENT_PATTERN + STAR_CHAR_APPENDER;

            modelUrn = Pattern.matches(OSS_DEFINED_REG_EXP, pfmMetaDataPattern) ?
                    modelUrn + pfmMetaDataPattern.substring(pfmMetaDataPattern.lastIndexOf(NE_DEFINED_PREFIX)) + STAR_CHAR_APPENDER :
                    getNeDefinedModelUrn(pfmMetaDataPattern, modelUrn);

            log.info("Model Urn {} from [{}]", modelUrn, pfmMetaDataPattern);
            final String mostRecentTmi = targetTypeVersionInformation.getMostAppropriateTmiForTarget(ossModelIdentity, modelUrn);
            log.info("Target model identity (TMI) for neType {} ossModelIdentity {} and modelUrn {} is {}", neType, ossModelIdentity, modelUrn,
                    mostRecentTmi);

            final Collection<String> supportedModelUrns = targetTypeVersionInformation.getSupportedModels(mostRecentTmi);
            log.debug("Model Urns for neType {}, ossModelIdentity {} existing in model service are {}", neType, ossModelIdentity, supportedModelUrns);
            pfmModelUrns = filterPfmMetaDataUrns(supportedModelUrns, pfmMetaDataPattern);
            log.debug("Filtered pfm Model Urns for neType {}, ossModelIdentity {} are {}", neType, ossModelIdentity, pfmModelUrns);
        } catch (final IllegalArgumentException ex) {
            log.info("No models available for ne type {} ossModelIdentity {}.", neType, ossModelIdentity);
        }
        return pfmModelUrns;
    }

    private String getNeDefinedModelUrn(final String pfmMetaDataPattern, final String modelUrn) {
        return pfmMetaDataPattern.contains(PmMetaDataConstants.NE_DEFINED_PATTERN) ?
                modelUrn + PmMetaDataConstants.NE_DEFINED_PATTERN + STAR_CHAR_APPENDER :
                modelUrn + "/*/*";
    }

    /**
     * Gets meta data pattern from meta data type
     *
     * @param metaDataType
     *         the meta data type
     *
     * @return the meta data pattern
     */
    public String getMetaDataPattern(final MetaDataType metaDataType) {
        String metaDataPattern = null;
        if (metaDataType.equals(MetaDataType.COUNTERS)) {
            metaDataPattern = PmMetaDataConstants.PFM_MEASUREMENT_PATTERN;
        } else {
            metaDataPattern = PmMetaDataConstants.PFM_EVENT_PATTERN;
        }
        return metaDataPattern;
    }

    /**
     * Gets meta data type from subscription type
     *
     * @param type
     *         the subscription type
     *
     * @return the meta data type
     */
    public MetaDataType getMetaDataType(final SubscriptionType type) {
        MetaDataType metaData = MetaDataType.EVENTS;
        if (type.equals(SubscriptionType.STATISTICAL) || type.equals(SubscriptionType.MOINSTANCE) || type.equals(SubscriptionType.EBS)) {
            metaData = MetaDataType.COUNTERS;
        }
        return metaData;
    }

    private Collection<String> filterPfmMetaDataUrns(final Collection<String> modelUrns, final String pfmMetaDataPattern) {
        final Collection<String> pfmMetaDataModelUrns = new HashSet<>();
        final String pattern = CIRCUMFLEX + pfmMetaDataPattern + WILD;
        for (final String modelUrn : modelUrns) {
            if (Pattern.matches(pattern, modelUrn)) {
                pfmMetaDataModelUrns.add(modelUrn);
            }
        }
        return pfmMetaDataModelUrns;
    }
}
