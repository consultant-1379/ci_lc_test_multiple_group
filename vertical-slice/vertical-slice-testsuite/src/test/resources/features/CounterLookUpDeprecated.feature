@Rest @MetaData @Counters
Feature: PfmMeasurement Lookup Deprecated

  Scenario: Rest call for getting all NE Counters for Single ERBS ossModelIdentity deprecated
    When Make the rest call
      | headers                           | address   | port | operation | uri                                      | params                           | acceptType       | contentType      |expectedStatusCodes | paramSeperator  |
      | X-Tor-UserId=pmOperator           | localhost | 8080 | GET       | pm-service/rest/pmsubscription/counters  | mim=ERBS:3958-644-341&definer=NE | application/json | application/json |200                 | &               |
    Then Response contains 52 Unique "sourceObject"
    And Response contains 2386 "counterName"

  Scenario: Rest call for getting all NE Counters for Multiple ERBS ossModelIdentity deprecated
    When Make the rest call
      | headers                           | address   | port | operation | uri                                      | params                                                                | expectedStatusCodes | paramSeperator  |
      | X-Tor-UserId=pmOperator           | localhost | 8080 | GET       | /pm-service/rest/pmsubscription/counters | mim=ERBS:4322-436-393,ERBS:4322-940-032,ERBS:3958-644-341&definer=NE  | 200                 | &               |
    Then Response contains 61 Unique "sourceObject"
    And Response contains 2627 "counterName"

  Scenario: Rest call for getting all NE Counters for Valid and Invalid ERBS ossModelIdentity deprecated
    When Make the rest call
      | headers                           | address   | port | operation | uri                                      | params                                        | expectedStatusCodes | paramSeperator  |
      | X-Tor-UserId=pmOperator           | localhost | 8080 | GET       | /pm-service/rest/pmsubscription/counters | mim=ERBS:3958-644-341,XXXX:9.9.9&definer=NE   | 400                 | &               |

  Scenario: Rest call for getting all NE Counters for Invalid ossModelIdentity deprecated
    When Make the rest call
      | headers                           | address   | port | operation | uri                                      | params                    | expectedStatusCodes | paramSeperator  |
      | X-Tor-UserId=pmOperator           | localhost | 8080 | GET       | /pm-service/rest/pmsubscription/counters | mim=XXXX:9.9.9&definer=NE | 400                 | &               |


  Scenario: Rest call for getting all NE Counters for Single SGSN-MME ossModelIdentity deprecated
    When Make the rest call
      | headers                           | address   | port | operation | uri                                      | params                           | expectedStatusCodes | paramSeperator   |
      | X-Tor-UserId=pmOperator           | localhost | 8080 | GET       | /pm-service/rest/pmsubscription/counters | mim=SGSN-MME:15B-CP01&definer=NE | 200                 | &                |
    Then Response contains 127 Unique "sourceObject"
    And Response contains 1280 "counterName"


  Scenario: Rest call for getting all OSS Counters for Single SGSN-MME ossModelIdentity with EPS technology Domain deprecated
    When Make the rest call
      | headers                           | address   | port | operation | uri                                      | params                                | expectedStatusCodes | paramSeperator  |
      | X-Tor-UserId=pmOperator           | localhost | 8080 | GET       | /pm-service/rest/pmsubscription/counters | mim=SGSN-MME:16A-CP02:EPS&definer=OSS | 200                 | &               |
    Then Response contains 2 Unique "sourceObject"
    And Response contains 283 "counterName"

  Scenario: Rest call for getting all NE Counters for Single SGSN-MME ossModelIdentity with EPS technology Domain deprecated
    When Make the rest call
      | headers                           | address   | port | operation | uri                                      | params                               | expectedStatusCodes | paramSeperator   |
      | X-Tor-UserId=pmOperator           | localhost | 8080 | GET       | /pm-service/rest/pmsubscription/counters | mim=SGSN-MME:16A-CP02:EPS&definer=NE | 200                 | &                |
    Then Response contains 66 Unique "sourceObject"
    And Response contains 624 "counterName"

  Scenario: Rest call for getting all NE Counters for single RadioNode with EPS technology domain deprecated
    When Make the rest call
      | headers                           | address   | port | operation | uri                                      | params                                    | expectedStatusCodes | paramSeperator  |
      | X-Tor-UserId=pmOperator           | localhost | 8080 | GET       | /pm-service/rest/pmsubscription/counters | mim=RadioNode:18.Q2-R43A23:EPS&definer=NE | 200                 | &               |
    Then Response contains 2335 Unique "counterName"

  Scenario: Rest call for getting all NE Counters for single RadioNode with 5GS technology domain deprecated
    When Make the rest call
      | headers                           | address   | port | operation | uri                                      | params                                    | expectedStatusCodes | paramSeperator |
      | X-Tor-UserId=pmOperator           | localhost | 8080 | GET       | /pm-service/rest/pmsubscription/counters | mim=RadioNode:19.Q1-R31A14:5GS&definer=NE | 200                 | & |
    Then Response contains 422 Unique "counterName"

  Scenario: Rest call for getting all NE Counters for a mix of RadioNode with EPS and 5GS technology domains deprecated
    When Make the rest call
      | headers                           | address   | port | operation | uri                                      | params                                                                | expectedStatusCodes | paramSeperator  |
      | X-Tor-UserId=pmOperator           | localhost | 8080 | GET       | /pm-service/rest/pmsubscription/counters | mim=RadioNode:18.Q2-R43A23:EPS,RadioNode:19.Q1-R31A14:5GS&definer=NE  | 200                 | &               |
    Then Response contains 2461 Unique "counterName"

  Scenario: Rest call for getting all NE Counters for a mixed Node with EPS and 5GS technology domains deprecated
    When Make the rest call
      | headers                           | address   | port | operation | uri                                      | params                                        | expectedStatusCodes   | paramSeperator    |
      | X-Tor-UserId=pmOperator           | localhost | 8080 | GET       | /pm-service/rest/pmsubscription/counters | mim=RadioNode:19.Q3-R41A26:5GS#EPS&definer=NE | 200                   | &                 |
    Then Response contains 5040 "counterName"

  Scenario: Rest call for getting all OSS Counters for single RadioNode with 5GS technology domain deprecated
    When Make the rest call
      | headers                           | address   | port | operation | uri                                       | params                                        | expectedStatusCodes | paramSeperator |
      | X-Tor-UserId=pmOperator           | localhost | 8080 | GET       | /pm-service/rest/pmsubscription/counters | mim=RadioNode:19.Q3-R41A26:5GS&definer=OSS    | 200                 | &              |
    Then Response contains 6 "counterName"

  Scenario: Rest call for getting all OSS Counters for single RadioNode with EPS technology domain deprecated
    When Make the rest call
      | headers                           | address   | port | operation | uri                                      | params                                        | expectedStatusCodes | paramSeperator  |
      | X-Tor-UserId=pmOperator           | localhost | 8080 | GET       | /pm-service/rest/pmsubscription/counters | mim=RadioNode:19.Q3-R41A26:EPS&definer=OSS    | 200                 | &               |
    Then Response contains 774 "counterName"

  Scenario: Rest call for getting all NE Counters for Single SNMP-CUSTOM-NODE ossModelIdentity when measurement model contains SNMP OIDs deprecated
    When Make the rest call
      | headers                 | address   | port | operation | uri                                     | params                              | acceptType       | contentType      | expectedStatusCodes | paramSeperator |
      | X-Tor-UserId=pmOperator | localhost | 8080 | GET       | pm-service/rest/pmsubscription/counters | mim=SNMP-CUSTOM-NODE:17A&definer=NE | application/json | application/json | 200                 | &              |
    Then Response contains 9 Unique "sourceObject" different from OID
    And Response contains 65 "counterName" different from OID