Feature: Initial stock for catalog products
  Missing stock counters are initialized before application readiness.
  Existing counters are preserved and Redis failures abort initialization.

  Scenario: Initialize missing counters without expiration
    Given the stock counters for products 1, 2 and 3 are missing
    When the initial stock initializer runs
    Then products 1, 2 and 3 each have 100 units in stock
    And the stock counters for products 1, 2 and 3 have no TTL

  Scenario: Repeated initialization is idempotent
    Given the stock counters for products 1, 2 and 3 are missing
    When the initial stock initializer runs
    And the initial stock initializer runs again
    Then products 1, 2 and 3 each have 100 units in stock
    And the stock counters for products 1, 2 and 3 have no TTL

  Scenario: Initialize missing counters without overwriting existing stock
    Given the stock counters for products 1, 2 and 3 are missing
    And product "1" has an existing stock counter of 42 units
    When the initial stock initializer runs
    Then the stock counter for product "1" remains 42
    And the stock counter for product "2" remains 100
    And the stock counter for product "3" remains 100
    And the stock counters for products 1, 2 and 3 have no TTL

  Scenario: Reinitialization preserves reduced and exhausted counters
    Given the stock counters for products 1, 2 and 3 are missing
    When the initial stock initializer runs
    Given product "1" has an existing stock counter of 99 units
    And product "2" has an existing stock counter of 0 units
    And product "3" has an existing stock counter of 73 units
    When the initial stock initializer runs again
    Then the stock counter for product "1" remains 99
    And the stock counter for product "2" remains 0
    And the stock counter for product "3" remains 73
    And the stock counters for products 1, 2 and 3 have no TTL

  Scenario: Redis unavailability fails initialization
    Given Redis is unavailable during stock initialization
    When the initial stock initializer runs with unavailable Redis
    Then stock initialization fails with the Redis error
