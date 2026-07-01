# shop-inventory

Mikroserwis zarządzający stanami magazynowymi w systemie sklepowym. Zapobiega overselllingowi dzięki atomowym skryptom Lua na Redis przy jednoczesnym utrzymaniu trwałości w Postgres.

## Stack

- **Java 25 / Spring Boot** — logika aplikacji
- **Redis** — gorąca ścieżka: `stock:{productId}`, `reservation:{orderId}` (Lua)
- **PostgreSQL** (`inventory_db`) — źródło prawdy: `products`, `reservations`, `outbox`, `processed_events`
- **Kafka** — konsument `order-events`, producent `inventory-events`
- **Flyway** — migracje schematu (`V1__init.sql`)

## Jak działa

```
order-events ──► OrderEventListener ──► InventoryService
                                              │
                                   Lua reserve/release (Redis)
                                              │
                               (tx) reservation + outbox (Postgres)
                                              │
                                       OutboxPublisher (co 1 s)
                                              │
                                    inventory-events (Kafka)
```

1. **`OrderCreated`** → atomowy Lua: sprawdź i zmniejsz `stock:{productId}`, ustaw `reservation:{orderId}` z TTL → emit `StockReserved` / `StockReservationFailed`
2. **`ReleaseStock`** → atomowy Lua: `INCRBY stock` + `DEL reservation` → emit `StockReleased`
3. Idempotencja: `processed_events(event_id PK)` chroni przed podwójnym przetworzeniem

## REST API

| Metoda | Ścieżka | Opis |
|--------|---------|------|
| `GET` | `/inventory/{productId}` | Aktualny stan (Redis, fallback do Postgres) |
| `PUT` | `/inventory/{productId}` | Ustaw stan (tylko test-support) |
| `DELETE` | `/inventory/{productId}` | Usuń produkt (tylko test-support) |

Endpointy `PUT`/`DELETE` aktywne wyłącznie gdy `SHOP_TEST_SUPPORT_ENABLED=true`.

## Zmienne środowiskowe

| Zmienna | Domyślna | Opis |
|---------|---------|------|
| `SPRING_DATASOURCE_URL` | — | JDBC URL do `inventory_db` |
| `SPRING_DATA_REDIS_HOST` | `localhost` | Host Redis |
| `SPRING_KAFKA_BOOTSTRAP_SERVERS` | `localhost:9092` | Broker Kafka |
| `SPRING_KAFKA_CONSUMER_GROUP_ID` | `shop-inventory` | Grupa konsumenta |
| `RESERVATION_TTL_SECONDS` | `600` | TTL rezerwacji w Redis |
| `SHOP_TEST_SUPPORT_ENABLED` | `false` | Włącz endpointy testowe |

## Budowanie i uruchamianie

```bash
# Build (wymaga JDK 25)
./gradlew bootJar

# Docker
docker build -t shop-inventory .
docker run -p 8080:8080 shop-inventory
```

## Testy

```bash
./gradlew test          # testy jednostkowe + cucumber
```

## CI

Każdy PR uruchamia pełny preprod gate (`pr-to-main.yml`): buduje obraz, deployuje na lokalny klaster kind i odpala acceptance testy (`shop-acceptance-tests`). PR jest mergowalny dopiero po zielonym statusie `preprod-gate / gate`.
