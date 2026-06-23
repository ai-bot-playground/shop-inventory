Feature: Stock reservation
  Inventory prevents overselling during a flash sale by reserving stock
  atomically in Redis, with Postgres as the source of truth.

  Background:
    Given product "P1" has 5 units in stock

  Scenario: Reserve stock when units are available
    When an OrderCreated event arrives for order "O1" reserving 2 units of "P1"
    Then a StockReserved event is published for order "O1"
    And the available stock of "P1" is 3

  Scenario: Reservation fails when stock is insufficient
    When an OrderCreated event arrives for order "O2" reserving 10 units of "P1"
    Then a StockReservationFailed event is published for order "O2"
    And the available stock of "P1" is 5

  Scenario: Releasing a reservation restores stock (compensation)
    Given order "O1" has reserved 2 units of "P1"
    When a ReleaseStock command arrives for order "O1"
    Then a StockReleased event is published for order "O1"
    And the available stock of "P1" is 5

  Scenario: Duplicate OrderCreated is processed only once (idempotent)
    When the OrderCreated event for order "O1" reserving 2 units of "P1" is delivered twice
    Then stock is reserved only once
    And the available stock of "P1" is 3

  Scenario: Duplicate ReleaseStock does not return stock twice (idempotent)
    Given order "O1" has reserved 2 units of "P1"
    When the ReleaseStock command for order "O1" is delivered twice
    Then the available stock of "P1" is 5

  Scenario: Concurrent buyers never oversell
    Given product "P2" has 3 units in stock
    When 100 OrderCreated events each reserve 1 unit of "P2" concurrently
    Then exactly 3 reservations succeed
    And the available stock of "P2" is 0
