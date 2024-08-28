@Rest @MetaData @Events
Feature: PfmEvent Lookup

  Scenario: Rest call for getting all Events for Single ERBS ossModelIdentity
    When Make the rest call
      | headers                           | address   | port | operation | uri                                      | params                | expectedStatusCodes |
      | X-Tor-UserId=pmOperator           | localhost | 8080 | GET       | /pm-service/rest/pmsubscription/getEvent | mim=ERBS:4322-436-393 | 200                 |
    Then Response contains 14 Unique "sourceObject"
    And Response contains 439 "eventName"

  Scenario: Rest call for getting all Events for multiple ERBS ossModelIdentity
    When Make the rest call
      | headers                           | address   | port | operation | uri                                      | params                                    | expectedStatusCodes | paramSeperator  |
      | X-Tor-UserId=pmOperator           | localhost | 8080 | GET       | /pm-service/rest/pmsubscription/getEvent | mim=ERBS:4322-436-393,ERBS:3958-644-341   | 200                 | &               |
    Then Response contains 16 Unique "sourceObject"
    And Response contains 489 "eventName"

  Scenario: Rest call for getting all Events for Valid and Invalid ossModelIdentity
    When Make the rest call
      | headers                           | address   | port | operation | uri                                      | params                            | expectedStatusCodes | paramSeperator  |
      | X-Tor-UserId=pmOperator           | localhost | 8080 | GET       | /pm-service/rest/pmsubscription/getEvent | mim=ERBS:3958-644-341,XXXX:9.9.9  | 400                 | &               |
    #Then Verify that rest response contains "Invalid Version XXXX:9.9.9. Please remove invalid version(s)."

  Scenario: Rest call for getting all Events for Single SGSN-MME ossModelIdentity
    When Make the rest call
      | headers                           | address   | port | operation | uri                                      | params                | expectedStatusCodes |
      | X-Tor-UserId=pmOperator           | localhost | 8080 | GET       | /pm-service/rest/pmsubscription/getEvent | mim=SGSN-MME:15B-CP01 | 200                 |
      | X-Tor-UserId=pmOperator           | localhost | 8080 | GET       | /pm-service/rest/pmsubscription/getEvent | mim=SGSN-MME:16A-CP02 | 200                 |
    Then Response contains 1 Unique "sourceObject"
    And Response contains 18 "eventName"

  Scenario: Rest call for getting all Events for Single ERBS ossModelIdentity
    When Make the rest call
      | headers                           | address   | port | operation | uri                                      | params                 | expectedStatusCodes |
      | X-Tor-UserId=pmOperator           | localhost | 8080 | GET       | /pm-service/rest/pmsubscription/getEvent | mim=ERBS:18.Q4-J.2.300 | 200                 |
    Then Response contains 17 Unique "sourceObject"
    And Response contains 603 "eventName"

  Scenario: Rest call for getting all Events for Single RadioNode ossModelIdentity
    When Make the rest call
      | headers                           | address   | port | operation | uri                                      | params                     | expectedStatusCodes |
      | X-Tor-UserId=pmOperator           | localhost | 8080 | GET       | /pm-service/rest/pmsubscription/getEvent | mim=RadioNode:18.Q3-R49A16 | 200                 |
    Then Response contains 16 Unique "sourceObject"
    And Response contains 582 "eventName"

  Scenario: Rest call for getting all Events for Single RNC ossModelIdentity for SubscriptionType GPEH
    When Make the rest call
      | headers                           | address   | port | operation | uri                                            | params                                    | expectedStatusCodes | paramSeperator    |
      | X-Tor-UserId=pmOperator           | localhost | 8080 | GET       | /pm-service/rest/pmsubscription/getWcdmaEvents | mim=RNC:16A-V.6.940&subscriptionType=GPEH | 200                 | &                 |
    Then Response contains 7 Unique "sourceObject"
    And Response contains 301 "eventName"
