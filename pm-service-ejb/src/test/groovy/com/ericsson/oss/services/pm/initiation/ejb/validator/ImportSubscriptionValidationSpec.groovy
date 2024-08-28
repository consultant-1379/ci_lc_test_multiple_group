/*
 * ------------------------------------------------------------------------------
 *  ********************************************************************************
 *  * COPYRIGHT Ericsson  2017
 *  *
 *  * The copyright to the computer program(s) herein is the property of
 *  * Ericsson Inc. The programs may be used and/or copied only with written
 *  * permission from Ericsson Inc. or in accordance with the terms and
 *  * conditions stipulated in the agreement/contract under which the
 *  * program(s) have been supplied.
 *  *******************************************************************************
 *  *----------------------------------------------------------------------------
 */

package com.ericsson.oss.services.pm.initiation.ejb.validator

import static com.ericsson.oss.pmic.dto.node.enums.NetworkElementType.ERBS
import static com.ericsson.oss.pmic.dto.subscription.enums.AdministrationState.INACTIVE
import static com.ericsson.oss.pmic.dto.subscription.enums.CellTraceCategory.*
import static com.ericsson.oss.services.pm.initiation.constants.ApplicationMessages.COUNTERS_NOT_ALLOWED_FOR_EBS_FILE_WITH_STREAMING_OUTPUTMODE

import org.codehaus.jackson.map.DeserializationConfig
import org.codehaus.jackson.map.ObjectMapper
import org.codehaus.jackson.map.SerializationConfig
import spock.lang.Unroll

import com.ericsson.cds.cdi.support.providers.custom.sfwk.PropertiesForTest
import com.ericsson.cds.cdi.support.providers.custom.sfwk.SuppliedProperty
import com.ericsson.cds.cdi.support.rule.ImplementationClasses
import com.ericsson.cds.cdi.support.rule.ImplementationInstance
import com.ericsson.cds.cdi.support.rule.ObjectUnderTest
import com.ericsson.oss.itpf.datalayer.dps.persistence.ManagedObject
import com.ericsson.oss.pmic.cdi.test.util.PmBaseSpec
import com.ericsson.oss.pmic.dto.node.Node
import com.ericsson.oss.pmic.dto.node.enums.NetworkElementType
import com.ericsson.oss.pmic.dto.subscription.*
import com.ericsson.oss.pmic.dto.subscription.cdts.*
import com.ericsson.oss.pmic.dto.subscription.enums.*
import com.ericsson.oss.pmic.impl.counters.PmCountersLifeCycleResolverImpl
import com.ericsson.oss.pmic.impl.modelservice.PmCapabilitiesLookup
import com.ericsson.oss.pmic.impl.modelservice.PmCapabilityReaderImpl
import com.ericsson.oss.services.pm.cache.PmFunctionEnabledWrapper
import com.ericsson.oss.services.pm.exception.NodeNotFoundDataAccessException
import com.ericsson.oss.services.pm.initiation.common.DateUtils
import com.ericsson.oss.services.pm.initiation.constants.ApplicationMessages
import com.ericsson.oss.services.pm.initiation.model.metadata.PMICModelDeploymentValidator
import com.ericsson.oss.services.pm.services.exception.PfmDataException
import com.ericsson.oss.services.pm.services.exception.PmFunctionValidationException
import com.ericsson.oss.services.pm.services.exception.ValidationException
import com.ericsson.oss.services.pm.services.generic.SubscriptionReadOperationServiceImpl

class ImportSubscriptionValidationSpec extends PmBaseSpec {

    public static final String LTE02_ERBS00021 = "LTE02ERBS00021"

    @ObjectUnderTest
    SubscriptionReadOperationServiceImpl subscriptionReadOperationService

    private List<Node> neList = new ArrayList<Node>()
    private int nodeListIdentity

    private static Date now = new Date()
    private static DateUtils dateUtils = new DateUtils()

    private Subscription subscription

    private static final String WRONG_NE_TYPE_TO_BE_REPLACED = "Wrong Ne Type"
    private static final String WRONG_OSS_MODEL_IDENTITY_TO_BE_REPLACED = "Wrong ossModelIdentity"

    private static final String NOT_EXISTENT_NODE_NAME = "NotExistentNode"
    private static final String EMPTY_NODE_NAME = ""
    private static final String NODE_NAME_SGSNMME = "Node_SGSNMME"
    private static final String NODE_NAME_ERBS_1 = "Node_ERBS_1"
    private static final String NODE_NAME_RADIO_NODE = "Node_RadioNode"
    private static final String NODE_NAME_ERBS_FAKE_CUSTOM_NODE = "Node_NoOssModelIdentity_NoCounters"
    private static final String NODE_NAME_ERBS_2 = "Node_ERBS_2"
    private static final String NODE_NAME_RNC = "Node_RNC"
    private static final String NODE_NAME_RNC_2 = "Node_RNC_2"
    private static final String NODE_NAME_RNC_3 = "Node_RNC_3"
    private static final String NODE_NAME_ESA1 = "ESA01"
    private static final String NODE_NAME_EPG = "Node_EPG"
    private static final String NODE_NAME_FRONTHAUL6020 = "Node_FRONTHAUL6020"
    private static final String NODE_NAME_FRONTHAUL6080 = "Node_FRONTHAUL6080"
    private static final String NODE_NAME_SBG_IS = "Node_SBG-IS"
    private static final String NODE_NAME_SIU2 = "Node_SIU02"
    private static final String NODE_NAME_TCU02 = "Node_TCU02"
    private static final String NODE_NAME_VEPG = "Node_VEPG"
    private static final String NODE_NAME_vwMG = "Node_vwMG"
    private static final String NODE_NAME_WMG = "Node_WMG"
    private static final String NODE_TYPE_ESA = "GenericESA"
    private static final String NODE_TYPE_EPG = "EPG"
    private static final String NODE_TYPE_FRONTHAUL6020 = "FRONTHAUL-6020"
    private static final String NODE_TYPE_FRONTHAUL6080 = "FRONTHAUL-6080"
    private static final String NODE_TYPE_SBG_IS = "SBG-IS"
    private static final String NODE_TYPE_SIU2 = "SIU02"
    private static final String NODE_TYPE_TCU02 = "TCU02"
    private static final String NODE_TYPE_VEPG = "VEPG"
    private static final String NODE_TYPE_vwMG = "vwMG"
    private static final String NODE_TYPE_WMG = "WMG"


    def static VALID_COUNTER = new CounterInfo("pmHoExeAttAto", "UtranCellRelation")
    def static INVALID_COUNTER = new CounterInfo("Invalid_Counter", "Invalid_Counter_Group")
    def static INVALID_EVENT = new EventInfo("INVALID_EVENT", "INVALID_EVENT_GROUP")
    def static VALID_EVENT = new EventInfo("INTERNAL_EVENT_ANR_CONFIG_MISSING", "INTERNAL")
    def static CELTRACE_BASE_EVENT_FOR_COUNTER_1 = new EventInfo("INTERNAL_PER_PROCESSOR_LOAD", "INTERNAL_PERIODIC")
    def static CELTRACE_BASE_EVENT_FOR_COUNTER_2 = new EventInfo("INTERNAL_PER_PROCESSOR_LOAD", "CAPACITY_MANAGEMENT_EVALUATION")
    def static CELTRACE_BASE_EVENT_FOR_COUNTER_3 = new EventInfo("INTERNAL_PER_PROCESSOR_LOAD", "GENERAL_EVALUATION")
    def static CELTRACE_COUNTER_EVENT_BASED = new CounterInfo("pmProcessorLoadSamp", "ProcessorLoad")

    def static CELLTRACE_EVENTS_FOR_COUNTER_LIST = [CELTRACE_BASE_EVENT_FOR_COUNTER_1, CELTRACE_BASE_EVENT_FOR_COUNTER_2, CELTRACE_BASE_EVENT_FOR_COUNTER_3]
    def static CELLTRACE_EVENTS_FOR_COUNTER_LIST_DUPLICATES = [CELTRACE_BASE_EVENT_FOR_COUNTER_1, CELTRACE_BASE_EVENT_FOR_COUNTER_2, CELTRACE_BASE_EVENT_FOR_COUNTER_3,
                                                               CELTRACE_BASE_EVENT_FOR_COUNTER_1, CELTRACE_BASE_EVENT_FOR_COUNTER_2, CELTRACE_BASE_EVENT_FOR_COUNTER_3]
    def static COMBINED_EVENT_LIST = [CELTRACE_BASE_EVENT_FOR_COUNTER_1, CELTRACE_BASE_EVENT_FOR_COUNTER_2, CELTRACE_BASE_EVENT_FOR_COUNTER_3, VALID_EVENT]

    @ImplementationInstance
    PmFunctionEnabledWrapper mockedPmFunctionEnabledWrapper = Mock(PmFunctionEnabledWrapper)

    @ImplementationInstance
    private PMICModelDeploymentValidator mockedPmicModelDeploymentValidator = Mock(PMICModelDeploymentValidator)

    @ImplementationClasses
    def classes = [PmCapabilitiesLookup.class, PmCapabilityReaderImpl.class, PmCountersLifeCycleResolverImpl.class]

    def setup() {
        mockedPmicModelDeploymentValidator.isCounterValidationSupportedForGivenTargetType(_) >> true
    }

    @Unroll
    def "When validating an imported subscription of class=#subscriptionClass, type=#subscriptionType then it should throw InvalidSubscriptionException"() {
        given:
        subscription = subscriptionClass.getIdentifier().newInstance() as Subscription
        subscription.setType(subscriptionType)
        subscription.setName("SubscriptionTestName")
        subscription.setUserType(UserType.USER_DEF)
        subscription.setAdministrationState(INACTIVE)
        subscription.setIsImported(true)

        when: "subscription is validated"
        subscriptionReadOperationService.validateAndAdjustSubscriptionData(subscription)

        then: "exception should be thrown and node retained, attributes replaced with the correct ones"
        def exception = thrown(ValidationException)
        exception.message.contains("@class and type parameters are not equal: ")
        exception.message.contains(subscriptionClass.getIdentifier().getName())
        exception.message.contains(subscriptionType.toString())

        where:
        subscriptionClass << [SubscriptionType.MOINSTANCE]
        subscriptionType << [SubscriptionType.STATISTICAL]
    }

    @Unroll
    def "When validating a subscription of type=#subscriptionType, name=#testData.subscriptionName, userType=#testData.userType, isImported=#testData.isImported then it should throw Exception with message #testData.exceptionMessage"(SubscriptionType subscriptionType, Object testData) {
        given:
        subscription = subscriptionType.getIdentifier().newInstance() as Subscription
        subscription.setName(testData.subscriptionName)
        subscription.setUserType(testData.userType)
        subscription.setType(subscriptionType)
        subscription.setIsImported(testData.isImported)

        when: "subscription is validated"
        subscriptionReadOperationService.validateAndAdjustSubscriptionData(subscription)

        then: "exception should be thrown"
        def exception = thrown(ValidationException)
        exception.message == testData.exceptionMessage

        where:
        [subscriptionType, testData] << [
                SubscriptionType.values().findAll {
                    !(it in [SubscriptionType.EVENTS, SubscriptionType.EBS, SubscriptionType.RPMO, SubscriptionType.GPEH, SubscriptionType.RES, SubscriptionType.CELLTRAFFIC, SubscriptionType.CELLRELATION, SubscriptionType.RESOURCE, SubscriptionType.BSCRECORDINGS, SubscriptionType.MTR])
                },
                [
                        //Test Data
                        [subscriptionName: null, userType: UserType.SYSTEM_DEF, exceptionMessage: ApplicationMessages.SUBSCRIPTION_NAME_TAG_MISSING, isImported: false],
                        [subscriptionName: "", userType: UserType.USER_DEF, exceptionMessage: ApplicationMessages.EMPTY_SUBSCRIPTION_NAME, isImported: false],
                        [subscriptionName: "   ", userType: UserType.USER_DEF, exceptionMessage: "   " + ApplicationMessages.EMPTY_SUBSCRIPTION_NAME, isImported: false],
                        [subscriptionName: "\",=\\[\\]{}~`", userType: UserType.USER_DEF, exceptionMessage: "\",=\\[\\]{}~`" + ApplicationMessages.NOTVALID_SUBSCRIPTION_NAME, isImported: false],
                        [subscriptionName: null, userType: null, exceptionMessage: ApplicationMessages.SUBSCRIPTION_NAME_TAG_MISSING, isImported: false],
                        [subscriptionName: null, userType: UserType.SYSTEM_DEF, exceptionMessage: ApplicationMessages.SUBSCRIPTION_NAME_TAG_MISSING, isImported: true],
                        [subscriptionName: "", userType: UserType.USER_DEF, exceptionMessage: ApplicationMessages.EMPTY_SUBSCRIPTION_NAME, isImported: true],
                        [subscriptionName: "   ", userType: UserType.USER_DEF, exceptionMessage: "   " + ApplicationMessages.EMPTY_SUBSCRIPTION_NAME, isImported: true],
                        [subscriptionName: "\",=\\[\\]{}~`", userType: UserType.USER_DEF, exceptionMessage: "\",=\\[\\]{}~`" + ApplicationMessages.NOTVALID_SUBSCRIPTION_NAME, isImported: true],
                        [subscriptionName: null, userType: null, exceptionMessage: ApplicationMessages.SUBSCRIPTION_NAME_TAG_MISSING, isImported: true],
                ]
        ].combinations()
    }

    @Unroll
    def "When validating a subscription of type=#subscriptionType, name=#testData.subscriptionName, userType=#testData.userType, isImported=#testData.isImported, then it should NOT throw Exception"(SubscriptionType subscriptionType, Object testData) {
        given:
        subscription = subscriptionType.getIdentifier().newInstance() as Subscription
        subscription.setName(testData.subscriptionName)
        subscription.setUserType(testData.userType)
        subscription.setType(subscriptionType)
        subscription.setIsImported(testData.isImported)

        when: "subscription is validated"
        subscriptionReadOperationService.validateAndAdjustSubscriptionData(subscription)

        then: "exception should not be thrown"
        noExceptionThrown()

        where:
        [subscriptionType, testData] << [
                SubscriptionType.values().findAll {
                    !(it in [SubscriptionType.EVENTS, SubscriptionType.EBS, SubscriptionType.RPMO, SubscriptionType.GPEH, SubscriptionType.RES, SubscriptionType.CELLTRAFFIC, SubscriptionType.CELLRELATION, SubscriptionType.RESOURCE, SubscriptionType.BSCRECORDINGS, SubscriptionType.MTR])
                },
                [
                        //Test Data
                        [subscriptionName: "!#\$%&'()*+-./:<>?@\\^_|", userType: UserType.USER_DEF, isImported: true],
                        [subscriptionName: "TestSub", userType: UserType.SYSTEM_DEF, isImported: true],
                        [subscriptionName: "TestSub", userType: UserType.USER_DEF, isImported: true],
                        [subscriptionName: "TestStats Sub", userType: UserType.USER_DEF, isImported: true],
                        [subscriptionName: "TestStats %&'()", userType: UserType.USER_DEF, isImported: true],
                        [subscriptionName: "\$%&TestSub", userType: UserType.SYSTEM_DEF, isImported: true],
                        [subscriptionName: "|TestSub./:<", userType: UserType.USER_DEF, isImported: true]
                ]
        ].combinations()
    }

    @Unroll
    def "When validating a subscription of type=#subscriptionType with scheduleInfo=[#testData.startDateTime:#testData.endDateTime] and ropPeriod=#testData.ropPeriod and isImported=#testData.isImported then it should throw Exception with message #testData.exceptionMessage"(SubscriptionType subscriptionType, Object testData) {
        given:
        subscription = subscriptionType.getIdentifier().newInstance() as Subscription
        subscription.setName("SubscriptionTestName")
        subscription.setUserType(UserType.USER_DEF)
        subscription.setRop(testData.ropPeriod)
        subscription.setScheduleInfo(new ScheduleInfo(testData.startDateTime, testData.endDateTime))
        subscription.setType(subscriptionType)
        subscription.setIsImported(testData.isImported)

        when: "subscription is validated"
        subscriptionReadOperationService.validateAndAdjustSubscriptionData(subscription)

        then: "exception should be thrown"
        def exception = thrown(ValidationException)
        exception.message == testData.exceptionMessage

        where:
        [subscriptionType, testData] << [
                SubscriptionType.values().findAll {
                    !(it in [SubscriptionType.EVENTS, SubscriptionType.EBS, SubscriptionType.RPMO, SubscriptionType.GPEH, SubscriptionType.RES, SubscriptionType.CELLTRAFFIC, SubscriptionType.CELLRELATION, SubscriptionType.RESOURCE, SubscriptionType.BSCRECORDINGS, SubscriptionType.MTR])
                },
                [
                        //Test Data
                        //Negative test: Validation test for a subscription with schedule info that [ startDateTime = endDateTime ]
                        [ropPeriod: RopPeriod.ONE_MIN, startDateTime: dateUtils.addMinutesOnDate(now, 100), endDateTime: dateUtils.addMinutesOnDate(now, 100), exceptionMessage: ApplicationMessages.ENDTIME_GREATER_START_TIME, isImported: true],
                        //Negative test: Validation test for subscription with schedule info that [ endDateTime < currentTime ]
                        [ropPeriod: RopPeriod.ONE_HOUR, startDateTime: dateUtils.addMinutesOnDate(now, -60), endDateTime: dateUtils.addMinutesOnDate(now, -30), exceptionMessage: ApplicationMessages.ENDTIME_GREATER_CURRENT_TIME, isImported: true],
                        //Negative test: Validation test for subscription with schedule info that with only endDateTime and [ endDateTime < currentTime ]
                        [ropPeriod: RopPeriod.ONE_HOUR, startDateTime: null, endDateTime: dateUtils.addMinutesOnDate(now, -30), exceptionMessage: ApplicationMessages.ENDTIME_GREATER_CURRENT_TIME, isImported: true],
                        //Negative test: Validation test for subscription with schedule info that [ endDateTime < (current + rop) ]
                        [ropPeriod: RopPeriod.ONE_HOUR, startDateTime: dateUtils.addMinutesOnDate(now, -60), endDateTime: dateUtils.addMinutesOnDate(now, 30), exceptionMessage: ApplicationMessages.ENDTIME_EQUAL_OR_BEFORE_CURRENTTIME_PLUS_ROP, isImported: true],
                        //Negative test: Validation test for subscription with schedule info that have only invalidate endDateTime [ endDateTime < (current + rop) ]
                        [ropPeriod: RopPeriod.ONE_HOUR, startDateTime: null, endDateTime: dateUtils.addMinutesOnDate(now, 30), exceptionMessage: ApplicationMessages.ENDTIME_EQUAL_OR_BEFORE_CURRENTTIME_PLUS_ROP, isImported: true],
                        //Negative test:  Validation test for subscription have only invalid endDateTime during next available_ROP
                        [ropPeriod: RopPeriod.ONE_HOUR, startDateTime: null, endDateTime: new Date(now.getTime() + (1000 * 60 * 15)), exceptionMessage: ApplicationMessages.ENDTIME_EQUAL_OR_BEFORE_CURRENTTIME_PLUS_ROP, isImported: true]
                ]
        ].combinations()
    }

    @Unroll
    def "When validating a subscription of type=#subscriptionType and NO RopPeriod and isImported=true then it should NOT throw Exception default 15min used"() {
        given:
        subscription = subscriptionType.getIdentifier().newInstance() as Subscription
        subscription.setName("SubscriptionTestName")
        subscription.setUserType(UserType.SYSTEM_DEF)
        subscription.setType(subscriptionType)
        subscription.setIsImported(true)

        when: "subscription is validated"
        subscriptionReadOperationService.validateAndAdjustSubscriptionData(subscription)

        then: "exception not should be thrown"
        noExceptionThrown()
        subscription.rop == RopPeriod.FIFTEEN_MIN

        where:
        subscriptionType << SubscriptionType.values().findAll {
            !(it in [SubscriptionType.EVENTS, SubscriptionType.EBS, SubscriptionType.GPEH, SubscriptionType.RPMO, SubscriptionType.RES, SubscriptionType.CELLTRAFFIC, SubscriptionType.CELLRELATION, SubscriptionType.RESOURCE, SubscriptionType.BSCRECORDINGS, SubscriptionType.MTR])
        }
    }

    @Unroll
    def "When validating a subscription of type=#subscriptionType with scheduleInfo=[#testData.startDateTime:#testData.endDateTime] and ropPeriod=#testData.ropPeriod and isImported=#testData.isImported then it should throw Exception for 24 ROP period with message #testData.exceptionMessage"(SubscriptionType subscriptionType, Object testData) {
        given:
        subscription = subscriptionType.getIdentifier().newInstance() as Subscription
        subscription.setName("SubscriptionTestName")
        subscription.setUserType(UserType.USER_DEF)
        subscription.setRop(testData.ropPeriod)
        subscription.setScheduleInfo(new ScheduleInfo(testData.startDateTime, testData.endDateTime))
        subscription.setType(subscriptionType)

        when: "subscription is validated"
        subscriptionReadOperationService.validateAndAdjustSubscriptionData(subscription)

        then: "exception should be thrown"
        def exception = thrown(ValidationException)
        exception.message == testData.exceptionMessage

        where:
        [subscriptionType, testData] << [
                SubscriptionType.values().findAll {
                    (it in [SubscriptionType.STATISTICAL])
                },
                [
                        //Test Data
                        //Negative test:  Validation test for subscription have only invalid endDateTime during next available_ROP for 24 ROP
                        [ropPeriod: RopPeriod.ONE_DAY, startDateTime: null, endDateTime: new Date(now.getTime() + (1000 * 60 * 60 * 24)), exceptionMessage: ApplicationMessages.ENDTIME_EQUAL_OR_BEFORE_CURRENTTIME_PLUS_ROP, isImported: true]
                ]
        ].combinations()
    }

    @Unroll
    def "When validating a subscription of type=#subscriptionType with Valid IP Address and OutputMode=STREAMING and isImported=true, it should NOT throw Exception"() {
        given:
        subscription = subscriptionType.getIdentifier().newInstance() as EventSubscription
        subscription.setName("SubscriptionTestName")
        subscription.setUserType(UserType.SYSTEM_DEF)
        subscription.setType(subscriptionType)
        subscription.setOutputMode(OutputModeType.STREAMING)
        subscription.setStreamInfoList([new StreamInfo("128.2.2.2", 22)])
        subscription.setIsImported(true)

        when: "subscription is validated"
        subscriptionReadOperationService.validateAndAdjustSubscriptionData(subscription)

        then: "exception no should be thrown"
        noExceptionThrown()

        where:
        subscriptionType << [SubscriptionType.CELLTRACE, SubscriptionType.CONTINUOUSCELLTRACE]
    }

    @Unroll
    def "When validating a subscription of type=#subscriptionType with Valid IP Address and OutputMode=STREAMING and isImported=true, it should throw Exception"() {
        given:
        subscription = subscriptionType.getIdentifier().newInstance() as EventSubscription
        subscription.setName("SubscriptionTestName")
        subscription.setUserType(UserType.USER_DEF)
        subscription.setType(subscriptionType)
        subscription.setOutputMode(OutputModeType.STREAMING)
        subscription.setStreamInfoList([new StreamInfo("128.2.2.2", 22)])
        subscription.setIsImported(true)

        when: "subscription is validated"
        subscriptionReadOperationService.validateAndAdjustSubscriptionData(subscription)

        then: "exception should be thrown"
        def exception = thrown(ValidationException)
        def errorMessage = "Invalid OutputMode " + "STREAMING for subscription type " + subscriptionType
        exception.message == errorMessage

        where:
        subscriptionType << [SubscriptionType.EBM]
    }

    def "When validating a subscription of type=UETRACE with Valid IP Address and OutputMode=STREAMING and isImported=true , it should NOT throw Exception"() {
        given:
        subscription = new UETraceSubscription()
        subscription.setType(SubscriptionType.UETRACE)
        subscription.setName("SubscriptionTestName")
        subscription.setUserType(UserType.SYSTEM_DEF)
        subscription.setOutputMode(OutputModeType.STREAMING)
        subscription.setStreamInfo(new StreamInfo("128.2.2.2", 22))
        subscription.setIsImported(true)

        when: "subscription is validated"
        subscriptionReadOperationService.validateAndAdjustSubscriptionData(subscription)

        then: "exception no should be thrown"
        noExceptionThrown()
    }

    @Unroll
    def "When validating a subscription of type=#subscriptionType with OutputMode=FILE and Valid IP Address and isImported=true , it should throw Exception"() {
        given:
        subscription = subscriptionType.getIdentifier().newInstance() as EventSubscription
        subscription.setName("SubscriptionTestName")
        subscription.setUserType(UserType.SYSTEM_DEF)
        subscription.setType(subscriptionType)
        subscription.setOutputMode(OutputModeType.FILE)
        subscription.setStreamInfoList([new StreamInfo("128.2.2.2", 22), new StreamInfo("192.128.1.3", 22), new StreamInfo("255.255.255.255", 22)])
        subscription.setIsImported(true)

        when: "subscription is validated"
        subscriptionReadOperationService.validateAndAdjustSubscriptionData(subscription)

        then: "exception should be thrown"
        def exception = thrown(ValidationException)
        exception.message == ApplicationMessages.NOTVALID_EVENT_FILEMODE_PARAMS

        where:
        subscriptionType << [SubscriptionType.CELLTRACE, SubscriptionType.EBM, SubscriptionType.CONTINUOUSCELLTRACE]
    }

    def "When validating a subscription of type=UETRACE with OutputMode=FILE and Valid IP Address, it should throw Exception"() {
        given:
        subscription = new UETraceSubscription()
        subscription.setType(SubscriptionType.UETRACE)
        subscription.setName("SubscriptionTestName")
        subscription.setUserType(UserType.SYSTEM_DEF)
        subscription.setOutputMode(OutputModeType.FILE)
        subscription.setStreamInfo(new StreamInfo("128.2.2.2", 22))
        subscription.setIsImported(true)

        when: "subscription is validated"
        subscriptionReadOperationService.validateAndAdjustSubscriptionData(subscription)

        then: "exception should be thrown"
        def exception = thrown(ValidationException)
        exception.message == ApplicationMessages.NOTVALID_EVENT_FILEMODE_PARAMS
    }

    @Unroll
    def "When validating a subscription of type=#subscriptionType with OutputMode=FILE and IP Address is Empty and isImported=true , it should NOT throw Exception"() {
        given:
        subscription = subscriptionType.getIdentifier().newInstance() as EventSubscription
        subscription.setName("SubscriptionTestName")
        subscription.setUserType(UserType.USER_DEF)
        subscription.setType(subscriptionType)
        subscription.setOutputMode(OutputModeType.FILE)
        subscription.setIsImported(true)

        when: "subscription is validated"
        subscriptionReadOperationService.validateAndAdjustSubscriptionData(subscription)

        then: "exception should not be thrown"
        noExceptionThrown()

        where:
        subscriptionType << [SubscriptionType.CELLTRACE, SubscriptionType.EBM, SubscriptionType.CONTINUOUSCELLTRACE]
    }

    @Unroll
    def "When validating a subscription of type=#subscriptionType with Invalid IPv6 Address and isImported=true , it should throw Exception"() {
        given:
        subscription = subscriptionType.getIdentifier().newInstance() as EventSubscription
        subscription.setName("SubscriptionTestName")
        subscription.setUserType(UserType.SYSTEM_DEF)
        subscription.setType(subscriptionType)
        subscription.setOutputMode(OutputModeType.STREAMING)
        subscription.setStreamInfoList([
                new StreamInfo("20012:db8:a0b:12f0:0::1", 22),
                new StreamInfo("2607:f0d0:1002:51::4:22", 22),
                new StreamInfo("FE80:0000:0000:00040:0202:B3FF:AEFF:8329", 22)
        ])
        subscription.setIsImported(true)

        when: "subscription is validated"
        subscriptionReadOperationService.validateAndAdjustSubscriptionData(subscription)

        then: "exception should be thrown"
        def exception = thrown(ValidationException)
        exception.message == ApplicationMessages.INVALID_IP_FORMAT

        where:
        subscriptionType << [SubscriptionType.CELLTRACE, SubscriptionType.CONTINUOUSCELLTRACE]
    }

    @Unroll
    def "When validating a subscription of type=#subscriptionType with Valid IPv6 Address and isImported=true, it should NOT throw Exception"() {
        given:
        subscription = subscriptionType.getIdentifier().newInstance() as EventSubscription
        subscription.setName("SubscriptionTestName")
        subscription.setUserType(UserType.SYSTEM_DEF)
        subscription.setType(subscriptionType)
        subscription.setOutputMode(OutputModeType.STREAMING)
        subscription.setStreamInfoList([new StreamInfo("2607:f0d0:1002:51::4", 22), new StreamInfo("2001:db8:a0b:12f0:0::1", 22), new StreamInfo("FE80:0000:0000:0000:0202:B3FF:AEFF:8329", 22)])
        subscription.setIsImported(true)

        when: "subscription is validated"
        subscriptionReadOperationService.validateAndAdjustSubscriptionData(subscription)

        then: "exception not should be thrown"
        noExceptionThrown()

        where:
        subscriptionType << [SubscriptionType.CELLTRACE, SubscriptionType.CONTINUOUSCELLTRACE]
    }

    @Unroll
    def "When validating a subscription of type=#subscriptionType with Invalid IPv4 Address:#testData.streamInfo and isImported=true, it should throw Exception"(SubscriptionType subscriptionType, StreamInfo streamInfo) {
        given:
        subscription = subscriptionType.getIdentifier().newInstance() as EventSubscription
        subscription.setName("SubscriptionTestName")
        subscription.setUserType(UserType.SYSTEM_DEF)
        subscription.setType(subscriptionType)
        subscription.setOutputMode(OutputModeType.STREAMING)
        subscription.setStreamInfoList([streamInfo])
        subscription.setIsImported(true)

        when: "subscription is validated"
        subscriptionReadOperationService.validateAndAdjustSubscriptionData(subscription)

        then: "exception should be thrown"
        def exception = thrown(ValidationException)
        exception.message == ApplicationMessages.INVALID_IP_FORMAT

        where:
        [subscriptionType, streamInfo] << [
                [SubscriptionType.CELLTRACE, SubscriptionType.CONTINUOUSCELLTRACE],
                [
                        new StreamInfo("128.2.23.2333", 22),
                        new StreamInfo("128.2.4555.23", 22),
                        new StreamInfo("191.1221.21.1", 22),
                        new StreamInfo("128454.7.2.23", 22),
                        new StreamInfo("191.1.1.1.1", 22),
                        new StreamInfo("127.0.0", 22)]
        ].combinations()
    }

    @Unroll
    def "When validating a subscription of type=#subscriptionType with both Valid IPv4 and IPV6 Addresses and isImported=true should NOT throw Exception"() {
        given:
        subscription = subscriptionType.getIdentifier().newInstance() as EventSubscription
        subscription.setName("SubscriptionTestName")
        subscription.setUserType(UserType.SYSTEM_DEF)
        subscription.setType(subscriptionType)
        subscription.setOutputMode(OutputModeType.STREAMING)
        subscription.setStreamInfoList([new StreamInfo("2607:f0d0:1002:51::4", 22), new StreamInfo("192.168.0.50", 22)])
        subscription.setIsImported(true)

        when: "subscription is validated"
        subscriptionReadOperationService.validateAndAdjustSubscriptionData(subscription)

        then: "exception not should be thrown"
        noExceptionThrown()

        where:
        subscriptionType << [SubscriptionType.CELLTRACE, SubscriptionType.CONTINUOUSCELLTRACE]
    }

    @Unroll
    def "When validating a subscription of type=#subscriptionType with OutputMode=testData.outputModeType but no IP Address and isImported=true, it should throw Exception"() {
        given:
        subscription = subscriptionType.getIdentifier().newInstance() as EventSubscription
        subscription.setName("SubscriptionTestName")
        subscription.setUserType(UserType.SYSTEM_DEF)
        subscription.setType(subscriptionType as SubscriptionType)
        subscription.setOutputMode(outputModeType as OutputModeType)
        subscription.setIsImported(true)

        when: "subscription is validated"
        subscriptionReadOperationService.validateAndAdjustSubscriptionData(subscription)

        then: "exception should be thrown"
        def exception = thrown(ValidationException)
        exception.message == ApplicationMessages.NO_IP_OR_PORT_AVAILABLE

        where:
        [subscriptionType, outputModeType] << [[SubscriptionType.CELLTRACE, SubscriptionType.CONTINUOUSCELLTRACE], [OutputModeType.STREAMING, OutputModeType.FILE_AND_STREAMING],].combinations()
    }

    @Unroll
    def "When validating a subscription of type=#subscriptionType with invalid OutputMode=testData.outputModeType and no IP Address and isImported=true, it should throw Exception"() {
        given:
        subscription = subscriptionType.getIdentifier().newInstance() as EventSubscription
        subscription.setName("SubscriptionTestName")
        subscription.setUserType(UserType.SYSTEM_DEF)
        subscription.setType(subscriptionType as SubscriptionType)
        subscription.setOutputMode(outputModeType as OutputModeType)
        subscription.setIsImported(true)

        when: "subscription is validated"
        subscriptionReadOperationService.validateAndAdjustSubscriptionData(subscription)

        then: "exception should be thrown"
        def exception = thrown(ValidationException)
        def errorMessage = "Invalid OutputMode " + outputModeType + " for subscription type " + subscriptionType
        exception.message == errorMessage

        where:
        [subscriptionType, outputModeType] << [[SubscriptionType.EBM], [OutputModeType.STREAMING, OutputModeType.FILE_AND_STREAMING],].combinations()
    }

    @Unroll
    def "When validating a subscription of type=UETRACE with OutputMode=testData.outputModeType but no IP Address and isImported=true, it should throw Exception"() {
        given:
        subscription = new UETraceSubscription()
        subscription.setType(SubscriptionType.UETRACE)
        subscription.setName("SubscriptionTestName")
        subscription.setUserType(UserType.SYSTEM_DEF)
        subscription.setOutputMode(outputMode)
        subscription.setIsImported(true)

        when: "subscription is validated"
        subscriptionReadOperationService.validateAndAdjustSubscriptionData(subscription)

        then: "exception should be thrown"
        def exception = thrown(ValidationException)
        exception.message == ApplicationMessages.NO_IP_OR_PORT_AVAILABLE

        where:
        outputMode << [OutputModeType.STREAMING, OutputModeType.FILE_AND_STREAMING]
    }

    @Unroll
    def "When validating a subscription with #testData.testMessage UeInfo[#testData.ueType:#testData.value] and isImported=true, it should throw Exception Message:#testData.exceptionMessage"() {
        given:
        subscription = new UETraceSubscription()
        subscription.setType(SubscriptionType.UETRACE)
        subscription.setName("SubscriptionTestName")
        subscription.setUserType(UserType.SYSTEM_DEF)
        subscription.setOutputMode(OutputModeType.FILE)
        subscription.setUeInfo(new UeInfo(testData.ueType, testData.value))
        subscription.setIsImported(true)

        when: "subscription is validated"
        subscriptionReadOperationService.validateAndAdjustSubscriptionData(subscription)

        then: "exception should be thrown"
        def exception = thrown(ValidationException)
        exception.message == testData.exceptionMessage

        where:
        testData <<
                [
                        [ueType: UeType.IMEI, value: "1234", exceptionMessage: ApplicationMessages.INVALID_IMEI_FORMAT, testMessage: "4 digit IMEI"],
                        [ueType: UeType.IMSI, value: "invalidImsi", exceptionMessage: ApplicationMessages.INVALID_IMSI_FORMAT, testMessage: "invalid IMSI"],
                        [ueType: UeType.IMEI, value: "12345678901234", exceptionMessage: ApplicationMessages.INVALID_IMEI_FORMAT, testMessage: "invalid IMEI"],
                        [ueType: UeType.IMSI, value: "1234", exceptionMessage: ApplicationMessages.INVALID_IMSI_FORMAT, testMessage: "4 digit IMSI"],
                        [ueType: UeType.IMEI_SOFTWARE_VERSION, value: "invalidImsi", exceptionMessage: ApplicationMessages.INVALID_IMEI_SOFTWARE_VERSION_FORMAT, testMessage: "invalid IMEI SOFTWARE VERSION"],
                ]
    }

    @Unroll
    def "When validating an imported subscription of type=#subscriptionType with Valid ERBS Nodes then All Nodes are Retained and node info replaced with valid values"() {
        given:
        mockedPmFunctionEnabledWrapper.isPmFunctionEnabled(_) >> true
        createNodesinDps()
        nodeListIdentity = ResourceSubscription.getHashCodeFromNodeNames(neList)
        List<Node> nodeList = new ArrayList<Node>()
        nodeList.add(fillNodeInfo(NODE_NAME_ERBS_1, NetworkElementType.ERBS.getNeTypeString(), WRONG_OSS_MODEL_IDENTITY_TO_BE_REPLACED))
        nodeList.add(fillNodeInfo(NODE_NAME_ERBS_2, NetworkElementType.ERBS.getNeTypeString(), WRONG_OSS_MODEL_IDENTITY_TO_BE_REPLACED))

        subscription = subscriptionType.getIdentifier().newInstance() as Subscription
        subscription.setType(subscriptionType)
        subscription.setName("SubscriptionTestName")
        subscription.setUserType(UserType.USER_DEF)
        subscription.setAdministrationState(INACTIVE)
        subscription.setNodes(nodeList)
        subscription.setIsImported(true)

        when: "subscription is validated"
        subscriptionReadOperationService.validateAndAdjustSubscriptionData(subscription)

        then: "exception should not be thrown and node retained, attributes replaced with the correct ones"
        noExceptionThrown()
        subscription.getNodes().size() == 2
        Node ne = subscription.getNodes()[0]
        ne.getNeType() == NetworkElementType.ERBS.getNeTypeString()
        ne.getOssModelIdentity() == "18.Q2-J.1.280"
        ne.getTechnologyDomain() == ["EPS"]

        where:
        subscriptionType << [SubscriptionType.STATISTICAL, SubscriptionType.CELLTRACE]
    }

    @Unroll
    def "When validating an imported subscription of type=#subscriptionType with Valid and Invalid ERBS Nodes then it should throw Exception"() {
        given:
        createNodesinDps()
        nodeListIdentity = ResourceSubscription.getHashCodeFromNodeNames(neList)
        List<Node> nodeList = new ArrayList<Node>()
        nodeList.add(fillNodeInfo(NODE_NAME_ERBS_1, WRONG_NE_TYPE_TO_BE_REPLACED, WRONG_OSS_MODEL_IDENTITY_TO_BE_REPLACED))
        nodeList.add(fillNodeInfo(NOT_EXISTENT_NODE_NAME, NetworkElementType.ERBS.getNeTypeString(), WRONG_OSS_MODEL_IDENTITY_TO_BE_REPLACED))
        nodeList.add(fillNodeInfo(EMPTY_NODE_NAME, NetworkElementType.ERBS.getNeTypeString(), WRONG_OSS_MODEL_IDENTITY_TO_BE_REPLACED))
        nodeList.add(fillNodeInfo(null, NetworkElementType.ERBS.getNeTypeString(), WRONG_OSS_MODEL_IDENTITY_TO_BE_REPLACED))

        subscription = subscriptionType.getIdentifier().newInstance() as Subscription
        subscription.setType(subscriptionType)
        subscription.setName("SubscriptionTestName")
        subscription.setUserType(UserType.USER_DEF)
        subscription.setAdministrationState(INACTIVE)
        subscription.setNodes(nodeList)
        subscription.setIsImported(true)

        when: "subscription is validated"
        subscriptionReadOperationService.validateAndAdjustSubscriptionData(subscription)

        then: "exception should be thrown and node retained, attributes replaced with the correct ones"
        def exception = thrown(NodeNotFoundDataAccessException)
        exception.message.contains("Invalid node fdns: ")
        exception.message.contains(NOT_EXISTENT_NODE_NAME)
        exception.message.contains(EMPTY_NODE_NAME)
        exception.message.contains("NetworkElement=null")
        //nodes information untouched when an error occurs
        subscription.getNodes().size() == 4
        Node ne = subscription.getNodes()[0]
        ne.getNeType() == WRONG_NE_TYPE_TO_BE_REPLACED
        ne.getOssModelIdentity() == WRONG_OSS_MODEL_IDENTITY_TO_BE_REPLACED

        where:
        subscriptionType << [SubscriptionType.STATISTICAL, SubscriptionType.CONTINUOUSCELLTRACE, SubscriptionType.CELLTRACE]
    }

    @Unroll
    def "When validating an imported subscription of type=#subscriptionType with Valid RNC Nodes then All Nodes are Retained and node info replaced with valid values"() {
        given:
        mockedPmFunctionEnabledWrapper.isPmFunctionEnabled(_) >> true
        createNodesinDps()
        nodeListIdentity = ResourceSubscription.getHashCodeFromNodeNames(neList)
        List<Node> nodeList = new ArrayList<Node>()
        nodeList.add(fillNodeInfo(NODE_NAME_RNC, NetworkElementType.RNC.getNeTypeString(), WRONG_OSS_MODEL_IDENTITY_TO_BE_REPLACED))

        subscription = subscriptionType.getIdentifier().newInstance() as Subscription
        subscription.setType(subscriptionType)
        subscription.setName("SubscriptionTestName")
        subscription.setUserType(UserType.USER_DEF)
        subscription.setAdministrationState(INACTIVE)
        subscription.setNodes(nodeList)
        subscription.setIsImported(true)

        when: "subscription is validated"
        subscriptionReadOperationService.validateAndAdjustSubscriptionData(subscription)

        then: "exception should not be thrown and node retained, attributes replaced with the correct ones"
        noExceptionThrown()
        subscription.getNodes().size() == 1
        Node ne = subscription.getNodes()[0]
        ne.getNeType() == NetworkElementType.RNC.getNeTypeString()
        ne.getOssModelIdentity() == "16B-V.7.1659"
        ne.getTechnologyDomain() == ["EPS"]

        where:
        subscriptionType << [SubscriptionType.RES, SubscriptionType.STATISTICAL, SubscriptionType.MOINSTANCE, SubscriptionType.GPEH, SubscriptionType.UETR, SubscriptionType.CELLTRAFFIC]
    }

    @Unroll
    def "When validating an imported subscription of type=#subscriptionType with Valid and Invalid RNC Nodes then it should throw Exception"() {
        given:
        createNodesinDps()
        nodeListIdentity = ResourceSubscription.getHashCodeFromNodeNames(neList)
        List<Node> nodeList = new ArrayList<Node>()
        nodeList.add(fillNodeInfo(NODE_NAME_RNC, WRONG_NE_TYPE_TO_BE_REPLACED, WRONG_OSS_MODEL_IDENTITY_TO_BE_REPLACED))
        nodeList.add(fillNodeInfo(NOT_EXISTENT_NODE_NAME, NetworkElementType.ERBS.getNeTypeString(), WRONG_OSS_MODEL_IDENTITY_TO_BE_REPLACED))
        nodeList.add(fillNodeInfo(EMPTY_NODE_NAME, NetworkElementType.ERBS.getNeTypeString(), WRONG_OSS_MODEL_IDENTITY_TO_BE_REPLACED))
        nodeList.add(fillNodeInfo(null, NetworkElementType.ERBS.getNeTypeString(), WRONG_OSS_MODEL_IDENTITY_TO_BE_REPLACED))

        subscription = subscriptionType.getIdentifier().newInstance() as Subscription
        subscription.setType(subscriptionType)
        subscription.setName("SubscriptionTestName")
        subscription.setUserType(UserType.USER_DEF)
        subscription.setAdministrationState(INACTIVE)
        subscription.setNodes(nodeList)
        subscription.setIsImported(true)

        when: "subscription is validated"
        subscriptionReadOperationService.validateAndAdjustSubscriptionData(subscription)

        then: "exception should be thrown and node retained, attributes replaced with the correct ones"
        def exception = thrown(NodeNotFoundDataAccessException)
        exception.message.contains("Invalid node fdns: ")
        exception.message.contains(NOT_EXISTENT_NODE_NAME)
        exception.message.contains(EMPTY_NODE_NAME)
        exception.message.contains("NetworkElement=null")
        //nodes information untouched when an error occurs
        subscription.getNodes().size() == 4
        Node ne = subscription.getNodes()[0]
        ne.getNeType() == WRONG_NE_TYPE_TO_BE_REPLACED
        ne.getOssModelIdentity() == WRONG_OSS_MODEL_IDENTITY_TO_BE_REPLACED

        where:
        subscriptionType << [SubscriptionType.RES, SubscriptionType.STATISTICAL, SubscriptionType.MOINSTANCE, SubscriptionType.GPEH, SubscriptionType.UETR, SubscriptionType.CELLTRAFFIC]
    }

    @Unroll
    def "When validating an imported subscription with RNC nodes with type=#subscriptionType then it should throw Exeption"() {
        given:
        createNodesinDps()
        nodeListIdentity = ResourceSubscription.getHashCodeFromNodeNames(neList)
        List<Node> nodeList = new ArrayList<Node>()
        nodeList.add(fillNodeInfo(NODE_NAME_RNC, NetworkElementType.RNC.getNeTypeString(), "16A-CP02"))
        nodeList.add(fillNodeInfo(NODE_NAME_SGSNMME, NetworkElementType.SGSNMME.getNeTypeString(), "16A-CP02"))


        subscription = subscriptionType.getIdentifier().newInstance() as Subscription
        subscription.setType(subscriptionType)
        subscription.setName("SubscriptionTestName")
        subscription.setUserType(UserType.USER_DEF)
        subscription.setAdministrationState(INACTIVE)
        subscription.setNodes(nodeList)
        subscription.setIsImported(true)

        when: "subscription is validated"
        subscriptionReadOperationService.validateAndAdjustSubscriptionData(subscription)

        then: "exception should be thrown"
        def exception = thrown(ValidationException)
        exception.message.contains("RNC cannot be combined with other NE types. ")


        where:
        subscriptionType << [SubscriptionType.STATISTICAL]

    }

    @Unroll
    def "When validating an imported subscription with not allowed nodes of type=#subscriptionType then it should throw Exeption"() {
        given:
        mockedPmFunctionEnabledWrapper.isPmFunctionEnabled(_) >> true
        createEPGFakeNodesInDps()
        createFronthaul6020FakeNodesInDps()
        createFronthaul6080FakeNodesInDps()
        createSBG_ISNodesInDps()
        createSIU2NodesInDps()
        createTCU2FakeNodesInDps()
        createVEPGFakeNodesInDps()
        createvwMGFakeNodesInDps()
        createWMGFakeNodesInDps()

        nodeListIdentity = ResourceSubscription.getHashCodeFromNodeNames(neList)
        List<Node> nodeList = new ArrayList<Node>()
        nodeList.add(fillNodeInfo(testData.nodeName, testData.NodeType, testData.ossModelIdentity))
        subscription = testData.subscriptionType.getIdentifier().newInstance() as StatisticalSubscription
        subscription.setType(testData.subscriptionType)
        subscription.setCounters([new CounterInfo("pmBwErrBlocks", "VpcTp")])
        subscription.setName("SubscriptionTestName")
        subscription.setUserType(UserType.USER_DEF)
        subscription.setAdministrationState(INACTIVE)
        subscription.setNodes(nodeList)
        subscription.setIsImported(true)

        when: "subscription is validated"
        subscriptionReadOperationService.validateAndAdjustSubscriptionData(subscription)

        then: "exception should be thrown"
        def exception = thrown(ValidationException)
        exception.message.contains("Not allowed nodeTypes are present in the subscription: ")

        where:
        testData <<
                [
                        [subscriptionType: SubscriptionType.STATISTICAL, nodeName: NODE_NAME_EPG, nodeType: NODE_TYPE_EPG, ossModelIdentity: "16A-CP02"],
                        [subscriptionType: SubscriptionType.STATISTICAL, nodeName: NODE_NAME_FRONTHAUL6020, nodeType: NODE_TYPE_FRONTHAUL6020, ossModelIdentity: "16A-CP02"],
                        [subscriptionType: SubscriptionType.STATISTICAL, nodeName: NODE_NAME_FRONTHAUL6080, nodeType: NODE_TYPE_FRONTHAUL6080, ossModelIdentity: "16A-CP02"],
                        [subscriptionType: SubscriptionType.STATISTICAL, nodeName: NODE_NAME_SBG_IS, nodeType: NODE_TYPE_SBG_IS, ossModelIdentity: "16A-CP02"],
                        [subscriptionType: SubscriptionType.STATISTICAL, nodeName: NODE_NAME_SIU2, nodeType: NODE_TYPE_SIU2, ossModelIdentity: "16A-CP02"],
                        [subscriptionType: SubscriptionType.STATISTICAL, nodeName: NODE_NAME_TCU02, nodeType: NODE_TYPE_TCU02, ossModelIdentity: "16A-CP02"],
                        [subscriptionType: SubscriptionType.STATISTICAL, nodeName: NODE_NAME_VEPG, nodeType: NODE_TYPE_VEPG, ossModelIdentity: "16A-CP02"],
                        [subscriptionType: SubscriptionType.STATISTICAL, nodeName: NODE_NAME_vwMG, nodeType: NODE_TYPE_vwMG, ossModelIdentity: "16A-CP02"],
                        [subscriptionType: SubscriptionType.STATISTICAL, nodeName: NODE_NAME_WMG, nodeType: NODE_TYPE_WMG, ossModelIdentity: "16A-CP02"],
                ]
    }

    @Unroll
    def "When validating an imported subscription with RNC nodes of type=#subscriptionType with valid NodeCounters then All NodeCounters are Retained"() {
        given:
        mockedPmFunctionEnabledWrapper.isPmFunctionEnabled(_) >> true
        createNodesinDps()
        nodeListIdentity = ResourceSubscription.getHashCodeFromNodeNames(neList)
        List<Node> nodeList = new ArrayList<Node>()
        nodeList.add(fillNodeInfo(NODE_NAME_RNC, WRONG_NE_TYPE_TO_BE_REPLACED, WRONG_OSS_MODEL_IDENTITY_TO_BE_REPLACED))

        subscription = subscriptionType.getIdentifier().newInstance() as StatisticalSubscription
        subscription.setType(subscriptionType)
        subscription.setCounters([new CounterInfo("pmBwErrBlocks", "VpcTp")])
        subscription.setName("SubscriptionTestName")
        subscription.setUserType(UserType.USER_DEF)
        subscription.setAdministrationState(INACTIVE)
        subscription.setNodes(nodeList)
        subscription.setIsImported(true)

        when: "subscription is validated"
        subscriptionReadOperationService.validateAndAdjustSubscriptionData(subscription)

        then: "exception should not be thrown"
        noExceptionThrown()
        ((StatisticalSubscription) subscription).getCounters().size() == 1

        where:
        subscriptionType << [SubscriptionType.STATISTICAL, SubscriptionType.MOINSTANCE]
    }

    @Unroll
    def "When validating an imported subscription with RNC nodes of type=#subscriptionType with invalid NodeCounters then it should throw Exception"() {
        given:
        mockedPmFunctionEnabledWrapper.isPmFunctionEnabled(_) >> true
        createNodesinDps()
        nodeListIdentity = ResourceSubscription.getHashCodeFromNodeNames(neList)
        List<Node> nodeList = new ArrayList<Node>()
        nodeList.add(fillNodeInfo(NODE_NAME_RNC, WRONG_NE_TYPE_TO_BE_REPLACED, WRONG_OSS_MODEL_IDENTITY_TO_BE_REPLACED))

        subscription = subscriptionType.getIdentifier().newInstance() as StatisticalSubscription
        subscription.setType(subscriptionType)
        subscription.setCounters([new CounterInfo("Invalid Counter", "Invalid Counter Group"), new CounterInfo("ipv6InReceives", "SGSN-MME_IPv6_RouterInstance")])
        subscription.setName("SubscriptionTestName")
        subscription.setUserType(UserType.USER_DEF)
        subscription.setAdministrationState(INACTIVE)
        subscription.setNodes(nodeList)
        subscription.setIsImported(true)

        when: "subscription is validated"
        subscriptionReadOperationService.validateAndAdjustSubscriptionData(subscription)

        then: "exception should be thrown"
        def exception = thrown(ValidationException)
        exception.message.contains("Counters provided from imported subscription are invalid: ")

        where:
        subscriptionType << [SubscriptionType.STATISTICAL, SubscriptionType.MOINSTANCE]
    }

    def "When validating a Celltrace imported subscription with ERBS nodes with invalid NodeCounters then it should throw Exception"() {
        given:
        mockedPmFunctionEnabledWrapper.isPmFunctionEnabled(_) >> true
        createNodesinDps()
        nodeListIdentity = ResourceSubscription.getHashCodeFromNodeNames(neList)
        List<Node> nodeList = new ArrayList<Node>()
        nodeList.add(fillNodeInfo(NODE_NAME_ERBS_1, NetworkElementType.ERBS.getNeTypeString(), WRONG_OSS_MODEL_IDENTITY_TO_BE_REPLACED))
        nodeList.add(fillNodeInfo(NODE_NAME_ERBS_2, NetworkElementType.ERBS.getNeTypeString(), WRONG_OSS_MODEL_IDENTITY_TO_BE_REPLACED))

        subscription = new CellTraceSubscription()
        subscription.setType(SubscriptionType.CELLTRACE)
        subscription.setEbsCounters([INVALID_COUNTER, VALID_COUNTER])
        subscription.setName("SubscriptionTestName")
        subscription.setUserType(UserType.USER_DEF)
        subscription.setAdministrationState(INACTIVE)
        subscription.setNodes(nodeList)
        subscription.setIsImported(true)

        when: "subscription is validated"
        subscriptionReadOperationService.validateAndAdjustSubscriptionData(subscription)

        then: "exception should be thrown"
        def exception = thrown(ValidationException)
        exception.message.contains("Counters provided from imported subscription are invalid: ")
    }

    @Unroll
    @PropertiesForTest(properties = [@SuppliedProperty(name = "pmicEbsStreamClusterDeployed", value = "true")])
    def "When validating an imported Celltrace subscription with invalid events and ebsEvents"() {
        given:
        mockedPmFunctionEnabledWrapper.isPmFunctionEnabled(_) >> true
        createNodesinDps()
        nodeListIdentity = ResourceSubscription.getHashCodeFromNodeNames(neList)
        List<Node> nodeList = new ArrayList<Node>()
        nodeList.add(fillNodeInfo(NODE_NAME_ERBS_1, NetworkElementType.ERBS.getNeTypeString(), "18.Q2-J.1.280"))

        subscription = new CellTraceSubscription()
        subscription.setType(SubscriptionType.CELLTRACE)
        subscription.setEbsCounters([VALID_COUNTER])
        subscription.setName("SubscriptionTestName")
        subscription.setUserType(UserType.USER_DEF)
        subscription.setAdministrationState(INACTIVE)
        subscription.setNodes(nodeList)
        subscription.setIsImported(true)
        subscription.setEbsEvents(ebsEvents)
        subscription.setEvents(events)

        when: "subscription is validated"
        subscriptionReadOperationService.validateAndAdjustSubscriptionData(subscription)
        then: "exception should be thrown"
        def exception = thrown(PfmDataException)
        exception.message.contains("Events provided from imported subscription are invalid")
        and: "subscription attributes were validated"
        ebsEventsResult == subscription.getEbsEvents()
        eventsResult == subscription.getEvents()
        where:
        events                         | ebsEvents                      || eventsResult                   | ebsEventsResult
        [VALID_EVENT, INVALID_EVENT]   | [VALID_EVENT, INVALID_EVENT]   || [VALID_EVENT, INVALID_EVENT]   | [VALID_EVENT, INVALID_EVENT]
        [INVALID_EVENT]                | [VALID_EVENT, INVALID_EVENT]   || [INVALID_EVENT]                | [VALID_EVENT, INVALID_EVENT]
        [INVALID_EVENT, INVALID_EVENT] | [INVALID_EVENT, INVALID_EVENT] || [INVALID_EVENT, INVALID_EVENT] | [INVALID_EVENT, INVALID_EVENT]
        [VALID_EVENT, INVALID_EVENT]   | []                             || [VALID_EVENT, INVALID_EVENT]   | []
        [VALID_EVENT]                  | [VALID_EVENT, INVALID_EVENT]   || [VALID_EVENT]                  | [VALID_EVENT, INVALID_EVENT]
        [VALID_EVENT, INVALID_EVENT]   | [VALID_EVENT]                  || [VALID_EVENT, INVALID_EVENT]   | [VALID_EVENT]
        []                             | [VALID_EVENT, INVALID_EVENT]   || []                             | [VALID_EVENT, INVALID_EVENT]
        [VALID_EVENT, INVALID_EVENT]   | [VALID_EVENT, INVALID_EVENT]   || [VALID_EVENT, INVALID_EVENT]   | [VALID_EVENT, INVALID_EVENT]
        [INVALID_EVENT]                | [VALID_EVENT]                  || [INVALID_EVENT]                | [VALID_EVENT]
        [VALID_EVENT]                  | [INVALID_EVENT]                || [VALID_EVENT]                  | [INVALID_EVENT]
    }

    @Unroll
    def "When validating an imported Celltrace subscription with invalid events"() {
        given: "a subscription"
        mockedPmFunctionEnabledWrapper.isPmFunctionEnabled(_) >> true
        createNodesinDps()
        nodeListIdentity = ResourceSubscription.getHashCodeFromNodeNames(neList)
        List<Node> nodeList = new ArrayList<Node>()
        nodeList.add(fillNodeInfo(NODE_NAME_ERBS_1, NetworkElementType.ERBS.getNeTypeString(), "18.Q2-J.1.280"))

        subscription = new CellTraceSubscription()
        subscription.setType(SubscriptionType.CELLTRACE)
        subscription.setEbsCounters([VALID_COUNTER])
        subscription.setName("SubscriptionTestName")
        subscription.setUserType(UserType.USER_DEF)
        subscription.setAdministrationState(INACTIVE)
        subscription.setNodes(nodeList)
        subscription.setIsImported(true)
        subscription.setEvents(events)
        when: "subscription is validated"
        subscriptionReadOperationService.validateAndAdjustSubscriptionData(subscription)
        then: "exception should be thrown"
        def exception = thrown(PfmDataException)
        exception.message.contains("Events provided from imported subscription are invalid")
        and: "subscription attributes were validated"
        eventsResult == subscription.getEvents()
        where:
        events                         || eventsResult
        [VALID_EVENT, INVALID_EVENT]   || [VALID_EVENT, INVALID_EVENT]
        [INVALID_EVENT]                || [INVALID_EVENT]
        [INVALID_EVENT, INVALID_EVENT] || [INVALID_EVENT, INVALID_EVENT]
        [VALID_EVENT, INVALID_EVENT]   || [VALID_EVENT, INVALID_EVENT]
        [VALID_EVENT, INVALID_EVENT]   || [VALID_EVENT, INVALID_EVENT]
        [VALID_EVENT, INVALID_EVENT]   || [VALID_EVENT, INVALID_EVENT]
        [INVALID_EVENT]                || [INVALID_EVENT]
    }

    @Unroll
    @PropertiesForTest(properties = [@SuppliedProperty(name = "pmicEbsStreamClusterDeployed", value = "false")])
    def "When validating an imported Celltrace subscription with valid events"() {
        given:
        mockedPmFunctionEnabledWrapper.isPmFunctionEnabled(_) >> true
        createNodesinDps()
        nodeListIdentity = ResourceSubscription.getHashCodeFromNodeNames(neList)
        List<Node> nodeList = new ArrayList<Node>()
        nodeList.add(fillNodeInfo(NODE_NAME_ERBS_1, NetworkElementType.ERBS.getNeTypeString(), "18.Q2-J.1.280"))

        subscription = new CellTraceSubscription()
        subscription.setType(SubscriptionType.CELLTRACE)
        subscription.setName("SubscriptionTestName")
        subscription.setUserType(UserType.USER_DEF)
        subscription.setAdministrationState(INACTIVE)
        subscription.setNodes(nodeList)
        subscription.setIsImported(true)

        subscription.setEbsCounters([CELTRACE_COUNTER_EVENT_BASED])
        subscription.setEbsEvents(ebsEvents)
        subscription.setEvents(events)
        when: "subscription is validated"
        subscriptionReadOperationService.validateAndAdjustSubscriptionData(subscription)

        then: "no exception should be thrown"
        noExceptionThrown()
        and: "subscription attributes were validated"
        ebsEventsResult.sort() == subscription.getEbsEvents().sort()
        eventsResult.sort() == subscription.getEvents().sort()
        cellTraceCategory == subscription.getCellTraceCategory()
        where:
        events        | ebsEvents || eventsResult                      | ebsEventsResult | cellTraceCategory
        [VALID_EVENT] | []        || COMBINED_EVENT_LIST               | []              | CELLTRACE_AND_EBSL_FILE
        []            | []        || CELLTRACE_EVENTS_FOR_COUNTER_LIST | []              | CELLTRACE_AND_EBSL_FILE
    }

    @Unroll
    @PropertiesForTest(properties = [@SuppliedProperty(name = "pmicEbsStreamClusterDeployed", value = "true")])
    def "When Importing Variant of CellTrace and Stream Cluster is Deployed then subscription should have the correct attributes and category"() {
        given:
        createNodesinDps()
        List<Node> nodeList = new ArrayList<Node>() {
            {
                add(fillNodeInfo(LTE02_ERBS00021, NetworkElementType.ERBS.getNeTypeString(), '18.Q2-J.1.280'))
            }
        }
        subscription = new CellTraceSubscription()
        subscription.setName("SubscriptionTestName")
        subscription.setType(SubscriptionType.CELLTRACE)
        subscription.setUserType(UserType.USER_DEF)
        subscription.setAdministrationState(INACTIVE)
        subscription.setNodes(nodeList)
        subscription.setIsImported(true)

        subscription.setEbsCounters(ebsCounters as List<CounterInfo>)
        subscription.setEbsEvents(ebsEvents as List<EventInfo>)
        subscription.setEvents(events as List<EventInfo>)

        when: "subscription is validated"
        subscriptionReadOperationService.validateAndAdjustSubscriptionData(subscription)

        then: "exception should not be thrown"
        noExceptionThrown()
        and:
        with(subscription) {
            getName() == 'SubscriptionTestName'
            getNodes().size() == 1
            getEbsEvents().sort() == expectedEbsEvent.sort() as List<EventInfo>
            getEbsCounters() == expectedCounters as List<CounterInfo>
            getEvents() == expectedEvent as List<EventInfo>
            getCellTraceCategory() == celltraceCategory
            Node node = getNodes()[0]
            with(node) {
                getNeType() == ERBS.name()
                getOssModelIdentity() == '18.Q2-J.1.280'
                getTechnologyDomain() == ['EPS']
            }
        }

        where:
        ebsCounters                    | ebsEvents                         | events        || expectedCounters               | expectedEbsEvent                  | expectedEvent | celltraceCategory
        [CELTRACE_COUNTER_EVENT_BASED] | CELLTRACE_EVENTS_FOR_COUNTER_LIST | [VALID_EVENT] || [CELTRACE_COUNTER_EVENT_BASED] | CELLTRACE_EVENTS_FOR_COUNTER_LIST | [VALID_EVENT] | CELLTRACE_AND_EBSL_STREAM
        [CELTRACE_COUNTER_EVENT_BASED] | CELLTRACE_EVENTS_FOR_COUNTER_LIST | []            || [CELTRACE_COUNTER_EVENT_BASED] | CELLTRACE_EVENTS_FOR_COUNTER_LIST | []            | EBSL_STREAM
        [CELTRACE_COUNTER_EVENT_BASED] | []                                | [VALID_EVENT] || [CELTRACE_COUNTER_EVENT_BASED] | CELLTRACE_EVENTS_FOR_COUNTER_LIST | [VALID_EVENT] | CELLTRACE_AND_EBSL_STREAM
        [CELTRACE_COUNTER_EVENT_BASED] | []                                | []            || [CELTRACE_COUNTER_EVENT_BASED] | CELLTRACE_EVENTS_FOR_COUNTER_LIST | []            | EBSL_STREAM
        []                             | []                                | [VALID_EVENT] || []                             | []                                | [VALID_EVENT] | CELLTRACE
    }

    @Unroll
    @PropertiesForTest(properties = [@SuppliedProperty(name = "pmicEbsStreamClusterDeployed", value = "true")])
    def "When validating an imported Celltrace subscription with valid events moved to EBSEvents"() {
        given:
        mockedPmFunctionEnabledWrapper.isPmFunctionEnabled(_) >> true
        createNodesinDps()
        nodeListIdentity = ResourceSubscription.getHashCodeFromNodeNames(neList)
        List<Node> nodeList = new ArrayList<Node>()
        nodeList.add(fillNodeInfo(NODE_NAME_ERBS_1, NetworkElementType.ERBS.getNeTypeString(), "18.Q2-J.1.280"))

        subscription = new CellTraceSubscription()
        subscription.setType(SubscriptionType.CELLTRACE)
        subscription.setEbsCounters([CELTRACE_COUNTER_EVENT_BASED])
        subscription.setName("SubscriptionTestName")
        subscription.setUserType(UserType.USER_DEF)
        subscription.setAdministrationState(INACTIVE)
        subscription.setNodes(nodeList)
        subscription.setIsImported(true)
        subscription.setEbsEvents(ebsEvents)
        subscription.setEvents(events)

        when: "subscription is validated"
        subscriptionReadOperationService.validateAndAdjustSubscriptionData(subscription)
        then: "no exception should be thrown"
        noExceptionThrown()
        and: "subscription attributes were validated"
        ebsEventsResult.sort() == subscription.getEbsEvents().sort()
        subscription.getEbsEvents().size() == 3
        subscription.getEvents().size() == 1
        where:
        events                              | ebsEvents     || eventsResult | ebsEventsResult
        [CELTRACE_BASE_EVENT_FOR_COUNTER_1] | [VALID_EVENT] || []           | CELLTRACE_EVENTS_FOR_COUNTER_LIST
    }

    @Unroll
    def "When validating an imported Celltrace subscription with counters, outputMode is STREAMING"() {
        given:
        mockedPmFunctionEnabledWrapper.isPmFunctionEnabled(_) >> true
        createNodesinDps()
        nodeListIdentity = ResourceSubscription.getHashCodeFromNodeNames(neList)
        List<Node> nodeList = new ArrayList<Node>()
        nodeList.add(fillNodeInfo(NODE_NAME_ERBS_1, NetworkElementType.ERBS.getNeTypeString(), "18.Q2-J.1.280"))

        subscription = new CellTraceSubscription()
        subscription.setType(SubscriptionType.CELLTRACE)
        subscription.setEbsCounters([VALID_COUNTER])
        subscription.setName("SubscriptionTestName")
        subscription.setUserType(UserType.USER_DEF)
        subscription.setAdministrationState(INACTIVE)
        subscription.setNodes(nodeList)
        subscription.setIsImported(true)
        subscription.setEbsEvents(ebsEvents)
        subscription.setEvents(events)
        subscription.setOutputMode(OutputModeType.STREAMING)

        when: "subscription is validated"
        subscriptionReadOperationService.validateAndAdjustSubscriptionData(subscription)
        then: "exception should be thrown"
        def exception = thrown(ValidationException)
        exception.message.contains(COUNTERS_NOT_ALLOWED_FOR_EBS_FILE_WITH_STREAMING_OUTPUTMODE.replace('{}', subscription.getName()))

        and: "subscription attributes were validated"
        ebsEventsResult == subscription.getEbsEvents()
        eventsResult == subscription.getEvents()
        where:
        events        | ebsEvents || eventsResult  | ebsEventsResult
        [VALID_EVENT] | []        || [VALID_EVENT] | []
        []            | []        || []            | []
    }

    def 'When validating an imported CellTrace EBSL-File subscription, then ensure extra not imported events and not added: TORF-269582'() {
        given:
        mockedPmFunctionEnabledWrapper.isPmFunctionEnabled(_) >> true
        createNodesinDps()
        List<Node> nodeList = new ArrayList<Node>() {
            {
                add(fillNodeInfo(LTE02_ERBS00021, NetworkElementType.ERBS.getNeTypeString(), '18.Q2-J.1.280'))
            }
        }
        ObjectMapper mapper = new ObjectMapper()
        CellTraceSubscription subscription = mapper.readValue(new File('src/test/resources/importCellTraceTestSubscription.json'),
                Subscription.class)
        subscription.setNodes(nodeList)
        subscription.setIsImported(true)

        when: 'imported subscription is validated'
        subscriptionReadOperationService.validateAndAdjustSubscriptionData(subscription)

        then: 'no exception should be thrown'
        noExceptionThrown()
        and: 'subscription attributes were validated'
        with(subscription) {
            getName() == 'CellTraceTestSubscription'
            getNodes().size() == 1
            getEbsCounters().size() == old(subscription.getEbsCounters().size())
            getEvents().size() == old(subscription.getEvents().size())
            Node node = getNodes()[0]
            with(node) {
                getNeType() == ERBS.name()
                getOssModelIdentity() == '18.Q2-J.1.280'
                getTechnologyDomain() == ['EPS']
            }
        }
    }

    @PropertiesForTest(properties = [@SuppliedProperty(name = "pmicEbsStreamClusterDeployed", value = "true")])
    def 'When validating an imported CellTrace EBSL-Stream subscription with NOT_APPLICABLE Rop, then no Exception Thrown :TORF-274639'() {
        given:
        createNodesinDps()
        List<Node> nodeList = new ArrayList<Node>() {
            {
                add(fillNodeInfo(LTE02_ERBS00021, NetworkElementType.ERBS.getNeTypeString(), '18.Q2-J.1.280'))
            }
        }
        ObjectMapper mapper = new ObjectMapper()
        CellTraceSubscription subscription = mapper.readValue(new File('src/test/resources/importCellTraceSubscriptionRopTest.json'),
                Subscription.class)
        subscription.setNodes(nodeList)
        subscription.setIsImported(true)

        when: 'imported subscription is validated'
        subscriptionReadOperationService.validateAndAdjustSubscriptionData(subscription)

        then: 'no exception should be thrown'
        noExceptionThrown()
        and: 'subscription attributes were validated'
        with(subscription) {
            getName() == 'CellTraceTestSubscription'
            getNodes().size() == 1
            getRop() == RopPeriod.NOT_APPLICABLE
            getEbsEvents() != old(subscription.getEbsEvents())
            getEvents().size() == old(subscription.getEvents().size())
            getEbsCounters().size() == old(subscription.getEbsCounters().size())
            getCellTraceCategory() == EBSL_STREAM

            Node node = getNodes()[0]
            with(node) {
                getNeType() == ERBS.name()
                getOssModelIdentity() == '18.Q2-J.1.280'
                getTechnologyDomain() == ['EPS']
            }
        }
    }

    def "When validating an ebm imported subscription without nodes of CELLTRACE with invalid NodeCounters then it should throw Exception"() {
        given:
        subscription = new CellTraceSubscription()
        subscription.setType(SubscriptionType.CELLTRACE)
        subscription.setEbsCounters([new CounterInfo("Invalid Counter", "Invalid Counter Group"), new CounterInfo("MME ATTACH ABORT", "TAI")])
        subscription.setName("SubscriptionTestName")
        subscription.setUserType(UserType.USER_DEF)
        subscription.setAdministrationState(INACTIVE)
        subscription.setIsImported(true)

        when: "subscription is validated"
        subscriptionReadOperationService.validateAndAdjustSubscriptionData(subscription)

        then: "exception should be thrown"
        def exception = thrown(ValidationException)
        exception.message.contains("Nodes list in imported subscription is empty")
    }

    def "When validating an imported subscription with SGSN-MME nodes of STATISTICAL and valid NodeCounters then All NodeCounters are Retained"() {
        given:
        mockedPmFunctionEnabledWrapper.isPmFunctionEnabled(_) >> true
        createNodesinDps()
        def node = new Node()
        node.setNeType("SGSN-MME")
        node.setFdn("NetworkElement=" + NODE_NAME_SGSNMME)
        node.setOssModelIdentity("16A-CP02")
        node.setName((String) NODE_NAME_SGSNMME)
        node.setId(281474977591390L)
        node.setTechnologyDomain(["EPS"])
        node.setPmFunction(true)

        subscription = new StatisticalSubscription()
        subscription.setType(SubscriptionType.STATISTICAL)
        subscription.setCounters([new CounterInfo("ipv6OutNoRoutes", "SGSN-MME_IPv6_RouterInstance"), new CounterInfo("ipv6InReceives", "SGSN-MME_IPv6_RouterInstance")])
        subscription.setName("SubscriptionTestName")
        subscription.setUserType(UserType.USER_DEF)
        subscription.setAdministrationState(INACTIVE)
        subscription.setNodes([node])
        subscription.setIsImported(true)

        when: "subscription is validated"
        subscriptionReadOperationService.validateAndAdjustSubscriptionData(subscription)

        then: "exception should not be thrown"
        noExceptionThrown()
        ((StatisticalSubscription) subscription).getCounters().size() == 2
    }

    def "When validating an imported subscription with RNC nodes in a subscriptionType=MOINSTANCE with No NodeCounters then it should throw InvalidSubscriptionException"() {
        given:
        mockedPmFunctionEnabledWrapper.isPmFunctionEnabled(_) >> true
        createNodesinDps()
        nodeListIdentity = ResourceSubscription.getHashCodeFromNodeNames(neList)
        List<Node> nodeList = new ArrayList<Node>()
        nodeList.add(fillNodeInfo(NODE_NAME_RNC, WRONG_NE_TYPE_TO_BE_REPLACED, WRONG_OSS_MODEL_IDENTITY_TO_BE_REPLACED))

        subscription = new MoinstanceSubscription()
        subscription.setType(SubscriptionType.MOINSTANCE)
        subscription.setMoInstances([new MoinstanceInfo(NODE_NAME_RNC, "Aal5TpVccTp")])
        subscription.setName("SubscriptionTestName")
        subscription.setUserType(UserType.USER_DEF)
        subscription.setAdministrationState(INACTIVE)
        subscription.setNodes(nodeList)
        subscription.setIsImported(true)

        when: "subscription is validated"
        subscriptionReadOperationService.validateAndAdjustSubscriptionData(subscription)

        //Mo Instances needs both nodes and counters
        then: "exception should be thrown"
        def exception = thrown(ValidationException)
        exception.message == "Mo Instances needs nodes and counters"
    }

    def "When validating an imported subscription with RNC nodes in a subscriptionType=MOINSTANCE with valid NodeCounters but no nodes then it should throw InvalidSubscriptionException"() {
        given:
        createNodesinDps()
        nodeListIdentity = ResourceSubscription.getHashCodeFromNodeNames(neList)
        List<Node> nodeList = new ArrayList<Node>()

        subscription = new MoinstanceSubscription()
        subscription.setType(SubscriptionType.MOINSTANCE)
        subscription.setMoInstances([new MoinstanceInfo(NODE_NAME_RNC, "Aal1TpVccTp=1-13-1")])
        subscription.setName("SubscriptionTestName")
        subscription.setUserType(UserType.USER_DEF)
        subscription.setAdministrationState(INACTIVE)
        subscription.setNodes(nodeList)
        subscription.setIsImported(true)

        when: "subscription is validated"
        subscriptionReadOperationService.validateAndAdjustSubscriptionData(subscription)

        //Mo Instances needs both nodes and counters
        then: "exception should be thrown"
        def exception = thrown(ValidationException)
        exception.message == "Mo Instances needs nodes and counters"
    }

    def "When validating an imported subscription with RNC nodes in a subscriptionType=MOINSTANCE with valid NodeCounters and no MOInstances then then no exceptions should by throw"() {
        given:
        mockedPmFunctionEnabledWrapper.isPmFunctionEnabled(_) >> true
        createNodesinDps()
        nodeListIdentity = ResourceSubscription.getHashCodeFromNodeNames(neList)
        List<Node> nodeList = new ArrayList<Node>()
        nodeList.add(fillNodeInfo(NODE_NAME_RNC, WRONG_NE_TYPE_TO_BE_REPLACED, WRONG_OSS_MODEL_IDENTITY_TO_BE_REPLACED))

        subscription = new MoinstanceSubscription()
        subscription.setType(SubscriptionType.MOINSTANCE)
        subscription.setCounters([new CounterInfo("pmBwErrBlocks", "Aal1TpVccTp")])
        subscription.setName("SubscriptionTestName")
        subscription.setUserType(UserType.USER_DEF)
        subscription.setAdministrationState(INACTIVE)
        subscription.setNodes(nodeList)
        subscription.setIsImported(true)

        when: "subscription is validated"
        subscriptionReadOperationService.validateAndAdjustSubscriptionData(subscription)

        then: "exception should not be thrown"
        noExceptionThrown()
    }

    def "When validating an imported subscription with RNC nodes in a subscriptionType=MOINSTANCE with valid NodeCounters then then no exceptions should by throw"() {
        given:
        mockedPmFunctionEnabledWrapper.isPmFunctionEnabled(_) >> true
        createNodesinDps()
        nodeListIdentity = ResourceSubscription.getHashCodeFromNodeNames(neList)
        List<Node> nodeList = new ArrayList<Node>()
        nodeList.add(fillNodeInfo(NODE_NAME_RNC, WRONG_NE_TYPE_TO_BE_REPLACED, WRONG_OSS_MODEL_IDENTITY_TO_BE_REPLACED))

        subscription = new MoinstanceSubscription()
        subscription.setType(SubscriptionType.MOINSTANCE)
        subscription.setCounters([new CounterInfo("pmBwErrBlocks", "Aal1TpVccTp")])
        subscription.setMoInstances([new MoinstanceInfo(NODE_NAME_RNC, "Aal1TpVccTp=1-13-1")])
        subscription.setName("SubscriptionTestName")
        subscription.setUserType(UserType.USER_DEF)
        subscription.setAdministrationState(INACTIVE)
        subscription.setNodes(nodeList)
        subscription.setIsImported(true)

        when: "subscription is validated"
        subscriptionReadOperationService.validateAndAdjustSubscriptionData(subscription)

        then: "exception should not be thrown"
        noExceptionThrown()
    }

    @Unroll
    def "When validating a subscription of type=#subscriptionType with One Valid Event then Only Valid Event is Retained"() {
        given:
        mockedPmFunctionEnabledWrapper.isPmFunctionEnabled(_) >> true
        createNodesinDps()
        List<Node> nodeList = new ArrayList<Node>()
        nodeList.add(fillNodeInfo(NODE_NAME_SGSNMME, NetworkElementType.SGSNMME.getNeTypeString(), WRONG_OSS_MODEL_IDENTITY_TO_BE_REPLACED))

        def subscription = subscriptionType.getIdentifier().newInstance() as EventSubscription
        subscription.setEvents([new EventInfo("ACTIVATE", "OTHER")])
        subscription.setName("SubscriptionTestName")
        subscription.setUserType(UserType.USER_DEF)
        subscription.setOutputMode(OutputModeType.FILE)
        subscription.setNodes(nodeList)
        subscription.setType(subscriptionType)
        subscription.setIsImported(true)

        when: "subscription is validated"
        subscriptionReadOperationService.validateAndAdjustSubscriptionData(subscription)

        then: "exception should be thrown"
        noExceptionThrown()
        subscription.getEvents().equals([new EventInfo("ACTIVATE", "OTHER")])

        where:
        subscriptionType << [SubscriptionType.EBM]
    }

    @Unroll
    def "When validating a subscription of type=#subscriptionType with One Valid Event and One Invalid Event then Only Valid Event is Retained"() {
        given:
        mockedPmFunctionEnabledWrapper.isPmFunctionEnabled(_) >> true
        createNodesinDps()
        List<Node> nodeList = new ArrayList<Node>()
        nodeList.add(fillNodeInfo(NODE_NAME_ERBS_1, NetworkElementType.ERBS.getNeTypeString(), WRONG_OSS_MODEL_IDENTITY_TO_BE_REPLACED))
        def node = new Node()
        node.setNeType("SGSN-MME")
        node.setFdn("NetworkElement=" + NODE_NAME_SGSNMME)
        node.setOssModelIdentity("16A-CP02")
        node.setName((String) NODE_NAME_SGSNMME)
        node.setId(281474977591390L)
        node.setTechnologyDomain(["EPS"])
        node.setPmFunction(true)

        def node1 = new Node()
        node1.setNeType("ERBS")
        node1.setFdn("NetworkElement=LTE01ERBS00002")
        node1.setOssModelIdentity("18.Q2-J.1.280")
        node1.setName("LTE01ERBS00002")
        node1.setId(281474977608869L)
        node1.setTechnologyDomain(["EPS"])
        node1.setPmFunction(true)

        def subscription = subscriptionType.getIdentifier().newInstance() as EventSubscription
        subscription.setEvents([new EventInfo("ACTIVATE", "OTHER"), new EventInfo("INVALID_EVENT", "INVALID_EVENT_GROUP")])
        subscription.setName("SubscriptionTestName")
        subscription.setUserType(UserType.USER_DEF)
        subscription.setOutputMode(OutputModeType.FILE)
        subscription.setNodes(nodeList)
        subscription.setType(subscriptionType)
        subscription.setIsImported(true)

        when: "subscription is validated"
        subscriptionReadOperationService.validateAndAdjustSubscriptionData(subscription)

        then: "exception should be thrown"
        def exception = thrown(PfmDataException)
        exception.message.contains("Events provided from imported subscription are invalid: ")

        where:
        subscriptionType << [SubscriptionType.EBM, SubscriptionType.CELLTRACE, SubscriptionType.CONTINUOUSCELLTRACE, SubscriptionType.UETR]
    }

    @Unroll
    def "When validating a subscription of type=#subscriptionType with Valid Event Based Counter and One Valid Event then Valid Counters and Events are Retained"() {
        given:
        mockedPmFunctionEnabledWrapper.isPmFunctionEnabled(_) >> true
        createNodesinDps()
        nodeListIdentity = ResourceSubscription.getHashCodeFromNodeNames(neList)
        List<Node> nodeList = new ArrayList<Node>()
        nodeList.add(fillNodeInfo(NODE_NAME_SGSNMME, NetworkElementType.SGSNMME.getNeTypeString(), WRONG_OSS_MODEL_IDENTITY_TO_BE_REPLACED))
        nodeList.add(fillNodeInfo(NODE_NAME_ERBS_1, NetworkElementType.ERBS.getNeTypeString(), "18.Q2-J.1.280"))
        nodeList.add(fillNodeInfo(NODE_NAME_RADIO_NODE, NetworkElementType.RADIONODE.getNeTypeString(), "16A-CP02"))

        def subscription = subscriptionType.getIdentifier().newInstance()
        subscription.setEbsCounters([new CounterInfo("MME Attach", "MME"), new CounterInfo("MME ATTACH ABORT", "TAI")])
        subscription.setEvents([new EventInfo("L_TAU", "OTHER")])
        subscription.setName("SubscriptionTestName")
        subscription.setUserType(UserType.USER_DEF)
        subscription.setOutputMode(OutputModeType.FILE)
        subscription.setNodes([nodeList.get(nodeIndex)])
        subscription.setType(subscriptionType)
        subscription.setIsImported(true)
        subscription.setEbsEnabled(true)

        when: "subscription is validated"
        subscriptionReadOperationService.validateAndAdjustSubscriptionData(subscription)

        then: "exception should not be thrown"
        noExceptionThrown()
        subscription.getEbsCounters().size() == 2
        subscription.getEvents().sort() == [new EventInfo("L_TAU", "OTHER"), new EventInfo("L_ATTACH", "OTHER")].sort()

        where:
        subscriptionType << [SubscriptionType.EBM]
        nodeIndex << [0]
    }

    @Unroll
    def "When validating an imported subscription of type=#subscriptionType with Valid Counters and Events but wrong UeFRaction"() {
        given:
        mockedPmFunctionEnabledWrapper.isPmFunctionEnabled(_) >> true
        createNodesinDps()
        nodeListIdentity = ResourceSubscription.getHashCodeFromNodeNames(neList)
        List<Node> nodeList = new ArrayList<Node>()
        nodeList.add(fillNodeInfo(NODE_NAME_ERBS_1, NetworkElementType.ERBS.getNeTypeString(), "18.Q2-J.1.280"))

        def subscription = subscriptionType.getIdentifier().newInstance()
        subscription.setEbsCounters([new CounterInfo("pmHoExeAttAto", "UtranCellRelation")])
        subscription.setEvents([new EventInfo("INTERNAL_EVENT_ANR_CONFIG_MISSING", "INTERNAL")])
        subscription.setName("SubscriptionTestName")
        subscription.setUserType(UserType.USER_DEF)
        subscription.setOutputMode(OutputModeType.FILE)
        subscription.setNodes([nodeList.get(nodeIndex)])
        subscription.setType(subscriptionType)
        subscription.setIsImported(true)
        subscription.setUeFraction(3000)

        when: "subscription is validated"
        subscriptionReadOperationService.validateAndAdjustSubscriptionData(subscription)

        then: "exception should not be thrown"
        def exception = thrown(ValidationException)
        exception.message.contains("Numeric value '3000' is outside allowable range. ueFraction : it ranges from 1 to 1000, default is 1")

        where:
        subscriptionType << [SubscriptionType.CELLTRACE]
        nodeIndex << [0]

    }

    @Unroll
    def "When validating an imported subscription of type=#subscriptionType with valid InterfaceType=#testData.interfaceTypes for #testData.nodeGrouping, it should NOT throw Exception"() {
        given:
        List<NodeInfo> nodeInfoList = new ArrayList<>();
        com.ericsson.oss.pmic.dto.subscription.cdts.NodeInfo nodeInfo = new com.ericsson.oss.pmic.dto.subscription.cdts.NodeInfo(testData.nodeGrouping, traceDepth, testData.interfaceTypes)
        nodeInfoList.add(nodeInfo)
        subscription = subscriptionType.getIdentifier().newInstance() as Subscription
        subscription.setName("SubscriptionTestName")
        subscription.setUserType(UserType.USER_DEF)
        subscription.setType(subscriptionType)
        subscription.setNodeInfoList(nodeInfoList)
        subscription.setIsImported(true)

        when: "subscription is validated"
        subscriptionReadOperationService.validateAndAdjustSubscriptionData(subscription)

        then: "exception should not be thrown"
        noExceptionThrown()

        where:
        [subscriptionType, traceDepth, testData] << [
                [SubscriptionType.UETRACE],
                [TraceDepth.MINIMUM, TraceDepth.MAXIMUM, TraceDepth.MEDIUM, TraceDepth.MINIMUM_WITHOUT_VENDOR_SPECIFIC_EXTENSION, TraceDepth.MEDIUM_WITHOUT_VENDOR_SPECIFIC_EXTENSION, TraceDepth.MAXIMUM_WITHOUT_VENDOR_SPECIFIC_EXTENSION, TraceDepth.DISABLED],
                [
                        [nodeGrouping: NodeGrouping.ENODEB, interfaceTypes: ["uu", "s1", "x2"]],
                        [nodeGrouping: NodeGrouping.ENODEB, interfaceTypes: ["x2"]],
                        [nodeGrouping: NodeGrouping.ENODEB, interfaceTypes: ["uu", "s1"]],
                        [nodeGrouping: NodeGrouping.ENODEB, interfaceTypes: ["uu", "s1", "x2"]],
                        [nodeGrouping: NodeGrouping.MME, interfaceTypes: ["s1_mme", "s3_s16", "s6a", "s11", "sv", "iu", "gb", "slg", "sls", "gr", "gn_gp", "sgs", "s6d", "s4", "s3_s10"]],
                        [nodeGrouping: NodeGrouping.MME, interfaceTypes: ["s11", "sv", "iu", "gb", "slg", "sls", "s6d", "s4", "s3_s10"]],
                        [nodeGrouping: NodeGrouping.MME, interfaceTypes: ["s3_s10"]]
                ]
        ].combinations()
    }

    @Unroll
    def "When validating an imported subscription of type=#subscriptionType with invalid Interface Types=#testData.interfaceTypes for nodeGrouping=#testData.nodeGrouping, it should throw Exception"() {
        given:
        List<NodeInfo> nodeInfoList = new ArrayList<>();
        com.ericsson.oss.pmic.dto.subscription.cdts.NodeInfo nodeInfo = new com.ericsson.oss.pmic.dto.subscription.cdts.NodeInfo(testData.nodeGrouping, traceDepth, testData.interfaceTypes)
        nodeInfoList.add(nodeInfo)
        subscription = subscriptionType.getIdentifier().newInstance() as Subscription
        subscription.setName("SubscriptionTestName")
        subscription.setUserType(UserType.USER_DEF)
        subscription.setType(subscriptionType)
        subscription.setNodeInfoList(nodeInfoList)
        subscription.setIsImported(true)

        when: "subscription is validated"
        subscriptionReadOperationService.validateAndAdjustSubscriptionData(subscription)

        then: "exception should be thrown"
        def exception = thrown(ValidationException)
        exception.message.contains("Invalid Interface types ")

        where:
        [subscriptionType, traceDepth, testData] << [
                [SubscriptionType.UETRACE],
                [TraceDepth.MINIMUM, TraceDepth.MAXIMUM, TraceDepth.MEDIUM, TraceDepth.MINIMUM_WITHOUT_VENDOR_SPECIFIC_EXTENSION, TraceDepth.MEDIUM_WITHOUT_VENDOR_SPECIFIC_EXTENSION, TraceDepth.MAXIMUM_WITHOUT_VENDOR_SPECIFIC_EXTENSION, TraceDepth.DISABLED],
                [
                        [nodeGrouping: NodeGrouping.MME, interfaceTypes: ["uu", "s1", "x2"]],
                        [nodeGrouping: NodeGrouping.MME, interfaceTypes: ["Unknown"]],
                        [nodeGrouping: NodeGrouping.ENODEB, interfaceTypes: ["s1_mme", "s3_s16", "s6a", "s11", "sv", "iu", "gb", "slg", "sls", "gr", "gn_gp", "sgs", "s6d", "s4", "s3_s10"]],
                        [nodeGrouping: NodeGrouping.ENODEB, interfaceTypes: ["Unknown"]]
                ]
        ].combinations()
    }

    @Unroll
    def "When validating an imported subscription of type=#subscriptionType with counters for nodes without ossModelIdentity and not supporting counters, it should throw an Exception"() {
        given:
        mockedPmFunctionEnabledWrapper.isPmFunctionEnabled(_) >> true
        mockedPmicModelDeploymentValidator.isCounterValidationSupportedForGivenTargetType(_) >> false
        createNodesinDps()
        def node = new Node()
        node.setNeType("ERBS")
        node.setFdn("NetworkElement=" + NODE_NAME_ERBS_FAKE_CUSTOM_NODE)
        node.setOssModelIdentity(null)
        node.setName((String) NODE_NAME_ERBS_FAKE_CUSTOM_NODE)
        node.setId(281474977591390L)
        node.setTechnologyDomain(["EPS"])
        node.setPmFunction(true)

        subscription = subscriptionType.getIdentifier().newInstance() as Subscription
        subscription.setName("SubscriptionTestName")
        subscription.setUserType(UserType.USER_DEF)
        subscription.setType(subscriptionType)
        subscription.setCounters([new CounterInfo("ipv6OutNoRoutes", "SGSN-MME_IPv6_RouterInstance"), new CounterInfo("ipv6InReceives", "SGSN-MME_IPv6_RouterInstance")])
        subscription.setAdministrationState(INACTIVE)
        subscription.setNodes([node])
        subscription.setIsImported(true)

        when: "subscription is validated"
        subscriptionReadOperationService.validateAndAdjustSubscriptionData(subscription)

        then: "exception should be thrown"
        def exception = thrown(ValidationException)
        exception.message.contains("Counters provided from imported subscription are invalid:")

        where:
        subscriptionType << [SubscriptionType.STATISTICAL]
    }

    @Unroll
    def "When validating Celltrace subscription with valid events - isImported false "() {
        given:
        createNodesinDps()
        nodeListIdentity = ResourceSubscription.getHashCodeFromNodeNames(neList)
        List<Node> nodeList = new ArrayList<Node>()
        nodeList.add(fillNodeInfo(NODE_NAME_ERBS_1, NetworkElementType.ERBS.getNeTypeString(), "18.Q2-J.1.280"))

        subscription = new CellTraceSubscription()
        subscription.setType(SubscriptionType.CELLTRACE)
        subscription.setEbsCounters([VALID_COUNTER])
        subscription.setName("SubscriptionTestName")
        subscription.setUserType(UserType.USER_DEF)
        subscription.setAdministrationState(INACTIVE)
        subscription.setNodes(nodeList)
        subscription.setIsImported(false)
        subscription.setEbsEvents(ebsEvents)
        subscription.setEvents(events)

        when: "subscription is validated"
        subscriptionReadOperationService.validateAndAdjustSubscriptionData(subscription)
        then: "exception should be thrown"
        thrown ValidationException
        where:
        events                     | ebsEvents                  || eventsResult  | ebsEventsResult
        [VALID_EVENT]              | [VALID_EVENT]              || [VALID_EVENT] | []
        [VALID_EVENT, VALID_EVENT] | [VALID_EVENT, VALID_EVENT] || [VALID_EVENT] | []
        [VALID_EVENT]              | []                         || [VALID_EVENT] | []
        []                         | [VALID_EVENT]              || []            | []
        []                         | []                         || []            | []
    }

    @Unroll
    def "When validating an imported Celltrace subscription with valid events moved to EBSEvents - isImported false"() {
        given:
        createNodesinDps()
        nodeListIdentity = ResourceSubscription.getHashCodeFromNodeNames(neList)
        List<Node> nodeList = new ArrayList<Node>()
        nodeList.add(fillNodeInfo(NODE_NAME_ERBS_1, NetworkElementType.ERBS.getNeTypeString(), "18.Q2-J.1.280"))

        subscription = new CellTraceSubscription()
        subscription.setType(SubscriptionType.CELLTRACE)
        subscription.setEbsCounters([CELTRACE_COUNTER_EVENT_BASED])
        subscription.setName("SubscriptionTestName")
        subscription.setUserType(UserType.USER_DEF)
        subscription.setAdministrationState(INACTIVE)
        subscription.setNodes(nodeList)
        subscription.setIsImported(false)
        subscription.setEbsEvents(ebsEvents)
        subscription.setEvents(events)

        when: "subscription is validated"
        subscriptionReadOperationService.validateAndAdjustSubscriptionData(subscription)
        then: "exception should be thrown"
        thrown ValidationException
        where:
        events                              | ebsEvents     || eventsResult | ebsEventsResult
        [CELTRACE_BASE_EVENT_FOR_COUNTER_1] | [VALID_EVENT] || []           | [CELTRACE_BASE_EVENT_FOR_COUNTER_3, CELTRACE_BASE_EVENT_FOR_COUNTER_1, CELTRACE_BASE_EVENT_FOR_COUNTER_2]
    }

    @Unroll
    def "When validating an imported subscription of type=#subscriptionType mixed with invalid node types, it should throw an Exception"() {
        given:
        mockedPmFunctionEnabledWrapper.isPmFunctionEnabled(_) >> true
        mockedPmicModelDeploymentValidator.isCounterValidationSupportedForGivenTargetType(_) >> false
        createNodesinDps()
        nodeListIdentity = ResourceSubscription.getHashCodeFromNodeNames(neList)
        List<Node> nodeList = new ArrayList<Node>()
        nodeList.add(fillNodeInfo(NODE_NAME_ESA1, NODE_TYPE_ESA, null))
        nodeList.add(fillNodeInfo(NODE_NAME_ERBS_1, "ERBS", "18.Q2-J.1.280"))

        subscription = subscriptionType.getIdentifier().newInstance() as Subscription
        subscription.setName("SubscriptionTestWithMixedNodes")
        subscription.setUserType(UserType.USER_DEF)
        subscription.setType(subscriptionType)
        subscription.setAdministrationState(INACTIVE)
        subscription.setNodes(nodeList)
        subscription.setIsImported(true)

        when: "subscription is validated"
        subscriptionReadOperationService.validateAndAdjustSubscriptionData(subscription)

        then: "exception should be thrown"
        def exception = thrown(ValidationException)
        exception.message.contains("Incompatible nodeTypes are present in the subscription")

        where:
        subscriptionType << [SubscriptionType.STATISTICAL]
    }

    @Unroll
    def "When validating an imported subscription of type=#subscriptionType for nodes without ossModelIdentity and not supporting counters, it should not throw  Exception"() {
        given:
        mockedPmicModelDeploymentValidator.isCounterValidationSupportedForGivenTargetType(_) >> false
        mockedPmFunctionEnabledWrapper.isPmFunctionEnabled(_) >> true
        createNodesinDps()
        def node = new Node()
        node.setNeType("ERBS")
        node.setFdn("NetworkElement=" + NODE_NAME_ERBS_FAKE_CUSTOM_NODE)
        node.setOssModelIdentity(null)
        node.setName((String) NODE_NAME_ERBS_FAKE_CUSTOM_NODE)
        node.setId(281474977591390L)
        node.setTechnologyDomain(["EPS"])
        node.setPmFunction(true)

        subscription = subscriptionType.getIdentifier().newInstance() as Subscription
        subscription.setName("SubscriptionTestName")
        subscription.setUserType(UserType.USER_DEF)
        subscription.setType(subscriptionType)
        subscription.setAdministrationState(INACTIVE)
        subscription.setNodes([node])
        subscription.setIsImported(true)

        when: "subscription is validated"
        subscriptionReadOperationService.validateAndAdjustSubscriptionData(subscription)

        then: "exception should be thrown"
        noExceptionThrown()
        Node ne = ((StatisticalSubscription) subscription).getNodes()[0]
        ne.getNeType() == NetworkElementType.ERBS.getNeTypeString()
        ne.getOssModelIdentity() == null
        ne.getTechnologyDomain() == ["EPS"]

        where:
        subscriptionType << [SubscriptionType.STATISTICAL]
    }

    @Unroll
    def "When validating an imported valid RES subscription with #titleString, it should not throw exceptions"() {
        given: "node in dps and json parser configured"
        mockedPmFunctionEnabledWrapper.isPmFunctionEnabled(_) >> true
        createRncNodesInDps()
        createRadioNodeNodesInDps()
        def path = "src/test/resources/" + jsonFileName
        ObjectMapper mapper = new ObjectMapper()
        //pretify the output
        mapper.configure(SerializationConfig.Feature.INDENT_OUTPUT, true)
        //sort properties alphabetically
        mapper.configure(SerializationConfig.Feature.SORT_PROPERTIES_ALPHABETICALLY, true)
        //When reading JSON from file and deserializing to POJO, fail if there are unknown properties
        mapper.configure(DeserializationConfig.Feature.FAIL_ON_UNKNOWN_PROPERTIES, true)
        and: "deserialize valid imported subscription"
        Subscription subscription = mapper.readValue(new File(path), ResSubscription.class)
        subscription.setIsImported(true)

        when: "subscription is validated"
        subscriptionReadOperationService.validateAndAdjustSubscriptionData(subscription)

        then: "no exception should be thrown"
        noExceptionThrown()

        where:
        titleString                   | jsonFileName
        "no RBS measurements enabled" | "ResSubscription.json"
        "RBS measurements enabled"    | "ResSubscription_AttachedNodes.json"
    }

    @Unroll
    def "When validating an imported RES subscription with #titleString, it should throw an Exception"() {
        given: "node in dps and json parser configured"
        mockedPmFunctionEnabledWrapper.isPmFunctionEnabled(_) >> true
        createRncNodesInDps()
        def path = "src/test/resources/" + jsonFileName + ".json"
        ObjectMapper mapper = new ObjectMapper()
        //pretify the output
        mapper.configure(SerializationConfig.Feature.INDENT_OUTPUT, true)
        //sort properties alphabetically
        mapper.configure(SerializationConfig.Feature.SORT_PROPERTIES_ALPHABETICALLY, true)
        //When reading JSON from file and deserializing to POJO, fail if there are unknown properties
        mapper.configure(DeserializationConfig.Feature.FAIL_ON_UNKNOWN_PROPERTIES, true)
        and: "deserialize imported subscription"
        Subscription subscription = mapper.readValue(new File(path), ResSubscription.class)
        subscription.setIsImported(true)

        when: "subscription is validated"
        subscriptionReadOperationService.validateAndAdjustSubscriptionData(subscription)

        then: "exception should be thrown"
        def exception = thrown(ValidationException)
        exception.message.contains(exceptionString)

        where:
        titleString                    | jsonFileName                               | exceptionString
        "duplicated Rmq-Service pair"  | "ResSubscription_DuplicateRmq-ServicePair" | "Rmq-Service pair provided from input subscription is already used"
        "invalid counters"             | "ResSubscription_InvalidCounter"           | "Counter provided from input subscription is invalid"
        "invalid counter groups"       | "ResSubscription_InvalidCounterGroup"      | "Counter Group provided from input subscription is invalid"
        "counters not consistent"      | "ResSubscription_CountersNotConsistent"    | "counters and resMeasDef attributes from input Subscription are not consistent"
        "invalid ResMeasDef attribute" | "ResSubscription_InvalidResMeasDefCounter" | "Counters provided from input subscription are invalid"
        "invalid rmq"                  | "ResSubscription_InvalidRmq"               | "Rmq provided from input subscription is invalid"
        "invalid service"              | "ResSubscription_InvalidService"           | "Service provided from input subscription is invalid"
        "invalid cells"                | "ResSubscription_InvalidCells"             | "Cell provided from input subscription are invalid"
        "invalid UE Fraction"          | "ResSubscription_InvalidUeFraction"        | "UE Fraction provided from input subscription is invalid"
        "empty nodes list"             | "ResSubscription_NoNodes"                  | "Nodes list in imported subscription is empty"
    }

    def "When validating an imported CellTrace subscription of Category ESN with valid ebsEvents - isImported true"() {
        given:
        createNodesinDps()
        nodeListIdentity = ResourceSubscription.getHashCodeFromNodeNames(neList)
        List<Node> nodeList = new ArrayList<Node>()
        nodeList.add(fillNodeInfo(NODE_NAME_ERBS_1, NetworkElementType.ERBS.getNeTypeString(), "18.Q2-J.1.280"))

        subscription = new CellTraceSubscription()
        subscription.setType(SubscriptionType.CELLTRACE)
        subscription.setCellTraceCategory(CellTraceCategory.ESN)
        subscription.setName("ESNSubscriptionTestName")
        subscription.setUserType(UserType.USER_DEF)
        subscription.setAdministrationState(INACTIVE)
        subscription.setNodes(nodeList)
        subscription.setIsImported(true)
        subscription.setEbsEvents([VALID_EVENT])

        when: "subscription is validated"
        subscriptionReadOperationService.validateAndAdjustSubscriptionData(subscription)

        then: "exception should be thrown"
        def exception = thrown ValidationException
        exception.getMessage() == "Import is not supported for Event Stream NBI Subscription ESNSubscriptionTestName"
    }

    @Unroll
    def "When validating an imported #subscriptionType subscription of ropPeriod #ropPeriod - isImported true"() {
        given:
        nodeListIdentity = ResourceSubscription.getHashCodeFromNodeNames(neList)

        subscription = subscriptionType.getIdentifier().newInstance() as Subscription
        subscription.setType(subscriptionType)
        subscription.setName("BscRecordingsSubscriptionTestName")
        subscription.setUserType(UserType.USER_DEF)
        subscription.setAdministrationState(INACTIVE)
        subscription.setRop(ropPeriod)
        subscription.setIsImported(true)

        when: "subscription is validated"
        subscriptionReadOperationService.validateAndAdjustSubscriptionData(subscription)

        then: "exception should be thrown"

        def exception = thrown ValidationException
        exception.getMessage() == "Invalid rop period " + ropPeriod.toString() + " for subscription type = " + subscriptionType.toString()

        where:
        subscriptionType               | ropPeriod
        SubscriptionType.BSCRECORDINGS | RopPeriod.ONE_MIN
        SubscriptionType.BSCRECORDINGS | RopPeriod.ONE_HOUR
        SubscriptionType.BSCRECORDINGS | RopPeriod.ONE_DAY
        SubscriptionType.MTR           | RopPeriod.ONE_MIN
        SubscriptionType.MTR           | RopPeriod.ONE_HOUR
        SubscriptionType.MTR           | RopPeriod.ONE_DAY
    }

    @Unroll
    def "When validating an imported #subscriptionType subscription with Invalid AccessTypes #mtrAccessType - isImported true"() {
        given:

        subscription = new MtrSubscription()
        subscription.setType(subscriptionType)
        subscription.setName("BscRecordingsSubscriptionTestName")
        subscription.setUserType(UserType.USER_DEF)
        subscription.setAdministrationState(INACTIVE)
        subscription.setRop(RopPeriod.FIFTEEN_MIN)
        subscription.setMtrAccessTypes(mtrAccessType)
        subscription.setIsImported(true)

        when: "subscription is validated"
        subscriptionReadOperationService.validateAndAdjustSubscriptionData(subscription)

        then: "exception should be thrown"

        def exception = thrown ValidationException
        if (NullError) {
            exception.getMessage() == "Access Types cannot be null"
        } else {
            exception.getMessage() == "Invalid AccessTypes for subscription type = " + subscriptionType.toString() + ", LCS is mandatory"
        }

        where:
        subscriptionType     | mtrAccessType                                | NullError
        SubscriptionType.MTR | []                                           | false
        SubscriptionType.MTR | [MtrAccessType.values() - MtrAccessType.LCS] | false
        SubscriptionType.MTR | null                                         | true
    }


    @Unroll
    def "When validating an imported #subscriptionType subscription with all nodes having PM Function disabled then it should throw Exception"() {
        given: "an imported #subscriptionType subscription with all nodes having PM Function disabled"
        mockedPmFunctionEnabledWrapper.containsFdn(_) >> true
        mockedPmFunctionEnabledWrapper.isPmFunctionEnabled(_) >> false
        createNodesinDps()
        List<Node> nodeList = new ArrayList<Node>()
        nodeList.add(fillNodeInfo(NODE_NAME_ERBS_1, NetworkElementType.ERBS.getNeTypeString(), "16B-G.1.281", false))
        nodeList.add(fillNodeInfo(NODE_NAME_RNC, NetworkElementType.RNC.getNeTypeString(), "16B-V.7.1659", false))

        subscription = subscriptionType.getIdentifier().newInstance() as Subscription
        subscription.setType(subscriptionType)
        subscription.setName("SubscriptionTestName")
        subscription.setUserType(UserType.USER_DEF)
        subscription.setAdministrationState(INACTIVE)
        subscription.setNodes(nodeList)
        subscription.setIsImported(true)

        when: "subscription is validated"
        subscriptionReadOperationService.validateAndAdjustSubscriptionData(subscription)

        then: "exception should be thrown"
        def exception = thrown(PmFunctionValidationException)
        assert exception.message.contains("All nodes are unavailable due to PM Function disabled")

        where:
        subscriptionType << [SubscriptionType.CTUM, SubscriptionType.RES, SubscriptionType.CELLTRACE, SubscriptionType.CELLTRAFFIC, SubscriptionType.CONTINUOUSCELLTRACE, SubscriptionType.EBM, SubscriptionType.GPEH, SubscriptionType.UETR, SubscriptionType.CELLRELATION, SubscriptionType.MOINSTANCE, SubscriptionType.STATISTICAL]
    }

    @Unroll
    def "When validating an imported #subscriptionType subscription with some nodes having PM Function disabled then it should throw Exception"() {
        given: "an imported #subscriptionType subscription with some nodes having PM Function disabled"
        mockedPmFunctionEnabledWrapper.containsFdn(_) >> true
        mockedPmFunctionEnabledWrapper.isPmFunctionEnabled("NetworkElement=" + NODE_NAME_ERBS_1) >> true
        mockedPmFunctionEnabledWrapper.isPmFunctionEnabled("NetworkElement=" + NODE_NAME_RNC) >> false
        mockedPmFunctionEnabledWrapper.isPmFunctionEnabled("NetworkElement=" + NODE_NAME_ERBS_2) >> false

        createNodesinDps()
        List<Node> nodeList = new ArrayList<Node>()
        nodeList.add(fillNodeInfo(NODE_NAME_ERBS_1, NetworkElementType.ERBS.getNeTypeString(), "16B-G.1.281"))
        nodeList.add(fillNodeInfo(NODE_NAME_RNC, NetworkElementType.RNC.getNeTypeString(), "16B-V.7.1659", false))
        nodeList.add(fillNodeInfo(NODE_NAME_ERBS_2, NetworkElementType.ERBS.getNeTypeString(), "16B-G.1.281", false))

        subscription = subscriptionType.getIdentifier().newInstance() as Subscription
        subscription.setType(subscriptionType)
        subscription.setName("SubscriptionTestName")
        subscription.setUserType(UserType.USER_DEF)
        subscription.setAdministrationState(INACTIVE)
        subscription.setNodes(nodeList)
        subscription.setIsImported(true)

        when: "subscription is validated"
        subscriptionReadOperationService.validateAndAdjustSubscriptionData(subscription)

        then: "exception should be thrown containg invalid nodes"
        def exception = thrown(PmFunctionValidationException)
        assert exception.message.contains("There are unavailable nodes due to PM Function disabled")
        def invalidNodes = exception.getInvalidNodes()
        assert invalidNodes.size() == 2
        def invalidNodeNames = []
        invalidNodes.each { invalidNodeNames.add(it.getName()) }
        assert invalidNodeNames.containsAll([NODE_NAME_RNC, NODE_NAME_ERBS_2])

        where:
        subscriptionType << [SubscriptionType.CTUM, SubscriptionType.RES, SubscriptionType.CELLTRACE, SubscriptionType.CELLTRAFFIC, SubscriptionType.CONTINUOUSCELLTRACE, SubscriptionType.EBM, SubscriptionType.GPEH, SubscriptionType.UETR, SubscriptionType.CELLRELATION, SubscriptionType.MOINSTANCE, SubscriptionType.STATISTICAL]
    }

    def "When validating an imported MOINSTANCE subscription with MoInstances having PM function disabled then no exceptions should by throw"() {
        given: "an imported MOINSTANCE subscription with some nodes having PM Function disabled"
        mockedPmFunctionEnabledWrapper.containsFdn(_) >> true
        mockedPmFunctionEnabledWrapper.isPmFunctionEnabled("NetworkElement=" + NODE_NAME_RNC) >> true
        mockedPmFunctionEnabledWrapper.isPmFunctionEnabled("NetworkElement=" + NODE_NAME_RNC_2) >> false
        mockedPmFunctionEnabledWrapper.isPmFunctionEnabled("NetworkElement=" + NODE_NAME_RNC_3) >> true

        createNodesinDps()
        List<Node> nodeList = new ArrayList<Node>()
        nodeList.add(fillNodeInfo(NODE_NAME_RNC, NetworkElementType.RNC.getNeTypeString(), "16B-V.7.1659", false))
        nodeList.add(fillNodeInfo(NODE_NAME_RNC_3, NetworkElementType.RNC.getNeTypeString(), "16B-V.7.1659", false))

        subscription = new MoinstanceSubscription()
        subscription.setType(SubscriptionType.MOINSTANCE)
        subscription.setCounters([new CounterInfo("pmBwErrBlocks", "Aal1TpVccTp")])
        subscription.setMoInstances([new MoinstanceInfo(NODE_NAME_RNC_3, "Aal1TpVccTp=1-13-1"), new MoinstanceInfo(NODE_NAME_RNC, "Aal1TpVccTp=1-13-1"), new MoinstanceInfo(NODE_NAME_RNC_2, "Aal1TpVccTp=1-13-1")])
        subscription.setName("SubscriptionTestName")
        subscription.setUserType(UserType.USER_DEF)
        subscription.setAdministrationState(INACTIVE)
        subscription.setNodes(nodeList)
        subscription.setIsImported(true)

        when: "subscription is validated"
        subscriptionReadOperationService.validateAndAdjustSubscriptionData(subscription)

        then: "no exception should be thrown"
        noExceptionThrown()
        def moInstancesNodeNames = []
        subscription.getMoInstances().each { moInstancesNodeNames.add(it.getNodeName()) }
        assert moInstancesNodeNames.containsAll([NODE_NAME_RNC, NODE_NAME_RNC_3])
    }

    def addManagedElementInDps(final ManagedObject managedObject, final String nodeName) {
        //managedElement
        ManagedObject managedElement = configurableDps.addManagedObject().withFdn("NetworkElement=" + nodeName + ",ManagedElement=1").build()
        managedObject.addAssociation("nodeRootRef", managedElement)
        //transportNetwork
        configurableDps.addManagedObject().withFdn("NetworkElement=" + nodeName + ",ManagedElement=1,TransportNetwork=1").namespace('RNC_NODE_MODEL').build()
        //moInstances
        configurableDps.addManagedObject().withFdn("NetworkElement=" + nodeName + ",ManagedElement=1,TransportNetwork=1,Aal1TpVccTp=1-13-1").namespace('RNC_NODE_MODEL').type('Aal1TpVccTp').addAttributes(["name": "1-1-1", "type": "Aal1TpVccTp"]).build()
        configurableDps.addManagedObject().withFdn("NetworkElement=" + nodeName + ",ManagedElement=1,TransportNetwork=1,Aal1TpVccTp=1-31-1").namespace('RNC_NODE_MODEL').type('Aal1TpVccTp').addAttributes(["name": "1-1-1", "type": "Aal1TpVccTp"]).build()
        configurableDps.addManagedObject().withFdn("NetworkElement=" + nodeName + ",ManagedElement=1,TransportNetwork=1,Aal1TpVccTp=1-1-1").namespace('RNC_NODE_MODEL').type('Aal1TpVccTp').addAttributes(["name": "1-1-1", "type": "Aal1TpVccTp"]).build()
        //UtranCells
        configurableDps.addManagedObject().withFdn("NetworkElement=" + nodeName + ",ManagedElement=1,RncFunction=1,UtranCell=RNC01-1-1").namespace('RNC_NODE_MODEL').type('UtranCell').addAttributes(["name": "RNC01-1-1", "type": "UtranCell"]).build()
        configurableDps.addManagedObject().withFdn("NetworkElement=" + nodeName + ",ManagedElement=1,RncFunction=1,UtranCell=RNC01-10-1").namespace('RNC_NODE_MODEL').type('UtranCell').addAttributes(["name": "RNC01-10-1", "type": "UtranCell"]).build()
        configurableDps.addManagedObject().withFdn("NetworkElement=" + nodeName + ",ManagedElement=1,RncFunction=1,UtranCell=RNC01-10-2").namespace('RNC_NODE_MODEL').type('UtranCell').addAttributes(["name": "RNC01-10-2", "type": "UtranCell"]).build()
    }

    private ManagedObject addNode(String name, NetworkElementType neType, String ossModelIdentity) {
        Map attributes = new HashMap<String, Object>()
        List<String> technologyDomains = ["EPS"]
        attributes.put("technologyDomain", technologyDomains)

        if (ossModelIdentity != null) {
            return nodeUtil.builder(name).neType(neType).ossModelIdentity(ossModelIdentity).attributes(attributes).build()
        } else {
            return nodeUtil.builder(name).neType(neType).ossModelIdentity(null).attributes(attributes).build()
        }
    }

    private static Node getNode(ManagedObject nodeMO) {
        Node nodeInfo = new Node()
        nodeInfo.setFdn(nodeMO.getFdn())
        nodeInfo.setOssModelIdentity(nodeMO.getAttribute(Node.NetworkElement200Attribute.ossModelIdentity.name()) as String)
        nodeInfo.setId(nodeMO.getPoId())
        nodeInfo.setNeType(nodeMO.getAttribute(Node.NetworkElement200Attribute.neType.name()) as String)
        nodeInfo.setPmFunction(nodeMO.getAttribute(Node.NetworkElement200Attribute.pmFunction.name()))
        return nodeInfo
    }

    private void createNodesinDps() {
        createErbsNodesInDps()
        createSgsnMmeNodesInDps()
        createRncNodesInDps()
        createRadioNodeNodesInDps()
        createErbsFakeNodesInDps()
        createESAFakeNodesInDps()
    }

    private void createErbsNodesInDps() {
        neList.add(getNode(addNode(NODE_NAME_ERBS_1, NetworkElementType.ERBS, "18.Q2-J.1.280")))
        neList.add(getNode(addNode(NODE_NAME_ERBS_2, NetworkElementType.ERBS, "18.Q2-J.1.280")))
        neList.add(getNode(addNode(LTE02_ERBS00021, NetworkElementType.ERBS, "18.Q2-J.1.280")))
    }

    private void createSgsnMmeNodesInDps() {
        neList.add(getNode(addNode(NODE_NAME_SGSNMME, NetworkElementType.SGSNMME, "16A-CP02")))
    }

    private void createRncNodesInDps() {
        ManagedObject nodeMO = addNode(NODE_NAME_RNC, NetworkElementType.RNC, "16B-V.7.1659")
        neList.add(getNode(nodeMO))
        addManagedElementInDps(nodeMO, NODE_NAME_RNC)

        nodeMO = addNode(NODE_NAME_RNC_2, NetworkElementType.RNC, "16B-V.7.1659")
        neList.add(getNode(nodeMO))
        addManagedElementInDps(nodeMO, NODE_NAME_RNC_2)

        nodeMO = addNode(NODE_NAME_RNC_3, NetworkElementType.RNC, "16B-V.7.1659")
        neList.add(getNode(nodeMO))
        addManagedElementInDps(nodeMO, NODE_NAME_RNC_3)
    }

    private void createRadioNodeNodesInDps() {
        neList.add(getNode(addNode(NODE_NAME_RADIO_NODE, NetworkElementType.RADIONODE, "16A-CP02")))
    }

    private void createErbsFakeNodesInDps() {
        neList.add(getNode(addNode(NODE_NAME_ERBS_FAKE_CUSTOM_NODE, NetworkElementType.ERBS, null)))
    }

    private void createESAFakeNodesInDps() {
        neList.add(getNode(nodeUtil.builder(NODE_NAME_ESA1).neType(NODE_TYPE_ESA).ossModelIdentity(null).attributes(new HashMap<String, Object>()).build()))
    }

    private void createEPGFakeNodesInDps() {
        neList.add(getNode(nodeUtil.builder(NODE_NAME_EPG).neType(NODE_TYPE_EPG).ossModelIdentity("16A-CP02").attributes(new HashMap<String, Object>()).build()))
    }

    private void createFronthaul6020FakeNodesInDps() {
        neList.add(getNode(nodeUtil.builder(NODE_NAME_FRONTHAUL6020).neType(NODE_TYPE_FRONTHAUL6020).ossModelIdentity("16A-CP02").attributes(new HashMap<String, Object>()).build()))
    }

    private void createFronthaul6080FakeNodesInDps() {
        neList.add(getNode(nodeUtil.builder(NODE_NAME_FRONTHAUL6080).neType(NODE_TYPE_FRONTHAUL6080).ossModelIdentity("16A-CP02").attributes(new HashMap<String, Object>()).build()))
    }

    private void createSBG_ISNodesInDps() {
        neList.add(getNode(nodeUtil.builder(NODE_NAME_SBG_IS).neType(NODE_TYPE_SBG_IS).ossModelIdentity("16A-CP02").attributes(new HashMap<String, Object>()).build()))
    }

    private void createSIU2NodesInDps() {
        neList.add(getNode(nodeUtil.builder(NODE_NAME_SIU2).neType(NODE_TYPE_SIU2).ossModelIdentity("16A-CP02").attributes(new HashMap<String, Object>()).build()))
    }

    private void createTCU2FakeNodesInDps() {
        neList.add(getNode(nodeUtil.builder(NODE_NAME_TCU02).neType(NODE_TYPE_TCU02).ossModelIdentity("16A-CP02").attributes(new HashMap<String, Object>()).build()))
    }

    private void createVEPGFakeNodesInDps() {
        neList.add(getNode(nodeUtil.builder(NODE_NAME_VEPG).neType(NODE_TYPE_VEPG).ossModelIdentity("16A-CP02").attributes(new HashMap<String, Object>()).build()))
    }

    private void createvwMGFakeNodesInDps() {
        neList.add(getNode(nodeUtil.builder(NODE_NAME_vwMG).neType(NODE_TYPE_vwMG).ossModelIdentity("16A-CP02").attributes(new HashMap<String, Object>()).build()))
    }

    private void createWMGFakeNodesInDps() {
        neList.add(getNode(nodeUtil.builder(NODE_NAME_WMG).neType(NODE_TYPE_WMG).ossModelIdentity("16A-CP02").attributes(new HashMap<String, Object>()).build()))
    }


    private static Node fillNodeInfo(String nodeName, String nodeType, String ossModelIdentity, boolean isPmEnabled = true) {
        Node node = new Node()
        node.setNeType(nodeType)
        node.setFdn("NetworkElement=" + nodeName)
        node.setOssModelIdentity(ossModelIdentity)
        node.setName(nodeName)
        node.setId(0L)
        node.setTechnologyDomain(["EPS"])
        node.setPmFunction(isPmEnabled)
        return node
    }
}
