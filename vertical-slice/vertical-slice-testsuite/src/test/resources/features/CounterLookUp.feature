@Rest @MetaData @Counters
Feature: PfmMeasurement Lookup
    Scenario Outline: Rest call for getting all NE Counters for Single ERBS ossModelIdentity for <subscriptionType> subscription
        When Make the rest call
            | headers                           | address   | port | operation | uri                                      | params                                                                  | acceptType       | contentType      |expectedStatusCodes | paramSeperator  |
            | X-Tor-UserId=pmOperator           | localhost | 8080 | GET       | pm-service/rest/pmsubscription/counters  | mim=ERBS:3958-644-341&definer=<subscriptionType>_SubscriptionAttributes | application/json | application/json |200                 | &               |
        Then Response contains 52 Unique "sourceObject"
        And Response contains 2386 "counterName"
        Examples:
            | subscriptionType  |
            | STATISTICAL       |
            | MOINSTANCE        |
            | EBS               |
            | CELLRELATION      |
            | RES               |

    Scenario Outline: Rest call for getting all NE Counters for Single ERBS ossModelIdentity for <subscriptionType> subscription
        When Make the rest call
            | headers                           | address   | port | operation | uri                                      | params                                                                  | acceptType       | contentType      |expectedStatusCodes | paramSeperator  |
            | X-Tor-UserId=pmOperator           | localhost | 8080 | GET       | pm-service/rest/pmsubscription/counters  | mim=ERBS:3958-644-341&definer=<subscriptionType>_SubscriptionAttributes | application/json | application/json |200                 | &               |
        Then Response contains 52 Unique "sourceObject"
        And Response contains 2386 "counterName"
        Examples:
            | subscriptionType  |
            | STATISTICAL       |
            | MOINSTANCE        |
            | EBS               |
            | CELLRELATION      |
            | RES               |

    Scenario Outline: Rest call for getting all NE Counters for Multiple ERBS ossModelIdentity for <subscriptionType> subscription
        When Make the rest call
            | headers                           | address   | port | operation | uri                                      | params                                                                                                      | expectedStatusCodes | paramSeperator  |
            | X-Tor-UserId=pmOperator           | localhost | 8080 | GET       | /pm-service/rest/pmsubscription/counters | mim=ERBS:4322-436-393,ERBS:4322-940-032,ERBS:3958-644-341&definer=<subscriptionType>_SubscriptionAttributes | 200                 | &               |
        Then Response contains 61 Unique "sourceObject"
        And Response contains 2627 "counterName"
            Examples:
                | subscriptionType  |
                | STATISTICAL       |
                | MOINSTANCE        |
                | EBS               |
                | CELLRELATION      |
                | RES               |

    Scenario Outline: Rest call for getting all NE Counters for Valid and Invalid ERBS ossModelIdentity for <subscriptionType> subscription
        When Make the rest call
            | headers                           | address   | port | operation | uri                                      | params                                                                              | expectedStatusCodes | paramSeperator  |
            | X-Tor-UserId=pmOperator           | localhost | 8080 | GET       | /pm-service/rest/pmsubscription/counters | mim=ERBS:3958-644-341,XXXX:9.9.9&definer=<subscriptionType>_SubscriptionAttributes  | 400                 | &               |
        Examples:
            | subscriptionType  |
            | STATISTICAL       |
            | MOINSTANCE        |
            | EBS               |
            | CELLRELATION      |
            | RES               |

    Scenario Outline: Rest call for getting all NE Counters for Invalid ossModelIdentity for <subscriptionType> subscription
        When Make the rest call
            | headers                           | address   | port | operation | uri                                      | params                                                            | expectedStatusCodes | paramSeperator  |
            | X-Tor-UserId=pmOperator           | localhost | 8080 | GET       | /pm-service/rest/pmsubscription/counters | mim=XXXX:9.9.9&definer=<subscriptionType>_SubscriptionAttributes  | 400                 | &               |
        Examples:
            | subscriptionType  |
            | STATISTICAL       |
            | MOINSTANCE        |
            | EBS               |
            | CELLRELATION      |
            | RES               |

    Scenario Outline: Rest call for getting all NE Counters for Single SGSN-MME ossModelIdentity for <subscriptionType> subscription
        When Make the rest call
            | headers                           | address   | port | operation | uri                                      | params                                                                    | expectedStatusCodes | paramSeperator   |
            | X-Tor-UserId=pmOperator           | localhost | 8080 | GET       | /pm-service/rest/pmsubscription/counters | mim=SGSN-MME:15B-CP01&definer=<subscriptionType>_SubscriptionAttributes   | 200                 | &                |
        Then Response contains 127 Unique "sourceObject"
        And Response contains 1280 "counterName"
                Examples:
                    | subscriptionType  |
                    | STATISTICAL       |
                    | MOINSTANCE        |
                    | EBS               |
                    | CELLRELATION      |
                    | RES               |

    Scenario: Rest call for getting all OSS Counters for Single SGSN-MME ossModelIdentity with EPS technology Domain
        When Make the rest call
            | headers                           | address   | port | operation | uri                                      | params                                                        | expectedStatusCodes | paramSeperator  |
            | X-Tor-UserId=pmOperator           | localhost | 8080 | GET       | /pm-service/rest/pmsubscription/counters | mim=SGSN-MME:16A-CP02:EPS&definer=EBM_SubscriptionAttributes  | 200                 | &               |
        Then Response contains 2 Unique "sourceObject"
        And Response contains 283 "counterName"

    Scenario Outline: Rest call for getting all NE Counters for Single SGSN-MME ossModelIdentity with EPS technology Domain <subscriptionType> subscription
        When Make the rest call
            | headers                           | address   | port | operation | uri                                      | params                                                         | expectedStatusCodes| paramSeperator   |
            | X-Tor-UserId=pmOperator           | localhost | 8080 | GET       | /pm-service/rest/pmsubscription/counters | mim=SGSN-MME:16A-CP02:EPS&definer=<subscriptionType>_SubscriptionAttributes   | 200                | &                |
        Then Response contains 66 Unique "sourceObject"
        And Response contains 624 "counterName"
        Examples:
            | subscriptionType  |
            | STATISTICAL       |
            | MOINSTANCE        |
            | EBS               |
            | CELLRELATION      |
            | RES               |

    Scenario Outline: Rest call for getting all NE Counters for single RadioNode with EPS technology domain for <subscriptionType> subscription
        When Make the rest call
            | headers                           | address   | port | operation | uri                                      | params                                                                            | expectedStatusCodes | paramSeperator  |
            | X-Tor-UserId=pmOperator           | localhost | 8080 | GET       | /pm-service/rest/pmsubscription/counters | mim=RadioNode:18.Q2-R43A23:EPS&definer=<subscriptionType>_SubscriptionAttributes  | 200                 | &               |
        Then Response contains 2335 Unique "counterName"
        Examples:
            | subscriptionType  |
            | STATISTICAL       |
            | MOINSTANCE        |
            | EBS               |
            | CELLRELATION      |
            | RES               |

    Scenario Outline: Rest call for getting all NE Counters for single RadioNode with 5GS technology domain for <subscriptionType> subscription
        When Make the rest call
            | headers                           | address   | port | operation | uri                                      | params                                                                            | expectedStatusCodes | paramSeperator |
            | X-Tor-UserId=pmOperator           | localhost | 8080 | GET       | /pm-service/rest/pmsubscription/counters | mim=RadioNode:19.Q1-R31A14:5GS&definer=<subscriptionType>_SubscriptionAttributes  | 200                 | & |
        Then Response contains 422 Unique "counterName"
        Examples:
            | subscriptionType  |
            | STATISTICAL       |
            | MOINSTANCE        |
            | EBS               |
            | CELLRELATION      |
            | RES               |

    Scenario Outline: Rest call for getting all NE Counters for a mix of RadioNode with EPS and 5GS technology domains for <subscriptionType> subscription
        When Make the rest call
            | headers                           | address   | port | operation | uri                                      | params                                                                                                        | expectedStatusCodes | paramSeperator  |
            | X-Tor-UserId=pmOperator           | localhost | 8080 | GET       | /pm-service/rest/pmsubscription/counters | mim=RadioNode:18.Q2-R43A23:EPS,RadioNode:19.Q1-R31A14:5GS&definer=<subscriptionType>_SubscriptionAttributes   | 200                 | &               |
        Then Response contains 2461 Unique "counterName"
        Examples:
            | subscriptionType  |
            | STATISTICAL       |
            | MOINSTANCE        |
            | EBS               |
            | CELLRELATION      |
            | RES               |

    Scenario Outline: Rest call for getting all NE Counters for a mixed Node with EPS and 5GS technology domains for <subscriptionType> subscription
        When Make the rest call
            | headers                           | address   | port | operation | uri                                      | params                                                                                | expectedStatusCodes   | paramSeperator    |
            | X-Tor-UserId=pmOperator           | localhost | 8080 | GET       | /pm-service/rest/pmsubscription/counters | mim=RadioNode:19.Q3-R41A26:5GS#EPS&definer=<subscriptionType>_SubscriptionAttributes  | 200                   | &                 |
        Then Response contains 5040 "counterName"
        Examples:
            | subscriptionType  |
            | STATISTICAL       |
            | MOINSTANCE        |
            | EBS               |
            | CELLRELATION      |
            | RES               |

    Scenario Outline: Rest call for getting all OSS Counters for single RadioNode with <techDomain> technology domain
        When Make the rest call
            | headers                           | address   | port | operation | uri                                      | params                                                                                      | expectedStatusCodes | paramSeperator |
            | X-Tor-UserId=pmOperator           | localhost | 8080 | GET       | /pm-service/rest/pmsubscription/counters | mim=RadioNode:19.Q3-R41A26:<techDomain>&definer=<subscriptionType>_SubscriptionAttributes   | 200                 | &              |
        Then Response contains <expectedCounters> "counterName"
        Examples:
            | techDomain    | subscriptionType  | expectedCounters  |
            | 5GS           | CELLTRACENRAN     | 6                 |
            | EPS           | CELLTRACE         | 774               |

    Scenario: Rest call for getting all NE Counters for Single SNMP-CUSTOM-NODE ossModelIdentity when measurement model contains SNMP OIDs
        When Make the rest call
            | headers                 | address   | port | operation | uri                                     | params                                                                | acceptType       | contentType      | expectedStatusCodes | paramSeperator |
            | X-Tor-UserId=pmOperator | localhost | 8080 | GET       | pm-service/rest/pmsubscription/counters | mim=SNMP-CUSTOM-NODE:17A&definer=STATISTICAL_SubscriptionAttributes   | application/json | application/json | 200                 | &              |
        Then Response contains 9 Unique "sourceObject" different from OID
        And Response contains 65 "counterName" different from OID