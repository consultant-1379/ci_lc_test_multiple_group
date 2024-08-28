/*******************************************************************************
 * COPYRIGHT Ericsson 2017
 *
 * The copyright to the computer program(s) herein is the property of
 * Ericsson Inc. The programs may be used and/or copied only with written
 * permission from Ericsson Inc. or in accordance with the terms and
 * conditions stipulated in the agreement/contract under which the
 * program(s) have been supplied.
 ******************************************************************************/

package com.ericsson.oss.services.pm.initiation.ejb.validator

import com.ericsson.cds.cdi.support.rule.ImplementationClasses
import com.ericsson.oss.pmic.impl.counters.PmCountersLifeCycleResolverImpl
import com.ericsson.oss.pmic.impl.modelservice.PmCapabilityReaderImpl
import spock.lang.Ignore

import static com.ericsson.oss.pmic.dto.subscription.enums.AdministrationState.ACTIVE
import static com.ericsson.oss.pmic.dto.subscription.enums.AdministrationState.INACTIVE
import static com.ericsson.oss.pmic.dto.subscription.enums.OutputModeType.*
import static com.ericsson.oss.pmic.dto.subscription.enums.RopPeriod.*
import static com.ericsson.oss.pmic.dto.subscription.enums.UeType.*
import static com.ericsson.oss.pmic.dto.subscription.enums.UserType.SYSTEM_DEF
import static com.ericsson.oss.pmic.dto.subscription.enums.UserType.USER_DEF
import static com.ericsson.oss.services.pm.initiation.constants.ApplicationMessages.*

import spock.lang.Shared
import spock.lang.Unroll

import javax.inject.Inject
import java.text.ParseException
import java.text.SimpleDateFormat

import com.ericsson.cds.cdi.support.rule.ImplementationInstance
import com.ericsson.oss.pmic.cdi.test.util.PmBaseSpec
import com.ericsson.oss.pmic.dto.node.Node
import com.ericsson.oss.pmic.dto.subscription.*
import com.ericsson.oss.pmic.dto.subscription.cdts.*
import com.ericsson.oss.pmic.dto.subscription.enums.CellTraceCategory
import com.ericsson.oss.pmic.dto.subscription.enums.OutputModeType
import com.ericsson.oss.pmic.dto.subscription.enums.SubscriptionType
import com.ericsson.oss.services.pm.cache.PmFunctionEnabledWrapper
import com.ericsson.oss.services.pm.initiation.common.DateUtils
import com.ericsson.oss.services.pm.initiation.constants.ApplicationMessages
import com.ericsson.oss.services.pm.services.exception.PmFunctionValidationException
import com.ericsson.oss.services.pm.services.exception.ValidationException
import com.ericsson.oss.services.pm.services.generic.SubscriptionReadOperationService

class SubscriptionValidationSpec extends PmBaseSpec {

    private static Date now = new Date()
    private static DateUtils dateUtils = new DateUtils()

    @Inject
    private SubscriptionReadOperationService subscriptionReadOperationService

    private Subscription subscription

    @Shared
    private def COUNTERS = [new CounterInfo('MME Periodic TAU REJECT CC17', 'TAI'), new CounterInfo('MME Periodic TAU REJECT CC95', 'TAI')]

    @Shared
    private def EVENTS = [new EventInfo('INTERNAL_PER_PROCESSOR_LOAD', 'GENERAL_EVALUATION'), new EventInfo('INTERNAL_EVENT_ANR_CONFIG_MISSING', 'INTERNAL')]

    @ImplementationInstance
    PmFunctionEnabledWrapper mockedPmFunctionEnabledWrapper = Mock(PmFunctionEnabledWrapper)

    @ImplementationClasses
    def implementationClasses = [PmCountersLifeCycleResolverImpl.class, PmCapabilityReaderImpl.class]


    @Unroll
    def 'When validating a subscription of type=#subscriptionType, name=#testData.subscriptionName, userType=#testData.userType then it should throw ValidationException with message #testData.exceptionMessage'() {
        given:
            subscription = subscriptionType.identifier.newInstance() as Subscription
            subscription.name = testData.subscriptionName
            subscription.userType = testData.userType
            subscription.type = subscriptionType

        when: 'subscription is validated'
            subscriptionReadOperationService.validate(subscription)

        then: 'exception should be thrown'
            def exception = thrown(ValidationException)
            exception.message == testData.exceptionMessage

        where:
            [subscriptionType, testData] << [
                    SubscriptionType.values().findAll {
                        !(it in [SubscriptionType.EVENTS, SubscriptionType.EBS, SubscriptionType.RPMO, SubscriptionType.CELLRELATION, SubscriptionType.GPEH, SubscriptionType.CELLTRAFFIC, SubscriptionType.CELLRELATION, SubscriptionType.RESOURCE])
                    },
                    [
                            //Test Data
                            [subscriptionName: 'TestSub', userType: null, exceptionMessage: ApplicationMessages.NOTVALID_USER_TYPE],
                            [subscriptionName: null, userType: SYSTEM_DEF, exceptionMessage: ApplicationMessages.SUBSCRIPTION_NAME_TAG_MISSING],
                            [subscriptionName: '', userType: USER_DEF, exceptionMessage: ApplicationMessages.EMPTY_SUBSCRIPTION_NAME],
                            [subscriptionName: '\',=\\[\\]{}~`', userType: USER_DEF, exceptionMessage: '\',=\\[\\]{}~`' + ApplicationMessages.NOTVALID_SUBSCRIPTION_NAME],
                            [subscriptionName: null, userType: null, exceptionMessage: ApplicationMessages.SUBSCRIPTION_NAME_TAG_MISSING],
                    ]
            ].combinations()
    }

    @Unroll
    def 'When validating a subscription of type=#subscriptionType, name=#testData.subscriptionName, userType=#testData.userType then it should NOT throw Exception'() {
        given:
            subscription = subscriptionType.identifier.newInstance() as Subscription
            subscription.name = testData.subscriptionName
            subscription.userType = testData.userType
            subscription.type = subscriptionType

        when: 'subscription is validated'
            subscriptionReadOperationService.validate(subscription)

        then: 'exception should not be thrown'
            noExceptionThrown()

        where:
            [subscriptionType, testData] << [
                    SubscriptionType.values().findAll {
                        !(it in [SubscriptionType.EVENTS, SubscriptionType.EBS, SubscriptionType.RPMO, SubscriptionType.CELLRELATION, SubscriptionType.GPEH, SubscriptionType.CELLTRAFFIC, SubscriptionType.CELLRELATION, SubscriptionType.RESOURCE])
                    },
                    [
                            //Test Data
                            [subscriptionName: "!#\$%&'()*+-./:<>?@\\^_|", userType: USER_DEF],
                            [subscriptionName: 'TestSub', userType: SYSTEM_DEF],
                            [subscriptionName: 'TestSub', userType: USER_DEF],
                            [subscriptionName: 'TestStats Sub', userType: USER_DEF],
                            [subscriptionName: "TestStats %&'()", userType: USER_DEF],
                            [subscriptionName: '\$%&TestSub', userType: SYSTEM_DEF],
                            [subscriptionName: '|TestSub./:<', userType: USER_DEF]
                    ]
            ].combinations()
    }

    @Unroll
    def 'When validating a subscription of type=#subscriptionType with scheduleInfo=[#testData.startDateTime:#testData.endDateTime] and ropPeriod=#testData.ropPeriod then it should throw Exception with message #testData.exceptionMessage'() {
        given:
            subscription = subscriptionType.identifier.newInstance() as Subscription
            subscription.name = 'SubscriptionTestName'
            subscription.userType = USER_DEF
            subscription.rop = testData.ropPeriod
            subscription.scheduleInfo = new ScheduleInfo(testData.startDateTime, testData.endDateTime)
            subscription.type = subscriptionType

        when: 'subscription is validated'
            subscriptionReadOperationService.validate(subscription)

        then: 'exception should be thrown'
            def exception = thrown(ValidationException)
            exception.message == testData.exceptionMessage

        where:
            [subscriptionType, testData] << [
                    SubscriptionType.values().findAll {
                        !(it in [SubscriptionType.EVENTS, SubscriptionType.EBS, SubscriptionType.RPMO, SubscriptionType.CELLRELATION, SubscriptionType.GPEH, SubscriptionType.CELLTRAFFIC, SubscriptionType.CELLRELATION, SubscriptionType.RESOURCE])
                    },
                    [
                            //Test Data
                            //Negative test: Validation test for a subscription with schedule info that [ startDateTime = endDateTime ]
                            [ropPeriod                    : ONE_MIN, startDateTime: dateUtils.addMinutesOnDate(now, 100), endDateTime: dateUtils.addMinutesOnDate(now,
                                    100), exceptionMessage: ENDTIME_GREATER_START_TIME],
                            //Negative test: Validation test for subscription with schedule info that [ endDateTime < currentTime ]
                            [ropPeriod                    : ONE_HOUR, startDateTime: dateUtils.addMinutesOnDate(now, -60), endDateTime: dateUtils.addMinutesOnDate(now,
                                    -30), exceptionMessage: ENDTIME_GREATER_CURRENT_TIME],
                            //Negative test: Validation test for subscription with schedule info that with only endDateTime and [ endDateTime < currentTime ]
                            [ropPeriod                    : ONE_HOUR, startDateTime: null, endDateTime: dateUtils.addMinutesOnDate(now,
                                    -30), exceptionMessage: ENDTIME_GREATER_CURRENT_TIME],
                            //Negative test: Validation test for subscription with schedule info that [ endDateTime < (current + rop) ]
                            [ropPeriod                   : ONE_HOUR, startDateTime: dateUtils.addMinutesOnDate(now, -60), endDateTime: dateUtils.addMinutesOnDate(now,
                                    30), exceptionMessage: ENDTIME_EQUAL_OR_BEFORE_CURRENTTIME_PLUS_ROP],
                            //Negative test: Validation test for subscription with schedule info that have only invalidate endDateTime [ endDateTime < (current + rop) ]
                            [ropPeriod                   : ONE_HOUR, startDateTime: null, endDateTime: dateUtils.addMinutesOnDate(now,
                                    30), exceptionMessage: ENDTIME_EQUAL_OR_BEFORE_CURRENTTIME_PLUS_ROP],
                            //Negative test:  Validation test for subscription have only invalid endDateTime during next available_ROP
                            [ropPeriod                                                 : ONE_HOUR, startDateTime: null, endDateTime: new Date(
                                    now.getTime() + (1000 * 60 * 15)), exceptionMessage: ENDTIME_EQUAL_OR_BEFORE_CURRENTTIME_PLUS_ROP]
                    ]
            ].combinations()
    }

    @Unroll
    def 'When validating a subscription of type=#subscriptionType with scheduleInfo=[#testData.startDateTime:#testData.endDateTime] and NO RopPeriod then it should throw Exception with message #testData.exceptionMessage'() {
        given:
            subscription = subscriptionType.identifier.newInstance() as Subscription
            subscription.name = 'SubscriptionTestName'
            subscription.userType = USER_DEF
            subscription.rop = testData.ropPeriod
            subscription.scheduleInfo = new ScheduleInfo(testData.startDateTime, testData.endDateTime)
            subscription.type = subscriptionType

        when: 'subscription is validated'
            subscriptionReadOperationService.validate(subscription)

        then: 'exception should be thrown'
            def exception = thrown(ValidationException)
            exception.message == testData.exceptionMessage
            subscription.rop == NOT_APPLICABLE

        where:
            [subscriptionType, testData] << [
                    SubscriptionType.values().findAll {
                        !(it in [SubscriptionType.EVENTS, SubscriptionType.EBS, SubscriptionType.RPMO, SubscriptionType.CELLRELATION, SubscriptionType.RESOURCE])
                    },
                    [
                            //Test Data
                            //Negative test: Validation test for a subscription with schedule info that [ startDateTime = endDateTime ]
                            [ropPeriod                    : NOT_APPLICABLE, startDateTime: dateUtils.addMinutesOnDate(now, 100), endDateTime: dateUtils.addMinutesOnDate(now,
                                    100), exceptionMessage: ENDTIME_GREATER_START_TIME],
                            //Negative test: Validation test for subscription with schedule info that [ endDateTime < currentTime ]
                            [ropPeriod                    : NOT_APPLICABLE, startDateTime: dateUtils.addMinutesOnDate(now, -60), endDateTime: dateUtils.addMinutesOnDate(now,
                                    -30), exceptionMessage: ENDTIME_GREATER_CURRENT_TIME],
                            //Negative test: Validation test for subscription with schedule info that with only endDateTime and [ endDateTime < currentTime ]
                            [ropPeriod                    : NOT_APPLICABLE, startDateTime: null, endDateTime: dateUtils.addMinutesOnDate(now,
                                    -30), exceptionMessage: ENDTIME_GREATER_CURRENT_TIME],
                    ]
            ].combinations()
    }

    @Unroll
    def 'When validating a subscription of type=#subscriptionType with scheduleInfo=[#testData.startDateTime:#testData.endDateTime] and NO RopPeriod then it should NOT throw Exception'() {
        given:
            subscription = subscriptionType.identifier.newInstance() as Subscription
            subscription.name = 'SubscriptionTestName'
            subscription.userType = USER_DEF
            subscription.rop = testData.ropPeriod
            subscription.scheduleInfo = new ScheduleInfo(testData.startDateTime, testData.endDateTime)
            subscription.type = subscriptionType

        when: 'subscription is validated'
            subscriptionReadOperationService.validate(subscription)

        then: 'exception not should be thrown'
            noExceptionThrown()
            subscription.rop == NOT_APPLICABLE

        where:
            [subscriptionType, testData] << [
                    SubscriptionType.values().findAll {
                        !(it in [SubscriptionType.EVENTS, SubscriptionType.EBS, SubscriptionType.RPMO, SubscriptionType.CELLRELATION, SubscriptionType.RESOURCE])
                    },
                    [
                            //Test Data
                            //Negative test: Validation test for subscription with schedule info that [ endDateTime < (current + rop) ]
                            [ropPeriod: NOT_APPLICABLE, startDateTime: dateUtils.addMinutesOnDate(now, -60), endDateTime: dateUtils.addMinutesOnDate(now, 30)],
                            //Negative test: Validation test for subscription with schedule info that have only invalidate endDateTime [ endDateTime < (current + rop) ]
                            [ropPeriod: NOT_APPLICABLE, startDateTime: null, endDateTime: dateUtils.addMinutesOnDate(now, 30)],
                            //Negative test:  Validation test for subscription have only invalid endDateTime during next available_ROP
                            [ropPeriod: NOT_APPLICABLE, startDateTime: null, endDateTime: new Date(now.time + (1000 * 60 * 15))]
                    ]
            ].combinations()
    }

    @Unroll
    def 'When validating a subscription of type=#subscriptionType with scheduleInfo=[#testData.startDateTime:#testData.endDateTime] and AdminState=#testData.adminState then it should NOT throw Exception'() {
        given:
            subscription = subscriptionType.identifier.newInstance() as Subscription
            subscription.name = 'SubscriptionTestName'
            subscription.userType = SYSTEM_DEF
            subscription.rop = testData.ropPeriod
            subscription.scheduleInfo = new ScheduleInfo(testData.startDateTime, testData.endDateTime)
            subscription.administrationState = testData.adminState
            subscription.type = subscriptionType

        when: 'subscription is validated'
            subscriptionReadOperationService.validate(subscription)

        then: 'exception should not be thrown'
            noExceptionThrown()
            subscription.rop == testData.ropPeriod

        where:
            [subscriptionType, testData] << [
                    SubscriptionType.values().findAll {
                        !(it in [SubscriptionType.MOINSTANCE, SubscriptionType.RPMO, SubscriptionType.EVENTS, SubscriptionType.EBS, SubscriptionType.CELLTRACE, SubscriptionType.GPEH, SubscriptionType.CELLTRAFFIC, SubscriptionType.CELLRELATION, SubscriptionType.RESOURCE])
                    },
                    [
                            //Test Data
                            //Positive test: it should be OK when subscription active but current time is final scheduled rop [endDateTime - 2hrs: endDateTime + 15min]
                            [ropPeriod                : ONE_HOUR, adminState: ACTIVE, startDateTime: dateUtils.addMinutesOnDate(dateUtils.addMinutesOnDate(now, 15),
                                    -120), endDateTime: dateUtils.
                                    addMinutesOnDate(now, 15)],
                            //Positive test: it should be OK to create a subscription with only valid endDateTime
                            [ropPeriod: ONE_HOUR, adminState: INACTIVE, startDateTime: null, endDateTime: dateUtils.addMinutesOnDate(now, 120)],
                            //Positive test: it should be OK to create a subscription with both startDateTime and endDateTime  null
                            [ropPeriod: ONE_HOUR, adminState: INACTIVE, startDateTime: null, endDateTime: null],
                            //Positive test: schedule info startDateTime should be before endDateTime and endTime should be after currentTimeplus Rop
                            [ropPeriod                                 : ONE_HOUR, adminState: INACTIVE, startDateTime: dateUtils.fromString(
                                    '2014-04-16T13:00:00'), endDateTime: dateUtils.addMinutesOnDate(now, 120)],
                            //Positive test: it should be OK to create a subscription with only start time
                            [ropPeriod                                 : ONE_HOUR, adminState: INACTIVE, startDateTime: formatString('2014-04-16 13:00:00', 'UTC',
                                    'yyyy-MM-dd HH:mm:ss'), endDateTime: null]
                    ]
            ].combinations()
    }

    static Date formatString(final String date, final String timeZone, final String format) throws ParseException {
        def formatter = new SimpleDateFormat(format)
        formatter.setTimeZone(TimeZone.getTimeZone(timeZone))
        return formatter.parse(date)
    }

    @Unroll
    def 'When validating a subscription of type=#subscriptionType and NO RopPeriod then it should NOT throw Exception default 15min used'() {
        given:
            subscription = subscriptionType.identifier.newInstance() as Subscription
            subscription.name = 'SubscriptionTestName'
            subscription.userType = SYSTEM_DEF
            subscription.type = subscriptionType

        when: 'subscription is validated'
            subscriptionReadOperationService.validate(subscription)

        then: 'exception not should be thrown'
            noExceptionThrown()
            subscription.rop == FIFTEEN_MIN

        where:
            subscriptionType << SubscriptionType.values().findAll {
                !(it in [SubscriptionType.EVENTS, SubscriptionType.EBS, SubscriptionType.RPMO, SubscriptionType.CELLRELATION, SubscriptionType.GPEH, SubscriptionType.CELLTRAFFIC, SubscriptionType.CELLRELATION, SubscriptionType.RESOURCE])
            }
    }

    @Unroll
    def 'When validating a subscription of type=#subscriptionType with scheduleInfo=[#testData.startDateTime:#testData.endDateTime] and ropPeriod=#testData.ropPeriod then it should throw Exception for 24 ROP period with message #testData.exceptionMessage'() {
        given:
            subscription = subscriptionType.identifier.newInstance() as Subscription
            subscription.name = 'SubscriptionTestName'
            subscription.userType = USER_DEF
            subscription.rop = testData.ropPeriod
            subscription.scheduleInfo = new ScheduleInfo(testData.startDateTime, testData.endDateTime)
            subscription.type = subscriptionType

        when: 'subscription is validated'
            subscriptionReadOperationService.validate(subscription)

        then: 'exception should be thrown'
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
                            [ropPeriod: ONE_DAY, startDateTime: null, endDateTime: new Date(now.time + (1000 * 60 * 60 * 24)), exceptionMessage: ENDTIME_EQUAL_OR_BEFORE_CURRENTTIME_PLUS_ROP]
                    ]
            ].combinations()
    }

    @Unroll
    def 'When validating a subscription of type=#subscriptionType and with RopPeriod=ONE_MIN then it should NOT be Replaced with Default'() {
        given:
            subscription = subscriptionType.identifier.newInstance() as Subscription
            subscription.name = 'SubscriptionTestName'
            subscription.userType = SYSTEM_DEF
            subscription.rop = ONE_MIN
            subscription.type = subscriptionType

        when: 'subscription is validated'
            subscriptionReadOperationService.validate(subscription)

        then: 'exception not should be thrown'
            noExceptionThrown()
            subscription.rop == ONE_MIN

        where:
            subscriptionType << SubscriptionType.values().findAll {
                !(it in [SubscriptionType.EVENTS, SubscriptionType.EBS, SubscriptionType.RPMO, SubscriptionType.CELLRELATION, SubscriptionType.GPEH, SubscriptionType.CELLTRAFFIC, SubscriptionType.CELLRELATION, SubscriptionType.RESOURCE])
            }
    }

    @Unroll
    def 'When validating a subscription of type=#subscriptionType with Valid IP Address and OutputMode=STREAMING, it should NOT throw Exception'() {
        given:
            subscription = subscriptionType.identifier.newInstance() as EventSubscription
            subscription.name = 'SubscriptionTestName'
            subscription.userType = SYSTEM_DEF
            subscription.type = subscriptionType
            subscription.outputMode = STREAMING
            subscription.streamInfoList = [new StreamInfo('128.2.2.2', 22)]

        when: 'subscription is validated'
            subscriptionReadOperationService.validate(subscription)

        then: 'exception no should be thrown'
            noExceptionThrown()

        where:
            subscriptionType << [SubscriptionType.CELLTRACE, SubscriptionType.EBM, SubscriptionType.CONTINUOUSCELLTRACE, SubscriptionType.RPMO]
    }

    @Ignore
    def 'When validating a subscription of type=UETRACE with Valid IP Address and OutputMode=STREAMING, it should NOT throw Exception'() {
        given:
            subscription = new UETraceSubscription()
            subscription.type = SubscriptionType.UETRACE
            subscription.name = 'SubscriptionTestName'
            subscription.userType = SYSTEM_DEF
            subscription.outputMode = STREAMING
            subscription.streamInfo = new StreamInfo('128.2.2.2', 22)

        when: 'subscription is validated'
            subscriptionReadOperationService.validate(subscription)

        then: 'exception no should be thrown'
            noExceptionThrown()
    }

    @Unroll
    @Ignore
    def 'When validating a subscription of type=RPMO where the valid IpAdress or Port is not found, it should throw Exception'() {
        given:
            subscription = new RpmoSubscription()
            subscription.type = SubscriptionType.RPMO
            subscription.name = 'SubscriptionTestName'
            subscription.userType = SYSTEM_DEF
            subscription.outputMode = STREAMING

        when: 'subscription is validated'
        subscriptionReadOperationService.validate(subscription)

        then: 'exception should be thrown'
            def exception = thrown(ValidationException)
            exception.message == NO_IP_OR_PORT_AVAILABLE

        where:
            streamInfo << [new StreamInfo('20012:db8:a0b:12f0:0::1', 22),
                           new StreamInfo('128.2.23.2333', 22),
                           new StreamInfo('128.2.4555.23', 22),
                           new StreamInfo('191.1221.21.1', 22),
                           new StreamInfo('128454.7.2.23', 22),
                           new StreamInfo('191.1.1.1.1', 22),
                           new StreamInfo('127.0.0', 22)]
    }

    @Unroll
    def 'When validating a subscription of type=#subscriptionType with OutputMode=FILE and Valid IP Address, it should throw Exception'() {
        given:
            subscription = subscriptionType.identifier.newInstance() as EventSubscription
            subscription.name = 'SubscriptionTestName'
            subscription.userType = SYSTEM_DEF
            subscription.type = subscriptionType
            subscription.outputMode = FILE
            subscription.streamInfoList = [new StreamInfo('128.2.2.2', 22),
                                           new StreamInfo('192.128.1.3', 22),
                                           new StreamInfo('255.255.255.255', 22)]

        when: 'subscription is validated'
            subscriptionReadOperationService.validate(subscription)

        then: 'exception should be thrown'
            def exception = thrown(ValidationException)
            exception.message == NOTVALID_EVENT_FILEMODE_PARAMS

        where:
            subscriptionType << [SubscriptionType.CELLTRACE, SubscriptionType.EBM, SubscriptionType.CONTINUOUSCELLTRACE]
    }

    def 'When validating a subscription of type=UETRACE with OutputMode=FILE and Valid IP Address, it should throw Exception'() {
        given:
            subscription = new UETraceSubscription()
            subscription.type = SubscriptionType.UETRACE
            subscription.name = 'SubscriptionTestName'
            subscription.userType = SYSTEM_DEF
            subscription.outputMode = FILE
            subscription.streamInfo = new StreamInfo('128.2.2.2', 22)

        when: 'subscription is validated'
            subscriptionReadOperationService.validate(subscription)

        then: 'exception should be thrown'
            def exception = thrown(ValidationException)
            exception.message == NOTVALID_EVENT_FILEMODE_PARAMS
    }

    @Unroll
    def 'When validating a subscription of type=#subscriptionType with OutputMode=FILE and IP Address is Empty, it should NOT throw Exception'() {
        given:
            subscription = subscriptionType.identifier.newInstance() as EventSubscription
            subscription.name = 'SubscriptionTestName'
            subscription.userType = SYSTEM_DEF
            subscription.type = subscriptionType
            subscription.outputMode = FILE

        when: 'subscription is validated'
            subscriptionReadOperationService.validate(subscription)

        then: 'exception should be thrown'
            noExceptionThrown()

        where:
            subscriptionType << [SubscriptionType.CELLTRACE, SubscriptionType.EBM, SubscriptionType.CONTINUOUSCELLTRACE]
    }

    def 'When validating a subscription of type=UETRACE with OutputMode=FILE and IP Address is Empty, it should NOT throw Exception'() {
        given:
            subscription = new UETraceSubscription()
            subscription.type = SubscriptionType.UETRACE
            subscription.name = 'SubscriptionTestName'
            subscription.userType = SYSTEM_DEF
            subscription.outputMode = FILE

        when: 'subscription is validated'
            subscriptionReadOperationService.validate(subscription)

        then: 'exception should be thrown'
            noExceptionThrown()
    }

    @Unroll
    def 'When validating a subscription of type=#subscriptionType with Invalid IPv6 Address, it should throw Exception'() {
        given:
            subscription = subscriptionType.identifier.newInstance() as EventSubscription
            subscription.name = 'SubscriptionTestName'
            subscription.userType = SYSTEM_DEF
            subscription.type = subscriptionType
            subscription.outputMode = STREAMING
            subscription.streamInfoList = [new StreamInfo('20012:db8:a0b:12f0:0::1', 22),
                                           new StreamInfo('2607:f0d0:1002:51::4:22', 22),
                                           new StreamInfo('FE80:0000:0000:00040:0202:B3FF:AEFF:8329', 22)]

        when: 'subscription is validated'
            subscriptionReadOperationService.validate(subscription)

        then: 'exception should be thrown'
            def exception = thrown(ValidationException)
            exception.message == INVALID_IP_FORMAT

        where:
            subscriptionType << [SubscriptionType.CELLTRACE, SubscriptionType.EBM, SubscriptionType.CONTINUOUSCELLTRACE]
    }

    @Unroll
    def 'When validating a subscription of type=UETRACE with Invalid IP Address, it should throw Exception'() {
        given:
            subscription = new UETraceSubscription()
            subscription.type = SubscriptionType.UETRACE
            subscription.name = 'SubscriptionTestName'
            subscription.userType = SYSTEM_DEF
            subscription.outputMode = STREAMING
            subscription.streamInfo = streamInfo

        when: 'subscription is validated'
            subscriptionReadOperationService.validate(subscription)

        then: 'exception should be thrown'
            def exception = thrown(ValidationException)
            exception.message == INVALID_IP_FORMAT

        where:
            streamInfo << [new StreamInfo('20012:db8:a0b:12f0:0::1', 22),
                           new StreamInfo('128.2.23.2333', 22),
                           new StreamInfo('128.2.4555.23', 22),
                           new StreamInfo('191.1221.21.1', 22),
                           new StreamInfo('128454.7.2.23', 22),
                           new StreamInfo('191.1.1.1.1', 22),
                           new StreamInfo('127.0.0', 22)]
    }

    @Unroll
    def 'When validating a subscription of type=CTUM with Invalid IP Address, it should throw Exception'() {
        given:
            subscription = new CtumSubscription()
            subscription.type = SubscriptionType.CTUM
            subscription.name = 'SubscriptionTestName'
            subscription.userType = SYSTEM_DEF
            subscription.outputMode  = STREAMING
            subscription.streamInfo = streamInfo

        when: 'subscription is validated'
            subscriptionReadOperationService.validate(subscription)

        then: 'exception should be thrown'
            def exception = thrown(ValidationException)
            exception.message == INVALID_IP_FORMAT

        where:
            streamInfo << [new StreamInfo('20012:db8:a0b:12f0:0::1', 22),
                           new StreamInfo('128.2.23.2333', 22),
                           new StreamInfo('128.2.4555.23', 22),
                           new StreamInfo('191.1221.21.1', 22),
                           new StreamInfo('128454.7.2.23', 22),
                           new StreamInfo('191.1.1.1.1', 22),
                           new StreamInfo('127.0.0', 22)]
    }

    @Unroll
    def 'When validating a subscription of type=#subscriptionType with Valid IPv6 Address, it should NOT throw Exception'() {
        given:
            subscription = subscriptionType.identifier.newInstance() as EventSubscription
            subscription.name = 'SubscriptionTestName'
            subscription.userType = SYSTEM_DEF
            subscription.type = subscriptionType
            subscription.outputMode = STREAMING
            subscription.streamInfoList = [new StreamInfo('2607:f0d0:1002:51::4', 22),
                                           new StreamInfo('2001:db8:a0b:12f0:0::1', 22),
                                           new StreamInfo('FE80:0000:0000:0000:0202:B3FF:AEFF:8329', 22)]

        when: 'subscription is validated'
            subscriptionReadOperationService.validate(subscription)

        then: 'exception not should be thrown'
            noExceptionThrown()

        where:
            subscriptionType << [SubscriptionType.CELLTRACE, SubscriptionType.EBM, SubscriptionType.CONTINUOUSCELLTRACE]
    }

    @Unroll
    def 'When validating a subscription of type=UETRACE with Valid IPv6 Address, it should NOT throw Exception'() {
        given:
            subscription = new UETraceSubscription()
            subscription.type = SubscriptionType.UETRACE
            subscription.name = 'SubscriptionTestName'
            subscription.userType = SYSTEM_DEF
            subscription.outputMode = STREAMING
            subscription.streamInfo = new StreamInfo('2607:f0d0:1002:51::4', 22)

        when: 'subscription is validated'
            subscriptionReadOperationService.validate(subscription)

        then: 'exception not should be thrown'
            noExceptionThrown()
    }

    def 'When validating a subscription of type=CTUM with Valid IPv6 Address, it should NOT throw Exception'() {
        given:
            subscription = new CtumSubscription()
            subscription.type = SubscriptionType.CTUM
            subscription.name = 'SubscriptionTestName'
            subscription.userType = SYSTEM_DEF
            subscription.outputMode = STREAMING
            subscription.streamInfo = new StreamInfo('2607:f0d0:1002:51::4', 22)

        when: 'subscription is validated'
            subscriptionReadOperationService.validate(subscription)

        then: 'exception not should be thrown'
            noExceptionThrown()
    }

    @Unroll
    def 'When validating a subscription of type=#subscriptionType with Invalid IPv4 Address:#testData.streamInfo, it should throw Exception'() {
        given:
            subscription = subscriptionType.identifier.newInstance() as EventSubscription
            subscription.name = 'SubscriptionTestName'
            subscription.userType = SYSTEM_DEF
            subscription.type = subscriptionType
            subscription.outputMode = STREAMING
            subscription.streamInfoList = [streamInfo]

        when: 'subscription is validated'
            subscriptionReadOperationService.validate(subscription)

        then: 'exception should be thrown'
            def exception = thrown(ValidationException)
            exception.message == INVALID_IP_FORMAT

        where:
            [subscriptionType, streamInfo] << [
                    [SubscriptionType.CELLTRACE, SubscriptionType.EBM, SubscriptionType.CONTINUOUSCELLTRACE],
                    [new StreamInfo('128.2.23.2333', 22),
                     new StreamInfo('128.2.4555.23', 22),
                     new StreamInfo('191.1221.21.1', 22),
                     new StreamInfo('128454.7.2.23', 22),
                     new StreamInfo('191.1.1.1.1', 22),
                     new StreamInfo('127.0.0', 22)]
            ].combinations()
    }

    @Unroll
    def 'When validating a subscription of type=#subscriptionType with both Valid IPv4 and IPV6 Addresses should NOT throw Exception'() {
        given:
            subscription = subscriptionType.identifier.newInstance() as EventSubscription
            subscription.name = 'SubscriptionTestName'
            subscription.userType = SYSTEM_DEF
            subscription.type = subscriptionType
            subscription.outputMode = STREAMING
            subscription.streamInfoList = [new StreamInfo('2607:f0d0:1002:51::4', 22), new StreamInfo('192.168.0.50', 22)]

        when: 'subscription is validated'
            subscriptionReadOperationService.validate(subscription)

        then: 'exception not should be thrown'
            noExceptionThrown()

        where:
            subscriptionType << [SubscriptionType.CELLTRACE, SubscriptionType.EBM, SubscriptionType.CONTINUOUSCELLTRACE]
    }

    @Unroll
    def 'When validating a subscription of type=#subscriptionType with OutputMode=testData.outputModeType but no IP Address, it should throw Exception'() {
        given:
            subscription = subscriptionType.identifier.newInstance() as EventSubscription
            subscription.name = 'SubscriptionTestName'
            subscription.userType = SYSTEM_DEF
            subscription.type = subscriptionType as SubscriptionType
            subscription.outputMode = outputModeType as OutputModeType

        when: 'subscription is validated'
            subscriptionReadOperationService.validate(subscription)

        then: 'exception should be thrown'
            def exception = thrown(ValidationException)
            exception.message == NO_IP_OR_PORT_AVAILABLE

        where:
            [subscriptionType, outputModeType] << [
                    [SubscriptionType.CELLTRACE, SubscriptionType.EBM, SubscriptionType.CONTINUOUSCELLTRACE],
                    [STREAMING, FILE_AND_STREAMING],
            ].combinations()
    }

    @Unroll
    def 'When validating a subscription of type=UETRACE with OutputMode=testData.outputModeType but no IP Address, it should throw Exception'() {
        given:
            subscription = new UETraceSubscription()
            subscription.type = SubscriptionType.UETRACE
            subscription.name = 'SubscriptionTestName'
            subscription.userType = SYSTEM_DEF
            subscription.outputMode = outputMode

        when: 'subscription is validated'
            subscriptionReadOperationService.validate(subscription)

        then: 'exception should be thrown'
            def exception = thrown(ValidationException)
            exception.message == NO_IP_OR_PORT_AVAILABLE

        where:
            outputMode << [STREAMING, FILE_AND_STREAMING]
    }

    @Unroll
    def 'When validating a subscription of type=CTUM with OutputMode=testData.outputModeType but no IP Address, it should throw Exception'() {
        given:
            subscription = new CtumSubscription()
            subscription.type = SubscriptionType.CTUM
            subscription.name = 'SubscriptionTestName'
            subscription.userType = SYSTEM_DEF
            subscription.outputMode = outputMode

        when: 'subscription is validated'
            subscriptionReadOperationService.validate(subscription)

        then: 'exception should be thrown'
            def exception = thrown(ValidationException)
            exception.message == NO_IP_OR_PORT_AVAILABLE

        where:
            outputMode << [STREAMING, FILE_AND_STREAMING]
    }

    @Unroll
    def 'When validating a subscription of type=#subscriptionType with #testData.testMessage UeInfo[#testData.ueType:#testData.value], it should throw Exception Message:#testData.exceptionMessage'() {
        given:
            subscription = new UETraceSubscription()
            subscription.type = SubscriptionType.UETRACE
            subscription.name = 'SubscriptionTestName'
            subscription.userType = SYSTEM_DEF
            subscription.outputMode = FILE
            subscription.ueInfo = new UeInfo(testData.ueType, testData.value)

        when: 'subscription is validated'
            subscriptionReadOperationService.validate(subscription)

        then: 'exception should be thrown'
            def exception = thrown(ValidationException)
            exception.message == testData.exceptionMessage

        where:
            testData <<
                    [[ueType: IMEI, value: '1234', exceptionMessage: INVALID_IMEI_FORMAT, testMessage: '4 digit IMEI'],
                     [ueType: IMSI, value: 'invalidImsi', exceptionMessage: INVALID_IMSI_FORMAT, testMessage: 'invalid IMSI'],
                     [ueType: IMEI, value: '12345678901234', exceptionMessage: INVALID_IMEI_FORMAT, testMessage: 'invalid IMEI'],
                     [ueType: IMSI, value: '1234', exceptionMessage: INVALID_IMSI_FORMAT, testMessage: '4 digit IMSI'],
                     [ueType: IMEI_SOFTWARE_VERSION, value: 'invalidImsi', exceptionMessage: INVALID_IMEI_SOFTWARE_VERSION_FORMAT, testMessage: 'invalid IMEI SOFTWARE VERSION'],]
    }

    @Unroll
    def 'When validating a subscription of type=#subscriptionType with #testData.testMessage UeInfo[#testData.ueType:#testData.value], it should NOT throw Exception'() {
        given:
            subscription = new UETraceSubscription()
            subscription.type = SubscriptionType.UETRACE
            subscription.name = 'SubscriptionTestName'
            subscription.userType = SYSTEM_DEF
            subscription.outputMode = FILE
            subscription.ueInfo = new UeInfo(testData.ueType, testData.value)

        when: 'subscription is validated'
            subscriptionReadOperationService.validate(subscription)

        then: 'exception should be thrown'
            noExceptionThrown()

        where:
            testData << [[ueType: IMEI, value: '123456789098765', testMessage: 'Valid 14 digit IMEI'],
                         [ueType: IMSI, value: '123456789098765', testMessage: 'Valid 14 digit IMSI'],
                         [ueType: IMEI_SOFTWARE_VERSION, value: '1234567890987654', testMessage: 'Valid 15 digit IMEI SOFTWARE VERSION']]
    }

    def 'When validating a stats subscription with Valid NodeCounters then All NodeCounters are Retained'() {
        given:
            mockedPmFunctionEnabledWrapper.containsFdn(_) >> true
            mockedPmFunctionEnabledWrapper.isPmFunctionEnabled(_) >> true


            subscription = new StatisticalSubscription()
            subscription.counters = [new CounterInfo('ipv6OutNoRoutes', 'SGSN-MME_IPv6_RouterInstance'),
                                     new CounterInfo('ipv6InReceives', 'SGSN-MME_IPv6_RouterInstance')]
            subscription.type = SubscriptionType.STATISTICAL
            subscription.name = 'SubscriptionTestName'
            subscription.userType = SYSTEM_DEF
            subscription.nodes = [getSgsnNode()]

        when: 'subscription is validated'
            subscriptionReadOperationService.validate(subscription)

        then: 'exception should be thrown'
            noExceptionThrown()
            ((StatisticalSubscription) subscription).counters.size() == 2
    }

    def 'When validating a celltrace subscription with Valid OSS Counters then All OSS Counters are Retained'() {
        given:
            mockedPmFunctionEnabledWrapper.containsFdn(_) >> true
            mockedPmFunctionEnabledWrapper.isPmFunctionEnabled(_) >> true

            def subscription = createCellTraceSubscription(CellTraceCategory.CELLTRACE_AND_EBSL_FILE, [getSgsnNode()], [],
                    [new CounterInfo('MME Periodic TAU REJECT CC17', 'TAI'),
                     new CounterInfo('MME Periodic TAU REJECT CC95', 'TAI')],
                    [])

        when: 'subscription is validated'
            subscriptionReadOperationService.validate(subscription)

        then: 'no exception should be thrown'
            noExceptionThrown()
            ((CellTraceSubscription) subscription).ebsCounters.size() == 2
    }

    @Unroll
    def 'When validating a cell trace subscription only the appropriate EBS counters are kept'() {
        given:
            mockedPmFunctionEnabledWrapper.containsFdn(_) >> true
            mockedPmFunctionEnabledWrapper.isPmFunctionEnabled(_) >> true
        and: 'a RadioNode with specific technology domains'
            def node = getRadioNode()
        and: 'a cell trace subscription with EBS counters'
            def events = []
            def ebsEvents = []
            def subscription = createCellTraceSubscription(subscriptionCellTraceCategory, [node], events,
                                                           ebsCounters, ebsEvents)
        and: 'the subscription was imported from the UI'
            subscription.isImported = true

        when: 'subscription is validated'
            subscriptionReadOperationService.validate(subscription)

        then: 'no exception should be thrown'
            noExceptionThrown()
        and: 'the validated EBS counters only include counters according to the supported technology domain'
            ((CellTraceSubscription) subscription).ebsCounters.size() == expectedEbsCountersListSize
        where:
            subscriptionCellTraceCategory                  | nodeTechnologyDomains | ebsCounters                                                     || expectedEbsCountersListSize
            CellTraceCategory.CELLTRACE_AND_EBSL_FILE      | ['EPS']               | [new CounterInfo('pmAnrMeasFailEutranCgi', 'EUtranCellFDD')]    || 1
            CellTraceCategory.CELLTRACE_AND_EBSL_FILE      | ['EPS', '5GS']        | [new CounterInfo('pmAnrMeasFailEutranCgi', 'EUtranCellFDD')]    || 1
            CellTraceCategory.CELLTRACE_NRAN_AND_EBSN_FILE | ['5GS']               | [new CounterInfo('pmEbsCuUpExampleCounter', 'GNBCUUPFunction')] || 1
            CellTraceCategory.CELLTRACE_NRAN_AND_EBSN_FILE | ['EPS', '5GS']        | [new CounterInfo('pmEbsCuUpExampleCounter', 'GNBCUUPFunction')] || 1
    }

    def 'When validating as ebm subscription with Valid OSS Counters then All OSS Counters are Retained'() {
        given:
            mockedPmFunctionEnabledWrapper.containsFdn(_) >> true
            mockedPmFunctionEnabledWrapper.isPmFunctionEnabled(_) >> true

            subscription = new EbmSubscription()
            subscription.ebsCounters = [new CounterInfo('MME Periodic TAU REJECT CC17', 'TAI'),
                                        new CounterInfo('MME Periodic TAU REJECT CC95', 'TAI')]
            subscription.name = 'SubscriptionTestName'
            subscription.userType = SYSTEM_DEF
            subscription.outputMode = FILE
            subscription.nodes = [getSgsnNode()]
            subscription.type = SubscriptionType.EBM
            subscription.ebsEnabled = true

        when: 'subscription is validated'
            subscriptionReadOperationService.validate(subscription)

        then: 'exception should be thrown'
            noExceptionThrown()
            ((EbmSubscription) subscription).ebsCounters.size() == 2
    }

    @Unroll
    def 'When validating a subscription of type #subscriptionType with Valid Events then All Events are Retained'() {
        given:
            mockedPmFunctionEnabledWrapper.containsFdn(_) >> true
            mockedPmFunctionEnabledWrapper.isPmFunctionEnabled(_) >> true

            subscription = subscriptionType.identifier.newInstance() as EventSubscription
            subscription.events = [new EventInfo('L_TAU', 'OTHER'),
                                   new EventInfo('INTERNAL_EVENT_ANR_CONFIG_MISSING', 'INTERNAL')]
            subscription.name = 'SubscriptionTestName'
            subscription.userType = USER_DEF
            subscription.outputMode = FILE
            subscription.nodes = [getSgsnNode(), getErbsNode()]
            subscription.type = subscriptionType

        when: 'subscription is validated'
            subscriptionReadOperationService.validate(subscription)

        then: 'exception should be thrown'
            noExceptionThrown()
            subscription.events.size() == 2

        where:
            subscriptionType << [SubscriptionType.EBM, SubscriptionType.CELLTRACE, SubscriptionType.CONTINUOUSCELLTRACE, SubscriptionType.UETR]
    }

    @Unroll
    def 'When validating a uetr subscription with #testData.testMessage UeInfo[#testData.ueType:#testData.value], it should throw Exception Message:#testData.exceptionMessage'() {
        given:
            subscription = new UetrSubscription()
            subscription.name = 'SubscriptionTestName'
            subscription.userType = SYSTEM_DEF
            subscription.outputMode = FILE
            subscription.type = SubscriptionType.UETR
            subscription.ueInfo = [new UeInfo(testData.ueType, testData.value)]

        when: 'subscription is validated'
            subscriptionReadOperationService.validate(subscription)

        then: 'exception should be thrown'
            def exception = thrown(ValidationException)
            exception.message == testData.exceptionMessage

        where:
            testData << [[ueType: IMSI, value: '1234567895434501234', exceptionMessage: INVALID_IMSI_FORMAT, testMessage: 'invalid IMEI'],
                         [ueType: IMSI, value: '1234', exceptionMessage: INVALID_IMSI_FORMAT, testMessage: '4 digit IMSI']]
    }

    @Unroll
    def 'When validating CellTrace subscription with wrong CellTraceCategory, it should throw ValidationException'() {
        given: 'CellTrace Subscription'
            mockedPmFunctionEnabledWrapper.containsFdn(_) >> true
            mockedPmFunctionEnabledWrapper.isPmFunctionEnabled(_) >> true

            def subscription = createCellTraceSubscription(cellTraceCategory, [getErbsNode()], events, ebsCounters, ebsEvents)

        when: 'subscription is validated'
            subscriptionReadOperationService.validate(subscription)

        then: 'exception should be thrown'
            def exception = thrown(ValidationException)
            exception.message == exceptionMessage

        where:
            cellTraceCategory                               | ebsCounters | ebsEvents | events || exceptionMessage
            null                                            | []          | []        | []     || String.format(INVALID_NULL_CELLTRACE_CATEGORY, 'SubscriptionTestName')
            null                                            | COUNTERS    | []        | []     || String.format(INVALID_NULL_CELLTRACE_CATEGORY, 'SubscriptionTestName')
            null                                            | COUNTERS    | EVENTS    | []     || String.format(INVALID_NULL_CELLTRACE_CATEGORY, 'SubscriptionTestName')
            null                                            | COUNTERS    | EVENTS    | EVENTS || String.format(INVALID_NULL_CELLTRACE_CATEGORY, 'SubscriptionTestName')
            CellTraceCategory.CELLTRACE                     | COUNTERS    | EVENTS    | EVENTS || String.format(INVALID_FILE_AND_STREAM_CELLTRACE_CATEGORY, 'SubscriptionTestName', 'CELLTRACE_AND_EBSL_STREAM', 'CELLTRACE_NRAN_AND_EBSN_STREAM', 'ASR', 'ESN')
            CellTraceCategory.CELLTRACE_AND_EBSL_FILE       | COUNTERS    | EVENTS    | EVENTS || String.format(INVALID_FILE_AND_STREAM_CELLTRACE_CATEGORY, 'SubscriptionTestName', 'CELLTRACE_AND_EBSL_STREAM', 'CELLTRACE_NRAN_AND_EBSN_STREAM',  'ASR', 'ESN')
            CellTraceCategory.CELLTRACE_NRAN                | COUNTERS    | EVENTS    | EVENTS || String.format(INVALID_FILE_AND_STREAM_CELLTRACE_CATEGORY, 'SubscriptionTestName', 'CELLTRACE_AND_EBSL_STREAM', 'CELLTRACE_NRAN_AND_EBSN_STREAM',  'ASR', 'ESN')
            CellTraceCategory.CELLTRACE_NRAN_AND_EBSN_FILE  | COUNTERS    | EVENTS    | EVENTS || String.format(INVALID_FILE_AND_STREAM_CELLTRACE_CATEGORY, 'SubscriptionTestName', 'CELLTRACE_AND_EBSL_STREAM', 'CELLTRACE_NRAN_AND_EBSN_STREAM',  'ASR', 'ESN')
            CellTraceCategory.NRAN_EBSN_STREAM              | COUNTERS    | EVENTS    | EVENTS || String.format(INVALID_FILE_AND_STREAM_CELLTRACE_CATEGORY, 'SubscriptionTestName', 'CELLTRACE_AND_EBSL_STREAM', 'CELLTRACE_NRAN_AND_EBSN_STREAM',  'ASR', 'ESN')
            CellTraceCategory.EBSL_STREAM                   | COUNTERS    | []        | []     || String.format(INVALID_FILE_CELLTRACE_CATEGORY, 'SubscriptionTestName', Arrays.asList('CELLTRACE_AND_EBSL_FILE, CELLTRACE_NRAN_AND_EBSN_FILE'));
            CellTraceCategory.CELLTRACE_AND_EBSL_STREAM     | COUNTERS    | []        | []     || String.format(INVALID_FILE_CELLTRACE_CATEGORY, 'SubscriptionTestName', Arrays.asList('CELLTRACE_AND_EBSL_FILE, CELLTRACE_NRAN_AND_EBSN_FILE'))
            CellTraceCategory.CELLTRACE                     | COUNTERS    | []        | []     || String.format(INVALID_FILE_CELLTRACE_CATEGORY, 'SubscriptionTestName', Arrays.asList('CELLTRACE_AND_EBSL_FILE, CELLTRACE_NRAN_AND_EBSN_FILE'))
            CellTraceCategory.CELLTRACE_NRAN                | COUNTERS    | []        | []     || String.format(INVALID_FILE_CELLTRACE_CATEGORY, 'SubscriptionTestName', Arrays.asList('CELLTRACE_AND_EBSL_FILE, CELLTRACE_NRAN_AND_EBSN_FILE'))
            CellTraceCategory.ASR                           | COUNTERS    | []        | []     || String.format(INVALID_FILE_CELLTRACE_CATEGORY, 'SubscriptionTestName', Arrays.asList('CELLTRACE_AND_EBSL_FILE, CELLTRACE_NRAN_AND_EBSN_FILE'))
            CellTraceCategory.ESN                           | COUNTERS    | []        | []     || String.format(INVALID_FILE_CELLTRACE_CATEGORY, 'SubscriptionTestName', Arrays.asList('CELLTRACE_AND_EBSL_FILE, CELLTRACE_NRAN_AND_EBSN_FILE'))
            CellTraceCategory.NRAN_EBSN_STREAM              | COUNTERS    | []        | []     || String.format(INVALID_FILE_CELLTRACE_CATEGORY, 'SubscriptionTestName', Arrays.asList('CELLTRACE_AND_EBSL_FILE, CELLTRACE_NRAN_AND_EBSN_FILE'))
            CellTraceCategory.CELLTRACE_NRAN_AND_EBSN_STREAM| COUNTERS    | []        | []     || String.format(INVALID_FILE_CELLTRACE_CATEGORY, 'SubscriptionTestName', Arrays.asList('CELLTRACE_AND_EBSL_FILE, CELLTRACE_NRAN_AND_EBSN_FILE'))
            CellTraceCategory.CELLTRACE                     | COUNTERS    | EVENTS    | []     || String.format(INVALID_STREAM_CELLTRACE_CATEGORY, 'SubscriptionTestName', 'EBSL_STREAM', 'NRAN_EBSN_STREAM', 'ASR', 'ESN')
            CellTraceCategory.CELLTRACE_AND_EBSL_FILE       | COUNTERS    | EVENTS    | []     || String.format(INVALID_STREAM_CELLTRACE_CATEGORY, 'SubscriptionTestName', 'EBSL_STREAM', 'NRAN_EBSN_STREAM', 'ASR', 'ESN')
            CellTraceCategory.CELLTRACE_AND_EBSL_STREAM     | COUNTERS    | EVENTS    | []     || String.format(INVALID_STREAM_CELLTRACE_CATEGORY, 'SubscriptionTestName', 'EBSL_STREAM', 'NRAN_EBSN_STREAM', 'ASR', 'ESN')
            CellTraceCategory.CELLTRACE_NRAN                | COUNTERS    | EVENTS    | []     || String.format(INVALID_STREAM_CELLTRACE_CATEGORY, 'SubscriptionTestName', 'EBSL_STREAM', 'NRAN_EBSN_STREAM', 'ASR', 'ESN')
            CellTraceCategory.CELLTRACE_NRAN_AND_EBSN_FILE  | COUNTERS    | EVENTS    | []     || String.format(INVALID_STREAM_CELLTRACE_CATEGORY, 'SubscriptionTestName', 'EBSL_STREAM', 'NRAN_EBSN_STREAM', 'ASR', 'ESN')
            CellTraceCategory.CELLTRACE_NRAN_AND_EBSN_STREAM| COUNTERS    | EVENTS    | []     || String.format(INVALID_STREAM_CELLTRACE_CATEGORY, 'SubscriptionTestName', 'EBSL_STREAM', 'NRAN_EBSN_STREAM', 'ASR', 'ESN')
    }

    @Unroll
    def 'When validating CellTrace subscription with correct CellTraceCategory, it should not throw ValidationException'() {
        given: 'CellTrace Subscription'
            mockedPmFunctionEnabledWrapper.containsFdn(_) >> true
            mockedPmFunctionEnabledWrapper.isPmFunctionEnabled(_) >> true

            def subscription = createCellTraceSubscription(cellTraceCategory, [getErbsNode()], events, ebsCounters, ebsEvents)

        when: 'subscription is validated'
            subscriptionReadOperationService.validate(subscription)

        then: 'exception should not be thrown'
            noExceptionThrown()

        where:
            cellTraceCategory                               | ebsCounters | ebsEvents | events
            CellTraceCategory.CELLTRACE                     | []          | []        | []
            CellTraceCategory.CELLTRACE                     | []          | []        | EVENTS
            CellTraceCategory.CELLTRACE_NRAN                | []          | []        | []
            CellTraceCategory.CELLTRACE_NRAN                | []          | []        | EVENTS
            CellTraceCategory.CELLTRACE_AND_EBSL_FILE       | COUNTERS    | []        | EVENTS
            CellTraceCategory.CELLTRACE_AND_EBSL_FILE       | COUNTERS    | []        | []
            CellTraceCategory.EBSL_STREAM                   | COUNTERS    | EVENTS    | []
            CellTraceCategory.ASR                           | COUNTERS    | EVENTS    | []
            CellTraceCategory.ESN                           | COUNTERS    | EVENTS    | []
            CellTraceCategory.CELLTRACE_AND_EBSL_STREAM     | COUNTERS    | EVENTS    | EVENTS
            CellTraceCategory.ASR                           | COUNTERS    | EVENTS    | []
            CellTraceCategory.ESN                           | COUNTERS    | EVENTS    | []
            CellTraceCategory.CELLTRACE_NRAN_AND_EBSN_FILE  | COUNTERS    | []        | EVENTS
            CellTraceCategory.CELLTRACE_NRAN_AND_EBSN_STREAM| COUNTERS    | EVENTS    | EVENTS
            CellTraceCategory.NRAN_EBSN_STREAM              | COUNTERS    | EVENTS    | []
    }

    @Unroll
    def 'When validating a subscription of type=#subscriptionType with all nodes having PM Function disabled, it should throw Exception'() {
        given: '#subscriptionType subscrition with all nodes having PM Function disabled'
            mockedPmFunctionEnabledWrapper.containsFdn(_) >> true
            mockedPmFunctionEnabledWrapper.isPmFunctionEnabled(_) >> false

            subscription = subscriptionType.identifier.newInstance() as Subscription
            subscription.type = subscriptionType
            subscription.name = 'SubscriptionTestName'
            subscription.userType = USER_DEF
            subscription.nodes = [getErbsNode()]

        when: 'subscription is validated'
            subscriptionReadOperationService.validate(subscription)

        then: 'exception should be thrown'
            def exception = thrown(PmFunctionValidationException)
            assert exception.message.contains('All nodes are unavailable due to PM Function disabled')

        where:
            subscriptionType << [SubscriptionType.RES, SubscriptionType.CELLTRACE, SubscriptionType.CELLTRAFFIC, SubscriptionType.CONTINUOUSCELLTRACE, SubscriptionType.EBM, SubscriptionType.GPEH, SubscriptionType.UETR, SubscriptionType.CELLRELATION, SubscriptionType.MOINSTANCE, SubscriptionType.STATISTICAL]
    }

    @Unroll
    def 'When validating a subscription of type=#subscriptionType with some nodes having PM Function disabled then it should throw Exception'() {
        given: '#subscriptionType subscription with some nodes having PM Function disabled'
            mockedPmFunctionEnabledWrapper.containsFdn(_) >> true
            mockedPmFunctionEnabledWrapper.isPmFunctionEnabled('NetworkElement=SGSN-16A-V1-CP0203') >> true
            mockedPmFunctionEnabledWrapper.isPmFunctionEnabled('NetworkElement=LTE01ERBS00002') >> false

            subscription = subscriptionType.identifier.newInstance() as Subscription
            subscription.type = subscriptionType
            subscription.name = 'SubscriptionTestName'
            subscription.userType = USER_DEF
            subscription.nodes = [getSgsnNode(), getErbsNode()]

        when: 'subscription is validated'
            subscriptionReadOperationService.validateAndAdjustSubscriptionData(subscription)

        then: 'exception should be thrown containg invalid nodes'
            def exception = thrown(PmFunctionValidationException)
            assert exception.message.contains('There are unavailable nodes due to PM Function disabled')
            def invalidNodes = exception.getInvalidNodes()
            assert invalidNodes.size() == 1
            def invalidNodeNames = []
            invalidNodes.each { invalidNodeNames.add(it.name) }
            assert invalidNodeNames.containsAll(['LTE01ERBS00002'])

        where:
            subscriptionType << [SubscriptionType.RES, SubscriptionType.CELLTRACE, SubscriptionType.CELLTRAFFIC, SubscriptionType.CONTINUOUSCELLTRACE, SubscriptionType.EBM, SubscriptionType.GPEH, SubscriptionType.UETR, SubscriptionType.CELLRELATION, SubscriptionType.MOINSTANCE, SubscriptionType.STATISTICAL]
    }

    Node getRadioNode() {
        def node = new Node()
        node.neType = 'RadioNode'
        node.fdn = 'NetworkElement=RadioNode1'
        node.ossModelIdentity = '19.Q3-R41A26'
        node.name = 'RadioNode1'
        node.id = 281474977608870L
        node.technologyDomain = ['EPS']
        node.pmFunction = true
        return node
    }

    Node getErbsNode() {
        def node = new Node()
        node.neType = 'ERBS'
        node.fdn = 'NetworkElement=LTE01ERBS00002'
        node.ossModelIdentity = '18.Q2-J.1.280'
        node.name = 'LTE01ERBS00002'
        node.id = 281474977608869L
        node.technologyDomain = ['EPS']
        node.pmFunction = true
        return node
    }

    Node getSgsnNode() {
        def node = new Node()
        node.neType = 'SGSN-MME'
        node.fdn = 'NetworkElement=SGSN-16A-V1-CP0203'
        node.ossModelIdentity = '16A-CP02'
        node.name = 'SGSN-16A-V1-CP0203'
        node.id = 281474977591390L
        node.technologyDomain = ['EPS']
        node.pmFunction = true
        return node
    }

    CellTraceSubscription createCellTraceSubscription(final CellTraceCategory cellTraceCategory,
                                                      final List<Node> nodes,
                                                      final List<EventInfo> events,
                                                      final List<CounterInfo> ebsCounters,
                                                      final List<EventInfo> ebsEvents,
                                                      final String name = 'SubscriptionTestName') {
        def subscription = new CellTraceSubscription()
        subscription.cellTraceCategory = cellTraceCategory
        subscription.ebsCounters = ebsCounters
        subscription.ebsEvents = ebsEvents
        subscription.events = events
        subscription.name = name
        subscription.userType = SYSTEM_DEF
        subscription.outputMode = FILE
        subscription.nodes = nodes
        subscription.type = SubscriptionType.CELLTRACE
        subscription.administrationState = INACTIVE
        return subscription
    }
}