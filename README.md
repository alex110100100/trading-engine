# Trading Engine

A small **Spring Boot** trading engine in Java: submit orders, match by **price–time priority**, and inspect trades and **per-symbol** order books. Prices and quantities use `**BigDecimal`** in the domain and JSON.

## Requirements

- **Java 17**
- **Maven** (or `./mvnw`)
- **PostgreSQL** (see [Persistence](#persistence))

## API


| Method   | Path                        | Description                                                                                                                                                                                                                                       |
| -------- | --------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `POST`   | `/order`                    | Submit an order (JSON body). Returns `{ "orderId", "status" }` with status `ACCEPTED`, `PARTIALLY_FILLED`, or `FILLED`.                                                                                                                           |
| `GET`    | `/trades`                   | Trade history sorted by execution time (deterministic tie-break). **Reads from PostgreSQL** (`trades` table) when the app runs with JPA, so history survives restarts.                                                                            |
| `GET`    | `/orderbook?symbol=&limit=` | Snapshot of top bid/ask **levels for one symbol** (e.g. `BTC/USD`). `symbol` is required; `limit` defaults to `10`, minimum `1`.                                                                                                                  |
| `GET`    | `/order/{id}`               | Order status: **in-memory** when the engine still holds it; otherwise **from `order_states`** when JPA is enabled (survives restart for terminal states such as `FILLED` / `CANCELLED`). Returns `{ "orderId", "status" }` or **404** if unknown. |
| `DELETE` | `/order/{id}`               | Cancel a **resting** order. **204** if removed, **404** if not found.                                                                                                                                                                             |


### OpenAPI / Swagger UI

With the app running:

- **[http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html)**
- **[http://localhost:8080/v3/api-docs](http://localhost:8080/v3/api-docs)** (OpenAPI JSON)

### Actuator & metrics

**Spring Boot Actuator** exposes operational endpoints used for health checks and tooling:

- `**GET /actuator/health`**, `**GET /actuator/health/liveness**`, `**GET /actuator/health/readiness**` — health and Kubernetes-style probes (JDBC included when JPA is active).
- `**GET /actuator/prometheus**` — Prometheus exposition format (compatible with scrape-based monitoring).
- `**GET /actuator/info**`, `**GET /actuator/metrics**` — lightweight metadata and per-meter inspection.

**Micrometer** records matching behaviour: timer `**matching.engine.process.order`** (by **status**), counters `**matching.engine.orders`**, `**matching.engine.trades.executed**`, `**matching.engine.cancels**`, and gauge `**matching.engine.symbols.active**`.

**Graceful shutdown** is enabled (`server.shutdown=graceful`) so in-flight HTTP work can finish on SIGTERM.

### Observability (Grafana Cloud)

Optional metrics export to **Grafana Cloud** uses **OTLP over HTTP** (`micrometer-registry-otlp`). Copy `**application-grafana-cloud.properties.example`** to a **gitignored** local properties file, set the OTLP endpoint and credentials per **[Grafana Cloud: send metrics using OTLP](https://grafana.com/docs/grafana-cloud/send-data/otlp/send-metrics-otlp/)** (including map-style resource attributes, e.g. `**management.otlp.metrics.export.resource-attributes.[service.name]=trading-engine`**). `**GET /actuator/prometheus**` remains available for debugging and Prometheus-compatible tooling.

### Example: submit order

```bash
curl -s -X POST http://localhost:8080/order \
  -H "Content-Type: application/json" \
  -d '{"id":"o1","symbol":"BTC/USD","orderSide":"BUY","price":50000,"quantity":0.1}'
```

### Validation and errors

Request bodies and query parameters are validated. Typical **400** responses for invalid JSON, missing fields, or constraint violations.

Error body shape (`application/json`):

```json
{
  "timestamp": "2026-04-08T12:00:00Z",
  "status": 400,
  "message": "Validation failed",
  "fieldErrors": {
    "symbol": "symbol is required",
    "quantity": "must be greater than 0"
  }
}
```

`fieldErrors` may be empty for non-field errors (e.g. malformed JSON).

## Persistence

The service uses **PostgreSQL** with **Flyway** for schema migrations (`src/main/resources/db/migration`). Hibernate **validates** the schema only (`ddl-auto=validate`); tables are created and evolved through Flyway scripts, not at runtime by JPA.

The checked-in datasource URL is a typical **[Neon](https://neon.tech)**-style managed Postgres endpoint.

- **Further migrations:** add new versioned scripts under `src/main/resources/db/migration` (e.g. `V2__description.sql`) when the schema changes.
- **Trades:** On each match, new trades are written to the `**trades`** table. `**GET /trades**` reads from that table when JPA is enabled.
- **Open orders:** Resting liquidity is mirrored in `**open_orders`** after each change for that symbol (submit, match, cancel). On startup, `**OpenOrderReplayRunner**` reloads those rows into the in-memory book (same sort order as stored) so the book can recover after a restart.
- **How startup replay runs:** `**OpenOrderReplayRunner`** is a Spring bean that implements `**ApplicationRunner**`. After the application context is up (JPA repositories and `**MatchingEngine**` are ready), Spring calls `**run(ApplicationArguments)**` once. That method only delegates to `**MatchingEngine.replayOpenOrdersFromDatabase()**` — no HTTP involved. `**@Order(20)**` controls ordering relative to *other* runners (lower numbers run first); we use `20` so you can add an earlier runner later (e.g. migrations) without changing this class.
- **Order status:** Each submit/cancel updates `**order_states*`* (when JPA is enabled), including orders on the **passive** (resting) side of a match. `**GET /order/{id}`** checks memory first, then the database, so `FILLED`, `CANCELLED`, and other statuses remain visible after a restart even when the order is no longer in the book.

## How matching works

1. `**MatchingEngine**` routes each order to an `**OrderBook**` for its **symbol** (books are created on demand).
2. **BUY** matches against the best **ask** prices; **SELL** against the best **bid** prices, until filled or nothing crosses.
3. Partial fills are supported; remainder rests in that symbol’s book.
4. Each execution produces an immutable `**Trade`**; trades are appended in the book and **persisted** when JPA is enabled.

### Concurrency

- **Per-symbol locks:** different symbols can be processed on different threads in parallel; the same symbol is serialized so matching stays deterministic.

### Price–time priority

- **Price:** Better prices first (higher bid, lower ask).
- **Time:** At the same price, earlier orders match first by `**LinkedHashMap`** insertion order per level.

#### Implementation (per symbol)

- **Bids:** `TreeMap<BigDecimal, LinkedHashMap<String, BookEntry>>` — **descending** key order (best bid first).
- **Asks:** `TreeMap<BigDecimal, LinkedHashMap<String, BookEntry>>` — **ascending** key order (best ask first).
- Each `**BookEntry`** tracks remaining quantity for partial fills.

