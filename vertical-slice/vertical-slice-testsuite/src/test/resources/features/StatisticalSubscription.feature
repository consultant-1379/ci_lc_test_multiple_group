@Rest @StatisticalSubscription
Feature: StatisticalSubscription CURD operation

  Scenario: create read and delete Statistical Subscription
    When Create a subscription
      | headers                           | address   | port | operation | jsonFile                                  | expectedStatusCodes |
      | X-Tor-UserId=pmOperator           | localhost | 8080 | POST      | Statistical/StatisticalSubscription1.json | 201,202             |
    Then Wait for Subscription creation
    When Retrieve a subscription
      | headers                           | address   | port | operation | uri                   | expectedStatusCodes |
      | X-Tor-UserId=pmOperator           | localhost | 8080 | GET       | getIdByName/EmptyStat | 200                 |
    When Retrieve a subscription
      | headers                           | address   | port | operation | expectedStatusCodes |
      | X-Tor-UserId=pmOperator           | localhost | 8080 | GET       | 200                 |
    When Export a subscription
      | headers                           | address   | port | operation | uri                 | expectedStatusCodes |
      | X-Tor-UserId=pmOperator           | localhost | 8080 | GET       | exportsubscription/ | 200                 |
    Then Verify Exported Subscription contains
      | name        |
      | description |
      | type        |
    And Verify Exported Subscription does not contains
      | activationTime   |
      | deactivationTime |
    When Delete a subscription
      | headers                           | address   | port | operation | expectedStatusCodes |
      | X-Tor-UserId=pmOperator           | localhost | 8080 | DELETE    | 200                 |

